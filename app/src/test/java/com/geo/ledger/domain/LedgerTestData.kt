package com.geo.ledger.domain

import com.geo.ledger.data.local.TransactionEntity
import com.geo.ledger.data.local.TransactionType
import java.time.LocalDate

internal fun ledgerTx(
    id: Long,
    date: String,
    amount: Long,
    type: TransactionType,
    created: Long = id,
): TransactionEntity = TransactionEntity(
    id = id,
    type = type,
    amountCents = amount,
    transactionDate = LocalDate.parse(date).toEpochDay(),
    createdAtMillis = created,
    updatedAtMillis = created,
)

internal fun ledgerEntries(vararg transactions: TransactionEntity): List<LedgerEntry> =
    LedgerCalculator.withRunningBalances(transactions.toList())
