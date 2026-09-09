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
import com.geo.ledger.domain.LedgerObservationPipeline
import com.geo.ledger.domain.NamedOption
import com.geo.ledger.domain.OptionNameValidator
import com.geo.ledger.domain.OptionSnapshotPolicy
import com.geo.ledger.domain.SaveIdempotency
import com.geo.ledger.domain.TransactionDraft
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import com.geo.ledger.data.local.*
import com.geo.ledger.data.transfer.*
import com.geo.ledger.data.attachments.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

class LedgerRepository(
    internal val database: GeoDatabase,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    computation: CoroutineDispatcher = Dispatchers.Default,
    sharingScope: CoroutineScope? = null,
    val blobStore: PrivateBlobStore? = null,
) {
    internal val mutationMutex = Mutex()
    val historyDao = database.historyDao()
    val auditHistory = historyDao.observeAudit()
    val attachmentCounts = historyDao.observeCounts()
    fun observeAttachments(uuid: String) = historyDao.observeAttachments(uuid)
    suspend fun activeAttachments(uuid: String) = historyDao.activeAttachments(uuid)
    private val transactionDao = database.transactionDao()
    private val personDao = database.personOptionDao()
    private val categoryDao = database.expenseCategoryDao()

    val ledgerObservation: Flow<LedgerObservation> = LedgerObservationPipeline.observe(
        rows = transactionDao.observeAllOrdered(),
        computation = computation,
        sharingScope = sharingScope,
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

    suspend fun getTransaction(id: Long): TransactionEntity? = transactionDao.getById(id)?.takeUnless { it.isDeleted }

    suspend fun saveTransaction(
        id: Long?,
        draft: TransactionDraft,
        clientOpKey: String? = null,
    ): Long = mutationMutex.withLock { withContext(Dispatchers.IO) {
      draft.attachments?.forEach { record ->
          requireNotNull(blobStore) { "附件存储不可用" }.verify(record.internalStorageKey, record.sizeBytes, record.sha256)
      }
      database.withTransaction {
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
        // A restored creation draft may have changed before its local ID was checkpointed.
        // Resolve active replays as edits (identical business content remains a no-op).
        // Never resurrect a deleted row from an old creation callback.
        if (id == null && existing?.isDeleted == true) return@withTransaction existing.id
        require(existing?.isDeleted != true) { "Transaction is deleted" }

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
            transactionUuid = existing?.transactionUuid ?: UUID.randomUUID().toString(),
        )

        val previousAttachments = existing?.let { historyDao.activeAttachments(it.transactionUuid) }.orEmpty()
        val attachments = (draft.attachments ?: previousAttachments).mapIndexed { index, a ->
            a.copy(relation = a.relation.copy(transactionUuid = entity.transactionUuid, sortOrder = index, isActive = true, removedAtMillis = null))
        }
        val before = existing?.let { TransactionSnapshot(it, previousAttachments.map(AttachmentRef::from)) }
        val after = TransactionSnapshot(entity, attachments.map(AttachmentRef::from)).also { it.validate() }
        if (before != null && before.business() == after.business()) return@withTransaction existing.id

        val current = transactionDao.getAllOrdered()
        val candidate = if (existing == null) {
            current + entity.copy(id = Long.MAX_VALUE)
        } else {
            current.map { if (it.id == existing.id) entity else it }
        }
        LedgerCalculator.validateCandidateHistory(candidate)

        val resultId = if (existing == null) {
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
        historyDao.deactivateAttachments(entity.transactionUuid, now)
        attachments.forEach { attach ->
            val blob = historyDao.blob(attach.relation.blobUuid)
            if (blob == null) historyDao.insertBlob(AttachmentBlobEntity(attach.relation.blobUuid, attach.sha256,
                attach.sizeBytes, attach.mimeType, attach.internalStorageKey, now))
            else require(blob.sha256 == attach.sha256 && blob.sizeBytes == attach.sizeBytes && blob.internalStorageKey == attach.internalStorageKey)
            val old = historyDao.attachment(attach.relation.attachmentUuid)
            if (old == null) historyDao.insertAttachment(attach.relation)
            else {
                require(old.transactionUuid == entity.transactionUuid && old.blobUuid == attach.relation.blobUuid && old.originalFileName == attach.relation.originalFileName)
                historyDao.updateAttachment(old.copy(sortOrder = attach.relation.sortOrder, isActive = true, removedAtMillis = null))
            }
        }
        if (before != null) historyDao.insertEvent(AuditEventEntity(UUID.randomUUID().toString(), entity.transactionUuid,
            "EDIT", "USER", now, beforeSnapshotJson = before.encode(), afterSnapshotJson = after.encode()))
        resultId
      }
    }
    }

    suspend fun deleteTransaction(id: Long) = mutationMutex.withLock { database.withTransaction {
        val existing = transactionDao.getById(id) ?: return@withTransaction
        if (existing.isDeleted) return@withTransaction
        val remaining = transactionDao.getAllOrdered().filterNot { it.id == existing.id }
        LedgerCalculator.validateCandidateHistory(remaining)
        val now = nowMillis()
        val before = TransactionSnapshot(existing, historyDao.activeAttachments(existing.transactionUuid).map(AttachmentRef::from))
        historyDao.insertEvent(AuditEventEntity(UUID.randomUUID().toString(), existing.transactionUuid,
            "DELETE", "USER", now, beforeSnapshotJson = before.encode(), afterSnapshotJson = null))
        historyDao.deactivateAttachments(existing.transactionUuid, now)
        transactionDao.update(existing.copy(isDeleted = true, deletedAtMillis = now, updatedAtMillis = now))
    } }

    suspend fun addPerson(name: String): Long = database.withTransaction {
        val normalized = validateOptionName(name)
        require(personDao.getAll().none { it.isActive && normalizedOptionName(it.name) == normalizedOptionName(normalized) }) { "Active person name already exists" }
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
        require(personDao.getAll().none { it.isActive && it.id != id && normalizedOptionName(it.name) == normalizedOptionName(normalized) }) { "Active person name already exists" }
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
        require(categoryDao.getAll().none { it.isActive && normalizedOptionName(it.name) == normalizedOptionName(normalized) }) { "Active category name already exists" }
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
        require(categoryDao.getAll().none { it.isActive && it.id != id && normalizedOptionName(it.name) == normalizedOptionName(normalized) }) { "Active category name already exists" }
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
