package com.geo.ledger.util

import com.geo.ledger.data.local.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyParserTest {
    @Test
    fun parsesSupportedFormsExactlyAsCents() {
        assertEquals(1L, MoneyParser.parseCents("0.01"))
        assertEquals(10L, MoneyParser.parseCents("0.1"))
        assertEquals(12_800L, MoneyParser.parseCents("128"))
        assertEquals(12_800L, MoneyParser.parseCents("128."))
        assertEquals(12_800L, MoneyParser.parseCents("128.00"))
        assertEquals(50L, MoneyParser.parseCents(".5"))
        assertEquals(99_999_999_999L, MoneyParser.parseCents("999999999.99"))
        assertEquals(Long.MAX_VALUE - 1, MoneyParser.parseCents("92233720368547758.06"))
    }

    @Test
    fun rejectsInvalidZeroNegativeExponentAndOverflowInputs() {
        listOf("", "0", "0.00", "-1", "1.001", "abc", "NaN", "Infinity", "1e3", "+1", ".", "92233720368547758.08")
            .forEach { assertNull("Expected rejection for $it", MoneyParser.parseCents(it)) }
    }

    @Test
    fun draftLengthCapRejectsOverlongInput() {
        val over = "1".repeat(MoneyParser.MAX_DRAFT_LENGTH + 1)
        assertEquals(MoneyParser.DraftIssue.TooLong, MoneyParser.draftIssue(over))
        assertFalse(MoneyParser.isAllowedDraftInput(over))
        assertTrue(MoneyParser.isAllowedDraftInput("128.00"))
    }

    @Test
    fun formatsWithoutFloatingPoint() {
        assertEquals("¥0.01", MoneyFormatter.unsigned(1))
        assertEquals("¥999,999,999.99", MoneyFormatter.unsigned(99_999_999_999L))
        assertEquals("+¥5,000.00", MoneyFormatter.transaction(TransactionType.INCOME, 500_000))
        assertEquals("-¥128.00", MoneyFormatter.transaction(TransactionType.EXPENSE, 12_800))
    }
}
