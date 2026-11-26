package com.jumlo.app.ui.files

import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.util.rememberAppHaptics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.jumlo.app.ui.DataLoadStatus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.automirrored.rounded.ArrowBack
import com.jumlo.app.ui.icons.automirrored.rounded.Sort
import com.jumlo.app.ui.icons.automirrored.rounded.ViewList
import com.jumlo.app.ui.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jumlo.app.R
import com.jumlo.app.core.StoragePaths
import com.jumlo.app.data.files.DocumentEntry
import com.jumlo.app.data.files.DocumentListing
import com.jumlo.app.data.files.previewDocumentRename
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.components.*
import com.jumlo.app.model.*

@OptIn(ExperimentalFoundationApi::class)
@Composable fun FilesScreen(vm:AppViewModel,onImport:()->Unit,onFolder:()->Unit,onSavedFolder:()->Unit,onOpen:(OutputRecord)->Unit,onShare:(OutputRecord)->Unit,onExport:(OutputRecord)->Unit,onReuse:(OutputRecord)->Unit,onSource:(String)->Unit,onBrowse:(DocumentEntry)->Unit,onProviderOpen:(DocumentEntry)->Unit,onProviderImport:(DocumentEntry)->Unit,onCopy:(DocumentEntry)->Unit,onRename:(DocumentEntry)->Unit,onDelete:(DocumentEntry)->Unit,onNewFolder:()->Unit,
    onOutputWorkflow:(List<OutputRecord>,String)->Unit,onSourceWorkflow:(List<SourceRef>,String)->Unit,onPrint:((OutputRecord)->Unit)?=null,onPrintSource:((String)->Unit)?=null,onChangeSaveFolder:()->Unit=onFolder,onStorage:()->Unit={},onImportToFolder:(String?)->Unit={ onImport() },onScan:(()->Unit)?=null,initialFilter:String?=null,resetToRootEpoch:Int=0,listState:androidx.compose.foundation.lazy.LazyListState=androidx.compose.foundation.lazy.rememberLazyListState(),onBackInterceptChange:((Boolean)->Unit)?=null) {
    val haptics=rememberAppHaptics()
    val context=LocalContext.current
    val outputs by vm.outputs.collectAsStateWithLifecycle()
    val trashed by vm.trashed.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val outputLoad by vm.outputLoad.collectAsStateWithLifecycle()
    val trashLoad by vm.trashLoad.collectAsStateWithLifecycle()
    var renameOutput by remember { mutableStateOf<OutputRecord?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    fun rename(record:OutputRecord) { renameOutput=record;renameText=record.displayName }
    fun printAction(record:OutputRecord): (() -> Unit)? = if(onPrint!=null &&
        (record.mimeType=="application/pdf" || record.mimeType.startsWith("image/"))) ({onPrint(record)}) else null
    var section by rememberSaveable {mutableStateOf("outputs")}
    var search by rememberSaveable {mutableStateOf("")}
    var sort by rememberSaveable {mutableStateOf("date")}
    var type by rememberSaveable {mutableStateOf("all")}
    var group by rememberSaveable {mutableStateOf("none")}
    var sortPicker by rememberSaveable {mutableStateOf(false)}
    var selected by rememberSaveable(stateSaver=FileSelectionSaver) {mutableStateOf(setOf<String>())}
    var selecting by rememberSaveable {mutableStateOf(false)}
    var deleteIds by remember {mutableStateOf<Set<String>?>(null)}
    var purgeIds by remember {mutableStateOf<Set<String>?>(null)}
    var details by remember {mutableStateOf<OutputRecord?>(null)}
    var activeFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var unlockedFolderIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val dragState = rememberFileDragDropState()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val currentLockedFolderIds by rememberUpdatedState(settings.lockedFolderIds)
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                unlockedFolderIds = emptySet()
                if (activeFolderId != null && activeFolderId in currentLockedFolderIds) activeFolderId = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun openFolderSafely(folder: FileFolder) {
        if (folder.id !in settings.lockedFolderIds || folder.id in unlockedFolderIds) {
            activeFolderId = folder.id
            if (!dragState.isDragging) { selected = emptySet(); selecting = false }
            return
        }
        val act = findFragmentActivity(context)
        if (act == null) { vm.notifyMessage("VAULT_UNAVAILABLE"); return }
        com.jumlo.app.data.files.BiometricVaultGuard.authenticate(
            activity = act,
            title = folder.name,
            subtitle = context.getString(R.string.vault_unlock_subtitle),
            onSuccess = {
                unlockedFolderIds = unlockedFolderIds + folder.id
                activeFolderId = folder.id
                if (!dragState.isDragging) { selected = emptySet(); selecting = false }
            },
            onUnavailable = { vm.notifyMessage("VAULT_NO_BIOMETRIC") },
            onError = { vm.notifyMessage("VAULT_AUTH_FAILED") }
        )
    }
    var pageActionsOpen by remember { mutableStateOf(false) }
    var folderMenuTarget by remember { mutableStateOf<FileFolder?>(null) }
    fun openFolderMenuSafely(folder: FileFolder) {
        if (folder.id !in settings.lockedFolderIds || folder.id in unlockedFolderIds) {
            folderMenuTarget = folder
            return
        }
        val act = findFragmentActivity(context)
        if (act == null) { vm.notifyMessage("VAULT_UNAVAILABLE"); return }
        com.jumlo.app.data.files.BiometricVaultGuard.authenticate(
            activity = act,
            title = folder.name,
            subtitle = context.getString(R.string.vault_unlock_subtitle),
            onSuccess = { unlockedFolderIds = unlockedFolderIds + folder.id; folderMenuTarget = folder },
            onUnavailable = { vm.notifyMessage("VAULT_NO_BIOMETRIC") },
            onError = { vm.notifyMessage("VAULT_AUTH_FAILED") }
        )
    }
    var quickAddOpen by remember { mutableStateOf(false) }
    var fileToolsTarget by remember { mutableStateOf<OutputRecord?>(null) }
    var createFolderOpen by rememberSaveable { mutableStateOf(false) }
    var renameFolderTarget by remember { mutableStateOf<FileFolder?>(null) }
    var deleteFolderTarget by remember { mutableStateOf<FileFolder?>(null) }
    var folderNameInput by rememberSaveable { mutableStateOf("") }
    var moveOutputIds by remember { mutableStateOf<Set<String>?>(null) }
    var folderSearchOpen by rememberSaveable { mutableStateOf(false) }
    val activeFolder = remember(settings.fileFolders, activeFolderId) {
        settings.fileFolders.firstOrNull { it.id == activeFolderId }
    }
    val folderPath = remember(settings.fileFolders, activeFolder) {
        if (activeFolder == null) emptyList()
        else {
            val byId = settings.fileFolders.associateBy { it.id }
            val chain = ArrayList<FileFolder>()
            val visited = HashSet<String>()
            var cur: FileFolder? = activeFolder
            while (cur != null && visited.add(cur.id)) {
                chain.add(cur)
                cur = cur.parentId?.let { byId[it] }
            }
            chain.reverse()
            chain
        }
    }
    fun navigateToFolderId(targetId: String?) {
        if (targetId == null) {
            activeFolderId = null
            folderSearchOpen = false
            search = ""
            if (!dragState.isDragging) { selected = emptySet(); selecting = false }
        } else {
            val target = settings.fileFolders.firstOrNull { it.id == targetId } ?: return
            folderSearchOpen = false
            search = ""
            openFolderSafely(target)
        }
    }
    LaunchedEffect(settings.fileFolders, activeFolderId) {
        if (activeFolderId != null && settings.fileFolders.none { it.id == activeFolderId }) {
            activeFolderId = null
        }
    }
    LaunchedEffect(section) {
        if (section != "outputs" && section != "trash") section = "outputs"
    }
    LaunchedEffect(initialFilter) {
        val raw = initialFilter?.trim()?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        activeFolderId = null
        folderSearchOpen = false
        selected = emptySet()
        selecting = false
        when (raw.lowercase()) {
            "trash" -> { section = "trash"; type = "all" }
            "pdf" -> { section = "outputs"; type = "PDF" }
            "images", "image" -> { section = "outputs"; type = "Images" }
            "video", "videos" -> { section = "outputs"; type = "Video" }
            "audio" -> { section = "outputs"; type = "Audio" }
            "documents", "docs" -> { section = "outputs"; type = "Documents" }
            "favorites", "favourites" -> { section = "outputs"; type = "favorites" }
            else -> { section = "outputs"; type = "all" }
        }
    }
    androidx.activity.compose.BackHandler(enabled = section == "trash") {
        section = "outputs"
        selected = emptySet()
        selecting = false
    }
    LaunchedEffect(resetToRootEpoch) {
        if (resetToRootEpoch > 0) {
            activeFolderId = null
            section = "outputs"
            folderSearchOpen = false
            selecting = false
            selected = emptySet()
            listState.animateScrollToItem(0)
        }
    }
    androidx.activity.compose.BackHandler(enabled = activeFolderId != null) {
        activeFolderId = activeFolder?.parentId
        folderSearchOpen = false
        search = ""
        if (!dragState.isDragging) {
            selected = emptySet()
            selecting = false
        }
    }
    androidx.activity.compose.BackHandler(enabled = selecting) {
        selecting = false
        selected = emptySet()
    }
    val filesOwnsBack = selecting || activeFolderId != null || section == "trash"
    DisposableEffect(filesOwnsBack) {
        onBackInterceptChange?.invoke(filesOwnsBack)
        onDispose { onBackInterceptChange?.invoke(false) }
    }
    val folderCounts = remember(outputs, settings.outputFolderIds) {
        val counts = HashMap<String, Int>()
        outputs.forEach { out ->
            val fid = settings.outputFolderIds[out.id]
            if (fid != null) counts[fid] = (counts[fid] ?: 0) + 1
        }
        counts
    }
    fun shareOutputList(itemsToShare: List<OutputRecord>) {
        if (itemsToShare.isEmpty()) return
        val valid = itemsToShare.mapNotNull { item ->
            android.net.Uri.parse(item.uri).takeIf { u -> u.scheme.equals("content", ignoreCase = true) }?.let { item to it }
        }
        if (valid.isEmpty()) { vm.notifyMessage("NO_COMPATIBLE_APP_OR_ACCESS_LOST"); return }
        val uris = ArrayList(valid.map { it.second })
        try {
            val mime = valid.map { it.first.mimeType }.distinct().singleOrNull() ?: "*/*"
            val intent = if (uris.size == 1) {
                val (singleItem, singleUri) = valid.single()
                android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(android.content.Intent.EXTRA_STREAM, singleUri)
                    putExtra(android.content.Intent.EXTRA_TITLE, singleItem.displayName)
                    clipData = android.content.ClipData.newUri(context.contentResolver, singleItem.displayName, singleUri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                    type = mime
                    putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
                    clipData = android.content.ClipData.newUri(context.contentResolver, "Jumlo", uris.first()).also { clip ->
                        uris.drop(1).forEach { clip.addItem(android.content.ClipData.Item(it)) }
                    }
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            val chooser = android.content.Intent.createChooser(intent, null)
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(chooser)
        } catch (_: Exception) { vm.notifyMessage("NO_COMPATIBLE_APP_OR_ACCESS_LOST") }
    }
    fun handleFileDrop(ids: Set<String>, targetId: String) {
        haptics.dragEnd()
        vm.moveOutputsToFolder(ids, if (targetId == ROOT_DROP_TARGET_ID) null else targetId)
        selected = emptySet()
        selecting = false
    }
    LaunchedEffect(outputs,outputLoad) {
        if(outputLoad==DataLoadStatus.READY) {
            selected=selected.intersect(outputs.map {it.id}.toSet())
            if(outputs.isEmpty()) selecting=false
        }
    }
    val density=LocalDensity.current
    val visible=remember(outputs,search,sort,type,settings.favoriteOutputIds,settings.outputFolderIds,settings.lockedFolderIds,unlockedFolderIds,activeFolder) {
        outputs.filter { out ->
            val fid = settings.outputFolderIds[out.id]
            val hiddenInVault = fid != null && fid in settings.lockedFolderIds && fid !in unlockedFolderIds
            if (hiddenInVault) return@filter false
            val inScope = when {
                activeFolder != null -> settings.outputFolderIds[out.id] == activeFolder.id
                search.isNotBlank() || type != "all" -> true
                else -> settings.outputFolderIds[out.id] == null
            }
            inScope && out.displayName.contains(search,true) && (type=="all" || (type=="favorites" && out.id in settings.favoriteOutputIds) || StoragePaths.category(out.mimeType)==type)
        }.let {list->
            when(sort) {"name"->list.sortedBy {it.displayName.lowercase()};"size"->list.sortedByDescending {it.sizeBytes};"type"->list.sortedWith(compareBy({StoragePaths.category(it.mimeType)},{it.displayName}));else->list.sortedByDescending {it.createdAtEpochMs}}
        }
    }
    val visibleTrashed=remember(trashed,search) {
        trashed.filter {it.displayName.contains(search,true)}
    }
    val hasPrivateOutputs = remember(outputs) { outputs.any { it.publicUri == null } }
    val selectedOutputs = remember(visible, selected) { visible.filter { it.id in selected } }
    val selectedTrashed = remember(visibleTrashed, selected) { visibleTrashed.filter { it.id in selected } }
    val now = remember(visible) { System.currentTimeMillis() }
    val grouped = remember(visible, group, now) {
        when(group) {
            "type" -> visible.groupBy { StoragePaths.category(it.mimeType) }
            "date" -> visible.groupBy { fileDateGroup(it.createdAtEpochMs, now).name }
            else -> linkedMapOf("" to visible)
        }
    }
    var overflowMenuOpen by remember { mutableStateOf(false) }
    val outputFilters = remember { listOf("all","favorites","Video","Audio","PDF","Images","Documents") }
    val colors = MaterialTheme.colorScheme
    val pillars = QuietTheme.pillars
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = pillars.cardSurface,
        labelColor = colors.onSurfaceVariant,
        selectedContainerColor = colors.primaryContainer,
        selectedLabelColor = colors.onPrimaryContainer
    )
    val activeChipBorder = remember(colors.primary) { BorderStroke(1.dp, colors.primary.copy(alpha = 0.25f)) }
    val canDrag = settings.fileFolders.isNotEmpty() || activeFolder != null
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .testTag("files.screen")
            .onGloballyPositioned { dragState.containerOriginInRoot = it.positionInRoot() }
            .fileDragDropContainer(
                dragState = dragState,
                onHoverHaptic = { haptics.snap() },
                onDrop = ::handleFileDrop
            )
            .pointerInput(section) {
                detectTapGestures(
                    onLongPress = {
                        if (section == "outputs" && !dragState.isDragging) {
                            haptics.longPress()
                            pageActionsOpen = true
                        }
                    }
                )
            }
    ) {
    val paneWidth = minOf(maxWidth.value, 980f)
    val columns = when {
        paneWidth >= 600f -> if (density.fontScale >= 1.5f) 2 else 3
        density.fontScale >= 1.5f -> 1
        else -> 2
    }
    val groupedRows = remember(grouped, columns) { grouped.mapValues { (_, files) -> files.chunked(columns) } }
    val trashedRows = remember(visibleTrashed, columns) { visibleTrashed.chunked(columns) }
    val currentFolders = remember(settings.fileFolders, activeFolderId) {
        settings.fileFolders.filter { it.parentId == activeFolderId }
    }
    val folderRows = remember(currentFolders, columns) { currentFolders.chunked(columns) }
    Column(modifier = Modifier.fillMaxSize().align(Alignment.TopCenter)) {
    LazyColumn(
        state=listState,
        modifier=Modifier.weight(1f).fillMaxWidth(),
        contentPadding=PaddingValues(start=20.dp,end=20.dp,top=10.dp,
            bottom=QuietTheme.spacing.listBottomBuffer + if(section=="outputs" && selecting && selectedOutputs.isNotEmpty()) 88.dp else 0.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ) {
        if(selecting && (section=="outputs" || section=="trash")) {
            item("heading", contentType = "heading") {
                Text(stringResource(R.string.neon_selection_count,selected.size),
                    style=QuietTheme.typography.sectionTitle,
                    color=colors.primary,
                    modifier=Modifier.semantics { heading() })
            }
        }
        if (activeFolder == null && section == "outputs") {
            item("search") {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.weight(1f)) {
                        QuietSearchBar(search,stringResource(R.string.ui_search_files),{search=it;selected=emptySet()},{search="";selected=emptySet()})
                    }
                    Box {
                        FilledTonalIconButton(
                            onClick = { haptics.actionClick(); overflowMenuOpen = true },
                            modifier = Modifier.size(48.dp).clip(QuietTheme.shapes.inputField).pressFeedback(),
                            shape = QuietTheme.shapes.inputField,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = colors.surfaceContainer,
                                contentColor = colors.onSurfaceVariant
                            )
                        ) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.files_more_actions), modifier = Modifier.size(21.dp))
                        }
                        DropdownMenu(
                            expanded = overflowMenuOpen,
                            onDismissRequest = { overflowMenuOpen = false },
                            shape = QuietTheme.shapes.card,
                            containerColor = pillars.cardSurface
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.files_trash_title)) },
                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = colors.onSurfaceVariant) },
                                onClick = { overflowMenuOpen = false; haptics.selectionToggle(); section = "trash"; selecting = false; selected = emptySet() }
                            )
                        }
                    }
                }
            }
        } else if (section != "outputs") {
            item("sub-section-header") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = { haptics.actionClick(); section = "outputs"; selecting = false; selected = emptySet(); search = "" },
                            shape = CircleShape,
                            color = colors.surfaceContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.ui_outputs),
                                    tint = colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.files_trash_title),
                            style = QuietTheme.typography.sectionTitle,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).semantics { heading() }
                        )
                        if (trashed.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    haptics.selectionToggle()
                                    vm.setFileViewMode(if (settings.fileViewMode == FileViewMode.LIST) FileViewMode.GRID else FileViewMode.LIST)
                                },
                                modifier = Modifier.size(40.dp).testTag("files.trash.view")
                            ) {
                                Icon(
                                    if (settings.fileViewMode == FileViewMode.LIST) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                                    contentDescription = stringResource(R.string.redesign_file_view),
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (selecting) {
                                TextButton(
                                    onClick = { haptics.selectionToggle(); selected = visibleTrashed.map { it.id }.toSet() },
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text(stringResource(R.string.redesign_select_all), style = QuietTheme.typography.metadataStrong)
                                }
                            }
                            TextButton(
                                onClick = { haptics.selectionToggle(); selecting = !selecting; selected = emptySet() },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(
                                    stringResource(if (selecting) R.string.action_cancel else R.string.ui_select_files),
                                    style = QuietTheme.typography.metadataStrong
                                )
                            }
                            if (!selecting) {
                                var trashMenuOpen by remember { mutableStateOf(false) }
                                Box {
                                    Surface(
                                        onClick = { haptics.actionClick(); trashMenuOpen = true },
                                        shape = CircleShape,
                                        color = colors.surfaceContainer,
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Rounded.MoreVert,
                                                contentDescription = stringResource(R.string.files_more_actions),
                                                tint = colors.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = trashMenuOpen,
                                        onDismissRequest = { trashMenuOpen = false },
                                        shape = QuietTheme.shapes.card,
                                        containerColor = pillars.cardSurface
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.files_purge_trash), color = colors.error) },
                                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = colors.error) },
                                            onClick = { trashMenuOpen = false; purgeIds = trashed.map { it.id }.toSet() }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (trashed.isNotEmpty()) {
                        QuietSearchBar(search, stringResource(R.string.ui_search_files), { search = it; selected = emptySet() }, { search = ""; selected = emptySet() })
                    }
                }
            }
        }
        if(section=="outputs" && hasPrivateOutputs) item("public-store-warning", contentType = "notice") {
            Surface(color=MaterialTheme.colorScheme.errorContainer.copy(alpha=0.65f),shape=MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.redesign_save_fallback),modifier=Modifier.padding(14.dp),
                    color=MaterialTheme.colorScheme.onErrorContainer,style=QuietTheme.typography.metadata)
            }
        }
        when(section) {
            "outputs" -> {
                if (outputs.isNotEmpty() && activeFolder == null) item("filter", contentType = "filter") { Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        items(outputFilters,key={it},contentType={"filter-chip"}) {filter->
                            val active = type==filter
                            FilterChip(
                                selected=active,
                                onClick={haptics.selectionToggle();type=filter;selected=emptySet()},
                                shape=QuietTheme.shapes.pill,
                                colors=chipColors,
                                border=if(active) activeChipBorder else null,
                                label={Text(when(filter){"all"->stringResource(R.string.ui_all);"favorites"->stringResource(R.string.files_favorites);"Video"->stringResource(R.string.redesign_filter_video);"Audio"->stringResource(R.string.redesign_filter_audio);"PDF"->stringResource(R.string.redesign_pdf_type);"Images"->stringResource(R.string.redesign_filter_images);else->stringResource(R.string.redesign_filter_documents)},style=QuietTheme.typography.metadataStrong)}
                            )
                        }
                    }
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick={haptics.actionClick();sortPicker=true}, modifier=Modifier.heightIn(min=48.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.Sort,null,Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.modern_sort_group),style=QuietTheme.typography.metadataStrong)
                        }
                        val viewLabel=stringResource(if(settings.fileViewMode==FileViewMode.LIST) R.string.redesign_view_list else R.string.redesign_view_grid)
                        IconButton(onClick={haptics.selectionToggle();vm.setFileViewMode(if(settings.fileViewMode==FileViewMode.LIST) FileViewMode.GRID else FileViewMode.LIST)},
                            modifier=Modifier.testTag("files.view").semantics { stateDescription=viewLabel }) {
                            Icon(if(settings.fileViewMode==FileViewMode.LIST) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,stringResource(R.string.redesign_file_view))
                        }
                        Spacer(Modifier.weight(1f))
                        if(selecting) {
                            TextButton(onClick={haptics.selectionToggle();selected=visible.map { it.id }.toSet()}, modifier=Modifier.heightIn(min=48.dp)) {
                                Text(stringResource(R.string.redesign_select_all),style=QuietTheme.typography.metadataStrong)
                            }
                        }
                        TextButton(onClick={haptics.selectionToggle();selecting=!selecting;selected=emptySet()}, modifier=Modifier.heightIn(min=48.dp)) {
                            Text(stringResource(if(selecting) R.string.action_cancel else R.string.ui_select_files),style=QuietTheme.typography.metadataStrong)
                        }
                    }
                } }
                if (activeFolder != null) {
                    item("active-folder-bar", contentType = "active-folder-bar") {
                        FolderBreadcrumbBar(
                            folder = activeFolder,
                            itemCount = folderCounts[activeFolder.id] ?: 0,
                            dragState = dragState,
                            onBackToRoot = {
                                haptics.actionClick()
                                activeFolderId = activeFolder.parentId
                                folderSearchOpen = false; search = ""
                                if (!dragState.isDragging) { selected = emptySet(); selecting = false }
                            },
                            onOpenFolderActions = { folderMenuTarget = activeFolder },

                            onToggleSearch = { haptics.selectionToggle(); folderSearchOpen = !folderSearchOpen; if (!folderSearchOpen) search = "" },
                            onToggleSelect = { haptics.selectionToggle(); selecting = !selecting; selected = emptySet() },
                            selecting = selecting,
                            onSelectAll = { haptics.selectionToggle(); selected = visible.map { it.id }.toSet() },
                            ancestors = folderPath,
                            onNavigateToFolder = { targetId ->
                                haptics.navigationTab()
                                navigateToFolderId(targetId)
                            }
                        )
                    }
                    if (folderSearchOpen) {
                        item("folder-search", contentType = "search") {
                            QuietSearchBar(search, stringResource(R.string.ui_search_files), { search = it; selected = emptySet() }, { search = ""; selected = emptySet() })
                        }
                    }
                }
                if (currentFolders.isNotEmpty() && search.isBlank() && (activeFolder != null || type == "all")) {
                    items(folderRows, key = { "folder-row-" + it.joinToString("-") { f -> f.id } }, contentType = { "folderRow" }) { fRow ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            val reduced = com.jumlo.app.ui.theme.LocalReducedMotion.current
                            fRow.forEach { folder ->
                                val count = folderCounts[folder.id] ?: 0
                                val isHovered = dragState.isDragging && dragState.hoveredTargetId == folder.id
                                val hoverScale by animateFloatAsState(
                                    targetValue = if (isHovered && !reduced) 1.04f else 1f,
                                    animationSpec = if (reduced) snap() else spring(dampingRatio = 0.72f, stiffness = 420f),
                                    label = "folderCardDropScale"
                                )
                                Surface(
                                    modifier = Modifier.weight(1f)
                                        .graphicsLayer { scaleX = hoverScale; scaleY = hoverScale }
                                        .clip(QuietTheme.shapes.card)
                                        .fileDropTarget(folder.id, dragState)
                                        .pressFeedback()
                                        .combinedClickable(
                                            onClick = { haptics.actionClick(); openFolderSafely(folder) },
                                            onLongClick = { haptics.longPress(); openFolderMenuSafely(folder) }
                                        ),
                                    shape = QuietTheme.shapes.card,
                                    color = if (isHovered) colors.primaryContainer else pillars.cardSurface,
                                    border = BorderStroke(if (isHovered) 2.5.dp else 1.dp, if (isHovered) colors.primary else pillars.cardBorder),
                                    shadowElevation = if (isHovered) 8.dp else 0.dp
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        JumloFolderIcon(
                                            hasItems = count > 0,
                                            isHovered = isHovered,
                                            accent = if (isHovered) colors.primary else pillars.filesPrimary,
                                            modifier = Modifier.size(38.dp)
                                        )
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                folder.name,
                                                style = QuietTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, textDirection = TextDirection.Content),
                                                color = if (isHovered) colors.onPrimaryContainer else colors.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                if (isHovered) stringResource(R.string.files_folder_drop_here)
                                                else if (count == 0) stringResource(R.string.files_folder_empty_short)
                                                else stringResource(R.string.files_folder_count_items, count),
                                                style = QuietTheme.typography.metadata,
                                                color = if (isHovered) colors.primary else colors.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                        IconButton(onClick = { haptics.actionClick(); openFolderMenuSafely(folder) }, modifier = Modifier.size(40.dp)) {
                                            Icon(if (folder.id in settings.lockedFolderIds) Icons.Rounded.Lock else Icons.Rounded.MoreVert, stringResource(R.string.files_folder_actions_title), tint = if (folder.id in settings.lockedFolderIds) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                            repeat(columns - fRow.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                if(outputLoad!=DataLoadStatus.READY) item("outputs-state", contentType = "load-state") { DocumentLoadState(outputLoad,vm::reloadSavedData) }
                if(outputLoad==DataLoadStatus.READY && visible.isEmpty() && (currentFolders.isEmpty() || search.isNotEmpty() || type != "all")) item("empty", contentType = "empty") {
                    val filtered=search.isNotEmpty() || type!="all"
                    when {
                        activeFolder != null && !filtered -> QuietStateCard(
                            stringResource(R.string.files_folder_empty_title),
                            stringResource(R.string.files_folder_empty_body),
                            stringResource(R.string.import_device_files),
                            { onImportToFolder(activeFolder.id) },
                            icon = Icons.Rounded.FolderOpen
                        )
                        else -> QuietStateCard(
                            stringResource(if(filtered) R.string.upgrade_no_results else R.string.ui_recent_empty),
                            stringResource(if(filtered) R.string.upgrade_no_results_hint else R.string.redesign_storage_folder),
                            stringResource(if(filtered) R.string.files_state_reset_filters else R.string.import_device_files),
                            { if(filtered) { search=""; type="all"; selected=emptySet() } else onImport() },
                            icon=if(filtered) Icons.Rounded.Search else Icons.Rounded.FolderOpen
                        )
                    }
                }
                grouped.forEach { (groupKey, groupFiles) ->
                if(groupKey.isNotEmpty()) item("file-group-$groupKey", contentType = "group-header") {
                    Text(stringResource(when(groupKey) {
                        "TODAY" -> R.string.modern_today
                        "YESTERDAY" -> R.string.modern_yesterday
                        "EARLIER" -> R.string.modern_earlier
                        "Video" -> R.string.redesign_filter_video
                        "Audio" -> R.string.redesign_filter_audio
                        "Images" -> R.string.redesign_filter_images
                        "PDF" -> R.string.redesign_pdf_type
                        else -> R.string.redesign_filter_documents
                    }), style=QuietTheme.typography.sectionTitle,
                        modifier=Modifier.padding(top=8.dp).semantics { heading() })
                }
                if(settings.fileViewMode==FileViewMode.LIST) {
                    items(groupFiles,key={it.id},contentType={"output"}) { output ->
                        val isDragged = output.id in dragState.draggingIds
                        val dragMod = if (canDrag) Modifier
                            .alpha(if (isDragged) 0.45f else 1f)
                            .fileDragSource(
                                itemId = output.id,
                                itemLabel = output.displayName,
                                dragState = dragState,
                                resolveDragIds = { if (selecting && output.id in selected) selected else setOf(output.id) },
                                onDragStartHaptic = { haptics.dragStart() },
                                onHoverHaptic = { haptics.snap() },
                                onDrop = ::handleFileDrop
                            ) else Modifier
                        SwipeFileActions(output.displayName,enabled=!selecting && !dragState.isDragging,onShare={onShare(output)},
                            onRename={rename(output)},onTrash={vm.trashOutputs(listOf(output))},onPrint=printAction(output)) {
                            OutputRow(output,{if(selecting) selected=selected.symmetric(output.id) else onOpen(output)},
                                {onShare(output)},{onExport(output)},{fileToolsTarget=output},{deleteIds=setOf(output.id)},
                                if(selecting) output.id in selected else null,{selected=selected.symmetric(output.id)},
                                onDetails={details=output},isFavorite=output.id in settings.favoriteOutputIds,
                                onFavorite={vm.toggleOutputFavorite(output.id)},onPrint=printAction(output),onRename={rename(output)},
                                onLongClick=if(canDrag) null else ({haptics.selectionToggle();selecting=true;selected=selected+output.id}),
                                onMoveToFolder={moveOutputIds=setOf(output.id)},
                                rowModifier=dragMod)
                        }
                    }
                } else {
                    items(groupedRows[groupKey].orEmpty(),key={it.joinToString("-") {f->f.id}},contentType={"tileRow"}) {row->
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.fillMaxWidth()) {
                            row.forEach {output->
                                val cardHeight=206.dp
                                val isDragged = output.id in dragState.draggingIds
                                val gridDragMod = if (canDrag) Modifier
                                    .alpha(if (isDragged) 0.45f else 1f)
                                    .fileDragSource(
                                        itemId = output.id,
                                        itemLabel = output.displayName,
                                        dragState = dragState,
                                        resolveDragIds = { if (selecting && output.id in selected) selected else setOf(output.id) },
                                        onDragStartHaptic = { haptics.dragStart() },
                                        onHoverHaptic = { haptics.snap() },
                                        onDrop = ::handleFileDrop
                                    ) else Modifier
                                Surface(
                                    modifier=Modifier.weight(1f).heightIn(min=cardHeight)
                                        .clip(QuietTheme.shapes.card)
                                        .then(gridDragMod)
                                        .pressFeedback()
                                        .combinedClickable(onClick={haptics.actionClick();if(selecting) selected=selected.symmetric(output.id) else onOpen(output)},
                                            onLongClick=if(canDrag) null else ({haptics.selectionToggle();selecting=true;selected=selected+output.id}))
                                        .semantics { if(selecting) { this.selected=output.id in selected; role=Role.Checkbox } },
                                    shape=QuietTheme.shapes.card,
                                    color=if(selecting && output.id in selected) MaterialTheme.colorScheme.primaryContainer
                                        else pillars.cardSurface,
                                    border=BorderStroke(
                                        1.dp,
                                        if(selecting && output.id in selected) MaterialTheme.colorScheme.primary.copy(alpha=0.55f)
                                        else pillars.cardBorder
                                    ),
                                    shadowElevation=0.dp
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                        Box(Modifier.fillMaxWidth()
                                            .height(104.dp)) {
                                            NeonOutputPreview(output, Modifier.fillMaxSize())
                                            if(selecting) Icon(
                                                if(output.id in selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                                stringResource(R.string.ui_select_files),
                                                tint=MaterialTheme.colorScheme.primary,
                                                modifier=Modifier.align(Alignment.TopEnd).padding(7.dp).size(27.dp)
                                            )
                                        }
                                        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                            Text(output.displayName,style=QuietTheme.typography.bodyMedium.copy(fontWeight=FontWeight.SemiBold,textDirection=TextDirection.Content),maxLines=1,overflow=TextOverflow.Ellipsis)
                                            val originalBytes=output.metadata.originalSizeBytes
                                            val savedPercent=if(originalBytes!=null && originalBytes>output.sizeBytes && originalBytes>0L)
                                                (((originalBytes-output.sizeBytes).toDouble()*100.0)/originalBytes).toInt().coerceIn(0,99)
                                                else null
                                            Text(
                                                if(savedPercent!=null)
                                                    stringResource(R.string.visual_edit_saved_percent,savedPercent)+" · "+formatOutputSizeSummary(output)
                                                else formatOutputSizeSummary(output),
                                                style=QuietTheme.typography.metadata,
                                                color=if(savedPercent!=null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines=1,overflow=TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(fileDateLabel(output.createdAtEpochMs), style=QuietTheme.typography.metadata,
                                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                                        if(!selecting) {
                                            var actionsExpanded by remember(output.id) { mutableStateOf(false) }
                                            Row(horizontalArrangement=Arrangement.End,modifier=Modifier.fillMaxWidth()) {
                                                IconButton(onClick={haptics.selectionToggle();vm.toggleOutputFavorite(output.id)},modifier=Modifier.pressFeedback().size(48.dp)) {
                                                    val isFav = output.id in settings.favoriteOutputIds
                                                    Icon(if(isFav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                                        stringResource(if(isFav) R.string.files_unmark_favorite else R.string.files_mark_favorite),
                                                        tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(20.dp))
                                                }
                                                Box {
                                                    IconButton(onClick={haptics.actionClick();actionsExpanded=true},modifier=Modifier.pressFeedback().size(48.dp)) {
                                                        Icon(Icons.Rounded.MoreVert,stringResource(R.string.ui_more_actions),Modifier.size(22.dp))
                                                    }
                                                    DropdownMenu(expanded=actionsExpanded,onDismissRequest={actionsExpanded=false}) {
                                                        printAction(output)?.let { print -> DropdownMenuItem(text={Text(stringResource(R.string.upgrade_print))},onClick={actionsExpanded=false;print()}) }
                                                        DropdownMenuItem(text={Text(stringResource(R.string.files_move_to_folder))},onClick={actionsExpanded=false;moveOutputIds=setOf(output.id)})
                                                        DropdownMenuItem(text={Text(stringResource(R.string.action_rename))},onClick={actionsExpanded=false;rename(output)})
                                                        DropdownMenuItem(text={Text(stringResource(R.string.action_share))},onClick={actionsExpanded=false;onShare(output)})
                                                        DropdownMenuItem(text={Text(stringResource(R.string.action_details))},onClick={actionsExpanded=false;details=output})
                                                        DropdownMenuItem(text={Text(stringResource(R.string.action_save_as))},onClick={actionsExpanded=false;onExport(output)})
                                                        DropdownMenuItem(text={Text(stringResource(R.string.action_use_as_input))},onClick={actionsExpanded=false;fileToolsTarget=output})
                                                        DropdownMenuItem(text={Text(stringResource(R.string.action_move_to_trash))},onClick={actionsExpanded=false;deleteIds=setOf(output.id)})
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            repeat(columns-row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                }
            }
            "trash" -> {
                if (trashed.isNotEmpty()) {
                    item("trash-notice", contentType = "notice") {
                        Text(
                            text = stringResource(R.string.ui_trash_notice),
                            style = QuietTheme.typography.metadata,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
                if (trashLoad != DataLoadStatus.READY) {
                    item("trash-state", contentType = "load-state") { DocumentLoadState(trashLoad, vm::reloadTrash) }
                } else {
                    if (trashed.isEmpty()) {
                        item("trash-empty", contentType = "empty") {
                            QuietStateCard(
                                stringResource(R.string.files_trash_title),
                                stringResource(R.string.files_trash_empty),
                                icon = Icons.Rounded.DeleteOutline
                            )
                        }
                    } else if (visibleTrashed.isEmpty()) {
                        item("trash-filtered-empty", contentType = "empty") {
                            QuietStateCard(
                                stringResource(R.string.upgrade_no_results),
                                stringResource(R.string.upgrade_no_results_hint),
                                stringResource(R.string.files_state_clear_search),
                                { search = "" },
                                icon = Icons.Rounded.Search
                            )
                        }
                    } else if (settings.fileViewMode == FileViewMode.LIST) {
                        items(visibleTrashed, key = { it.id }, contentType = { "trashed" }) { entry ->
                            FileMenuRow(
                                name = entry.displayName,
                                metadata = formatOutputSizeSummary(entry) + " · " + fileDateLabel(entry.createdAtEpochMs),
                                onOpen = {
                                    if (selecting) {
                                        haptics.selectionToggle()
                                        selected = selected.symmetric(entry.id)
                                    } else {
                                        haptics.actionClick()
                                        vm.restoreOutput(entry.id)
                                    }
                                },
                                icon = fileIconFor(entry.displayName.substringAfterLast('.', "")),
                                selection = if (selecting) entry.id in selected else null,
                                onSelect = { haptics.selectionToggle(); selected = selected.symmetric(entry.id) },
                                showOverflow = !selecting,
                                onLongClick = { haptics.selectionToggle(); selecting = true; selected = selected + entry.id },
                                leadingPreview = { NeonOutputPreview(entry, Modifier.size(44.dp)) }
                            ) { close ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_restore)) },
                                    leadingIcon = { Icon(Icons.Rounded.Undo, null, tint = colors.primary) },
                                    onClick = { close(); haptics.actionClick(); vm.restoreOutput(entry.id) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ui_delete_forever), color = colors.error) },
                                    leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = colors.error) },
                                    onClick = { close(); haptics.secondaryAction(); purgeIds = setOf(entry.id) }
                                )
                            }
                        }
                    } else {
                        items(trashedRows, key = { "trash-row-" + it.joinToString("-") { f -> f.id } }, contentType = { "trashedTileRow" }) { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                row.forEach { entry ->
                                    val isSel = selecting && entry.id in selected
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 200.dp)
                                            .clip(QuietTheme.shapes.card)
                                            .pressFeedback()
                                            .combinedClickable(
                                                onClick = {
                                                    if (selecting) {
                                                        haptics.selectionToggle()
                                                        selected = selected.symmetric(entry.id)
                                                    } else {
                                                        haptics.actionClick()
                                                        vm.restoreOutput(entry.id)
                                                    }
                                                },
                                                onLongClick = { haptics.selectionToggle(); selecting = true; selected = selected + entry.id }
                                            )
                                            .semantics { if (selecting) { this.selected = isSel; role = Role.Checkbox } },
                                        shape = QuietTheme.shapes.card,
                                        color = if (isSel) colors.primaryContainer else pillars.cardSurface,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSel) colors.primary.copy(alpha = 0.55f) else pillars.cardBorder
                                        ),
                                        shadowElevation = 0.dp
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Box(Modifier.fillMaxWidth().height(104.dp)) {
                                                NeonOutputPreview(entry, Modifier.fillMaxSize())
                                                if (selecting) {
                                                    Icon(
                                                        if (isSel) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                                        contentDescription = stringResource(R.string.ui_select_files),
                                                        tint = colors.primary,
                                                        modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).size(27.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                entry.displayName,
                                                style = QuietTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, textDirection = TextDirection.Content),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                formatOutputSizeSummary(entry),
                                                style = QuietTheme.typography.metadata,
                                                color = colors.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    fileDateLabel(entry.createdAtEpochMs),
                                                    style = QuietTheme.typography.metadata,
                                                    color = colors.onSurfaceVariant
                                                )
                                                if (!selecting) {
                                                    var itemMenuOpen by remember(entry.id) { mutableStateOf(false) }
                                                    Box {
                                                        IconButton(
                                                            onClick = { haptics.actionClick(); itemMenuOpen = true },
                                                            modifier = Modifier.pressFeedback().size(36.dp)
                                                        ) {
                                                            Icon(Icons.Rounded.MoreVert, stringResource(R.string.ui_more_actions), Modifier.size(20.dp))
                                                        }
                                                        DropdownMenu(expanded = itemMenuOpen, onDismissRequest = { itemMenuOpen = false }) {
                                                            DropdownMenuItem(
                                                                text = { Text(stringResource(R.string.action_restore)) },
                                                                onClick = { itemMenuOpen = false; haptics.actionClick(); vm.restoreOutput(entry.id) }
                                                            )
                                                            DropdownMenuItem(
                                                                text = { Text(stringResource(R.string.ui_delete_forever), color = colors.error) },
                                                                onClick = { itemMenuOpen = false; haptics.secondaryAction(); purgeIds = setOf(entry.id) }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
    }
    if(section=="outputs" && selecting && selectedOutputs.isNotEmpty()) {

        val onlyPdfs = remember(selectedOutputs) {
            selectedOutputs.size >= 2 && selectedOutputs.all { it.mimeType=="application/pdf" }
        }
        val onlyImages = remember(selectedOutputs) {
            selectedOutputs.isNotEmpty() && selectedOutputs.all { it.mimeType.startsWith("image/") }
        }
        Surface(
            modifier=Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .widthIn(max=520.dp)
                .padding(start=16.dp,end=16.dp,bottom=82.dp),
            color=pillars.dockSurface.copy(alpha=0.94f),
            shape=QuietTheme.shapes.dockPill,
            border=BorderStroke(1.dp,pillars.dockBorder),
            shadowElevation=10.dp
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=12.dp,vertical=7.dp),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                NeonBatchAction(Icons.Rounded.Share,stringResource(R.string.action_share),{
                    haptics.actionClick()
                    shareOutputList(selectedOutputs)
                })
                if(selectedOutputs.size==1) printAction(selectedOutputs.single())?.let { print ->
                    NeonBatchAction(Icons.Rounded.Print,stringResource(R.string.upgrade_print),{print()})
                }
                NeonBatchAction(Icons.Rounded.Folder,stringResource(R.string.files_move_to_folder),{
                    haptics.actionClick()
                    moveOutputIds=selectedOutputs.map { it.id }.toSet()
                })
                NeonBatchAction(Icons.Rounded.FolderZip,stringResource(R.string.files_bundle_selected,selectedOutputs.size),{
                    haptics.actionClick()
                    onOutputWorkflow(selectedOutputs,"file_07")
                    selected=emptySet();selecting=false
                })
                if(onlyPdfs) NeonBatchAction(Icons.Rounded.MergeType,
                    stringResource(R.string.ui_merge_pdf),{
                        haptics.actionClick()
                        onOutputWorkflow(selectedOutputs,"pdf_03")
                        selected=emptySet();selecting=false
                    })
                else if(onlyImages) NeonBatchAction(Icons.Rounded.PictureAsPdf,
                    stringResource(R.string.ui_images_pdf),{
                        haptics.actionClick()
                        onOutputWorkflow(selectedOutputs,"pdf_02")
                        selected=emptySet();selecting=false
                    })
                NeonBatchAction(Icons.Rounded.DeleteOutline,stringResource(R.string.action_delete),{
                    haptics.secondaryAction()
                    deleteIds=selectedOutputs.map { it.id }.toSet()
                },destructive=true)
            }
        }
    }
    if (section == "trash" && selecting && selectedTrashed.isNotEmpty()) {
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .widthIn(max = 420.dp)
                .padding(start = 16.dp, end = 16.dp, bottom = 82.dp),
            color = pillars.dockSurface.copy(alpha = 0.94f),
            shape = QuietTheme.shapes.dockPill,
            border = BorderStroke(1.dp, pillars.dockBorder),
            shadowElevation = 10.dp
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                NeonBatchAction(Icons.Rounded.Undo, stringResource(R.string.action_restore), {
                    haptics.actionClick()
                    vm.restoreOutputs(selectedTrashed.map { it.id })
                    selected = emptySet()
                    selecting = false
                })
                NeonBatchAction(Icons.Rounded.DeleteOutline, stringResource(R.string.ui_delete_forever), {
                    haptics.secondaryAction()
                    purgeIds = selectedTrashed.map { it.id }.toSet()
                }, destructive = true)
            }
        }
    }
    if (!selecting && section == "outputs") {
        FloatingActionButton(
            onClick = { haptics.actionClick(); quickAddOpen = true },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = QuietTheme.spacing.listBottomBuffer - 12.dp)
                .pressFeedback()
                .testTag("files.fab.add")
        ) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.files_quick_add_title))
        }
    }
    FloatingDragGhost(dragState = dragState, modifier = Modifier.align(AbsoluteAlignment.TopLeft))
    }
    if(sortPicker) AlertDialog(onDismissRequest={sortPicker=false},title={Text(stringResource(R.string.modern_sort_group),modifier=Modifier.semantics { heading() })},text={Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.redesign_sort), style=QuietTheme.typography.metadataStrong, modifier=Modifier.semantics { heading() })
        Column(Modifier.selectableGroup()) {
            FileSortOptions.forEach { (key,label)->
                Row(Modifier.fillMaxWidth().heightIn(min=52.dp).selectable(sort==key,role=Role.RadioButton,onClick={sort=key}),verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected=sort==key,onClick=null)
                    Text(stringResource(label),Modifier.padding(start=12.dp))
                }
            }
        }
        Text(stringResource(R.string.modern_group_by), style=QuietTheme.typography.metadataStrong, modifier=Modifier.semantics { heading() })
        Column(Modifier.selectableGroup()) {
            FileGroupOptions.forEach { (key,label) ->
                Row(Modifier.fillMaxWidth().heightIn(min=52.dp).selectable(group==key,role=Role.RadioButton,onClick={group=key}),verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected=group==key,onClick=null)
                    Text(stringResource(label),Modifier.padding(start=12.dp))
                }
            }
        }
    }},confirmButton={TextButton(onClick={sortPicker=false}) {Text(stringResource(R.string.account_done))}})
    deleteIds?.let { ids -> AlertDialog(onDismissRequest={deleteIds=null},title={Text(stringResource(R.string.dialog_delete_title),modifier=Modifier.semantics { heading() })},text={Text(stringResource(R.string.redesign_delete_confirm))},confirmButton={
        TextButton(onClick={haptics.secondaryAction();vm.trashOutputs(outputs.filter {it.id in ids});selected=emptySet();selecting=false;deleteIds=null}) {Text(stringResource(R.string.action_delete))}
    },dismissButton={TextButton(onClick={deleteIds=null}) {Text(stringResource(R.string.account_cancel))}})}
    renameOutput?.let { output ->
        val nameValid=remember(renameText,output.displayName) {
            runCatching { com.jumlo.app.core.DocumentNamePolicy.rename(output.displayName,renameText) }.isSuccess
        }
        AlertDialog(onDismissRequest={renameOutput=null},title={Text(stringResource(R.string.action_rename),modifier=Modifier.semantics { heading() })},
            text={TextField(renameText,{renameText=it},singleLine=true,
                label={Text(stringResource(R.string.upgrade_new_name))},
                textStyle=MaterialTheme.typography.bodyLarge.copy(textDirection=TextDirection.Content),
                isError=!nameValid,
                supportingText=if(!nameValid) {{Text(stringResource(R.string.files_rename_invalid_name))}} else null)},
            confirmButton={TextButton(onClick={vm.renameOutput(output,renameText);renameOutput=null},enabled=nameValid) {Text(stringResource(R.string.account_done))}},
            dismissButton={TextButton(onClick={renameOutput=null}) {Text(stringResource(R.string.action_cancel))}})
    }
    details?.let { output -> OutputDetailsDialog(output = output, onDismiss = { details = null }) }
    purgeIds?.let {ids->AlertDialog(onDismissRequest={purgeIds=null},title={Text(stringResource(R.string.ui_purge_title),modifier=Modifier.semantics { heading() })},text={Text(stringResource(R.string.ui_purge_body,ids.size))},confirmButton={TextButton(onClick={purgeIds=null;selected=emptySet();selecting=false;vm.purgeTrashIds(ids)}) {Text(stringResource(R.string.ui_delete_forever))}},dismissButton={TextButton(onClick={purgeIds=null}) {Text(stringResource(R.string.action_cancel))}})}
    if (quickAddOpen) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { quickAddOpen = false }) {
            Surface(
                shape = QuietTheme.shapes.heroCard,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, QuietTheme.pillars.cardBorder),
                shadowElevation = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = activeFolder?.name ?: stringResource(R.string.files_quick_add_title),
                        style = QuietTheme.typography.sectionTitle,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(Modifier.height(4.dp))
                    if (onScan != null) {
                        SheetActionRow(
                            icon = Icons.Rounded.DocumentScanner,
                            title = stringResource(R.string.modern_scan),
                            onClick = { quickAddOpen = false; haptics.actionClick(); onScan() }
                        )
                    }
                    SheetActionRow(
                        icon = Icons.Rounded.Add,
                        title = stringResource(R.string.files_import_action),
                        onClick = { quickAddOpen = false; haptics.actionClick(); onImportToFolder(activeFolderId) }
                    )
                    SheetActionRow(
                        icon = Icons.Rounded.Folder,
                        title = stringResource(R.string.files_folder_create_title),
                        onClick = { quickAddOpen = false; haptics.actionClick(); folderNameInput = ""; createFolderOpen = true }
                    )
                }
            }
        }
    }
    fileToolsTarget?.let { target ->
        val adaptiveToolIds = remember(target.mimeType) {
            when {
                target.mimeType == "application/pdf" -> listOf(
                    "pdf_signature", "pdf_compress", "pdf_ocr", "pdf_to_docx", "pdf_to_pptx",
                    "pdf_long_image", "pdf_protect", "pdf_stamp", "pdf_privacy_redact", "pdf_05", "pdf_04"
                )
                target.mimeType.startsWith("image/") -> listOf(
                    "pdf_02", "img_04", "img_02", "img_watermark", "pdf_ocr"
                )
                target.mimeType.contains("word") || target.mimeType.contains("spreadsheet") ||
                    target.mimeType.contains("presentation") || target.mimeType.contains("officedocument") -> listOf(
                    "office_to_pdf", "file_07"
                )
                target.mimeType.contains("zip") || target.mimeType.contains("archive") -> listOf(
                    "file_08", "file_07"
                )
                else -> listOf("file_07", "file_batch_rename")
            }
        }
        val adaptiveTools = remember(adaptiveToolIds) {
            adaptiveToolIds.mapNotNull { id ->
                if (!com.jumlo.app.data.jobs.CapabilityRegistry.visible(id) || !com.jumlo.app.data.jobs.CapabilityRegistry.enabled(id)) null
                else com.jumlo.app.data.jobs.CapabilityRegistry.tools.firstOrNull { it.id == id }
            }
        }
        androidx.compose.ui.window.Dialog(onDismissRequest = { fileToolsTarget = null }) {
            Surface(
                shape = QuietTheme.shapes.heroCard,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, QuietTheme.pillars.cardBorder),
                shadowElevation = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = target.displayName,
                        style = QuietTheme.typography.sectionTitle.copy(textDirection = TextDirection.Content),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = stringResource(R.string.files_adaptive_tools_subtitle),
                        style = QuietTheme.typography.metadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        adaptiveTools.forEach { tool ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(QuietTheme.shapes.inputField)
                                    .pressFeedback()
                                    .clickable {
                                        haptics.actionClick()
                                        fileToolsTarget = null
                                        onOutputWorkflow(listOf(target), tool.id)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ToolIcon(
                                    toolId = tool.id,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = if (settings.language.isRtl) tool.titleAr else tool.titleEn,
                                    style = QuietTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )

                            }
                        }
                    }
                }
            }
        }
    }
    if (pageActionsOpen) {
        PageQuickActionsSheet(
            canSelect = outputs.isNotEmpty(),
            onDismiss = { pageActionsOpen = false },
            onCreateFolder = { folderNameInput = ""; createFolderOpen = true },
            onImport = { onImportToFolder(activeFolderId) },
            onStartSelect = { selecting = true; selected = emptySet() },
            onOpenSort = { sortPicker = true }
        )
    }
    folderMenuTarget?.let { folder ->
        val folderOutputs = remember(outputs, settings.outputFolderIds, folder.id) {
            outputs.filter { settings.outputFolderIds[it.id] == folder.id }
        }
        val folderPdfs = remember(folderOutputs) { folderOutputs.filter { it.mimeType == "application/pdf" } }
        val folderImages = remember(folderOutputs) { folderOutputs.filter { it.mimeType.startsWith("image/") } }
        FolderActionsSheet(
            folder = folder,
            folderOutputs = folderOutputs,
            onDismiss = { folderMenuTarget = null },
            onZipFolder = { haptics.actionClick(); onOutputWorkflow(folderOutputs, "file_07") },
            onMergePdfs = { haptics.actionClick(); onOutputWorkflow(folderPdfs, "pdf_03") },
            onImagesToPdf = { haptics.actionClick(); onOutputWorkflow(folderImages, "pdf_02") },
            onCompressImages = { haptics.actionClick(); onOutputWorkflow(folderImages, "img_04") },
            onShareAll = { haptics.actionClick(); shareOutputList(folderOutputs) },
            onRename = { folderNameInput = folder.name; renameFolderTarget = folder },
            onDelete = { deleteFolderTarget = folder },
            isLocked = folder.id in settings.lockedFolderIds,
            onToggleLock = { haptics.actionClick(); vm.toggleFolderLock(folder.id); folderMenuTarget = null },
            onImportToFolder = { haptics.actionClick(); onImportToFolder(folder.id) }
        )
    }
    if (createFolderOpen) {
        FolderNameDialog(
            title = stringResource(R.string.files_folder_create_title),
            confirmLabel = stringResource(R.string.files_folder_create_action),
            value = folderNameInput,
            onValueChange = { folderNameInput = it },
            onConfirm = {
                haptics.actionClick()
                vm.createFileFolder(folderNameInput, parentId = activeFolderId)
                createFolderOpen = false
                folderNameInput = ""
            },
            onDismiss = { createFolderOpen = false }
        )
    }
    renameFolderTarget?.let { folder ->
        FolderNameDialog(
            title = stringResource(R.string.files_folder_rename_title),
            confirmLabel = stringResource(R.string.account_done),
            value = folderNameInput,
            onValueChange = { folderNameInput = it },
            onConfirm = {
                haptics.actionClick()
                vm.renameFileFolder(folder.id, folderNameInput)
                renameFolderTarget = null
                folderNameInput = ""
            },
            onDismiss = { renameFolderTarget = null }
        )
    }
    deleteFolderTarget?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleteFolderTarget = null },
            shape = QuietTheme.shapes.heroCard,
            title = { Text(stringResource(R.string.files_folder_delete_title), modifier = Modifier.semantics { heading() }) },
            text = { Text(stringResource(R.string.files_folder_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    haptics.secondaryAction()
                    if (activeFolderId == folder.id) activeFolderId = folder.parentId
                    vm.deleteFileFolder(folder.id)
                    deleteFolderTarget = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteFolderTarget = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
    moveOutputIds?.let { ids ->
        MoveToFolderDialog(
            folders = settings.fileFolders,
            currentFolderId = activeFolderId,
            onSelectFolder = { targetId ->
                haptics.actionClick()
                vm.moveOutputsToFolder(ids, targetId)
                moveOutputIds = null
                selected = emptySet()
                selecting = false
            },
            onCreateNewFolder = {
                moveOutputIds = null
                folderNameInput = ""
                createFolderOpen = true
            },
            onDismiss = { moveOutputIds = null }
        )
    }
}

@Composable
private fun RowScope.NeonBatchAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onAction: () -> Unit,
    destructive: Boolean = false
) {
    Column(Modifier.widthIn(min = 68.dp).pressFeedback(),
        horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onAction) {
            Icon(icon, label, Modifier.size(23.dp),
                tint = if(destructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary)
        }
        Text(label, style = QuietTheme.typography.metadata,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val FileSelectionSaver = listSaver<Set<String>, String>(save={it.toList()},restore={it.toSet()})

private val FileSortOptions = listOf(
    "date" to R.string.redesign_sort_date,
    "size" to R.string.redesign_sort_size,
    "name" to R.string.redesign_sort_name,
    "type" to R.string.redesign_sort_type
)

private val FileGroupOptions = listOf(
    "none" to R.string.modern_group_none,
    "date" to R.string.redesign_sort_date,
    "type" to R.string.redesign_sort_type
)

private fun Set<String>.symmetric(id:String):Set<String> = if(id in this) this-id else this+id

private fun findFragmentActivity(context: android.content.Context): androidx.fragment.app.FragmentActivity? =
    generateSequence<android.content.Context>(context) { c -> (c as? android.content.ContextWrapper)?.baseContext?.takeUnless { it === c } }
        .filterIsInstance<androidx.fragment.app.FragmentActivity>().firstOrNull()

@Composable internal fun ProviderFileRow(
    entry: DocumentEntry,
    destinationWritable: Boolean,
    onBrowse: () -> Unit,
    onOpen: () -> Unit,
    onImport: () -> Unit,
    onCopy: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val showMutations = !entry.isDirectory && (entry.canRename || entry.canDelete)
    FileMenuRow(entry.name,
        if(entry.isDirectory) stringResource(R.string.files_folder_label) else formatBytes(entry.sizeBytes),
        if(entry.readable) ({if(entry.isDirectory) onBrowse() else onOpen()}) else null,
        if(entry.isDirectory) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
        showOverflow=entry.readable || showMutations
    ) { close ->
        if(entry.isDirectory && entry.readable) DropdownMenuItem(text={Text(stringResource(R.string.files_browse_inside))},onClick={close();onBrowse()})
        if(!entry.isDirectory && entry.readable) {
            DropdownMenuItem(text={Text(stringResource(R.string.action_open))},onClick={close();onOpen()})
            DropdownMenuItem(text={Text(stringResource(R.string.files_import_action))},onClick={close();onImport()})
            if(destinationWritable) DropdownMenuItem(text={Text(stringResource(R.string.action_copy))},onClick={close();onCopy()})
        }
        if(!entry.isDirectory) {
            if(entry.canRename) DropdownMenuItem(text={Text(stringResource(R.string.action_rename))},onClick={close();onRename()})
            if(entry.canDelete) DropdownMenuItem(text={Text(stringResource(R.string.files_delete_original))},onClick={close();onDelete()})
        }
    }
}

@Composable internal fun ProviderRenameDialog(
    sourceUri: String,
    enteredName: String,
    listing: DocumentListing?,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val preview = remember(listing,sourceUri,enteredName) {previewDocumentRename(listing,sourceUri,enteredName)}
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text(stringResource(R.string.dialog_rename_title),modifier=Modifier.semantics { heading() })},
        text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            TextField(enteredName,onNameChange,singleLine=true,modifier=Modifier.fillMaxWidth(),
                label={Text(stringResource(R.string.files_rename_new_name))},
                textStyle=MaterialTheme.typography.bodyLarge.copy(textDirection=TextDirection.Content),
                isError=preview.errorCode!=null)
            preview.name?.let {name->
                Text(stringResource(R.string.files_rename_proposed),style=QuietTheme.typography.metadataStrong)
                TechnicalText(name)
            }
            val message = when(preview.errorCode) {
                "INVALID_FILENAME" -> R.string.files_rename_invalid_name
                "DESTINATION_EXISTS" -> R.string.files_rename_exists
                "ACCESS_LOST" -> R.string.msg_access_lost
                "PROVIDER_RENAME_UNAVAILABLE" -> R.string.msg_provider_unavailable
                else -> if(!preview.canConfirm) R.string.files_rename_unchanged else null
            }
            message?.let {Text(stringResource(it),style=QuietTheme.typography.metadata,
                color=if(preview.errorCode!=null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)}
        }},
        confirmButton={TextButton(enabled=preview.canConfirm,onClick={
            if(preview.canConfirm) preview.name?.let(onConfirm)
        }) {Text(stringResource(R.string.action_rename))}},
        dismissButton={TextButton(onClick=onDismiss) {Text(stringResource(R.string.action_cancel))}}
    )
}
