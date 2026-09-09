package com.geo.ledger.update

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val validApk = "https://github.com/kimo423/GEO/releases/latest/download/GEO.apk"

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun versionComparisonUsesIntegerCodeOnly() {
        assertTrue(AppUpdatePolicy.shouldPrompt(2, 3))
        assertFalse(AppUpdatePolicy.shouldPrompt(2, 2))
        assertFalse(AppUpdatePolicy.shouldPrompt(2, 1))
        assertFalse(AppUpdatePolicy.shouldPrompt(2, 0))
    }

    @Test
    fun validManifestParses() {
        val parsed = AppUpdatePolicy.parseAndValidate(validJson(3, "1.2.0"))
        assertNotNull(parsed)
        assertEquals(3, parsed!!.versionCode)
        assertEquals("1.2.0", parsed.versionName)
        assertEquals(validApk, parsed.apkUrl)
    }

    @Test
    fun invalidManifestsAreRejected() {
        assertNull(AppUpdatePolicy.parseAndValidate(""))
        assertNull(AppUpdatePolicy.parseAndValidate("{"))
        assertNull(AppUpdatePolicy.parseAndValidate("""{"versionCode":0,"versionName":"1","releaseNotes":"n","apkUrl":"$validApk"}"""))
        assertNull(AppUpdatePolicy.parseAndValidate("""{"versionCode":3,"versionName":"","releaseNotes":"n","apkUrl":"$validApk"}"""))
        assertNull(AppUpdatePolicy.parseAndValidate("""{"versionCode":3,"versionName":"1","releaseNotes":"","apkUrl":"$validApk"}"""))
        assertNull(AppUpdatePolicy.parseAndValidate("""{"versionCode":3,"versionName":"1","releaseNotes":"n","apkUrl":"http://github.com/kimo423/GEO/releases/latest/download/GEO.apk"}"""))
        assertNull(AppUpdatePolicy.parseAndValidate("""{"versionCode":3,"versionName":"1","releaseNotes":"n","apkUrl":"https://evil.example/GEO.apk"}"""))
        assertNull(AppUpdatePolicy.parseAndValidate("x".repeat(AppUpdateConfig.MAX_BODY_BYTES + 1)))
    }

    @Test
    fun apkUrlMustBeExactGithubReleaseAsset() {
        assertTrue(AppUpdatePolicy.isAllowedApkUrl(validApk))
        assertTrue(
            AppUpdatePolicy.isAllowedApkUrl(
                "https://github.com/kimo423/GEO/releases/download/v1.1.0/GEO.apk",
            ),
        )
        assertFalse(AppUpdatePolicy.isAllowedApkUrl("https://github.com/other/GEO/releases/latest/download/GEO.apk"))
        assertFalse(AppUpdatePolicy.isAllowedApkUrl("https://github.com/kimo423/GEO/archive/main.zip"))
        assertFalse(AppUpdatePolicy.isAllowedApkUrl("https://raw.githubusercontent.com/kimo423/GEO/main/GEO.apk"))
        assertFalse(AppUpdatePolicy.isAllowedApkUrl("$validApk?token=1"))
        assertFalse(AppUpdatePolicy.isAllowedApkUrl("https://github.com:8443/kimo423/GEO/releases/latest/download/GEO.apk"))
        assertFalse(AppUpdatePolicy.isAllowedApkUrl("https://www.github.com/kimo423/GEO/releases/latest/download/GEO.apk"))
        assertFalse(AppUpdatePolicy.isAllowedApkUrl("https://user@github.com/kimo423/GEO/releases/latest/download/GEO.apk"))
        assertFalse(AppUpdatePolicy.isAllowedApkUrl("$validApk#frag"))
    }

    @Test
    fun fetchUrlIsExactAllowlist() {
        assertTrue(AppUpdatePolicy.isAllowedFetchUrl(AppUpdateConfig.MANIFEST_URL))
        assertTrue(AppUpdatePolicy.isAllowedFetchUrl(AppUpdateConfig.MANIFEST_URL_REFS))
        assertFalse(AppUpdatePolicy.isAllowedFetchUrl("https://raw.githubusercontent.com/kimo423/GEO/issues/1"))
        assertFalse(AppUpdatePolicy.isAllowedFetchUrl("https://github.com/kimo423/GEO/blob/main/version.json"))
        assertFalse(AppUpdatePolicy.isAllowedFetchUrl("https://raw.githubusercontent.com/kimo423/GEOevil/main/version.json"))
        assertFalse(AppUpdatePolicy.isAllowedFetchUrl("https://raw.githubusercontent.com/kimo423/GEO.git/main/version.json"))
        assertFalse(AppUpdatePolicy.isAllowedFetchUrl("https://raw.githubusercontent.com/kimo423/GEO/../../evil/version.json"))
        assertFalse(AppUpdatePolicy.isAllowedFetchUrl("https://raw.githubusercontent.com/kimo423/GEO/%2e%2e/evil/version.json"))
        assertFalse(AppUpdatePolicy.isAllowedFetchUrl("https://raw.githubusercontent.com/kimo423/GEO//main/version.json"))
        assertFalse(AppUpdatePolicy.isAllowedFetchUrl("https://raw.githubusercontent.com/kimo423/GEO/./main/version.json"))
        assertFalse(AppUpdatePolicy.isAllowedFetchUrl("${AppUpdateConfig.MANIFEST_URL}?x=1"))
        assertFalse(AppUpdatePolicy.isAllowedFetchUrl("https://raw.githubusercontent.com:8443/kimo423/GEO/main/version.json"))
        assertFalse(AppUpdatePolicy.isAllowedFetchUrl("https://www.github.com/kimo423/GEO/main/version.json"))
        assertFalse(AppUpdatePolicy.isAllowedFetchUrl("https://user@raw.githubusercontent.com/kimo423/GEO/main/version.json"))
        assertFalse(AppUpdatePolicy.isAllowedFetchUrl("${AppUpdateConfig.MANIFEST_URL}#x"))
        assertFalse(AppUpdatePolicy.isAllowedFetchUrl("https://example.com/kimo423/GEO/version.json"))
    }

    @Test
    fun redirectResolutionIsPinnedAndSafe() {
        val main = AppUpdateConfig.MANIFEST_URL
        val refs = AppUpdateConfig.MANIFEST_URL_REFS
        assertEquals(refs, AppUpdatePolicy.resolveFetchRedirect(main, refs))
        assertEquals(main, AppUpdatePolicy.resolveFetchRedirect(main, "version.json"))
        assertNull(AppUpdatePolicy.resolveFetchRedirect(main, "http://raw.githubusercontent.com/kimo423/GEO/main/version.json"))
        assertNull(AppUpdatePolicy.resolveFetchRedirect(main, "HTTP://raw.githubusercontent.com/kimo423/GEO/main/version.json"))
        assertNull(AppUpdatePolicy.resolveFetchRedirect(main, "//evil.example/kimo423/GEO/main/version.json"))
        assertNull(AppUpdatePolicy.resolveFetchRedirect(main, "../evil/version.json"))
        assertNull(AppUpdatePolicy.resolveFetchRedirect(main, "/kimo423/GEO/issues/1"))
        assertNull(AppUpdatePolicy.resolveFetchRedirect(main, "https://github.com/kimo423/GEO/blob/main/version.json"))
        assertNull(AppUpdatePolicy.resolveFetchRedirect(main, "HTTPS://evil.example/version.json"))
        assertNull(AppUpdatePolicy.resolveFetchRedirect(main, "https://raw.githubusercontent.com/kimo423/GEO/../../evil/version.json"))
        assertNull(AppUpdatePolicy.resolveFetchRedirect(main, "\\\\evil\\share"))
        var hops = 0
        var current = main
        repeat(AppUpdateConfig.MAX_REDIRECTS) {
            current = AppUpdatePolicy.resolveFetchRedirect(current, refs)!!
            hops++
        }
        assertEquals(AppUpdateConfig.MAX_REDIRECTS, hops)
        assertEquals(refs, current)
    }

    @Test
    fun newerRemoteShowsDialogOnce() = runTest(dispatcher) {
        val calls = AtomicInteger(0)
        val fetcher = AppUpdateFetcher {
            calls.incrementAndGet()
            validJson(3, "1.2.0")
        }
        val viewModel = AppUpdateViewModel(localVersionCode = 2, fetcher = fetcher, io = dispatcher)
        assertEquals(0, calls.get())
        viewModel.checkOnce()
        val job = launch { viewModel.uiState.collect {} }
        assertEquals(1, calls.get())
        assertTrue(viewModel.uiState.value.dialogVisible)
        assertEquals("1.2.0", viewModel.uiState.value.remoteVersionName)
        viewModel.checkOnce()
        assertEquals(1, calls.get())
        job.cancel()
    }

    @Test
    fun equalOrOlderRemoteStaysSilent() = runTest(dispatcher) {
        val equal = AppUpdateViewModel(2, { validJson(2, "1.1.0") }, dispatcher)
        val older = AppUpdateViewModel(2, { validJson(1, "1.0.0") }, dispatcher)
        equal.checkOnce()
        older.checkOnce()
        launch { equal.uiState.collect {} }.cancel()
        launch { older.uiState.collect {} }.cancel()
        assertFalse(equal.uiState.value.dialogVisible)
        assertFalse(older.uiState.value.dialogVisible)
    }

    @Test
    fun networkFailureIsSilent() = runTest(dispatcher) {
        val viewModel = AppUpdateViewModel(
            localVersionCode = 2,
            fetcher = { error("network") },
            io = dispatcher,
        )
        viewModel.checkOnce()
        val job = launch { viewModel.uiState.collect {} }
        assertFalse(viewModel.uiState.value.dialogVisible)
        job.cancel()
    }

    @Test
    fun dismissHidesDialogForSession() = runTest(dispatcher) {
        val viewModel = AppUpdateViewModel(2, { validJson(9, "9.0.0") }, dispatcher)
        viewModel.checkOnce()
        val job = launch { viewModel.uiState.collect {} }
        assertTrue(viewModel.uiState.value.dialogVisible)
        viewModel.dismissForSession()
        assertFalse(viewModel.uiState.value.dialogVisible)
        viewModel.checkOnce()
        assertFalse(viewModel.uiState.value.dialogVisible)
        job.cancel()
    }

    @Test
    fun longNotesWithinCapParseAndStayOnState() = runTest(dispatcher) {
        val notes = "更新".repeat(AppUpdateConfig.MAX_NOTES / 2)
        val json =
            """{"versionCode":9,"versionName":"9.0.0","releaseNotes":"$notes","apkUrl":"$validApk"}"""
        val parsed = AppUpdatePolicy.parseAndValidate(json)
        assertNotNull(parsed)
        val viewModel = AppUpdateViewModel(2, { json }, dispatcher)
        viewModel.checkOnce()
        val job = launch { viewModel.uiState.collect {} }
        assertTrue(viewModel.uiState.value.dialogVisible)
        assertEquals(notes, viewModel.uiState.value.releaseNotes)
        job.cancel()
    }

    @Test
    fun launchSwallowsRuntimeFailuresAfterUrlCheck() {
        val hits = AtomicInteger(0)
        AppUpdateLaunch.open("https://evil.example/GEO.apk") {
            hits.incrementAndGet()
            error("should not run")
        }
        assertEquals(0, hits.get())
        AppUpdateLaunch.open(validApk) { throw SecurityException("blocked") }
        AppUpdateLaunch.open(validApk) { throw RuntimeException("no activity") }
        AppUpdateLaunch.open(validApk) { hits.incrementAndGet() }
        assertEquals(1, hits.get())
    }

    private fun validJson(code: Int, name: String): String = """
        {"versionCode":$code,"versionName":"$name","releaseNotes":"修复与更新说明","apkUrl":"$validApk"}
    """.trimIndent()
}
