package com.geo.ledger.domain

import com.geo.ledger.data.local.TransactionEntity
import com.geo.ledger.data.local.TransactionType
import java.time.LocalDate

data class TransactionDraft(
    val type: TransactionType,
    val amountCents: Long,
    val date: LocalDate,
    val expensePersonId: Long? = null,
    val expenseCategoryId: Long? = null,
    val incomeSource: String? = null,
    val note: String? = null,
    val personSelectionEdited: Boolean = false,
    val categorySelectionEdited: Boolean = false,
    val attachments: List<com.geo.ledger.data.local.AttachmentRecord>? = null,
)

data class LedgerEntry(
    val transaction: TransactionEntity,
    val balanceAfterCents: Long,
)

data class DateRange(
    val start: LocalDate,
    val endInclusive: LocalDate,
) {
    init {
        require(!start.isAfter(endInclusive)) { "Start date must not be after end date" }
    }

    fun contains(epochDay: Long): Boolean = epochDay in start.toEpochDay()..endInclusive.toEpochDay()
}

data class PeriodSummary(
    val incomeCents: Long,
    val expenseCents: Long,
    val netChangeCents: Long,
    val endingBalanceCents: Long,
)
