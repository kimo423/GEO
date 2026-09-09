package com.geo.ledger.domain

import com.geo.ledger.data.local.TransactionEntity
import com.geo.ledger.data.local.TransactionType

object LedgerCalculator {
    val stableAscendingComparator = compareBy<TransactionEntity>(
        TransactionEntity::transactionDate,
        TransactionEntity::createdAtMillis,
        TransactionEntity::id,
    )

    fun withRunningBalances(transactions: List<TransactionEntity>): List<LedgerEntry> {
        var balance = 0L
        return transactions.filterNot { it.isDeleted }.sortedWith(stableAscendingComparator).map { transaction ->
            require(transaction.amountCents >= 1) { "Transaction amount must be positive" }
            balance = when (transaction.type) {
                TransactionType.INCOME -> Math.addExact(balance, transaction.amountCents)
                TransactionType.EXPENSE -> Math.subtractExact(balance, transaction.amountCents)
            }
            LedgerEntry(transaction, balance)
        }
    }

    /**
     * Rejects a candidate history whose running balance or all-history income/expense
     * totals cannot be represented exactly as Long cents.
     */
    fun validateCandidateHistory(transactions: List<TransactionEntity>): List<LedgerEntry> {
        requireExactAggregates(transactions)
        return withRunningBalances(transactions)
    }

    fun requireExactAggregates(transactions: List<TransactionEntity>) {
        var income = 0L
        var expense = 0L
        transactions.filterNot { it.isDeleted }.forEach { transaction ->
            require(transaction.amountCents >= 1) { "Transaction amount must be positive" }
            when (transaction.type) {
                TransactionType.INCOME -> income = Math.addExact(income, transaction.amountCents)
                TransactionType.EXPENSE -> expense = Math.addExact(expense, transaction.amountCents)
            }
        }
    }

    fun summary(entries: List<LedgerEntry>, range: DateRange): PeriodSummary {
        val ordered = entries.sortedWith(compareBy(stableAscendingComparator) { it.transaction })
        var income = 0L
        var expense = 0L
        ordered.forEach { entry ->
            if (range.contains(entry.transaction.transactionDate)) {
                when (entry.transaction.type) {
                    TransactionType.INCOME -> income = Math.addExact(income, entry.transaction.amountCents)
                    TransactionType.EXPENSE -> expense = Math.addExact(expense, entry.transaction.amountCents)
                }
            }
        }
        val endingBalance = ordered
            .lastOrNull { it.transaction.transactionDate <= range.endInclusive.toEpochDay() }
            ?.balanceAfterCents
            ?: 0L
        return PeriodSummary(
            incomeCents = income,
            expenseCents = expense,
            netChangeCents = Math.subtractExact(income, expense),
            endingBalanceCents = endingBalance,
        )
    }
}
