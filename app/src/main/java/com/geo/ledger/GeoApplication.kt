package com.geo.ledger

import android.app.Application
import com.geo.ledger.data.LedgerRepository
import com.geo.ledger.data.local.GeoDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GeoApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: GeoDatabase by lazy { GeoDatabase.getInstance(this) }
    val repository: LedgerRepository by lazy {
        LedgerRepository(database, sharingScope = applicationScope,
            blobStore = com.geo.ledger.data.attachments.PrivateBlobStore(filesDir))
    }
    override fun onCreate() {
        super.onCreate()
        // Stale temporary sessions only. Never sweep live/historical blob storage.
        val cutoff=System.currentTimeMillis()-24L*60*60*1000
        applicationScope.launch(Dispatchers.IO) {
            val staging=java.io.File(filesDir,"staging")
            staging.listFiles()?.filter { it.lastModified()<cutoff }?.forEach { file ->
                if(file.canonicalFile.parentFile==staging.canonicalFile) file.deleteRecursively()
            }
        }
    }
}
