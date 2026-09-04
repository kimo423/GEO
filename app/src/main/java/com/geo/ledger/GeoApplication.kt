package com.geo.ledger

import android.app.Application
import com.geo.ledger.data.LedgerRepository
import com.geo.ledger.data.local.GeoDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class GeoApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: GeoDatabase by lazy { GeoDatabase.getInstance(this) }
    val repository: LedgerRepository by lazy {
        LedgerRepository(database, sharingScope = applicationScope)
    }
}
