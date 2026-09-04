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
    entities = [TransactionEntity::class, PersonOptionEntity::class, ExpenseCategoryEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(GeoTypeConverters::class)
abstract class GeoDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun personOptionDao(): PersonOptionDao
    abstract fun expenseCategoryDao(): ExpenseCategoryDao

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

        fun getInstance(context: Context): GeoDatabase =
            instance ?: synchronized(this) {
                instance ?: openFileDatabase(context.applicationContext, DATABASE_NAME)
                    .also { instance = it }
            }

        /** Same callback + migrations as production; used by tests with a dedicated file name. */
        fun openFileDatabase(context: Context, name: String): GeoDatabase =
            Room.databaseBuilder(context.applicationContext, GeoDatabase::class.java, name)
                // Version 1 is the baseline. Schema changes must add explicit migrations;
                // destructive fallback is intentionally not enabled because this is ledger data.
                .addCallback(DefaultCategoryCallback)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
