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
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class UpgradeRepositoryTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Test fun restoredCreationDraftEditsActiveRowButSameDraftIsNoop() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,GeoDatabase::class.java).build()
        val root=File(context.cacheDir,UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val store=PrivateBlobStore(root)
            val repo=LedgerRepository(db,blobStore=store)
            fun attach(name:String): AttachmentRecord {
                val stored=store.put(name.byteInputStream())
                return AttachmentRecord(TransactionAttachmentEntity(UUID.randomUUID().toString(),"",UUID.randomUUID().toString(),name,0,true,1),
                    stored.sha256,stored.size,"application/pdf",stored.key)
            }
            val a=attach("A.pdf"); val b=attach("B.pdf"); val c=attach("C.pdf")
            val original=TransactionDraft(TransactionType.INCOME,100,LocalDate.of(2026,9,9),attachments=listOf(a,b))
            val id=repo.saveTransaction(null,original,"restored-key")
            assertEquals(id,repo.saveTransaction(null,original,"restored-key"))
            assertTrue(db.historyDao().allAudit().isEmpty())
            assertEquals(id,repo.saveTransaction(null,original.copy(amountCents=200,attachments=listOf(a,c)),"restored-key"))
            assertEquals(1,db.transactionDao().getAllOrdered().size)
            assertEquals(200L,repo.getTransaction(id)!!.amountCents)
            val event=db.historyDao().allAudit().single()
            assertEquals(listOf("A.pdf","B.pdf"),TransactionSnapshot.decode(event.beforeSnapshotJson).attachments.map { it.originalFileName })
            assertEquals(listOf("A.pdf","C.pdf"),TransactionSnapshot.decode(event.afterSnapshotJson!!).attachments.map { it.originalFileName })
        } finally { db.close(); root.deleteRecursively() }
    }
    @Test fun activeOptionNamesUseImportNormalizationWithoutRewritingOldRows() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,GeoDatabase::class.java).build()
        try {
            val repo=LedgerRepository(db)
            val id=repo.addPerson("ABC")
            assertTrue(runCatching { repo.addPerson(" abc ") }.isFailure)
            repo.renamePerson(id,"abc")
            repo.addCategory("é")
            assertTrue(runCatching { repo.addCategory("e\u0301") }.isFailure)
            assertEquals(1,db.personOptionDao().getAll().size)
            assertEquals(1,db.expenseCategoryDao().getAll().size)
        } finally { db.close() }
    }
    @Test fun migrationFromEveryOldVersionPreservesRowsAndBalances() = runBlocking {
        for (version in 1..3) {
            val name = "migration-${UUID.randomUUID()}.db"
            val schema = File("schemas/com.geo.ledger.data.local.GeoDatabase/$version.json")
            val root = JSONObject(schema.readText()).getJSONObject("database")
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { sql ->
                val entities = root.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val e = entities.getJSONObject(i)
                    sql.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                    val indexes = e.getJSONArray("indices")
                    for (j in 0 until indexes.length()) sql.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                }
                sql.execSQL("INSERT INTO person_options (id,name,is_active,sort_order,created_at_millis,updated_at_millis) VALUES (1,'张三',1,0,1,1)")
                sql.execSQL("INSERT INTO expense_categories (id,name,is_active,sort_order,created_at_millis,updated_at_millis) VALUES (1,'耗材',1,0,1,1)")
                if (version >= 2) {
                    sql.execSQL("UPDATE person_options SET active_name_key=name")
                    sql.execSQL("UPDATE expense_categories SET active_name_key=name")
                }
                for (i in 1..6) {
                    sql.execSQL("INSERT INTO transactions (id,type,amount_cents,transaction_date,created_at_millis,updated_at_millis,expense_person_id,expense_person_snapshot,expense_category_id,expense_category_snapshot,note) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                        arrayOf<Any>(i, if (i % 2 == 0) "EXPENSE" else "INCOME", i * 100L, 20000L, 100L, 100L, 1L, "张三", 1L, "耗材", "旧账$i"))
                }
                sql.version = version
            }
            val db = GeoDatabase.openFileDatabase(context, name)
            try {
                val rows = db.transactionDao().getAllOrdered()
                assertEquals(6, rows.size)
                assertEquals((1L..6L).toList(), rows.map { it.id })
                assertEquals(6, rows.map { it.transactionUuid }.distinct().size)
                rows.forEach { AttachmentPolicy.uuid(it.transactionUuid); assertFalse(it.isDeleted); assertEquals("张三",it.expensePersonSnapshot); assertEquals(20000L,it.transactionDate) }
                assertEquals(-300L, LedgerCalculator.withRunningBalances(rows).last().balanceAfterCents)
                assertEquals(4, db.openHelper.readableDatabase.version)
            } finally { db.close(); context.deleteDatabase(name) }
        }
    }

    @Test fun auditContinuousNoopDeleteAndPrivateAttachments() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, GeoDatabase::class.java).build()
        val root = File(context.cacheDir, UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val store = PrivateBlobStore(root)
            var time = 100L
            val repo = LedgerRepository(db, nowMillis = { ++time }, blobStore = store)
            fun attachment(name: String): AttachmentRecord {
                val stored = name.byteInputStream().use { store.put(it) }
                return AttachmentRecord(TransactionAttachmentEntity(UUID.randomUUID().toString(), "", UUID.randomUUID().toString(), name,0,true,time),
                    stored.sha256, stored.size,"application/pdf",stored.key)
            }
            val a = attachment("A.pdf"); val b = attachment("B.pdf"); val c = attachment("C.pdf")
            val draft = TransactionDraft(TransactionType.INCOME,10000,LocalDate.of(2026,9,5), attachments=listOf(a,b))
            val id = repo.saveTransaction(null,draft,"K")
            val uuid = repo.getTransaction(id)!!.transactionUuid
            repo.saveTransaction(id,draft)
            assertEquals(0,db.historyDao().allAudit().size)
            repo.saveTransaction(id,draft.copy(amountCents=20000, attachments=listOf(a,c)))
            repo.saveTransaction(id,draft.copy(amountCents=30000, attachments=listOf(a,c)))
            val edits = db.historyDao().allAudit()
            assertEquals(2,edits.size)
            assertEquals(listOf(10000L,20000L), edits.map { TransactionSnapshot.decode(it.beforeSnapshotJson).transaction.amountCents })
            assertEquals(listOf("A.pdf","B.pdf"), TransactionSnapshot.decode(edits[0].beforeSnapshotJson).attachments.map { it.originalFileName })
            repo.deleteTransaction(id); repo.deleteTransaction(id)
            assertEquals(3,db.historyDao().allAudit().size)
            assertEquals(0,db.transactionDao().getAllOrdered().size)
            assertTrue(db.transactionDao().getById(id)!!.isDeleted)
            assertEquals(uuid,db.transactionDao().getById(id)!!.transactionUuid)
            assertEquals(id,repo.saveTransaction(null,draft,"K"))
            store.verify(b.internalStorageKey,b.sizeBytes,b.sha256)
            assertEquals(0,repo.activeAttachments(uuid).size)
        } finally { db.close(); root.deleteRecursively() }
    }
}
