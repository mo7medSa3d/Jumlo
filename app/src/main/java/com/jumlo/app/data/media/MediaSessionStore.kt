package com.jumlo.app.data.media

import android.content.Context
import android.webkit.CookieManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URI
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local browser cookies stay in Android's app-private WebView cookie jar.
 * Only non-secret UI preferences are persisted separately.
 */
@Singleton
class MediaSessionStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("jumlo_media_session_settings", Context.MODE_PRIVATE)
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
                it.matches(HOST_PATTERN)
        }
    }

    private companion object {
        val HOST_PATTERN = Regex("[a-z0-9.-]+")
    }

    fun getCustomUserAgent(): String = prefs.getString("user_agent", "").orEmpty()
    fun isPreferDesktopUa(): Boolean = prefs.getBoolean("prefer_desktop", true)
    fun getExtraHeaders(): String = sessionHeaders

    fun cookieHeaderFor(url: String): String? {
        host(url) ?: return null
        return CookieManager.getInstance().getCookie(url)?.takeIf {
            it.isNotBlank() && it.length <= 65536 && it.none(Char::isISOControl)
        }
    }
}
