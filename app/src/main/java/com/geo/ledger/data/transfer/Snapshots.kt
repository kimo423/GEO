package com.geo.ledger.data.transfer

import com.geo.ledger.data.local.*
import com.geo.ledger.data.attachments.AttachmentPolicy
import java.time.LocalDate

data class AttachmentRef(val attachmentUuid: String, val blobUuid: String, val originalFileName: String,
    val mimeType: String, val sizeBytes: Long, val sha256: String, val sortOrder: Int) {
    fun json(): Map<String, Any?> = linkedMapOf("attachmentUuid" to attachmentUuid, "blobUuid" to blobUuid,
        "originalFileName" to originalFileName, "mimeType" to mimeType, "sizeBytes" to sizeBytes,
        "sha256" to sha256, "sortOrder" to sortOrder)
    fun validate() {
        AttachmentPolicy.uuid(attachmentUuid); AttachmentPolicy.uuid(blobUuid)
        require(originalFileName.isNotBlank() && originalFileName.length <= 255) { "附件名无效或过长" }
        StrictJson.stringify(originalFileName)
        require(mimeType.length in 1..128 && Regex("[a-zA-Z0-9!#$&^_.+-]+/[a-zA-Z0-9!#$&^_.+-]+").matches(mimeType)) { "附件 MIME 无效" }
        require(sizeBytes in 0..AttachmentPolicy.MAX_FILE && sortOrder in 0..9) { "附件大小或顺序无效" }
        require(Regex("[0-9a-f]{64}").matches(sha256)) { "SHA-256 无效" }
    }
    companion object {
        fun from(record: AttachmentRecord) = AttachmentRef(record.relation.attachmentUuid, record.relation.blobUuid,
            record.relation.originalFileName, record.mimeType, record.sizeBytes, record.sha256, record.relation.sortOrder)
        fun parse(value: Any?): AttachmentRef {
            val o = value.jsonObject()
            return AttachmentRef(o.string("attachmentUuid"),o.string("blobUuid"),o.string("originalFileName"),
                o.string("mimeType"),o.long("sizeBytes"),o.string("sha256"),o.long("sortOrder").also { require(it in 0..9) }.toInt()).also { it.validate() }
        }
    }
}

data class TransactionSnapshot(val transaction: TransactionEntity, val attachments: List<AttachmentRef>) {
    fun json(): Map<String, Any?> = with(transaction) { linkedMapOf(
        "transactionUuid" to transactionUuid, "type" to type.name, "amountCents" to amountCents,
        "transactionDate" to transactionDate, "createdAtMillis" to createdAtMillis, "updatedAtMillis" to updatedAtMillis,
        "isDeleted" to isDeleted, "deletedAtMillis" to deletedAtMillis,
        "expensePersonId" to expensePersonId, "expensePersonSnapshot" to expensePersonSnapshot,
        "expenseCategoryId" to expenseCategoryId, "expenseCategorySnapshot" to expenseCategorySnapshot,
        "incomeSource" to incomeSource, "note" to note, "attachments" to attachments.map { it.json() }).also {
            if (expensePeopleJson != null) it["expensePeople"] = selectedPeople().map { person -> person.json() }
        } }
    fun encode(): String = StrictJson.stringify(json())
    fun business(): Map<String, Any?> = json().filterKeys {
        it !in setOf("updatedAtMillis", "expensePersonId", "expenseCategoryId", "expensePersonSnapshot", "expensePeople")
    } + ("expensePeopleNames" to transaction.selectedPeople().map { it.name }.sorted())
    fun changes(after: TransactionSnapshot): List<String> {
        val labels = linkedMapOf("type" to "收支类型", "amountCents" to "金额", "transactionDate" to "日期",
            "createdAtMillis" to "时间", "expensePeopleNames" to "使用人", "expenseCategorySnapshot" to "分类",
            "incomeSource" to "来源", "note" to "备注", "attachments" to "附件", "isDeleted" to "删除状态")
        val b = business(); val a = after.business()
        return labels.filter { (key, _) -> b[key] != a[key] }.values.toList()
    }
    fun validate() {
        AttachmentPolicy.uuid(transaction.transactionUuid)
        // Ensure later export cannot lose malformed UTF-16 through UTF-8 replacement.
        listOfNotNull(transaction.note,transaction.incomeSource,transaction.expensePersonSnapshot,transaction.expenseCategorySnapshot)
            .forEach { StrictJson.stringify(it) }
        require(transaction.amountCents > 0) { "金额无效" }
        LocalDate.ofEpochDay(transaction.transactionDate)
        require(transaction.isDeleted == (transaction.deletedAtMillis != null)) { "删除状态无效" }
        require(!transaction.isDeleted || attachments.isEmpty()) { "已删除账单不能有当前附件" }
        require((transaction.note?.length ?: 0) <= 1000 && (transaction.incomeSource?.length ?: 0) <= 120)
        require((transaction.expensePersonSnapshot?.length ?: 0) <= 40 && (transaction.expenseCategorySnapshot?.length ?: 0) <= 40)
        transaction.expensePeopleJson?.let {
            val people = transaction.selectedPeople()
            PersonSelections.validate(people)
            require(transaction.expensePersonId == people.firstOrNull()?.id && transaction.expensePersonSnapshot == people.firstOrNull()?.name) { "使用人快照不一致" }
        }
        require(transaction.type != TransactionType.INCOME ||
            (transaction.expensePersonSnapshot == null && transaction.expenseCategorySnapshot == null &&
                transaction.expensePersonId == null && transaction.expenseCategoryId == null && transaction.selectedPeople().isEmpty()))
        require(transaction.type != TransactionType.EXPENSE || transaction.incomeSource == null)
        attachments.forEach { it.validate() }; AttachmentPolicy.validate(attachments.map { it.sizeBytes })
        require(attachments.map { it.attachmentUuid }.distinct().size == attachments.size) { "附件 UUID 重复" }
        require(attachments.map { it.sortOrder } == attachments.indices.toList()) { "附件顺序无效" }
    }
    companion object {
        fun decode(raw: String): TransactionSnapshot = parse(StrictJson.parse(raw))
        fun parse(value: Any?): TransactionSnapshot {
            val o = value.jsonObject()
            return TransactionSnapshot(TransactionEntity(
                type = TransactionType.valueOf(o.string("type")), amountCents = o.long("amountCents"),
                transactionDate = o.long("transactionDate"), createdAtMillis = o.long("createdAtMillis"), updatedAtMillis = o.long("updatedAtMillis"),
                expensePersonId = o.nullableLong("expensePersonId"), expensePersonSnapshot = o.nullableString("expensePersonSnapshot"),
                expensePeopleJson = if (o.containsKey("expensePeople")) PersonSelections.encode(PersonSelections.parse(o["expensePeople"])) else null,
                expenseCategoryId = o.nullableLong("expenseCategoryId"), expenseCategorySnapshot = o.nullableString("expenseCategorySnapshot"),
                incomeSource = o.nullableString("incomeSource"), note = o.nullableString("note"),
                transactionUuid = o.string("transactionUuid"), isDeleted = o.bool("isDeleted"), deletedAtMillis = o.nullableLong("deletedAtMillis")),
                o.array("attachments").map(AttachmentRef::parse)).also { it.validate() }
        }
    }
}
