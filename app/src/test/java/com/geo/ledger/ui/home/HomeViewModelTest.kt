package com.geo.ledger.ui.home

import com.geo.ledger.data.local.TransactionType
import com.geo.ledger.domain.LedgerObservation
import com.geo.ledger.domain.ledgerEntries
import com.geo.ledger.domain.ledgerTx
import com.geo.ledger.util.GeoDates
import java.time.LocalDate
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
class HomeViewModelTest {
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
    fun unreadObservationIsNotReadyAndNotError() {
        val viewModel = HomeViewModel(
            kotlinx.coroutines.flow.MutableSharedFlow(),
            today = { LocalDate.of(2026, 9, 3) },
            tickCalendar = false,
        )
        assertFalse(viewModel.uiState.value.isReady)
        assertFalse(viewModel.uiState.value.ledgerError)
        assertEquals(0L, viewModel.uiState.value.snapshot.currentBalanceCents)
    }

    @Test
    fun readyObservationKeepsCachedBalanceAndDateLabel() = runTest(dispatcher) {
        val today = LocalDate.of(2026, 9, 3)
        val entries = ledgerEntries(ledgerTx(1, "2026-09-01", 12_800, TransactionType.INCOME))
        val flow = MutableStateFlow<LedgerObservation>(LedgerObservation.Ready(entries))
        val viewModel = HomeViewModel(flow, today = { today }, tickCalendar = false)
        val job = launch { viewModel.uiState.collect {} }
        assertEquals(GeoDates.formatDate(today), viewModel.uiState.value.dateLabel)
        assertEquals(12_800L, viewModel.uiState.value.snapshot.currentBalanceCents)
        assertTrue(viewModel.uiState.value.isReady)
        assertFalse(viewModel.uiState.value.ledgerError)
        job.cancel()
    }

    @Test
    fun invalidObservationSetsLedgerErrorWithoutThrowing() = runTest(dispatcher) {
        val today = LocalDate.of(2026, 9, 3)
        val flow = MutableStateFlow<LedgerObservation>(LedgerObservation.Invalid(IllegalStateException("bad")))
        val viewModel = HomeViewModel(flow, today = { today }, tickCalendar = false)
        val job = launch { viewModel.uiState.collect {} }
        assertTrue(viewModel.uiState.value.ledgerError)
        assertEquals(0L, viewModel.uiState.value.snapshot.currentBalanceCents)
        job.cancel()
    }
}
