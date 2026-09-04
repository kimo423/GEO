package com.geo.ledger.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TransactionType {
    INCOME,
    EXPENSE,
}

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["transaction_date"]),
        Index(value = ["type"]),
        Index(value = ["created_at_millis"]),
        Index(value = ["expense_person_id"]),
        Index(value = ["expense_category_id"]),
        Index(value = ["client_op_key"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: TransactionType,
    @ColumnInfo(name = "amount_cents")
    val amountCents: Long,
    @ColumnInfo(name = "transaction_date")
    val transactionDate: Long,
    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at_millis")
    val updatedAtMillis: Long,
    @ColumnInfo(name = "expense_person_id")
    val expensePersonId: Long? = null,
    @ColumnInfo(name = "expense_person_snapshot")
    val expensePersonSnapshot: String? = null,
    @ColumnInfo(name = "expense_category_id")
    val expenseCategoryId: Long? = null,
    @ColumnInfo(name = "expense_category_snapshot")
    val expenseCategorySnapshot: String? = null,
    @ColumnInfo(name = "income_source")
    val incomeSource: String? = null,
    val note: String? = null,
    @ColumnInfo(name = "client_op_key")
    val clientOpKey: String? = null,
)

@Entity(
    tableName = "person_options",
    indices = [
        Index(value = ["is_active", "sort_order"]),
        Index(value = ["active_name_key"], unique = true),
    ],
)
data class PersonOptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    @ColumnInfo(name = "active_name_key")
    val activeNameKey: String? = null,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at_millis")
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "expense_categories",
    indices = [
        Index(value = ["is_active", "sort_order"]),
        Index(value = ["active_name_key"], unique = true),
    ],
)
data class ExpenseCategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    @ColumnInfo(name = "active_name_key")
    val activeNameKey: String? = null,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at_millis")
    val updatedAtMillis: Long,
)

fun activeOptionNameKey(isActive: Boolean, normalizedName: String): String? =
    if (isActive) normalizedName else null
