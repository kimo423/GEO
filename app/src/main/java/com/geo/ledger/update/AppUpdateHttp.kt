package com.geo.ledger.update

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

fun interface AppUpdateFetcher {
    fun fetchManifest(): String
}

class HttpUrlConnectionFetcher(
    private val url: String = AppUpdateConfig.MANIFEST_URL,
) : AppUpdateFetcher {
    override fun fetchManifest(): String = getHttps(url)
}

internal fun getHttps(startUrl: String): String {
    var current = AppUpdatePolicy.canonicalizeHttps(startUrl)
        ?.takeIf(AppUpdatePolicy::isAllowedFetchUrl)
        ?: error("disallowed url")
    var redirects = 0
    while (true) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = AppUpdateConfig.CONNECT_TIMEOUT_MS
                readTimeout = AppUpdateConfig.READ_TIMEOUT_MS
                useCaches = false
                defaultUseCaches = false
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
            }
            val code = connection.responseCode
            if (code in 300..399) {
                if (redirects >= AppUpdateConfig.MAX_REDIRECTS) error("too many redirects")
                val location = connection.getHeaderField("Location") ?: error("missing location")
                current = AppUpdatePolicy.resolveFetchRedirect(current, location) ?: error("disallowed redirect")
                redirects++
                continue
            }
            if (code != HttpURLConnection.HTTP_OK) error("http $code")
            val declared = connection.contentLength
            if (declared > AppUpdateConfig.MAX_BODY_BYTES) error("oversized")
            return connection.inputStream.use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(512)
                var total = 0
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    total += read
                    if (total > AppUpdateConfig.MAX_BODY_BYTES) error("oversized")
                    buffer.write(chunk, 0, read)
                }
                buffer.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection?.disconnect()
        }
    }
}
