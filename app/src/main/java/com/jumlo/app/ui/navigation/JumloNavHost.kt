package com.jumlo.app.ui.navigation

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import com.jumlo.app.ui.theme.QuietTheme
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jumlo.app.BuildConfig
import com.jumlo.app.R
import com.jumlo.app.data.files.DocumentEntry
import com.jumlo.app.data.files.DocumentListing
import com.jumlo.app.data.jobs.CapabilityRegistry
import com.jumlo.app.data.jobs.stableError
import com.jumlo.app.model.*
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.PreviewFrame
import com.jumlo.app.ui.components.*
import com.jumlo.app.ui.intake.Intake
import com.jumlo.app.ui.intake.IntakeController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
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
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val urls by viewModel.urls.collectAsStateWithLifecycle()
    val tree by viewModel.tree.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    val context=LocalContext.current
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
    fun navigateTo(name: String) { navController.navigate(destinationFor(name)) { launchSingleTop = true } }
    fun navigateTab(next: DockTab) {
        tab = next
        navController.navigate(destinationFor(next.route)) {
            popUpTo(HomeRoute) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    fun goBack() { if (!navController.popBackStack()) navigateTab(tab) }
    var selectedJob by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var deleteEntry by remember { mutableStateOf<DocumentEntry?>(null) }
    var renameEntry by remember { mutableStateOf<DocumentEntry?>(null) }
    var renameText by remember { mutableStateOf("") }
    var newFolder by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    val snackbar=remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it.replace('_',' '));viewModel.dismissMessage() } }
    // Predictive/system back is handled by NavController (enableOnBackInvokedCallback=true).
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
        if(viewModel.chooseTool(tool.id)) navigateTo(if(tool.id=="file_02") "files_overview" else "workspace")
    }
    // Renders the scaffold UI for one logical (type-safe) destination.
    @Composable fun Shell(shellRoute: String) {
        val root = shellRoute in setOf("home","tools_catalog","downloads","files_overview")
    Scaffold(snackbarHost={ SnackbarHost(snackbar) },contentWindowInsets=WindowInsets(0,0,0,0)) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide=maxWidth>=840.dp
            Row(Modifier.fillMaxSize()) {
                if(wide && root) QuietExpandedNavigationRail(tab,settings.language,{ next -> navigateTab(next) })
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.fillMaxSize()) {
                        if(root) QuietRootTopBar(title=when(shellRoute) { "home"->stringResource(R.string.app_name);"tools_catalog"->stringResource(R.string.tools_catalog_title);"downloads"->stringResource(R.string.nav_downloads);else->stringResource(R.string.nav_files) },onSettingsClick={ navigateTo("settings") })
                        else QuietWorkspaceTopBar(title=when(shellRoute) { "workspace"->CapabilityRegistry.tools.find { it.id==form.toolId }?.let { title(it,settings.language) } ?: "Workspace";"activity"->"Activity & outputs";else->stringResource(R.string.settings_title) },onBack={ goBack() })
                        if(shellRoute=="scan") {
                            com.jumlo.app.ui.scan.ScanScreen(onCaptured={ file -> viewModel.intakeLocalFile(file,"scan.jpg","image/jpeg"); viewModel.chooseTool("pdf_02"); navigateTo("workspace") }, onBack={ goBack() })
                        } else if(shellRoute=="workspace" && form.toolId=="dl_media") {
                            com.jumlo.app.ui.downloads.MediaWorkspace(urls,{ selectedJob=it;navigateTo("activity") },{ value ->viewModel.chooseTool("dl_02");viewModel.setUrls(value);navigateTo("workspace") })
                        } else if(shellRoute=="workspace") {
                            WorkspaceScreen(viewModel,form,imported,busy,urls,settings.language,::importFiles,{photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}) { viewModel.submit { selectedJob=it;navigateTo("activity") } }
                        } else {
                            LazyColumn(Modifier.weight(1f).fillMaxWidth().widthIn(max=900.dp).align(Alignment.CenterHorizontally),contentPadding=PaddingValues(start=20.dp,end=20.dp,top=16.dp,bottom=if(root && !wide) 124.dp else 32.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                                if(shellRoute=="home" || shellRoute=="tools_catalog") {
                                    item { CandidateNotice() }
                                    if(shellRoute=="home") {
                                        item { QuietHero(title=stringResource(R.string.home_hero_title),subtitle=stringResource(R.string.home_hero_subtitle),icon=Icons.Rounded.Bolt) }
                                        item { QuietPrimaryButton(stringResource(R.string.import_device_files),::importFiles,Modifier.fillMaxWidth(),leadingIcon=Icons.Rounded.Add,enabled=!busy) }
                                        item { TextButton(onClick={ navigateTo("workspace") }) { Text("Resume saved workspace (${form.sourceIds.size} files)") } }
                                    }
                                    item { QuietSearchBar(query=search,placeholder=stringResource(R.string.home_search_placeholder),onQueryChange={ search=it },onClear={ search="" }) }
                                    val catalog=CapabilityRegistry.tools.filter { search.isBlank() || title(it,settings.language).contains(search,true) || it.id.contains(search,true) }
                                    for(pillar in listOf(PillarCategory.PDF,PillarCategory.IMAGES,PillarCategory.DOWNLOADS,PillarCategory.FILES,PillarCategory.VAULT)) {
                                        val tools=catalog.filter { it.pillar==pillar }
                                        if(tools.isNotEmpty()) item(key="heading-${pillar.name}") { QuietSectionTitle(if(settings.language.isRtl) pillar.labelAr else pillar.labelEn) }
                                        items(tools,key={it.id}) { tool -> ToolCard(tool,settings.language,{ openTool(tool) }) }
                                    }
                                    if(shellRoute=="home") {
                                        item { QuietSectionTitle(stringResource(R.string.recent_results_title)) }
                                        items(outputs.take(5),key={"recent-${it.id}"}) { output -> OutputCard(output,{launchExternal(output.uri,output.mimeType,false)},{launchExternal(output.uri,output.mimeType,true)},{exportOutput(output)},{viewModel.importOutput(output);navigateTo("workspace")}) }
                                    }
                                }
                                if(shellRoute=="downloads") {
                                    item { CandidateNotice() }
                                    items(CapabilityRegistry.tools.filter { it.id in setOf("dl_02","dl_03","dl_media") },key={it.id}) { tool ->ToolCard(tool,settings.language,{openTool(tool)}) }
                                    item { QuietSectionTitle(stringResource(R.string.transfer_activity_title)) }
                                    val downloads=jobs.filter { it.request.toolId.startsWith("dl_") }
                                    if(downloads.isEmpty()) item { Text("No transfers. Network failures are reported, never replaced by sample files.") }
                                    items(downloads,key={it.request.id}) { job -> JobCard(job,{ selectedJob=job.request.id;navigateTo("activity") },{viewModel.cancel(job.request.id)},{viewModel.retry(job)}) }
                                }
                                if(shellRoute=="files_overview") {
                                    item { Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { OutlinedButton(onClick=::importFiles) { Text(stringResource(R.string.import_device_files)) };OutlinedButton(onClick={pickFolder()}) {Text(stringResource(R.string.tool_browse_storage))} } }
                                    item { Text("Imported snapshots",style=MaterialTheme.typography.titleLarge);Text("Originals remain unchanged. Selecting a snapshot adds it to the workspace.") }
                                    if(imported.isEmpty()) item { Text("No imported files yet.") }
                                    items(imported,key={"source-${it.id}"}) { source ->
                                        QuietCard {
                                            Text(source.displayName,style=MaterialTheme.typography.titleMedium)
                                            Text("${bytes(source.sizeBytes)} | ${source.mimeType ?: "Unknown type"}")
                                            TextButton(onClick={viewModel.addSource(source.id);navigateTo("workspace")}) { Text("Add to workspace") }
                                        }
                                    }
                                    item { QuietSectionTitle(stringResource(R.string.recent_results_title)) }
                                    items(outputs,key={it.id}) { output ->OutputCard(output,{launchExternal(output.uri,output.mimeType,false)},{launchExternal(output.uri,output.mimeType,true)},{exportOutput(output)},{viewModel.importOutput(output);navigateTo("workspace")}) }
                                    tree?.let { listing ->
                                        item { Text(listing.name,style=MaterialTheme.typography.titleLarge);TextButton(onClick={newFolder=true;folderName=""}) {Text("New folder")} }
                                        items(listing.entries,key={"tree-${it.uri}"}) { entry -> QuietCard {
                                            Text(entry.name,style=MaterialTheme.typography.titleMedium);Text(if(entry.isDirectory) "Folder" else bytes(entry.sizeBytes))
                                            if(entry.isDirectory) TextButton(onClick={pickFolder(entry.uri)}) {Text("Browse inside")}
                                            else {
                                                Row { TextButton(onClick={launchExternal(entry.uri,entry.mimeType,false)}) {Text(stringResource(R.string.action_open))};TextButton(onClick={viewModel.intake(Intake(listOf(entry.uri),Intent.FLAG_GRANT_READ_URI_PERMISSION,null));navigateTo("workspace")}) {Text("Import")} }
                                                Row { TextButton(enabled=entry.writable,onClick={renameEntry=entry;renameText=entry.name}) {Text("Rename")};TextButton(enabled=entry.writable,onClick={deleteEntry=entry}) {Text("Delete original")} }
                                            }
                                        } }
                                    }
                                }
                                if(shellRoute=="activity") {
                                    item { Text("Only published output records appear below. A queued job is not a completed result.") }
                                    val displayed=if(selectedJob==null) jobs else jobs.filter { it.request.id==selectedJob }
                                    items(displayed,key={it.request.id}) { job ->
                                        JobCard(job,{}, {viewModel.cancel(job.request.id)},{viewModel.retry(job)})
                                        if(job.request.toolId.startsWith("dl_") && job.progress.stage==JobStage.WAITING_FOR_INPUT) {
                                            var replacement by remember(job.request.id) { mutableStateOf("") }
                                            OutlinedTextField(replacement,{replacement=it},label={Text("Re-enter the original URL(s) after process restart")},modifier=Modifier.fillMaxWidth())
                                            Text("URLs are not saved in drafts or diagnostics. Changed URLs or validators require a new transfer; old partial bytes are not appended.")
                                            TextButton(onClick={viewModel.retry(job,replacement);replacement=""},enabled=replacement.isNotBlank()) {Text("Retry with re-entered URL(s)")}
                                        }
                                        if(job.outcomes.isNotEmpty()) Text(job.outcomes.joinToString("\n") { "${it.itemKey}: ${it.status}${it.errorCode?.let { code->" | $code" } ?: ""}" },style=MaterialTheme.typography.bodySmall)
                                    }
                                    items(outputs.filter { selectedJob==null || it.jobId==selectedJob },key={it.id}) { output ->OutputCard(output,{launchExternal(output.uri,output.mimeType,false)},{launchExternal(output.uri,output.mimeType,true)},{exportOutput(output)},{viewModel.importOutput(output);navigateTo("workspace")}) }
                                    item { TextButton(onClick={viewModel.allowNewRun();navigateTo("workspace")}) {Text("Start a new run")} }
                                }
                                if(shellRoute=="settings") {
                                    item { Text(stringResource(R.string.settings_appearance_language),style=MaterialTheme.typography.titleLarge) }
                                    items(ThemeMode.entries) { mode -> Row(verticalAlignment=Alignment.CenterVertically) { RadioButton(settings.themeMode==mode,{viewModel.setThemeMode(mode)});Text(if(settings.language.isRtl) mode.labelAr else mode.labelEn) } }
                                    items(AppLanguage.entries) { lang -> Row(verticalAlignment=Alignment.CenterVertically) { RadioButton(settings.language==lang,{viewModel.setLanguage(lang)});Text(lang.displayName) } }
                                    item { if(Build.VERSION.SDK_INT>=33) OutlinedButton(onClick={notifications.launch(Manifest.permission.POST_NOTIFICATIONS)}) {Text("Allow job notifications") } }
                                    item { TextButton(onClick=viewModel::refreshStorage) {Text("Refresh storage totals")};Text("Imported snapshots: ${bytes(storage.sources)}\nPublished outputs: ${bytes(storage.outputs)}\nDownload partials: ${bytes(storage.partials)}\nTemporary cache: ${bytes(storage.temporary)}") }
                                    item { Text("No AI, OCR, analytics, or cloud document processing. Media extraction and browser pages can contact the submitted site and its associated media/CDN services. A file selected through Android's document picker may itself be hosted by that provider.") }
                                    item { CandidateNotice() }
                                    item { Text("Release verification and feature limitations are documented in the source package's verification report.") }
                                    item { OutlinedButton(onClick={ viewModel.prepareErrorLogExport(); errorLogExportPicker.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE);type="text/plain";putExtra(Intent.EXTRA_TITLE,"jumlo-errors.txt") }) }) { Text(stringResource(R.string.settings_export_error_log)) } }
                                }
                            }
                        }
                    }
                    if(root && !wide) QuietFloatingDock(tab,settings.language,{ next->tab=next;route=next.route },Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
    }

    NavHost(navController = navController, startDestination = HomeRoute, modifier = Modifier.fillMaxSize()) {
        composable<HomeRoute> { Shell("home") }
        composable<ToolsRoute> { Shell("tools_catalog") }
        composable<DownloadsRoute> { Shell("downloads") }
        composable<FilesRoute> { Shell("files_overview") }
        composable<SettingsRoute> { Shell("settings") }
        composable<ScanRoute> { Shell("scan") }
        composable<WorkspaceRoute>(deepLinks = listOf(navDeepLink<WorkspaceRoute>(basePath = "jumlo://workspace"))) { Shell("workspace") }
        composable<ActivityRoute> { Shell("activity") }
    }
    if(!settings.hasCompletedOnboarding) AlertDialog(onDismissRequest={},title={Text(stringResource(R.string.welcome_title))},text={Text("Select your files, configure a tool, then run it locally. This source candidate has not passed Android device qualification. Existing originals are never replaced by processing jobs.")},confirmButton={TextButton(onClick=viewModel::finishOnboarding) {Text(stringResource(R.string.welcome_get_started))}})
    deleteEntry?.let { entry -> AlertDialog(onDismissRequest={deleteEntry=null},title={Text("Delete original file?")},text={Text(entry.name+"\nThis requests permanent deletion from the selected provider. Imported snapshots are separate copies.")},confirmButton={TextButton(onClick={deleteEntry=null;viewModel.deleteFile(entry)}) {Text("Delete")}},dismissButton={TextButton(onClick={deleteEntry=null}) {Text(stringResource(R.string.action_cancel))}}) }
    renameEntry?.let { entry -> AlertDialog(onDismissRequest={renameEntry=null},title={Text("Rename original file")},text={OutlinedTextField(renameText,{renameText=it},singleLine=true)},confirmButton={TextButton(onClick={renameEntry=null;viewModel.renameFile(entry,renameText)}) {Text("Rename")}},dismissButton={TextButton(onClick={renameEntry=null}) {Text(stringResource(R.string.action_cancel))}}) }
    if(newFolder) AlertDialog(onDismissRequest={newFolder=false},title={Text("New folder")},text={OutlinedTextField(folderName,{folderName=it},singleLine=true)},confirmButton={TextButton(onClick={newFolder=false;viewModel.createFolder(folderName)}) {Text("Create")}},dismissButton={TextButton(onClick={newFolder=false}) {Text(stringResource(R.string.action_cancel))}})
}

@Composable private fun CandidateNotice() {
    Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=MaterialTheme.shapes.medium) {
        Text(if(BuildConfig.DEBUG) "Development candidate: real processing is enabled for testing; Android build and device qualification are pending." else "Processing features remain disabled in release until device qualification is completed.",Modifier.padding(16.dp),style=MaterialTheme.typography.bodySmall)
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
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            QuietIconTile(resolveStitchIcon(tool.iconName),tint,container)
            Column(Modifier.weight(1f)) {
                Text(title(tool,language),style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(if(language.isRtl) tool.subtitleAr else tool.subtitleEn,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
        }
        if(enabled) QuietTonalButton(stringResource(R.string.action_open),onOpen,Modifier.align(Alignment.End),leadingIcon=Icons.AutoMirrored.Rounded.ArrowForward)
        else Text(CapabilityRegistry.reason(tool.id).replace('_',' '),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun OutputCard(output:OutputRecord,onOpen:()->Unit,onShare:()->Unit,onExport:()->Unit,onReuse:()->Unit) {
    QuietCard {
        Text(output.displayName,style=MaterialTheme.typography.titleMedium)
        Text("${bytes(output.sizeBytes)} | ${output.mimeType}")
        output.metadata.pageCount?.let { Text("$it pages") }
        if(output.metadata.width!=null && output.metadata.height!=null) Text("${output.metadata.width} x ${output.metadata.height} pixels")
        output.metadata.entryCount?.let { Text("$it archive entries") }
        Text("SHA-256: ${output.sha256}",style=MaterialTheme.typography.bodySmall)
        LazyRow { item { TextButton(onOpen) {Text(stringResource(R.string.action_open))} };item {TextButton(onShare) {Text(stringResource(R.string.action_share))}};item {TextButton(onExport) {Text("Save as")}};item {TextButton(onReuse) {Text("Use as input")}} }
    }
}
@Composable private fun JobCard(job:JobSnapshot,onOpen:()->Unit,onCancel:()->Unit,onRetry:()->Unit) {
    QuietCard {
        val active=job.progress.stage in setOf(JobStage.QUEUED,JobStage.VALIDATING,JobStage.RUNNING,JobStage.FINALIZING)
        Text(job.request.toolId+" | "+job.progress.stage.name.replace('_',' '),style=MaterialTheme.typography.titleMedium)
        Text(DateFormat.getDateTimeInstance().format(Date(job.updatedAtEpochMs)),style=MaterialTheme.typography.bodySmall)
        if(active) {
            val total=job.progress.totalUnits
            if(total==null || total<=0) LinearProgressIndicator(Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress={ (job.progress.completedUnits.toDouble()/total).toFloat().coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth())
            Text(if(job.request.toolId.startsWith("dl_")) "${bytes(job.progress.completedUnits)} / ${bytes(total)}" else "${job.progress.completedUnits} / ${total?.toString() ?: "Unknown"}")
        }
        Row { TextButton(onOpen) {Text("Details")};if(active) TextButton(onCancel) {Text(stringResource(R.string.action_cancel))};if(job.progress.stage in setOf(JobStage.FAILED,JobStage.PARTIAL,JobStage.INTERRUPTED,JobStage.WAITING_FOR_INPUT)) TextButton(onRetry) {Text("Retry failed items")} }
        val created=job.outcomes.count { it.outputId!=null };val failed=job.outcomes.count { it.status==ItemStatus.FAILED }
        if(job.outcomes.isNotEmpty()) Text("$created published; $failed failed",style=MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun WorkspaceScreen(vm:AppViewModel,form:WorkspaceDraft,imported:List<SourceRef>,busy:Boolean,urls:String,language:AppLanguage,onImport:()->Unit,onPhotos:()->Unit,onExecute:()->Unit) {
    val tool=form.toolId
    val download=tool.startsWith("dl_")
    val viewer=tool=="pdf_01"
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    val queue=form.sourceIds.mapNotNull { id->imported.find { it.id==id } }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().widthIn(max=760.dp).align(Alignment.CenterHorizontally),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item { CandidateNotice() }
            if(!download) {
                item { Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { OutlinedButton(onImport,enabled=!busy) {Text(stringResource(R.string.import_device_files))};if(tool=="pdf_02" || tool.startsWith("img_")) OutlinedButton(onPhotos,enabled=!busy) {Text(stringResource(R.string.import_photo_gallery))} } }
                item { Text("${queue.size} selected | ${bytes(queue.mapNotNull { it.sizeBytes }.sum())}",style=MaterialTheme.typography.titleMedium);Text("Files are processed in the order shown. Missing or unreadable inputs fail explicitly.") }
                itemsIndexed(queue,key={_,it->it.id}) { index,source -> QuietCard {
                    Text(source.displayName,style=MaterialTheme.typography.titleMedium);Text("${bytes(source.sizeBytes)} | ${source.mimeType ?: "Unknown type"}")
                    LazyRow { item {TextButton(onClick={previewId=source.id}) {Text("Preview")}};item {TextButton(onClick={vm.moveSource(index,-1)},enabled=index>0) {Text("Move up")}};item {TextButton(onClick={vm.moveSource(index,1)},enabled=index<queue.lastIndex) {Text("Move down")}};item {TextButton(onClick={vm.removeSource(source.id)}) {Text("Remove")}} }
                } }
                if(queue.isNotEmpty()) item { TextButton(onClick=vm::clearQueue) {Text("Clear selection (keep files)")} }
                if(viewer && queue.isNotEmpty()) item { InputPreview(vm,queue.first().id) }
            } else {
                item { OutlinedTextField(urls,vm::setUrls,label={Text(if(tool=="dl_03") "Direct URLs, one per line" else "Direct file URL")},modifier=Modifier.fillMaxWidth(),minLines=if(tool=="dl_03") 4 else 2);Text("URLs stay in memory. After process restart you may need to enter them again. Downloaded bytes are published only after the response finishes and validation passes.",style=MaterialTheme.typography.bodySmall) }
            }
            if(!viewer) item { WorkspaceFields(form,vm::updateForm) }
        }
        if(!viewer) Surface(shadowElevation=6.dp) {
            QuietPrimaryButton(if(download) "Queue download" else "Process selected files",onExecute,Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal=20.dp,vertical=12.dp),enabled=!busy && CapabilityRegistry.enabled(tool) && (tool=="webpage_pdf" || (download && urls.isNotBlank() || !download && form.sourceIds.isNotEmpty())))
        }
    }
    previewId?.let { id -> AlertDialog(onDismissRequest={previewId=null},title={Text("Input preview")},text={InputPreview(vm,id)},confirmButton={TextButton(onClick={previewId=null}) {Text("Close")}}) }
}

@Composable private fun InputPreview(vm:AppViewModel,sourceId:String) {
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
    // Bitmap references are bounded to one preview. Compose owns the current frame until it leaves.
    DisposableEffect(frame) { val previous=frame;onDispose { previous?.bitmap?.takeUnless { it.isRecycled }?.recycle() } }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        failure?.let {Text(it.replace('_',' '))}
        frame?.let { current ->
            Image(current.bitmap.asImageBitmap(),"Input preview",Modifier.fillMaxWidth().heightIn(min=120.dp,max=420.dp),contentScale=ContentScale.Fit)
            Text("${current.width} x ${current.height} | ${current.page} / ${current.count}")
            if(current.count>1) Row { TextButton(enabled=page>1 && !loading,onClick={page--}) {Text("Previous")};TextButton(enabled=page<current.count && !loading,onClick={page++}) {Text("Next")} }
        }
    }
}

@Composable private fun Field(label:String,value:String,onValue:(String)->Unit,numeric:Boolean=false) {
    OutlinedTextField(value,{onValue(it.take(512))},label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=if(numeric) KeyboardType.Decimal else KeyboardType.Text))
}
@Composable private fun Options(label:String,options:List<String>,selected:String,onSelect:(String)->Unit) {
    Text(label,style=MaterialTheme.typography.titleSmall)
    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { items(options) { choice ->FilterChip(selected==choice,{onSelect(choice)},label={Text(choice)}) } }
}
@Composable private fun Toggle(label:String,checked:Boolean,onChange:(Boolean)->Unit) { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text(label,Modifier.weight(1f));Switch(checked,onChange)} }

@Composable private fun WorkspaceFields(f:WorkspaceDraft,edit:((WorkspaceDraft)->WorkspaceDraft)->Unit) {
    QuietCard {
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            val image=f.toolId.startsWith("img_")
            if(image) {
                Options("Output format",listOf("JPEG","PNG","WEBP"),f.format) { value->edit {it.copy(format=value)} }
                Field("Quality (1-100; PNG is lossless)",f.quality,{value->edit {it.copy(quality=value)}},true)
                Field("Output filename prefix",f.prefix,{value->edit {it.copy(prefix=value)}})
                Text("Orientation is normalized before processing. Output is sRGB; metadata is removed. JPEG transparency is flattened onto white. Savings are measured after encoding, not predicted.",style=MaterialTheme.typography.bodySmall)
            }
            when(f.toolId) {
                "img_02" -> {
                    Field("Target width (pixels)",f.width,{v->edit {it.copy(width=v)}},true)
                    Field("Target height (pixels)",f.height,{v->edit {it.copy(height=v)}},true)
                    Field("Percentage (optional)",f.percentage,{v->edit {it.copy(percentage=v,longestEdge="")}},true)
                    Field("Longest edge (optional)",f.longestEdge,{v->edit {it.copy(longestEdge=v,percentage="")}},true)
                    Options("Fit strategy",listOf("FIT","FILL","PAD","STRETCH"),f.fit) {v->edit {it.copy(fit=v)}}
                    Toggle("Allow upscaling",f.allowUpscaling) {v->edit {it.copy(allowUpscaling=v)}}
                }
                "img_03" -> {
                    Text("Crop coordinates are normalized to the orientation-corrected original (0 to 1).")
                    Field("Left",f.cropLeft,{v->edit {it.copy(cropLeft=v)}},true);Field("Top",f.cropTop,{v->edit {it.copy(cropTop=v)}},true)
                    Field("Right",f.cropRight,{v->edit {it.copy(cropRight=v)}},true);Field("Bottom",f.cropBottom,{v->edit {it.copy(cropBottom=v)}},true)
                    Options("Clockwise rotation",listOf("0","90","180","270"),(f.turns*90).toString()) {v->edit {it.copy(turns=v.toInt()/90)}}
                    Toggle("Flip horizontal",f.flipX) {v->edit {it.copy(flipX=v)}};Toggle("Flip vertical",f.flipY) {v->edit {it.copy(flipY=v)}}
                }
                "pdf_02" -> {
                    Field("PDF filename",f.outputName,{v->edit {it.copy(outputName=v)}})
                    Field("Paper width (points; A4 = 595)",f.paperWidth,{v->edit {it.copy(paperWidth=v)}},true)
                    Field("Paper height (points; A4 = 842)",f.paperHeight,{v->edit {it.copy(paperHeight=v)}},true)
                    Field("Margin (mm)",f.margin,{v->edit {it.copy(margin=v)}},true)
                    Options("Orientation",listOf("AUTO","PORTRAIT","LANDSCAPE"),f.orientation) {v->edit {it.copy(orientation=v)}}
                    Toggle("Fill page (may crop edges)",f.fill) {v->edit {it.copy(fill=v)}}
                }
                "pdf_07" -> {
                    Field("Physical pages: all or 1-3,7",f.pages,{v->edit {it.copy(pages=v)}})
                    Field("DPI (36-600)",f.dpi,{v->edit {it.copy(dpi=v)}},true)
                    Options("Output format",listOf("PNG","JPEG"),f.format) {v->edit {it.copy(format=v)}}
                    Field("JPEG quality (1-100)",f.quality,{v->edit {it.copy(quality=v)}},true)
                    Toggle("Grayscale",f.grayscale) {v->edit {it.copy(grayscale=v)}}
                    Text("One image is published per selected physical page. Text and vectors become raster pixels; this is not a structural PDF edit.")
                }
                "file_07" -> { Field("Archive filename (.zip)",f.outputName,{v->edit {it.copy(outputName=v)}});Field("Compression (0-9)",f.zipLevel,{v->edit {it.copy(zipLevel=v)}},true);Text("ZIP entries contain the selected file bytes. Original files are preserved; collisions use keep-both names.") }
                "file_08" -> Text("Choose exactly one ZIP. Paths, entry count, actual expanded bytes and CRC are checked. Each extracted file becomes a separate verified-byte output; its relative path remains in its job item key.")
                "sha256" -> Field("Expected SHA-256 (optional)",f.expectedHash,{v->edit {it.copy(expectedHash=v)}})
                "pdf_03" -> { Field("Output filename",f.outputName,{v->edit {it.copy(outputName=v)}}); Text("All selected PDFs merge in queue order.") }
                "pdf_05" -> { Field("Output filename",f.outputName,{v->edit {it.copy(outputName=v)}}); Toggle("Split every N pages",f.flag1) {v->edit {it.copy(flag1=v)}}; if(f.flag1) Field("Pages per part",f.num1,{v->edit {it.copy(num1=v)}},true) else Field("Pages (one output each): 1,3,5",f.pages,{v->edit {it.copy(pages=v)}}) }
                "pdf_06" -> { Field("Output filename",f.outputName,{v->edit {it.copy(outputName=v)}}); Field("Pages: 1-3,7",f.pages,{v->edit {it.copy(pages=v)}}) }
                "pdf_04" -> { Field("Output filename",f.outputName,{v->edit {it.copy(outputName=v)}}); Field("New order (comma pages, blank = original; omitted pages are deleted)",f.text1,{v->edit {it.copy(text1=v)}}); Field("Rotate these pages (ranges)",f.text2,{v->edit {it.copy(text2=v)}}); Field("Rotate degrees (90)",f.num1,{v->edit {it.copy(num1=v)}},true) }
                "pdf_crop" -> { Field("Output filename",f.outputName,{v->edit {it.copy(outputName=v)}}); Field("Pages: all or 1-3",f.pages,{v->edit {it.copy(pages=v)}}); Field("Left (0-1)",f.cropLeft,{v->edit {it.copy(cropLeft=v)}},true); Field("Top (0-1)",f.cropTop,{v->edit {it.copy(cropTop=v)}},true); Field("Right (0-1)",f.cropRight,{v->edit {it.copy(cropRight=v)}},true); Field("Bottom (0-1)",f.cropBottom,{v->edit {it.copy(cropBottom=v)}},true) }
                "pdf_compress" -> { Field("Output filename",f.outputName,{v->edit {it.copy(outputName=v)}}); Field("Max image DPI",f.num1,{v->edit {it.copy(num1=v)}},true); Field("JPEG quality (1-100)",f.quality,{v->edit {it.copy(quality=v)}},true); Text("Text stays selectable; only oversized images are downscaled.") }
                "pdf_stamp" -> { Field("Output filename",f.outputName,{v->edit {it.copy(outputName=v)}}); Field("Watermark / stamp text",f.text1,{v->edit {it.copy(text1=v)}}); Field("Pages: all or 1-3",f.pages,{v->edit {it.copy(pages=v)}}); Toggle("Add page numbers",f.flag1) {v->edit {it.copy(flag1=v)}} }
                "pdf_annotate" -> { Field("Output filename",f.outputName,{v->edit {it.copy(outputName=v)}}); Field("Text",f.text1,{v->edit {it.copy(text1=v)}}); Field("Page",f.num1,{v->edit {it.copy(num1=v)}},true); Field("X (0-1)",f.num2,{v->edit {it.copy(num2=v)}},true); Field("Y from top (0-1)",f.num3,{v->edit {it.copy(num3=v)}},true); Toggle("As note annotation (not overlay)",f.flag1) {v->edit {it.copy(flag1=v)}} }
                "pdf_signature" -> { Field("Output filename",f.outputName,{v->edit {it.copy(outputName=v)}}); Text("Select the PDF first, then the signature image (PNG with transparency works best). Or draw below, save it, and add it as the second file."); com.jumlo.app.ui.scan.SignaturePad { file -> vm.intakeLocalFile(file,"signature.png","image/png") }; Field("Page",f.num1,{v->edit {it.copy(num1=v)}},true); Field("Left (0-1)",f.cropLeft,{v->edit {it.copy(cropLeft=v)}},true); Field("Top (0-1)",f.cropTop,{v->edit {it.copy(cropTop=v)}},true); Field("Right (0-1)",f.cropRight,{v->edit {it.copy(cropRight=v)}},true); Field("Bottom (0-1)",f.cropBottom,{v->edit {it.copy(cropBottom=v)}},true) }
                "pdf_metadata" -> { Field("Output filename",f.outputName,{v->edit {it.copy(outputName=v)}}); Field("Title",f.text1,{v->edit {it.copy(text1=v)}}); Field("Author",f.text2,{v->edit {it.copy(text2=v)}}); Field("Subject",f.text3,{v->edit {it.copy(text3=v)}}); Field("Keywords",f.text4,{v->edit {it.copy(text4=v)}}) }
                "pdf_protect" -> { Field("Output filename",f.outputName,{v->edit {it.copy(outputName=v)}}); Toggle("Remove protection (open with current password first)",f.flag1) {v->edit {it.copy(flag1=v)}}; if(!f.flag1){ Field("User password (session only)",f.secret1,{v->edit {it.copy(secret1=v)}}); Field("Owner password (session only, required)",f.secret2,{v->edit {it.copy(secret2=v)}}) }; Text("Passwords are never saved in drafts, jobs, or logs. Encrypted input PDFs are not opened by other tools.") }
                "pdf_forms" -> { Field("Output filename",f.outputName,{v->edit {it.copy(outputName=v)}}); Field("Fields JSON: {\"name\":\"Mo\",\"agree\":true}",f.text1,{v->edit {it.copy(text1=v)}}); Toggle("Flatten after filling",f.flag1) {v->edit {it.copy(flag1=v)}} }
                "pdf_compare" -> Text("Select exactly two PDFs. The report compares extracted text page by page.")
                "pdf_redact" -> { Field("Output filename",f.outputName,{v->edit {it.copy(outputName=v)}}); Field("Page",f.num1,{v->edit {it.copy(num1=v)}},true); Field("Left (0-1)",f.cropLeft,{v->edit {it.copy(cropLeft=v)}},true); Field("Top (0-1)",f.cropTop,{v->edit {it.copy(cropTop=v)}},true); Field("Right (0-1)",f.cropRight,{v->edit {it.copy(cropRight=v)}},true); Field("Bottom (0-1)",f.cropBottom,{v->edit {it.copy(cropBottom=v)}},true); Field("Verify text is gone (optional)",f.text1,{v->edit {it.copy(text1=v)}}); Text("Content inside the region is removed at operator level and verified; complex pages fail instead of faking redaction.") }
                "pdf_ocr" -> { Field("Pages ignored: all pages are OCR'd",f.pages,{v->edit {it.copy(pages=v)}}); Text("On-device OCR. Publishes ocr.txt with page separators.") }
                "webpage_pdf" -> { Field("Webpage URL (https://…)",f.text1,{v->edit {it.copy(text1=v)}}); Text("The page is rendered by WebView and printed to PDF. Login/DRM pages may print blank.") }
                "img_exif" -> { Toggle("Remove all metadata in output",f.flag1) {v->edit {it.copy(flag1=v)}}; Toggle("Remove location in output",f.flag2) {v->edit {it.copy(flag2=v)}}; Text("Publishes an EXIF report plus a re-encoded, metadata-stripped copy.") }
                "img_watermark" -> { Field("Watermark text",f.text1,{v->edit {it.copy(text1=v)}}); Field("Opacity % (1-100)",f.num1,{v->edit {it.copy(num1=v)}},true); Toggle("Use second selected image as logo",f.flag1) {v->edit {it.copy(flag1=v)}} }
                "img_join","img_stitch" -> { if(f.toolId=="img_join") Options("Layout",listOf("GRID2","HSTRIP","VSTRIP"),f.text1.ifBlank{"GRID2"}) {v->edit {it.copy(text1=v)}}; Field("Gap (px)",f.num1,{v->edit {it.copy(num1=v)}},true) }
                "img_adjust" -> { Field("Brightness (-100..100)",f.num1,{v->edit {it.copy(num1=v)}},true); Field("Contrast % (100 = none)",f.num2,{v->edit {it.copy(num2=v)}},true); Field("Saturation % (100 = none)",f.num3,{v->edit {it.copy(num3=v)}},true); Field("Padding px (white canvas)",f.num4,{v->edit {it.copy(num4=v)}},true) }
                "img_target" -> Field("Target bytes",f.num1,{v->edit {it.copy(num1=v)}},true)
                "img_compare" -> Text("Select exactly two images. Publishes a dimension/difference report.")
                "file_batch_rename" -> { Field("Pattern: file-{n}, {name}, {ext}",f.text1,{v->edit {it.copy(text1=v)}}); Field("Start number",f.num1,{v->edit {it.copy(num1=v)}},true); Text("Originals are never renamed: renamed copies are published as results.") }
                "file_duplicates" -> Text("Hashes every selected file (SHA-256) and reports exact duplicate groups.")
                "file_trash" -> Text("Runs the duplicate/storage report. Published results remain recoverable in Files until you delete them; nothing is auto-deleted.")
                "file_encrypted_zip" -> { Field("Archive filename (.zip)",f.outputName,{v->edit {it.copy(outputName=v)}}); Toggle("Extract instead of create",f.flag1) {v->edit {it.copy(flag1=v)}}; Field("ZIP password (session only)",f.secret1,{v->edit {it.copy(secret1=v)}}); Text("AES-256 encrypted ZIP via zip4j. Password is never saved.") }
                "dl_02","dl_03" -> {
                    Field("Output filename (batch adds an index)",f.outputName,{v->edit {it.copy(outputName=v)}})
                    Toggle("Wi-Fi only",f.wifiOnly) {v->edit {it.copy(wifiOnly=v)}}
                    Toggle("Unmetered only",f.unmeteredOnly) {v->edit {it.copy(unmeteredOnly=v)}}
                    Toggle("Allow unencrypted HTTP for this job",f.cleartextConsent) {v->edit {it.copy(cleartextConsent=v)}}
                    if(f.cleartextConsent) Text("HTTP is not encrypted. Do not use credentials or private documents. HTTPS downgrades and credential headers over cleartext remain blocked.",color=MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
