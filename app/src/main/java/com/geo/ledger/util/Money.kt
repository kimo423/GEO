package com.geo.ledger.util

import com.geo.ledger.data.local.TransactionType
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object MoneyParser {
    /** Longest accepted amount draft, covering Long.MAX_VALUE-1 cents: 92233720368547758.06 */
    const val MAX_DRAFT_LENGTH = 20

    private val validPattern = Regex("^(?:[0-9]+(?:\\.[0-9]{0,2})?|\\.[0-9]{1,2})$")
    private val draftPattern = Regex("^(?:[0-9]+(?:\\.[0-9]{0,2})?|\\.[0-9]{0,2})$")

    enum class DraftIssue {
        None,
        Empty,
        TooLong,
        Invalid,
    }

    fun draftIssue(input: String): DraftIssue {
        val value = input.trim()
        if (value.isEmpty()) return DraftIssue.Empty
        if (value.length > MAX_DRAFT_LENGTH) return DraftIssue.TooLong
        if (parseCents(value) == null) return DraftIssue.Invalid
        return DraftIssue.None
    }

    fun parseCents(input: String): Long? {
        val value = input.trim()
        if (!validPattern.matches(value)) return null
        val parts = value.split('.', limit = 2)
        val wholePart = parts[0].ifEmpty { "0" }
        val fractionPart = parts.getOrElse(1) { "" }.padEnd(2, '0')
        return try {
            val whole = wholePart.toLong()
            val fraction = fractionPart.ifEmpty { "00" }.toLong()
            val cents = Math.addExact(Math.multiplyExact(whole, 100L), fraction)
            cents.takeIf { it >= 1 }
        } catch (_: NumberFormatException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }

    fun isAllowedDraftInput(input: String): Boolean {
        val value = input.trim()
        if (value.isEmpty()) return true
        if (value.any { it == '-' || it == '+' || it == ',' || it == 'e' || it == 'E' }) return false
        if (!draftPattern.matches(value)) return false
        return value.length <= MAX_DRAFT_LENGTH
    }
}

object MoneyFormatter {
    private fun decimalFormat(): DecimalFormat = DecimalFormat(
        "#,##0.00",
        DecimalFormatSymbols(Locale.CHINA),
    ).apply {
        isParseBigDecimal = true
    }

    fun unsigned(cents: Long): String = "¥${decimalFormat().format(BigDecimal.valueOf(cents, 2).abs())}"

    fun plain(cents: Long): String = when {
        cents < 0 -> "-${unsigned(cents)}"
        else -> unsigned(cents)
    }

    fun signed(cents: Long): String = when {
        cents > 0 -> "+${unsigned(cents)}"
        cents < 0 -> "-${unsigned(cents)}"
        else -> unsigned(0)
    }

    fun transaction(type: TransactionType, amountCents: Long): String = when (type) {
        TransactionType.INCOME -> "+${unsigned(amountCents)}"
        TransactionType.EXPENSE -> "-${unsigned(amountCents)}"
    }

    fun editable(cents: Long): String {
        val whole = cents / 100
        val fraction = (cents % 100).toInt()
        return String.format(Locale.US, "%d.%02d", whole, fraction)
    }
}
