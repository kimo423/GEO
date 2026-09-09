package com.geo.ledger.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.geo.ledger.data.local.GeoDatabase
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Host execution does not substitute for phone UI verification. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RoomHostSmokeTest {
    @Test fun opensRealRoomSchema() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, GeoDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
                db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM transactions").use {
                    it.moveToFirst()
                    assertEquals(0, it.getInt(0))
                }
        } finally { db.close() }
    }
}
