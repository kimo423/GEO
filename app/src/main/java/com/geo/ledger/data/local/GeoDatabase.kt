package com.geo.ledger.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class GeoTypeConverters {
    @TypeConverter
    fun transactionTypeToString(value: TransactionType): String = value.name

    @TypeConverter
    fun stringToTransactionType(value: String): TransactionType = TransactionType.valueOf(value)
}

@Database(
    entities = [TransactionEntity::class, PersonOptionEntity::class, ExpenseCategoryEntity::class,
        AttachmentBlobEntity::class, TransactionAttachmentEntity::class, AuditEventEntity::class],
    version = 5,
    exportSchema = true,
)
@TypeConverters(GeoTypeConverters::class)
abstract class GeoDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun personOptionDao(): PersonOptionDao
    abstract fun expenseCategoryDao(): ExpenseCategoryDao
    abstract fun historyDao(): HistoryDao

    companion object {
        const val DATABASE_NAME = "geo-ledger.db"

        @Volatile
        private var instance: GeoDatabase? = null

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateOptionTable(db, "person_options")
                migrateOptionTable(db, "expense_categories")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `client_op_key` TEXT")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_client_op_key` ON `transactions` (`client_op_key`)",
                )
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN transaction_uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE transactions ADD COLUMN is_deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN deleted_at_millis INTEGER")
                db.query("SELECT id FROM transactions").use { cursor ->
                    while (cursor.moveToNext()) {
                        db.execSQL("UPDATE transactions SET transaction_uuid=? WHERE id=?",
                            arrayOf(java.util.UUID.randomUUID().toString(), cursor.getLong(0)))
                    }
                }
                db.query("SELECT COUNT(*),COUNT(DISTINCT transaction_uuid),SUM(CASE WHEN transaction_uuid='' THEN 1 ELSE 0 END) FROM transactions").use {
                    check(it.moveToFirst() && it.getLong(0) == it.getLong(1) && it.getLong(2) == 0L) { "UUID migration validation failed" }
                }
                db.execSQL("CREATE UNIQUE INDEX index_transactions_transaction_uuid ON transactions(transaction_uuid)")
                db.execSQL("CREATE TABLE IF NOT EXISTS attachment_blobs (blobUuid TEXT NOT NULL PRIMARY KEY, sha256 TEXT NOT NULL, sizeBytes INTEGER NOT NULL, mimeType TEXT NOT NULL, internalStorageKey TEXT NOT NULL, createdAtMillis INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX index_attachment_blobs_internalStorageKey ON attachment_blobs(internalStorageKey)")
                db.execSQL("CREATE TABLE IF NOT EXISTS transaction_attachments (attachmentUuid TEXT NOT NULL PRIMARY KEY, transactionUuid TEXT NOT NULL, blobUuid TEXT NOT NULL, originalFileName TEXT NOT NULL, sortOrder INTEGER NOT NULL, isActive INTEGER NOT NULL, createdAtMillis INTEGER NOT NULL, removedAtMillis INTEGER, FOREIGN KEY(transactionUuid) REFERENCES transactions(transaction_uuid) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(blobUuid) REFERENCES attachment_blobs(blobUuid) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX index_transaction_attachments_transactionUuid ON transaction_attachments(transactionUuid)")
                db.execSQL("CREATE INDEX index_transaction_attachments_blobUuid ON transaction_attachments(blobUuid)")
                db.execSQL("CREATE TABLE IF NOT EXISTS audit_events (eventUuid TEXT NOT NULL PRIMARY KEY, transactionUuid TEXT NOT NULL, eventType TEXT NOT NULL, source TEXT NOT NULL, occurredAtMillis INTEGER NOT NULL, schemaVersion INTEGER NOT NULL, beforeSnapshotJson TEXT NOT NULL, afterSnapshotJson TEXT, FOREIGN KEY(transactionUuid) REFERENCES transactions(transaction_uuid) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX index_audit_events_transactionUuid ON audit_events(transactionUuid)")
                db.execSQL("CREATE INDEX index_audit_events_occurredAtMillis ON audit_events(occurredAtMillis)")
            }
        }

        fun getInstance(context: Context): GeoDatabase =
            instance ?: synchronized(this) {
                instance ?: openFileDatabase(context.applicationContext, DATABASE_NAME)
                    .also { instance = it }
            }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // NULL means legacy single-person data. Keep every original snapshot intact.
                db.execSQL("ALTER TABLE transactions ADD COLUMN expense_people_json TEXT")
            }
        }

        /** Same callback + migrations as production; used by tests with a dedicated file name. */
        fun openFileDatabase(context: Context, name: String): GeoDatabase =
            Room.databaseBuilder(context.applicationContext, GeoDatabase::class.java, name)
                // Version 1 is the baseline. Schema changes must add explicit migrations;
                // destructive fallback is intentionally not enabled because this is ledger data.
                .addCallback(DefaultCategoryCallback)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()

        internal fun migrateOptionTable(db: SupportSQLiteDatabase, table: String) {
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `active_name_key` TEXT")
            db.execSQL(
                """
                CREATE TEMP TABLE `${table}_keep_active` AS
                SELECT MIN(id) AS id FROM `$table` WHERE is_active = 1 GROUP BY name
                """.trimIndent(),
            )
            db.execSQL(
                """
                UPDATE `$table`
                SET is_active = 0
                WHERE is_active = 1
                  AND id NOT IN (SELECT id FROM `${table}_keep_active`)
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE `${table}_keep_active`")
            db.execSQL("UPDATE `$table` SET `active_name_key` = `name` WHERE is_active = 1")
            db.execSQL("UPDATE `$table` SET `active_name_key` = NULL WHERE is_active = 0")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_${table}_active_name_key` ON `$table` (`active_name_key`)",
            )
        }
    }

    object DefaultCategoryCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            val now = System.currentTimeMillis()
            listOf("设备", "耗材", "交通", "餐饮", "其他").forEachIndexed { index, name ->
                db.execSQL(
                    """
                    INSERT INTO expense_categories
                    (name, is_active, active_name_key, sort_order, created_at_millis, updated_at_millis)
                    VALUES (?, 1, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any>(name, name, index, now, now),
                )
            }
        }
    }
}
