package com.geo.ledger.data.transfer

import com.geo.ledger.data.local.*
import com.geo.ledger.data.attachments.AttachmentPolicy
import com.geo.ledger.domain.LedgerCalculator
import java.io.*
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.LocalDate
import java.util.*
import java.util.zip.*

data class ArchiveAttachment(val transactionUuid: String, val ref: AttachmentRef, val archivePath: String) {
    fun json() = ref.json() + linkedMapOf("transactionUuid" to transactionUuid,"archivePath" to archivePath)
    fun identity() = json().filterKeys { it !in setOf("sortOrder","archivePath") }
}
data class ArchiveDataset(val transactions: List<TransactionSnapshot>, val events: List<AuditEventEntity>)
data class ValidatedArchive(val dataset: ArchiveDataset, val attachments: List<ArchiveAttachment>,
    val blobFiles: Map<String,File>, val exportedAt: String, private val root: File) : Closeable {
    override fun close() { root.deleteRecursively() }
}

fun AuditEventEntity.json(): Map<String,Any?> = linkedMapOf("eventUuid" to eventUuid,"transactionUuid" to transactionUuid,
    "eventType" to eventType,"source" to source,"occurredAtMillis" to occurredAtMillis,"schemaVersion" to schemaVersion,
    "beforeSnapshot" to TransactionSnapshot.decode(beforeSnapshotJson).json(),
    "afterSnapshot" to afterSnapshotJson?.let { TransactionSnapshot.decode(it).json() })
fun AuditEventEntity.canonical(): String = StrictJson.stringify(json())

object DataArchive {
    const val MAX_ARCHIVE = 1024L*1024*1024
    const val MAX_EXPANDED = 2L*1024*1024*1024
    const val MAX_JSON = 32L*1024*1024
    const val MAX_ENTRIES = 100_000
    private val roots = setOf("manifest.json","transactions.json","audit_logs.json")
    fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun safePath(path: String) {
        require(path.isNotEmpty() && path.length <= 512 && !path.startsWith('/') && '\\' !in path && ':' !in path &&
            path.none { it.code < 32 } && path.split('/').all { it.isNotEmpty() && it!="." && it!=".." }) { "ZIP 包含危险路径" }
    }
    fun folder(tx: TransactionEntity): String {
        AttachmentPolicy.uuid(tx.transactionUuid)
        val type = if (tx.type==TransactionType.EXPENSE) "支出" else "收入"
        val amount = "${tx.amountCents/100}.${(tx.amountCents%100).toString().padStart(2,'0')}"
        val label = if (tx.type==TransactionType.EXPENSE) tx.expenseCategorySnapshot ?: tx.expensePersonSnapshot else tx.incomeSource
        val stem = "${LocalDate.ofEpochDay(tx.transactionDate)}_${type}_$amount" + (label?.takeIf { it.isNotBlank() }?.let { "_${AttachmentPolicy.safeName(it,32)}" } ?: "")
        return "${stem}_${tx.transactionUuid.take(8)}"
    }
    private fun fileName(raw: String): String {
        val name = AttachmentPolicy.safeName(raw,100)
        return if (name.substringBefore('.').uppercase(Locale.ROOT) in setOf("CON","PRN","AUX","NUL","COM1","COM2","COM3","COM4","COM5","COM6","COM7","COM8","COM9","LPT1","LPT2","LPT3","LPT4","LPT5","LPT6","LPT7","LPT8","LPT9")) "_$name" else name
    }
    fun allReferences(data: ArchiveDataset): List<Pair<String,AttachmentRef>> = buildList {
        data.transactions.forEach { snapshot -> snapshot.attachments.forEach { add(snapshot.transaction.transactionUuid to it) } }
        data.events.forEach { event ->
            listOfNotNull(event.beforeSnapshotJson,event.afterSnapshotJson).forEach { json ->
                val snapshot = TransactionSnapshot.decode(json)
                snapshot.attachments.forEach { add(snapshot.transaction.transactionUuid to it) }
            }
        }
    }
    fun write(data: ArchiveDataset, output: File, resolveBlob: (String)->File, versionName: String, versionCode: Int) {
        validateDataset(data)
        val txMap = data.transactions.associateBy { it.transaction.transactionUuid }
        val usedFolders = mutableSetOf<String>(); val folders = mutableMapOf<String,String>()
        val usedPaths = mutableSetOf<String>(); val blobPaths = mutableMapOf<String,String>()
        val index = allReferences(data).distinctBy { it.second.attachmentUuid }.map { (owner, ref) ->
            val path = blobPaths.getOrPut(ref.blobUuid) {
                val tx = txMap.getValue(owner)
                val folder = folders.getOrPut(owner) {
                    val base = folder(tx.transaction)
                    if (usedFolders.add(base.lowercase(Locale.ROOT))) base else "${base.take(60)}_$owner".also {
                        check(usedFolders.add(it.lowercase(Locale.ROOT))) { "附件目录发生冲突" }
                    }
                }
                val section = if (tx.attachments.any { it.blobUuid==ref.blobUuid } && !tx.transaction.isDeleted) "current" else "history_only"
                val base = "attachments/$folder/$section/${fileName(ref.originalFileName)}"
                var candidate = base; var suffix = 1
                while (!usedPaths.add(candidate.lowercase(Locale.ROOT))) {
                    val stem = base.substringBeforeLast('.',base); val ext = base.substringAfterLast('.',"")
                    candidate = "${stem}_${++suffix}" + if(ext.isEmpty()) "" else ".$ext"
                }
                candidate
            }
            ArchiveAttachment(owner,ref,path)
        }
        val transactionsBytes = StrictJson.stringify(data.transactions.map { it.json() }).toByteArray(Charsets.UTF_8)
        val auditBytes = StrictJson.stringify(data.events.map { it.json() }).toByteArray(Charsets.UTF_8)
        require(transactionsBytes.size <= MAX_JSON && auditBytes.size <= MAX_JSON) { "备份 JSON 超过 32 MiB" }
        val manifest = linkedMapOf("format" to "GEO_DATA","formatVersion" to 2,"exportedAt" to java.time.Instant.now().toString(),
            "appVersionName" to versionName,"appVersionCode" to versionCode,"transactionCount" to data.transactions.size,
            "auditEventCount" to data.events.size,"attachmentCount" to index.size,
            "transactionUuids" to data.transactions.map { it.transaction.transactionUuid },
            "transactionsSha256" to hash(transactionsBytes),"auditLogsSha256" to hash(auditBytes),
            "currentSnapshotHashes" to data.transactions.associate { it.transaction.transactionUuid to hash(it.encode().toByteArray(Charsets.UTF_8)) },
            "attachments" to index.map { it.json() })
        val manifestBytes = StrictJson.stringify(manifest).toByteArray(Charsets.UTF_8)
        require(manifestBytes.size <= MAX_JSON && index.size + 3 <= MAX_ENTRIES)
        var expanded = transactionsBytes.size.toLong()+auditBytes.size+manifestBytes.size
        require(expanded + index.distinctBy { it.ref.blobUuid }.sumOf { it.ref.sizeBytes } <= MAX_ARCHIVE - 1024*1024) { "备份超过本版 1 GiB 限制" }
        ZipOutputStream(BufferedOutputStream(FileOutputStream(output))).use { zip ->
            fun json(name: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
            json("manifest.json",manifestBytes); json("transactions.json",transactionsBytes); json("audit_logs.json",auditBytes)
            index.distinctBy { it.ref.blobUuid }.forEach { item ->
                val file = resolveBlob(item.ref.blobUuid)
                val crc = CRC32()
                file.inputStream().use { input ->
                    val b=ByteArray(64*1024); var n=input.read(b); while(n>=0) { crc.update(b,0,n); n=input.read(b) }
                }
                val entry = ZipEntry(item.archivePath).apply { method=ZipEntry.STORED; size=item.ref.sizeBytes; compressedSize=size; this.crc=crc.value }
                zip.putNextEntry(entry)
                val copied = file.inputStream().use { AttachmentPolicy.copy(it,zip) }
                require(copied.size==item.ref.sizeBytes && copied.sha256==item.ref.sha256) { "附件损坏，备份未完成" }
                expanded += copied.size; require(expanded <= MAX_EXPANDED)
                zip.closeEntry()
            }
        }
        require(output.length() <= MAX_ARCHIVE) { "备份超过本版 1 GiB 限制" }
    }
    fun validateDataset(data: ArchiveDataset) {
        require(data.transactions.size <= 20000 && data.events.size <= 50000) { "数据记录超出本版限制" }
        val uuids = data.transactions.map { it.transaction.transactionUuid }
        require(uuids.distinct().size == uuids.size) { "账单 UUID 重复" }
        data.transactions.forEach { it.validate() }
        require(data.events.map { it.eventUuid }.distinct().size==data.events.size) { "审计事件 UUID 重复" }
        data.events.forEach { event ->
            AttachmentPolicy.uuid(event.eventUuid); require(event.transactionUuid in uuids && event.schemaVersion==1)
            require(event.source in setOf("USER","IMPORT") && event.eventType in setOf("EDIT","DELETE")) { "审计类型无效" }
            val before=TransactionSnapshot.decode(event.beforeSnapshotJson)
            require(before.transaction.transactionUuid==event.transactionUuid)
            if(event.eventType=="DELETE") require(event.afterSnapshotJson==null && !before.transaction.isDeleted)
            else {
                val after=TransactionSnapshot.decode(requireNotNull(event.afterSnapshotJson))
                require(after.transaction.transactionUuid==event.transactionUuid)
                require(event.source!="USER" || (!before.transaction.isDeleted && !after.transaction.isDeleted))
            }
        }
        val refs=allReferences(data)
        refs.groupBy { it.second.attachmentUuid }.values.forEach { group ->
            val identities=group.map { (owner,ref) -> ArchiveAttachment(owner,ref,"").identity() }
            require(identities.distinct().size==1) { "附件 UUID 对应不同内容或账单" }
        }
        refs.groupBy { it.second.blobUuid }.values.forEach { group ->
            require(group.map { Triple(it.second.sha256,it.second.sizeBytes,it.second.mimeType) }.distinct().size==1) { "Blob UUID 内容冲突" }
        }
        LedgerCalculator.validateCandidateHistory(data.transactions.mapIndexed { index,s -> s.transaction.copy(id=index.toLong()+1) })
    }
    fun read(input: InputStream, stagingParent: File): ValidatedArchive {
        val root=File(stagingParent,"import-${UUID.randomUUID()}").apply { check(mkdirs()) }
        try {
            val archive=File(root,"input.zip")
            FileOutputStream(archive).use { AttachmentPolicy.copy(input,it,MAX_ARCHIVE) }
            ZipFile(archive).use { zip ->
                val entries=buildList {
                    val iterator=zip.entries()
                    while(iterator.hasMoreElements()) {
                        require(size<MAX_ENTRIES) { "ZIP 条目过多" }
                        add(iterator.nextElement())
                    }
                }
                val folded=mutableSetOf<String>()
                var declaredTotal=0L
                entries.forEach { e ->
                    val path=e.name.removeSuffix("/"); safePath(path)
                    require(folded.add(path.lowercase(Locale.ROOT))) { "ZIP 条目重复" }
                    require(e.method in setOf(ZipEntry.STORED,ZipEntry.DEFLATED)) { "不支持的 ZIP 压缩方式" }
                    require(e.size in 0..MAX_EXPANDED && e.compressedSize>=0)
                    declaredTotal=Math.addExact(declaredTotal,e.size); require(declaredTotal<=MAX_EXPANDED)
                    if(e.size>1024*1024) require(e.compressedSize>0 && e.size/e.compressedSize <= 200) { "ZIP 压缩率异常" }
                }
                require(root.usableSpace >= declaredTotal + 16*1024*1024) { "设备剩余空间不足" }
                val byName=entries.associateBy { it.name }
                fun bytes(name: String, limit: Long): ByteArray {
                    val e=requireNotNull(byName[name]) { "数据包缺少 $name" }; require(!e.isDirectory && e.size<=limit)
                    return ByteArrayOutputStream().use { output ->
                        val copied=zip.getInputStream(e).use { AttachmentPolicy.copy(it,output,limit) }
                        require(copied.size==e.size) { "ZIP 实际大小与目录不一致" }
                        output.toByteArray()
                    }
                }
                fun decode(bytes: ByteArray): String = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                val manifest=StrictJson.parse(decode(bytes("manifest.json",MAX_JSON))).jsonObject()
                require(manifest.string("format")=="GEO_DATA" && manifest.long("formatVersion") in 1L..2L) { "不支持的 GEO 数据格式" }
                val txBytes=bytes("transactions.json",MAX_JSON); val auditBytes=bytes("audit_logs.json",MAX_JSON)
                require(hash(txBytes)==manifest.string("transactionsSha256") && hash(auditBytes)==manifest.string("auditLogsSha256")) { "账单或审计 JSON 校验失败" }
                val txList=StrictJson.parse(decode(txBytes)) as? List<*> ?: error("账单 JSON 无效")
                val eventList=StrictJson.parse(decode(auditBytes)) as? List<*> ?: error("审计 JSON 无效")
                val data=ArchiveDataset(txList.map(TransactionSnapshot::parse),eventList.map { value ->
                    val o=value.jsonObject(); require(o.long("schemaVersion")==1L)
                    AuditEventEntity(o.string("eventUuid"),o.string("transactionUuid"),o.string("eventType"),o.string("source"),
                        o.long("occurredAtMillis"),1,TransactionSnapshot.parse(o["beforeSnapshot"]).encode(),
                        o["afterSnapshot"]?.let { TransactionSnapshot.parse(it).encode() })
                })
                validateDataset(data)
                val indexed=manifest.array("attachments").map { value ->
                    val o=value.jsonObject(); val path=o.string("archivePath"); safePath(path)
                    require(path.startsWith("attachments/")) { "附件不能覆盖数据 JSON" }
                    ArchiveAttachment(o.string("transactionUuid"),AttachmentRef.parse(o),path)
                }
                require(indexed.map { it.ref.attachmentUuid }.distinct().size==indexed.size) { "清单附件 UUID 重复" }
                require(manifest.long("transactionCount")==data.transactions.size.toLong() && manifest.long("auditEventCount")==data.events.size.toLong() && manifest.long("attachmentCount")==indexed.size.toLong())
                require(manifest.array("transactionUuids")==data.transactions.map { it.transaction.transactionUuid })
                val hashes=manifest["currentSnapshotHashes"].jsonObject()
                require(hashes.keys==data.transactions.map { it.transaction.transactionUuid }.toSet())
                data.transactions.forEach { require(hashes[it.transaction.transactionUuid]==hash(it.encode().toByteArray(Charsets.UTF_8))) }
                val actualRefs=allReferences(data).distinctBy { it.second.attachmentUuid }.associate { (owner,ref) -> ref.attachmentUuid to ArchiveAttachment(owner,ref,"").identity() }
                require(indexed.associate { it.ref.attachmentUuid to it.identity() }==actualRefs) { "清单与账单附件引用不一致" }
                indexed.groupBy { it.ref.blobUuid }.values.forEach { require(it.map { x->x.archivePath }.distinct().size==1) }
                indexed.groupBy { it.archivePath }.values.forEach { require(it.map { x->x.ref.blobUuid }.distinct().size==1) }
                val allowed=roots+indexed.map { it.archivePath }
                val allowedDirectories=allowed.flatMap { path ->
                    path.indices.filter { path[it]=='/' }.map { path.substring(0,it+1) }
                }.toSet()
                entries.forEach { e ->
                    if(e.isDirectory) require(e.name in allowedDirectories) { "ZIP 包含无用目录" }
                    else require(e.name in allowed) { "ZIP 包含未声明文件" }
                    val destination=File(root,e.name).canonicalFile
                    require(destination.path.startsWith(root.canonicalPath+File.separator)) { "ZIP 路径逃逸" }
                }
                val files=mutableMapOf<String,File>(); var expanded=txBytes.size.toLong()+auditBytes.size
                indexed.distinctBy { it.ref.blobUuid }.forEach { item ->
                    val e=requireNotNull(byName[item.archivePath]) { "缺少附件：${item.ref.originalFileName}" }
                    require(!e.isDirectory)
                    val file=File(root,UUID.randomUUID().toString())
                    val copied=FileOutputStream(file).use { out -> zip.getInputStream(e).use { AttachmentPolicy.copy(it,out) } }
                    require(copied.size==e.size && copied.size==item.ref.sizeBytes && copied.sha256==item.ref.sha256) { "附件大小或 SHA-256 校验失败" }
                    expanded+=copied.size; require(expanded<=MAX_EXPANDED)
                    files[item.ref.blobUuid]=file
                }
                return ValidatedArchive(data,indexed,files,manifest.string("exportedAt"),root)
            }
        } catch (e: Throwable) { root.deleteRecursively(); throw e }
    }
}
