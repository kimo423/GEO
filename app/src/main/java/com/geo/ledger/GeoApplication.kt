package com.geo.ledger

import android.app.Application
import com.geo.ledger.data.LedgerRepository
import com.geo.ledger.data.local.GeoDatabase

class GeoApplication : Application() {
    val database: GeoDatabase by lazy { GeoDatabase.getInstance(this) }
    val repository: LedgerRepository by lazy { LedgerRepository(database) }
}
