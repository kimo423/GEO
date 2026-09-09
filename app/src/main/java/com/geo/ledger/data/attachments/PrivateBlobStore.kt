package com.geo.ledger.data.attachments

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** Immutable files: failed DB commits may leave orphans, but never destroy prior content. */
class PrivateBlobStore(private val filesRoot: File) {
    private val blobs = File(filesRoot, "blobs").apply { check(mkdirs() || isDirectory) }
    private val staging = File(filesRoot, "staging").apply { check(mkdirs() || isDirectory) }
    data class Stored(val key: String, val size: Long, val sha256: String)

    fun put(input: InputStream, limit: Long = AttachmentPolicy.MAX_FILE): Stored {
        val key = UUID.randomUUID().toString()
        val temp = File(staging, key)
        try {
            val copied = FileOutputStream(temp).use { output ->
                AttachmentPolicy.copy(input, output, limit,
                    if (limit < AttachmentPolicy.MAX_FILE) AttachmentPolicy.TOTAL_ERROR else AttachmentPolicy.FILE_ERROR
                ).also { output.fd.sync() }
            }
            val target = file(key)
            check(!target.exists() && temp.renameTo(target)) { "无法保存附件，请检查剩余空间" }
            return Stored(key, copied.size, copied.sha256)
        } finally { temp.delete() }
    }

    fun file(key: String): File {
        AttachmentPolicy.uuid(key)
        val result = File(blobs, key)
        require(result.canonicalFile.parentFile == blobs.canonicalFile) { "附件路径无效" }
        return result
    }

    fun verify(key: String, size: Long, hash: String) {
        val actual = file(key).inputStream().use { AttachmentPolicy.copy(it, DiscardOutput, AttachmentPolicy.MAX_FILE) }
        require(actual.size == size && actual.sha256 == hash) { "附件内容已损坏或丢失" }
    }

    fun copyTo(key: String, output: OutputStream) = file(key).inputStream().use { it.copyTo(output, 64 * 1024) }

    private object DiscardOutput : OutputStream() {
        override fun write(b: Int) {}
        override fun write(b: ByteArray, off: Int, len: Int) {}
    }
}
