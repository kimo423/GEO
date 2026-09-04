package com.geo.ledger.data.local

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveOptionNameKeyTest {
    @Test
    fun activeRowsUseNormalizedNameAndInactiveRowsUseNull() {
        assertEquals("张三", activeOptionNameKey(isActive = true, normalizedName = "张三"))
        assertNull(activeOptionNameKey(isActive = false, normalizedName = "张三"))
        assertEquals("耗材", activeOptionNameKey(isActive = true, normalizedName = "耗材"))
        assertNull(activeOptionNameKey(isActive = false, normalizedName = "耗材"))
    }

    @Test
    fun schemaV2HasUniqueNullableActiveNameKeyOnBothOptionTables() {
        val schema = locateSchema("2.json").readText()
        assertTrue(schema.contains("\"version\": 2"))
        assertTrue(schema.contains("index_person_options_active_name_key"))
        assertTrue(schema.contains("index_expense_categories_active_name_key"))
        assertTrue(schema.contains("CREATE UNIQUE INDEX IF NOT EXISTS `index_person_options_active_name_key`"))
        assertTrue(schema.contains("CREATE UNIQUE INDEX IF NOT EXISTS `index_expense_categories_active_name_key`"))
        assertTrue(schema.contains("\"columnName\": \"active_name_key\""))
        assertFalse(schema.contains("fallbackToDestructiveMigration"))
    }

    @Test
    fun schemaV3AddsClientOpKeyWithoutDestructiveFallback() {
        val schema = locateSchema("3.json").readText()
        val source = locateDatabaseSource().readText()
        assertTrue(schema.contains("\"version\": 3"))
        assertTrue(schema.contains("client_op_key"))
        assertTrue(schema.contains("index_transactions_client_op_key"))
        assertTrue(source.contains("MIGRATION_2_3"))
        assertTrue(source.contains("addMigrations(MIGRATION_1_2, MIGRATION_2_3)"))
        assertFalse(source.contains("fallbackToDestructiveMigration"))
    }

    @Test
    fun schemaV1IsPreservedWithoutActiveNameKey() {
        val schema = locateSchema("1.json").readText()
        assertTrue(schema.contains("\"version\": 1"))
        assertFalse(schema.contains("active_name_key"))
        assertFalse(schema.contains("index_person_options_active_name_key"))
    }

    private fun locateSchema(fileName: String): File {
        val relative = "schemas/com.geo.ledger.data.local.GeoDatabase/$fileName"
        val candidates = listOf(
            File(relative),
            File("app/$relative"),
            File("../$relative"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Missing $relative (cwd=${File(".").canonicalPath})")
    }

    private fun locateDatabaseSource(): File {
        val relative = "src/main/java/com/geo/ledger/data/local/GeoDatabase.kt"
        val candidates = listOf(
            File(relative),
            File("app/$relative"),
            File("../$relative"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Missing $relative (cwd=${File(".").canonicalPath})")
    }
}
