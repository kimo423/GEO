package com.geo.ledger.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.geo.ledger.data.local.ExpenseCategoryEntity
import com.geo.ledger.data.local.GeoDatabase
import com.geo.ledger.data.local.PersonOptionEntity
import com.geo.ledger.data.local.TransactionEntity
import com.geo.ledger.data.local.TransactionType
import com.geo.ledger.data.local.activeOptionNameKey
import com.geo.ledger.domain.LedgerCalculator
import com.geo.ledger.domain.LedgerEntry
import com.geo.ledger.domain.LedgerObservation
import com.geo.ledger.domain.LedgerObserver
import com.geo.ledger.domain.NamedOption
import com.geo.ledger.domain.OptionNameValidator
import com.geo.ledger.domain.OptionSnapshotPolicy
import com.geo.ledger.domain.SaveIdempotency
import com.geo.ledger.domain.TransactionDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

class LedgerRepository(
    private val database: GeoDatabase,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val transactionDao = database.transactionDao()
    private val personDao = database.personOptionDao()
    private val categoryDao = database.expenseCategoryDao()

    val ledgerObservation: Flow<LedgerObservation> = transactionDao.observeAllOrdered().map(
        LedgerObserver::observe,
    )
    val ledgerEntries: Flow<List<LedgerEntry>> = ledgerObservation.mapNotNull { observation ->
        (observation as? LedgerObservation.Ready)?.entries
    }
    val activePersons: Flow<List<PersonOptionEntity>> = personDao.observeActive()
    val activeCategories: Flow<List<ExpenseCategoryEntity>> = categoryDao.observeActive()

    fun observeTransaction(id: Long): Flow<LedgerEntry?> = ledgerObservation.map { observation ->
        when (observation) {
            is LedgerObservation.Ready -> observation.entries.firstOrNull { it.transaction.id == id }
            is LedgerObservation.Invalid -> null
        }
    }

    suspend fun getTransaction(id: Long): TransactionEntity? = transactionDao.getById(id)

    suspend fun saveTransaction(
        id: Long?,
        draft: TransactionDraft,
        clientOpKey: String? = null,
    ): Long = database.withTransaction {
        require(draft.amountCents >= 1) { "Amount must be positive" }
        val existingId = SaveIdempotency.resolveExistingId(
            requestedId = id,
            clientOpKey = clientOpKey,
            idForClientOpKey = clientOpKey?.takeIf { it.isNotBlank() }?.let { key ->
                transactionDao.getByClientOpKey(key)?.id
            },
        )
        val existing = existingId?.let { transactionDao.getById(it) }
        if (id != null && id > 0) requireNotNull(existing) { "Transaction does not exist" }

        val now = nowMillis()
        val personSnapshot = if (draft.type == TransactionType.EXPENSE) {
            resolvePersonSnapshot(existing, draft.expensePersonId, draft.personSelectionEdited)
        } else {
            null
        }
        val categorySnapshot = if (draft.type == TransactionType.EXPENSE) {
            resolveCategorySnapshot(existing, draft.expenseCategoryId, draft.categorySelectionEdited)
        } else {
            null
        }
        val normalizedSource = draft.incomeSource.normalized(MAX_SOURCE_LENGTH)
        val normalizedNote = draft.note.normalized(MAX_NOTE_LENGTH)
        val entity = TransactionEntity(
            id = existing?.id ?: 0,
            type = draft.type,
            amountCents = draft.amountCents,
            transactionDate = draft.date.toEpochDay(),
            createdAtMillis = existing?.createdAtMillis ?: now,
            updatedAtMillis = now,
            expensePersonId = if (draft.type == TransactionType.EXPENSE) draft.expensePersonId else null,
            expensePersonSnapshot = personSnapshot,
            expenseCategoryId = if (draft.type == TransactionType.EXPENSE) draft.expenseCategoryId else null,
            expenseCategorySnapshot = categorySnapshot,
            incomeSource = if (draft.type == TransactionType.INCOME) normalizedSource else null,
            note = normalizedNote,
            clientOpKey = existing?.clientOpKey ?: clientOpKey?.takeIf { it.isNotBlank() },
        )

        val current = transactionDao.getAllOrdered()
        val candidate = if (existing == null) {
            current + entity.copy(id = Long.MAX_VALUE)
        } else {
            current.map { if (it.id == existing.id) entity else it }
        }
        LedgerCalculator.validateCandidateHistory(candidate)

        if (existing == null) {
            try {
                transactionDao.insert(entity)
            } catch (error: SQLiteConstraintException) {
                val raced = clientOpKey?.takeIf { it.isNotBlank() }?.let { transactionDao.getByClientOpKey(it) }
                if (raced != null) raced.id else throw error
            }
        } else {
            transactionDao.update(entity)
            entity.id
        }
    }

    suspend fun deleteTransaction(id: Long) = database.withTransaction {
        val existing = transactionDao.getById(id) ?: return@withTransaction
        val remaining = transactionDao.getAllOrdered().filterNot { it.id == existing.id }
        LedgerCalculator.validateCandidateHistory(remaining)
        transactionDao.deleteById(id)
    }

    suspend fun addPerson(name: String): Long = database.withTransaction {
        val normalized = validateOptionName(name)
        require(personDao.countActiveByName(normalized) == 0) { "Active person name already exists" }
        val now = nowMillis()
        preservingUniqueActiveName("Active person name already exists") {
            personDao.insert(
                PersonOptionEntity(
                    name = normalized,
                    isActive = true,
                    activeNameKey = activeOptionNameKey(isActive = true, normalizedName = normalized),
                    sortOrder = personDao.nextSortOrder(),
                    createdAtMillis = now,
                    updatedAtMillis = now,
                ),
            )
        }
    }

    suspend fun renamePerson(id: Long, name: String) = database.withTransaction {
        val normalized = validateOptionName(name)
        val existing = requireNotNull(personDao.getById(id)) { "Person does not exist" }
        require(existing.isActive) { "Person is inactive" }
        require(personDao.countActiveByName(normalized, id) == 0) { "Active person name already exists" }
        preservingUniqueActiveName("Active person name already exists") {
            personDao.update(
                existing.copy(
                    name = normalized,
                    activeNameKey = activeOptionNameKey(isActive = true, normalizedName = normalized),
                    updatedAtMillis = nowMillis(),
                ),
            )
        }
    }

    suspend fun deactivatePerson(id: Long) = database.withTransaction {
        val existing = requireNotNull(personDao.getById(id)) { "Person does not exist" }
        if (existing.isActive) {
            personDao.update(
                existing.copy(
                    isActive = false,
                    activeNameKey = activeOptionNameKey(isActive = false, normalizedName = existing.name),
                    updatedAtMillis = nowMillis(),
                ),
            )
        }
    }

    suspend fun addCategory(name: String): Long = database.withTransaction {
        val normalized = validateOptionName(name)
        require(categoryDao.countActiveByName(normalized) == 0) { "Active category name already exists" }
        val now = nowMillis()
        preservingUniqueActiveName("Active category name already exists") {
            categoryDao.insert(
                ExpenseCategoryEntity(
                    name = normalized,
                    isActive = true,
                    activeNameKey = activeOptionNameKey(isActive = true, normalizedName = normalized),
                    sortOrder = categoryDao.nextSortOrder(),
                    createdAtMillis = now,
                    updatedAtMillis = now,
                ),
            )
        }
    }

    suspend fun renameCategory(id: Long, name: String) = database.withTransaction {
        val normalized = validateOptionName(name)
        val existing = requireNotNull(categoryDao.getById(id)) { "Category does not exist" }
        require(existing.isActive) { "Category is inactive" }
        require(categoryDao.countActiveByName(normalized, id) == 0) { "Active category name already exists" }
        preservingUniqueActiveName("Active category name already exists") {
            categoryDao.update(
                existing.copy(
                    name = normalized,
                    activeNameKey = activeOptionNameKey(isActive = true, normalizedName = normalized),
                    updatedAtMillis = nowMillis(),
                ),
            )
        }
    }

    suspend fun deactivateCategory(id: Long) = database.withTransaction {
        val existing = requireNotNull(categoryDao.getById(id)) { "Category does not exist" }
        if (existing.isActive) {
            categoryDao.update(
                existing.copy(
                    isActive = false,
                    activeNameKey = activeOptionNameKey(isActive = false, normalizedName = existing.name),
                    updatedAtMillis = nowMillis(),
                ),
            )
        }
    }

    private suspend fun resolvePersonSnapshot(
        existing: TransactionEntity?,
        selectedId: Long?,
        selectionEdited: Boolean,
    ): String? {
        val option = selectedId?.let { personDao.getById(it) }
        return OptionSnapshotPolicy.snapshotForSave(
            selectedId = selectedId,
            existingId = existing?.takeIf { it.type == TransactionType.EXPENSE }?.expensePersonId,
            existingSnapshot = existing?.expensePersonSnapshot,
            selectionEdited = selectionEdited,
            currentOption = option?.let { NamedOption(it.id, it.name, it.isActive) },
            missingMessage = "Person does not exist",
            inactiveMessage = "Person is inactive",
        )
    }

    private suspend fun resolveCategorySnapshot(
        existing: TransactionEntity?,
        selectedId: Long?,
        selectionEdited: Boolean,
    ): String? {
        val option = selectedId?.let { categoryDao.getById(it) }
        return OptionSnapshotPolicy.snapshotForSave(
            selectedId = selectedId,
            existingId = existing?.takeIf { it.type == TransactionType.EXPENSE }?.expenseCategoryId,
            existingSnapshot = existing?.expenseCategorySnapshot,
            selectionEdited = selectionEdited,
            currentOption = option?.let { NamedOption(it.id, it.name, it.isActive) },
            missingMessage = "Category does not exist",
            inactiveMessage = "Category is inactive",
        )
    }

    private fun validateOptionName(name: String): String {
        return when (
            val result = OptionNameValidator.validate(name, maxLength = MAX_OPTION_NAME_LENGTH)
        ) {
            is OptionNameValidator.Result.Valid -> result.normalized
            is OptionNameValidator.Result.Invalid -> throw IllegalArgumentException(
                when (result.reason) {
                    OptionNameValidator.Reason.Blank -> "Name must not be blank"
                    OptionNameValidator.Reason.TooLong -> "Name is too long"
                    OptionNameValidator.Reason.Duplicate -> "Active name already exists"
                },
            )
        }
    }

    private fun String?.normalized(maxLength: Int): String? {
        val normalized = this?.trim()?.takeIf(String::isNotEmpty) ?: return null
        require(normalized.length <= maxLength) { "Text is too long" }
        return normalized
    }

    private suspend inline fun <T> preservingUniqueActiveName(
        duplicateMessage: String,
        block: suspend () -> T,
    ): T {
        return try {
            block()
        } catch (error: SQLiteConstraintException) {
            throw IllegalArgumentException(duplicateMessage, error)
        }
    }

    companion object {
        const val MAX_OPTION_NAME_LENGTH = 40
        const val MAX_SOURCE_LENGTH = 120
        const val MAX_NOTE_LENGTH = 1_000
    }
}
