package com.geo.ledger.data

import com.geo.ledger.data.attachments.PrivateBlobStore
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Assert.*
import java.io.InputStream

class PrivateBlobStoreTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun survivesExternalDeletionAndStoreRecreation() {
        val source = folder.newFile("original.pdf").apply { writeText("invoice") }
        val root = folder.newFolder("private")
        val original = PrivateBlobStore(root)
        val ref = source.inputStream().use { original.put(it) }
        assertTrue(source.delete())
        val restarted = PrivateBlobStore(root)
        restarted.verify(ref.key, ref.size, ref.sha256)
        assertEquals("invoice", restarted.file(ref.key).readText())
    }
    @Test fun sourceFailureLeavesNoPartialFile() {
        val root = folder.newFolder("private")
        val store = PrivateBlobStore(root)
        assertThrows(java.io.IOException::class.java) {
            store.put(object : InputStream() { override fun read(): Int = throw java.io.IOException("source gone") })
        }
        assertTrue(java.io.File(root,"blobs").listFiles()!!.isEmpty())
        assertTrue(java.io.File(root,"staging").listFiles()!!.isEmpty())
    }
    @Test fun sourceNamesNeverBecomeStorageKeys() {
        val store = PrivateBlobStore(folder.newFolder())
        listOf("../evil.pdf", "C:\\evil", "/etc/hosts", "").forEach {
            assertThrows(IllegalArgumentException::class.java) { store.file(it) }
        }
    }
}
