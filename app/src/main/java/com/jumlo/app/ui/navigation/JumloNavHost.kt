package com.jumlo.app.ui.navigation

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
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
    val message by viewModel.message.collectAsStateWithLifecycle()
    val undoTrashIds by viewModel.undoTrashIds.collectAsStateWithLifecycle()
    val sessionEpoch by viewModel.sessionEpoch.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val appHaptics = rememberAppHaptics(settings.hapticFeedbackOnCommit)
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
    LaunchedEffect(navController, viewModel) {
        viewModel.sharedLinkOpenRequests.collect {
            navController.navigate(WorkspaceRoute) { launchSingleTop = true }
        }
    }
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
    var lastTab by rememberSaveable { mutableStateOf(DockTab.HOME) }
    val tab = DockTab.entries.firstOrNull { it.route==route } ?: lastTab
    var selectedJob by rememberSaveable { mutableStateOf<String?>(null) }
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
        lastTab = next
        if(route=="workspace") viewModel.closeToolSession()
        if(route=="activity") selectedJob = null
        navController.navigate(destinationFor(next.route)) {
            popUpTo(HomeRoute) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    fun leaveWorkspace() {
        appHaptics.navigationTab()
        if(route=="workspace") viewModel.closeToolSession()
        if(route=="activity") selectedJob = null
        if (!navController.popBackStack()) navigateTab(tab) 
    }
    val dirty by viewModel.hasUnsavedWorkspace.collectAsStateWithLifecycle()
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    fun goBack() { if(route=="workspace" && dirty) confirmLeave=true else leaveWorkspace() }
    var backSwipe by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = route == "workspace") { progress ->
        try {
            progress.collect { backSwipe=it.progress.coerceIn(0f,1f) }
            if(dirty) confirmLeave=true else leaveWorkspace()
        } finally { backSwipe=0f }
    }

    var deleteEntry by remember { mutableStateOf<DocumentEntry?>(null) }
    var renameEntry by remember { mutableStateOf<DocumentEntry?>(null) }
    var renameText by remember { mutableStateOf("") }
    var newFolder by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var showAccountHub by rememberSaveable { mutableStateOf(false) }
    val snackbar=remember { SnackbarHostState() }
    val undoLabel=stringResource(R.string.action_undo)
    LaunchedEffect(message, undoTrashIds) {
        val currentMsg=message ?: return@LaunchedEffect
        val targetUndo=undoTrashIds
        val result=snackbar.showSnackbar(
            message=context.getString(userMessageResource(currentMsg)),
            actionLabel=if(targetUndo.isNotEmpty()) undoLabel else null,
            duration=SnackbarDuration.Short
        )
        if(result==SnackbarResult.ActionPerformed && targetUndo.isNotEmpty()) {
            viewModel.restoreOutputs(targetUndo)
        } else {
            viewModel.dismissMessage()
        }
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode==Activity.RESULT_OK) viewModel.intake(IntakeController.collect(result.data), attachToWorkspace=true)
    }
    val libraryPicker=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode==Activity.RESULT_OK) viewModel.intake(IntakeController.collect(result.data), attachToWorkspace=false)
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
        val mime=when(form.toolId) { "pdf_02","img_01","img_02","img_03","img_04"->"image/*";"pdf_01","pdf_03","pdf_07"->"application/pdf";"file_08"->"application/zip";else->"*/*" }
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
            "dl_settings" -> navigateTo("settings")
            "file_02", "file_trash" -> navigateTo("files_overview")
            else -> if(viewModel.chooseTool(tool.id)) navigateTo("workspace")
        }
    }

    @Composable fun Shell(shellRoute: String) {
        val root = shellRoute in setOf("home","tools_catalog","downloads","files_overview","settings")
        val swipeDistance=with(LocalDensity.current) { 54.dp.toPx() }
        com.jumlo.app.ui.adaptive.HingeSafeViewport {
        Scaffold(
            snackbarHost={SnackbarHost(snackbar,modifier=Modifier.padding(bottom=if(root) 96.dp else 0.dp))},
            contentWindowInsets=WindowInsets(0,0,0,0)
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).graphicsLayer {
                if(shellRoute=="workspace" && !reduceMotion) {
                    translationX=(if(rtl) -1f else 1f)*backSwipe*swipeDistance
                    scaleX=1f-.035f*backSwipe
                    scaleY=1f-.035f*backSwipe
                }
            }) {
                Column(Modifier.fillMaxSize()) {
                    if(root) QuietRootTopBar(
                        title=stringResource(when(shellRoute) { "home"->R.string.app_name;"tools_catalog"->R.string.tools_catalog_title;"downloads"->R.string.nav_downloads;"settings"->R.string.settings_title;else->R.string.nav_files }),
                        onSearchClick=if(shellRoute=="home") ({navigateTab(DockTab.TOOLS)}) else null,
                        onSettingsClick={navigateTo("settings")},onAccountClick={showAccountHub=true}
                    ) else QuietWorkspaceTopBar(
                        title=when(shellRoute) {"workspace"->CapabilityRegistry.tools.find {it.id==form.toolId}?.let {title(it,settings.language)} ?: stringResource(R.string.workspace_title);"activity"->stringResource(R.string.activity_title);"scan"->stringResource(R.string.scan_title);else->stringResource(R.string.settings_title)},
                        onBack=::goBack
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when(shellRoute) {
                            "home" -> com.jumlo.app.ui.home.HomeScreen(viewModel,::openTool,{navigateTab(DockTab.TOOLS)},{selectedJob=it;navigateTo("activity")},{launchExternal(it.uri,it.mimeType,false)},{navigateTo("workspace")})
                            "tools_catalog" -> com.jumlo.app.ui.tools.ToolsScreen(viewModel,::openTool)
                            "downloads" -> com.jumlo.app.ui.downloads.DownloadsScreen(viewModel,::openTool,{selectedJob=it;navigateTo("activity")})
                            "files_overview" -> com.jumlo.app.ui.files.FilesScreen(viewModel,
                                ::importLibraryFiles, {pickFolder()}, ::openSavedFolder,
                                {launchExternal(it.uri,it.mimeType,false)},
                                {launchExternal(it.uri,it.mimeType,true)}, ::exportOutput,
                                {record->viewModel.prepareOutputWorkflow(OutputWorkflowPolicy.reuseTool(record.mimeType),listOf(record)) {navigateTo("workspace")} },
                                {sourceId->viewModel.prepareSourceWorkflow(sourceId) {navigateTo("workspace")} },
                                {pickFolder(it.uri)}, {launchExternal(it.uri,it.mimeType,false)},
                                {entry->viewModel.importProviderEntryToWorkspace(entry.uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) {navigateTo("workspace")} },
                                {viewModel.copyOrMoveFile(it,false,CollisionPolicy.KEEP_BOTH)},
                                {renameEntry=it;renameText=it.name}, {deleteEntry=it}, {newFolder=true;folderName=""},
                                {chosen,toolId->viewModel.prepareOutputWorkflow(toolId,chosen) {navigateTo("workspace")} },
                                {chosen,toolId->viewModel.prepareSourceBatchWorkflow(toolId,chosen) {navigateTo("workspace")} })
                            "settings" -> com.jumlo.app.ui.settings.SettingsScreen(viewModel,
                                onRequestNotifications={if(Build.VERSION.SDK_INT>=33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)},
                                onExportLogs={viewModel.prepareErrorLogExport();errorLogExportPicker.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {addCategory(Intent.CATEGORY_OPENABLE);type="text/plain";putExtra(Intent.EXTRA_TITLE,"jumlo-errors.txt")})},
                                onOpenFiles={navigateTab(DockTab.FILES)},onChooseFolder={pickFolder()},
                                onChooseDownloadFolder={pickDownloadDestination()},onOpenSavedFolder={openSavedFolder()})
                            "scan" -> com.jumlo.app.ui.scan.ScanScreen(onCaptured={file->viewModel.chooseTool("pdf_02");viewModel.intakeLocalFile(file,"scan.jpg","image/jpeg");navigateTo("workspace")},onBack=::goBack)
                            "workspace" -> {
                                val imported by viewModel.imported.collectAsStateWithLifecycle()
                                val busy by viewModel.busy.collectAsStateWithLifecycle()
                                val urls by viewModel.urls.collectAsStateWithLifecycle()
                                if(!CapabilityRegistry.visible(form.toolId) || !CapabilityRegistry.enabled(form.toolId)) QuietStateCard(
                                    title=stringResource(R.string.ui_tool_unavailable),
                                    subtitle=stringResource(R.string.ui_unavailable_body),
                                    actionLabel=stringResource(R.string.ui_choose_tool),
                                    onAction={goBack()},modifier=Modifier.padding(20.dp)
                                ) else if(form.toolId in setOf("dl_media","dl_02")) com.jumlo.app.ui.downloads.MediaWorkspace(urls,sessionEpoch,{selectedJob=it;navigateTo("activity")},{viewModel.chooseTool("dl_02");viewModel.setUrls(it);navigateTo("workspace")},defaultWifiOnly=settings.wifiOnlyDefault)
                                else com.jumlo.app.ui.workspace.WorkspaceScreen(viewModel,form,imported,busy,urls,reduceMotion,::importFiles,{photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},::launchGoogleSignIn) {viewModel.submit {selectedJob=it;navigateTo("activity")}}
                            }
                            "activity" -> com.jumlo.app.ui.downloads.ActivityScreen(viewModel,selectedJob,{launchExternal(it.uri,it.mimeType,false)},{launchExternal(it.uri,it.mimeType,true)},::exportOutput,{record->viewModel.prepareOutputWorkflow(OutputWorkflowPolicy.reuseTool(record.mimeType),listOf(record)) {navigateTo("workspace")} },{selectedJob=null;viewModel.allowNewRun();navigateTo("workspace")},onShowAll=if(selectedJob!=null) ({selectedJob=null}) else null)
                        }
                        if(root) QuietFloatingDock(
                            selectedTab=tab,
                            language=settings.language,
                            onSelectTab=::navigateTab,
                            modifier=Modifier.align(Alignment.BottomCenter)
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
            enterTransition = {
                fadeIn(spring(dampingRatio=1f,stiffness=420f)) +
                    (if(reduceMotion) EnterTransition.None else slideInHorizontally(spring(dampingRatio=.88f,stiffness=440f)) {
                        (if(rtl) -1 else 1) * it / 7
                    })
            },
            exitTransition = {
                fadeOut(spring(dampingRatio=1f,stiffness=440f)) +
                    (if(reduceMotion) ExitTransition.None else slideOutHorizontally(spring(dampingRatio=.88f,stiffness=440f)) {
                        (if(rtl) 1 else -1) * it / 10
                    })
            },
            popEnterTransition = {
                fadeIn(spring(dampingRatio=1f,stiffness=420f)) +
                    (if(reduceMotion) EnterTransition.None else slideInHorizontally(spring(dampingRatio=.88f,stiffness=440f)) {
                        (if(rtl) 1 else -1) * it / 7
                    })
            },
            popExitTransition = {
                fadeOut(spring(dampingRatio=1f,stiffness=440f)) +
                    (if(reduceMotion) ExitTransition.None else slideOutHorizontally(spring(dampingRatio=.88f,stiffness=440f)) {
                        (if(rtl) -1 else 1) * it / 10
                    })
            }
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
        val listing by viewModel.tree.collectAsStateWithLifecycle()
        com.jumlo.app.ui.files.ProviderRenameDialog(entry.uri,renameText,listing,
            {renameText=it},{renameEntry=null},{name->renameEntry=null;viewModel.renameFile(entry,name)})
    }
    if(newFolder) {
        AlertDialog(
            onDismissRequest={newFolder=false},
            title={Text(stringResource(R.string.files_new_folder))},
            text={OutlinedTextField(folderName,{folderName=it},singleLine=true,label={Text(stringResource(R.string.files_new_folder))})},
            confirmButton={TextButton(onClick={newFolder=false;viewModel.createFolder(folderName)}) {Text(stringResource(R.string.action_create))}},
            dismissButton={TextButton(onClick={newFolder=false}) {Text(stringResource(R.string.action_cancel))}}
        )
    }
    if (showAccountHub) {
        val outputs by viewModel.outputs.collectAsStateWithLifecycle()
        val storage by viewModel.storage.collectAsStateWithLifecycle()
        AccountHubModal(
            settings=settings, storage=storage, outputsCount=outputs.size,
            onDismiss={showAccountHub=false},
            onOpenSettings={showAccountHub=false;navigateTo("settings")},
            onOpenFiles={showAccountHub=false;navigateTab(DockTab.FILES)}
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
