package com.geo.ledger.ui

import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.geo.ledger.GeoApplication
import com.geo.ledger.data.transfer.*
import com.geo.ledger.ui.settings.SettingsToolsViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=GeoApplication::class)
class SettingsToolsViewModelTest {
    @Test fun replacingPreviewClosesOldStagingAndMalformedConfigKeepsCurrentPreview() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app=ApplicationProvider.getApplicationContext<GeoApplication>()
        val owner=ViewModelStore()
        val vm=SettingsToolsViewModel(app).also { owner.put("settings",it) }
        val root=File(app.cacheDir,UUID.randomUUID().toString()).apply { mkdirs() }
        val staging=File(app.filesDir,"staging")
        fun importRoots()=staging.listFiles().orEmpty().filter { it.name.startsWith("import-") }.toSet()
        suspend fun inspect(file:File,config:Boolean) {
            vm.inspect(Uri.fromFile(file),config)
            withTimeout(10000) { while(vm.busy.value!=null) delay(10) }
        }
        try {
            val source=File(root,"backup.geodata")
            DataArchive.write(ArchiveDataset(emptyList(),emptyList()),source,{error("No blobs")},"1.2.0",6)
            inspect(source,false)
            assertNull(vm.message.value); assertNotNull(vm.archive.value)
            val previous=importRoots(); assertEquals(1,previous.size)
            inspect(source,false)
            assertNull(vm.message.value); assertNotNull(vm.archive.value)
            assertEquals(1,importRoots().size); assertTrue(previous.none { it.exists() })
            val current=vm.archive.value
            val invalid=File(root,"bad.geocfg").apply { writeBytes(byteArrayOf(0xc3.toByte(),0x28)) }
            inspect(invalid,true)
            assertNotNull(vm.message.value); assertSame(current,vm.archive.value)
            val good=File(root,"good.geocfg").apply { writeText(ConfigPackage("2026-09-09T00:00:00Z",emptyList(),emptyList()).encode("1.2.0",6)) }
            vm.message.value=null
            inspect(good,true)
            assertNull(vm.message.value); assertNotNull(vm.config.value); assertNull(vm.archive.value)
            assertTrue(importRoots().isEmpty())
            inspect(source,false)
            vm.applyImport(ImportMode.MATCHING_ONLY)
            withTimeout(10000) { while(vm.busy.value!=null) delay(10) }
            assertEquals("数据导入成功，已处理 0 笔账单",vm.message.value)
            assertNull(vm.archive.value); assertTrue(importRoots().isEmpty())
        } finally { owner.clear(); root.deleteRecursively(); Dispatchers.resetMain() }
    }
}
