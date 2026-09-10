package com.geo.ledger.update

import org.junit.Test
import org.junit.Assert.*

class ReleaseVersionTest {
    @Test fun installedPreviewCanUpgradeToStableWithoutAcceptingRemotePrereleases() {
        val stable = ReleaseVersion.parse("1.3.0")!!
        assertTrue(stable.isNewerThanInstalled("1.3.0-preview"))
        assertTrue(stable.isNewerThanInstalled("1.3.0-preview.1+build.7"))
        assertFalse(ReleaseVersion.parse("1.2.0")!!.isNewerThanInstalled("1.3.0-preview"))
        assertTrue(ReleaseVersion.parse("1.4.0")!!.isNewerThanInstalled("1.3.0-preview"))
        assertFalse(stable.isNewerThanInstalled("1.3.0"))
        assertTrue(stable.isNewerThanInstalled("1.2.0"))
        assertNull(ReleaseVersion.parse("1.4.0-preview"))
        assertThrows(IllegalArgumentException::class.java) { stable.isNewerThanInstalled("junk") }
    }
    @Test fun parsesCapturedOfficialGithubResponse() {
        // Public, anonymous GET captured 2026-09-09; this test itself never networks.
        val json=java.io.File("../docs/upgrade-release-response.json").readText()
        val release=GithubReleasePolicy.parse(json)
        assertEquals(ReleaseVersion.parse("1.1.3"),release.version)
        assertTrue(release.downloadUrl.endsWith("/v1.1.3/GEO.apk"))
    }
    @Test fun comparesNumerically() {
        listOf("1.0.0" to "1.0.1", "1.0.9" to "1.0.10", "1.9.0" to "1.10.0", "1.99.99" to "2.0.0").forEach { (a,b) ->
            assertTrue(ReleaseVersion.parse(a)!! < ReleaseVersion.parse(b)!!)
        }
        assertEquals(ReleaseVersion.parse("1.2.3"), ReleaseVersion.parse("v1.2.3"))
        assertEquals(ReleaseVersion.parse("1.2.3"), ReleaseVersion.parse("1.2.3+build.1"))
        listOf("", "1.2", "01.2.3", "1.2.3-beta", "a.b.c", "1.2.3junk").forEach { assertNull(ReleaseVersion.parse(it)) }
    }
    @Test fun releaseFallbackAndMalformedResponses() {
        val json = """{"draft":false,"prerelease":false,"tag_name":"v1.2.3","html_url":"https://github.com/kimo423/GEO/releases/tag/v1.2.3","name":"GEO","body":"更新","published_at":"2026-09-05T00:00:00Z","assets":[]}"""
        assertTrue(GithubReleasePolicy.parse(json).downloadUrl.endsWith("/tag/v1.2.3"))
        assertThrows(RuntimeException::class.java) { GithubReleasePolicy.parse("{}") }
        assertThrows(RuntimeException::class.java) { GithubReleasePolicy.parse(json.replace("v1.2.3", "junk")) }
        assertFalse(GithubReleasePolicy.allowedUrl("https://github.com.evil/kimo423/GEO/releases/tag/v1"))
    }
}
