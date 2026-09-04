package com.geo.ledger.ui.addtransaction

import androidx.lifecycle.SavedStateHandle
import com.geo.ledger.data.LedgerRepository
import com.geo.ledger.data.local.PersonOptionEntity
import com.geo.ledger.data.local.TransactionType
import com.geo.ledger.domain.OptionChipModel
import com.geo.ledger.domain.TransactionDraft
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddTransactionViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun historicalChipClearsAndLiveChipReselectsSameId() = runTest(dispatcher) {
        val persons = MutableStateFlow(
            listOf(person(7L, "实验耗材")),
        )
        val viewModel = AddTransactionViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    AddTransactionViewModel.ARG_TRANSACTION_ID to 1L,
                    "editor_loaded" to true,
                    "editor_person_id" to 7L,
                    "editor_person_snapshot" to "耗材",
                    "editor_amount" to "1.00",
                    "editor_type" to TransactionType.EXPENSE.name,
                    "editor_epoch_day" to LocalDate.of(2026, 9, 3).toEpochDay(),
                ),
            ),
            activePersonsFlow = persons,
            activeCategoriesFlow = MutableStateFlow(emptyList()),
            getTransaction = { null },
            saveTransaction = { _, _, _ -> 1L },
            today = { LocalDate.of(2026, 9, 3) },
        )
        val job = launch { viewModel.uiState.collect {} }
        val chips = viewModel.uiState.value.personChips
        assertTrue("expected historical+live chips, got $chips", chips.any { it.historical && it.selected })
        val historical = chips.first { it.historical }
        viewModel.togglePerson(viewModel.uiState.value.personChips.first { !it.historical })
        assertFalse(viewModel.uiState.value.personChips.any { it.historical })
        assertTrue(viewModel.uiState.value.personChips.single { it.id == 7L }.selected)
        viewModel.togglePerson(OptionChipModel(7L, "耗材", historical = true, selected = true))
        assertEquals(null, viewModel.uiState.value.personId)
        job.cancel()
    }

    @Test
    fun restoredNewEditorWithSameClientOpKeyDoesNotInsertAgain() = runTest(dispatcher) {
        val inserts = AtomicInteger(0)
        var storedId: Long? = null
        var storedKey: String? = null
        val saver: suspend (Long?, TransactionDraft, String?) -> Long = { id, _, key ->
            when {
                id != null && id > 0 -> id
                key != null && key == storedKey && storedId != null -> storedId!!
                else -> {
                    inserts.incrementAndGet()
                    storedKey = key
                    storedId = 88L
                    88L
                }
            }
        }
        val fields = mapOf(
            AddTransactionViewModel.ARG_TRANSACTION_ID to AddTransactionViewModel.NEW_TRANSACTION_ID,
            AddTransactionViewModel.KEY_CLIENT_OP to "op-1",
            "editor_amount" to "12.00",
            "editor_type" to TransactionType.EXPENSE.name,
            "editor_epoch_day" to LocalDate.of(2026, 9, 3).toEpochDay(),
        )
        val first = AddTransactionViewModel(
            savedStateHandle = SavedStateHandle(fields),
            activePersonsFlow = MutableStateFlow(emptyList()),
            activeCategoriesFlow = MutableStateFlow(emptyList()),
            getTransaction = { null },
            saveTransaction = saver,
            today = { LocalDate.of(2026, 9, 3) },
        )
        val job1 = launch { first.uiState.collect {} }
        first.save()
        job1.cancel()
        val restored = AddTransactionViewModel(
            savedStateHandle = SavedStateHandle(fields),
            activePersonsFlow = MutableStateFlow(emptyList()),
            activeCategoriesFlow = MutableStateFlow(emptyList()),
            getTransaction = { null },
            saveTransaction = saver,
            today = { LocalDate.of(2026, 9, 3) },
        )
        val job2 = launch { restored.uiState.collect {} }
        restored.save()
        assertEquals(1, inserts.get())
        assertEquals(88L, storedId)
        job2.cancel()
    }

    @Test
    fun amountCapTruncatesOverlongDraft() = runTest(dispatcher) {
        val viewModel = AddTransactionViewModel(
            savedStateHandle = SavedStateHandle(),
            activePersonsFlow = MutableStateFlow(emptyList()),
            activeCategoriesFlow = MutableStateFlow(emptyList()),
            getTransaction = { null },
            saveTransaction = { _, _, _ -> 1L },
            today = { LocalDate.of(2026, 9, 3) },
        )
        val job = launch { viewModel.uiState.collect {} }
        viewModel.onAmountChange("1".repeat(30))
        assertTrue(viewModel.uiState.value.amountRaw.length <= com.geo.ledger.util.MoneyParser.MAX_DRAFT_LENGTH)
        viewModel.onSourceChange("s".repeat(LedgerRepository.MAX_SOURCE_LENGTH + 40))
        assertEquals(LedgerRepository.MAX_SOURCE_LENGTH, viewModel.uiState.value.source.length)
        viewModel.onNoteChange("n".repeat(LedgerRepository.MAX_NOTE_LENGTH + 50))
        assertEquals(LedgerRepository.MAX_NOTE_LENGTH, viewModel.uiState.value.note.length)
        job.cancel()
    }

    private fun person(id: Long, name: String) = PersonOptionEntity(
        id = id,
        name = name,
        isActive = true,
        activeNameKey = name,
        sortOrder = 0,
        createdAtMillis = 1,
        updatedAtMillis = 1,
    )
}
