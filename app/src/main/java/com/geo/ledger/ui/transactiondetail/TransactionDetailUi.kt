package com.geo.ledger.ui.transactiondetail
import com.geo.ledger.data.local.peopleLabel

import com.geo.ledger.data.local.TransactionType
import com.geo.ledger.domain.LedgerEntry

sealed interface TransactionDetailUiState {
    data object Loading : TransactionDetailUiState
    data object NotFound : TransactionDetailUiState
    data object Deleted : TransactionDetailUiState
    data class Content(
        val transactionId: Long,
        val type: TransactionType,
        val amountCents: Long,
        val epochDay: Long,
        val createdAtMillis: Long,
        val personSnapshot: String?,
        val categorySnapshot: String?,
        val incomeSource: String?,
        val note: String?,
        val balanceAfterCents: Long,
        val isDeleting: Boolean,
        val errorRes: Int? = null,
    ) : TransactionDetailUiState
}

sealed interface TransactionDetailEvent {
    data object Deleted : TransactionDetailEvent
    data class Failed(val messageRes: Int) : TransactionDetailEvent
}

object TransactionDetailUiMapper {
    fun map(
        transactionId: Long,
        loaded: Boolean,
        entry: LedgerEntry?,
        isDeleting: Boolean,
        deleted: Boolean,
        errorRes: Int?,
    ): TransactionDetailUiState {
        if (deleted) return TransactionDetailUiState.Deleted
        if (transactionId <= 0L) return TransactionDetailUiState.NotFound
        if (!loaded) return TransactionDetailUiState.Loading
        val content = entry?.let { TransactionDetailPresenter.from(it, isDeleting, errorRes) }
        return content ?: TransactionDetailUiState.NotFound
    }
}

object TransactionDetailDeleteGuard {
    fun canAttempt(
        transactionId: Long,
        state: TransactionDetailUiState,
        inFlight: Boolean,
    ): Boolean {
        if (inFlight || transactionId <= 0L) return false
        return state is TransactionDetailUiState.Content && !state.isDeleting
    }
}

object TransactionDetailPresenter {
    fun from(
        entry: LedgerEntry,
        isDeleting: Boolean = false,
        errorRes: Int? = null,
    ): TransactionDetailUiState.Content {
        val transaction = entry.transaction
        val isExpense = transaction.type == TransactionType.EXPENSE
        return TransactionDetailUiState.Content(
            transactionId = transaction.id,
            type = transaction.type,
            amountCents = transaction.amountCents,
            epochDay = transaction.transactionDate,
            createdAtMillis = transaction.createdAtMillis,
            personSnapshot = present(transaction.peopleLabel()).takeIf { isExpense },
            categorySnapshot = present(transaction.expenseCategorySnapshot).takeIf { isExpense },
            incomeSource = present(transaction.incomeSource).takeUnless { isExpense },
            note = present(transaction.note),
            balanceAfterCents = entry.balanceAfterCents,
            isDeleting = isDeleting,
            errorRes = errorRes,
        )
    }

    internal fun present(value: String?): String? = value?.trim()?.takeIf(String::isNotEmpty)
}
