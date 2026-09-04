package com.geo.ledger.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query(
        """
        SELECT * FROM transactions
        ORDER BY transaction_date ASC, created_at_millis ASC, id ASC
        """,
    )
    fun observeAllOrdered(): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        ORDER BY transaction_date ASC, created_at_millis ASC, id ASC
        """,
    )
    suspend fun getAllOrdered(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE client_op_key = :key LIMIT 1")
    suspend fun getByClientOpKey(key: String): TransactionEntity?

    @Insert
    suspend fun insert(entity: TransactionEntity): Long

    @Update
    suspend fun update(entity: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}

@Dao
interface PersonOptionDao {
    @Query("SELECT * FROM person_options WHERE is_active = 1 ORDER BY sort_order ASC, id ASC")
    fun observeActive(): Flow<List<PersonOptionEntity>>

    @Query("SELECT * FROM person_options WHERE id = :id")
    suspend fun getById(id: Long): PersonOptionEntity?

    @Query("SELECT * FROM person_options WHERE id = :id")
    fun observeById(id: Long): Flow<PersonOptionEntity?>

    @Query("SELECT COUNT(*) FROM person_options WHERE is_active = 1 AND name = :name AND id != :excludeId")
    suspend fun countActiveByName(name: String, excludeId: Long = -1): Int

    @Query("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM person_options")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insert(entity: PersonOptionEntity): Long

    @Update
    suspend fun update(entity: PersonOptionEntity)
}

@Dao
interface ExpenseCategoryDao {
    @Query("SELECT * FROM expense_categories WHERE is_active = 1 ORDER BY sort_order ASC, id ASC")
    fun observeActive(): Flow<List<ExpenseCategoryEntity>>

    @Query("SELECT * FROM expense_categories WHERE id = :id")
    suspend fun getById(id: Long): ExpenseCategoryEntity?

    @Query("SELECT * FROM expense_categories WHERE id = :id")
    fun observeById(id: Long): Flow<ExpenseCategoryEntity?>

    @Query("SELECT COUNT(*) FROM expense_categories WHERE is_active = 1 AND name = :name AND id != :excludeId")
    suspend fun countActiveByName(name: String, excludeId: Long = -1): Int

    @Query("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM expense_categories")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insert(entity: ExpenseCategoryEntity): Long

    @Update
    suspend fun update(entity: ExpenseCategoryEntity)
}
