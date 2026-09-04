package com.geo.ledger.domain

import com.geo.ledger.data.local.TransactionType

object TransactionDisplay {
    fun title(
        type: TransactionType,
        categorySnapshot: String?,
        incomeSource: String?,
        note: String?,
        expenseFallback: String,
        incomeFallback: String,
    ): String = when (type) {
        TransactionType.EXPENSE -> firstText(categorySnapshot) ?: firstText(note) ?: expenseFallback
        TransactionType.INCOME -> firstText(incomeSource) ?: incomeFallback
    }

    fun subtitle(
        type: TransactionType,
        personSnapshot: String?,
        categorySnapshot: String?,
        note: String?,
    ): String? {
        val parts = when (type) {
            TransactionType.EXPENSE -> {
                val titleUsesNote = firstText(categorySnapshot) == null && firstText(note) != null
                listOfNotNull(
                    firstText(personSnapshot),
                    firstText(note).takeUnless { titleUsesNote },
                )
            }
            TransactionType.INCOME -> listOfNotNull(firstText(note))
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private fun firstText(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
}
