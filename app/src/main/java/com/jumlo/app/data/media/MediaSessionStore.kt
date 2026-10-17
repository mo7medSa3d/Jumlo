package com.jumlo.app.data.media

import android.content.Context
import android.webkit.CookieManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.URI
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local browser cookies stay in Android's app-private WebView cookie jar.
 * Only non-secret UI preferences are persisted separately. Custom headers
 * stay in process memory; extractor cookie files exist only for one invocation.
 */
@Singleton
class MediaSessionStore @Inject constructor(@ApplicationContext context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("jumlo_media_session_settings", Context.MODE_PRIVATE)
    private val cookieDir = File(app.cacheDir, "jumlo-extractor-cookies")
    private val cookieName = Regex("[A-Za-z0-9!#%&'*+.^_|~-]{1,80}")
    private val permittedHeader = Regex("[A-Za-z0-9-]{1,64}")
    private val extraNames = setOf(
        "accept", "accept-language", "accept-encoding", "range",
        "referer", "origin", "user-agent", "dnt", "x-requested-with"
    )
    @Volatile private var sessionHeaders = ""

    private fun host(input: String): String? {
        val raw = input.trim()
        if (raw.isBlank() || raw.length > 4096) return null
        val url = if ("://" in raw) raw else "https://" + raw
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val hostname = uri.host?.lowercase(Locale.ROOT) ?: return null
        return hostname.takeIf {
            uri.scheme.equals("https", true) && uri.rawUserInfo == null &&
                uri.port == -1 && it.length in 4..253 && '.' in it &&
                it.matches(Regex("[a-z0-9.-]+"))
        }
    }

    private fun origin(domain: String) = "https://" + domain + "/"

    @Synchronized private fun rememberDomain(domain: String) {
        val domains = (activeDomainsSummary() + domain).distinct().takeLast(16)
        prefs.edit().putString("domains", domains.joinToString("|")).apply()
    }

    fun getCustomUserAgent(): String = prefs.getString("user_agent", "").orEmpty()
    fun isPreferDesktopUa(): Boolean = prefs.getBoolean("prefer_desktop", true)
    fun getExtraHeaders(): String = sessionHeaders

    @Synchronized fun activeDomainsSummary(): List<String> =
        prefs.getString("domains", "").orEmpty().split('|')
            .mapNotNull { host(it) }.distinct().take(16)
            .filter { !CookieManager.getInstance().getCookie(origin(it)).isNullOrBlank() }

    fun hasSavedCookies(): Boolean = activeDomainsSummary().isNotEmpty()

    fun cookieHeaderFor(url: String): String? {
        val domain = host(url) ?: return null
        return CookieManager.getInstance().getCookie(url)?.takeIf {
            it.isNotBlank() && it.length <= 65536 && it.none(Char::isISOControl)
        }
    }

    /** Observe existing WebView cookies without copying them to other hosts. */
    @Synchronized fun syncFromWebView(currentWebViewUrl: String, originalUrl: String) {
        val manager = CookieManager.getInstance()
        listOfNotNull(host(currentWebViewUrl), host(originalUrl)).distinct().forEach { domain ->
            if (!manager.getCookie(origin(domain)).isNullOrBlank()) rememberDomain(domain)
        }
        manager.flush()
    }

    /** Accept only cookies for the user-selected HTTPS domain or its parents. */
    @Synchronized fun importCookiesText(rawCookies: String, targetDomainOrUrl: String) {
        require(rawCookies.length in 1..65536) { "INVALID_COOKIES" }
        val target = host(targetDomainOrUrl) ?: throw IllegalArgumentException("INVALID_COOKIE_DOMAIN")
        val manager = CookieManager.getInstance()
        var count = 0
        fun add(domain: String, name: String, value: String, path: String) {
            if (count >= 256 || !cookieName.matches(name) || value.length > 4096 ||
                value.any { it == '\r' || it == '\n' || it == '\t' || it == ';' } ||
                !path.startsWith("/") || path.length > 256) return
            val cookie = name + "=" + value + "; Path=" + path +
                "; Secure; Domain=" + domain
            manager.setCookie(origin(target), cookie)
            count++
        }
        for (originalLine in rawCookies.lineSequence()) {
            val line = originalLine.trim().removePrefix("#HttpOnly_")
            if (line.isEmpty() || line.startsWith("#")) continue
            val cols = line.split('\t')
            if (cols.size >= 7) {
                val cookieDomain = cols[0].trim().removePrefix(".").lowercase(Locale.ROOT)
                if (host(cookieDomain) == null ||
                    !(target == cookieDomain || target.endsWith("." + cookieDomain))) continue
                add(cookieDomain, cols[5], cols[6], cols[2])
            } else {
                val header = line.removePrefix("Cookie:").removePrefix("cookie:")
                for (pair in header.split(';')) {
                    val name = pair.substringBefore('=', "").trim()
                    val value = pair.substringAfter('=', "").trim()
                    if ('=' in pair) add(target, name, value, "/")
                }
            }
        }
        require(count > 0) { "INVALID_COOKIES" }
        rememberDomain(target)
        manager.flush()
    }

    /** Never persist potentially credential-bearing custom headers on disk. */
    @Synchronized fun setCustomConfig(userAgent: String, extraHeaders: String, preferDesktop: Boolean) {
        val trimmedAgent = userAgent.trim()
        require(trimmedAgent.length <= 320 && trimmedAgent.none(Char::isISOControl)) { "INVALID_USER_AGENT" }
        require(extraHeaders.length <= 8192) { "INVALID_HTTP_HEADERS" }
        val rows = extraHeaders.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        require(rows.size <= 32 && rows.all { row ->
            val name = row.substringBefore(':').trim()
            val value = row.substringAfter(':', "").trim()
            ':' in row && permittedHeader.matches(name) &&
                name.lowercase(Locale.ROOT) in extraNames &&
                value.isNotEmpty() && value.length <= 2048 &&
                value.none(Char::isISOControl)
        }) { "INVALID_HTTP_HEADERS" }
        sessionHeaders = rows.joinToString("\n")
        prefs.edit().putString("user_agent", trimmedAgent)
            .putBoolean("prefer_desktop", preferDesktop).apply()
    }

    @Synchronized fun clearAllSessions() {
        sessionHeaders = ""
        prefs.edit().clear().apply()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        cookieDir.listFiles()?.forEach { if (it.isFile) it.delete() }
    }

    /**
     * Materialize URL-scoped WebView cookies in Netscape format for one yt-dlp
     * invocation. The caller MUST call cleanupExtractorEnv in finally.
     */
    fun buildExtractorEnv(url: String): Map<String, String> {
        val domain = host(url) ?: return emptyMap()
        val env = linkedMapOf<String, String>()
        getCustomUserAgent().takeIf(String::isNotBlank)?.let { env["UU_USER_AGENT"] = it }
        env["UU_REFERER"] = origin(domain)
        getExtraHeaders().takeIf(String::isNotBlank)?.let { env["UU_EXTRA_HEADERS"] = it }
        val header = cookieHeaderFor(url) ?: return env
        val rows = header.split(';').mapNotNull { entry ->
            val name = entry.substringBefore('=', "").trim()
            val value = entry.substringAfter('=', "").trim()
            if (!cookieName.matches(name) || value.length > 4096 ||
                value.any { it == '\t' || it == '\r' || it == '\n' }) null
            else domain + "\tFALSE\t/\tTRUE\t0\t" + name + "\t" + value
        }.take(256)
        if (rows.isEmpty()) return env
        require(cookieDir.isDirectory || cookieDir.mkdirs()) { "COOKIE_STORAGE_UNAVAILABLE" }
        val temp = File.createTempFile("jumlo-session-", ".txt", cookieDir)
        try {
            temp.setReadable(false, false)
            temp.setWritable(false, false)
            require(temp.setReadable(true, true) && temp.setWritable(true, true)) { "COOKIE_STORAGE_UNAVAILABLE" }
            temp.writeText("# Netscape HTTP Cookie File\n" + rows.joinToString("\n") + "\n")
            env["UU_COOKIES_FILE"] = temp.absolutePath
            return env
        } catch (error: Exception) {
            temp.delete()
            throw error
        }
    }

    fun cleanupExtractorEnv(env: Map<String, String>) {
        val path = env["UU_COOKIES_FILE"] ?: return
        val file = File(path)
        if (file.name.startsWith("jumlo-session-") && file.name.endsWith(".txt") &&
            file.parentFile?.canonicalFile == cookieDir.canonicalFile) file.delete()
    }
}
