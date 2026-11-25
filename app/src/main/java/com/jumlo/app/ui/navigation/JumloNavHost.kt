package com.jumlo.app.ui.navigation

import android.Manifest
import android.app.Activity
import android.content.ContextWrapper
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jumlo.app.ui.theme.JumloMotion
import com.jumlo.app.ui.theme.QuietTheme
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
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
import com.jumlo.app.data.office.DocumentViewerFormats
import com.jumlo.app.data.office.DocumentViewerKind
import com.jumlo.app.ui.viewer.OfficeDocumentViewerScreen
import com.jumlo.app.core.OutputWorkflowPolicy
import com.jumlo.app.model.*
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.worker.ActiveTaskTracker
import com.jumlo.app.ui.components.*
import com.jumlo.app.ui.intake.Intake
import com.jumlo.app.ui.util.rememberAppHaptics
import com.jumlo.app.ui.intake.IntakeController
import com.jumlo.app.ui.theme.LocalReducedMotion
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

private val ROOT_ROUTES = setOf("home", "tools", "files", "settings")
private val DROP_TARGET_ROUTES = setOf("files", "workspace")
private val MEDIA_WORKSPACE_TOOLS = setOf("dl_media", "dl_02")

private fun title(tool:UtilityTool,language:AppLanguage)=if(language.isRtl) tool.titleAr else tool.titleEn
private fun subtitle(tool:UtilityTool,language:AppLanguage)=if(language.isRtl) tool.subtitleAr else tool.subtitleEn

/** Serializable route arguments survive recreation; readable names are documented in ROUTES.md. */
@Serializable @SerialName("home") object HomeRoute
@Serializable @SerialName("tools") object ToolsRoute
@Serializable @SerialName("downloads") object DownloadsRoute
@Serializable @SerialName("tools/print") object PrintRoute
@Serializable @SerialName("files") object FilesRoute
@Serializable @SerialName("settings") object SettingsRoute
@Serializable @SerialName("settings/details") object SettingsDetailRoute
@Serializable @SerialName("settings/storage") object StorageRoute
@Serializable @SerialName("tools/scan") data class ScanRoute(val transitionOrigin: String = "tools", val initialMode: String? = null)
@Serializable @SerialName("tools/run") data class ToolRoute(val toolId: String, val transitionOrigin: String = "tools")
@Serializable @SerialName("files/viewer") data class ViewerRoute(val uri: String, val title: String, val returnTab: String? = null, val mimeType: String? = null)
@Serializable @SerialName("downloads/job") data class JobRoute(val jobId: String, val returnTab: String? = null)
@Serializable @SerialName("workspace") object WorkspaceRoute // Compatibility deep link.
@Serializable @SerialName("downloads/activity") object ActivityRoute

@Composable
fun JumloNavHost(viewModel:AppViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val formToolId by remember(viewModel) {
        viewModel.form.map { it.toolId }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = "pdf_02")
    val printSource by viewModel.printSource.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val undoTrashIds by viewModel.undoTrashIds.collectAsStateWithLifecycle()
    val undoRename by viewModel.undoRename.collectAsStateWithLifecycle()
    val sessionEpoch by viewModel.sessionEpoch.collectAsStateWithLifecycle()
    val workspaceReady by viewModel.workspaceReady.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val appHaptics = rememberAppHaptics()
    val latestHaptics by rememberUpdatedState(appHaptics)
    LaunchedEffect(viewModel) {
        val lastStages = mutableMapOf<String, JobStage>()
        viewModel.jobs
            .map { jobs -> jobs.map { it.request.id to it.progress.stage } }
            .distinctUntilChanged()
            .collect { stages ->
                lastStages.keys.retainAll(stages.mapTo(mutableSetOf()) { it.first })
                stages.forEach { (id, stage) ->
                    val previous = lastStages.put(id, stage)
                    if (previous != null && previous != JobStage.COMPLETED && stage == JobStage.COMPLETED) latestHaptics.downloadComplete()
                    else if (previous != null && previous != JobStage.FAILED && stage == JobStage.FAILED) latestHaptics.error()
                }
            }
    }
    val reduceMotion=LocalReducedMotion.current
    val rtl=LocalLayoutDirection.current==LayoutDirection.Rtl
    val swipeDistance=with(LocalDensity.current) { 54.dp.toPx() }
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
    var lastTab by rememberSaveable { mutableStateOf(DockTab.HOME) }
    val tab = MainDockTabs.firstOrNull { it.route == route } ?: lastTab
    var tabHistory by rememberSaveable { mutableStateOf(listOf<String>()) }
    var selectedJob by rememberSaveable { mutableStateOf<String?>(null) }
    // Keyed per back-stack entry so an exiting screen's onDispose cannot clobber the active screen's back ownership.
    val childBackOwners = remember { mutableStateMapOf<String, Boolean>() }
    val childBackOwned = childBackOwners[currentEntry?.id ?: route] == true
    val activeToolId = when {
        route != "workspace" -> null
        destination?.hasRoute<ToolRoute>() == true ->
            runCatching { currentEntry?.toRoute<ToolRoute>()?.toolId }.getOrNull() ?: formToolId
        else -> formToolId
    }
    LaunchedEffect(route, selectedJob, activeToolId) { ActiveTaskTracker.updateNavigation(route, selectedJob, activeToolId) }
    var filesInitialFilter by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(route) { if (route == "storage") filesInitialFilter = null }
    fun tabDestination(next: DockTab): Any = when (next) {
        DockTab.TOOLS -> ToolsRoute
        DockTab.FILES -> FilesRoute
        DockTab.HOME -> HomeRoute
        DockTab.DOWNLOADS -> DownloadsRoute
        DockTab.SETTINGS -> SettingsRoute
        DockTab.PRINT -> PrintRoute
    }
    var homeResetEpoch by rememberSaveable { mutableIntStateOf(0) }
    var toolsResetEpoch by rememberSaveable { mutableIntStateOf(0) }
    var filesResetEpoch by rememberSaveable { mutableIntStateOf(0) }
    var settingsResetEpoch by rememberSaveable { mutableIntStateOf(0) }
    val homeListState = rememberLazyListState()
    val toolsGridState = rememberLazyGridState()
    val filesListState = rememberLazyListState()
    val settingsListState = rememberLazyListState()
    fun switchTab(next: DockTab, recordHistory: Boolean = true) {
        // Downloads remains reachable, but is no longer a root tab or a saved root stack.
        if (next == DockTab.DOWNLOADS) {
            navController.navigate(DownloadsRoute) { launchSingleTop = true }
            return
        }
        if (next == DockTab.PRINT) {
            navController.navigate(PrintRoute) { launchSingleTop = true }
            return
        }
        if (next == tab) {
            if (route !in ROOT_ROUTES) {
                if (route == "workspace") viewModel.closeToolSession()
                if (route == "activity") selectedJob = null
                val targetDest = tabDestination(next)
                val popped = when (next) {
                    DockTab.HOME -> navController.popBackStack<HomeRoute>(inclusive = false)
                    DockTab.TOOLS -> navController.popBackStack<ToolsRoute>(inclusive = false)
                    DockTab.FILES -> navController.popBackStack<FilesRoute>(inclusive = false)
                    DockTab.SETTINGS -> navController.popBackStack<SettingsRoute>(inclusive = false)
                    else -> false
                }
                if (!popped) {
                    navController.navigate(targetDest) {
                        popUpTo(targetDest) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }
            when (next) {
                DockTab.HOME -> homeResetEpoch++
                DockTab.TOOLS -> toolsResetEpoch++
                DockTab.FILES -> filesResetEpoch++
                DockTab.SETTINGS -> settingsResetEpoch++
                else -> Unit
            }
            return
        }
        if (recordHistory) tabHistory = (tabHistory.filterNot { it == next.name } + tab.name).takeLast(5)
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
            entry.destination.hasRoute<JobRoute>() -> entry.toRoute<JobRoute>().returnTab
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
        appHaptics.screenTransition()
        when (name) {
            "workspace" -> openWorkspace()
            "settings" -> switchTab(DockTab.SETTINGS)
            "storage" -> navController.navigate(StorageRoute) { launchSingleTop = true }
            "scan" -> {
                val origin=currentToolOrigin() ?: tab.route
                viewModel.recordToolOpen("scan_doc")
                if (tab != DockTab.TOOLS) switchTab(DockTab.TOOLS)
                navController.navigate(ScanRoute(origin)) {
                    popUpTo(ToolsRoute)
                    launchSingleTop = true
                }
            }
            "activity" -> navController.navigate(selectedJob?.let { JobRoute(it) } ?: ActivityRoute) { launchSingleTop = true }
            "files", "files_overview" -> switchTab(DockTab.FILES)
            "tools", "tools_catalog" -> switchTab(DockTab.TOOLS)
            "downloads" -> switchTab(DockTab.DOWNLOADS)
            "print" -> navController.navigate(PrintRoute) { launchSingleTop = true }
            else -> switchTab(DockTab.HOME)
        }
    }
    fun leaveWorkspace(preserveDraft: Boolean = false) {
        appHaptics.screenTransition()
        val originTab=currentToolOrigin()?.let {origin->MainDockTabs.firstOrNull {it.route==origin}}
        if (route == "workspace") viewModel.closeToolSession(preserveDraft = preserveDraft)
        if (route == "activity") selectedJob = null
        if(originTab!=null && originTab!=tab) {
            // Remove the completed tool before saving the Tools stack; reopening the tab must not
            // restore a closed session. Its source tab then becomes the actual Back destination.
            navController.popBackStack()
            tabHistory=tabHistory.filterNot {it==originTab.name}
            switchTab(originTab,recordHistory=false)
        } else if (route in ROOT_ROUTES && tabHistory.isNotEmpty()) {
            val previous = DockTab.entries.firstOrNull { it.name == tabHistory.last() } ?: DockTab.HOME
            tabHistory = tabHistory.dropLast(1)
            switchTab(previous, recordHistory = false)
        } else if (!navController.popBackStack()) switchTab(DockTab.HOME, recordHistory = false)
    }
    val dirty by viewModel.hasUnsavedWorkspace.collectAsStateWithLifecycle()
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    fun goBack() { if (route == "workspace" && dirty) confirmLeave = true else leaveWorkspace() }
    var backSwipe by remember { mutableFloatStateOf(0f) }
    // A lambda refreshes the captured tab; local function-reference equality ignores captures.
    val latestOpenWorkspace by rememberUpdatedState { openWorkspace() }
    DisposableEffect(hostActivity, navController) {
        val listener = Consumer<Intent> { incoming ->
            val uri = incoming.data
            if ((incoming.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0 &&
                incoming.action == Intent.ACTION_VIEW && uri?.scheme == "jumlo" && uri.host == "workspace") {
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
    fun openCompletedJob(id: String) {
        selectedJob = id
        val origin = currentToolOrigin()
        val fromToolRoute = navController.currentDestination?.hasRoute<ToolRoute>() == true
        val fromWorkspaceRoute = navController.currentDestination?.hasRoute<WorkspaceRoute>() == true
        val fromWorkspace = fromToolRoute || fromWorkspaceRoute
        if (fromWorkspace) viewModel.closeToolSession()
        navController.navigate(JobRoute(id, returnTab = if (fromWorkspace) origin else null)) {
            if (fromToolRoute) popUpTo<ToolRoute> { inclusive = true }
            else if (fromWorkspaceRoute) popUpTo<WorkspaceRoute> { inclusive = true }
            launchSingleTop = true
        }
    }
    LaunchedEffect(viewModel, navController) {
        viewModel.jobOpenRequests.collect { id -> openCompletedJob(id) }
    }

    var deleteEntry by remember { mutableStateOf<DocumentEntry?>(null) }
    var renameEntry by remember { mutableStateOf<DocumentEntry?>(null) }
    var renameText by remember { mutableStateOf("") }
    var newFolder by rememberSaveable { mutableStateOf(false) }
    var folderName by rememberSaveable { mutableStateOf("") }
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
        snackbar.currentSnackbarData?.dismiss()
        val targetUndo=undoTrashIds
        val renameUndo=undoRename
        val hasUndo=targetUndo.isNotEmpty() || renameUndo!=null
        val result=snackbar.showSnackbar(
            message=context.getString(userMessageResource(currentMsg)),
            actionLabel=if(hasUndo) undoLabel else null,
            // An Undo action needs time to reach; Short (4s) is too tight for TalkBack/motor users.
            duration=if(hasUndo) SnackbarDuration.Long else SnackbarDuration.Short
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
    var pendingImportFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    val libraryPicker=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val targetFolder = pendingImportFolderId
        pendingImportFolderId = null
        if(result.resultCode==Activity.RESULT_OK) collectProviderIntake(result.data)?.let { viewModel.importToLibrary(it, targetFolderId = targetFolder) }
    }
    val printPicker=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode==Activity.RESULT_OK) collectProviderIntake(result.data)?.let(viewModel::importForPrint)
    }
    fun <T> ActivityResultLauncher<T>.safeLaunch(input: T) {
        try { launch(input) } catch (_: ActivityNotFoundException) { viewModel.notifyMessage("NO_COMPATIBLE_APP_OR_ACCESS_LOST") }
    }
    fun importPrintDocument() {
        printPicker.safeLaunch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
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
    var askedNotificationPermission by rememberSaveable { mutableStateOf(false) }
    fun openAppNotificationSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.notificationsGranted(granted)
        if (!granted && Build.VERSION.SDK_INT >= 33) {
            val canAskAgain = hostActivity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
            } ?: false
            if (!canAskAgain) openAppNotificationSettings()
        }
    }
    val errorLogExportPicker=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.exportErrorLog(if(result.resultCode==Activity.RESULT_OK) result.data?.data?.toString() else null)
    }
    fun importFiles() {
        val currentForm = viewModel.form.value
        val spec = com.jumlo.app.ui.workspace.ToolInputPolicy.resolve(currentForm.toolId, currentForm)
        picker.safeLaunch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (spec.mimeTypes.size == 1) spec.mimeTypes.first() else "*/*"
            if (spec.mimeTypes.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, spec.mimeTypes.toTypedArray())
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, spec.allowMultiple)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        })
    }
    fun importLibraryFiles(targetFolderId:String? = null) {
        pendingImportFolderId = targetFolderId
        libraryPicker.safeLaunch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE);type="*/*";putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) })
    }
    fun pickFolder(initial:String?=null) {
        folderPicker.safeLaunch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);if(initial!=null) putExtra(DocumentsContract.EXTRA_INITIAL_URI,Uri.parse(initial)) })
    }
    fun pickDownloadDestination() {
        downloadFolderPicker.safeLaunch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
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
            require(content.scheme.equals("content", ignoreCase = true))
            val intent=if(share) Intent(Intent.ACTION_SEND).apply { type=mime ?: "*/*";putExtra(Intent.EXTRA_STREAM,content) }
                else Intent(Intent.ACTION_VIEW).setDataAndType(content,mime ?: "application/octet-stream")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.clipData=ClipData.newUri(context.contentResolver,"document",content)
            val target=if(share) Intent.createChooser(intent,null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) else intent
            context.startActivity(target)
        } catch(_:Exception) { viewModel.notifyMessage("NO_COMPATIBLE_APP_OR_ACCESS_LOST") }
    }
    fun openOutput(output: OutputRecord) {
        if (DocumentViewerFormats.supportsInAppViewer(output.mimeType, output.displayName)) {
            navController.navigate(ViewerRoute(output.uri, output.displayName, mimeType = output.mimeType)) { launchSingleTop = true }
        } else launchExternal(output.uri, output.mimeType, false)
    }
    fun exportOutput(output:OutputRecord) {
        viewModel.prepareExport(output)
        exportPicker.safeLaunch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE);type=output.mimeType;putExtra(Intent.EXTRA_TITLE,output.displayName) })
    }
    fun openTool(tool:UtilityTool) {
        if(!CapabilityRegistry.visible(tool.id) || !CapabilityRegistry.enabled(tool.id)) {
            viewModel.notifyMessage("CAPABILITY_UNAVAILABLE");return
        }
        when(tool.id) {
            "scan_doc" -> navigateTo("scan")
            "scan_id_card" -> {
                val origin = currentToolOrigin() ?: tab.route
                viewModel.recordToolOpen("scan_id_card")
                if (tab != DockTab.TOOLS) switchTab(DockTab.TOOLS)
                navController.navigate(ScanRoute(origin, initialMode = "id_card")) {
                    popUpTo(ToolsRoute)
                    launchSingleTop = true
                }
            }
            "dl_settings" -> {viewModel.recordToolOpen(tool.id);navigateTo("settings")}
            "file_02" -> {viewModel.recordToolOpen(tool.id);navigateTo("files_overview")}
            else -> if(viewModel.chooseTool(tool.id)) navigateTo("workspace")
        }
    }

    @Composable fun Shell(shellRoute: String, jobId: String? = null, viewer: ViewerRoute? = null, entryId:String?=null, transitionScope:AnimatedVisibilityScope?=null, entryToolId:String?=null, transitionOrigin:String="tools", scanInitialMode:String?=null) {
        var scanPublishing by remember(entryId) { mutableStateOf(false) }
        var scannedOutputIds by rememberSaveable(entryId) { mutableStateOf<List<String>>(emptyList()) }
        var scanRunEpoch by rememberSaveable(entryId) { mutableIntStateOf(0) }
        val backOwnerKey = entryId ?: shellRoute
        val onBackIntercept: (Boolean) -> Unit = remember(backOwnerKey) {
            { owned ->
                if (owned) childBackOwners[backOwnerKey] = true else childBackOwners.remove(backOwnerKey)
            }
        }
        DisposableEffect(backOwnerKey) {
            onDispose { childBackOwners.remove(backOwnerKey) }
        }
        val root = shellRoute in ROOT_ROUTES
        val showDock = root
        val headerToolId=entryToolId ?: formToolId
        val headerTool=remember(shellRoute, headerToolId) {
            if (shellRoute == "workspace") CapabilityRegistry.tools.firstOrNull { it.id == headerToolId } else null
        }
        CompositionLocalProvider(LocalToolTransitionVisibility provides transitionScope,
            LocalToolTransitionOrigin provides if(root) shellRoute else transitionOrigin) {
        com.jumlo.app.ui.adaptive.HingeSafeViewport {
        Scaffold(
            snackbarHost={SnackbarHost(snackbar, modifier=Modifier.padding(bottom=if(showDock) 84.dp else 0.dp))},
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
                        titleContent=if(shellRoute=="home") {{ JumloWordmark() }} else null,
                        onSettingsClick={navigateTab(DockTab.SETTINGS)},showSettings=false
                    ) else if(shellRoute!="viewer" && shellRoute!="scan") QuietWorkspaceTopBar(
                        title=when(shellRoute) {
                            "workspace" -> headerTool?.let {title(it,settings.language)} ?: stringResource(R.string.workspace_title)
                            "activity" -> stringResource(R.string.activity_title)
                            "downloads" -> if(settings.language.isRtl) DockTab.DOWNLOADS.labelAr else DockTab.DOWNLOADS.labelEn
                            "scan" -> stringResource(R.string.scan_title)
                            "print" -> stringResource(R.string.upgrade_print)
                            "storage" -> stringResource(R.string.modern_storage_usage)
                            "viewer" -> viewer?.title.orEmpty()
                            else -> stringResource(R.string.settings_title)
                        },
                        // Every tool screen shows its benefit subtitle and its own
                        // pillar family color; previously the subtitle was dropped
                        // and every tool inherited the PDF pillar colors.
                        subtitle=if(shellRoute=="workspace") headerTool?.let {subtitle(it,settings.language).takeIf { s -> s.isNotBlank() }} else null,
                        pillar=if(shellRoute=="workspace") headerTool?.pillar ?: com.jumlo.app.model.PillarCategory.PDF else com.jumlo.app.model.PillarCategory.PDF,
                        onBack=::goBack,
                        toolId=when(shellRoute) { "workspace" -> headerToolId; "scan" -> "scan_doc"; else -> null }
                    )
                    DropFilesTarget(enabled=shellRoute in DROP_TARGET_ROUTES,
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
                                 {navigateTab(DockTab.DOWNLOADS)},
                                 onScanIdCard = { viewModel.recordToolOpen("scan_id_card"); navController.navigate(ScanRoute("home", initialMode = "id_card")) { launchSingleTop = true } },
                                 listState = homeListState,
                                 resetToRootEpoch = homeResetEpoch)
                            "tools" -> com.jumlo.app.ui.tools.ToolsScreen(viewModel,::openTool, gridState = toolsGridState, resetToRootEpoch = toolsResetEpoch)
                            "downloads" -> com.jumlo.app.ui.downloads.DownloadsScreen(viewModel,::openTool,{selectedJob=it;navigateTo("activity")})
                            "files" -> com.jumlo.app.ui.files.FilesScreen(viewModel,
                                {importLibraryFiles(null)}, {pickFolder()}, ::openSavedFolder,
                                {openOutput(it)},
                                {launchExternal(it.uri,it.mimeType,true)}, ::exportOutput,
                                {record->viewModel.prepareOutputWorkflow(OutputWorkflowPolicy.reuseTool(record.mimeType),listOf(record)) {navigateTo("workspace")} },
                                {sourceId->viewModel.prepareSourceWorkflow(sourceId) {navigateTo("workspace")} },
                                {pickFolder(it.uri)}, {entry ->
                                    if (DocumentViewerFormats.supportsInAppViewer(entry.mimeType, entry.name)) {
                                        navController.navigate(ViewerRoute(entry.uri, entry.name, mimeType = entry.mimeType)) { launchSingleTop = true }
                                    } else launchExternal(entry.uri, entry.mimeType, false)
                                },
                                {entry->viewModel.importProviderEntryToWorkspace(entry.uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) {navigateTo("workspace")} },
                                {viewModel.copyOrMoveFile(it,false,CollisionPolicy.KEEP_BOTH)},
                                {renameEntry=it;renameText=it.name}, {deleteEntry=it}, {newFolder=true;folderName=""},
                                {chosen,toolId->viewModel.prepareOutputWorkflow(toolId,chosen) {navigateTo("workspace")} },
                                {chosen,toolId->viewModel.prepareSourceBatchWorkflow(toolId,chosen) {navigateTo("workspace")} },
                                onPrint=viewModel::printOutput,onPrintSource={viewModel.printSource(it)},
                                onChangeSaveFolder=::pickDownloadDestination,onStorage={navigateTo("storage")},onImportToFolder={importLibraryFiles(it)}, onScan = { navigateTo("scan") }, initialFilter = filesInitialFilter, resetToRootEpoch = filesResetEpoch, listState = filesListState, onBackInterceptChange = onBackIntercept)
                            "print" -> com.jumlo.app.printing.PrintScreen(
                                source=printSource,onBack=::goBack,onPickDocument=::importPrintDocument,
                                modifier=Modifier.padding(bottom=QuietTheme.spacing.listBottomBuffer))
                            "settings", "settings_detail" -> com.jumlo.app.ui.settings.SettingsScreen(viewModel,
                                onRequestNotifications={
                                    val hasPostPermission = Build.VERSION.SDK_INT < 33 ||
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                                    if (Build.VERSION.SDK_INT >= 33 && !hasPostPermission) {
                                        val permanentlyDenied = askedNotificationPermission && hostActivity?.let {
                                            !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
                                        } == true
                                        if (permanentlyDenied) {
                                            openAppNotificationSettings()
                                        } else {
                                            askedNotificationPermission = true
                                            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    } else {
                                        openAppNotificationSettings()
                                    }
                                },
                                onExportLogs={viewModel.prepareErrorLogExport();errorLogExportPicker.safeLaunch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {addCategory(Intent.CATEGORY_OPENABLE);type="text/plain";putExtra(Intent.EXTRA_TITLE,"jumlo-errors.txt")})},
                                onChooseDownloadFolder={pickDownloadDestination()},onOpenSavedFolder={openSavedFolder()},
                                onShowGuide={showGuide=true},onOpenStorage={navigateTo("storage")},
                                listState = settingsListState, resetToRootEpoch = settingsResetEpoch)
                            "storage" -> com.jumlo.app.ui.settings.StorageScreen(viewModel,::pickDownloadDestination,::openSavedFolder,onOpenCategory={ category ->
                                when (category.lowercase()) {
                                    "downloads" -> switchTab(DockTab.DOWNLOADS)
                                    "cache" -> viewModel.cleanTemporary()
                                    else -> {
                                        filesInitialFilter = category
                                        if (!navController.popBackStack<FilesRoute>(inclusive = false)) {
                                            navController.popBackStack()
                                            switchTab(DockTab.FILES)
                                        }
                                    }
                                }
                            })
                            "viewer" -> viewer?.let {
                                val viewerKind = DocumentViewerFormats.detect(it.mimeType, it.title)
                                if (viewerKind == null || viewerKind == DocumentViewerKind.PDF) {
                                    com.jumlo.app.ui.viewer.PdfViewerScreen(
                                        uri = it.uri,
                                        title = it.title,
                                        onBack = ::goBack,
                                        onOpenFile = { if (!navController.popBackStack<FilesRoute>(inclusive = false)) { navController.popBackStack(); switchTab(DockTab.FILES) } },
                                        onPrint = { u, _ -> viewModel.importForPrint(Intake(listOf(u), Intent.FLAG_GRANT_READ_URI_PERMISSION, null)) },
                                        onSaveMarkupResult = { u, t, items, done -> viewModel.flattenPdfMarkup(u, t, items, onResult = done) },
                                        onSaveInPlaceResult = { u, t, items, done -> viewModel.flattenPdfMarkupInPlace(u, t, items, onResult = done) },
                                        onOpenSavedOutput = { savedUri, savedTitle ->
                                            navController.navigate(ViewerRoute(savedUri, savedTitle)) { launchSingleTop = true }
                                        },
                                        onBackInterceptChange = onBackIntercept
                                    )
                                } else {
                                    OfficeDocumentViewerScreen(
                                        uri = it.uri,
                                        title = it.title,
                                        mimeType = it.mimeType,
                                        onBack = ::goBack,
                                        onShare = { launchExternal(it.uri, it.mimeType, true) },
                                        onConvertToPdf = if (viewerKind != DocumentViewerKind.TEXT) {
                                            {
                                                viewModel.importProviderEntryToWorkspace(it.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION, targetTool = "office_to_pdf") {
                                                    navigateTo("workspace")
                                                }
                                            }
                                        } else null,
                                        onOpenExternal = { launchExternal(it.uri, it.mimeType, false) },
                                        onBackInterceptChange = onBackIntercept
                                    )
                                }
                            }
                            "scan" -> {
                                val allOutputs by viewModel.outputs.collectAsStateWithLifecycle()
                                val scannedOutputs = remember(scannedOutputIds, allOutputs) {
                                    if (scannedOutputIds.isEmpty()) emptyList()
                                    else {
                                        val byId = allOutputs.associateBy { it.id }
                                        scannedOutputIds.mapNotNull(byId::get)
                                    }
                                }
                                if (scannedOutputIds.isNotEmpty()) {
                                    Column(Modifier.fillMaxSize()) {
                                        QuietWorkspaceTopBar(
                                            title = stringResource(R.string.scan_title),
                                            onBack = ::goBack,
                                            toolId = "scan_doc"
                                        )
                                        if (scannedOutputs.isEmpty()) {
                                            Box(
                                                Modifier.fillMaxWidth().weight(1f).padding(20.dp),
                                                contentAlignment = androidx.compose.ui.Alignment.Center
                                            ) {
                                                CircularProgressIndicator()
                                            }
                                        } else Column(
                                            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp, vertical = 16.dp).navigationBarsPadding(),
                                            verticalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            val primary = scannedOutputs.first()
                                            com.jumlo.app.ui.downloads.CompletedResultCard(
                                                outputs = scannedOutputs,
                                                onOpen = {
                                                    scannedOutputIds = emptyList()
                                                    if (primary.mimeType.equals("application/pdf", ignoreCase = true)) {
                                                        navController.navigate(ViewerRoute(primary.uri, primary.displayName, returnTab = transitionOrigin)) {
                                                            popUpTo<ScanRoute> { inclusive = true }
                                                            launchSingleTop = true
                                                        }
                                                    } else openOutput(primary)
                                                },
                                                onShare = { launchExternal(primary.uri, primary.mimeType, true) },
                                                onNewRun = {
                                                    scannedOutputIds = emptyList()
                                                    scanRunEpoch++
                                                },
                                                newRunLabel = stringResource(R.string.t34_scan_again)
                                            )
                                            if (scannedOutputs.size == 1 && primary.mimeType.equals("application/pdf", ignoreCase = true)) {
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    QuietTonalButton(
                                                        stringResource(R.string.scan_next_ocr),
                                                        onClick = {
                                                            viewModel.prepareOutputWorkflow("pdf_ocr", listOf(primary)) {
                                                                scannedOutputIds = emptyList()
                                                                navController.popBackStack()
                                                                navigateTo("workspace")
                                                            }
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    QuietTonalButton(
                                                        stringResource(R.string.scan_next_compress),
                                                        onClick = {
                                                            viewModel.prepareOutputWorkflow("pdf_compress", listOf(primary)) {
                                                                scannedOutputIds = emptyList()
                                                                navController.popBackStack()
                                                                navigateTo("workspace")
                                                            }
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    QuietTonalButton(
                                                        stringResource(R.string.scan_next_redact),
                                                        onClick = {
                                                            viewModel.prepareOutputWorkflow("pdf_privacy_redact", listOf(primary)) {
                                                                scannedOutputIds = emptyList()
                                                                navController.popBackStack()
                                                                navigateTo("workspace")
                                                            }
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else key(scanRunEpoch) {
                                    com.jumlo.app.ui.scan.ScanScreen(
                                        externalBusy = scanPublishing,
                                        onScannedJpegs = { files ->
                                            scanPublishing = true
                                            viewModel.publishScannedImages(files, onFinished = { scanPublishing = false }) { outputs ->
                                                if (navController.currentBackStackEntry?.id == entryId &&
                                                    navController.currentDestination?.hasRoute<ScanRoute>() == true &&
                                                    outputs.isNotEmpty()
                                                ) {
                                                    scannedOutputIds = outputs.map { it.id }
                                                }
                                            }
                                        },
                                        onScannedPdf = { file ->
                                            scanPublishing = true
                                            viewModel.publishScannedPdf(file, onFinished = { scanPublishing = false }) { output ->
                                                if (navController.currentBackStackEntry?.id == entryId &&
                                                    navController.currentDestination?.hasRoute<ScanRoute>() == true
                                                ) {
                                                    scannedOutputIds = listOf(output.id)
                                                }
                                            }
                                        },
                                        onBack = ::goBack,
                                        initialMode = scanInitialMode,
                                        onBackInterceptChange = onBackIntercept
                                    )
                                }
                            }
                            "workspace" -> {
                                val form by viewModel.form.collectAsStateWithLifecycle()
                                val imported by viewModel.imported.collectAsStateWithLifecycle()
                                val busy by viewModel.busy.collectAsStateWithLifecycle()
                                val urls by viewModel.urls.collectAsStateWithLifecycle()
                                if(!CapabilityRegistry.visible(form.toolId) || !CapabilityRegistry.enabled(form.toolId)) QuietStateCard(
                                    title=stringResource(R.string.ui_tool_unavailable),
                                    subtitle=stringResource(R.string.ui_unavailable_body),
                                    actionLabel=stringResource(R.string.ui_choose_tool),
                                    onAction={goBack()},modifier=Modifier.padding(20.dp)
                                ) else if(form.toolId in MEDIA_WORKSPACE_TOOLS) com.jumlo.app.ui.downloads.MediaWorkspace(urls,sessionEpoch,{openCompletedJob(it)},{viewModel.chooseTool("dl_02");viewModel.setUrls(it);navigateTo("workspace")},defaultVideoQuality=settings.defaultVideoQuality,defaultAudioQuality=settings.defaultAudioQuality,onEditedUrl=viewModel::setUrls)
                                else com.jumlo.app.ui.workspace.WorkspaceScreen(viewModel,form,imported,busy,urls,reduceMotion,::importFiles,{photos.safeLaunch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},onPrintPages={sourceId,pages->viewModel.printSource(sourceId,pages)},onBack=::goBack) {viewModel.submit {openCompletedJob(it)}}
                            }
                            "activity" -> com.jumlo.app.ui.downloads.ActivityScreen(viewModel,jobId,{openOutput(it)},{launchExternal(it.uri,it.mimeType,true)},::exportOutput,{record->viewModel.prepareOutputWorkflow(OutputWorkflowPolicy.reuseTool(record.mimeType),listOf(record)) {navigateTo("workspace")} },{
                                val targetToolId = jobId?.let { id -> viewModel.jobs.value.firstOrNull { it.request.id == id }?.request?.toolId } ?: viewModel.lastToolId.value
                                selectedJob = null
                                viewModel.allowNewRun()
                                when {
                                    targetToolId == "scan_doc" -> navigateTo("scan")
                                    targetToolId == "scan_id_card" -> {
                                        val origin = currentToolOrigin() ?: tab.route
                                        viewModel.recordToolOpen("scan_id_card")
                                        if (tab != DockTab.TOOLS) switchTab(DockTab.TOOLS)
                                        navController.navigate(ScanRoute(origin, initialMode = "id_card")) {
                                            popUpTo(ToolsRoute)
                                            launchSingleTop = true
                                        }
                                    }
                                    targetToolId != null && viewModel.chooseTool(targetToolId) -> navigateTo("workspace")
                                    else -> navigateTo("tools")
                                }
                            },onShowAll=if(jobId!=null) ({selectedJob=null;navController.navigate(ActivityRoute) { launchSingleTop=true }}) else null,onPrint=viewModel::printOutput)
                        }


                    }
                }
                if (showDock) {
                    JumloNavigationBar(
                        selectedTab = tab,
                        language = settings.language,
                        onSelectTab = ::navigateTab,
                        modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                    )
                }
            }
        }
        }
    }

    }
    SharedTransitionLayout(Modifier.fillMaxSize()) {
    CompositionLocalProvider(LocalToolSharedTransitionScope provides this) {
    Box(Modifier.fillMaxSize()) {
        fun isRootTabDestination(dest: androidx.navigation.NavDestination?): Boolean =
            dest?.hasRoute<HomeRoute>() == true ||
                dest?.hasRoute<ToolsRoute>() == true ||
                dest?.hasRoute<FilesRoute>() == true ||
                dest?.hasRoute<SettingsRoute>() == true
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                val isTabSwitch = isRootTabDestination(initialState.destination) && isRootTabDestination(targetState.destination)
                when {
                    isTabSwitch -> JumloMotion.tabSwitchEnter(reduceMotion)
                    targetState.destination.hasRoute<PrintRoute>() -> JumloMotion.modalEnter(reduceMotion)
                    else -> JumloMotion.screenPushEnter(reduceMotion, rtl)
                }
            },
            exitTransition = {
                val isTabSwitch = isRootTabDestination(initialState.destination) && isRootTabDestination(targetState.destination)
                when {
                    isTabSwitch -> JumloMotion.tabSwitchExit(reduceMotion)
                    initialState.destination.hasRoute<PrintRoute>() -> JumloMotion.modalExit(reduceMotion)
                    else -> JumloMotion.screenPushExit(reduceMotion, rtl)
                }
            },
            popEnterTransition = {
                val isTabSwitch = isRootTabDestination(initialState.destination) && isRootTabDestination(targetState.destination)
                when {
                    isTabSwitch -> JumloMotion.tabSwitchEnter(reduceMotion)
                    targetState.destination.hasRoute<PrintRoute>() -> JumloMotion.modalEnter(reduceMotion)
                    else -> JumloMotion.screenPopEnter(reduceMotion, rtl)
                }
            },
            popExitTransition = {
                val isTabSwitch = isRootTabDestination(initialState.destination) && isRootTabDestination(targetState.destination)
                when {
                    isTabSwitch -> JumloMotion.tabSwitchExit(reduceMotion)
                    initialState.destination.hasRoute<PrintRoute>() -> JumloMotion.modalExit(reduceMotion)
                    else -> JumloMotion.screenPopExit(reduceMotion, rtl)
                }
            }
        ) {
            composable<HomeRoute> { Shell("home",transitionScope=this) }
            composable<ToolsRoute> { Shell("tools",transitionScope=this) }
            composable<DownloadsRoute> { Shell("downloads",transitionScope=this) }
            composable<FilesRoute> { entry -> Shell("files",entryId=entry.id,transitionScope=this) }
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
            composable<ViewerRoute> { entry -> Shell("viewer",viewer=entry.toRoute<ViewerRoute>(),entryId=entry.id,transitionScope=this) }
            composable<JobRoute> { entry -> Shell("activity",jobId=entry.toRoute<JobRoute>().jobId,transitionScope=this) }
            composable<ScanRoute> { entry ->
                val args = entry.toRoute<ScanRoute>()
                Shell("scan",entryId=entry.id,transitionScope=this,
                    transitionOrigin=args.transitionOrigin, scanInitialMode=args.initialMode)
            }
            composable<WorkspaceRoute>(deepLinks = listOf(navDeepLink<WorkspaceRoute>(basePath = "jumlo://workspace"))) { Shell("workspace",transitionScope=this) }
            composable<ActivityRoute> { Shell("activity",transitionScope=this) }
        }
        // NavHost registers its own back callback. Register the workspace-aware
        // handler *after* NavHost in composition order so Android Back reaches
        // the unsaved-changes guard before Navigation can pop the tool.
        // Keep this active for both legacy button Back and predictive gestures.
        PredictiveBackHandler(enabled = !childBackOwned && (route !in ROOT_ROUTES || tabHistory.isNotEmpty())) { progress ->
            try {
                progress.collect { backSwipe = it.progress.coerceIn(0f, 1f) }
                goBack()
            } finally { backSwipe = 0f }
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
            text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(entry.name,style=QuietTheme.typography.bodyMedium.copy(fontWeight=FontWeight.SemiBold,textDirection=TextDirection.Content))
                Text(stringResource(R.string.dialog_delete_body))
            }},
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
            confirmButton={TextButton(enabled=folderName.isNotBlank(),onClick={newFolder=false;viewModel.createFolder(folderName.trim())}) {Text(stringResource(R.string.action_create))}},
            dismissButton={TextButton(onClick={newFolder=false}) {Text(stringResource(R.string.action_cancel))}}
        )
    }
    if(confirmLeave) {
      val busy by viewModel.busy.collectAsStateWithLifecycle()
      AlertDialog(
        onDismissRequest={confirmLeave=false},
        title={Text(stringResource(R.string.ui_unsaved_title))},
        text={Text(stringResource(R.string.ui_unsaved_body))},
        confirmButton={TextButton(enabled=!busy,onClick={viewModel.saveWorkspaceDraft { confirmLeave=false;leaveWorkspace(preserveDraft=true) }}) {Text(stringResource(R.string.ui_save_leave))}},
        dismissButton={Column {
            TextButton(onClick={confirmLeave=false}) {Text(stringResource(R.string.ui_keep_editing))}
            TextButton(enabled=!busy,onClick={viewModel.discardWorkspaceChanges { confirmLeave=false;leaveWorkspace() }}) {Text(stringResource(R.string.ui_discard_leave))}
        }}
    )
    }
}
