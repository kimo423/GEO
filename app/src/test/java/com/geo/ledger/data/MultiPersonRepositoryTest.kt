package com.geo.ledger.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.geo.ledger.data.attachments.PrivateBlobStore
import com.geo.ledger.data.local.*
import com.geo.ledger.data.transfer.*
import com.geo.ledger.domain.TransactionDraft
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.zip.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MultiPersonRepositoryTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun manyPeopleSurviveEditingAuditAndBothImportModes() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, GeoDatabase::class.java).build()
        val target = Room.inMemoryDatabaseBuilder(context, GeoDatabase::class.java).build()
        val root = File(context.cacheDir, "people-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val repo = LedgerRepository(db, blobStore = PrivateBlobStore(File(root, "source")))
            val other = LedgerRepository(target, blobStore = PrivateBlobStore(File(root, "target")))
            val people = (1..128).map { n -> PersonSelection(repo.addPerson("使用人$n"), "使用人$n") }
            val blob = repo.blobStore!!.put("合成收据".byteInputStream())
            val attachment = AttachmentRecord(TransactionAttachmentEntity(UUID.randomUUID().toString(), "", UUID.randomUUID().toString(), "收据.txt", 0, true, 1),
                blob.sha256, blob.size, "text/plain", blob.key)
            val draft = TransactionDraft(TransactionType.EXPENSE, 10000, LocalDate.of(2026, 9, 9), expensePeople = people, attachments = listOf(attachment))
            val id = repo.saveTransaction(null, draft)
            assertEquals(128, repo.getTransaction(id)!!.selectedPeople().size)
            assertEquals(10000L, repo.getTransaction(id)!!.amountCents)
            repo.renamePerson(people[0].id!!, "已改名")
            repo.deactivatePerson(people[1].id!!)
            val remaining = people.filterIndexed { index, _ -> index != 60 }
            repo.saveTransaction(id, draft.copy(expensePeople = remaining))
            assertEquals(remaining, repo.getTransaction(id)!!.selectedPeople())
            val event = db.historyDao().allAudit().single()
            val before = TransactionSnapshot.decode(event.beforeSnapshotJson)
            val after = TransactionSnapshot.decode(event.afterSnapshotJson!!)
            assertEquals(people, before.transaction.selectedPeople())
            assertEquals(listOf("使用人"), before.changes(after))
            repo.saveTransaction(id, draft.copy(expensePeople = remaining.reversed()))
            assertEquals(1, db.historyDao().allAudit().size) // order alone isn't a financial edit

            other.addPerson("本机已有项")
            val localFirst = other.addPerson("使用人1")
            val backup = File(root, "all.geodata")
            DataTransfer(repo).export(backup, "test", 8)
            DataArchive.read(backup.inputStream(), root).use { pack ->
                DataTransfer(other).apply(pack, ImportMode.FULL_REPLACE)
                var restored = target.transactionDao().getAllOrdered().single()
                assertEquals(remaining.map { it.name }, restored.selectedPeople().map { it.name })
                assertEquals(localFirst, restored.selectedPeople().first().id)
                assertTrue(restored.selectedPeople().drop(1).all { it.id == null })
                assertEquals(1, other.activeAttachments(restored.transactionUuid).size)
                other.saveTransaction(restored.id, draft.copy(expensePeople = restored.selectedPeople(), note = "本地修改", attachments = null))
                DataTransfer(other).apply(pack, ImportMode.MATCHING_ONLY)
                restored = target.transactionDao().getAllOrdered().single()
                assertNull(restored.note)
                assertEquals(127, restored.selectedPeople().size)
                other.deleteTransaction(restored.id)
                val deleted = target.historyDao().allAudit().last { it.eventType == "DELETE" }
                assertEquals(127, TransactionSnapshot.decode(deleted.beforeSnapshotJson).transaction.selectedPeople().size)
            }
        } finally { db.close(); target.close(); root.deleteRecursively() }
    }

    @Test fun oldSinglePersonArchiveAndDelimitedNamesRemainLossless() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, GeoDatabase::class.java).build()
        val root = File(context.cacheDir, "legacy-people-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val repo = LedgerRepository(db, blobStore = PrivateBlobStore(root))
            val personId = repo.addPerson("甲、乙")
            val draft = TransactionDraft(TransactionType.EXPENSE, 123, LocalDate.now(), expensePersonId = personId, personSelectionEdited = true)
            val id = repo.saveTransaction(null, draft)
            val old = repo.getTransaction(id)!!
            assertNull(old.expensePeopleJson)
            assertEquals(listOf(PersonSelection(personId, "甲、乙")), old.selectedPeople())
            val encoded = TransactionSnapshot(old, emptyList()).encode()
            assertFalse(encoded.contains("expensePeople"))
            val file = File(root, "legacy.geodata")
            DataTransfer(repo).export(file, "1.2.0", 6)
            val entries = ZipFile(file).use { zip -> zip.entries().asSequence().associate { it.name to zip.getInputStream(it).readBytes() } }.toMutableMap()
            val manifest = StrictJson.parse(entries.getValue("manifest.json").toString(Charsets.UTF_8)).jsonObject().toMutableMap()
            manifest["formatVersion"] = 1
            entries["manifest.json"] = StrictJson.stringify(manifest).toByteArray()
            ZipOutputStream(file.outputStream()).use { zip -> entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } }
            DataArchive.read(file.inputStream(), root).use { assertEquals(encoded, it.dataset.transactions.single().encode()) }
            val second = PersonSelection(repo.addPerson("丙"), "丙")
            repo.saveTransaction(id, draft.copy(expensePeople = old.selectedPeople() + second))
            assertEquals(listOf("甲、乙", "丙"), repo.getTransaction(id)!!.selectedPeople().map { it.name })
            repo.saveTransaction(id, draft.copy(expensePeople = emptyList()))
            assertTrue(repo.getTransaction(id)!!.selectedPeople().isEmpty())
            assertNull(repo.getTransaction(id)!!.expensePersonSnapshot)
        } finally { db.close(); root.deleteRecursively() }
    }
}
