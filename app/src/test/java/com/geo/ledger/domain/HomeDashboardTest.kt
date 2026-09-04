package com.geo.ledger.domain

import com.geo.ledger.data.local.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HomeDashboardTest {
    @Test
    fun overflowPeriodTotalsThrowInsteadOfSilentZero() {
        val maxMinusOne = Long.MAX_VALUE - 1
        val entries = ledgerEntries(
            ledgerTx(1, "2026-09-01", maxMinusOne, TransactionType.INCOME),
            ledgerTx(2, "2026-09-02", maxMinusOne, TransactionType.EXPENSE),
            ledgerTx(3, "2026-09-03", maxMinusOne, TransactionType.INCOME),
        )
        assertThrows(ArithmeticException::class.java) {
            HomeDashboard.from(entries, LocalDate.of(2026, 9, 3))
        }
        assertEquals(maxMinusOne, entries.last().balanceAfterCents)
    }
}
