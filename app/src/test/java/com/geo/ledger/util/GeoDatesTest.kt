package com.geo.ledger.util

import com.geo.ledger.domain.BillsPeriodMode
import com.geo.ledger.domain.BillsQuery
import com.geo.ledger.domain.DateRange
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoDatesTest {
    @Test
    fun dayMonthAndYearRangesAreInclusiveForJan31LeapFeb29AndDec31() {
        val jan31 = LocalDate.of(2026, 1, 31)
        val day = GeoDates.day(jan31)
        assertEquals(jan31, day.start)
        assertEquals(jan31, day.endInclusive)
        assertTrue(day.contains(jan31.toEpochDay()))
        assertFalse(day.contains(LocalDate.of(2026, 1, 30).toEpochDay()))
        assertFalse(day.contains(LocalDate.of(2026, 2, 1).toEpochDay()))

        val january = GeoDates.month(YearMonth.of(2026, 1))
        assertEquals(LocalDate.of(2026, 1, 1), january.start)
        assertEquals(jan31, january.endInclusive)
        assertTrue(january.contains(jan31.toEpochDay()))
        assertFalse(january.contains(LocalDate.of(2026, 2, 1).toEpochDay()))

        val leapFeb = GeoDates.month(YearMonth.of(2024, 2))
        assertEquals(LocalDate.of(2024, 2, 1), leapFeb.start)
        assertEquals(LocalDate.of(2024, 2, 29), leapFeb.endInclusive)
        assertTrue(leapFeb.contains(LocalDate.of(2024, 2, 29).toEpochDay()))
        assertFalse(leapFeb.contains(LocalDate.of(2024, 3, 1).toEpochDay()))

        val nonLeapFeb = GeoDates.month(YearMonth.of(2025, 2))
        assertEquals(LocalDate.of(2025, 2, 28), nonLeapFeb.endInclusive)
        assertFalse(nonLeapFeb.contains(LocalDate.of(2025, 3, 1).toEpochDay()))

        val year2026 = GeoDates.year(2026)
        assertEquals(LocalDate.of(2026, 1, 1), year2026.start)
        assertEquals(LocalDate.of(2026, 12, 31), year2026.endInclusive)
        assertTrue(year2026.contains(LocalDate.of(2026, 12, 31).toEpochDay()))
        assertFalse(year2026.contains(LocalDate.of(2025, 12, 31).toEpochDay()))
        assertFalse(year2026.contains(LocalDate.of(2027, 1, 1).toEpochDay()))

        val leapYear = GeoDates.year(2024)
        assertTrue(leapYear.contains(LocalDate.of(2024, 2, 29).toEpochDay()))
    }

    @Test
    fun monthShiftFromJanuaryKeepsFullFebruaryAndMarch() {
        val january = YearMonth.of(2026, 1)
        assertEquals(
            DateRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)),
            GeoDates.month(january.plusMonths(1)),
        )
        assertEquals(
            DateRange(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)),
            GeoDates.month(january.plusMonths(2)),
        )
        assertEquals(
            DateRange(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29)),
            GeoDates.month(YearMonth.of(2024, 1).plusMonths(1)),
        )

        val jan31 = LocalDate.of(2026, 1, 31)
        assertEquals(GeoDates.day(LocalDate.of(2026, 2, 1)), GeoDates.day(jan31.plusDays(1)))
        assertEquals(
            GeoDates.month(YearMonth.of(2026, 2)),
            BillsQuery.range(
                BillsPeriodMode.MONTH,
                jan31,
                YearMonth.of(2026, 1).plusMonths(1),
                2026,
                jan31,
                jan31,
            ),
        )
    }

    @Test
    fun corruptEpochAndPickerOverflowDoNotThrow() {
        assertEquals(null, GeoDates.fromEpochDayOrNull(Long.MAX_VALUE))
        assertEquals(null, GeoDates.fromEpochDayOrNull(Long.MIN_VALUE))
        assertEquals("", GeoDates.formatEpochDay(Long.MAX_VALUE))
        GeoDates.toPickerMillisOrNull(LocalDate.MAX)
        GeoDates.fromPickerMillisOrNull(Long.MAX_VALUE)
        assertEquals(LocalDate.of(2026, 9, 3), GeoDates.fromEpochDayOrNull(LocalDate.of(2026, 9, 3).toEpochDay()))
    }

    @Test
    fun pickerMillisRoundTripKeepsUtcCalendarDays() {
        listOf(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 31),
            LocalDate.of(2024, 2, 29),
            LocalDate.of(2025, 2, 28),
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 12, 31),
        ).forEach { date ->
            assertEquals(date, GeoDates.fromPickerMillis(GeoDates.toPickerMillis(date)))
        }
    }
}
