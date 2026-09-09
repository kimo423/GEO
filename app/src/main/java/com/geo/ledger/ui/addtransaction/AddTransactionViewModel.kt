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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.geo.ledger.data.local.AttachmentRecord
import com.geo.ledger.data.local.TransactionAttachmentEntity
import com.geo.ledger.data.attachments.AttachmentPolicy
import com.geo.ledger.data.transfer.*

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
    private val attachmentRepository: LedgerRepository? = null,
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
        attachmentRepository = repository,
    )

    private val initialEditingId = savedStateHandle.get<Long>(ARG_TRANSACTION_ID) ?: NEW_TRANSACTION_ID
    private val saveMutex = Mutex()
    private val saveInFlight = AtomicBoolean(false)
    private val loadStarted = AtomicBoolean(false)
    private val _isSaving = MutableStateFlow(false)
    private val _events = Channel<AddTransactionEvent>(Channel.BUFFERED)
    private val _attachments = MutableStateFlow<List<AttachmentRecord>>(emptyList())
    val attachments: StateFlow<List<AttachmentRecord>> = _attachments
    val attachmentBusy = MutableStateFlow(false)
    val attachmentMessage = MutableStateFlow<String?>(null)
    val attachmentLoadFailed = MutableStateFlow(false)

    private fun persistAttachments() {
        savedStateHandle["attachment_draft"] = StrictJson.stringify(_attachments.value.map { record ->
            AttachmentRef.from(record).json() + mapOf("internalStorageKey" to record.internalStorageKey,
                "createdAtMillis" to record.relation.createdAtMillis)
        })
    }

    fun addAttachments(context: android.content.Context, uris: List<android.net.Uri>) {
        if (uris.isEmpty() || attachmentBusy.value || _isSaving.value) return
        attachmentBusy.value=true
        viewModelScope.launch {
            val newRecords=mutableListOf<AttachmentRecord>()
            try {
                val store=requireNotNull(attachmentRepository?.blobStore)
                require(_attachments.value.size + uris.size<=10) { AttachmentPolicy.COUNT_ERROR }
                val existing=_attachments.value
                withContext(Dispatchers.IO) {
                    var remaining=AttachmentPolicy.MAX_TOTAL-existing.sumOf { it.sizeBytes }
                    uris.forEach { uri ->
                        val resolver=context.contentResolver
                        var name="附件"
                        resolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
                            if(it.moveToFirst() && !it.isNull(0)) name=it.getString(0).take(255)
                                .let { value->if(value.lastOrNull()?.let(Character::isHighSurrogate)==true) value.dropLast(1) else value }.ifBlank { "附件" }
                        }
                        val mime=resolver.getType(uri)?.takeIf { Regex("[a-zA-Z0-9!#$&^_.+-]+/[a-zA-Z0-9!#$&^_.+-]+").matches(it) } ?: "application/octet-stream"
                        val stored=requireNotNull(resolver.openInputStream(uri)) { "无法读取该文件，请重新选择" }.use { store.put(it,minOf(AttachmentPolicy.MAX_FILE,remaining)) }
                        remaining-=stored.size
                        newRecords+=AttachmentRecord(TransactionAttachmentEntity(UUID.randomUUID().toString(),"",UUID.randomUUID().toString(),
                            name,existing.size+newRecords.size,true,System.currentTimeMillis()),stored.sha256,stored.size,mime,stored.key)
                    }
                }
                AttachmentPolicy.validate((existing+newRecords).map { it.sizeBytes })
                (existing+newRecords).map(AttachmentRef::from).forEach { it.validate() }
                _attachments.value=existing+newRecords; persistAttachments()
            } catch(e: Exception) {
                withContext(kotlinx.coroutines.NonCancellable+Dispatchers.IO) {
                    newRecords.forEach { attachmentRepository?.blobStore?.file(it.internalStorageKey)?.delete() }
                }
                if(e is CancellationException) throw e
                attachmentMessage.value=if(e is java.io.IOException || e is SecurityException) "无法读取该文件，请重新选择" else e.message ?: "无法读取该文件，请重新选择"
            } finally { attachmentBusy.value=false }
        }
    }
    fun removeAttachment(uuid: String) {
        if(attachmentBusy.value || _isSaving.value) return
        _attachments.value=_attachments.value.filterNot { it.relation.attachmentUuid==uuid }.mapIndexed { i,r -> r.copy(relation=r.relation.copy(sortOrder=i)) }
        persistAttachments()
    }

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
                allowDetachedSnapshot = true,
            ),
            categoryChips = OptionSnapshotPolicy.chips(
                active = options.categories.map { it.id to it.name },
                selectedId = fields.categoryId,
                historicalSnapshot = options.categorySnapshot.ifBlank { null },
                allowDetachedSnapshot = true,
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
        if(attachmentRepository!=null) {
            attachmentBusy.value=true
            viewModelScope.launch {
                try {
                    val saved=savedStateHandle.get<String>("attachment_draft")
                    _attachments.value=if(saved!=null) (StrictJson.parse(saved) as List<*>).map { item ->
                        val ref=AttachmentRef.parse(item); val o=item.jsonObject()
                        AttachmentRecord(TransactionAttachmentEntity(ref.attachmentUuid,"",ref.blobUuid,ref.originalFileName,ref.sortOrder,true,o.long("createdAtMillis")),
                            ref.sha256,ref.sizeBytes,ref.mimeType,o.string("internalStorageKey"))
                    } else if(initialEditingId>0) attachmentRepository.getTransaction(initialEditingId)?.let {
                        attachmentRepository.activeAttachments(it.transactionUuid)
                    }.orEmpty() else emptyList()
                    persistAttachments()
                } catch(e: Exception) {
                    if(e is CancellationException) throw e
                    attachmentLoadFailed.value=true
                    attachmentMessage.value="附件加载失败，请返回后重试，已禁止保存以保护原附件"
                }
                finally { attachmentBusy.value=false }
            }
        }
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
            savedStateHandle[snapshotKey] = ""
            return
        }
        val current = savedStateHandle.get<Long>(idKey) ?: NONE_ID
        val historicalSelected = chips().any { it.historical && it.selected }
        val selectedId = if (current == chip.id && !historicalSelected) NONE_ID else chip.id
        savedStateHandle[idKey] = selectedId
        if (selectedId == chip.id) {
            savedStateHandle[snapshotKey] = chip.label
        } else savedStateHandle[snapshotKey] = ""
    }

    fun onSourceChange(value: String) {
        if (_isSaving.value) return
        savedStateHandle[KEY_SOURCE] = AttachmentPolicy.truncateText(value,LedgerRepository.MAX_SOURCE_LENGTH)
    }

    fun onNoteChange(value: String) {
        if (_isSaving.value) return
        savedStateHandle[KEY_NOTE] = AttachmentPolicy.truncateText(value,LedgerRepository.MAX_NOTE_LENGTH)
    }

    fun setDate(date: LocalDate) {
        if (_isSaving.value) return
        savedStateHandle[KEY_EPOCH_DAY] = date.toEpochDay()
    }

    fun save() {
        if(attachmentBusy.value || attachmentLoadFailed.value) return
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
                    val savedId = saveTransaction(editId, if(attachmentRepository==null) draft else draft.copy(attachments=_attachments.value), clientOpKey)
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
