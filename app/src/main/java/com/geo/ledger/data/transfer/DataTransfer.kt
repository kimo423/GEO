package com.geo.ledger.data.transfer

import androidx.room.withTransaction
import com.geo.ledger.data.LedgerRepository
import com.geo.ledger.data.local.*
import com.geo.ledger.domain.LedgerCalculator
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

class DataTransfer(private val repo: LedgerRepository) {
    private val db get()=repo.database
    private val history get()=repo.historyDao
    private val store get()=requireNotNull(repo.blobStore)
    suspend fun export(file: File, versionName: String, versionCode: Int) = withContext(Dispatchers.IO) {
        val (snapshot, blobs)=repo.mutationMutex.withLock { db.withTransaction {
            val data=ArchiveDataset(db.transactionDao().getAllIncludingDeleted().map { tx ->
                TransactionSnapshot(tx,history.activeAttachments(tx.transactionUuid).map(AttachmentRef::from))
            }, history.allAudit())
            data to history.allBlobs().associateBy { it.blobUuid }
        } }
        try { DataArchive.write(snapshot,file,{ uuid -> store.file(blobs.getValue(uuid).internalStorageKey) },versionName,versionCode) }
        catch(e: Throwable) { file.delete(); throw e }
    }

    suspend fun apply(pack: ValidatedArchive, mode: ImportMode): Int = withContext(Dispatchers.IO) {
      repo.mutationMutex.withLock {
        val newKeys=mutableListOf<String>()
        try {
            DataArchive.validateDataset(pack.dataset)
            val local=db.transactionDao().getAllIncludingDeleted().associateBy { it.transactionUuid }
            val targets=pack.dataset.transactions.filter { mode==ImportMode.FULL_REPLACE || it.transaction.transactionUuid in local }
            val targetIds=targets.map { it.transaction.transactionUuid }.toSet()
            val events=pack.dataset.events.filter { it.transactionUuid in targetIds }
            val selected=pack.attachments.filter { it.transactionUuid in targetIds }
            val needed=selected.distinctBy { it.ref.blobUuid }
            val importedBlobs=needed.map { item ->
                ensureActive()
                val old=if(mode==ImportMode.MATCHING_ONLY) history.blob(item.ref.blobUuid) else null
                if(old!=null) {
                    require(old.sha256==item.ref.sha256 && old.sizeBytes==item.ref.sizeBytes && old.mimeType==item.ref.mimeType) { "本机 Blob UUID 内容冲突" }
                    store.verify(old.internalStorageKey,old.sizeBytes,old.sha256); old
                } else {
                    val stored=pack.blobFiles.getValue(item.ref.blobUuid).inputStream().use { store.put(it) }
                    newKeys+=stored.key
                    require(stored.size==item.ref.sizeBytes && stored.sha256==item.ref.sha256) { "暂存附件内容发生变化" }
                    AttachmentBlobEntity(item.ref.blobUuid,stored.sha256,stored.size,item.ref.mimeType,stored.key,System.currentTimeMillis())
                }
            }
            db.withTransaction {
                val latest=db.transactionDao().getAllIncludingDeleted().associateBy { it.transactionUuid }
                if(mode==ImportMode.MATCHING_ONLY) require(targetIds.all { it in latest }) { "导入预览已过期，请重新选择文件" }
                events.forEach { imported ->
                    val old=history.event(imported.eventUuid)
                    require(old==null || old.canonical()==imported.canonical()) { "审计 UUID 内容冲突，未应用导入" }
                }
                fun <T> names(rows: List<T>, name: (T)->String, id: (T)->Long): Map<String,Long> {
                    val grouped=rows.groupBy { normalizedOptionName(name(it)) }
                    require(grouped.values.all { it.size==1 }) { "本机配置存在同名歧义，请先重命名" }
                    return grouped.mapValues { id(it.value.single()) }
                }
                val persons=names(db.personOptionDao().getAll().filter { it.isActive },{it.name},{it.id})
                val categories=names(db.expenseCategoryDao().getAll().filter { it.isActive },{it.name},{it.id})
                val mapped=targets.mapIndexed { index, snapshot ->
                    val tx=snapshot.transaction; val old=latest[tx.transactionUuid]
                    snapshot.copy(transaction=tx.copy(id=if(mode==ImportMode.FULL_REPLACE) index.toLong()+1 else old!!.id,
                        clientOpKey=if(mode==ImportMode.FULL_REPLACE) null else old?.clientOpKey,
                        expensePersonId=tx.expensePersonSnapshot?.let { persons[normalizedOptionName(it)] },
                        expenseCategoryId=tx.expenseCategorySnapshot?.let { categories[normalizedOptionName(it)] }))
                }
                val candidate=if(mode==ImportMode.FULL_REPLACE) mapped.map { it.transaction }
                    else latest.values.filter { it.transactionUuid !in targetIds } + mapped.map { it.transaction }
                LedgerCalculator.validateCandidateHistory(candidate)
                val now=System.currentTimeMillis()
                val generated=if(mode==ImportMode.MATCHING_ONLY) mapped.mapNotNull { after ->
                    val tx=latest.getValue(after.transaction.transactionUuid)
                    val before=TransactionSnapshot(tx,history.activeAttachments(tx.transactionUuid).map(AttachmentRef::from))
                    if(before.business()==after.business()) null else AuditEventEntity(UUID.randomUUID().toString(),tx.transactionUuid,
                        "EDIT","IMPORT",now,beforeSnapshotJson=before.encode(),afterSnapshotJson=after.encode())
                } else emptyList()
                if(mode==ImportMode.FULL_REPLACE) {
                    history.clearAuditForRestore(); history.clearAttachmentsForRestore()
                    db.transactionDao().clearForRestore(); history.clearBlobsForRestore()
                }
                importedBlobs.forEach { blob -> if(history.blob(blob.blobUuid)==null) history.insertBlob(blob) }
                mapped.forEach { snapshot ->
                    if(mode==ImportMode.FULL_REPLACE) db.transactionDao().insert(snapshot.transaction)
                    else db.transactionDao().update(snapshot.transaction)
                    history.deactivateAttachments(snapshot.transaction.transactionUuid,now)
                }
                val activeRefs=mapped.flatMap { it.attachments }.associateBy { it.attachmentUuid }
                selected.forEach { item ->
                    val old=history.attachment(item.ref.attachmentUuid)
                    require(old==null || (old.transactionUuid==item.transactionUuid && old.blobUuid==item.ref.blobUuid && old.originalFileName==item.ref.originalFileName)) { "附件 UUID 归属或内容冲突" }
                    val active=activeRefs[item.ref.attachmentUuid]
                    val relation=TransactionAttachmentEntity(item.ref.attachmentUuid,item.transactionUuid,item.ref.blobUuid,item.ref.originalFileName,
                        active?.sortOrder ?: item.ref.sortOrder,active!=null,old?.createdAtMillis ?: now,if(active==null) now else null)
                    if(old==null) history.insertAttachment(relation) else history.updateAttachment(relation)
                }
                events.forEach { if(history.event(it.eventUuid)==null) history.insertEvent(it) }
                generated.forEach { history.insertEvent(it) }
                targets.size
            }
        } finally {
            // Check actual committed references even when cancellation arrives after COMMIT.
            withContext(NonCancellable) {
                if(newKeys.isNotEmpty()) {
                    // If DB cannot be read, retain orphans rather than guess about references.
                    val referenced=runCatching { history.allBlobs().map { it.internalStorageKey }.toSet() }.getOrNull()
                    if(referenced!=null) newKeys.filterNot { it in referenced }.forEach { store.file(it).delete() }
                }
            }
        }
      }
    }
}
