package com.geo.ledger.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.geo.ledger.data.local.*
import com.geo.ledger.data.transfer.*
import com.geo.ledger.data.attachments.*
import com.geo.ledger.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.zip.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=android.app.Application::class)
class ArchiveRoundTripTest {
    @Test fun fullRestoreSameRecordAndConfigurationStrictReplacement()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,GeoDatabase::class.java).build()
        val root=File(context.cacheDir,UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val store=PrivateBlobStore(root); val repo=LedgerRepository(db,blobStore=store)
            val service=DataTransfer(repo)
            fun ref(): AttachmentRecord {
                val stored="invoice".byteInputStream().use { store.put(it) }
                return AttachmentRecord(TransactionAttachmentEntity(UUID.randomUUID().toString(),"",UUID.randomUUID().toString(),"发票.pdf",0,true,1),
                    stored.sha256,stored.size,"application/pdf",stored.key)
            }
            val draft=TransactionDraft(TransactionType.INCOME,50000,LocalDate.of(2026,9,5),incomeSource="科研:拨款",attachments=List(10){ref()})
            val b=repo.saveTransaction(null,draft)
            val c=repo.saveTransaction(null,draft.copy(amountCents=20000,attachments=emptyList()))
            val d=repo.saveTransaction(null,draft.copy(amountCents=30000,attachments=listOf(ref())))
            repo.saveTransaction(d,draft.copy(amountCents=31000,attachments=emptyList()))
            repo.deleteTransaction(d)
            val uuidB=repo.getTransaction(b)!!.transactionUuid
            val uuidC=repo.getTransaction(c)!!.transactionUuid
            val expected=db.transactionDao().getAllIncludingDeleted().map { it.transactionUuid to it.amountCents }
            val zip=File(root,"complete.geodata"); service.export(zip,"1.2.0",6)
            ZipFile(zip).use { archive ->
                assertNotNull(archive.getEntry("audit_logs.json"))
                val files=archive.entries().asSequence().map { it.name }.filter { it.startsWith("attachments/") }.toList()
                assertEquals(11,files.size); assertEquals(files.size,files.distinct().size)
                assertTrue(files.any { "history_only" in it }); assertFalse(files.any { uuidC.take(8) in it })
            }
            val staged=zip.inputStream().use { DataArchive.read(it,root) }
            staged.use { pack ->
                service.apply(pack,ImportMode.FULL_REPLACE)
                assertEquals(expected,db.transactionDao().getAllIncludingDeleted().map { it.transactionUuid to it.amountCents })
                assertEquals(2,db.historyDao().allAudit().size)
                val restoredB=db.transactionDao().getByUuid(uuidB)!!
                repo.saveTransaction(restoredB.id,draft.copy(amountCents=90000,attachments=null))
                val a=repo.saveTransaction(null,draft.copy(amountCents=1,attachments=emptyList()))
                // C is physically removed only in test to exercise unmatched import identity.
                db.openHelper.writableDatabase.execSQL("DELETE FROM transactions WHERE transaction_uuid=?",arrayOf(uuidC))
                service.apply(pack,ImportMode.MATCHING_ONLY)
                assertNotNull(repo.getTransaction(a)); assertNull(db.transactionDao().getByUuid(uuidC))
                assertEquals(50000,db.transactionDao().getByUuid(uuidB)!!.amountCents)
                assertTrue(db.historyDao().allAudit().any { it.source=="IMPORT" })
                assertEquals(10,repo.activeAttachments(uuidB).size)
            }
            repo.addPerson("张三"); repo.addPerson("李四")
            val config=ConfigPackage("now",listOf(ConfigOption("张三",9,true),ConfigOption("实验室",1,true)),emptyList())
            ConfigTransfer(repo).apply(config,ImportMode.MATCHING_ONLY)
            assertEquals(setOf("张三","李四"),db.personOptionDao().getAll().filter { it.isActive }.map { it.name }.toSet())
            assertEquals(9,db.personOptionDao().getAll().single { it.name=="张三" }.sortOrder)
            ConfigTransfer(repo).apply(config,ImportMode.FULL_REPLACE)
            assertEquals(setOf("张三","实验室"),db.personOptionDao().getAll().filter { it.isActive }.map { it.name }.toSet())
        } finally { db.close(); root.deleteRecursively() }
    }
    @Test fun rejectsCorruptZipAndLeavesDatabaseUntouched()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,GeoDatabase::class.java).build()
        val root=File(context.cacheDir,UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val repo=LedgerRepository(db,blobStore=PrivateBlobStore(root))
            repo.saveTransaction(null,TransactionDraft(TransactionType.INCOME,100,LocalDate.now()))
            val before=db.transactionDao().getAllIncludingDeleted()
            for(path in listOf("../evil","/absolute","C:\\evil","manifest.json")) {
                val zip=File(root,"bad.zip")
                ZipOutputStream(zip.outputStream()).use { out -> out.putNextEntry(ZipEntry(path));out.write("{}".toByteArray());out.closeEntry() }
                assertThrows(RuntimeException::class.java) { zip.inputStream().use { DataArchive.read(it,root) } }
                assertEquals(before,db.transactionDao().getAllIncludingDeleted())
            }
        } finally { db.close();root.deleteRecursively() }
    }

    @Test fun eventConflictRollsBackAndCleansOnlyNewImportFiles()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,GeoDatabase::class.java).build()
        val root=File(context.cacheDir,UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val store=PrivateBlobStore(root); val repo=LedgerRepository(db,blobStore=store)
            val draft=TransactionDraft(TransactionType.INCOME,100,LocalDate.now())
            val id=repo.saveTransaction(null,draft)
            repo.saveTransaction(id,draft.copy(amountCents=200))
            val beforeRows=db.transactionDao().getAllIncludingDeleted()
            val beforeEvents=db.historyDao().allAudit()
            val existingEvent=beforeEvents.single()
            val current=repo.getTransaction(id)!!
            val external=File(root,"incoming.pdf").apply { writeText("new imported blob") }
            val ref=AttachmentRef(UUID.randomUUID().toString(),UUID.randomUUID().toString(),"new.pdf","application/pdf",external.length(),DataArchive.hash(external.readBytes()),0)
            val badEvent=existingEvent.copy(occurredAtMillis=existingEvent.occurredAtMillis+1)
            val badData=ArchiveDataset(listOf(TransactionSnapshot(current,listOf(ref))),listOf(badEvent))
            val zip=File(root,"conflict.geodata")
            DataArchive.write(badData,zip,{external},"1.2.0",6)
            val filesBefore=File(root,"blobs").list()!!.toSet()
            zip.inputStream().use { DataArchive.read(it,root) }.use { pack ->
                for(mode in ImportMode.entries) {
                    var failed=false
                    try { DataTransfer(repo).apply(pack,mode) } catch(e: IllegalArgumentException) { failed=true }
                    assertTrue("conflicting immutable event must fail",failed)
                    assertEquals(beforeRows,db.transactionDao().getAllIncludingDeleted())
                    assertEquals(beforeEvents,db.historyDao().allAudit())
                    assertEquals(filesBefore,File(root,"blobs").list()!!.toSet())
                }
            }
        } finally { db.close();root.deleteRecursively() }
    }
}
