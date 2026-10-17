package com.jumlo.app.ui.navigation

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import com.jumlo.app.BuildConfig
import com.jumlo.app.R
import com.jumlo.app.data.files.DocumentEntry
import com.jumlo.app.data.jobs.CapabilityRegistry
import com.jumlo.app.data.jobs.stableError
import com.jumlo.app.model.*
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.PreviewFrame
import com.jumlo.app.ui.components.*
import com.jumlo.app.ui.intake.Intake
import com.jumlo.app.ui.util.rememberAppHaptics
import com.jumlo.app.ui.intake.IntakeController
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private fun bytes(value:Long?):String {
    if(value==null) return "Unknown"
    if(value<1024) return "$value B"
    val units=listOf("KiB","MiB","GiB","TiB");var n=value.toDouble()/1024;var index=0
    while(n>=1024 && index<units.lastIndex) { n/=1024;index++ }
    return String.format(Locale.ROOT,"%.1f %s",n,units[index])
}
private fun title(tool:UtilityTool,language:AppLanguage)=if(language.isRtl) tool.titleAr else tool.titleEn

/** Type-safe Navigation Compose routes (Phase 4). */
@Serializable object HomeRoute
@Serializable object ToolsRoute
@Serializable object DownloadsRoute
@Serializable object FilesRoute
@Serializable object SettingsRoute
@Serializable object ScanRoute
@Serializable object WorkspaceRoute
@Serializable object ActivityRoute

@Composable
fun JumloNavHost(viewModel:AppViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val imported by viewModel.imported.collectAsStateWithLifecycle()
    val outputs by viewModel.outputs.collectAsStateWithLifecycle()
    val trashed by viewModel.trashed.collectAsStateWithLifecycle()
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val undoTrashId by viewModel.undoTrashId.collectAsStateWithLifecycle()
    val urls by viewModel.urls.collectAsStateWithLifecycle()
    val tree by viewModel.tree.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val haptics=LocalHapticFeedback.current
    val appHaptics = rememberAppHaptics(settings.hapticFeedbackOnCommit)
    val systemReduceMotion=remember { try { android.provider.Settings.Global.getFloat(context.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f } catch(_:Exception) { false } }
    val reduceMotion=systemReduceMotion || settings.respectReducedMotion
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val route: String = when (currentEntry?.destination?.route) {
        ToolsRoute::class.qualifiedName -> "tools_catalog"
        DownloadsRoute::class.qualifiedName -> "downloads"
        FilesRoute::class.qualifiedName -> "files_overview"
        SettingsRoute::class.qualifiedName -> "settings"
        ScanRoute::class.qualifiedName -> "scan"
        WorkspaceRoute::class.qualifiedName -> "workspace"
        ActivityRoute::class.qualifiedName -> "activity"
        else -> "home"
    }
    var tab by rememberSaveable { mutableStateOf(DockTab.HOME) }
    fun destinationFor(name: String): Any = when (name) {
        "tools_catalog" -> ToolsRoute
        "downloads" -> DownloadsRoute
        "files_overview" -> FilesRoute
        "settings" -> SettingsRoute
        "scan" -> ScanRoute
        "workspace" -> WorkspaceRoute
        "activity" -> ActivityRoute
        else -> HomeRoute
    }
    fun navigateTo(name: String) {
        appHaptics.actionClick()
        navController.navigate(destinationFor(name)) { launchSingleTop = true }
    }
    fun navigateTab(next: DockTab) {
        appHaptics.navigationTab()
        tab = next
        navController.navigate(destinationFor(next.route)) {
            popUpTo(HomeRoute) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    fun goBack() { 
        appHaptics.navigationTab()
        if (!navController.popBackStack()) navigateTab(tab) 
    }
    BackHandler(enabled = route != "home") { goBack() }

    var selectedJob by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var selectedPillar by rememberSaveable { mutableStateOf("all") }
    var deleteEntry by remember { mutableStateOf<DocumentEntry?>(null) }
    var renameEntry by remember { mutableStateOf<DocumentEntry?>(null) }
    var renameText by remember { mutableStateOf("") }
    var collisionPolicy by rememberSaveable { mutableStateOf(CollisionPolicy.KEEP_BOTH) }
    var newFolder by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var showAccountHub by rememberSaveable { mutableStateOf(false) }
    val snackbar=remember { SnackbarHostState() }
    val undoLabel=stringResource(R.string.action_undo)
    LaunchedEffect(message, undoTrashId) {
        val currentMsg=message ?: return@LaunchedEffect
        val targetUndo=undoTrashId
        val result=snackbar.showSnackbar(
            message=currentMsg.replace('_',' '),
            actionLabel=if(targetUndo!=null) undoLabel else null,
            duration=SnackbarDuration.Short
        )
        if(result==SnackbarResult.ActionPerformed && targetUndo!=null) {
            viewModel.restoreOutput(targetUndo)
        } else {
            viewModel.dismissMessage()
        }
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode==Activity.RESULT_OK) viewModel.intake(IntakeController.collect(result.data))
    }
    val photos=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        viewModel.intake(Intake(uris.map(Uri::toString),Intent.FLAG_GRANT_READ_URI_PERMISSION,null))
    }
    val folderPicker=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode==Activity.RESULT_OK) result.data?.let { data -> data.data?.let { viewModel.acceptTree(it.toString(),data.flags) } }
    }
    val exportPicker=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.export(if(result.resultCode==Activity.RESULT_OK) result.data?.data?.toString() else null)
    }
    val notifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(),viewModel::notificationsGranted)
    val errorLogExportPicker=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.exportErrorLog(if(result.resultCode==Activity.RESULT_OK) result.data?.data?.toString() else null)
    }
    val googleAccountPicker=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode==Activity.RESULT_OK) {
            val accountName=result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            val accountType=result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE)
            if(!accountName.isNullOrBlank() && !accountType.isNullOrBlank()) {
                val account=android.accounts.Account(accountName,accountType)
                val activity=context as? Activity
                if(activity!=null) {
                    AccountManager.get(context).getAuthToken(
                        account,
                        "oauth2:https://www.googleapis.com/auth/drive.file",
                        null,
                        activity,
                        { future ->
                            runCatching {
                                val bundle=future.result
                                val token=bundle.getString(AccountManager.KEY_AUTHTOKEN)
                                if(!token.isNullOrBlank()) viewModel.updateForm { it.copy(secret1=token) }
                            }.onFailure { viewModel.notifyMessage("LOGIN_REQUIRED") }
                        },
                        null
                    )
                }
            }
        }
    }
    fun launchGoogleSignIn() {
        try {
            @Suppress("DEPRECATION")
            val intent=AccountManager.newChooseAccountIntent(null,null,arrayOf("com.google"),false,null,null,null,null)
            googleAccountPicker.launch(intent)
        } catch(_:Exception) { viewModel.notifyMessage("LOGIN_REQUIRED") }
    }
    fun importFiles() {
        val mime=when(form.toolId) { "pdf_02","img_01","img_02","img_03","img_04"->"image/*";"pdf_01","pdf_07"->"application/pdf";"file_08"->"application/zip";else->"*/*" }
        picker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE);type=mime;putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) })
    }
    fun pickFolder(initial:String?=null) {
        folderPicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);if(initial!=null) putExtra(DocumentsContract.EXTRA_INITIAL_URI,Uri.parse(initial)) })
    }
    fun launchExternal(uri:String,mime:String?,share:Boolean) {
        try {
            val content=Uri.parse(uri)
            val intent=if(share) Intent(Intent.ACTION_SEND).apply { type=mime ?: "application/octet-stream";putExtra(Intent.EXTRA_STREAM,content) }
                else Intent(Intent.ACTION_VIEW).setDataAndType(content,mime ?: "application/octet-stream")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.clipData=ClipData.newRawUri("document",content)
            context.startActivity(if(share) Intent.createChooser(intent,null) else intent)
        } catch(_:Exception) { viewModel.notifyMessage("NO_COMPATIBLE_APP_OR_ACCESS_LOST") }
    }
    fun exportOutput(output:OutputRecord) {
        viewModel.prepareExport(output)
        exportPicker.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE);type=output.mimeType;putExtra(Intent.EXTRA_TITLE,output.displayName) })
    }
    fun openTool(tool:UtilityTool) {
        if(tool.id=="scan_doc") { navigateTo("scan"); return }
        if(tool.id=="dl_settings") { navigateTo("settings"); return }
        if(viewModel.chooseTool(tool.id)) navigateTo(if(tool.id=="file_02") "files_overview" else "workspace")
    }

    @Composable fun Shell(shellRoute: String) {
        val root = shellRoute in setOf("home","tools_catalog","downloads","files_overview")
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        Scaffold(snackbarHost={ SnackbarHost(snackbar) },contentWindowInsets=WindowInsets(0,0,0,0)) { padding ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                val showNavRail = root && (maxWidth >= 600.dp || isLandscape)
                val showBottomDock = root && !showNavRail
                Row(Modifier.fillMaxSize()) {
                    this@Row.AnimatedVisibility(
                        visible = showNavRail,
                        enter = if (reduceMotion) fadeIn() else (
                            slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f)
                            ) + expandHorizontally(
                                expandFrom = Alignment.Start,
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f)
                            ) + fadeIn(animationSpec = tween(durationMillis = 220))
                        ),
                        exit = if (reduceMotion) fadeOut() else (
                            slideOutHorizontally(
                                targetOffsetX = { -it },
                                animationSpec = spring(dampingRatio = 0.95f, stiffness = 380f)
                            ) + shrinkHorizontally(
                                shrinkTowards = Alignment.Start,
                                animationSpec = spring(dampingRatio = 0.95f, stiffness = 380f)
                            ) + fadeOut(animationSpec = tween(durationMillis = 180))
                        )
                    ) {
                        QuietExpandedNavigationRail(
                            selectedTab = tab,
                            language = settings.language,
                            onSelectTab = { next -> navigateTab(next) }
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Column(Modifier.fillMaxSize()) {
                            if(root) QuietRootTopBar(
                                title=when(shellRoute) {
                                    "home"->stringResource(R.string.app_name)
                                    "tools_catalog"->stringResource(R.string.tools_catalog_title)
                                    "downloads"->stringResource(R.string.nav_downloads)
                                    else->stringResource(R.string.nav_files)
                                },
                                onSettingsClick={ navigateTo("settings") },
                                onAccountClick={ showAccountHub = true }
                            )
                            else QuietWorkspaceTopBar(
                                title=when(shellRoute) {
                                    "workspace"->CapabilityRegistry.tools.find { it.id==form.toolId }?.let { title(it,settings.language) } ?: stringResource(R.string.workspace_title)
                                    "activity"->stringResource(R.string.activity_title)
                                    "scan"->stringResource(R.string.scan_title)
                                    else->stringResource(R.string.settings_title)
                                },
                                onBack={ goBack() }
                            )
                            if(shellRoute=="scan") {
                                com.jumlo.app.ui.scan.ScanScreen(
                                    onCaptured={ file ->
                                        viewModel.intakeLocalFile(file,"scan.jpg","image/jpeg")
                                        viewModel.chooseTool("pdf_02")
                                        navigateTo("workspace")
                                    },
                                    onBack={ goBack() }
                                )
                            } else if(shellRoute=="workspace" && form.toolId=="dl_media") {
                                com.jumlo.app.ui.downloads.MediaWorkspace(
                                    urls,
                                    { selectedJob=it;navigateTo("activity") },
                                    { value ->viewModel.chooseTool("dl_02");viewModel.setUrls(value);navigateTo("workspace") }
                                )
                            } else if(shellRoute=="workspace") {
                                WorkspaceScreen(
                                    viewModel,form,imported,busy,urls,reduceMotion,
                                    ::importFiles,
                                    { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    ::launchGoogleSignIn
                                ) { viewModel.submit { selectedJob=it;navigateTo("activity") } }
                            } else {
                                val visibleTools = remember(search, settings.language) {
                                    CapabilityRegistry.tools
                                        .filter { !it.isExpansionConcept }
                                        .filter { search.isBlank() || title(it,settings.language).contains(search,true) || it.id.contains(search,true) }
                                }
                                LazyColumn(
                                    Modifier.weight(1f).fillMaxWidth().widthIn(max=900.dp).align(Alignment.CenterHorizontally),
                                    contentPadding=PaddingValues(start=20.dp,end=20.dp,top=16.dp,bottom=if(showBottomDock) 124.dp else 32.dp),
                                    verticalArrangement=Arrangement.spacedBy(16.dp)
                                ) {
                                    if(shellRoute=="home") {
                                        // 1. Executive Hero & System Status
                                        item(key="home-hero") {
                                            Surface(
                                                shape = RoundedCornerShape(26.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                                shadowElevation = 2.dp,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(20.dp),
                                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = stringResource(R.string.home_greeting),
                                                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Surface(
                                                            shape = RoundedCornerShape(12.dp),
                                                            color = MaterialTheme.colorScheme.primaryContainer,
                                                            modifier = Modifier.padding(start = 4.dp)
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF00C853)))
                                                                Text(
                                                                    text = stringResource(R.string.offline_badge),
                                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Text(
                                                        text = stringResource(R.string.home_greeting_sub),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        // 2. Executive Quick Stats Dashboard Card
                                        item(key="home-dashboard") {
                                            Surface(
                                                shape = RoundedCornerShape(22.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 14.dp, vertical = 14.dp),
                                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text(
                                                            text = outputs.size.toString(),
                                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                        Text(
                                                            text = stringResource(R.string.home_stat_outputs),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text(
                                                            text = jobs.size.toString(),
                                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                                            color = MaterialTheme.colorScheme.secondary
                                                        )
                                                        Text(
                                                            text = stringResource(R.string.home_stat_jobs),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text(
                                                            text = bytes(storage.outputs + storage.sources),
                                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                                            color = MaterialTheme.colorScheme.tertiary
                                                        )
                                                        Text(
                                                            text = stringResource(R.string.home_stat_storage),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // 3. Featured 4 Core Super-Utilities in high-contrast 2x2 grid
                                        item(key="featured-heading") {
                                            QuietSectionTitle(stringResource(R.string.home_featured_header))
                                        }
                                        item(key="featured-grid-1") {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Box(modifier = Modifier.weight(1f)) {
                                                    HomeFeatureCard(
                                                        title = if (settings.language.isRtl) "تنزيل الفيديو التلقائي" else "Instant Video Download",
                                                        subtitle = if (settings.language.isRtl) "تنزيل فوري برابط واحد" else "1-tap direct stream",
                                                        icon = Icons.Rounded.Download,
                                                        accentColor = Color(0xFF059669),
                                                        containerColor = Color(0xFFECFDF5),
                                                        onClick = {
                                                            if (viewModel.chooseTool("dl_media")) navigateTo("workspace")
                                                        }
                                                    )
                                                }
                                                Box(modifier = Modifier.weight(1f)) {
                                                    HomeFeatureCard(
                                                        title = if (settings.language.isRtl) "دمج وتحويل PDF" else "Smart PDF Studio",
                                                        subtitle = if (settings.language.isRtl) "دمج الصور والمستندات" else "Merge, convert, pages",
                                                        icon = Icons.Rounded.PictureAsPdf,
                                                        accentColor = Color(0xFFDC2626),
                                                        containerColor = Color(0xFFFEF2F2),
                                                        onClick = {
                                                            if (viewModel.chooseTool("pdf_02")) navigateTo("workspace")
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        item(key="featured-grid-2") {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Box(modifier = Modifier.weight(1f)) {
                                                    HomeFeatureCard(
                                                        title = if (settings.language.isRtl) "ضغط ومعالجة الصور" else "Image Optimization",
                                                        subtitle = if (settings.language.isRtl) "تصغير الحجم وتغيير الصيغ" else "Compress, WebP, resize",
                                                        icon = Icons.Rounded.PhotoLibrary,
                                                        accentColor = Color(0xFFD97706),
                                                        containerColor = Color(0xFFFFFBEB),
                                                        onClick = {
                                                            if (viewModel.chooseTool("img_04")) navigateTo("workspace")
                                                        }
                                                    )
                                                }
                                                Box(modifier = Modifier.weight(1f)) {
                                                    HomeFeatureCard(
                                                        title = if (settings.language.isRtl) "ماسح المستندات" else "Document Scanner",
                                                        subtitle = if (settings.language.isRtl) "مسح فوري بالكاميرا" else "Instant camera to PDF",
                                                        icon = Icons.Rounded.GridView,
                                                        accentColor = Color(0xFF2563EB),
                                                        containerColor = Color(0xFFEFF6FF),
                                                        onClick = { navigateTo("scan") }
                                                    )
                                                }
                                            }
                                        }

                                        // 4. Call to Action: Explore Complete 16 Tools Directory
                                        item(key="browse-catalog-banner") {
                                            Surface(
                                                onClick = { navigateTab(DockTab.TOOLS) },
                                                shape = RoundedCornerShape(22.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(46.dp)
                                                                .clip(CircleShape)
                                                                .background(MaterialTheme.colorScheme.primary),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(Icons.Rounded.Dashboard, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                                                        }
                                                        Column {
                                                            Text(
                                                                text = stringResource(R.string.home_browse_catalog_banner_title),
                                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Text(
                                                                text = stringResource(R.string.home_browse_catalog_banner_sub),
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                    Icon(
                                                        Icons.AutoMirrored.Rounded.ArrowForward,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // 5. Recent Results
                                        item(key="home-recent-heading") {
                                            QuietSectionTitle(stringResource(R.string.recent_results_title))
                                        }
                                        if (outputs.isEmpty()) {
                                            item(key="home-no-recents") {
                                                QuietStateCard(
                                                    title = stringResource(R.string.home_no_recent_outputs),
                                                    subtitle = stringResource(R.string.offline_pledge_short)
                                                )
                                            }
                                        } else {
                                            items(outputs.take(4), key = { "home-recent-${it.id}" }) { output ->
                                                OutputCard(
                                                    output,
                                                    { launchExternal(output.uri, output.mimeType, false) },
                                                    { launchExternal(output.uri, output.mimeType, true) },
                                                    { exportOutput(output) },
                                                    { viewModel.importOutput(output); navigateTo("workspace") },
                                                    { viewModel.trashOutput(output) }
                                                )
                                            }
                                        }
                                    }

                                    if(shellRoute=="tools_catalog") {
                                        item(key="catalog-search-bar") {
                                            QuietSearchBar(
                                                query = search,
                                                placeholder = stringResource(R.string.home_search_placeholder),
                                                onQueryChange = { search = it },
                                                onClear = { search = "" }
                                            )
                                        }
                                        item(key="catalog-pillar-chips") {
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                contentPadding = PaddingValues(horizontal = 2.dp)
                                            ) {
                                                val categories = listOf(
                                                    "all" to (if (settings.language.isRtl) "الكل (16)" else "All (16)"),
                                                    PillarCategory.PDF.name to (if (settings.language.isRtl) "PDF" else "PDF"),
                                                    PillarCategory.IMAGES.name to (if (settings.language.isRtl) "الصور" else "Images"),
                                                    PillarCategory.DOWNLOADS.name to (if (settings.language.isRtl) "التنزيلات" else "Downloads"),
                                                    PillarCategory.FILES.name to (if (settings.language.isRtl) "الملفات" else "Files")
                                                )
                                                items(categories, key = { it.first }) { (key, label) ->
                                                    val isSelected = selectedPillar == key
                                                    FilterChip(
                                                        selected = isSelected,
                                                        onClick = {
                                                            appHaptics.selectionToggle()
                                                            selectedPillar = key
                                                        },
                                                        label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                                        shape = RoundedCornerShape(14.dp),
                                                        colors = FilterChipDefaults.filterChipColors(
                                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                        if(visibleTools.isEmpty()) {
                                            item(key="no-matching-tools") {
                                                QuietStateCard(
                                                    title = stringResource(R.string.tools_no_matching),
                                                    subtitle = stringResource(R.string.tools_no_matching_sub),
                                                    actionLabel = stringResource(R.string.tools_reset_filters),
                                                    onAction = { search = ""; selectedPillar = "all" }
                                                )
                                            }
                                        }
                                        val pillarsToShow = when (selectedPillar) {
                                            PillarCategory.PDF.name -> listOf(PillarCategory.PDF)
                                            PillarCategory.IMAGES.name -> listOf(PillarCategory.IMAGES)
                                            PillarCategory.DOWNLOADS.name -> listOf(PillarCategory.DOWNLOADS)
                                            PillarCategory.FILES.name -> listOf(PillarCategory.FILES)
                                            else -> listOf(PillarCategory.PDF, PillarCategory.IMAGES, PillarCategory.DOWNLOADS, PillarCategory.FILES)
                                        }
                                        for(pillar in pillarsToShow) {
                                            val tools = visibleTools.filter { it.pillar == pillar }
                                            if(tools.isNotEmpty()) {
                                                item(key = "catalog-heading-${pillar.name}") {
                                                    QuietSectionTitle(if(settings.language.isRtl) pillar.labelAr else pillar.labelEn)
                                                }
                                                items(tools.chunked(2), key = { pair -> "catalog-grid-${pair.joinToString("-") { it.id }}" }) { rowTools ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                    ) {
                                                        rowTools.forEach { tool ->
                                                            Box(modifier = Modifier.weight(1f)) {
                                                                ToolGridCard(tool, settings.language, { openTool(tool) })
                                                            }
                                                        }
                                                        if (rowTools.size == 1) {
                                                            Spacer(modifier = Modifier.weight(1f))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if(shellRoute=="downloads") {
                                        val dlTools = CapabilityRegistry.tools.filter { it.id in setOf("dl_media","dl_02","dl_03") }
                                        items(dlTools.chunked(2), key={ pair -> "dl-grid-${pair.joinToString("-") { it.id }}" }) { rowTools ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                rowTools.forEach { tool ->
                                                    Box(modifier = Modifier.weight(1f)) {
                                                        ToolGridCard(tool, settings.language, { openTool(tool) })
                                                    }
                                                }
                                                if (rowTools.size == 1) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                        item(key="dl-activity-heading") { QuietSectionTitle(stringResource(R.string.transfer_activity_title)) }
                                        val downloads=jobs.filter { it.request.toolId.startsWith("dl_") }
                                        if(downloads.isEmpty()) {
                                            item(key="dl-empty") {
                                                QuietStateCard(
                                                    title=stringResource(R.string.downloads_empty_title),
                                                    subtitle=stringResource(R.string.downloads_empty_sub)
                                                )
                                            }
                                        }
                                        items(downloads,key={it.request.id}) { job ->
                                            JobCard(
                                                job = job,
                                                onOpen = { selectedJob=job.request.id;navigateTo("activity") },
                                                onPause = { viewModel.pause(job.request.id) },
                                                onCancel = { viewModel.cancel(job.request.id) },
                                                onRetry = { viewModel.retry(job) }
                                            )
                                        }
                                    }
                                    if(shellRoute=="files_overview") {
                                        item(key="files-actions") {
                                            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                                QuietTonalButton(text=stringResource(R.string.import_device_files),onClick=::importFiles)
                                                QuietTonalButton(text=stringResource(R.string.tool_browse_storage),onClick={pickFolder()})
                                            }
                                        }
                                        item(key="snapshots-header") {
                                            QuietSectionTitle(stringResource(R.string.files_snapshots_title))
                                            Text(stringResource(R.string.files_snapshots_sub),style=MaterialTheme.typography.bodySmall)
                                        }
                                        if(imported.isEmpty()) {
                                            item(key="snapshots-empty") {
                                                QuietStateCard(
                                                    title=stringResource(R.string.files_empty_snapshots),
                                                    subtitle=stringResource(R.string.files_snapshots_sub),
                                                    actionLabel=stringResource(R.string.import_device_files),
                                                    onAction=::importFiles
                                                )
                                            }
                                        }
                                        items(imported,key={"source-${it.id}"}) { source ->
                                            val ext=source.displayName.substringAfterLast('.',"bin")
                                            QuietFileRow(
                                                file=ManagedFileItem(
                                                    id=source.id,
                                                    name=source.displayName,
                                                    path=source.uri,
                                                    folder="Snapshots",
                                                    extension=ext,
                                                    mimeType=source.mimeType ?: "application/octet-stream",
                                                    sizeBytes=source.sizeBytes ?: 0L,
                                                    formattedSize=bytes(source.sizeBytes),
                                                    modifiedLabelEn=source.mimeType ?: "Snapshot",
                                                    modifiedLabelAr=source.mimeType ?: "نسخة"
                                                ),
                                                language=settings.language,
                                                onClick={viewModel.addSource(source.id);navigateTo("workspace")},
                                                trailingActionText=stringResource(R.string.files_add_to_workspace),
                                                onTrailingAction={viewModel.addSource(source.id);navigateTo("workspace")}
                                            )
                                        }
                                        item(key="outputs-header") { QuietSectionTitle(stringResource(R.string.recent_results_title)) }
                                        items(outputs,key={it.id}) { output ->
                                            OutputCard(
                                                output,
                                                { launchExternal(output.uri,output.mimeType,false) },
                                                { launchExternal(output.uri,output.mimeType,true) },
                                                { exportOutput(output) },
                                                { viewModel.importOutput(output);navigateTo("workspace") },
                                                { viewModel.trashOutput(output) }
                                            )
                                        }
                                        item(key="trash-header") {
                                            QuietSectionTitle(
                                                text=stringResource(R.string.files_trash_title),
                                                trailingText=if(trashed.isNotEmpty()) stringResource(R.string.files_purge_trash) else null,
                                                onTrailingClick=if(trashed.isNotEmpty()) ({ viewModel.purgeTrashItem(null) }) else null
                                            )
                                        }
                                        if(trashed.isEmpty()) {
                                            item(key="trash-empty") { Text(stringResource(R.string.files_trash_empty),style=MaterialTheme.typography.bodySmall) }
                                        }
                                        items(trashed,key={"trash-${it.id}"}) { item ->
                                            QuietCard {
                                                Text(item.displayName,style=MaterialTheme.typography.titleMedium)
                                                Text("${bytes(item.sizeBytes)} | ${item.mimeType}")
                                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                                    TextButton(onClick={viewModel.restoreOutput(item.id)}) { Text(stringResource(R.string.action_restore)) }
                                                    TextButton(onClick={viewModel.purgeTrashItem(item.id)}) { Text(stringResource(R.string.action_delete)) }
                                                }
                                            }
                                        }
                                        tree?.let { listing ->
                                            item(key="tree-header") {
                                                QuietSectionTitle(listing.name)
                                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                                                    TextButton(onClick={newFolder=true;folderName=""}) { Text(stringResource(R.string.files_new_folder)) }
                                                    CollisionPolicy.entries.forEach { pol ->
                                                        FilterChip(
                                                            selected=collisionPolicy==pol,
                                                            onClick={collisionPolicy=pol},
                                                            label={Text(if(settings.language.isRtl) pol.labelAr else pol.labelEn)}
                                                        )
                                                    }
                                                }
                                            }
                                            items(listing.entries,key={"tree-${it.uri}"}) { entry ->
                                                QuietCard {
                                                    Text(entry.name,style=MaterialTheme.typography.titleMedium)
                                                    Text(if(entry.isDirectory) stringResource(R.string.files_folder_label) else bytes(entry.sizeBytes))
                                                    if(entry.isDirectory) {
                                                        TextButton(onClick={pickFolder(entry.uri)}) { Text(stringResource(R.string.files_browse_inside)) }
                                                    } else {
                                                        LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                                            item { TextButton(onClick={launchExternal(entry.uri,entry.mimeType,false)}) { Text(stringResource(R.string.action_open)) } }
                                                            item { TextButton(onClick={viewModel.intake(Intake(listOf(entry.uri),Intent.FLAG_GRANT_READ_URI_PERMISSION,null));navigateTo("workspace")}) { Text(stringResource(R.string.files_import_action)) } }
                                                            item { TextButton(onClick={viewModel.copyOrMoveFile(entry,false,collisionPolicy)}) { Text(stringResource(R.string.action_copy)) } }
                                                            item { TextButton(enabled=entry.writable,onClick={renameEntry=entry;renameText=entry.name}) { Text(stringResource(R.string.action_rename)) } }
                                                            item { TextButton(enabled=entry.writable,onClick={deleteEntry=entry}) { Text(stringResource(R.string.files_delete_original)) } }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if(shellRoute=="activity") {
                                        item(key="activity-notice") { Text(stringResource(R.string.activity_published_notice)) }
                                        val displayed=if(selectedJob==null) jobs else jobs.filter { it.request.id==selectedJob }
                                        items(displayed,key={it.request.id}) { job ->
                                            JobCard(
                                                job = job,
                                                onOpen = {},
                                                onPause = { viewModel.pause(job.request.id) },
                                                onCancel = { viewModel.cancel(job.request.id) },
                                                onRetry = { viewModel.retry(job) }
                                            )
                                            if(job.request.toolId.startsWith("dl_") && job.progress.stage==JobStage.WAITING_FOR_INPUT) {
                                                var replacement by remember(job.request.id) { mutableStateOf("") }
                                                OutlinedTextField(replacement,{replacement=it},label={Text(stringResource(R.string.activity_reenter_url))},modifier=Modifier.fillMaxWidth())
                                                Text(stringResource(R.string.activity_reenter_url_hint),style=MaterialTheme.typography.bodySmall)
                                                TextButton(onClick={viewModel.retry(job,replacement);replacement=""},enabled=replacement.isNotBlank()) { Text(stringResource(R.string.activity_retry_urls)) }
                                            }
                                            if(job.outcomes.isNotEmpty()) Text(job.outcomes.joinToString("\n") { "${it.itemKey}: ${it.status}${it.errorCode?.let { code->" | $code" } ?: ""}" },style=MaterialTheme.typography.bodySmall)
                                        }
                                        items(outputs.filter { selectedJob==null || it.jobId==selectedJob },key={it.id}) { output ->
                                            OutputCard(
                                                output,
                                                { launchExternal(output.uri,output.mimeType,false) },
                                                { launchExternal(output.uri,output.mimeType,true) },
                                                { exportOutput(output) },
                                                { viewModel.importOutput(output);navigateTo("workspace") },
                                                { viewModel.trashOutput(output) }
                                            )
                                        }
                                        item(key="activity-new-run") { TextButton(onClick={viewModel.allowNewRun();navigateTo("workspace")}) { Text(stringResource(R.string.activity_start_new_run)) } }
                                    }
                                    if(shellRoute=="settings") {
                                        item(key="settings-appearance") { QuietSectionTitle(stringResource(R.string.settings_appearance_language)) }
                                        items(ThemeMode.entries,key={it.name}) { mode ->
                                            Row(verticalAlignment=Alignment.CenterVertically) {
                                                RadioButton(settings.themeMode==mode,{viewModel.setThemeMode(mode)})
                                                Text(if(settings.language.isRtl) mode.labelAr else mode.labelEn)
                                            }
                                        }
                                        items(AppLanguage.entries,key={it.name}) { lang ->
                                            Row(verticalAlignment=Alignment.CenterVertically) {
                                                RadioButton(settings.language==lang,{viewModel.setLanguage(lang)})
                                                Text(lang.displayName)
                                            }
                                        }
                                        item(key="settings-reduce-motion") {
                                            Toggle(stringResource(R.string.settings_reduce_motion),settings.respectReducedMotion,viewModel::setRespectReducedMotion)
                                        }
                                        item(key="settings-notifications") {
                                            if(Build.VERSION.SDK_INT>=33) QuietTonalButton(text=stringResource(R.string.settings_allow_notifications),onClick={notifications.launch(Manifest.permission.POST_NOTIFICATIONS)})
                                        }
                                        item(key="settings-storage-actions") {
                                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                                QuietTonalButton(text=stringResource(R.string.settings_refresh_storage),onClick=viewModel::refreshStorage)
                                                QuietTonalButton(text=stringResource(R.string.settings_clean_temp),onClick=viewModel::cleanTemporary)
                                            }
                                        }
                                        item(key="settings-storage-totals") {
                                            Text(stringResource(R.string.settings_storage_summary, bytes(storage.sources), bytes(storage.outputs), bytes(storage.trash), bytes(storage.partials), bytes(storage.temporary)))
                                        }
                                        item(key="settings-privacy") { Text(stringResource(R.string.settings_privacy_summary)) }
                                        item(key="settings-candidate") { CandidateNotice() }
                                        item(key="settings-error-log") {
                                            QuietTonalButton(
                                                text=stringResource(R.string.settings_export_error_log),
                                                onClick={
                                                    viewModel.prepareErrorLogExport()
                                                    errorLogExportPicker.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                                        addCategory(Intent.CATEGORY_OPENABLE)
                                                        type="text/plain"
                                                        putExtra(Intent.EXTRA_TITLE,"jumlo-errors.txt")
                                                    })
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showBottomDock,
                            modifier = Modifier.align(Alignment.BottomCenter),
                            enter = if (reduceMotion) fadeIn() else (
                                slideInVertically(
                                    initialOffsetY = { it },
                                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f)
                                ) + fadeIn(animationSpec = tween(durationMillis = 220))
                            ),
                            exit = if (reduceMotion) fadeOut() else (
                                slideOutVertically(
                                    targetOffsetY = { it },
                                    animationSpec = spring(dampingRatio = 0.95f, stiffness = 380f)
                                ) + fadeOut(animationSpec = tween(durationMillis = 180))
                            )
                        ) {
                            QuietFloatingDock(
                                selectedTab = tab,
                                language = settings.language,
                                onSelectTab = { next -> navigateTab(next) }
                            )
                        }
                    }
                }
            }
        }
    }

    SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(spring(dampingRatio = 0.9f, stiffness = 380f)) },
            exitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(spring(dampingRatio = 0.9f, stiffness = 380f)) },
            popEnterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(spring(dampingRatio = 0.9f, stiffness = 380f)) },
            popExitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(spring(dampingRatio = 0.9f, stiffness = 380f)) }
        ) {
            composable<HomeRoute> { Shell("home") }
            composable<ToolsRoute> { Shell("tools_catalog") }
            composable<DownloadsRoute> { Shell("downloads") }
            composable<FilesRoute> { Shell("files_overview") }
            composable<SettingsRoute> { Shell("settings") }
            composable<ScanRoute> { Shell("scan") }
            composable<WorkspaceRoute>(deepLinks = listOf(navDeepLink<WorkspaceRoute>(basePath = "jumlo://workspace"))) { Shell("workspace") }
            composable<ActivityRoute> { Shell("activity") }
        }
    }
    if(!settings.hasCompletedOnboarding) {
        AlertDialog(
            onDismissRequest={},
            title={Text(stringResource(R.string.welcome_title))},
            text={Text(stringResource(R.string.welcome_card_body))},
            confirmButton={TextButton(onClick=viewModel::finishOnboarding) {Text(stringResource(R.string.welcome_get_started))}}
        )
    }
    deleteEntry?.let { entry ->
        AlertDialog(
            onDismissRequest={deleteEntry=null},
            title={Text(stringResource(R.string.dialog_delete_title))},
            text={Text(entry.name+"\n"+stringResource(R.string.dialog_delete_body))},
            confirmButton={TextButton(onClick={deleteEntry=null;viewModel.deleteFile(entry)}) {Text(stringResource(R.string.action_delete))}},
            dismissButton={TextButton(onClick={deleteEntry=null}) {Text(stringResource(R.string.action_cancel))}}
        )
    }
    renameEntry?.let { entry ->
        AlertDialog(
            onDismissRequest={renameEntry=null},
            title={Text(stringResource(R.string.dialog_rename_title))},
            text={OutlinedTextField(renameText,{renameText=it},singleLine=true)},
            confirmButton={TextButton(onClick={renameEntry=null;viewModel.renameFile(entry,renameText)}) {Text(stringResource(R.string.action_rename))}},
            dismissButton={TextButton(onClick={renameEntry=null}) {Text(stringResource(R.string.action_cancel))}}
        )
    }
    if(newFolder) {
        AlertDialog(
            onDismissRequest={newFolder=false},
            title={Text(stringResource(R.string.files_new_folder))},
            text={OutlinedTextField(folderName,{folderName=it},singleLine=true)},
            confirmButton={TextButton(onClick={newFolder=false;viewModel.createFolder(folderName)}) {Text(stringResource(R.string.action_create))}},
            dismissButton={TextButton(onClick={newFolder=false}) {Text(stringResource(R.string.action_cancel))}}
        )
    }
    if (showAccountHub) {
        AccountHubModal(
            settings = settings,
            storage = storage,
            outputsCount = outputs.size,
            jobsCount = jobs.size,
            onDismiss = { showAccountHub = false },
            onOpenSettings = { navigateTo("settings") },
            onClearCache = {
                viewModel.cleanTemporary()
                viewModel.refreshStorage()
            },
            onExportLogs = {
                viewModel.prepareErrorLogExport()
                errorLogExportPicker.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TITLE, "jumlo-errors.txt")
                })
            },
            onThemeChange = { mode -> viewModel.setThemeMode(mode) },
            onLanguageChange = { lang -> viewModel.setLanguage(lang) }
        )
    }
}

@Composable private fun CandidateNotice() {
    Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=MaterialTheme.shapes.medium) {
        Text(
            text=stringResource(if(BuildConfig.DEBUG) R.string.candidate_debug_notice else R.string.candidate_release_notice),
            modifier=Modifier.padding(16.dp),
            style=MaterialTheme.typography.bodySmall
        )
    }
}
private data class ToolTheme(val primary: Color, val container: Color, val icon: ImageVector)

private fun resolveToolTheme(tool: UtilityTool): ToolTheme {
    return when(tool.id) {
        "pdf_02" -> ToolTheme(Color(0xFFE53935), Color(0xFFFFEBEE), Icons.Rounded.PictureAsPdf)
        "pdf_03" -> ToolTheme(Color(0xFFD81B60), Color(0xFFFCE4EC), Icons.Rounded.Merge)
        "pdf_05" -> ToolTheme(Color(0xFF8E24AA), Color(0xFFF3E5F5), Icons.Rounded.ContentCut)
        "pdf_04" -> ToolTheme(Color(0xFF5E35B1), Color(0xFFEDE7F6), Icons.Rounded.Dashboard)
        "pdf_06" -> ToolTheme(Color(0xFF3949AB), Color(0xFFE8EAF6), Icons.Rounded.Description)
        "pdf_07" -> ToolTheme(Color(0xFF1E88E5), Color(0xFFE3F2FD), Icons.Rounded.PhotoLibrary)
        "pdf_01" -> ToolTheme(Color(0xFF0288D1), Color(0xFFE1F5FE), Icons.Rounded.Visibility)
        "pdf_compress" -> ToolTheme(Color(0xFF0097A7), Color(0xFFE0F7FA), Icons.Rounded.Compress)
        "pdf_crop" -> ToolTheme(Color(0xFF00897B), Color(0xFFE0F2F1), Icons.Rounded.Crop)
        "pdf_annotate" -> ToolTheme(Color(0xFF43A047), Color(0xFFE8F5E9), Icons.Rounded.Edit)
        "pdf_stamp" -> ToolTheme(Color(0xFF689F38), Color(0xFFF1F8E9), Icons.Rounded.Star)
        "pdf_signature" -> ToolTheme(Color(0xFF558B2F), Color(0xFFF1F8E9), Icons.Rounded.Edit)
        "pdf_protect" -> ToolTheme(Color(0xFFF4511E), Color(0xFFFBE9E7), Icons.Rounded.Lock)
        "pdf_metadata" -> ToolTheme(Color(0xFF795548), Color(0xFFEFEBE9), Icons.Rounded.Tune)
        "pdf_forms" -> ToolTheme(Color(0xFF607D8B), Color(0xFFECEFF1), Icons.Rounded.Edit)
        "pdf_compare" -> ToolTheme(Color(0xFF5C6BC0), Color(0xFFE8EAF6), Icons.Rounded.SwapHoriz)
        "pdf_redact" -> ToolTheme(Color(0xFFD32F2F), Color(0xFFFFEBEE), Icons.Rounded.Visibility)
        "pdf_ocr" -> ToolTheme(Color(0xFF7B1FA2), Color(0xFFF3E5F5), Icons.Rounded.Bolt)
        "scan_doc" -> ToolTheme(Color(0xFF00C853), Color(0xFFE8F8EE), Icons.Rounded.GridView)
        "webpage_pdf" -> ToolTheme(Color(0xFF1565C0), Color(0xFFE3F2FD), Icons.Rounded.Link)

        "img_01" -> ToolTheme(Color(0xFF2962FF), Color(0xFFE8EAF6), Icons.Rounded.PhotoLibrary)
        "img_02" -> ToolTheme(Color(0xFF0091EA), Color(0xFFE1F5FE), Icons.Rounded.Crop)
        "img_03" -> ToolTheme(Color(0xFF00B8D4), Color(0xFFE0F7FA), Icons.Rounded.Crop)
        "img_04" -> ToolTheme(Color(0xFF00BFA5), Color(0xFFE0F2F1), Icons.Rounded.Compress)
        "img_exif" -> ToolTheme(Color(0xFF651FFF), Color(0xFFEDE7F6), Icons.Rounded.Tune)
        "img_watermark" -> ToolTheme(Color(0xFFAA00FF), Color(0xFFF3E5F5), Icons.Rounded.Star)
        "img_join" -> ToolTheme(Color(0xFFFF6D00), Color(0xFFFFF3E0), Icons.Rounded.GridView)
        "img_adjust" -> ToolTheme(Color(0xFFFFAB00), Color(0xFFFFF8E1), Icons.Rounded.Tune)
        "img_target" -> ToolTheme(Color(0xFF00897B), Color(0xFFE0F2F1), Icons.Rounded.Compress)
        "img_compare" -> ToolTheme(Color(0xFF3F51B5), Color(0xFFE8EAF6), Icons.Rounded.SwapHoriz)
        "img_stitch" -> ToolTheme(Color(0xFF00ACC1), Color(0xFFE0F7FA), Icons.Rounded.GridView)

        "dl_media" -> ToolTheme(Color(0xFFFF1744), Color(0xFFFFEBEE), Icons.Rounded.Download)
        "dl_02" -> ToolTheme(Color(0xFF00C853), Color(0xFFE8F8EE), Icons.Rounded.Download)
        "dl_03" -> ToolTheme(Color(0xFF0091EA), Color(0xFFE1F5FE), Icons.Rounded.Link)
        "dl_settings" -> ToolTheme(Color(0xFF546E7A), Color(0xFFECEFF1), Icons.Rounded.Settings)

        "file_02" -> ToolTheme(Color(0xFF1976D2), Color(0xFFE3F2FD), Icons.Rounded.Folder)
        "file_07" -> ToolTheme(Color(0xFFF57C00), Color(0xFFFFF3E0), Icons.Rounded.FolderZip)
        "file_08" -> ToolTheme(Color(0xFF388E3C), Color(0xFFE8F5E9), Icons.Rounded.Unarchive)
        "file_encrypted_zip" -> ToolTheme(Color(0xFF6A1B9A), Color(0xFFF3E5F5), Icons.Rounded.Lock)
        "file_batch_rename" -> ToolTheme(Color(0xFF0288D1), Color(0xFFE1F5FE), Icons.Rounded.Edit)
        "file_duplicates" -> ToolTheme(Color(0xFFC2185B), Color(0xFFFCE4EC), Icons.Rounded.Search)
        "file_trash" -> ToolTheme(Color(0xFFD32F2F), Color(0xFFFFEBEE), Icons.Rounded.Delete)
        "file_drive" -> ToolTheme(Color(0xFF0F9D58), Color(0xFFE8F5E9), Icons.Rounded.Cloud)
        else -> ToolTheme(Color(0xFF5B4CF5), Color(0xFFECEAFF), resolveStitchIcon(tool.iconName))
    }
}

@Composable
private fun QuickActionTile(
    title: String,
    icon: ImageVector,
    tint: Color,
    container: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val appHaptics = rememberAppHaptics()
    Surface(
        onClick = { appHaptics.toolSelect(); onClick() },
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(18.dp),
        color = container.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.25f)),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(container),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun HomeFeatureCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    containerColor: Color,
    onClick: () -> Unit
) {
    val appHaptics = rememberAppHaptics()
    Surface(
        onClick = {
            appHaptics.toolSelect()
            onClick()
        },
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
        shadowElevation = 2.dp,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().height(148.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable private fun ToolGridCard(tool:UtilityTool,language:AppLanguage,onOpen:()->Unit) {
    val enabled=CapabilityRegistry.enabled(tool.id)
    val theme=remember(tool.id) { resolveToolTheme(tool) }
    val appHaptics = rememberAppHaptics()
    Surface(
        onClick=if(enabled) { { appHaptics.toolSelect(); onOpen() } } else ({}),
        modifier=Modifier.fillMaxWidth().heightIn(min=152.dp),
        shape=RoundedCornerShape(22.dp),
        color=MaterialTheme.colorScheme.surfaceContainerLow,
        border=BorderStroke(1.dp, theme.primary.copy(alpha=0.22f)),
        shadowElevation=1.dp,
        tonalElevation=2.dp
    ) {
        Column(
            modifier=Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement=Arrangement.SpaceBetween
        ) {
            Row(
                modifier=Modifier.fillMaxWidth(),
                horizontalArrangement=Arrangement.SpaceBetween,
                verticalAlignment=Alignment.Top
            ) {
                Box(
                    modifier=Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(theme.container),
                    contentAlignment=Alignment.Center
                ) {
                    Icon(theme.icon,contentDescription=null,tint=theme.primary,modifier=Modifier.size(26.dp))
                }
                if(enabled) {
                    Box(
                        modifier=Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(theme.primary.copy(alpha=0.12f)),
                        contentAlignment=Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription=null,
                            tint=theme.primary,
                            modifier=Modifier.size(14.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Column {
                Text(
                    text=title(tool,language),
                    style=MaterialTheme.typography.titleMedium.copy(fontWeight=FontWeight.Bold),
                    maxLines=1,
                    overflow=TextOverflow.Ellipsis,
                    color=MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text=if(language.isRtl) tool.subtitleAr else tool.subtitleEn,
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines=2,
                    overflow=TextOverflow.Ellipsis,
                    lineHeight=16.sp
                )
            }
        }
    }
}

@Composable private fun ToolCard(tool:UtilityTool,language:AppLanguage,onOpen:()->Unit) {
    val pillars=QuietTheme.pillars
    val (tint,container)=when(tool.pillar) {
        PillarCategory.PDF->pillars.pdfPrimary to pillars.pdfContainer
        PillarCategory.IMAGES->pillars.imagesPrimary to pillars.imagesContainer
        PillarCategory.DOWNLOADS->pillars.downloadsPrimary to pillars.downloadsContainer
        PillarCategory.FILES->pillars.filesPrimary to pillars.filesContainer
        PillarCategory.VAULT->pillars.vaultPrimary to pillars.vaultContainer
    }
    val enabled=CapabilityRegistry.enabled(tool.id)
    QuietCard(onClick=if(enabled) onOpen else null) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            QuietIconTile(resolveStitchIcon(tool.iconName),tint,container)
            Column(Modifier.weight(1f)) {
                Text(title(tool,language),style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(if(language.isRtl) tool.subtitleAr else tool.subtitleEn,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
        }
        if(enabled) QuietTonalButton(stringResource(R.string.action_open),onOpen,Modifier.align(Alignment.End),leadingIcon=Icons.AutoMirrored.Rounded.ArrowForward)
        else Text(CapabilityRegistry.reason(tool.id).replace('_',' '),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun OutputCard(output:OutputRecord,onOpen:()->Unit,onShare:()->Unit,onExport:()->Unit,onReuse:()->Unit,onTrash:(()->Unit)?=null) {
    QuietCard {
        Text(output.displayName,style=MaterialTheme.typography.titleMedium)
        Text("${bytes(output.sizeBytes)} | ${output.mimeType}")
        output.metadata.pageCount?.let { Text(stringResource(R.string.output_pages_count, it)) }
        if(output.metadata.width!=null && output.metadata.height!=null) Text("${output.metadata.width} x ${output.metadata.height} px")
        output.metadata.entryCount?.let { Text(stringResource(R.string.output_entries_count, it)) }
        Text(stringResource(R.string.output_sha256_label, output.sha256),style=MaterialTheme.typography.bodySmall)
        LazyRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            item { TextButton(onOpen) { Text(stringResource(R.string.action_open)) } }
            item { TextButton(onShare) { Text(stringResource(R.string.action_share)) } }
            item { TextButton(onExport) { Text(stringResource(R.string.action_save_as)) } }
            item { TextButton(onReuse) { Text(stringResource(R.string.action_use_as_input)) } }
            if(onTrash!=null) item { TextButton(onTrash) { Text(stringResource(R.string.action_move_to_trash)) } }
        }
    }
}
@Composable private fun JobCard(job:JobSnapshot,onOpen:()->Unit,onPause:(()->Unit)?=null,onCancel:()->Unit,onRetry:()->Unit) {
    QuietCard {
        val active=job.progress.stage in setOf(JobStage.QUEUED,JobStage.VALIDATING,JobStage.RUNNING,JobStage.FINALIZING)
        Text(job.request.toolId+" | "+job.progress.stage.name.replace('_',' '),style=MaterialTheme.typography.titleMedium)
        Text(DateFormat.getDateTimeInstance().format(Date(job.updatedAtEpochMs)),style=MaterialTheme.typography.bodySmall)
        if(active) {
            val total=job.progress.totalUnits
            if(total==null || total<=0) LinearProgressIndicator(Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress={ (job.progress.completedUnits.toDouble()/total).toFloat().coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth())
            Text(if(job.request.toolId.startsWith("dl_")) "${bytes(job.progress.completedUnits)} / ${bytes(total)}" else "${job.progress.completedUnits} / ${total?.toString() ?: "?"}")
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(onOpen) { Text(stringResource(R.string.action_details)) }
            if(active && job.request.toolId.startsWith("dl_") && onPause!=null) {
                TextButton(onPause) { Text(stringResource(R.string.action_pause)) }
            }
            if(active) TextButton(onCancel) { Text(stringResource(R.string.action_cancel)) }
            if(job.progress.stage in setOf(JobStage.FAILED,JobStage.PARTIAL,JobStage.INTERRUPTED,JobStage.WAITING_FOR_INPUT)) {
                TextButton(onRetry) { Text(stringResource(R.string.action_retry_failed)) }
            }
        }
        val created=job.outcomes.count { it.outputId!=null };val failed=job.outcomes.count { it.status==ItemStatus.FAILED }
        if(job.outcomes.isNotEmpty()) Text(stringResource(R.string.job_outcomes_summary, created, failed),style=MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun WorkspaceScreen(
    vm:AppViewModel,form:WorkspaceDraft,imported:List<SourceRef>,busy:Boolean,urls:String,reduceMotion:Boolean,
    onImport:()->Unit,onPhotos:()->Unit,onGoogleSignIn:()->Unit,onExecute:()->Unit
) {
    val haptics=LocalHapticFeedback.current
    val tool=form.toolId
    val download=tool.startsWith("dl_")
    val driveImport=tool=="file_drive" && !form.flag1
    val viewer=tool=="pdf_01"
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    val queue=remember(form.sourceIds, imported) { form.sourceIds.mapNotNull { id->imported.find { it.id==id } } }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().widthIn(max=760.dp).align(Alignment.CenterHorizontally),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(!download) {
                item(key="ws-import-row") {
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        QuietTonalButton(text=stringResource(R.string.import_device_files),onClick=onImport)
                        if(tool=="pdf_02" || tool.startsWith("img_")) QuietTonalButton(text=stringResource(R.string.import_photo_gallery),onClick=onPhotos)
                    }
                }
                item(key="ws-queue-summary") {
                    Text(stringResource(R.string.workspace_queue_summary, queue.size, bytes(queue.mapNotNull { it.sizeBytes }.sum())),style=MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.workspace_queue_hint),style=MaterialTheme.typography.bodySmall)
                }
                itemsIndexed(queue,key={_,it->it.id}) { index,source ->
                    QuietCard {
                        Text(source.displayName,style=MaterialTheme.typography.titleMedium)
                        Text("${bytes(source.sizeBytes)} | ${source.mimeType ?: "?"}")
                        LazyRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            item { TextButton(onClick={previewId=source.id}) { Text(stringResource(R.string.action_preview)) } }
                            item { TextButton(onClick={haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove);vm.moveSource(index,-1)},enabled=index>0) { Text(stringResource(R.string.action_move_up)) } }
                            item { TextButton(onClick={haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove);vm.moveSource(index,1)},enabled=index<queue.lastIndex) { Text(stringResource(R.string.action_move_down)) } }
                            item { TextButton(onClick={vm.removeSource(source.id)}) { Text(stringResource(R.string.action_remove)) } }
                        }
                    }
                }
                if(queue.isNotEmpty()) item(key="ws-clear") { TextButton(onClick=vm::clearQueue) { Text(stringResource(R.string.workspace_clear_selection)) } }
                if(viewer && queue.isNotEmpty()) item(key="ws-viewer") { InputPreview(vm,queue.first().id,reduceMotion) }
                if(tool in setOf("img_04","img_target","pdf_compress") && queue.isNotEmpty()) {
                    item(key="ws-before-after") {
                        val first=queue.first()
                        QuietBeforeAfterPreview(
                            fileName=first.displayName,
                            resolution="${form.quality}%",
                            originalLabel="${stringResource(R.string.before_after_original)}: ${bytes(first.sizeBytes)}",
                            compressedLabel=stringResource(R.string.before_after_target)
                        )
                    }
                }
            } else {
                item(key="ws-dl-input") {
                    val clipboard = LocalClipboardManager.current
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        shadowElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = urls,
                                onValueChange = vm::setUrls,
                                label = { Text(stringResource(if(tool=="dl_03") R.string.workspace_direct_urls_batch else R.string.workspace_direct_url_single)) },
                                placeholder = { Text("https://example.com/file...") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = if(tool=="dl_03") 4 else 2,
                                shape = RoundedCornerShape(16.dp),
                                trailingIcon = {
                                    IconButton(onClick = {
                                        clipboard.getText()?.text?.let { text ->
                                            if(text.isNotBlank()) vm.setUrls(text)
                                        }
                                    }) {
                                        Icon(Icons.Rounded.ContentPaste, contentDescription = stringResource(R.string.action_paste), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            )
                            Text(stringResource(R.string.workspace_direct_url_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if(!viewer) item(key="ws-fields") { WorkspaceFields(vm,form,vm::updateForm,onGoogleSignIn) }
        }
        if(!viewer) Surface(shadowElevation=6.dp) {
            QuietPrimaryButton(
                text=stringResource(if(download) R.string.workspace_queue_download else R.string.workspace_process_files),
                onClick={ haptics.performHapticFeedback(HapticFeedbackType.LongPress); onExecute() },
                modifier=Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal=20.dp,vertical=12.dp),
                enabled=!busy && CapabilityRegistry.enabled(tool) && (tool=="webpage_pdf" || driveImport || (download && urls.isNotBlank() || !download && form.sourceIds.isNotEmpty()))
            )
        }
    }
    previewId?.let { id ->
        AlertDialog(
            onDismissRequest={previewId=null},
            title={Text(stringResource(R.string.dialog_preview_title))},
            text={InputPreview(vm,id,reduceMotion)},
            confirmButton={TextButton(onClick={previewId=null}) {Text(stringResource(R.string.action_close))}}
        )
    }
}

@Composable private fun InputPreview(vm:AppViewModel,sourceId:String,reduceMotion:Boolean=false) {
    var page by rememberSaveable(sourceId) { mutableIntStateOf(1) }
    var frame by remember(sourceId) { mutableStateOf<PreviewFrame?>(null) }
    var failure by remember(sourceId) { mutableStateOf<String?>(null) }
    var loading by remember(sourceId) { mutableStateOf(false) }
    LaunchedEffect(sourceId,page) {
        loading=true;failure=null
        try { frame=vm.preview(sourceId,page) }
        catch(cancel:CancellationException) { throw cancel }
        catch(error:Exception) { failure=stableError(error) }
        finally { loading=false }
    }
    DisposableEffect(frame) { val previous=frame;onDispose { previous?.bitmap?.takeUnless { it.isRecycled }?.recycle() } }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            QuietShimmerSkeleton(reduceMotion=reduceMotion)
        }
        failure?.let { Text(it.replace('_',' '),color=MaterialTheme.colorScheme.error) }
        frame?.let { current ->
            Image(current.bitmap.asImageBitmap(),stringResource(R.string.dialog_preview_title),Modifier.fillMaxWidth().heightIn(min=120.dp,max=420.dp),contentScale=ContentScale.Fit)
            Text("${current.width} x ${current.height} | ${current.page} / ${current.count}")
            if(current.count>1) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(enabled=page>1 && !loading,onClick={page--}) { Text(stringResource(R.string.action_previous)) }
                TextButton(enabled=page<current.count && !loading,onClick={page++}) { Text(stringResource(R.string.action_next)) }
            }
        }
    }
}

@Composable private fun Field(label:String,value:String,onValue:(String)->Unit,numeric:Boolean=false) {
    OutlinedTextField(value,{onValue(it.take(512))},label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=if(numeric) KeyboardType.Decimal else KeyboardType.Text))
}
@Composable private fun Options(label:String,options:List<String>,selected:String,onSelect:(String)->Unit) {
    Text(label,style=MaterialTheme.typography.titleSmall)
    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { items(options,key={it}) { choice -> FilterChip(selected==choice,{onSelect(choice)},label={Text(choice)}) } }
}
@Composable private fun Toggle(label:String,checked:Boolean,onChange:(Boolean)->Unit) { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text(label,Modifier.weight(1f));Switch(checked,onChange)} }

@Composable private fun WorkspaceFields(vm:AppViewModel,f:WorkspaceDraft,edit:((WorkspaceDraft)->WorkspaceDraft)->Unit,onGoogleSignIn:()->Unit={}) {
    QuietCard {
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            val image=f.toolId.startsWith("img_")
            if(image) {
                Options(stringResource(R.string.field_output_format),listOf("JPEG","PNG","WEBP"),f.format) { value->edit {it.copy(format=value)} }
                Field(stringResource(R.string.field_quality),f.quality,{value->edit {it.copy(quality=value)}},true)
                Field(stringResource(R.string.field_output_prefix),f.prefix,{value->edit {it.copy(prefix=value)}})
                Text(stringResource(R.string.field_image_policy_note),style=MaterialTheme.typography.bodySmall)
            }
            when(f.toolId) {
                "img_02" -> {
                    Field(stringResource(R.string.field_target_width),f.width,{v->edit {it.copy(width=v)}},true)
                    Field(stringResource(R.string.field_target_height),f.height,{v->edit {it.copy(height=v)}},true)
                    Field(stringResource(R.string.field_percentage_opt),f.percentage,{v->edit {it.copy(percentage=v,longestEdge="")}},true)
                    Field(stringResource(R.string.field_longest_edge_opt),f.longestEdge,{v->edit {it.copy(longestEdge=v,percentage="")}},true)
                    Options(stringResource(R.string.field_fit_strategy),listOf("FIT","FILL","PAD","STRETCH"),f.fit) {v->edit {it.copy(fit=v)}}
                    Toggle(stringResource(R.string.field_allow_upscaling),f.allowUpscaling) {v->edit {it.copy(allowUpscaling=v)}}
                }
                "img_03" -> {
                    Text(stringResource(R.string.field_crop_hint))
                    Field(stringResource(R.string.field_left),f.cropLeft,{v->edit {it.copy(cropLeft=v)}},true);Field(stringResource(R.string.field_top),f.cropTop,{v->edit {it.copy(cropTop=v)}},true)
                    Field(stringResource(R.string.field_right),f.cropRight,{v->edit {it.copy(cropRight=v)}},true);Field(stringResource(R.string.field_bottom),f.cropBottom,{v->edit {it.copy(cropBottom=v)}},true)
                    Options(stringResource(R.string.field_clockwise_rotation),listOf("0","90","180","270"),(f.turns*90).toString()) {v->edit {it.copy(turns=v.toInt()/90)}}
                    Toggle(stringResource(R.string.field_flip_horizontal),f.flipX) {v->edit {it.copy(flipX=v)}};Toggle(stringResource(R.string.field_flip_vertical),f.flipY) {v->edit {it.copy(flipY=v)}}
                }
                "pdf_02" -> {
                    Field(stringResource(R.string.field_output_filename),f.outputName,{v->edit {it.copy(outputName=v)}})
                    Field(stringResource(R.string.field_paper_width),f.paperWidth,{v->edit {it.copy(paperWidth=v)}},true)
                    Field(stringResource(R.string.field_paper_height),f.paperHeight,{v->edit {it.copy(paperHeight=v)}},true)
                    Field(stringResource(R.string.field_margin_mm),f.margin,{v->edit {it.copy(margin=v)}},true)
                    Options(stringResource(R.string.field_orientation),listOf("AUTO","PORTRAIT","LANDSCAPE"),f.orientation) {v->edit {it.copy(orientation=v)}}
                    Toggle(stringResource(R.string.field_fill_page),f.fill) {v->edit {it.copy(fill=v)}}
                }
                "pdf_07" -> {
                    Field(stringResource(R.string.field_pages_spec),f.pages,{v->edit {it.copy(pages=v)}})
                    Field(stringResource(R.string.field_dpi),f.dpi,{v->edit {it.copy(dpi=v)}},true)
                    Options(stringResource(R.string.field_output_format),listOf("PNG","JPEG"),f.format) {v->edit {it.copy(format=v)}}
                    Field(stringResource(R.string.field_quality),f.quality,{v->edit {it.copy(quality=v)}},true)
                    Toggle(stringResource(R.string.field_grayscale),f.grayscale) {v->edit {it.copy(grayscale=v)}}
                    Text(stringResource(R.string.field_raster_note))
                }
                "file_07" -> { Field(stringResource(R.string.field_output_filename),f.outputName,{v->edit {it.copy(outputName=v)}});Field(stringResource(R.string.field_zip_level),f.zipLevel,{v->edit {it.copy(zipLevel=v)}},true);Text(stringResource(R.string.field_zip_create_note)) }
                "file_08" -> Text(stringResource(R.string.field_zip_extract_note))
                "sha256" -> Field(stringResource(R.string.field_expected_sha256),f.expectedHash,{v->edit {it.copy(expectedHash=v)}})
                "pdf_03" -> { Field(stringResource(R.string.field_output_filename),f.outputName,{v->edit {it.copy(outputName=v)}}); Text(stringResource(R.string.field_merge_note)) }
                "pdf_05" -> { Field(stringResource(R.string.field_output_filename),f.outputName,{v->edit {it.copy(outputName=v)}}); Toggle(stringResource(R.string.field_split_every_n),f.flag1) {v->edit {it.copy(flag1=v)}}; if(f.flag1) Field(stringResource(R.string.field_pages_per_part),f.num1,{v->edit {it.copy(num1=v)}},true) else Field(stringResource(R.string.field_pages_spec),f.pages,{v->edit {it.copy(pages=v)}}) }
                "pdf_06" -> { Field(stringResource(R.string.field_output_filename),f.outputName,{v->edit {it.copy(outputName=v)}}); Field(stringResource(R.string.field_pages_spec),f.pages,{v->edit {it.copy(pages=v)}}) }
                "pdf_04" -> { Field(stringResource(R.string.field_output_filename),f.outputName,{v->edit {it.copy(outputName=v)}}); Field(stringResource(R.string.field_organize_order),f.text1,{v->edit {it.copy(text1=v)}}); Field(stringResource(R.string.field_rotate_pages),f.text2,{v->edit {it.copy(text2=v)}}); Field(stringResource(R.string.field_rotate_degrees),f.num1,{v->edit {it.copy(num1=v)}},true) }
                "pdf_crop" -> { Field(stringResource(R.string.field_output_filename),f.outputName,{v->edit {it.copy(outputName=v)}}); Field(stringResource(R.string.field_pages_spec),f.pages,{v->edit {it.copy(pages=v)}}); Field(stringResource(R.string.field_left),f.cropLeft,{v->edit {it.copy(cropLeft=v)}},true); Field(stringResource(R.string.field_top),f.cropTop,{v->edit {it.copy(cropTop=v)}},true); Field(stringResource(R.string.field_right),f.cropRight,{v->edit {it.copy(cropRight=v)}},true); Field(stringResource(R.string.field_bottom),f.cropBottom,{v->edit {it.copy(cropBottom=v)}},true) }
                "pdf_compress" -> { Field(stringResource(R.string.field_output_filename),f.outputName,{v->edit {it.copy(outputName=v)}}); Field(stringResource(R.string.field_max_image_dpi),f.num1,{v->edit {it.copy(num1=v)}},true); Field(stringResource(R.string.field_quality),f.quality,{v->edit {it.copy(quality=v)}},true); Text(stringResource(R.string.field_compress_note)) }
                "pdf_stamp" -> { Field(stringResource(R.string.field_output_filename),f.outputName,{v->edit {it.copy(outputName=v)}}); Field(stringResource(R.string.field_watermark_text),f.text1,{v->edit {it.copy(text1=v)}}); Field(stringResource(R.string.field_pages_spec),f.pages,{v->edit {it.copy(pages=v)}}); Toggle(stringResource(R.string.field_add_page_numbers),f.flag1) {v->edit {it.copy(flag1=v)}} }
                "pdf_annotate" -> { Field(stringResource(R.string.field_output_filename),f.outputName,{v->edit {it.copy(outputName=v)}}); Field(stringResource(R.string.field_text),f.text1,{v->edit {it.copy(text1=v)}}); Field(stringResource(R.string.field_page_number),f.num1,{v->edit {it.copy(num1=v)}},true); Field(stringResource(R.string.field_x_norm),f.num2,{v->edit {it.copy(num2=v)}},true); Field(stringResource(R.string.field_y_norm),f.num3,{v->edit {it.copy(num3=v)}},true); Toggle(stringResource(R.string.field_as_note_annotation),f.flag1) {v->edit {it.copy(flag1=v)}} }
                "pdf_signature" -> { Field(stringResource(R.string.field_output_filename),f.outputName,{v->edit {it.copy(outputName=v)}}); Text(stringResource(R.string.field_signature_note)); com.jumlo.app.ui.scan.SignaturePad { file -> vm.intakeLocalFile(file,"signature.png","image/png") }; Field(stringResource(R.string.field_page_number),f.num1,{v->edit {it.copy(num1=v)}},true); Field(stringResource(R.string.field_left),f.cropLeft,{v->edit {it.copy(cropLeft=v)}},true); Field(stringResource(R.string.field_top),f.cropTop,{v->edit {it.copy(cropTop=v)}},true); Field(stringResource(R.string.field_right),f.cropRight,{v->edit {it.copy(cropRight=v)}},true); Field(stringResource(R.string.field_bottom),f.cropBottom,{v->edit {it.copy(cropBottom=v)}},true) }
                "pdf_metadata" -> { Field(stringResource(R.string.field_output_filename),f.outputName,{v->edit {it.copy(outputName=v)}}); Field(stringResource(R.string.field_title),f.text1,{v->edit {it.copy(text1=v)}}); Field(stringResource(R.string.field_author),f.text2,{v->edit {it.copy(text2=v)}}); Field(stringResource(R.string.field_subject),f.text3,{v->edit {it.copy(text3=v)}}); Field(stringResource(R.string.field_keywords),f.text4,{v->edit {it.copy(text4=v)}}); Field(stringResource(R.string.field_insert_at_page),f.num1,{v->edit {it.copy(num1=v)}},true) }
                "pdf_protect" -> { Field(stringResource(R.string.field_output_filename),f.outputName,{v->edit {it.copy(outputName=v)}}); Toggle(stringResource(R.string.field_remove_protection),f.flag1) {v->edit {it.copy(flag1=v)}}; Field(stringResource(R.string.field_user_password),f.secret1,{v->edit {it.copy(secret1=v)}}); if(!f.flag1) Field(stringResource(R.string.field_owner_password),f.secret2,{v->edit {it.copy(secret2=v)}}); Text(stringResource(R.string.field_protect_note)) }
                "pdf_forms" -> { Field(stringResource(R.string.field_output_filename),f.outputName,{v->edit {it.copy(outputName=v)}}); Field(stringResource(R.string.field_forms_json),f.text1,{v->edit {it.copy(text1=v)}}); Toggle(stringResource(R.string.field_flatten_form),f.flag1) {v->edit {it.copy(flag1=v)}} }
                "pdf_compare" -> Text(stringResource(R.string.field_compare_pdf_note))
                "pdf_redact" -> { Field(stringResource(R.string.field_output_filename),f.outputName,{v->edit {it.copy(outputName=v)}}); Field(stringResource(R.string.field_page_number),f.num1,{v->edit {it.copy(num1=v)}},true); Field(stringResource(R.string.field_left),f.cropLeft,{v->edit {it.copy(cropLeft=v)}},true); Field(stringResource(R.string.field_top),f.cropTop,{v->edit {it.copy(cropTop=v)}},true); Field(stringResource(R.string.field_right),f.cropRight,{v->edit {it.copy(cropRight=v)}},true); Field(stringResource(R.string.field_bottom),f.cropBottom,{v->edit {it.copy(cropBottom=v)}},true); Field(stringResource(R.string.field_verify_redacted_text),f.text1,{v->edit {it.copy(text1=v)}}); Text(stringResource(R.string.field_redact_note)) }
                "pdf_ocr" -> { Field(stringResource(R.string.field_pages_spec),f.pages,{v->edit {it.copy(pages=v)}}); Text(stringResource(R.string.field_ocr_note)) }
                "webpage_pdf" -> { Field(stringResource(R.string.field_webpage_url),f.text1,{v->edit {it.copy(text1=v)}}); Text(stringResource(R.string.field_webpage_note)) }
                "img_exif" -> { Toggle(stringResource(R.string.field_exif_remove_all),f.flag1) {v->edit {it.copy(flag1=v)}}; Toggle(stringResource(R.string.field_exif_remove_location),f.flag2) {v->edit {it.copy(flag2=v)}}; Text(stringResource(R.string.field_exif_note)) }
                "img_watermark" -> { Field(stringResource(R.string.field_watermark_text),f.text1,{v->edit {it.copy(text1=v)}}); Field(stringResource(R.string.field_opacity_percent),f.num1,{v->edit {it.copy(num1=v)}},true); Toggle(stringResource(R.string.field_use_second_logo),f.flag1) {v->edit {it.copy(flag1=v)}} }
                "img_join","img_stitch" -> {
                    if(f.toolId=="img_join") Options(stringResource(R.string.field_layout),listOf("GRID2","HSTRIP","VSTRIP"),f.text1.ifBlank{"GRID2"}) {v->edit {it.copy(text1=v)}}
                    Field(stringResource(R.string.field_gap_px),f.num1,{v->edit {it.copy(num1=v)}},true)
                    if(f.toolId=="img_stitch") Field(stringResource(R.string.field_overlap_px),f.num2,{v->edit {it.copy(num2=v)}},true)
                }
                "img_adjust" -> { Field(stringResource(R.string.field_brightness),f.num1,{v->edit {it.copy(num1=v)}},true); Field(stringResource(R.string.field_contrast),f.num2,{v->edit {it.copy(num2=v)}},true); Field(stringResource(R.string.field_saturation),f.num3,{v->edit {it.copy(num3=v)}},true); Field(stringResource(R.string.field_padding_px),f.num4,{v->edit {it.copy(num4=v)}},true) }
                "img_target" -> Field(stringResource(R.string.field_target_bytes),f.num1,{v->edit {it.copy(num1=v)}},true)
                "img_compare" -> Text(stringResource(R.string.field_compare_img_note))
                "file_batch_rename" -> { Field(stringResource(R.string.field_rename_pattern),f.text1,{v->edit {it.copy(text1=v)}}); Field(stringResource(R.string.field_start_number),f.num1,{v->edit {it.copy(num1=v)}},true); Text(stringResource(R.string.field_rename_note)) }
                "file_duplicates" -> Text(stringResource(R.string.field_duplicates_note))
                "file_trash" -> Text(stringResource(R.string.field_trash_note))
                "file_encrypted_zip" -> { Field(stringResource(R.string.field_output_filename),f.outputName,{v->edit {it.copy(outputName=v)}}); Toggle(stringResource(R.string.field_extract_instead),f.flag1) {v->edit {it.copy(flag1=v)}}; Field(stringResource(R.string.field_zip_password),f.secret1,{v->edit {it.copy(secret1=v)}}); Text(stringResource(R.string.field_encrypted_zip_note)) }
                "file_drive" -> {
                    Toggle(stringResource(R.string.field_drive_export_mode),f.flag1) {v->edit {it.copy(flag1=v)}}
                    if(!f.flag1) Field(stringResource(R.string.field_drive_file_id),f.text1,{v->edit {it.copy(text1=v)}})
                    else Field(stringResource(R.string.field_drive_folder_id),f.text2,{v->edit {it.copy(text2=v)}})
                    QuietTonalButton(text=stringResource(R.string.field_drive_signin),onClick=onGoogleSignIn)
                    Field(stringResource(R.string.field_drive_token),f.secret1,{v->edit {it.copy(secret1=v)}})
                    Text(stringResource(R.string.field_drive_note),style=MaterialTheme.typography.bodySmall)
                }
                "dl_02","dl_03" -> {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.nav_downloads),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if(f.outputName.isNotBlank() && f.outputName != "download.bin") f.outputName else stringResource(R.string.workspace_direct_url_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
