package com.geo.ledger.ui.transactiondetail

import androidx.lifecycle.SavedStateHandle
import com.geo.ledger.R
import com.geo.ledger.data.local.TransactionEntity
import com.geo.ledger.data.local.TransactionType
import com.geo.ledger.domain.LedgerEntry
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
class TransactionDetailViewModelTest {
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
    fun deleteSuccessEmitsOnceAndLeavesDeletedState() = runTest(dispatcher) {
        val deletes = AtomicInteger(0)
        val entry = sampleEntry()
        val viewModel = TransactionDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf(TransactionDetailViewModel.ARG_TRANSACTION_ID to 11L)),
            observeTransaction = { MutableStateFlow(entry) },
            deleteTransaction = { deletes.incrementAndGet() },
        )
        val events = mutableListOf<TransactionDetailEvent>()
        val job = launch {
            launch { viewModel.uiState.collect {} }
            launch { viewModel.events.collect { events.add(it) } }
        }
        assertTrue(viewModel.uiState.value is TransactionDetailUiState.Content)
        viewModel.delete()
        assertEquals(listOf(TransactionDetailEvent.Deleted), events)
        assertEquals(TransactionDetailUiState.Deleted, viewModel.uiState.value)
        viewModel.delete()
        assertEquals(1, deletes.get())
        job.cancel()
    }

    @Test
    fun deleteFailureClearsLoadingAndAllowsRetry() = runTest(dispatcher) {
        val deletes = AtomicInteger(0)
        val entry = sampleEntry()
        val viewModel = TransactionDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf(TransactionDetailViewModel.ARG_TRANSACTION_ID to 11L)),
            observeTransaction = { MutableStateFlow(entry) },
            deleteTransaction = {
                deletes.incrementAndGet()
                error("db")
            },
        )
        val events = mutableListOf<TransactionDetailEvent>()
        val job = launch {
            launch { viewModel.uiState.collect {} }
            launch { viewModel.events.collect { events.add(it) } }
        }
        assertTrue(viewModel.uiState.value is TransactionDetailUiState.Content)
        viewModel.delete()
        val failed = events.single() as TransactionDetailEvent.Failed
        assertEquals(R.string.error_delete_failed, failed.messageRes)
        val content = viewModel.uiState.value as TransactionDetailUiState.Content
        assertFalse(content.isDeleting)
        viewModel.delete()
        assertEquals(2, deletes.get())
        job.cancel()
    }

    private fun sampleEntry() = LedgerEntry(
        transaction = TransactionEntity(
            id = 11L,
            type = TransactionType.EXPENSE,
            amountCents = 100L,
            transactionDate = LocalDate.of(2026, 9, 3).toEpochDay(),
            createdAtMillis = 1L,
            updatedAtMillis = 1L,
        ),
        balanceAfterCents = 100L,
    )
}
