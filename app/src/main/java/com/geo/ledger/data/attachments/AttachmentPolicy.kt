package com.geo.ledger.data.attachments

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.text.Normalizer
import java.util.UUID

object AttachmentPolicy {
    const val MAX_COUNT = 10
    const val MAX_FILE = 10L * 1024 * 1024
    const val MAX_TOTAL = 30L * 1024 * 1024
    const val COUNT_ERROR = "每笔账最多添加 10 个附件"
    const val FILE_ERROR = "单个附件不能超过 10 MB"
    const val TOTAL_ERROR = "全部附件总大小不能超过 30 MB"
    fun truncateText(value: String, limit: Int): String = value.take(limit).let {
        if(it.lastOrNull()?.let(Character::isHighSurrogate)==true) it.dropLast(1) else it
    }
    fun validate(sizes: List<Long>) {
        require(sizes.size <= MAX_COUNT) { COUNT_ERROR }
        require(sizes.all { it in 0..MAX_FILE }) { FILE_ERROR }
        require(sizes.sum() <= MAX_TOTAL) { TOTAL_ERROR }
    }
    fun uuid(value: String): String {
        require(runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)) { "UUID 无效" }
        return value
    }
    fun safeName(raw: String, max: Int = 96): String = Normalizer.normalize(raw, Normalizer.Form.NFC)
        .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
        .replace(Regex("_+"), "_").trim().trimEnd('.', ' ').take(max).let { if(it.lastOrNull()?.let(Character::isHighSurrogate)==true) it.dropLast(1) else it }.trimEnd('.', ' ')
        .ifBlank { "attachment" }

    data class Copied(val size: Long, val sha256: String)
    fun copy(input: InputStream, output: OutputStream, limit: Long = MAX_FILE,
        limitMessage: String = "文件超过允许大小"): Copied {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var size = 0L
        var emptyReads = 0
        while (true) {
            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
            val n = input.read(buffer)
            if (n < 0) break
            if (n == 0) {
                if (++emptyReads > 100) throw java.io.IOException("附件来源持续无法读取")
                continue
            }
            emptyReads=0
            size += n
            require(size <= limit) { limitMessage }
            digest.update(buffer, 0, n); output.write(buffer, 0, n)
        }
        return Copied(size, digest.digest().joinToString("") { "%02x".format(it) })
    }
}
