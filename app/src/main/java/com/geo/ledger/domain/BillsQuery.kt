package com.geo.ledger.domain

import com.geo.ledger.util.GeoDates
import java.time.LocalDate
import java.time.YearMonth

enum class BillsPeriodMode {
    DAY,
    MONTH,
    YEAR,
    CUSTOM,
}

data class DateGroup(
    val date: LocalDate,
    val label: String,
    val entries: List<LedgerEntry>,
)

object BillsQuery {
    fun range(
        mode: BillsPeriodMode,
        day: LocalDate,
        month: YearMonth,
        year: Int,
        customStart: LocalDate,
        customEnd: LocalDate,
    ): DateRange? = when (mode) {
        BillsPeriodMode.DAY -> GeoDates.day(day)
        BillsPeriodMode.MONTH -> GeoDates.month(month)
        BillsPeriodMode.YEAR -> GeoDates.year(year)
        // Fail closed: inverted custom bounds are rejected, never min/max-swapped.
        BillsPeriodMode.CUSTOM -> customRange(customStart, customEnd)
    }

    fun customRange(start: LocalDate, endInclusive: LocalDate): DateRange? =
        if (start.isAfter(endInclusive)) null else DateRange(start, endInclusive)

    fun groups(entries: List<LedgerEntry>, range: DateRange): List<DateGroup> {
        val includeYear = range.start.year != range.endInclusive.year
        val withinDay = compareBy(LedgerCalculator.stableAscendingComparator) { entry: LedgerEntry ->
            entry.transaction
        }.reversed()
        return entries
            .asSequence()
            .filter { range.contains(it.transaction.transactionDate) }
            .groupBy { LocalDate.ofEpochDay(it.transaction.transactionDate) }
            .toSortedMap(compareByDescending { it })
            .map { (date, grouped) ->
                DateGroup(
                    date = date,
                    label = if (includeYear) GeoDates.formatDate(date) else GeoDates.formatGroup(date),
                    entries = grouped.sortedWith(withinDay),
                )
            }
    }

    fun periodLabel(
        mode: BillsPeriodMode,
        day: LocalDate,
        month: YearMonth,
        year: Int,
        range: DateRange,
    ): String = when (mode) {
        BillsPeriodMode.DAY -> GeoDates.formatDate(day)
        BillsPeriodMode.MONTH -> GeoDates.formatMonth(month)
        BillsPeriodMode.YEAR -> GeoDates.formatYear(year)
        BillsPeriodMode.CUSTOM -> GeoDates.formatRange(range)
    }
}
