package com.geo.ledger.ui.bills

import androidx.lifecycle.SavedStateHandle
import com.geo.ledger.data.local.TransactionType
import com.geo.ledger.domain.BillsPeriodMode
import com.geo.ledger.domain.BillsQuery
import com.geo.ledger.domain.LedgerObservation
import com.geo.ledger.domain.ledgerEntries
import com.geo.ledger.domain.ledgerTx
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BillsViewModelTest {
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
    fun setCustomRangeRejectsStartAfterEnd() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val viewModel = BillsViewModel(
            ledgerObservation = flowOf(LedgerObservation.Ready(emptyList())),
            savedStateHandle = handle,
            today = { LocalDate.of(2026, 9, 3) },
        )
        val job = launch { viewModel.uiState.collect {} }

        assertFalse(viewModel.setCustomRange(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 1)))
        assertEquals(BillsPeriodMode.MONTH, viewModel.uiState.value.mode)
        assertEquals(LocalDate.of(2026, 9, 1), viewModel.uiState.value.range!!.start)
        assertEquals(LocalDate.of(2026, 9, 30), viewModel.uiState.value.range!!.endInclusive)

        assertTrue(viewModel.setCustomRange(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 1)))
        assertEquals(BillsPeriodMode.CUSTOM, viewModel.uiState.value.mode)
        assertEquals(LocalDate.of(2025, 12, 31), viewModel.uiState.value.range!!.start)
        assertEquals(LocalDate.of(2026, 1, 1), viewModel.uiState.value.range!!.endInclusive)
        job.cancel()
    }

    @Test
    fun invertedCustomSavedStateDoesNotSwapRange() = runTest(dispatcher) {
        val start = LocalDate.of(2026, 9, 3)
        val end = LocalDate.of(2026, 9, 1)
        val viewModel = BillsViewModel(
            ledgerObservation = flowOf(LedgerObservation.Ready(emptyList())),
            savedStateHandle = SavedStateHandle(
                mapOf(
                    BillsViewModel.KEY_MODE to BillsPeriodMode.CUSTOM.name,
                    BillsViewModel.KEY_CUSTOM_START to start.toEpochDay(),
                    BillsViewModel.KEY_CUSTOM_END to end.toEpochDay(),
                    BillsViewModel.KEY_MONTH to "2026-09",
                ),
            ),
            today = { LocalDate.of(2026, 9, 3) },
        )
        val job = launch { viewModel.uiState.collect {} }

        assertNull(BillsQuery.range(BillsPeriodMode.CUSTOM, start, YearMonth.of(2026, 9), 2026, start, end))
        val state = viewModel.uiState.value
        assertEquals(BillsPeriodMode.CUSTOM, state.mode)
        assertTrue(state.invalidCustomRange)
        assertNull(state.range)
        assertTrue(state.isReady)
        assertFalse(state.ledgerError)
        assertEquals(0L, state.summary.incomeCents)
        assertEquals(0L, state.summary.expenseCents)
        job.cancel()
    }

    @Test
    fun coldStartIsNotReadyUntilObservationEmits() = runTest(dispatcher) {
        val viewModel = BillsViewModel(
            ledgerObservation = kotlinx.coroutines.flow.MutableSharedFlow(),
            savedStateHandle = SavedStateHandle(),
            today = { LocalDate.of(2026, 9, 3) },
        )
        assertFalse(viewModel.uiState.value.isReady)
        assertFalse(viewModel.uiState.value.ledgerError)
        assertTrue(viewModel.uiState.value.groups.isEmpty())
    }

    @Test
    fun periodTotalsOverflowDoesNotCrashCustomOrMonthMapping() = runTest(dispatcher) {
        val maxMinusOne = Long.MAX_VALUE - 1
        val entries = ledgerEntries(
            ledgerTx(1, "2026-09-01", maxMinusOne, TransactionType.INCOME),
            ledgerTx(2, "2026-09-02", maxMinusOne, TransactionType.EXPENSE),
            ledgerTx(3, "2026-09-03", maxMinusOne, TransactionType.INCOME),
        )
        val viewModel = BillsViewModel(
            ledgerObservation = flowOf(LedgerObservation.Ready(entries)),
            savedStateHandle = SavedStateHandle(),
            today = { LocalDate.of(2026, 9, 3) },
        )
        val job = launch { viewModel.uiState.collect {} }
        val state = viewModel.uiState.value
        assertEquals(LocalDate.of(2026, 9, 1), state.range!!.start)
        assertEquals(LocalDate.of(2026, 9, 30), state.range!!.endInclusive)
        assertTrue(state.ledgerError)
        assertEquals(0L, state.summary.incomeCents)
        assertEquals(0L, state.summary.expenseCents)
        assertEquals(0L, state.summary.endingBalanceCents)
        job.cancel()
    }

    @Test
    fun monthShiftFromJan31UsesFullFollowingMonths() = runTest(dispatcher) {
        val viewModel = BillsViewModel(
            ledgerObservation = MutableStateFlow(LedgerObservation.Ready(emptyList())),
            savedStateHandle = SavedStateHandle(),
            today = { LocalDate.of(2026, 1, 31) },
        )
        val job = launch { viewModel.uiState.collect {} }

        assertEquals(LocalDate.of(2026, 1, 1), viewModel.uiState.value.range!!.start)
        assertEquals(LocalDate.of(2026, 1, 31), viewModel.uiState.value.range!!.endInclusive)

        viewModel.shift(1)
        assertEquals(LocalDate.of(2026, 2, 1), viewModel.uiState.value.range!!.start)
        assertEquals(LocalDate.of(2026, 2, 28), viewModel.uiState.value.range!!.endInclusive)

        viewModel.shift(1)
        assertEquals(LocalDate.of(2026, 3, 1), viewModel.uiState.value.range!!.start)
        assertEquals(LocalDate.of(2026, 3, 31), viewModel.uiState.value.range!!.endInclusive)
        job.cancel()
    }
}
