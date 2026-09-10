package com.geo.ledger.ui.addtransaction

import com.geo.ledger.data.LedgerRepository
import com.geo.ledger.data.local.TransactionType
import com.geo.ledger.domain.TransactionDraft
import com.geo.ledger.util.GeoDates
import com.geo.ledger.util.MoneyParser
import java.time.LocalDate

object AddTransactionValidator {
    fun parsedAmountCents(raw: String): Long? = MoneyParser.parseCents(raw)

    fun isSourceLengthValid(source: String): Boolean =
        source.trim().length <= LedgerRepository.MAX_SOURCE_LENGTH

    fun isNoteLengthValid(note: String): Boolean =
        note.trim().length <= LedgerRepository.MAX_NOTE_LENGTH

    fun isDateValid(epochDay: Long): Boolean = GeoDates.fromEpochDayOrNull(epochDay) != null

    fun canSave(
        type: TransactionType,
        amountRaw: String,
        source: String,
        note: String,
        epochDay: Long,
    ): Boolean {
        if (parsedAmountCents(amountRaw) == null) return false
        if (!isDateValid(epochDay)) return false
        if (!isNoteLengthValid(note)) return false
        if (type == TransactionType.INCOME && !isSourceLengthValid(source)) return false
        return true
    }

    fun toDraft(
        type: TransactionType,
        amountRaw: String,
        personId: Long?,
        categoryId: Long?,
        source: String,
        note: String,
        epochDay: Long,
        personSelectionEdited: Boolean = false,
        categorySelectionEdited: Boolean = false,
        people: List<com.geo.ledger.data.local.PersonSelection>? = null,
    ): TransactionDraft? {
        if (!canSave(type, amountRaw, source, note, epochDay)) return null
        val amountCents = parsedAmountCents(amountRaw) ?: return null
        val date = GeoDates.fromEpochDayOrNull(epochDay) ?: return null
        val normalizedNote = note.trim().takeIf { it.isNotEmpty() }
        return when (type) {
            TransactionType.EXPENSE -> TransactionDraft(
                type = TransactionType.EXPENSE,
                amountCents = amountCents,
                date = date,
                expensePersonId = personId,
                expenseCategoryId = categoryId,
                incomeSource = null,
                note = normalizedNote,
                personSelectionEdited = personSelectionEdited,
                expensePeople = people,
                categorySelectionEdited = categorySelectionEdited,
            )
            TransactionType.INCOME -> TransactionDraft(
                type = TransactionType.INCOME,
                amountCents = amountCents,
                date = date,
                expensePersonId = null,
                expenseCategoryId = null,
                incomeSource = source.trim().takeIf { it.isNotEmpty() },
                note = normalizedNote,
            )
        }
    }
}
