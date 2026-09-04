package com.geo.ledger.util

import com.geo.ledger.domain.DateRange
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

object GeoDates {
    private val fullChinese = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)
    private val monthChinese = DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)
    private val yearChinese = DateTimeFormatter.ofPattern("yyyy年", Locale.CHINA)
    private val groupChinese = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
    private val compact = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.CHINA)

    fun day(date: LocalDate): DateRange = DateRange(date, date)

    fun month(month: YearMonth): DateRange = DateRange(month.atDay(1), month.atEndOfMonth())

    fun year(year: Int): DateRange = DateRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))

    fun formatDate(date: LocalDate): String = fullChinese.format(date)

    fun formatMonth(month: YearMonth): String = monthChinese.format(month)

    fun formatYear(year: Int): String = yearChinese.format(LocalDate.of(year, 1, 1))

    fun formatGroup(date: LocalDate): String = groupChinese.format(date)

    fun formatCompact(date: LocalDate): String = compact.format(date)

    fun formatRange(range: DateRange): String = "${formatCompact(range.start)} — ${formatCompact(range.endInclusive)}"

    fun fromEpochDayOrNull(epochDay: Long): LocalDate? = try {
        LocalDate.ofEpochDay(epochDay)
    } catch (_: DateTimeException) {
        null
    }

    fun formatEpochDay(epochDay: Long): String =
        fromEpochDayOrNull(epochDay)?.let(::formatDate).orEmpty()

    // Material date pickers communicate UTC-based day millis. Database persistence remains epochDay.
    fun toPickerMillis(date: LocalDate): Long =
        toPickerMillisOrNull(date) ?: error("Date is out of picker range")

    fun toPickerMillisOrNull(date: LocalDate): Long? = try {
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    } catch (_: DateTimeException) {
        null
    } catch (_: ArithmeticException) {
        null
    }

    fun fromPickerMillis(millis: Long): LocalDate =
        fromPickerMillisOrNull(millis) ?: error("Picker millis is out of range")

    fun fromPickerMillisOrNull(millis: Long): LocalDate? = try {
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
    } catch (_: DateTimeException) {
        null
    } catch (_: ArithmeticException) {
        null
    }
}
