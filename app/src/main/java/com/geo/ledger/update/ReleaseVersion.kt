package com.geo.ledger.update

import com.geo.ledger.data.transfer.*
import java.math.BigInteger
import java.net.URI

/** Stable release SemVer; arbitrary-size integer components prevent overflow. */
data class ReleaseVersion(val major: BigInteger, val minor: BigInteger, val patch: BigInteger) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int = compareValuesBy(this, other,
        ReleaseVersion::major, ReleaseVersion::minor, ReleaseVersion::patch)
    override fun toString(): String = "$major.$minor.$patch"
    /** Only the installed app may be a preview; remote releases remain stable-only. */
    fun isNewerThanInstalled(raw: String): Boolean {
        parse(raw)?.let { return this > it }
        require(raw.length <= 128) { "本地版本号格式无效" }
        val preview = Regex("^(v?[0-9]+\\.[0-9]+\\.[0-9]+)-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$")
            .matchEntire(raw.trim())
        val core = requireNotNull(preview?.groupValues?.get(1)?.let(::parse)) { "本地版本号格式无效" }
        return this >= core
    }
    companion object {
        fun parse(raw: String): ReleaseVersion? {
            val match = Regex("^v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$")
                .matchEntire(raw.trim().takeIf { it.length <= 128 } ?: return null) ?: return null
            return ReleaseVersion(match.groupValues[1].toBigInteger(), match.groupValues[2].toBigInteger(), match.groupValues[3].toBigInteger())
        }
    }
}

data class GithubRelease(val version: ReleaseVersion, val name: String, val notes: String, val publishedAt: String, val downloadUrl: String)

object GithubReleasePolicy {
    const val API = "https://api.github.com/repos/kimo423/GEO/releases/latest"
    const val MAX_RESPONSE = 1024 * 1024
    fun allowedUrl(raw: String): Boolean = runCatching {
        val u = URI(raw)
        u.scheme == "https" && u.host == "github.com" && u.port == -1 && u.rawUserInfo == null &&
            u.rawQuery == null && u.rawFragment == null && u.path.startsWith("/kimo423/GEO/releases/") &&
            u.path.split('/').none { it == "." || it == ".." } && '\\' !in raw
    }.getOrDefault(false)
    fun parse(raw: String): GithubRelease {
        require(raw.length <= MAX_RESPONSE) { "更新响应过大" }
        val o = StrictJson.parse(raw).jsonObject()
        require(!o.bool("draft") && !o.bool("prerelease")) { "没有可用的正式版本" }
        val version = requireNotNull(ReleaseVersion.parse(o.string("tag_name"))) { "版本号格式无效" }
        val page = o.string("html_url")
        require(allowedUrl(page)) { "更新链接无效" }
        val apk = o.array("assets").map { it.jsonObject() }
            .filter { it.string("name").endsWith(".apk", ignoreCase = true) }
            .sortedBy { if (it.string("name") == "GEO.apk") 0 else 1 }
            .map { it.string("browser_download_url") }.firstOrNull(::allowedUrl)
        return GithubRelease(version, o.nullableString("name").orEmpty().take(200),
            o.nullableString("body").orEmpty().take(20_000), o.string("published_at"), apk ?: page)
    }
}
