package com.geo.ledger.ui.transactiondetail

import com.geo.ledger.R
import com.geo.ledger.data.local.TransactionEntity
import com.geo.ledger.data.local.TransactionType
import com.geo.ledger.domain.LedgerEntry
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionDetailUiMapperTest {
    private val expense = LedgerEntry(
        transaction = TransactionEntity(
            id = 11L,
            type = TransactionType.EXPENSE,
            amountCents = 12_800L,
            transactionDate = LocalDate.of(2026, 9, 3).toEpochDay(),
            createdAtMillis = 1L,
            updatedAtMillis = 1L,
            expensePersonSnapshot = "  张三  ",
            expenseCategorySnapshot = "办公",
            incomeSource = "should-not-show",
            note = "  打印纸  ",
        ),
        balanceAfterCents = -12_800L,
    )

    @Test
    fun invalidIdIsNotFoundEvenBeforeLoad() {
        assertEquals(
            TransactionDetailUiState.NotFound,
            TransactionDetailUiMapper.map(
                transactionId = 0L,
                loaded = false,
                entry = expense,
                isDeleting = false,
                deleted = false,
                errorRes = null,
            ),
        )
        assertEquals(
            TransactionDetailUiState.NotFound,
            TransactionDetailUiMapper.map(
                transactionId = -1L,
                loaded = true,
                entry = null,
                isDeleting = false,
                deleted = false,
                errorRes = null,
            ),
        )
    }

    @Test
    fun validIdStartsLoadingThenContentOrNotFound() {
        assertEquals(
            TransactionDetailUiState.Loading,
            TransactionDetailUiMapper.map(
                transactionId = 11L,
                loaded = false,
                entry = null,
                isDeleting = false,
                deleted = false,
                errorRes = null,
            ),
        )
        assertEquals(
            TransactionDetailUiState.NotFound,
            TransactionDetailUiMapper.map(
                transactionId = 11L,
                loaded = true,
                entry = null,
                isDeleting = false,
                deleted = false,
                errorRes = null,
            ),
        )
        val content = TransactionDetailUiMapper.map(
            transactionId = 11L,
            loaded = true,
            entry = expense,
            isDeleting = true,
            deleted = false,
            errorRes = R.string.error_delete_failed,
        )
        assertTrue(content is TransactionDetailUiState.Content)
        content as TransactionDetailUiState.Content
        assertEquals(11L, content.transactionId)
        assertTrue(content.isDeleting)
        assertEquals(R.string.error_delete_failed, content.errorRes)
        assertEquals(-12_800L, content.balanceAfterCents)
        assertEquals(1L, content.createdAtMillis)
    }

    @Test
    fun deletedWinsOverMissingEntry() {
        assertEquals(
            TransactionDetailUiState.Deleted,
            TransactionDetailUiMapper.map(
                transactionId = 11L,
                loaded = true,
                entry = null,
                isDeleting = true,
                deleted = true,
                errorRes = null,
            ),
        )
    }

    @Test
    fun presenterKeepsTypeAppropriateSnapshotsAndExactBalance() {
        val expenseContent = TransactionDetailPresenter.from(expense)
        assertEquals(TransactionType.EXPENSE, expenseContent.type)
        assertEquals(12_800L, expenseContent.amountCents)
        assertEquals("张三", expenseContent.personSnapshot)
        assertEquals("办公", expenseContent.categorySnapshot)
        assertNull(expenseContent.incomeSource)
        assertEquals("打印纸", expenseContent.note)
        assertEquals(-12_800L, expenseContent.balanceAfterCents)

        val income = LedgerEntry(
            transaction = expense.transaction.copy(
                type = TransactionType.INCOME,
                expensePersonSnapshot = "张三",
                expenseCategorySnapshot = "办公",
                incomeSource = "  科研项目拨款  ",
                note = "   ",
            ),
            balanceAfterCents = 50_000L,
        )
        val incomeContent = TransactionDetailPresenter.from(income)
        assertEquals(TransactionType.INCOME, incomeContent.type)
        assertNull(incomeContent.personSnapshot)
        assertNull(incomeContent.categorySnapshot)
        assertEquals("科研项目拨款", incomeContent.incomeSource)
        assertNull(incomeContent.note)
        assertEquals(50_000L, incomeContent.balanceAfterCents)
    }

    @Test
    fun deleteGuardBlocksInvalidMissingAndInFlightStates() {
        val content = TransactionDetailPresenter.from(expense)
        assertTrue(TransactionDetailDeleteGuard.canAttempt(11L, content, inFlight = false))
        assertFalse(TransactionDetailDeleteGuard.canAttempt(11L, content, inFlight = true))
        assertFalse(TransactionDetailDeleteGuard.canAttempt(11L, content.copy(isDeleting = true), inFlight = false))
        assertFalse(TransactionDetailDeleteGuard.canAttempt(0L, content, inFlight = false))
        assertFalse(TransactionDetailDeleteGuard.canAttempt(11L, TransactionDetailUiState.Loading, inFlight = false))
        assertFalse(TransactionDetailDeleteGuard.canAttempt(11L, TransactionDetailUiState.NotFound, inFlight = false))
        assertFalse(TransactionDetailDeleteGuard.canAttempt(11L, TransactionDetailUiState.Deleted, inFlight = false))
    }
}
