package com.jumlo.app.ui.navigation

import android.Manifest
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
import kotlinx.coroutines.CancellationException
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
    var route by rememberSaveable { mutableStateOf("home") }
    var tab by rememberSaveable { mutableStateOf(DockTab.HOME) }
    var selectedJob by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var deleteEntry by remember { mutableStateOf<DocumentEntry?>(null) }
    var renameEntry by remember { mutableStateOf<DocumentEntry?>(null) }
    var renameText by remember { mutableStateOf("") }
    var newFolder by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    val snackbar=remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it.replace('_',' '));viewModel.dismissMessage() } }
    BackHandler(route !in setOf("home","tools_catalog","downloads","files_overview")) { route=tab.route }
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
        if(viewModel.chooseTool(tool.id)) route=if(tool.id=="file_02") "files_overview" else "workspace"
    }
    val root=route in setOf("home","tools_catalog","downloads","files_overview")
    Scaffold(snackbarHost={ SnackbarHost(snackbar) },contentWindowInsets=WindowInsets(0,0,0,0)) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide=maxWidth>=840.dp
            Row(Modifier.fillMaxSize()) {
                if(wide && root) QuietExpandedNavigationRail(tab,settings.language,{ next ->tab=next;route=next.route })
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.fillMaxSize()) {
                        if(root) QuietRootTopBar(title=when(route) { "home"->stringResource(R.string.app_name);"tools_catalog"->stringResource(R.string.tools_catalog_title);"downloads"->stringResource(R.string.nav_downloads);else->stringResource(R.string.nav_files) },onSettingsClick={ route="settings" })
                        else QuietWorkspaceTopBar(title=when(route) { "workspace"->CapabilityRegistry.tools.find { it.id==form.toolId }?.let { title(it,settings.language) } ?: "Workspace";"activity"->"Activity & outputs";else->stringResource(R.string.settings_title) },onBack={ route=tab.route })
                        if(route=="workspace" && form.toolId=="dl_media") {
                            com.jumlo.app.ui.downloads.MediaWorkspace(urls,{ selectedJob=it;route="activity" },{ value ->viewModel.chooseTool("dl_02");viewModel.setUrls(value);route="workspace" })
                        } else if(route=="workspace") {
                            WorkspaceScreen(viewModel,form,imported,busy,urls,settings.language,::importFiles,{photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}) { viewModel.submit { selectedJob=it;route="activity" } }
                        } else {
                            LazyColumn(Modifier.weight(1f).fillMaxWidth().widthIn(max=900.dp).align(Alignment.CenterHorizontally),contentPadding=PaddingValues(start=20.dp,end=20.dp,top=16.dp,bottom=if(root && !wide) 124.dp else 32.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                                if(route=="home" || route=="tools_catalog") {
                                    item { CandidateNotice() }
                                    if(route=="home") {
                                        item { QuietHero(title=if(settings.language.isRtl) "أدوات ملفات قوية" else "Powerful file tools",subtitle=if(settings.language.isRtl) "PDF والصور والأرشيفات والتنزيلات، تتم معالجتها على جهازك." else "PDF, images, archives and downloads, processed on your device.",icon=Icons.Rounded.Bolt) }
                                        item { QuietPrimaryButton(stringResource(R.string.import_device_files),::importFiles,Modifier.fillMaxWidth(),leadingIcon=Icons.Rounded.Add,enabled=!busy) }
                                        item { TextButton(onClick={ route="workspace" }) { Text("Resume saved workspace (${form.sourceIds.size} files)") } }
                                    }
                                    item { QuietSearchBar(query=search,placeholder=stringResource(R.string.home_search_placeholder),onQueryChange={ search=it },onClear={ search="" }) }
                                    val catalog=CapabilityRegistry.tools.filter { search.isBlank() || title(it,settings.language).contains(search,true) || it.id.contains(search,true) }
                                    for(pillar in listOf(PillarCategory.PDF,PillarCategory.IMAGES,PillarCategory.DOWNLOADS,PillarCategory.FILES,PillarCategory.VAULT)) {
                                        val tools=catalog.filter { it.pillar==pillar }
                                        if(tools.isNotEmpty()) item(key="heading-${pillar.name}") { QuietSectionTitle(if(settings.language.isRtl) pillar.labelAr else pillar.labelEn) }
                                        items(tools,key={it.id}) { tool -> ToolCard(tool,settings.language,{ openTool(tool) }) }
                                    }
                                    if(route=="home") {
                                        item { QuietSectionTitle(stringResource(R.string.recent_results_title)) }
                                        items(outputs.take(5),key={"recent-${it.id}"}) { output -> OutputCard(output,{launchExternal(output.uri,output.mimeType,false)},{launchExternal(output.uri,output.mimeType,true)},{exportOutput(output)},{viewModel.importOutput(output);route="workspace"}) }
                                    }
                                }
                                if(route=="downloads") {
                                    item { CandidateNotice() }
                                    items(CapabilityRegistry.tools.filter { it.id in setOf("dl_02","dl_03","dl_media") },key={it.id}) { tool ->ToolCard(tool,settings.language,{openTool(tool)}) }
                                    item { QuietSectionTitle("Transfer activity") }
                                    val downloads=jobs.filter { it.request.toolId.startsWith("dl_") }
                                    if(downloads.isEmpty()) item { Text("No transfers. Network failures are reported, never replaced by sample files.") }
                                    items(downloads,key={it.request.id}) { job -> JobCard(job,{ selectedJob=job.request.id;route="activity" },{viewModel.cancel(job.request.id)},{viewModel.retry(job)}) }
                                }
                                if(route=="files_overview") {
                                    item { Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { OutlinedButton(onClick=::importFiles) { Text(stringResource(R.string.import_device_files)) };OutlinedButton(onClick={pickFolder()}) {Text(stringResource(R.string.tool_browse_storage))} } }
                                    item { Text("Imported snapshots",style=MaterialTheme.typography.titleLarge);Text("Originals remain unchanged. Selecting a snapshot adds it to the workspace.") }
                                    if(imported.isEmpty()) item { Text("No imported files yet.") }
                                    items(imported,key={"source-${it.id}"}) { source ->
                                        QuietCard {
                                            Text(source.displayName,style=MaterialTheme.typography.titleMedium)
                                            Text("${bytes(source.sizeBytes)} | ${source.mimeType ?: "Unknown type"}")
                                            TextButton(onClick={viewModel.addSource(source.id);route="workspace"}) { Text("Add to workspace") }
                                        }
                                    }
                                    item { QuietSectionTitle(stringResource(R.string.recent_results_title)) }
                                    items(outputs,key={it.id}) { output ->OutputCard(output,{launchExternal(output.uri,output.mimeType,false)},{launchExternal(output.uri,output.mimeType,true)},{exportOutput(output)},{viewModel.importOutput(output);route="workspace"}) }
                                    tree?.let { listing ->
                                        item { Text(listing.name,style=MaterialTheme.typography.titleLarge);TextButton(onClick={newFolder=true;folderName=""}) {Text("New folder")} }
                                        items(listing.entries,key={"tree-${it.uri}"}) { entry -> QuietCard {
                                            Text(entry.name,style=MaterialTheme.typography.titleMedium);Text(if(entry.isDirectory) "Folder" else bytes(entry.sizeBytes))
                                            if(entry.isDirectory) TextButton(onClick={pickFolder(entry.uri)}) {Text("Browse inside")}
                                            else {
                                                Row { TextButton(onClick={launchExternal(entry.uri,entry.mimeType,false)}) {Text(stringResource(R.string.action_open))};TextButton(onClick={viewModel.intake(Intake(listOf(entry.uri),Intent.FLAG_GRANT_READ_URI_PERMISSION,null));route="workspace"}) {Text("Import")} }
                                                Row { TextButton(enabled=entry.writable,onClick={renameEntry=entry;renameText=entry.name}) {Text("Rename")};TextButton(enabled=entry.writable,onClick={deleteEntry=entry}) {Text("Delete original")} }
                                            }
                                        } }
                                    }
                                }
                                if(route=="activity") {
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
                                    items(outputs.filter { selectedJob==null || it.jobId==selectedJob },key={it.id}) { output ->OutputCard(output,{launchExternal(output.uri,output.mimeType,false)},{launchExternal(output.uri,output.mimeType,true)},{exportOutput(output)},{viewModel.importOutput(output);route="workspace"}) }
                                    item { TextButton(onClick={viewModel.allowNewRun();route="workspace"}) {Text("Start a new run")} }
                                }
                                if(route=="settings") {
                                    item { Text(stringResource(R.string.settings_appearance_language),style=MaterialTheme.typography.titleLarge) }
                                    items(ThemeMode.entries) { mode -> Row(verticalAlignment=Alignment.CenterVertically) { RadioButton(settings.themeMode==mode,{viewModel.setThemeMode(mode)});Text(if(settings.language.isRtl) mode.labelAr else mode.labelEn) } }
                                    items(AppLanguage.entries) { lang -> Row(verticalAlignment=Alignment.CenterVertically) { RadioButton(settings.language==lang,{viewModel.setLanguage(lang)});Text(lang.displayName) } }
                                    item { if(Build.VERSION.SDK_INT>=33) OutlinedButton(onClick={notifications.launch(Manifest.permission.POST_NOTIFICATIONS)}) {Text("Allow job notifications") } }
                                    item { TextButton(onClick=viewModel::refreshStorage) {Text("Refresh storage totals")};Text("Imported snapshots: ${bytes(storage.sources)}\nPublished outputs: ${bytes(storage.outputs)}\nDownload partials: ${bytes(storage.partials)}\nTemporary cache: ${bytes(storage.temporary)}") }
                                    item { Text("No AI, OCR, analytics, or cloud document processing. Media extraction and browser pages can contact the submitted site and its associated media/CDN services. A file selected through Android's document picker may itself be hosted by that provider.") }
                                    item { CandidateNotice() }
                                    item { Text("Release verification and feature limitations are documented in the source package's verification report.") }
                                }
                            }
                        }
                    }
                    if(root && !wide) QuietFloatingDock(tab,settings.language,{ next->tab=next;route=next.route },Modifier.align(Alignment.BottomCenter))
                }
            }
        }
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
            QuietPrimaryButton(if(download) "Queue download" else "Process selected files",onExecute,Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal=20.dp,vertical=12.dp),enabled=!busy && CapabilityRegistry.enabled(tool) && (download && urls.isNotBlank() || !download && form.sourceIds.isNotEmpty()))
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
