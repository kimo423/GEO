package com.geo.ledger.data.local

import androidx.room.*

@Entity(tableName = "attachment_blobs", indices = [Index(value = ["internalStorageKey"], unique = true)])
data class AttachmentBlobEntity(
    @PrimaryKey val blobUuid: String,
    val sha256: String,
    val sizeBytes: Long,
    val mimeType: String,
    val internalStorageKey: String,
    val createdAtMillis: Long,
)

@Entity(tableName = "transaction_attachments", foreignKeys = [
    ForeignKey(entity = TransactionEntity::class, parentColumns = ["transaction_uuid"], childColumns = ["transactionUuid"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = AttachmentBlobEntity::class, parentColumns = ["blobUuid"], childColumns = ["blobUuid"], onDelete = ForeignKey.RESTRICT),
], indices = [Index("transactionUuid"), Index("blobUuid")])
data class TransactionAttachmentEntity(
    @PrimaryKey val attachmentUuid: String,
    val transactionUuid: String,
    val blobUuid: String,
    val originalFileName: String,
    val sortOrder: Int,
    val isActive: Boolean,
    val createdAtMillis: Long,
    val removedAtMillis: Long? = null,
)

@Entity(tableName = "audit_events", foreignKeys = [
    ForeignKey(entity = TransactionEntity::class, parentColumns = ["transaction_uuid"], childColumns = ["transactionUuid"], onDelete = ForeignKey.RESTRICT),
], indices = [Index("transactionUuid"), Index("occurredAtMillis")])
data class AuditEventEntity(
    @PrimaryKey val eventUuid: String,
    val transactionUuid: String,
    val eventType: String,
    val source: String,
    val occurredAtMillis: Long,
    val schemaVersion: Int = 1,
    val beforeSnapshotJson: String,
    val afterSnapshotJson: String?,
)

data class AttachmentRecord(
    @Embedded val relation: TransactionAttachmentEntity,
    val sha256: String,
    val sizeBytes: Long,
    val mimeType: String,
    val internalStorageKey: String,
)

data class AttachmentCount(val transactionUuid: String, val count: Int)

@Dao
interface HistoryDao {
    @Query("SELECT a.*, b.sha256,b.sizeBytes,b.mimeType,b.internalStorageKey FROM transaction_attachments a JOIN attachment_blobs b ON a.blobUuid=b.blobUuid WHERE a.transactionUuid=:uuid AND a.isActive=1 ORDER BY a.sortOrder,a.attachmentUuid")
    suspend fun activeAttachments(uuid: String): List<AttachmentRecord>
    @Query("SELECT a.*, b.sha256,b.sizeBytes,b.mimeType,b.internalStorageKey FROM transaction_attachments a JOIN attachment_blobs b ON a.blobUuid=b.blobUuid WHERE a.transactionUuid=:uuid AND a.isActive=1 ORDER BY a.sortOrder,a.attachmentUuid")
    fun observeAttachments(uuid: String): kotlinx.coroutines.flow.Flow<List<AttachmentRecord>>
    @Query("SELECT transactionUuid, COUNT(*) as count FROM transaction_attachments WHERE isActive=1 GROUP BY transactionUuid")
    fun observeCounts(): kotlinx.coroutines.flow.Flow<List<AttachmentCount>>
    @Query("SELECT * FROM audit_events ORDER BY occurredAtMillis DESC, eventUuid DESC")
    fun observeAudit(): kotlinx.coroutines.flow.Flow<List<AuditEventEntity>>
    @Query("SELECT * FROM audit_events ORDER BY occurredAtMillis,eventUuid")
    suspend fun allAudit(): List<AuditEventEntity>
    @Query("SELECT * FROM attachment_blobs") suspend fun allBlobs(): List<AttachmentBlobEntity>
    @Query("SELECT * FROM transaction_attachments") suspend fun allAttachments(): List<TransactionAttachmentEntity>
    @Query("SELECT * FROM attachment_blobs WHERE blobUuid=:uuid") suspend fun blob(uuid: String): AttachmentBlobEntity?
    @Query("SELECT * FROM transaction_attachments WHERE attachmentUuid=:uuid") suspend fun attachment(uuid: String): TransactionAttachmentEntity?
    @Query("SELECT * FROM audit_events WHERE eventUuid=:uuid") suspend fun event(uuid: String): AuditEventEntity?
    @Insert suspend fun insertBlob(blob: AttachmentBlobEntity)
    @Insert suspend fun insertAttachment(attachment: TransactionAttachmentEntity)
    @Update suspend fun updateAttachment(attachment: TransactionAttachmentEntity)
    @Insert suspend fun insertEvent(event: AuditEventEntity)
    @Query("UPDATE transaction_attachments SET isActive=0, removedAtMillis=:at WHERE transactionUuid=:uuid AND isActive=1")
    suspend fun deactivateAttachments(uuid: String, at: Long)
    // Dataset restore only; deliberately no per-event update/delete API.
    @Query("DELETE FROM audit_events") suspend fun clearAuditForRestore()
    @Query("DELETE FROM transaction_attachments") suspend fun clearAttachmentsForRestore()
    @Query("DELETE FROM attachment_blobs") suspend fun clearBlobsForRestore()
}
