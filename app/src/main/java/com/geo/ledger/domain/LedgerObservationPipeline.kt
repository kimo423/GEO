package com.geo.ledger.domain

import com.geo.ledger.data.local.TransactionEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

/**
 * Room emits query results off the collector thread; [map] and [flowOn] placement
 * determines where [LedgerObserver.observe] / running balances actually run.
 */
object LedgerObservationPipeline {
    const val SHARE_TIMEOUT_MS = 5_000L

    fun observe(
        rows: Flow<List<TransactionEntity>>,
        computation: CoroutineDispatcher,
        sharingScope: CoroutineScope? = null,
        started: SharingStarted = SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS),
        observeRows: (List<TransactionEntity>) -> LedgerObservation = LedgerObserver::observe,
    ): Flow<LedgerObservation> {
        val computed = rows
            .distinctUntilChanged()
            .map(observeRows)
            .flowOn(computation)
        return if (sharingScope != null) {
            computed.shareIn(sharingScope, started, replay = 1)
        } else {
            computed
        }
    }
}
