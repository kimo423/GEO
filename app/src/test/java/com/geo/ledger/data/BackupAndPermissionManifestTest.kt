package com.geo.ledger.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupAndPermissionManifestTest {
    @Test
    fun sourceManifestDisablesBackupAndOmitsDangerousPermissions() {
        val manifest = locate("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertFalse(manifest.contains("android:allowBackup=\"true\""))
        assertTrue(manifest.contains("android.permission.INTERNET"))
        val permissions = Regex("<uses-permission\\b[^>]*>").findAll(manifest).map { it.value }.toList()
        assertEquals(listOf("<uses-permission android:name=\"android.permission.INTERNET\" />"), permissions)
        DANGEROUS_PERMISSIONS.forEach { permission ->
            assertFalse("Manifest must not request $permission", manifest.contains(permission))
        }
    }

    @Test
    fun backupAndExtractionRulesExcludeDatabaseDomain() {
        val backupRules = locate("src/main/res/xml/backup_rules.xml").readText()
        assertTrue(backupRules.contains("<exclude domain=\"database\""))
        assertFalse(backupRules.contains("<include domain=\"database\""))

        val extractionRules = locate("src/main/res/xml/data_extraction_rules.xml").readText()
        assertTrue(extractionRules.contains("<cloud-backup>"))
        assertTrue(extractionRules.contains("<device-transfer>"))
        assertTrue(extractionRules.contains("<exclude domain=\"database\""))
        assertFalse(extractionRules.contains("<include domain=\"database\""))
    }

    @Test
    fun productionDatabaseBuilderDoesNotUseDestructiveMigration() {
        val source = locate("src/main/java/com/geo/ledger/data/local/GeoDatabase.kt").readText()
        assertFalse(source.contains("fallbackToDestructiveMigration"))
        assertTrue(source.contains("MIGRATION_1_2"))
        assertTrue(source.contains("MIGRATION_2_3"))
        assertTrue(source.contains("MIGRATION_3_4"))
        assertTrue(source.contains("MIGRATION_4_5"))
        assertTrue(source.contains("version = 5"))
    }

    private fun locate(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Missing $relativePath (cwd=${File(".").canonicalPath})")
    }

    private companion object {
        val DANGEROUS_PERMISSIONS = listOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
        )
    }
}
