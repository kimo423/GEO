package com.geo.ledger.ui

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.geo.ledger.GeoApplication
import com.geo.ledger.data.local.TransactionType
import com.geo.ledger.domain.TransactionDraft
import com.geo.ledger.ui.navigation.GeoApp
import com.geo.ledger.ui.theme.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Actual host Compose rendering, not device performance or OEM font validation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = GeoApplication::class, qualifiers = "w393dp-h852dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SkinComposeTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<GeoApplication>()
    private val fixtureIds = mutableListOf<Long>()
    private val fixturePersonIds = mutableListOf<Long>()

    @After fun removeOwnedFixtures() = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        // Robolectric can share the application's singleton across classes. Remove only this
        // test's records/events, leaving default categories and unrelated fixtures untouched.
        val db = app.database.openHelper.writableDatabase
        fixtureIds.forEach { id ->
            db.execSQL("DELETE FROM audit_events WHERE transactionUuid IN (SELECT transaction_uuid FROM transactions WHERE id=?)", arrayOf(id))
            db.execSQL("DELETE FROM transactions WHERE id=?", arrayOf(id))
        }
        fixturePersonIds.forEach { db.execSQL("DELETE FROM person_options WHERE id=?", arrayOf(it)) }
        SkinPreferences(app).select(GeoSkin.CLASSIC)
    }

    @Test fun unknownPreferenceFallsBackAndSelectionSurvivesNewStore() {
        assertEquals(GeoSkin.CLASSIC, SkinPreferences(app).read())
        app.getSharedPreferences("geo_appearance", 0).edit().putString("skin", "future_skin").commit()
        assertEquals(GeoSkin.CLASSIC, SkinPreferences(app).read())
        SkinPreferences(app).select(GeoSkin.GRAPHITE)
        assertEquals(GeoSkin.GRAPHITE, SkinPreferences(app).read())
        SkinPreferences(app).select(GeoSkin.CLASSIC)
        assertEquals(GeoSkin.CLASSIC, SkinPreferences(app).read())
    }

    @Test fun multiPersonControlsWorkInBothSkinsAndPersist() {
        val repo = app.repository
        val names = listOf("示例甲", "示例乙", "示例丙")
        runBlocking { names.forEach { fixturePersonIds += repo.addPerson(it) } }
        SkinPreferences(app).select(GeoSkin.GRAPHITE)
        var select: (GeoSkin) -> Unit = {}
        compose.setContent { GeoSkinHost {
            val change = LocalSelectSkin.current
            SideEffect { select = change }
            GeoApp()
        } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("当前余额").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("记一笔").performClick()
        compose.onNodeWithText("支出金额").performTextInput("7.00")
        compose.onNodeWithText("全选使用人").performScrollTo().performClick()
        compose.onNodeWithText("使用人（多选） · 已选 3 人").assertExists()
        screenshot("multi-person-graphite")
        compose.onNodeWithText("清空使用人").performClick()
        compose.onNodeWithText("使用人（多选） · 已选 0 人").assertExists()
        compose.onNodeWithText("示例甲").performClick()
        compose.onNodeWithText("示例丙").performClick()
        compose.runOnIdle { select(GeoSkin.CLASSIC) }
        compose.onNodeWithText("使用人（多选） · 已选 2 人").assertExists()
        screenshot("multi-person-classic")
        compose.onNodeWithText("保存支出 ¥7.00").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("当前余额").fetchSemanticsNodes().isNotEmpty() }
        val tx = runBlocking { repo.ledgerEntries.first { rows -> rows.any { it.transaction.amountCents == 700L } }.first { it.transaction.amountCents == 700L }.transaction }
        fixtureIds += tx.id
        assertEquals(listOf("示例甲", "示例丙"), com.geo.ledger.data.local.PersonSelections.decode(tx.expensePeopleJson!!).map { it.name })
    }

    @Test fun skinsSwitchInPlaceAndEditorDraftSurvives() {
        SkinPreferences(app).select(GeoSkin.CLASSIC)
        val repo = app.repository
        runBlocking {
            fixtureIds += repo.saveTransaction(null, TransactionDraft(TransactionType.INCOME, 800000, LocalDate.now(), incomeSource = "项目收入"))
            fixtureIds += repo.saveTransaction(null, TransactionDraft(TransactionType.EXPENSE, 24680, LocalDate.now(), note = "办公用品采购"))
        }
        val before = runBlocking { repo.ledgerEntries.first() }
        var select: (GeoSkin) -> Unit = {}
        compose.setContent {
            GeoSkinHost {
                val change = LocalSelectSkin.current
                SideEffect { select = change }
                GeoApp()
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("当前余额").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("¥7,753.20").assertExists()
        screenshot("classic-home")
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("石墨银").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("主题皮肤").assertIsDisplayed()
        assertEquals(GeoSkin.GRAPHITE, SkinPreferences(app).read())
        screenshot("graphite-settings")
        compose.onNodeWithText("首页").performClick()
        compose.onNodeWithText("¥7,753.20").assertExists()
        screenshot("graphite-home")
        compose.onNodeWithText("账单").performClick()
        compose.waitForIdle()
        screenshot("graphite-bills")
        compose.onNodeWithText("首页").performClick()
        compose.onNodeWithText("记一笔").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("支出金额").performTextInput("88.60")
        screenshot("graphite-editor")
        compose.runOnIdle { select(GeoSkin.CLASSIC) }
        compose.onNodeWithText("88.60").assertExists()
        compose.runOnIdle { select(GeoSkin.GRAPHITE) }
        compose.onNodeWithText("88.60").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("¥7,753.20").assertExists()
        compose.onNodeWithText("办公用品采购").performScrollTo().performClick()
        compose.waitForIdle()
        screenshot("graphite-detail")
        compose.onNodeWithText("-¥246.80").assertExists()
        assertEquals(before, runBlocking { repo.ledgerEntries.first() })
        compose.onNodeWithText("编辑").performClick()
        compose.onNodeWithText("支出金额").performTextClearance()
        compose.onNodeWithText("支出金额").performTextInput("249.80")
        compose.onNodeWithText("保存支出 ¥249.80").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("-¥249.80").fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
        compose.onNodeWithText("删除").performClick()
        compose.onNodeWithText("删除这笔账？").assertExists()
        compose.onAllNodesWithText("删除").onLast().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("当前余额").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("¥8,000.00").onFirst().assertExists()
        assertEquals(1, runBlocking { repo.ledgerEntries.first() }.size)
    }

    @Test
    @Config(qualifiers = "w320dp-h740dp")
    fun smallScreenLargeFontSkinSelectionAndEmptyHome() {
        val config = android.content.res.Configuration(app.resources.configuration).apply { fontScale = 1.5f }
        app.resources.updateConfiguration(config, app.resources.displayMetrics)
        SkinPreferences(app).select(GeoSkin.GRAPHITE)
        compose.setContent { GeoSkinHost { GeoApp() } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("当前余额").fetchSemanticsNodes().isNotEmpty() }
        screenshot("graphite-small-large-font")
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("经典").performScrollTo().performClick()
        assertEquals(GeoSkin.CLASSIC, SkinPreferences(app).read())
        compose.onNodeWithText("主题皮肤").assertExists()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val dir = java.io.File("../docs/skin-preview").apply { mkdirs() }
        compose.runOnIdle {
            val activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).first()
            val decor = activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(decor.width, decor.height, android.graphics.Bitmap.Config.ARGB_8888)
            decor.draw(android.graphics.Canvas(bitmap))
            java.io.File(dir, "$name.png").outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }
}
