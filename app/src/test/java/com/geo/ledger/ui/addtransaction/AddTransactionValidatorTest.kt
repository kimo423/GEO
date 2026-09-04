package com.geo.ledger.ui.addtransaction

import com.geo.ledger.data.LedgerRepository
import com.geo.ledger.data.local.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AddTransactionValidatorTest {
    private val today = LocalDate.of(2026, 9, 3).toEpochDay()

    @Test
    fun parsesValidAmountThroughMoneyParser() {
        assertEquals(12_800L, AddTransactionValidator.parsedAmountCents("128"))
        assertEquals(12_800L, AddTransactionValidator.parsedAmountCents("128.00"))
        assertEquals(1L, AddTransactionValidator.parsedAmountCents("0.01"))
        assertNull(AddTransactionValidator.parsedAmountCents(""))
        assertNull(AddTransactionValidator.parsedAmountCents("0"))
        assertNull(AddTransactionValidator.parsedAmountCents("1.001"))
        assertNull(AddTransactionValidator.parsedAmountCents("-1"))
    }

    @Test
    fun canSaveRequiresPositiveAmountValidDateAndMaxLengths() {
        assertTrue(
            AddTransactionValidator.canSave(
                type = TransactionType.EXPENSE,
                amountRaw = "128.00",
                source = "",
                note = "",
                epochDay = today,
            ),
        )
        assertFalse(
            AddTransactionValidator.canSave(
                type = TransactionType.EXPENSE,
                amountRaw = "0",
                source = "",
                note = "",
                epochDay = today,
            ),
        )
        assertFalse(
            AddTransactionValidator.canSave(
                type = TransactionType.EXPENSE,
                amountRaw = "10",
                source = "",
                note = "",
                epochDay = Long.MAX_VALUE,
            ),
        )
        assertFalse(
            AddTransactionValidator.canSave(
                type = TransactionType.EXPENSE,
                amountRaw = "10",
                source = "",
                note = "n".repeat(LedgerRepository.MAX_NOTE_LENGTH + 1),
                epochDay = today,
            ),
        )
        assertTrue(
            AddTransactionValidator.canSave(
                type = TransactionType.EXPENSE,
                amountRaw = "10",
                source = "",
                note = "n".repeat(LedgerRepository.MAX_NOTE_LENGTH),
                epochDay = today,
            ),
        )
    }

    @Test
    fun incomeRejectsOversizedSourceWhileExpenseIgnoresSourceLength() {
        val tooLong = "s".repeat(LedgerRepository.MAX_SOURCE_LENGTH + 1)
        assertFalse(
            AddTransactionValidator.canSave(
                type = TransactionType.INCOME,
                amountRaw = "10",
                source = tooLong,
                note = "",
                epochDay = today,
            ),
        )
        assertTrue(
            AddTransactionValidator.canSave(
                type = TransactionType.EXPENSE,
                amountRaw = "10",
                source = tooLong,
                note = "",
                epochDay = today,
            ),
        )
        assertTrue(AddTransactionValidator.isSourceLengthValid("s".repeat(LedgerRepository.MAX_SOURCE_LENGTH)))
        assertFalse(AddTransactionValidator.isSourceLengthValid(tooLong))
    }

    @Test
    fun expenseDraftDropsSourceAndIncomeDraftDropsPersonAndCategory() {
        val expense = AddTransactionValidator.toDraft(
            type = TransactionType.EXPENSE,
            amountRaw = "12.50",
            personId = 7L,
            categoryId = 9L,
            source = "should-not-persist",
            note = "  打印纸  ",
            epochDay = today,
        )
        assertNotNull(expense)
        assertEquals(TransactionType.EXPENSE, expense!!.type)
        assertEquals(1_250L, expense.amountCents)
        assertEquals(7L, expense.expensePersonId)
        assertEquals(9L, expense.expenseCategoryId)
        assertNull(expense.incomeSource)
        assertEquals("打印纸", expense.note)
        assertEquals(LocalDate.ofEpochDay(today), expense.date)

        val income = AddTransactionValidator.toDraft(
            type = TransactionType.INCOME,
            amountRaw = "5000",
            personId = 7L,
            categoryId = 9L,
            source = "  科研项目拨款  ",
            note = "",
            epochDay = today,
        )
        assertNotNull(income)
        assertEquals(TransactionType.INCOME, income!!.type)
        assertEquals(500_000L, income.amountCents)
        assertNull(income.expensePersonId)
        assertNull(income.expenseCategoryId)
        assertEquals("科研项目拨款", income.incomeSource)
        assertNull(income.note)
    }

    @Test
    fun toDraftReturnsNullWhenAmountOrDateInvalid() {
        assertNull(
            AddTransactionValidator.toDraft(
                type = TransactionType.EXPENSE,
                amountRaw = "abc",
                personId = null,
                categoryId = null,
                source = "",
                note = "",
                epochDay = today,
            ),
        )
        assertNull(
            AddTransactionValidator.toDraft(
                type = TransactionType.INCOME,
                amountRaw = "1",
                personId = null,
                categoryId = null,
                source = "",
                note = "",
                epochDay = Long.MAX_VALUE,
            ),
        )
    }

    @Test
    fun optionalPersonCategorySourceAndNoteRemainValid() {
        val draft = AddTransactionValidator.toDraft(
            type = TransactionType.EXPENSE,
            amountRaw = ".5",
            personId = null,
            categoryId = null,
            source = "",
            note = "   ",
            epochDay = today,
        )
        assertNotNull(draft)
        assertEquals(50L, draft!!.amountCents)
        assertNull(draft.expensePersonId)
        assertNull(draft.expenseCategoryId)
        assertNull(draft.note)
    }
}
