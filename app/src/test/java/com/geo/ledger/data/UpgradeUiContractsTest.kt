package com.geo.ledger.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class UpgradeUiContractsTest {
    private fun source(path: String)=File("src/main/$path").readText()
    @Test fun startupCannotLaunchUpdateAndManualActionHasNoInstallPermission() {
        val nav=source("java/com/geo/ledger/ui/navigation/GeoApp.kt")
        assertFalse(nav.contains("AppUpdateGate()"))
        val vm=source("java/com/geo/ledger/update/AppUpdateViewModel.kt")
        assertFalse(vm.contains("init {"))
        val settings=source("java/com/geo/ledger/ui/settings/SettingsScreen.kt")
        assertTrue(settings.contains("tools.checkUpdate()"))
        assertFalse(source("AndroidManifest.xml").contains("REQUEST_INSTALL_PACKAGES"))
    }
    @Test fun fileProviderExposesOnlyPrivateBlobs() {
        val xml=source("res/xml/attachment_paths.xml")
        assertTrue(xml.contains("path=\"blobs/\""))
        assertFalse(xml.contains("root-path"));assertFalse(xml.contains("external-path"))
        assertFalse(xml.contains("path=\".\""))
        assertTrue(source("AndroidManifest.xml").contains("android:grantUriPermissions=\"true\""))
    }
}
