package com.geo.ledger.domain

import com.geo.ledger.util.GeoDates
import java.time.LocalDate
import java.time.YearMonth

data class HomeSnapshot(
    val currentBalanceCents: Long,
    val monthIncomeCents: Long,
    val monthExpenseCents: Long,
    val recent: List<LedgerEntry>,
)

object HomeDashboard {
    const val RECENT_LIMIT = 5

    fun from(entries: List<LedgerEntry>, today: LocalDate): HomeSnapshot {
        val currentBalanceCents = entries
            .maxWithOrNull(compareBy(LedgerCalculator.stableAscendingComparator) { it.transaction })
            ?.balanceAfterCents
            ?: 0L
        val summary = LedgerCalculator.summary(entries, GeoDates.month(YearMonth.from(today)))
        return HomeSnapshot(
            currentBalanceCents = currentBalanceCents,
            monthIncomeCents = summary.incomeCents,
            monthExpenseCents = summary.expenseCents,
            recent = entries
                .sortedWith(compareBy(LedgerCalculator.stableAscendingComparator) { it.transaction })
                .asReversed()
                .take(RECENT_LIMIT),
        )
    }
}
