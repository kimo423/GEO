package com.geo.ledger.update

import java.net.URI

data class AppUpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val releaseNotes: String,
    val apkUrl: String,
)

object AppUpdateConfig {
    const val MANIFEST_URL = "https://raw.githubusercontent.com/kimo423/GEO/main/version.json"
    const val MANIFEST_URL_REFS = "https://raw.githubusercontent.com/kimo423/GEO/refs/heads/main/version.json"
    const val MAX_BODY_BYTES = 8_192
    const val CONNECT_TIMEOUT_MS = 8_000
    const val READ_TIMEOUT_MS = 8_000
    const val MAX_REDIRECTS = 2
    const val OWNER = "kimo423"
    const val REPO = "GEO"
    const val APK_ASSET = "GEO.apk"
    const val MAX_NOTES = 2_000
    const val MAX_NAME = 64
}

object AppUpdatePolicy {
    fun shouldPrompt(localVersionCode: Int, remoteVersionCode: Int): Boolean =
        remoteVersionCode > localVersionCode

    fun parseAndValidate(body: String): AppUpdateManifest? {
        if (body.length > AppUpdateConfig.MAX_BODY_BYTES) return null
        val fields = AppUpdateJson.parseFlatObject(body) ?: return null
        val code = fields["versionCode"]?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val name = fields["versionName"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val notes = fields["releaseNotes"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val apkUrl = fields["apkUrl"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (name.length > AppUpdateConfig.MAX_NAME || notes.length > AppUpdateConfig.MAX_NOTES) return null
        if (!isAllowedApkUrl(apkUrl)) return null
        return AppUpdateManifest(code, name, notes, apkUrl)
    }

    fun isAllowedApkUrl(url: String): Boolean {
        val canonical = canonicalizeHttps(url) ?: return false
        val parsed = URI(canonical)
        if (parsed.host != "github.com") return false
        val path = parsed.path
        val latest = "/${AppUpdateConfig.OWNER}/${AppUpdateConfig.REPO}/releases/latest/download/${AppUpdateConfig.APK_ASSET}"
        val tagged = Regex(
            "^/${Regex.escape(AppUpdateConfig.OWNER)}/${Regex.escape(AppUpdateConfig.REPO)}" +
                "/releases/download/[A-Za-z0-9][A-Za-z0-9._-]{0,63}/${Regex.escape(AppUpdateConfig.APK_ASSET)}$",
        )
        return path == latest || tagged.matches(path)
    }

    fun isAllowedFetchUrl(url: String): Boolean {
        val canonical = canonicalizeHttps(url) ?: return false
        return canonical in allowedFetchUrls
    }

    fun resolveFetchRedirect(current: String, location: String): String? {
        if (location.isBlank() || location.length > 512) return null
        if ('\\' in location || '\u0000' in location) return null
        if (location.startsWith("//")) return null
        val lower = location.trim().lowercase()
        if (lower.startsWith("http://")) return null
        val base = canonicalizeHttps(current) ?: return null
        val resolved = runCatching {
            if (hasExplicitScheme(location)) URI(location) else URI(base).resolve(location)
        }.getOrNull() ?: return null
        val canonical = canonicalizeHttps(resolved.toString()) ?: return null
        if (canonical !in allowedFetchUrls) return null
        if (!urlAgreesWithUri(canonical)) return null
        return canonical
    }

    fun canonicalizeHttps(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty() || trimmed.length > 512) return null
        if ('\\' in trimmed || '\u0000' in trimmed || ' ' in trimmed) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (!uri.isAbsolute) return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.rawUserInfo != null || uri.userInfo != null) return null
        if (uri.rawQuery != null || uri.query != null) return null
        if (uri.rawFragment != null || uri.fragment != null) return null
        if (uri.port != -1 && uri.port != 443) return null
        val host = uri.host?.lowercase() ?: return null
        if (host.isEmpty() || host.endsWith(".") || host.startsWith(".")) return null
        val path = uri.path ?: return null
        if (uri.rawPath != path) return null
        if (!isSafeAbsolutePath(path)) return null
        val canonical = "https://$host$path"
        if (!urlAgreesWithUri(canonical)) return null
        return canonical
    }

    private val allowedFetchUrls = setOf(
        AppUpdateConfig.MANIFEST_URL,
        AppUpdateConfig.MANIFEST_URL_REFS,
    )

    private fun isSafeAbsolutePath(path: String): Boolean {
        if (!path.startsWith("/")) return false
        if (path.contains("//") || '%' in path) return false
        val segments = path.split('/')
        if (segments.first() != "") return false
        return segments.drop(1).all { part ->
            part.isNotEmpty() && part != "." && part != ".." && part.none { it == '\\' }
        }
    }

    private fun hasExplicitScheme(location: String): Boolean =
        Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(location)

    private fun urlAgreesWithUri(canonical: String): Boolean {
        val uri = runCatching { URI(canonical) }.getOrNull() ?: return false
        val url = runCatching { java.net.URL(canonical) }.getOrNull() ?: return false
        return url.protocol.equals("https", ignoreCase = true) &&
            url.host.equals(uri.host, ignoreCase = true) &&
            url.port == uri.port &&
            url.path == uri.path &&
            url.query == null &&
            url.ref == null &&
            url.userInfo == null
    }
}

internal object AppUpdateJson {
    fun parseFlatObject(raw: String): Map<String, String>? {
        val text = raw.trim()
        if (text.length < 2 || text.first() != '{' || text.last() != '}') return null
        val inner = text.substring(1, text.length - 1).trim()
        if (inner.isEmpty()) return emptyMap()
        val values = linkedMapOf<String, String>()
        var i = 0
        while (i < inner.length) {
            i = skipWs(inner, i)
            if (i >= inner.length) break
            if (inner[i] != '"') return null
            val (key, afterKey) = readString(inner, i) ?: return null
            i = skipWs(inner, afterKey)
            if (i >= inner.length || inner[i] != ':') return null
            i = skipWs(inner, i + 1)
            if (i >= inner.length) return null
            val (value, afterValue) = when (inner[i]) {
                '"' -> {
                    val parsed = readString(inner, i) ?: return null
                    parsed.first to parsed.second
                }
                '-', in '0'..'9' -> readNumber(inner, i) ?: return null
                else -> return null
            }
            if (key in values) return null
            values[key] = value
            i = skipWs(inner, afterValue)
            if (i >= inner.length) break
            if (inner[i] != ',') return null
            i++
            if (skipWs(inner, i) >= inner.length) return null
        }
        return values
    }

    private fun skipWs(text: String, start: Int): Int {
        var i = start
        while (i < text.length && text[i].isWhitespace()) i++
        return i
    }

    private fun readString(text: String, start: Int): Pair<String, Int>? {
        if (start >= text.length || text[start] != '"') return null
        val out = StringBuilder()
        var i = start + 1
        while (i < text.length) {
            when (val ch = text[i]) {
                '"' -> return out.toString() to i + 1
                '\\' -> {
                    if (i + 1 >= text.length) return null
                    when (val esc = text[i + 1]) {
                        '"', '\\', '/' -> out.append(esc)
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        else -> return null
                    }
                    i += 2
                }
                else -> {
                    if (ch.code < 0x20) return null
                    out.append(ch)
                    i++
                }
            }
        }
        return null
    }

    private fun readNumber(text: String, start: Int): Pair<String, Int>? {
        var i = start
        if (text[i] == '-') i++
        if (i >= text.length || text[i] !in '0'..'9') return null
        val from = start
        while (i < text.length && text[i] in '0'..'9') i++
        if (i < text.length && (text[i] == '.' || text[i] == 'e' || text[i] == 'E')) return null
        return text.substring(from, i) to i
    }
}
