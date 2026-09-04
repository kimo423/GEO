package com.geo.ledger.ui.addtransaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geo.ledger.R
import com.geo.ledger.data.LedgerRepository
import com.geo.ledger.data.local.ExpenseCategoryEntity
import com.geo.ledger.data.local.PersonOptionEntity
import com.geo.ledger.data.local.TransactionEntity
import com.geo.ledger.data.local.TransactionType
import com.geo.ledger.domain.OptionChipModel
import com.geo.ledger.domain.OptionSnapshotPolicy
import com.geo.ledger.domain.TransactionDraft
import com.geo.ledger.util.MoneyFormatter
import com.geo.ledger.util.MoneyParser
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AddTransactionUiState(
    val type: TransactionType = TransactionType.EXPENSE,
    val amountRaw: String = "",
    val personId: Long? = null,
    val categoryId: Long? = null,
    val source: String = "",
    val epochDay: Long = 0L,
    val note: String = "",
    val persons: List<PersonOptionEntity> = emptyList(),
    val categories: List<ExpenseCategoryEntity> = emptyList(),
    val personChips: List<OptionChipModel> = emptyList(),
    val categoryChips: List<OptionChipModel> = emptyList(),
    val isSaving: Boolean = false,
    val canSave: Boolean = false,
    val isEdit: Boolean = false,
    val isReady: Boolean = true,
    val sourceError: Boolean = false,
    val noteError: Boolean = false,
    val amountIssue: MoneyParser.DraftIssue = MoneyParser.DraftIssue.Empty,
)

sealed interface AddTransactionEvent {
    data class Saved(val messageRes: Int) : AddTransactionEvent
    data class Failed(val messageRes: Int) : AddTransactionEvent
}

class AddTransactionViewModel(
    private val savedStateHandle: SavedStateHandle,
    activePersonsFlow: Flow<List<PersonOptionEntity>>,
    activeCategoriesFlow: Flow<List<ExpenseCategoryEntity>>,
    private val getTransaction: suspend (Long) -> TransactionEntity?,
    private val saveTransaction: suspend (Long?, TransactionDraft, String?) -> Long,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    constructor(
        repository: LedgerRepository,
        savedStateHandle: SavedStateHandle,
        today: () -> LocalDate = { LocalDate.now() },
    ) : this(
        savedStateHandle = savedStateHandle,
        activePersonsFlow = repository.activePersons,
        activeCategoriesFlow = repository.activeCategories,
        getTransaction = repository::getTransaction,
        saveTransaction = { id, draft, key -> repository.saveTransaction(id, draft, key) },
        today = today,
    )

    private val initialEditingId = savedStateHandle.get<Long>(ARG_TRANSACTION_ID) ?: NEW_TRANSACTION_ID
    private val saveMutex = Mutex()
    private val saveInFlight = AtomicBoolean(false)
    private val loadStarted = AtomicBoolean(false)
    private val _isSaving = MutableStateFlow(false)
    private val _events = Channel<AddTransactionEvent>(Channel.BUFFERED)

    val events = _events.receiveAsFlow()

    val activePersons: StateFlow<List<PersonOptionEntity>> = activePersonsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val activeCategories: StateFlow<List<ExpenseCategoryEntity>> = activeCategoriesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val uiState: StateFlow<AddTransactionUiState> = combine(
        combine(
            savedStateHandle.getStateFlow(KEY_TYPE, TransactionType.EXPENSE.name),
            savedStateHandle.getStateFlow(KEY_AMOUNT, ""),
            savedStateHandle.getStateFlow(KEY_PERSON_ID, NONE_ID),
            savedStateHandle.getStateFlow(KEY_CATEGORY_ID, NONE_ID),
            savedStateHandle.getStateFlow(KEY_SOURCE, ""),
        ) { typeName, amountRaw, personId, categoryId, source ->
            FormFields(
                type = runCatching { TransactionType.valueOf(typeName) }.getOrDefault(TransactionType.EXPENSE),
                amountRaw = amountRaw,
                personId = personId.takeIf { it > 0 },
                categoryId = categoryId.takeIf { it > 0 },
                source = source,
            )
        },
        combine(
            savedStateHandle.getStateFlow(KEY_EPOCH_DAY, today().toEpochDay()),
            savedStateHandle.getStateFlow(KEY_NOTE, ""),
            savedStateHandle.getStateFlow(ARG_TRANSACTION_ID, NEW_TRANSACTION_ID),
            savedStateHandle.getStateFlow(KEY_LOADED, initialEditingId <= 0),
            _isSaving,
        ) { epochDay, note, transactionId, loaded, saving ->
            FormMeta(
                epochDay = epochDay,
                note = note,
                transactionId = transactionId,
                loaded = loaded,
                saving = saving,
            )
        },
        combine(
            savedStateHandle.getStateFlow(KEY_PERSON_SNAPSHOT, ""),
            savedStateHandle.getStateFlow(KEY_CATEGORY_SNAPSHOT, ""),
            activePersons,
            activeCategories,
        ) { personSnapshot, categorySnapshot, persons, categories ->
            FormOptions(personSnapshot, categorySnapshot, persons, categories)
        },
    ) { fields, meta, options ->
        val amountIssue = MoneyParser.draftIssue(fields.amountRaw)
        val valid = AddTransactionValidator.canSave(
            type = fields.type,
            amountRaw = fields.amountRaw,
            source = fields.source,
            note = meta.note,
            epochDay = meta.epochDay,
        )
        AddTransactionUiState(
            type = fields.type,
            amountRaw = fields.amountRaw,
            personId = fields.personId,
            categoryId = fields.categoryId,
            source = fields.source,
            epochDay = meta.epochDay,
            note = meta.note,
            persons = options.persons,
            categories = options.categories,
            personChips = OptionSnapshotPolicy.chips(
                active = options.persons.map { it.id to it.name },
                selectedId = fields.personId,
                historicalSnapshot = options.personSnapshot.ifBlank { null },
            ),
            categoryChips = OptionSnapshotPolicy.chips(
                active = options.categories.map { it.id to it.name },
                selectedId = fields.categoryId,
                historicalSnapshot = options.categorySnapshot.ifBlank { null },
            ),
            isSaving = meta.saving,
            canSave = valid && !meta.saving && amountIssue == MoneyParser.DraftIssue.None,
            isEdit = meta.transactionId > 0,
            isReady = meta.loaded,
            sourceError = fields.type == TransactionType.INCOME &&
                !AddTransactionValidator.isSourceLengthValid(fields.source),
            noteError = !AddTransactionValidator.isNoteLengthValid(meta.note),
            amountIssue = amountIssue,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AddTransactionUiState(
            epochDay = today().toEpochDay(),
            isEdit = initialEditingId > 0,
            isReady = initialEditingId <= 0,
        ),
    )

    init {
        if (savedStateHandle.get<String>(KEY_CLIENT_OP) == null) {
            savedStateHandle[KEY_CLIENT_OP] = UUID.randomUUID().toString()
        }
        hydrateExistingIfNeeded()
    }

    fun setType(type: TransactionType) {
        if (_isSaving.value) return
        savedStateHandle[KEY_TYPE] = type.name
    }

    fun onAmountChange(value: String) {
        if (_isSaving.value) return
        if (MoneyParser.isAllowedDraftInput(value) || value.trim().length > MoneyParser.MAX_DRAFT_LENGTH) {
            savedStateHandle[KEY_AMOUNT] = value.take(MoneyParser.MAX_DRAFT_LENGTH)
        }
    }

    fun togglePerson(chip: OptionChipModel) {
        toggleOption(
            chip = chip,
            idKey = KEY_PERSON_ID,
            editedKey = KEY_PERSON_EDITED,
            snapshotKey = KEY_PERSON_SNAPSHOT,
            chips = { uiState.value.personChips },
        )
    }

    fun toggleCategory(chip: OptionChipModel) {
        toggleOption(
            chip = chip,
            idKey = KEY_CATEGORY_ID,
            editedKey = KEY_CATEGORY_EDITED,
            snapshotKey = KEY_CATEGORY_SNAPSHOT,
            chips = { uiState.value.categoryChips },
        )
    }

    private fun toggleOption(
        chip: OptionChipModel,
        idKey: String,
        editedKey: String,
        snapshotKey: String,
        chips: () -> List<OptionChipModel>,
    ) {
        if (_isSaving.value) return
        savedStateHandle[editedKey] = true
        if (chip.historical) {
            savedStateHandle[idKey] = NONE_ID
            return
        }
        val current = savedStateHandle.get<Long>(idKey) ?: NONE_ID
        val historicalSelected = chips().any { it.historical && it.selected }
        val selectedId = if (current == chip.id && !historicalSelected) NONE_ID else chip.id
        savedStateHandle[idKey] = selectedId
        if (selectedId == chip.id) {
            savedStateHandle[snapshotKey] = chip.label
        }
    }

    fun onSourceChange(value: String) {
        if (_isSaving.value) return
        savedStateHandle[KEY_SOURCE] = value.take(LedgerRepository.MAX_SOURCE_LENGTH)
    }

    fun onNoteChange(value: String) {
        if (_isSaving.value) return
        savedStateHandle[KEY_NOTE] = value.take(LedgerRepository.MAX_NOTE_LENGTH)
    }

    fun setDate(date: LocalDate) {
        if (_isSaving.value) return
        savedStateHandle[KEY_EPOCH_DAY] = date.toEpochDay()
    }

    fun save() {
        val snapshot = uiState.value
        if (!snapshot.canSave) return
        if (!saveInFlight.compareAndSet(false, true)) return
        viewModelScope.launch {
            saveMutex.withLock {
                var success = false
                try {
                    _isSaving.value = true
                    val latest = uiState.value
                    val draft = AddTransactionValidator.toDraft(
                        type = latest.type,
                        amountRaw = latest.amountRaw,
                        personId = latest.personId,
                        categoryId = latest.categoryId,
                        source = latest.source,
                        note = latest.note,
                        epochDay = latest.epochDay,
                        personSelectionEdited = savedStateHandle.get<Boolean>(KEY_PERSON_EDITED) == true,
                        categorySelectionEdited = savedStateHandle.get<Boolean>(KEY_CATEGORY_EDITED) == true,
                    )
                    if (draft == null) {
                        _events.send(AddTransactionEvent.Failed(R.string.error_save_failed))
                        return@withLock
                    }
                    val editId = (savedStateHandle.get<Long>(ARG_TRANSACTION_ID) ?: NEW_TRANSACTION_ID)
                        .takeIf { it > 0 }
                    val clientOpKey = savedStateHandle.get<String>(KEY_CLIENT_OP)
                    val savedId = saveTransaction(editId, draft, clientOpKey)
                    if (savedId > 0) {
                        savedStateHandle[ARG_TRANSACTION_ID] = savedId
                    }
                    val messageRes = if (editId != null) R.string.feedback_saved else R.string.feedback_recorded
                    _events.send(AddTransactionEvent.Saved(messageRes))
                    success = true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: ArithmeticException) {
                    _events.send(AddTransactionEvent.Failed(R.string.error_overflow))
                } catch (error: IllegalArgumentException) {
                    val messageRes = if (error.message?.contains("too long", ignoreCase = true) == true) {
                        R.string.error_text_too_long
                    } else {
                        R.string.error_save_failed
                    }
                    _events.send(AddTransactionEvent.Failed(messageRes))
                } catch (_: Exception) {
                    _events.send(AddTransactionEvent.Failed(R.string.error_save_failed))
                } finally {
                    _isSaving.value = false
                    if (!success) saveInFlight.set(false)
                }
            }
        }
    }

    internal fun clientOpKeyForTest(): String? = savedStateHandle.get<String>(KEY_CLIENT_OP)

    private fun hydrateExistingIfNeeded() {
        if (initialEditingId <= 0) return
        if (savedStateHandle.get<Boolean>(KEY_LOADED) == true) return
        if (!loadStarted.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                if (savedStateHandle.get<Boolean>(KEY_LOADED) == true) return@launch
                val existing = getTransaction(initialEditingId)
                if (savedStateHandle.get<Boolean>(KEY_LOADED) == true) return@launch
                if (existing == null) {
                    _events.send(AddTransactionEvent.Failed(R.string.transaction_missing))
                    return@launch
                }
                savedStateHandle[KEY_TYPE] = existing.type.name
                savedStateHandle[KEY_AMOUNT] = MoneyFormatter.editable(existing.amountCents)
                savedStateHandle[KEY_PERSON_ID] = existing.expensePersonId ?: NONE_ID
                savedStateHandle[KEY_CATEGORY_ID] = existing.expenseCategoryId ?: NONE_ID
                savedStateHandle[KEY_PERSON_SNAPSHOT] = existing.expensePersonSnapshot.orEmpty()
                savedStateHandle[KEY_CATEGORY_SNAPSHOT] = existing.expenseCategorySnapshot.orEmpty()
                savedStateHandle[KEY_SOURCE] = existing.incomeSource.orEmpty().take(LedgerRepository.MAX_SOURCE_LENGTH)
                savedStateHandle[KEY_EPOCH_DAY] = existing.transactionDate
                savedStateHandle[KEY_NOTE] = existing.note.orEmpty().take(LedgerRepository.MAX_NOTE_LENGTH)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                savedStateHandle[KEY_LOADED] = true
            }
        }
    }

    private data class FormFields(
        val type: TransactionType,
        val amountRaw: String,
        val personId: Long?,
        val categoryId: Long?,
        val source: String,
    )

    private data class FormMeta(
        val epochDay: Long,
        val note: String,
        val transactionId: Long,
        val loaded: Boolean,
        val saving: Boolean,
    )

    private data class FormOptions(
        val personSnapshot: String,
        val categorySnapshot: String,
        val persons: List<PersonOptionEntity>,
        val categories: List<ExpenseCategoryEntity>,
    )

    companion object {
        const val ARG_TRANSACTION_ID = "transactionId"
        const val NEW_TRANSACTION_ID = -1L
        internal const val KEY_CLIENT_OP = "editor_client_op_key"
        internal const val KEY_PERSON_EDITED = "editor_person_edited"
        internal const val KEY_CATEGORY_EDITED = "editor_category_edited"
        internal const val KEY_PERSON_SNAPSHOT = "editor_person_snapshot"
        internal const val KEY_CATEGORY_SNAPSHOT = "editor_category_snapshot"
        private const val NONE_ID = -1L
        private const val KEY_TYPE = "editor_type"
        private const val KEY_AMOUNT = "editor_amount"
        private const val KEY_PERSON_ID = "editor_person_id"
        private const val KEY_CATEGORY_ID = "editor_category_id"
        private const val KEY_SOURCE = "editor_source"
        private const val KEY_EPOCH_DAY = "editor_epoch_day"
        private const val KEY_NOTE = "editor_note"
        private const val KEY_LOADED = "editor_loaded"
    }
}
