package com.geo.ledger.domain

import com.geo.ledger.data.local.TransactionType
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerObservationPipelineTest {
    @Test
    fun observeRunsOnComputationDispatcherNotCollector() = runTest {
        val computation = StandardTestDispatcher(testScheduler)
        val rows = MutableStateFlow(listOf(ledgerTx(1, "2026-09-01", 12_800, TransactionType.INCOME)))
        val collected = mutableListOf<LedgerObservation>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            LedgerObservationPipeline.observe(rows, computation).collect { collected += it }
        }

        assertTrue(collected.isEmpty())
        testScheduler.runCurrent()
        val ready = collected.single() as LedgerObservation.Ready
        assertEquals(12_800L, ready.entries.single().balanceAfterCents)
        job.cancel()
    }

    @Test
    fun sharedCollectorsReuseOneObservationInstance() = runTest {
        val computation = UnconfinedTestDispatcher(testScheduler)
        val rows = MutableStateFlow(listOf(ledgerTx(1, "2026-09-01", 5_000, TransactionType.INCOME)))
        var maps = 0
        val pipeline = LedgerObservationPipeline.observe(
            rows = rows,
            computation = computation,
            sharingScope = backgroundScope,
            observeRows = { list ->
                maps += 1
                LedgerObserver.observe(list)
            },
        )
        val first = async { pipeline.first() }
        val second = async { pipeline.first() }
        val a = first.await()
        val b = second.await()
        assertEquals(1, maps)
        assertSame(a, b)
        assertEquals(5_000L, (a as LedgerObservation.Ready).entries.single().balanceAfterCents)
    }

    @Test
    fun equalRowSnapshotsDoNotRecompute() = runTest {
        val computation = UnconfinedTestDispatcher(testScheduler)
        val tx = ledgerTx(1, "2026-09-01", 9_000, TransactionType.INCOME)
        val rows = MutableStateFlow(listOf(tx))
        var maps = 0
        val pipeline = LedgerObservationPipeline.observe(
            rows = rows,
            computation = computation,
            sharingScope = backgroundScope,
            observeRows = { list ->
                maps += 1
                LedgerObserver.observe(list)
            },
        )
        val collected = mutableListOf<LedgerObservation>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            pipeline.collect { collected += it }
        }
        assertEquals(1, maps)
        assertEquals(1, collected.size)
        rows.value = listOf(tx.copy())
        assertEquals(1, maps)
        assertEquals(1, collected.size)
        assertSame(collected[0], pipeline.first())
        rows.value = listOf(tx.copy(note = "changed"))
        assertEquals(2, maps)
        assertEquals(2, collected.size)
        assertEquals("changed", (collected[1] as LedgerObservation.Ready).entries.single().transaction.note)
        job.cancel()
    }

    @Test
    fun cancellationFromObservePropagates() = runTest {
        val computation = UnconfinedTestDispatcher(testScheduler)
        val rows = MutableSharedFlow<List<com.geo.ledger.data.local.TransactionEntity>>()
        val pipeline = LedgerObservationPipeline.observe(
            rows = rows,
            computation = computation,
            observeRows = { throw CancellationException("pipeline-cancel") },
        )
        val observed = mutableListOf<Throwable>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            try {
                pipeline.collect { }
            } catch (error: CancellationException) {
                observed += error
                throw error
            }
        }
        rows.emit(emptyList())
        assertTrue(job.isCancelled)
        assertTrue(observed.single() is CancellationException)
        assertEquals("pipeline-cancel", observed.single().message)
    }
}
