package com.geo.ledger.domain

import com.geo.ledger.data.local.TransactionEntity
import com.geo.ledger.util.GeoDates
import kotlin.coroutines.cancellation.CancellationException

sealed class LedgerObservation {
    data class Ready(val entries: List<LedgerEntry>) : LedgerObservation()
    data class Invalid(val cause: Throwable) : LedgerObservation()
}

object LedgerObserver {
    fun observe(rows: List<TransactionEntity>): LedgerObservation {
        return try {
            rows.forEach { row ->
                requireNotNull(GeoDates.fromEpochDayOrNull(row.transactionDate)) {
                    "Transaction date is out of range"
                }
            }
            LedgerObservation.Ready(LedgerCalculator.withRunningBalances(rows))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            LedgerObservation.Invalid(error)
        }
    }
}
