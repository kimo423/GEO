package com.geo.ledger.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.geo.ledger.GeoApplication
import com.geo.ledger.ui.navigation.GeoApp
import com.geo.ledger.ui.theme.GeoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.graphics.asAndroidBitmap

/** Host-side Compose wiring only. Does not claim real-device smoothness or SAF behavior. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=GeoApplication::class,qualifiers="w393dp-h852dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UpgradeComposeSmokeTest {
    @get:Rule val compose=createComposeRule()
    @Test fun settingsHistoryAndBackRenderWithoutStartupNetwork() {
        compose.setContent { GeoTheme { GeoApp() } }
        compose.onNodeWithText("设置").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("数据管理").assertExists()
        compose.onNodeWithText("导出账单数据").assertExists()
        screenshot("host-settings")
        compose.onNodeWithText("修改与删除记录").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("暂无修改记录\n编辑或删除账单后，将在这里保留历史。").assertExists()
        screenshot("host-history-empty")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("检查更新").performScrollTo().assertIsDisplayed()
        screenshot("host-settings-about")
        // Do not press this network action in a rendering smoke test.
        val application=androidx.test.core.app.ApplicationProvider.getApplicationContext<GeoApplication>()
        val repo=application.repository
        kotlinx.coroutines.runBlocking {
            val person=repo.addPerson("张三")
            val category=application.database.expenseCategoryDao().getAll().first { it.name=="耗材" }.id
            repo.saveTransaction(null,com.geo.ledger.domain.TransactionDraft(com.geo.ledger.data.local.TransactionType.INCOME,
                500000,java.time.LocalDate.now(),incomeSource="科研项目拨款"))
            val records=List(10) { i ->
                val stored="invoice $i".byteInputStream().use { repo.blobStore!!.put(it) }
                com.geo.ledger.data.local.AttachmentRecord(com.geo.ledger.data.local.TransactionAttachmentEntity(
                    java.util.UUID.randomUUID().toString(),"",java.util.UUID.randomUUID().toString(),"发票${i+1}.pdf",i,true,1),
                    stored.sha256,stored.size,"application/pdf",stored.key)
            }
            val draft=com.geo.ledger.domain.TransactionDraft(com.geo.ledger.data.local.TransactionType.EXPENSE,12600,
                java.time.LocalDate.now(),expensePersonId=person,expenseCategoryId=category,note="实验室耗材采购，保留原始发票。",attachments=records)
            val id=repo.saveTransaction(null,draft)
            repo.saveTransaction(id,draft.copy(amountCents=12800,note="实验室耗材采购，金额已核对。",attachments=records.take(2)))
        }
        compose.onNodeWithText("首页").performClick()
        compose.waitForIdle()
        screenshot("host-home-data")
        compose.onNodeWithText("账单").performClick()
        compose.waitForIdle()
        screenshot("host-bills-data")
        compose.onNodeWithContentDescription("修改与删除记录").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("修改账单").assertExists()
        screenshot("host-history-edit")
    }
    private fun screenshot(name: String) {
        val dir=java.io.File("../docs/ui-review").apply { mkdirs() }
        compose.runOnIdle {
            val activity=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).first()
            val decor=activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(decor.width,decor.height,android.graphics.Bitmap.Config.ARGB_8888)
            decor.draw(android.graphics.Canvas(bitmap))
            java.io.File(dir,"$name.png").outputStream().use {
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
            }
            bitmap.recycle()
        }
    }
}
