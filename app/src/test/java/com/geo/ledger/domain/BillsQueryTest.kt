package com.geo.ledger.domain

import com.geo.ledger.data.local.TransactionType
import com.geo.ledger.util.GeoDates
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillsQueryTest {
    @Test
    fun dayMonthYearAndInclusiveCrossYearCustomRanges() {
        val jan31 = LocalDate.of(2026, 1, 31)
        assertEquals(
            GeoDates.day(jan31),
            BillsQuery.range(BillsPeriodMode.DAY, jan31, YearMonth.of(2026, 1), 2026, jan31, jan31),
        )
        assertEquals(
            DateRange(LocalDate.of(2026, 1, 1), jan31),
            BillsQuery.range(BillsPeriodMode.MONTH, jan31, YearMonth.of(2026, 1), 2026, jan31, jan31),
        )
        assertEquals(
            DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)),
            BillsQuery.range(BillsPeriodMode.YEAR, jan31, YearMonth.of(2026, 1), 2026, jan31, jan31),
        )

        val start = LocalDate.of(2025, 12, 31)
        val end = LocalDate.of(2026, 1, 1)
        val custom = BillsQuery.customRange(start, end)!!
        assertEquals(start, custom.start)
        assertEquals(end, custom.endInclusive)
        assertTrue(custom.contains(start.toEpochDay()))
        assertTrue(custom.contains(end.toEpochDay()))
        assertFalse(custom.contains(LocalDate.of(2025, 12, 30).toEpochDay()))
        assertFalse(custom.contains(LocalDate.of(2026, 1, 2).toEpochDay()))
        assertEquals(
            custom,
            BillsQuery.range(BillsPeriodMode.CUSTOM, jan31, YearMonth.of(2026, 1), 2026, start, end),
        )
    }

    @Test
    fun customRangeRejectsStartAfterEndAndAcceptsSingleDay() {
        val start = LocalDate.of(2026, 9, 3)
        val end = LocalDate.of(2026, 9, 1)
        assertNull(BillsQuery.customRange(start, end))
        assertNull(
            BillsQuery.range(
                BillsPeriodMode.CUSTOM,
                jan31(),
                YearMonth.of(2026, 9),
                2026,
                start,
                end,
            ),
        )
        assertEquals(DateRange(end, end), BillsQuery.customRange(end, end))
        assertEquals(DateRange(end, start), BillsQuery.customRange(end, start))
        assertEquals(
            DateRange(end, start),
            BillsQuery.range(
                BillsPeriodMode.CUSTOM,
                jan31(),
                YearMonth.of(2026, 9),
                2026,
                end,
                start,
            ),
        )
    }

    private fun jan31(): LocalDate = LocalDate.of(2026, 1, 31)

    @Test
    fun groupsAreDateDescendingSameDayDescendingAndOmitOutsideRange() {
        val range = DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3))
        val entries = ledgerEntries(
            ledgerTx(1, "2026-08-31", 50_000, TransactionType.INCOME, created = 1),
            ledgerTx(2, "2026-09-01", 10_000, TransactionType.INCOME, created = 2),
            ledgerTx(5, "2026-09-03", 500, TransactionType.EXPENSE, created = 5),
            ledgerTx(3, "2026-09-03", 2_000, TransactionType.EXPENSE, created = 3),
            ledgerTx(4, "2026-09-03", 1_000, TransactionType.EXPENSE, created = 3),
            ledgerTx(6, "2026-09-04", 8_000, TransactionType.EXPENSE, created = 6),
        )

        val groups = BillsQuery.groups(entries, range)
        assertEquals(
            listOf(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 1)),
            groups.map { it.date },
        )
        assertEquals(listOf(5L, 4L, 3L), groups[0].entries.map { it.transaction.id })
        assertEquals(listOf(2L), groups[1].entries.map { it.transaction.id })
        assertTrue(groups.none { group -> group.entries.any { it.transaction.id == 1L || it.transaction.id == 6L } })
        assertEquals("9月3日", groups[0].label)
        assertEquals("9月1日", groups[1].label)

        val crossYear = BillsQuery.groups(
            entries,
            DateRange(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 1)),
        )
        assertTrue(crossYear.isEmpty())
        val labeled = BillsQuery.groups(
            ledgerEntries(ledgerTx(9, "2025-12-31", 1, TransactionType.INCOME)),
            DateRange(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 1)),
        )
        assertEquals("2025年12月31日", labeled.single().label)
    }
}
