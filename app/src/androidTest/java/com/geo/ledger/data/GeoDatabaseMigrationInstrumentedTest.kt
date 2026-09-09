package com.geo.ledger.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geo.ledger.data.local.GeoDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeoDatabaseMigrationInstrumentedTest {
    @Test
    fun migratesV1ToUniqueActiveNameKeyWithoutDestroyingRows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "geo-v1-to-v2-migration.db"
        context.deleteDatabase(name)

        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { sqlite ->
            sqlite.execSQL(V1_TRANSACTIONS)
            sqlite.execSQL(V1_PERSON_OPTIONS)
            sqlite.execSQL(V1_EXPENSE_CATEGORIES)
            V1_INDICES.forEach(sqlite::execSQL)
            sqlite.execSQL(
                "INSERT INTO person_options (name, is_active, sort_order, created_at_millis, updated_at_millis) VALUES ('张三', 1, 0, 1, 1)",
            )
            sqlite.execSQL(
                "INSERT INTO person_options (name, is_active, sort_order, created_at_millis, updated_at_millis) VALUES ('张三', 0, 1, 2, 2)",
            )
            sqlite.execSQL(
                "INSERT INTO person_options (name, is_active, sort_order, created_at_millis, updated_at_millis) VALUES ('张三', 1, 2, 3, 3)",
            )
            sqlite.execSQL(
                "INSERT INTO expense_categories (name, is_active, sort_order, created_at_millis, updated_at_millis) VALUES ('耗材', 1, 0, 1, 1)",
            )
            sqlite.execSQL(
                "INSERT INTO expense_categories (name, is_active, sort_order, created_at_millis, updated_at_millis) VALUES ('耗材', 0, 1, 2, 2)",
            )
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            sqlite.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '055e3729edffa723e7e668af75d2e503')",
            )
            sqlite.version = 1
        }

        val migrated = Room.databaseBuilder(context, GeoDatabase::class.java, name)
            .addMigrations(GeoDatabase.MIGRATION_1_2, GeoDatabase.MIGRATION_2_3, GeoDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        try {
            val persons = queryOptions(migrated, "person_options")
            assertEquals(3, persons.size)
            assertEquals(listOf(1L, 2L, 3L), persons.map { it.id })
            assertEquals("张三", persons[0].name)
            assertEquals(1, persons[0].isActive)
            assertEquals("张三", persons[0].activeNameKey)
            assertEquals(0, persons[1].isActive)
            assertNull(persons[1].activeNameKey)
            assertEquals(0, persons[2].isActive)
            assertNull(persons[2].activeNameKey)

            val categories = queryOptions(migrated, "expense_categories")
            assertEquals(2, categories.size)
            assertEquals("耗材", categories[0].activeNameKey)
            assertEquals(1, categories[0].isActive)
            assertNull(categories[1].activeNameKey)
            assertEquals(0, categories[1].isActive)

            val indexes = migrated.openHelper.readableDatabase
                .query("SELECT name FROM sqlite_master WHERE type = 'index' AND name LIKE 'index_%active_name_key'")
            val indexNames = buildList {
                indexes.use { cursor ->
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }
            assertTrue(indexNames.contains("index_person_options_active_name_key"))
            assertTrue(indexNames.contains("index_expense_categories_active_name_key"))
        } finally {
            migrated.close()
        }

        val reopened = Room.databaseBuilder(context, GeoDatabase::class.java, name)
            .addMigrations(GeoDatabase.MIGRATION_1_2, GeoDatabase.MIGRATION_2_3, GeoDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        try {
            val persons = queryOptions(reopened, "person_options")
            assertEquals(3, persons.size)
            assertEquals("张三", persons.single { it.isActive == 1 }.activeNameKey)
        } finally {
            reopened.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migratesV1ToV2ToV3WithoutDestroyingTransactions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "geo-v1-v2-v3-migration.db"
        context.deleteDatabase(name)

        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { sqlite ->
            sqlite.execSQL(V1_TRANSACTIONS)
            sqlite.execSQL(V1_PERSON_OPTIONS)
            sqlite.execSQL(V1_EXPENSE_CATEGORIES)
            V1_INDICES.forEach(sqlite::execSQL)
            sqlite.execSQL(
                """
                INSERT INTO transactions
                (type, amount_cents, transaction_date, created_at_millis, updated_at_millis, income_source)
                VALUES ('INCOME', 500, 19723, 1, 1, '拨款')
                """.trimIndent(),
            )
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            sqlite.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '055e3729edffa723e7e668af75d2e503')",
            )
            sqlite.version = 1
        }

        val migrated = Room.databaseBuilder(context, GeoDatabase::class.java, name)
            .addMigrations(GeoDatabase.MIGRATION_1_2, GeoDatabase.MIGRATION_2_3, GeoDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        try {
            val db = migrated.openHelper.readableDatabase
            val columns = db.query("PRAGMA table_info(transactions)").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(1))
                }
            }
            assertTrue(columns.contains("client_op_key"))
            val row = db.query(
                "SELECT amount_cents, income_source, client_op_key FROM transactions WHERE id = 1",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                Triple(
                    cursor.getLong(0),
                    cursor.getString(1),
                    if (cursor.isNull(2)) null else cursor.getString(2),
                )
            }
            assertEquals(500L, row.first)
            assertEquals("拨款", row.second)
            assertNull(row.third)
            val indexes = db.query(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_transactions_client_op_key'",
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }
            assertEquals(listOf("index_transactions_client_op_key"), indexes)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    private fun queryOptions(database: GeoDatabase, table: String): List<MigratedOption> {
        val cursor = database.openHelper.readableDatabase.query(
            "SELECT id, name, is_active, active_name_key FROM `$table` ORDER BY id",
        )
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(
                        MigratedOption(
                            id = it.getLong(0),
                            name = it.getString(1),
                            isActive = it.getInt(2),
                            activeNameKey = if (it.isNull(3)) null else it.getString(3),
                        ),
                    )
                }
            }
        }
    }

    private data class MigratedOption(
        val id: Long,
        val name: String,
        val isActive: Int,
        val activeNameKey: String?,
    )

    private companion object {
        const val V1_TRANSACTIONS = """
            CREATE TABLE IF NOT EXISTS `transactions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `type` TEXT NOT NULL,
                `amount_cents` INTEGER NOT NULL,
                `transaction_date` INTEGER NOT NULL,
                `created_at_millis` INTEGER NOT NULL,
                `updated_at_millis` INTEGER NOT NULL,
                `expense_person_id` INTEGER,
                `expense_person_snapshot` TEXT,
                `expense_category_id` INTEGER,
                `expense_category_snapshot` TEXT,
                `income_source` TEXT,
                `note` TEXT
            )
        """
        const val V1_PERSON_OPTIONS = """
            CREATE TABLE IF NOT EXISTS `person_options` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `is_active` INTEGER NOT NULL,
                `sort_order` INTEGER NOT NULL,
                `created_at_millis` INTEGER NOT NULL,
                `updated_at_millis` INTEGER NOT NULL
            )
        """
        const val V1_EXPENSE_CATEGORIES = """
            CREATE TABLE IF NOT EXISTS `expense_categories` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `is_active` INTEGER NOT NULL,
                `sort_order` INTEGER NOT NULL,
                `created_at_millis` INTEGER NOT NULL,
                `updated_at_millis` INTEGER NOT NULL
            )
        """
        val V1_INDICES = listOf(
            "CREATE INDEX IF NOT EXISTS `index_transactions_transaction_date` ON `transactions` (`transaction_date`)",
            "CREATE INDEX IF NOT EXISTS `index_transactions_type` ON `transactions` (`type`)",
            "CREATE INDEX IF NOT EXISTS `index_transactions_created_at_millis` ON `transactions` (`created_at_millis`)",
            "CREATE INDEX IF NOT EXISTS `index_transactions_expense_person_id` ON `transactions` (`expense_person_id`)",
            "CREATE INDEX IF NOT EXISTS `index_transactions_expense_category_id` ON `transactions` (`expense_category_id`)",
            "CREATE INDEX IF NOT EXISTS `index_person_options_is_active_sort_order` ON `person_options` (`is_active`, `sort_order`)",
            "CREATE INDEX IF NOT EXISTS `index_expense_categories_is_active_sort_order` ON `expense_categories` (`is_active`, `sort_order`)",
        )
    }
}
