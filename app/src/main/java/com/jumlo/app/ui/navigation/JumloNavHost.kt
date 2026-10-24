package com.jumlo.app.ui.navigation

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import android.content.ContextWrapper
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import com.jumlo.app.ui.theme.JumloMotion
import com.jumlo.app.ui.theme.QuietTheme
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.util.Consumer
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import androidx.compose.ui.platform.testTag
import com.jumlo.app.R
import com.jumlo.app.data.files.DocumentEntry
import com.jumlo.app.data.jobs.CapabilityRegistry
import com.jumlo.app.core.OutputWorkflowPolicy
import com.jumlo.app.model.*
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.components.*
import com.jumlo.app.ui.intake.Intake
import com.jumlo.app.ui.util.rememberAppHaptics
import com.jumlo.app.ui.intake.IntakeController
import com.jumlo.app.ui.theme.LocalReducedMotion
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

private fun title(tool:UtilityTool,language:AppLanguage)=if(language.isRtl) tool.titleAr else tool.titleEn

/** Serializable route arguments survive recreation; readable names are documented in ROUTES.md. */
@Serializable @SerialName("home") object HomeRoute
@Serializable @SerialName("tools") object ToolsRoute
@Serializable @SerialName("downloads") object DownloadsRoute
@Serializable @SerialName("tools/print") object PrintRoute
@Serializable @SerialName("files") object FilesRoute
@Serializable @SerialName("settings") object SettingsRoute
@Serializable @SerialName("settings/details") object SettingsDetailRoute
@Serializable @SerialName("settings/storage") object StorageRoute
@Serializable @SerialName("tools/scan") data class ScanRoute(val transitionOrigin: String = "tools")
@Serializable @SerialName("tools/run") data class ToolRoute(val toolId: String, val transitionOrigin: String = "tools")
@Serializable @SerialName("files/viewer") data class ViewerRoute(val uri: String, val title: String, val returnTab: String? = null)
@Serializable @SerialName("downloads/job") data class JobRoute(val jobId: String)
@Serializable @SerialName("workspace") object WorkspaceRoute // Compatibility deep link.
@Serializable @SerialName("downloads/activity") object ActivityRoute

@Composable
fun JumloNavHost(viewModel:AppViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val printSource by viewModel.printSource.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val undoTrashIds by viewModel.undoTrashIds.collectAsStateWithLifecycle()
    val undoRename by viewModel.undoRename.collectAsStateWithLifecycle()
    val sessionEpoch by viewModel.sessionEpoch.collectAsStateWithLifecycle()
    val workspaceReady by viewModel.workspaceReady.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val appHaptics = rememberAppHaptics()
    val trackedJobs by viewModel.jobs.collectAsStateWithLifecycle()
    val lastStages=remember { mutableStateMapOf<String,JobStage>() }
    LaunchedEffect(trackedJobs) {
        trackedJobs.forEach {job ->
            val id=job.request.id
            val previous=lastStages.put(id,job.progress.stage)
            if(previous!=null && previous!=JobStage.COMPLETED && job.progress.stage==JobStage.COMPLETED &&
                job.request.toolId.startsWith("dl_")) appHaptics.downloadComplete()
            else if(previous!=null && previous!=JobStage.FAILED && job.progress.stage==JobStage.FAILED) appHaptics.error()
        }
    }
    val reduceMotion=LocalReducedMotion.current
    val rtl=LocalLayoutDirection.current==LayoutDirection.Rtl
    val navController = rememberNavController()
    val hostActivity = remember(context) {
        generateSequence(context) { current ->
            (current as? ContextWrapper)?.baseContext?.takeUnless { it === current }
        }.filterIsInstance<ComponentActivity>().firstOrNull()
    }
    val currentEntry by navController.currentBackStackEntryAsState()
    val destination = currentEntry?.destination
    val route = when {
        destination?.hasRoute<ToolsRoute>() == true -> "tools"
        destination?.hasRoute<DownloadsRoute>() == true -> "downloads"
        destination?.hasRoute<FilesRoute>() == true -> "files"
        destination?.hasRoute<PrintRoute>() == true -> "print"
        destination?.hasRoute<SettingsRoute>() == true -> "settings"
        destination?.hasRoute<SettingsDetailRoute>() == true -> "settings_detail"
        destination?.hasRoute<StorageRoute>() == true -> "storage"
        destination?.hasRoute<ScanRoute>() == true -> "scan"
        destination?.hasRoute<ToolRoute>() == true || destination?.hasRoute<WorkspaceRoute>() == true -> "workspace"
        destination?.hasRoute<ViewerRoute>() == true -> "viewer"
        destination?.hasRoute<JobRoute>() == true || destination?.hasRoute<ActivityRoute>() == true -> "activity"
        else -> "home"
    }
    val rootRoutes = setOf("home", "tools", "downloads", "files", "settings")
    var lastTab by rememberSaveable { mutableStateOf(DockTab.HOME) }
    val tab = MainDockTabs.firstOrNull { it.route == route } ?: lastTab
    var tabHistory by rememberSaveable { mutableStateOf(listOf<String>()) }
    var selectedJob by rememberSaveable { mutableStateOf<String?>(null) }
    fun tabDestination(next: DockTab): Any = when (next) {
        DockTab.TOOLS -> ToolsRoute
        DockTab.FILES -> FilesRoute
        DockTab.HOME -> HomeRoute
        DockTab.DOWNLOADS -> DownloadsRoute
        DockTab.SETTINGS -> SettingsRoute
        DockTab.PRINT -> PrintRoute
    }
    fun switchTab(next: DockTab, recordHistory: Boolean = true) {
        if (next == DockTab.PRINT) {
            navController.navigate(PrintRoute) { launchSingleTop = true }
            return
        }
        if (recordHistory && next != tab) tabHistory = (tabHistory.filterNot { it == next.name } + tab.name).takeLast(5)
        lastTab = next
        // Standard Navigation multiple-back-stack contract: save the outgoing stack,
        // restore the incoming stack, and never clear a processing session on a tab tap.
        navController.navigate(tabDestination(next)) {
            popUpTo(HomeRoute) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    fun navigateTab(next: DockTab) = switchTab(next)
    fun currentToolOrigin():String? {
        val entry=navController.currentBackStackEntry ?: return null
        return when {
            entry.destination.hasRoute<ToolRoute>() -> entry.toRoute<ToolRoute>().transitionOrigin
            entry.destination.hasRoute<ScanRoute>() -> entry.toRoute<ScanRoute>().transitionOrigin
            entry.destination.hasRoute<ViewerRoute>() -> entry.toRoute<ViewerRoute>().returnTab
            else -> null
        }
    }
    fun openWorkspace() {
        val origin=currentToolOrigin() ?: tab.route
        if (tab != DockTab.TOOLS) switchTab(DockTab.TOOLS)
        navController.navigate(ToolRoute(viewModel.form.value.toolId,origin)) {
            popUpTo(ToolsRoute)
            launchSingleTop = true
        }
    }
    fun navigateTo(name: String) {
        appHaptics.actionClick()
        when (name) {
            "workspace" -> openWorkspace()
            "settings" -> navController.navigate(SettingsDetailRoute) { launchSingleTop = true }
            "storage" -> navController.navigate(StorageRoute) { launchSingleTop = true }
            "scan" -> {
                val origin=currentToolOrigin() ?: tab.route
                viewModel.recordToolOpen("scan_doc")
                if (tab != DockTab.TOOLS) switchTab(DockTab.TOOLS)
                navController.navigate(ScanRoute(origin)) { launchSingleTop = true }
            }
            "activity" -> navController.navigate(selectedJob?.let(::JobRoute) ?: ActivityRoute) { launchSingleTop = true }
            "files", "files_overview" -> switchTab(DockTab.FILES)
            "tools", "tools_catalog" -> switchTab(DockTab.TOOLS)
            "downloads" -> switchTab(DockTab.DOWNLOADS)
            "print" -> navController.navigate(PrintRoute) { launchSingleTop = true }
            else -> switchTab(DockTab.HOME)
        }
    }
    fun leaveWorkspace() {
        appHaptics.navigationTab()
        val originTab=currentToolOrigin()?.let {origin->MainDockTabs.firstOrNull {it.route==origin}}
        if (route == "workspace") viewModel.closeToolSession()
        if (route == "activity") selectedJob = null
        if(originTab!=null && originTab!=tab) {
            // Remove the completed tool before saving the Tools stack; reopening the tab must not
            // restore a closed session. Its source tab then becomes the actual Back destination.
            navController.popBackStack()
            tabHistory=tabHistory.filterNot {it==originTab.name}
            switchTab(originTab,recordHistory=false)
        } else if (route in rootRoutes && tabHistory.isNotEmpty()) {
            val previous = DockTab.valueOf(tabHistory.last())
            tabHistory = tabHistory.dropLast(1)
            switchTab(previous, recordHistory = false)
        } else if (!navController.popBackStack()) switchTab(DockTab.HOME, recordHistory = false)
    }
    val dirty by viewModel.hasUnsavedWorkspace.collectAsStateWithLifecycle()
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    fun goBack() { if (route == "workspace" && dirty) confirmLeave = true else leaveWorkspace() }
    var backSwipe by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = route !in rootRoutes || tabHistory.isNotEmpty()) { progress ->
        try {
            progress.collect { backSwipe = it.progress.coerceIn(0f, 1f) }
            goBack()
        } finally { backSwipe = 0f }
    }
    // A lambda refreshes the captured tab; local function-reference equality ignores captures.
    val latestOpenWorkspace by rememberUpdatedState { openWorkspace() }
    DisposableEffect(hostActivity, navController) {
        val listener = Consumer<Intent> { incoming ->
            val uri = incoming.data
            if (incoming.action == Intent.ACTION_VIEW && uri?.scheme == "jumlo" && uri.host == "workspace") {
                runCatching { latestOpenWorkspace() }
                    .onFailure { viewModel.notifyMessage("INVALID_SHARED_CONTENT") }
            }
        }
        hostActivity?.addOnNewIntentListener(listener)
        onDispose { hostActivity?.removeOnNewIntentListener(listener) }
    }
    LaunchedEffect(navController, viewModel) {
        viewModel.sharedLinkOpenRequests.collect { latestOpenWorkspace() }
    }
    LaunchedEffect(viewModel, navController) {
        viewModel.openPrintRequests.collect { navController.navigate(PrintRoute) { launchSingleTop = true } }
    }
    LaunchedEffect(viewModel, navController) {
        viewModel.jobOpenRequests.collect { id ->
            selectedJob = id
            navController.navigate(JobRoute(id)) { launchSingleTop = true }
        }
    }

    var deleteEntry by remember { mutableStateOf<DocumentEntry?>(null) }
    var renameEntry by remember { mutableStateOf<DocumentEntry?>(null) }
    var renameText by remember { mutableStateOf("") }
    var newFolder by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    var firstRunGuideChecked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settings.isLoaded, settings.hasCompletedOnboarding) {
        if (settings.isLoaded && !firstRunGuideChecked) {
            firstRunGuideChecked = true
            if (!settings.hasCompletedOnboarding) showGuide = true
        }
    }
    val snackbar=remember { SnackbarHostState() }
    val undoLabel=stringResource(R.string.action_undo)
    LaunchedEffect(message, undoTrashIds, undoRename) {
        val currentMsg=message ?: return@LaunchedEffect
        val targetUndo=undoTrashIds
        val renameUndo=undoRename
        val result=snackbar.showSnackbar(
            message=context.getString(userMessageResource(currentMsg)),
            actionLabel=if(targetUndo.isNotEmpty() || renameUndo!=null) undoLabel else null,
            duration=SnackbarDuration.Short
        )
        if(result==SnackbarResult.ActionPerformed && targetUndo.isNotEmpty()) {
            viewModel.restoreOutputs(targetUndo)
        } else if(result==SnackbarResult.ActionPerformed && renameUndo!=null) {
            viewModel.undoOutputRename(renameUndo)
        } else {
            viewModel.dismissMessage()
        }
    }
    fun collectProviderIntake(data:Intent?):Intake? = try {
        IntakeController.collect(data)
    } catch(error:Exception) {
        com.jumlo.app.diagnostics.AppDiagnostics.report("intake",error)
        viewModel.notifyMessage("INVALID_SHARED_CONTENT")
        null
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode==Activity.RESULT_OK) collectProviderIntake(result.data)?.let { viewModel.intake(it, attachToWorkspace=true) }
    }
    val libraryPicker=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode==Activity.RESULT_OK) collectProviderIntake(result.data)?.let { viewModel.intake(it, attachToWorkspace=false) }
    }
    val printPicker=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode==Activity.RESULT_OK) collectProviderIntake(result.data)?.let(viewModel::importForPrint)
    }
    fun importPrintDocument() {
        printPicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type="*/*"
            putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("application/pdf","image/*"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        })
    }
    val photos=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        viewModel.intake(Intake(uris.map(Uri::toString),Intent.FLAG_GRANT_READ_URI_PERMISSION,null))
    }
    val folderPicker=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode==Activity.RESULT_OK) result.data?.let { data -> data.data?.let { viewModel.acceptTree(it.toString(),data.flags) } }
    }
    val downloadFolderPicker=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode==Activity.RESULT_OK) result.data?.let { data ->
            data.data?.let { uri -> viewModel.setDownloadDestinationFolder(uri.toString(),data.flags) }
        }
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
        val mime=when(form.toolId) { "pdf_02","img_01","img_02","img_03","img_04"->"image/*";"pdf_01","pdf_03","pdf_07","pdf_to_docx","pdf_to_pptx","pdf_compress","pdf_ocr"->"application/pdf";"file_08"->"application/zip";else->"*/*" }
        picker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE);type=mime;putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) })
    }
    fun importLibraryFiles() {
        libraryPicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE);type="*/*";putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) })
    }
    fun pickFolder(initial:String?=null) {
        folderPicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);if(initial!=null) putExtra(DocumentsContract.EXTRA_INITIAL_URI,Uri.parse(initial)) })
    }
    fun pickDownloadDestination() {
        downloadFolderPicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        })
    }
    fun openSavedFolder() {
        val location=settings.downloadDestinationFolder
        try {
            val folder=if(location.startsWith("content://")) Uri.parse(location)
                else Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2FJumlo")
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {setDataAndType(folder,"vnd.android.document/directory");addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)})
        } catch(_:Exception) { navigateTab(DockTab.FILES) }
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
    fun openOutput(output: OutputRecord) {
        if (output.mimeType.equals("application/pdf", ignoreCase = true)) {
            navController.navigate(ViewerRoute(output.uri, output.displayName)) { launchSingleTop = true }
        } else launchExternal(output.uri, output.mimeType, false)
    }
    fun exportOutput(output:OutputRecord) {
        viewModel.prepareExport(output)
        exportPicker.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE);type=output.mimeType;putExtra(Intent.EXTRA_TITLE,output.displayName) })
    }
    fun openTool(tool:UtilityTool) {
        if(!CapabilityRegistry.visible(tool.id) || !CapabilityRegistry.enabled(tool.id)) {
            viewModel.notifyMessage("CAPABILITY_UNAVAILABLE");return
        }
        when(tool.id) {
            "scan_doc" -> navigateTo("scan")
            "dl_settings" -> {viewModel.recordToolOpen(tool.id);navigateTo("settings")}
            "file_02", "file_trash" -> {viewModel.recordToolOpen(tool.id);navigateTo("files_overview")}
            else -> if(viewModel.chooseTool(tool.id)) navigateTo("workspace")
        }
    }

    @Composable fun Shell(shellRoute: String, jobId: String? = null, viewer: ViewerRoute? = null, entryId:String?=null, transitionScope:AnimatedVisibilityScope?=null, entryToolId:String?=null, transitionOrigin:String="tools") {
        var scanPublishing by remember(entryId) { mutableStateOf(false) }
        val root = shellRoute in rootRoutes
        val showDock = root || shellRoute in setOf("workspace", "activity")
        val swipeDistance=with(LocalDensity.current) { 54.dp.toPx() }
        val headerToolId=entryToolId ?: form.toolId
        CompositionLocalProvider(LocalToolTransitionVisibility provides transitionScope,
            LocalToolTransitionOrigin provides if(root) shellRoute else transitionOrigin) {
        com.jumlo.app.ui.adaptive.HingeSafeViewport {
        Scaffold(
            snackbarHost={SnackbarHost(snackbar)},
            bottomBar={if(showDock) QuietFloatingDock(selectedTab=tab,language=settings.language,onSelectTab=::navigateTab)},
            contentWindowInsets=WindowInsets(0,0,0,0)
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).testTag("route.$shellRoute").graphicsLayer {
                if(!root && !reduceMotion) {
                    translationX=(if(rtl) -1f else 1f)*backSwipe*swipeDistance
                    scaleX=1f-.035f*backSwipe
                    scaleY=1f-.035f*backSwipe
                }
            }) {
                Column(Modifier.fillMaxSize()) {
                    if(root) QuietRootTopBar(
                        title=if(shellRoute=="home") stringResource(R.string.app_name)
                            else if(settings.language.isRtl) tab.labelAr else tab.labelEn,
                        onSettingsClick={navigateTo("settings")},showSettings=shellRoute!="settings"
                    ) else if(shellRoute!="viewer") QuietWorkspaceTopBar(
                        title=when(shellRoute) {
                            "workspace" -> CapabilityRegistry.tools.find {it.id==headerToolId}?.let {title(it,settings.language)} ?: stringResource(R.string.workspace_title)
                            "activity" -> stringResource(R.string.activity_title)
                            "scan" -> stringResource(R.string.scan_title)
                            "print" -> stringResource(R.string.upgrade_print)
                            "storage" -> stringResource(R.string.modern_storage_usage)
                            "viewer" -> viewer?.title.orEmpty()
                            else -> stringResource(R.string.settings_title)
                        }, onBack=::goBack,
                        toolId=when(shellRoute) { "workspace" -> headerToolId; "scan" -> "scan_doc"; else -> null }
                    )
                    DropFilesTarget(enabled=shellRoute in setOf("files","workspace"),
                        modifier=Modifier.weight(1f).fillMaxWidth(),
                        onDropFiles={uris,release -> viewModel.intake(
                            Intake(uris,Intent.FLAG_GRANT_READ_URI_PERMISSION,null),
                            attachToWorkspace=shellRoute=="workspace",onFinished=release)},
                        onFailure={viewModel.notifyMessage("NO_COMPATIBLE_APP_OR_ACCESS_LOST")}) {
                        when(shellRoute) {
                            "home" -> com.jumlo.app.ui.home.HomeScreen(viewModel,::openTool,
                                 {navigateTab(DockTab.TOOLS)},
                                 {selectedJob=it;navigateTo("activity")},
                                 {openOutput(it)},
                                 {navigateTo("workspace")},
                                 {navigateTab(DockTab.FILES)},
                                 {navigateTo("scan")},
                                 {navigateTab(DockTab.DOWNLOADS)})
                            "tools" -> com.jumlo.app.ui.tools.ToolsScreen(viewModel,::openTool)
                            "downloads" -> com.jumlo.app.ui.downloads.DownloadsScreen(viewModel,::openTool,{selectedJob=it;navigateTo("activity")})
                            "files" -> com.jumlo.app.ui.files.FilesScreen(viewModel,
                                ::importLibraryFiles, {pickFolder()}, ::openSavedFolder,
                                {openOutput(it)},
                                {launchExternal(it.uri,it.mimeType,true)}, ::exportOutput,
                                {record->viewModel.prepareOutputWorkflow(OutputWorkflowPolicy.reuseTool(record.mimeType),listOf(record)) {navigateTo("workspace")} },
                                {sourceId->viewModel.prepareSourceWorkflow(sourceId) {navigateTo("workspace")} },
                                {pickFolder(it.uri)}, {entry ->
                                    if(entry.mimeType=="application/pdf") navController.navigate(ViewerRoute(entry.uri,entry.name))
                                    else launchExternal(entry.uri,entry.mimeType,false)
                                },
                                {entry->viewModel.importProviderEntryToWorkspace(entry.uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) {navigateTo("workspace")} },
                                {viewModel.copyOrMoveFile(it,false,CollisionPolicy.KEEP_BOTH)},
                                {renameEntry=it;renameText=it.name}, {deleteEntry=it}, {newFolder=true;folderName=""},
                                {chosen,toolId->viewModel.prepareOutputWorkflow(toolId,chosen) {navigateTo("workspace")} },
                                {chosen,toolId->viewModel.prepareSourceBatchWorkflow(toolId,chosen) {navigateTo("workspace")} },
                                onPrint=viewModel::printOutput,onPrintSource={viewModel.printSource(it)},
                                onChangeSaveFolder=::pickDownloadDestination,onStorage={navigateTo("storage")})
                            "print" -> com.jumlo.app.printing.PrintScreen(
                                source=printSource,onBack=::goBack,onPickDocument=::importPrintDocument,
                                modifier=Modifier.padding(bottom=QuietTheme.spacing.listBottomBuffer))
                            "settings", "settings_detail" -> com.jumlo.app.ui.settings.SettingsScreen(viewModel,
                                onRequestNotifications={if(Build.VERSION.SDK_INT>=33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)},
                                onExportLogs={viewModel.prepareErrorLogExport();errorLogExportPicker.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {addCategory(Intent.CATEGORY_OPENABLE);type="text/plain";putExtra(Intent.EXTRA_TITLE,"jumlo-errors.txt")})},
                                onOpenFiles={navigateTab(DockTab.FILES)},onChooseFolder={pickFolder()},
                                onChooseDownloadFolder={pickDownloadDestination()},onOpenSavedFolder={openSavedFolder()},
                                onShowGuide={showGuide=true},onOpenStorage={navigateTo("storage")})
                            "storage" -> com.jumlo.app.ui.settings.StorageScreen(viewModel,::pickDownloadDestination,::openSavedFolder)
                            "viewer" -> viewer?.let { com.jumlo.app.ui.viewer.PdfViewerScreen(it.uri,it.title,onBack=::goBack,onOpenFile={navigateTab(DockTab.FILES)},onPrint={u,_->viewModel.importForPrint(Intake(listOf(u),Intent.FLAG_GRANT_READ_URI_PERMISSION,null))}) }
                            "scan" -> com.jumlo.app.ui.scan.ScanScreen(
                                externalBusy=scanPublishing,
                                onScannedPdf={file -> scanPublishing=true; viewModel.publishScannedPdf(file,onFinished={scanPublishing=false}) { output ->
                                    // Publication may finish after the user has already left Scan.
                                    if(navController.currentBackStackEntry?.id==entryId && navController.currentDestination?.hasRoute<ScanRoute>()==true) {
                                        navController.navigate(ViewerRoute(output.uri,output.displayName,returnTab=transitionOrigin)) {
                                            popUpTo<ScanRoute> { inclusive=true }
                                        }
                                    }
                                } }, onBack=::goBack
                            )
                            "workspace" -> {
                                val imported by viewModel.imported.collectAsStateWithLifecycle()
                                val busy by viewModel.busy.collectAsStateWithLifecycle()
                                val urls by viewModel.urls.collectAsStateWithLifecycle()
                                if(!CapabilityRegistry.visible(form.toolId) || !CapabilityRegistry.enabled(form.toolId)) QuietStateCard(
                                    title=stringResource(R.string.ui_tool_unavailable),
                                    subtitle=stringResource(R.string.ui_unavailable_body),
                                    actionLabel=stringResource(R.string.ui_choose_tool),
                                    onAction={goBack()},modifier=Modifier.padding(20.dp)
                                ) else if(form.toolId in setOf("dl_media","dl_02")) com.jumlo.app.ui.downloads.MediaWorkspace(urls,sessionEpoch,{selectedJob=it;navigateTo("activity")},{viewModel.chooseTool("dl_02");viewModel.setUrls(it);navigateTo("workspace")},defaultVideoQuality=settings.defaultVideoQuality,defaultAudioQuality=settings.defaultAudioQuality)
                                else com.jumlo.app.ui.workspace.WorkspaceScreen(viewModel,form,imported,busy,urls,reduceMotion,::importFiles,{photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},::launchGoogleSignIn,onPrintPages={sourceId,pages->viewModel.printSource(sourceId,pages)}) {viewModel.submit {selectedJob=it;navigateTo("activity")}}
                            }
                            "activity" -> com.jumlo.app.ui.downloads.ActivityScreen(viewModel,jobId,{openOutput(it)},{launchExternal(it.uri,it.mimeType,true)},::exportOutput,{record->viewModel.prepareOutputWorkflow(OutputWorkflowPolicy.reuseTool(record.mimeType),listOf(record)) {navigateTo("workspace")} },{selectedJob=null;viewModel.allowNewRun();navigateTo("workspace")},onShowAll=if(jobId!=null) ({selectedJob=null;navController.navigate(ActivityRoute) { launchSingleTop=true }}) else null,onPrint=viewModel::printOutput)
                        }

                    }
                }
            }
        }
        }
    }

    }
    SharedTransitionLayout(Modifier.fillMaxSize()) {
    CompositionLocalProvider(LocalToolSharedTransitionScope provides this) {
    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                fadeIn(tween(if(reduceMotion) 0 else JumloMotion.standardMs)) +
                    (if(reduceMotion) EnterTransition.None
                    else if(targetState.destination.hasRoute<PrintRoute>()) scaleIn(spring(dampingRatio=JumloMotion.printScaleDamping,stiffness=JumloMotion.navStiffness),initialScale=.94f)
                    else slideInHorizontally(spring(dampingRatio=JumloMotion.navDamping,stiffness=JumloMotion.navStiffness)) {
                        (if(rtl) -1 else 1) * it / 7
                    })
            },
            exitTransition = {
                fadeOut(tween(if(reduceMotion) 0 else JumloMotion.exitMs)) +
                    (if(reduceMotion) ExitTransition.None
                    else if(initialState.destination.hasRoute<PrintRoute>()) scaleOut(spring(dampingRatio=JumloMotion.printScaleDamping,stiffness=JumloMotion.navStiffness),targetScale=.96f)
                    else slideOutHorizontally(spring(dampingRatio=JumloMotion.navDamping,stiffness=JumloMotion.navStiffness)) {
                        (if(rtl) 1 else -1) * it / 10
                    })
            },
            popEnterTransition = {
                fadeIn(tween(if(reduceMotion) 0 else JumloMotion.standardMs)) +
                    (if(reduceMotion) EnterTransition.None
                    else if(targetState.destination.hasRoute<PrintRoute>()) scaleIn(spring(dampingRatio=JumloMotion.printScaleDamping,stiffness=JumloMotion.navStiffness),initialScale=.94f)
                    else slideInHorizontally(spring(dampingRatio=JumloMotion.navDamping,stiffness=JumloMotion.navStiffness)) {
                        (if(rtl) 1 else -1) * it / 7
                    })
            },
            popExitTransition = {
                fadeOut(tween(if(reduceMotion) 0 else JumloMotion.exitMs)) +
                    (if(reduceMotion) ExitTransition.None
                    else if(initialState.destination.hasRoute<PrintRoute>()) scaleOut(spring(dampingRatio=JumloMotion.printScaleDamping,stiffness=JumloMotion.navStiffness),targetScale=.96f)
                    else slideOutHorizontally(spring(dampingRatio=JumloMotion.navDamping,stiffness=JumloMotion.navStiffness)) {
                        (if(rtl) -1 else 1) * it / 10
                    })
            }
        ) {
            composable<HomeRoute> { Shell("home",transitionScope=this) }
            composable<ToolsRoute> { Shell("tools",transitionScope=this) }
            composable<DownloadsRoute> { Shell("downloads",transitionScope=this) }
            composable<FilesRoute> { Shell("files",transitionScope=this) }
            composable<PrintRoute> { Shell("print",transitionScope=this) }
            composable<SettingsRoute> { Shell("settings",transitionScope=this) }
            composable<SettingsDetailRoute> { Shell("settings_detail",transitionScope=this) }
            composable<StorageRoute> { Shell("storage",transitionScope=this) }
            composable<ToolRoute> { entry ->
                val args=entry.toRoute<ToolRoute>()
                LaunchedEffect(args.toolId,workspaceReady) {
                    // An outgoing or just-restored entry must not reset the newly opened tool.
                    if(workspaceReady && navController.currentBackStackEntry?.id==entry.id &&
                        viewModel.form.value.toolId!=args.toolId) viewModel.chooseTool(args.toolId)
                }
                Shell("workspace",transitionScope=this,entryToolId=args.toolId,transitionOrigin=args.transitionOrigin)
            }
            composable<ViewerRoute> { entry -> Shell("viewer",viewer=entry.toRoute<ViewerRoute>(),transitionScope=this) }
            composable<JobRoute> { entry -> Shell("activity",jobId=entry.toRoute<JobRoute>().jobId,transitionScope=this) }
            composable<ScanRoute> { entry -> Shell("scan",entryId=entry.id,transitionScope=this,
                transitionOrigin=entry.toRoute<ScanRoute>().transitionOrigin) }
            composable<WorkspaceRoute>(deepLinks = listOf(navDeepLink<WorkspaceRoute>(basePath = "jumlo://workspace"))) { Shell("workspace",transitionScope=this) }
            composable<ActivityRoute> { Shell("activity",transitionScope=this) }
        }
        if (showGuide) OnboardingGuide(
            onImport = {
                showGuide = false
                viewModel.finishOnboarding()
                importLibraryFiles()
            },
            onScan = {
                showGuide = false
                viewModel.finishOnboarding()
                navigateTo("scan")
            },
            onExplore = {
                showGuide = false
                viewModel.finishOnboarding()
                navigateTab(DockTab.TOOLS)
            },
            onDismiss = {
                showGuide = false
                viewModel.finishOnboarding()
            }
        )
    }
    }
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
        val listing by viewModel.tree.collectAsStateWithLifecycle()
        com.jumlo.app.ui.files.ProviderRenameDialog(entry.uri,renameText,listing,
            {renameText=it},{renameEntry=null},{name->renameEntry=null;viewModel.renameFile(entry,name)})
    }
    if(newFolder) {
        AlertDialog(
            onDismissRequest={newFolder=false},
            title={Text(stringResource(R.string.files_new_folder))},
            text={TextField(folderName,{folderName=it},singleLine=true,label={Text(stringResource(R.string.files_new_folder))})},
            confirmButton={TextButton(onClick={newFolder=false;viewModel.createFolder(folderName)}) {Text(stringResource(R.string.action_create))}},
            dismissButton={TextButton(onClick={newFolder=false}) {Text(stringResource(R.string.action_cancel))}}
        )
    }
    if(confirmLeave) {
      val busy by viewModel.busy.collectAsStateWithLifecycle()
      AlertDialog(
        onDismissRequest={confirmLeave=false},
        title={Text(stringResource(R.string.ui_unsaved_title))},
        text={Text(stringResource(R.string.ui_unsaved_body))},
        confirmButton={TextButton(enabled=!busy,onClick={viewModel.saveWorkspaceDraft { confirmLeave=false;leaveWorkspace() }}) {Text(stringResource(R.string.ui_save_leave))}},
        dismissButton={Column {
            TextButton(onClick={confirmLeave=false}) {Text(stringResource(R.string.ui_keep_editing))}
            TextButton(enabled=!busy,onClick={viewModel.discardWorkspaceChanges { confirmLeave=false;leaveWorkspace() }}) {Text(stringResource(R.string.ui_discard_leave))}
        }}
    )
    }
}
