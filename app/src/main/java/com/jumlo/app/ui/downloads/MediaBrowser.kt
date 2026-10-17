package com.jumlo.app.ui.downloads

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.jumlo.app.R
import com.jumlo.app.core.media.MediaLinks
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

private data class BrowserCandidate(val url:String,val media:Boolean)

/** An optional native browser, not a JS bridge or a replacement for the app UI. */
@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION")
@Composable fun MediaBrowser(initialUrl:String,onClose:()->Unit,onMedia:(String)->Unit,onFile:(String)->Unit) {
    var address by remember { mutableStateOf(initialUrl) }
    var current by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var pageLoading by remember { mutableStateOf(false) }
    var showFound by remember { mutableStateOf(false) }
    var web by remember { mutableStateOf<WebView?>(null) }
    val found=remember { mutableStateListOf<BrowserCandidate>() }
    val epoch=remember { AtomicInteger() }
    fun normalize(value:String):String?=runCatching { MediaLinks.normalize(value) }.getOrNull()
    fun offer(value:String,media:Boolean,generation:Int) {
        if(normalize(value)==null) return
        web?.post {
            if(epoch.get()==generation && found.size<100 && found.none { it.url==value }) found.add(BrowserCandidate(value,media))
        }
    }
    fun load(value:String) {
        val url=normalize(value)
        if(url==null) error=true else { error=false;web?.loadUrl(url) }
    }
    DisposableEffect(Unit) { onDispose { epoch.incrementAndGet();web?.apply { stopLoading();webChromeClient=null;webViewClient=WebViewClient();removeAllViews();destroy() };web=null } }
    BackHandler { if(web?.canGoBack()==true) web?.goBack() else onClose() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(address,{address=it.take(32768)},modifier=Modifier.weight(1f),singleLine=true,label={Text(stringResource(R.string.media_browser_address))})
            TextButton(onClick={load(address)}) {Text(stringResource(R.string.media_go))}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(onClick=onClose) {Text(stringResource(R.string.media_close_browser))}
            TextButton(onClick={normalize(current)?.let(onMedia)},enabled=current.isNotEmpty()) {Text(stringResource(R.string.media_analyze_page))}
            TextButton(onClick={showFound=true}) {Text(stringResource(R.string.media_found,found.size))}
        }
        if(error) Text(stringResource(R.string.media_browser_error),color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(12.dp))
        if(pageLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        AndroidView(modifier=Modifier.weight(1f).fillMaxWidth(),factory={ context ->
            WebView(context).apply {
                web=this
                settings.javaScriptEnabled=true
                settings.domStorageEnabled=true
                settings.allowFileAccess=false
                settings.allowContentAccess=false
                settings.allowFileAccessFromFileURLs=false
                settings.allowUniversalAccessFromFileURLs=false
                settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.javaScriptCanOpenWindowsAutomatically=false
                settings.setSupportMultipleWindows(false)
                settings.setGeolocationEnabled(false)
                settings.safeBrowsingEnabled=true
                settings.mediaPlaybackRequiresUserGesture=true
                CookieManager.getInstance().setAcceptThirdPartyCookies(this,false)
                webChromeClient=object:WebChromeClient() {
                    override fun onPermissionRequest(request:PermissionRequest) { request.deny() }
                    override fun onGeolocationPermissionsShowPrompt(origin:String,callback:GeolocationPermissions.Callback) { callback.invoke(origin,false,false) }
                    override fun onProgressChanged(view:WebView,newProgress:Int) { pageLoading=newProgress<100 }
                }
                webViewClient=object:WebViewClient() {
                    override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean = normalize(request.url.toString())==null
                    override fun onPageStarted(view:WebView,url:String,favicon:android.graphics.Bitmap?) {
                        epoch.incrementAndGet();found.clear();current=url;address=url;pageLoading=true;error=false
                    }
                    override fun onReceivedError(view:WebView,request:WebResourceRequest,issue:WebResourceError) {
                        if(request.isForMainFrame) {error=true;pageLoading=false}
                    }
                    override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse? {
                        val value=request.url.toString()
                        if(normalize(value)==null) return WebResourceResponse("text/plain","UTF-8",ByteArrayInputStream(ByteArray(0)))
                        if(request.method=="GET") {
                            val path=request.url.path.orEmpty().lowercase(Locale.ROOT)
                            if(listOf(".m3u8",".mpd",".mp4",".webm",".m4a",".mp3",".ogg",".opus",".mov").any(path::endsWith)) offer(value,true,epoch.get())
                        }
                        return null
                    }
                }
                setDownloadListener { url,_,_,mime,_ ->
                    offer(url,mime?.startsWith("video/")==true || mime?.startsWith("audio/")==true || mime?.contains("mpegurl")==true,epoch.get())
                    showFound=true
                }
                normalize(initialUrl)?.let(::loadUrl)
            }
        })
        Text(stringResource(R.string.media_browser_scope),style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(12.dp))
    }
    if(showFound) ModalBottomSheet(onDismissRequest={showFound=false}) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=460.dp),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { Text(stringResource(R.string.media_candidates_note)) }
            if(found.isEmpty()) item {Text(stringResource(R.string.media_no_candidates))}
            items(found,key={it.url}) { candidate ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        // Do not print signed queries or tokens in the discovery list.
                        Text(Uri.parse(candidate.url).let { it.host.orEmpty()+it.path.orEmpty() }.take(180),maxLines=3)
                        TextButton(onClick={showFound=false;if(candidate.media) onMedia(candidate.url) else onFile(candidate.url)}) { Text(stringResource(if(candidate.media) R.string.media_analyze else R.string.media_direct_instead)) }
                    }
                }
            }
        }
    }
}
