package com.geo.ledger.domain

import com.geo.ledger.data.local.TransactionEntity
import com.geo.ledger.data.local.TransactionType
import com.geo.ledger.util.GeoDates
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LedgerCalculatorTest {
    @Test
    fun testA_incomeThenExpense() {
        val result = LedgerCalculator.withRunningBalances(
            listOf(tx(1, "2026-09-01", 500_000, TransactionType.INCOME), tx(2, "2026-09-02", 12_800, TransactionType.EXPENSE)),
        )
        assertEquals(listOf(500_000L, 487_200L), result.map { it.balanceAfterCents })
    }

    @Test
    fun testB_backfilledTransactionReordersAndRecalculates() {
        val result = LedgerCalculator.withRunningBalances(
            listOf(
                tx(1, "2026-09-01", 100_000, TransactionType.INCOME),
                tx(2, "2026-09-03", 10_000, TransactionType.EXPENSE),
                tx(3, "2026-09-02", 20_000, TransactionType.EXPENSE),
            ),
        )
        assertEquals(listOf(100_000L, 80_000L, 70_000L), result.map { it.balanceAfterCents })
    }

    @Test
    fun testEAndF_periodTotalsAndEndingBalanceIncludeHistory() {
        val entries = LedgerCalculator.withRunningBalances(
            listOf(
                tx(1, "2026-08-31", 50_000, TransactionType.INCOME),
                tx(2, "2026-09-02", 100_000, TransactionType.INCOME),
                tx(3, "2026-09-03", 20_000, TransactionType.EXPENSE),
            ),
        )
        val summary = LedgerCalculator.summary(entries, GeoDates.month(YearMonth.of(2026, 9)))
        assertEquals(100_000L, summary.incomeCents)
        assertEquals(20_000L, summary.expenseCents)
        assertEquals(80_000L, summary.netChangeCents)
        assertEquals(130_000L, summary.endingBalanceCents)
    }

    @Test
    fun testGEditingOldAmountRecalculatesAllLaterBalances() {
        val edited = listOf(
            tx(1, "2026-09-01", 100_000, TransactionType.INCOME),
            tx(2, "2026-09-02", 40_000, TransactionType.EXPENSE),
            tx(3, "2026-09-03", 10_000, TransactionType.EXPENSE),
        )
        assertEquals(listOf(100_000L, 60_000L, 50_000L), LedgerCalculator.withRunningBalances(edited).map { it.balanceAfterCents })
    }

    @Test
    fun testHDeletingOldTransactionRecalculatesLaterBalance() {
        val remaining = listOf(
            tx(1, "2026-09-01", 100_000, TransactionType.INCOME),
            tx(3, "2026-09-03", 10_000, TransactionType.EXPENSE),
        )
        assertEquals(listOf(100_000L, 90_000L), LedgerCalculator.withRunningBalances(remaining).map { it.balanceAfterCents })
    }

    @Test
    fun testIChangingDateReordersTransactions() {
        val changed = listOf(
            tx(1, "2026-09-01", 100_000, TransactionType.INCOME),
            tx(2, "2026-08-31", 20_000, TransactionType.EXPENSE),
        )
        val result = LedgerCalculator.withRunningBalances(changed)
        assertEquals(listOf(2L, 1L), result.map { it.transaction.id })
        assertEquals(listOf(-20_000L, 80_000L), result.map { it.balanceAfterCents })
    }

    @Test
    fun testK_invalidCustomRangeRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            DateRange(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 1))
        }
    }

    @Test
    fun testM_sameDayOrderUsesCreatedTimeThenId() {
        val date = "2026-09-03"
        val input = listOf(
            tx(5, date, 500, TransactionType.EXPENSE, created = 4),
            tx(3, date, 2_000, TransactionType.EXPENSE, created = 2),
            tx(1, date, 10_000, TransactionType.INCOME, created = 1),
            tx(4, date, 5_000, TransactionType.INCOME, created = 3),
            tx(2, date, 1_000, TransactionType.EXPENSE, created = 2),
        )
        val result = LedgerCalculator.withRunningBalances(input)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), result.map { it.transaction.id })
        assertEquals(listOf(10_000L, 9_000L, 7_000L, 12_000L, 11_500L), result.map { it.balanceAfterCents })
    }

    @Test
    fun rejectsAggregateLongOverflowInsteadOfWrappingBalance() {
        val input = listOf(
            tx(1, "2026-09-01", Long.MAX_VALUE, TransactionType.INCOME),
            tx(2, "2026-09-02", 1, TransactionType.INCOME),
        )
        assertThrows(ArithmeticException::class.java) { LedgerCalculator.withRunningBalances(input) }
    }

    @Test
    fun alternatingMaxMinusOneKeepsRunningBalanceButOverflowsPeriodAndHistoryTotals() {
        val maxMinusOne = Long.MAX_VALUE - 1
        val input = listOf(
            tx(1, "2026-09-01", maxMinusOne, TransactionType.INCOME),
            tx(2, "2026-09-02", maxMinusOne, TransactionType.EXPENSE),
            tx(3, "2026-09-03", maxMinusOne, TransactionType.INCOME),
        )
        val entries = LedgerCalculator.withRunningBalances(input)
        assertEquals(listOf(maxMinusOne, 0L, maxMinusOne), entries.map { it.balanceAfterCents })
        assertThrows(ArithmeticException::class.java) {
            LedgerCalculator.summary(entries, GeoDates.month(YearMonth.of(2026, 9)))
        }
        assertThrows(ArithmeticException::class.java) {
            LedgerCalculator.requireExactAggregates(input)
        }
        assertThrows(ArithmeticException::class.java) {
            LedgerCalculator.validateCandidateHistory(input)
        }
    }

    @Test
    fun validateCandidateHistoryRejectsUpdateAndDateMoveThatOverflowAggregates() {
        val maxMinusOne = Long.MAX_VALUE - 1
        val accepted = listOf(
            tx(1, "2026-08-01", maxMinusOne, TransactionType.INCOME),
            tx(2, "2026-09-02", maxMinusOne, TransactionType.EXPENSE),
        )
        val acceptedEntries = LedgerCalculator.validateCandidateHistory(accepted)
        assertEquals(listOf(maxMinusOne, 0L), acceptedEntries.map { it.balanceAfterCents })

        val dateMoved = listOf(
            tx(1, "2026-09-01", maxMinusOne, TransactionType.INCOME),
            tx(2, "2026-09-02", maxMinusOne, TransactionType.EXPENSE),
        )
        val movedEntries = LedgerCalculator.validateCandidateHistory(dateMoved)
        assertEquals(listOf(1L, 2L), movedEntries.map { it.transaction.id })
        val movedSummary = LedgerCalculator.summary(movedEntries, GeoDates.month(YearMonth.of(2026, 9)))
        assertEquals(maxMinusOne, movedSummary.incomeCents)
        assertEquals(maxMinusOne, movedSummary.expenseCents)
        assertEquals(0L, movedSummary.netChangeCents)
        assertEquals(0L, movedSummary.endingBalanceCents)

        val updatedToSecondIncome = listOf(
            tx(1, "2026-09-01", maxMinusOne, TransactionType.INCOME),
            tx(2, "2026-09-02", maxMinusOne, TransactionType.INCOME),
        )
        assertThrows(ArithmeticException::class.java) {
            LedgerCalculator.validateCandidateHistory(updatedToSecondIncome)
        }
        assertThrows(ArithmeticException::class.java) {
            LedgerCalculator.validateCandidateHistory(
                accepted + tx(3, "2026-09-03", maxMinusOne, TransactionType.INCOME),
            )
        }
    }

    @Test
    fun summaryEndingBalanceUsesStableOrderForUnsortedInput() {
        val t1 = tx(1, "2026-08-31", 50_000, TransactionType.INCOME)
        val t2 = tx(2, "2026-09-02", 100_000, TransactionType.INCOME)
        val t3 = tx(3, "2026-09-03", 20_000, TransactionType.EXPENSE)
        val unsorted = listOf(
            LedgerEntry(t3, 130_000),
            LedgerEntry(t1, 50_000),
            LedgerEntry(t2, 150_000),
        )
        val summary = LedgerCalculator.summary(unsorted, GeoDates.month(YearMonth.of(2026, 9)))
        assertEquals(100_000L, summary.incomeCents)
        assertEquals(20_000L, summary.expenseCents)
        assertEquals(80_000L, summary.netChangeCents)
        assertEquals(130_000L, summary.endingBalanceCents)
    }

    @Test
    fun leapDayAndCrossYearRangesAreInclusive() {
        assertEquals(
            LocalDate.of(2024, 2, 29),
            GeoDates.month(YearMonth.of(2024, 2)).endInclusive,
        )
        val range = DateRange(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 1))
        assertEquals(true, range.contains(LocalDate.of(2026, 1, 1).toEpochDay()))
    }

    @Test
    fun emptyOnlyIncomeOnlyExpenseAndMixedPeriodTotals() {
        val september = GeoDates.month(YearMonth.of(2026, 9))
        val empty = LedgerCalculator.summary(emptyList(), september)
        assertEquals(0L, empty.incomeCents)
        assertEquals(0L, empty.expenseCents)
        assertEquals(0L, empty.netChangeCents)
        assertEquals(0L, empty.endingBalanceCents)

        val onlyIncome = LedgerCalculator.summary(
            ledgerEntries(ledgerTx(1, "2026-09-10", 80_000, TransactionType.INCOME)),
            september,
        )
        assertEquals(80_000L, onlyIncome.incomeCents)
        assertEquals(0L, onlyIncome.expenseCents)
        assertEquals(80_000L, onlyIncome.netChangeCents)
        assertEquals(80_000L, onlyIncome.endingBalanceCents)

        val onlyExpense = LedgerCalculator.summary(
            ledgerEntries(ledgerTx(1, "2026-09-10", 12_800, TransactionType.EXPENSE)),
            september,
        )
        assertEquals(0L, onlyExpense.incomeCents)
        assertEquals(12_800L, onlyExpense.expenseCents)
        assertEquals(-12_800L, onlyExpense.netChangeCents)
        assertEquals(-12_800L, onlyExpense.endingBalanceCents)

        val mixed = LedgerCalculator.summary(
            ledgerEntries(
                ledgerTx(1, "2026-09-01", 100_000, TransactionType.INCOME),
                ledgerTx(2, "2026-09-02", 20_000, TransactionType.EXPENSE),
                ledgerTx(3, "2026-09-03", 5_000, TransactionType.EXPENSE),
            ),
            september,
        )
        assertEquals(100_000L, mixed.incomeCents)
        assertEquals(25_000L, mixed.expenseCents)
        assertEquals(75_000L, mixed.netChangeCents)
        assertEquals(75_000L, mixed.endingBalanceCents)
    }

    @Test
    fun endingBalanceIncludesHistoryWhenRangeIsEmptyOrHasLaterTransactions() {
        val entries = ledgerEntries(
            ledgerTx(1, "2026-08-31", 50_000, TransactionType.INCOME),
            ledgerTx(2, "2026-09-02", 100_000, TransactionType.INCOME),
            ledgerTx(3, "2026-09-03", 20_000, TransactionType.EXPENSE),
            ledgerTx(4, "2026-10-01", 5_000, TransactionType.EXPENSE),
        )
        val emptyJuly = LedgerCalculator.summary(entries, GeoDates.month(YearMonth.of(2026, 7)))
        assertEquals(0L, emptyJuly.incomeCents)
        assertEquals(0L, emptyJuly.expenseCents)
        assertEquals(0L, emptyJuly.netChangeCents)
        assertEquals(0L, emptyJuly.endingBalanceCents)

        val augustWithNoInRangeTx = LedgerCalculator.summary(
            entries,
            DateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 30)),
        )
        assertEquals(0L, augustWithNoInRangeTx.incomeCents)
        assertEquals(0L, augustWithNoInRangeTx.expenseCents)
        assertEquals(0L, augustWithNoInRangeTx.netChangeCents)
        assertEquals(0L, augustWithNoInRangeTx.endingBalanceCents)

        val augustThrough31 = LedgerCalculator.summary(
            entries,
            DateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)),
        )
        assertEquals(50_000L, augustThrough31.incomeCents)
        assertEquals(50_000L, augustThrough31.endingBalanceCents)

        val emptySeptemberDay = LedgerCalculator.summary(
            entries,
            GeoDates.day(LocalDate.of(2026, 9, 1)),
        )
        assertEquals(0L, emptySeptemberDay.incomeCents)
        assertEquals(0L, emptySeptemberDay.expenseCents)
        assertEquals(0L, emptySeptemberDay.netChangeCents)
        assertEquals(50_000L, emptySeptemberDay.endingBalanceCents)

        val september = LedgerCalculator.summary(entries, GeoDates.month(YearMonth.of(2026, 9)))
        assertEquals(100_000L, september.incomeCents)
        assertEquals(20_000L, september.expenseCents)
        assertEquals(80_000L, september.netChangeCents)
        assertEquals(130_000L, september.endingBalanceCents)
    }

    private fun tx(
        id: Long,
        date: String,
        amount: Long,
        type: TransactionType,
        created: Long = id,
    ) = TransactionEntity(
        id = id,
        type = type,
        amountCents = amount,
        transactionDate = LocalDate.parse(date).toEpochDay(),
        createdAtMillis = created,
        updatedAtMillis = created,
    )
}
