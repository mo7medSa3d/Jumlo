package com.jumlo.app.ui.downloads

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.jumlo.app.R
import java.net.URI

private fun secureBrowserUrl(value: String): Boolean {
    val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return false
    return uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() &&
        uri.rawUserInfo == null && uri.port == -1
}

/** Session sign-in is explicit; no JS bridge, third-party cookies or local file access. */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlatformSessionSheet(
    initialUrl: String,
    hasSavedSession: Boolean,
    activeDomains: List<String>,
    initialUserAgent: String,
    initialExtraHeaders: String,
    initialPreferDesktop: Boolean,
    onSyncWebView: (String) -> Unit,
    onImportCookies: (String, String) -> Unit,
    onSaveHeaders: (String, String, Boolean) -> Unit,
    onClearSessions: () -> Unit,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    var address by remember(initialUrl) {
        mutableStateOf(initialUrl.takeIf(::secureBrowserUrl) ?: "https://www.youtube.com/")
    }
    var currentPage by remember { mutableStateOf("") }
    var browserError by remember { mutableStateOf(false) }
    var browser by remember { mutableStateOf<WebView?>(null) }
    var pastedCookies by remember { mutableStateOf("") }
    var cookieTarget by remember(initialUrl) { mutableStateOf(initialUrl.take(2048)) }
    var userAgent by remember { mutableStateOf(initialUserAgent) }
    var extraHeaders by remember { mutableStateOf(initialExtraHeaders) }
    var preferDesktop by remember { mutableStateOf(initialPreferDesktop) }

    DisposableEffect(browser) {
        val view = browser
        onDispose {
            view?.stopLoading()
            view?.webChromeClient = null
            view?.webViewClient = WebViewClient()
            view?.destroy()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(stringResource(R.string.media_session_hub_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.media_session_hub_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (hasSavedSession && activeDomains.isNotEmpty()) {
                Text(
                    stringResource(R.string.media_session_active_domains, activeDomains.take(8).joinToString(", ")),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 },
                    text = { Text(stringResource(R.string.media_tab_webview_login)) })
                Tab(selected = tab == 1, onClick = { tab = 1 },
                    text = { Text(stringResource(R.string.media_tab_cookies_import)) })
                Tab(selected = tab == 2, onClick = { tab = 2 },
                    text = { Text(stringResource(R.string.media_tab_custom_headers)) })
            }
            Column(
                Modifier.fillMaxWidth().heightIn(max = 570.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (tab) {
                    0 -> {
                        OutlinedTextField(
                            value = address, onValueChange = { address = it.take(2048) },
                            label = { Text(stringResource(R.string.media_browser_address)) },
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(onClick = {
                            if (secureBrowserUrl(address)) {
                                browserError = false
                                browser?.loadUrl(address.trim())
                            } else browserError = true
                        }) { Text(stringResource(R.string.media_go)) }
                        if (browserError) Text(
                            stringResource(R.string.media_browser_error),
                            color = MaterialTheme.colorScheme.error
                        )
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().height(340.dp),
                            factory = { context ->
                                WebView(context).apply {
                                    browser = this
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.allowFileAccess = false
                                    settings.allowContentAccess = false
                                    settings.allowFileAccessFromFileURLs = false
                                    settings.allowUniversalAccessFromFileURLs = false
                                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    settings.javaScriptCanOpenWindowsAutomatically = false
                                    settings.setSupportMultipleWindows(false)
                                    settings.safeBrowsingEnabled = true
                                    settings.mediaPlaybackRequiresUserGesture = true
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onPermissionRequest(request: PermissionRequest) {
                                            request.deny()
                                        }
                                    }
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                            !secureBrowserUrl(request.url.toString())
                                        override fun onPageFinished(view: WebView, url: String) {
                                            if (secureBrowserUrl(url)) currentPage = url
                                        }
                                    }
                                    loadUrl(address)
                                }
                            }
                        )
                        Button(
                            onClick = {
                                onSyncWebView(currentPage.ifBlank { address })
                                onDismiss()
                            },
                            enabled = secureBrowserUrl(currentPage.ifBlank { address }),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.media_session_save_and_retry)) }
                    }
                    1 -> {
                        Text(stringResource(R.string.media_cookies_import_hint),
                            style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = cookieTarget, onValueChange = { cookieTarget = it.take(2048) },
                            label = { Text(stringResource(R.string.media_browser_address)) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        OutlinedTextField(
                            value = pastedCookies, onValueChange = { pastedCookies = it.take(65536) },
                            label = { Text(stringResource(R.string.media_cookies_input_label)) },
                            modifier = Modifier.fillMaxWidth(), minLines = 4, maxLines = 8
                        )
                        Button(
                            onClick = {
                                onImportCookies(pastedCookies, cookieTarget)
                                pastedCookies = ""
                                onDismiss()
                            },
                            enabled = pastedCookies.isNotBlank() && cookieTarget.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.media_cookies_save_btn)) }
                    }
                    else -> {
                        OutlinedTextField(
                            value = userAgent, onValueChange = { userAgent = it.take(320) },
                            label = { Text(stringResource(R.string.media_tab_custom_headers)) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.media_prefer_desktop_ua),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = preferDesktop, onCheckedChange = { preferDesktop = it })
                        }
                        OutlinedTextField(
                            value = extraHeaders, onValueChange = { extraHeaders = it.take(8192) },
                            label = { Text(stringResource(R.string.media_custom_headers_label)) },
                            modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6
                        )
                        Button(
                            onClick = {
                                onSaveHeaders(userAgent, extraHeaders, preferDesktop)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.media_headers_save_btn)) }
                    }
                }
            }
            TextButton(onClick = {
                onClearSessions()
                onDismiss()
            }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.media_session_clear))
            }
        }
    }
}
