package com.jumlo.app.ui.files

import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.util.rememberAppHaptics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.ExperimentalFoundationApi
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
import com.jumlo.app.ui.icons.automirrored.rounded.Sort
import com.jumlo.app.ui.icons.automirrored.rounded.ViewList
import com.jumlo.app.ui.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
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
    onOutputWorkflow:(List<OutputRecord>,String)->Unit,onSourceWorkflow:(List<SourceRef>,String)->Unit,onPrint:((OutputRecord)->Unit)?=null,onPrintSource:((String)->Unit)?=null,onChangeSaveFolder:()->Unit=onFolder,onStorage:()->Unit={}) {
    val haptics=rememberAppHaptics()
    val context=LocalContext.current
    val imported by vm.imported.collectAsStateWithLifecycle()
    val outputs by vm.outputs.collectAsStateWithLifecycle()
    val trashed by vm.trashed.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val tree by vm.tree.collectAsStateWithLifecycle()
    val outputLoad by vm.outputLoad.collectAsStateWithLifecycle()
    val sourceLoad by vm.sourceLoad.collectAsStateWithLifecycle()
    val trashLoad by vm.trashLoad.collectAsStateWithLifecycle()
    val treeLoad by vm.treeLoad.collectAsStateWithLifecycle()
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
    var selectedSources by rememberSaveable(stateSaver=FileSelectionSaver) { mutableStateOf(setOf<String>()) }
    var selectingSources by rememberSaveable { mutableStateOf(false) }
    var deleteIds by remember {mutableStateOf<Set<String>?>(null)}
    var purgeIds by remember {mutableStateOf<Set<String>?>(null)}
    var details by remember {mutableStateOf<OutputRecord?>(null)}
    var activeFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var pageActionsOpen by remember { mutableStateOf(false) }
    var folderMenuTarget by remember { mutableStateOf<FileFolder?>(null) }
    var createFolderOpen by rememberSaveable { mutableStateOf(false) }
    var renameFolderTarget by remember { mutableStateOf<FileFolder?>(null) }
    var deleteFolderTarget by remember { mutableStateOf<FileFolder?>(null) }
    var folderNameInput by rememberSaveable { mutableStateOf("") }
    var moveOutputIds by remember { mutableStateOf<Set<String>?>(null) }
    val dragState = rememberFileDragDropState()
    val activeFolder = remember(settings.fileFolders, activeFolderId) {
        settings.fileFolders.firstOrNull { it.id == activeFolderId }
    }
    LaunchedEffect(settings.fileFolders, activeFolderId) {
        if (activeFolderId != null && settings.fileFolders.none { it.id == activeFolderId }) {
            activeFolderId = null
        }
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
        val uris = ArrayList(itemsToShare.map { android.net.Uri.parse(it.publicUri ?: it.uri) })
        try {
            val mime = itemsToShare.map { it.mimeType }.distinct().singleOrNull() ?: "*/*"
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                type = mime
                putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
                clipData = android.content.ClipData.newUri(context.contentResolver, "Jumlo", uris.first()).also { clip ->
                    uris.drop(1).forEach { clip.addItem(android.content.ClipData.Item(it)) }
                }
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, null))
        } catch (_: Exception) { vm.notifyMessage("NO_COMPATIBLE_APP_OR_ACCESS_LOST") }
    }
    fun handleFileDrop(ids: Set<String>, targetId: String) {
        haptics.dragEnd()
        if (targetId == ROOT_DROP_TARGET_ID) {
            vm.moveOutputsToFolder(ids, null)
        } else {
            vm.moveOutputsToFolder(ids, targetId)
        }
        selected = emptySet()
        selecting = false
    }
    LaunchedEffect(outputs,outputLoad) {
        if(outputLoad==DataLoadStatus.READY) {
            selected=selected.intersect(outputs.map {it.id}.toSet())
            if(outputs.isEmpty()) selecting=false
        }
    }
    LaunchedEffect(imported,sourceLoad) {
        if(sourceLoad==DataLoadStatus.READY) {
            selectedSources=selectedSources.intersect(imported.map {it.id}.toSet())
            if(imported.isEmpty()) selectingSources=false
        }
    }
    val density=LocalDensity.current
    val visible=remember(outputs,search,sort,type,settings.favoriteOutputIds,settings.outputFolderIds,activeFolder) {
        outputs.filter { out ->
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
    val visibleSources=remember(imported,search,sort) {
        imported.filter {it.displayName.contains(search,true)}.let {list->
            when(sort) {
                "name" -> list.sortedBy {it.displayName.lowercase()}
                "size" -> list.sortedByDescending {it.sizeBytes ?: 0L}
                else -> list.sortedByDescending {it.modifiedAtEpochMs ?: 0L}
            }
        }
    }
    val visibleTrashed=remember(trashed,search) {
        trashed.filter {it.displayName.contains(search,true)}
    }
    val visibleProviderEntries=remember(tree,search) {
        tree?.entries?.filter {it.name.contains(search,true)}.orEmpty()
    }
    val hasPrivateOutputs = remember(outputs) { outputs.any { it.publicUri == null } }
    val selectedOutputs = remember(visible, selected) { visible.filter { it.id in selected } }
    val now = remember(visible) { System.currentTimeMillis() }
    val grouped = remember(visible, group, now) {
        when(group) {
            "type" -> visible.groupBy { StoragePaths.category(it.mimeType) }
            "date" -> visible.groupBy { fileDateGroup(it.createdAtEpochMs, now).name }
            else -> linkedMapOf("" to visible)
        }
    }
    val sectionTabs = remember { listOf("outputs" to R.string.ui_outputs,"sources" to R.string.ui_imported,"folder" to R.string.ui_granted_folder,"trash" to R.string.files_trash_title) }
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
            .pointerInput(section) {
                detectTapGestures(
                    onLongPress = {
                        if (section == "outputs") {
                            haptics.longPress()
                            pageActionsOpen = true
                        }
                    }
                )
            }
    ) {
    val paneWidth = minOf(maxWidth.value, 980f)
    val columns=((paneWidth-40f)/(if(density.fontScale>=1.5f) 240f else 160f)).toInt().coerceIn(1,4)
    val groupedRows = remember(grouped, columns) { grouped.mapValues { (_, files) -> files.chunked(columns) } }
    LazyColumn(
        modifier=Modifier.widthIn(max=980.dp).fillMaxSize().align(Alignment.TopCenter),
        contentPadding=PaddingValues(start=20.dp,end=20.dp,top=16.dp,
            bottom=QuietTheme.spacing.listBottomBuffer + if(section=="outputs" && selecting && selectedOutputs.isNotEmpty()) 96.dp else 0.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ) {
        item("heading") { Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(selecting && section=="outputs") {
                Text(stringResource(R.string.neon_selection_count,selected.size),
                    style=QuietTheme.typography.sectionTitle,
                    color=colors.primary,
                    modifier=Modifier.semantics { heading() })
            }
            QuietPrimaryButton(text=stringResource(R.string.import_device_files), onClick=onImport,
                modifier=Modifier.fillMaxWidth(),leadingIcon=Icons.Rounded.Add)
        } }
        item("search") {QuietSearchBar(search,stringResource(R.string.ui_search_files),{search=it;selected=emptySet()},{search="";selected=emptySet()})}
        item("sections") {
            Surface(
                shape = QuietTheme.shapes.inputField,
                color = colors.surfaceContainerLow,
                border = BorderStroke(1.dp, pillars.cardBorder.copy(alpha = 0.75f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(4.dp).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    sectionTabs.forEach { (key, label) ->
                        val active = section == key
                        Surface(
                            onClick = { haptics.selectionToggle(); section = key; selecting = false; selected = emptySet(); selectingSources = false; selectedSources = emptySet() },
                            modifier = Modifier.weight(1f).heightIn(min = 42.dp).semantics { this.selected = active; role = Role.Tab },
                            shape = QuietTheme.shapes.thumbnail,
                            color = if (active) pillars.cardSurface else androidx.compose.ui.graphics.Color.Transparent,
                            shadowElevation = if (active) 2.dp else 0.dp
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) {
                                Text(
                                    text = stringResource(label),
                                    style = QuietTheme.typography.metadataStrong,
                                    color = if (active) colors.primary else colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
        if(section=="outputs" && hasPrivateOutputs) item("public-store-warning") {
            Surface(color=MaterialTheme.colorScheme.errorContainer.copy(alpha=0.65f),shape=MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.redesign_save_fallback),modifier=Modifier.padding(14.dp),
                    color=MaterialTheme.colorScheme.onErrorContainer,style=QuietTheme.typography.metadata)
            }
        }
        when(section) {
            "outputs" -> {
                if (outputs.isNotEmpty()) item("filter") { Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
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
                    item("active-folder-bar") {
                        FolderBreadcrumbBar(
                            folder = activeFolder,
                            itemCount = folderCounts[activeFolder.id] ?: 0,
                            dragState = dragState,
                            onBackToRoot = { haptics.actionClick(); activeFolderId = null; selected = emptySet(); selecting = false },
                            onOpenFolderActions = { haptics.actionClick(); folderMenuTarget = activeFolder }
                        )
                    }
                } else {
                    item("folder-shelf") {
                        FolderShelf(
                            folders = settings.fileFolders,
                            folderCounts = folderCounts,
                            dragState = dragState,
                            onCreateFolder = { haptics.actionClick(); folderNameInput = ""; createFolderOpen = true },
                            onOpenFolder = { folder -> haptics.actionClick(); activeFolderId = folder.id; selected = emptySet(); selecting = false },
                            onFolderMenu = { folder -> haptics.longPress(); folderMenuTarget = folder }
                        )
                    }
                }
                if(outputLoad!=DataLoadStatus.READY) item("outputs-state") { DocumentLoadState(outputLoad,vm::reloadSavedData) }
                if(outputLoad==DataLoadStatus.READY && visible.isEmpty()) item("empty") {
                    val filtered=search.isNotEmpty() || type!="all"
                    when {
                        activeFolder != null && !filtered -> QuietStateCard(
                            stringResource(R.string.files_folder_empty_title),
                            stringResource(R.string.files_folder_empty_body),
                            stringResource(R.string.files_folder_all_files),
                            { activeFolderId = null },
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
                if(groupKey.isNotEmpty()) item("file-group-$groupKey") {
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
                                {onShare(output)},{onExport(output)},{onReuse(output)},{deleteIds=setOf(output.id)},
                                if(selecting) output.id in selected else null,{selected=selected.symmetric(output.id)},
                                onDetails={details=output},isFavorite=output.id in settings.favoriteOutputIds,
                                onFavorite={vm.toggleOutputFavorite(output.id)},onPrint=printAction(output),onRename={rename(output)},
                                onLongClick={haptics.selectionToggle();selecting=true;selected=selected+output.id},
                                onMoveToFolder={moveOutputIds=setOf(output.id)},
                                rowModifier=dragMod)
                        }
                    }
                } else {
                    items(groupedRows[groupKey].orEmpty(),key={it.joinToString("-") {f->f.id}},contentType={"tileRow"}) {row->
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.fillMaxWidth()) {
                            row.forEach {output->
                                val cardHeight=288.dp
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
                                        .then(gridDragMod)
                                        .pressFeedback()
                                        .combinedClickable(onClick={haptics.actionClick();if(selecting) selected=selected.symmetric(output.id) else onOpen(output)},
                                            onLongClick={haptics.selectionToggle();selecting=true;selected=selected+output.id})
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
                                    Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                        Box(Modifier.fillMaxWidth()
                                            .height(140.dp)) {
                                            NeonOutputPreview(output, Modifier.fillMaxSize())
                                            if(selecting) Icon(
                                                if(output.id in selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                                stringResource(R.string.ui_select_files),
                                                tint=MaterialTheme.colorScheme.primary,
                                                modifier=Modifier.align(Alignment.TopEnd).padding(7.dp).size(27.dp)
                                            )
                                        }
                                        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                            Text(output.displayName,style=QuietTheme.typography.bodyMedium.copy(fontWeight=FontWeight.SemiBold,textDirection=TextDirection.Content),maxLines=2,overflow=TextOverflow.Ellipsis)
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
                                                    Icon(if(output.id in settings.favoriteOutputIds) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                                        stringResource(if(output.id in settings.favoriteOutputIds) R.string.files_unmark_favorite else R.string.files_mark_favorite),Modifier.size(20.dp))
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
                                                        DropdownMenuItem(text={Text(stringResource(R.string.action_use_as_input))},onClick={actionsExpanded=false;onReuse(output)})
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
            "sources" -> {
                if(sourceLoad!=DataLoadStatus.READY) item("sources-state") { DocumentLoadState(sourceLoad,vm::reloadSavedData) }
                item("source-controls") {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(stringResource(R.string.files_snapshots_sub),style=QuietTheme.typography.metadata,modifier=Modifier.weight(1f))
                        TextButton(enabled=imported.isNotEmpty(),onClick={
                            haptics.selectionToggle();selectingSources=!selectingSources;selectedSources=emptySet()
                        }) {Text(stringResource(if(selectingSources) R.string.action_cancel else R.string.ui_select_files))}
                    }
                }
                if(selectingSources) item("source-batch-actions") {
                    val matching=remember(visibleSources,selectedSources) { visibleSources.filter { it.id in selectedSources } }
                    val onlyPdfs=remember(matching) { matching.size>=2 && matching.all {it.mimeType.equals("application/pdf",true)} }
                    val onlyImages=remember(matching) { matching.isNotEmpty() && matching.all {it.mimeType?.startsWith("image/")==true} }
                    Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        TextButton(onClick={selectedSources=visibleSources.map {it.id}.toSet()}) {
                            Text(stringResource(R.string.redesign_select_all))
                        }
                        if(matching.isNotEmpty()) {
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick={
                                    haptics.actionClick();onSourceWorkflow(matching,"file_07");selectingSources=false;selectedSources=emptySet()
                                },modifier=Modifier.weight(1f)) {
                                    Text(stringResource(R.string.files_bundle_selected,matching.size),maxLines=1)
                                }
                                if(onlyPdfs) FilledTonalButton(onClick={
                                    haptics.actionClick();onSourceWorkflow(matching,"pdf_03");selectingSources=false;selectedSources=emptySet()
                                },modifier=Modifier.weight(1f)) {
                                    Text(stringResource(R.string.files_merge_selected,matching.size),maxLines=1)
                                }
                                if(onlyImages) FilledTonalButton(onClick={
                                    haptics.actionClick();onSourceWorkflow(matching,"pdf_02");selectingSources=false;selectedSources=emptySet()
                                },modifier=Modifier.weight(1f)) {
                                    Text(stringResource(R.string.files_images_to_pdf_selected,matching.size),maxLines=1)
                                }
                            }
                        } else Text(stringResource(R.string.files_batch_tool_hint),style=QuietTheme.typography.metadata)
                    }
                }
                if(sourceLoad==DataLoadStatus.READY && imported.isEmpty()) item("sources-empty") {QuietStateCard(stringResource(R.string.files_empty_snapshots),stringResource(R.string.files_snapshots_sub),stringResource(R.string.import_device_files),onImport,icon=Icons.Rounded.FolderOpen)}
                if(sourceLoad==DataLoadStatus.READY && imported.isNotEmpty() && visibleSources.isEmpty()) item("sources-filtered-empty") {
                    QuietStateCard(stringResource(R.string.upgrade_no_results),stringResource(R.string.upgrade_no_results_hint),
                        stringResource(R.string.files_state_clear_search),{search="";selectedSources=emptySet()},icon=Icons.Rounded.Search)
                }
                items(visibleSources,key={it.id},contentType={"source"}) {source->
                    FileMenuRow(source.displayName,formatBytes(source.sizeBytes) + (source.modifiedAtEpochMs?.let { " · " + fileDateLabel(it) } ?: ""),
                        {if(selectingSources) selectedSources=selectedSources.symmetric(source.id) else onSource(source.id)},
                        selection=if(selectingSources) source.id in selectedSources else null,
                        onSelect={selectedSources=selectedSources.symmetric(source.id)},
                        onLongClick={haptics.longPress();selectingSources=true;selectedSources=selectedSources+source.id}) {close->
                        if(onPrintSource!=null && (source.mimeType=="application/pdf" || source.mimeType?.startsWith("image/")==true))
                            DropdownMenuItem(text={Text(stringResource(R.string.upgrade_print))},onClick={close();onPrintSource(source.id)})
                        DropdownMenuItem(text={Text(stringResource(R.string.files_add_to_workspace))},onClick={close();onSource(source.id)})
                    }
                }
            }
            "trash" -> {
                item("trash-notice") {Text(stringResource(R.string.ui_trash_notice),style=QuietTheme.typography.metadata)}
                if(trashLoad!=DataLoadStatus.READY) item("trash-state") {DocumentLoadState(trashLoad,vm::reloadTrash)}
                else {
                    if(trashed.isEmpty()) item("trash-empty") {QuietStateCard(stringResource(R.string.files_trash_title),stringResource(R.string.files_trash_empty),icon=Icons.Rounded.DeleteOutline)}
                    else item("trash-purge") {TextButton(onClick={purgeIds=trashed.map {it.id}.toSet()}) {Text(stringResource(R.string.files_purge_trash))}}
                    if(trashed.isNotEmpty() && visibleTrashed.isEmpty()) item("trash-filtered-empty") {
                        QuietStateCard(stringResource(R.string.upgrade_no_results),stringResource(R.string.upgrade_no_results_hint),
                            stringResource(R.string.files_state_clear_search),{search=""},icon=Icons.Rounded.Search)
                    }
                    items(visibleTrashed,key={it.id},contentType={"trashed"}) {entry->FileMenuRow(entry.displayName,formatBytes(entry.sizeBytes),{vm.restoreOutput(entry.id)},Icons.Rounded.DeleteOutline) {close->
                        DropdownMenuItem(text={Text(stringResource(R.string.action_restore))},onClick={close();vm.restoreOutput(entry.id)})
                        DropdownMenuItem(text={Text(stringResource(R.string.action_delete))},onClick={close();purgeIds=setOf(entry.id)})
                    }}
                }
            }
            "folder" -> {
                item("folder-browse") {FilledTonalButton(onClick=onFolder) {Text(stringResource(R.string.tool_browse_storage))}}
                val listing=tree
                if(treeLoad!=DataLoadStatus.READY) item("folder-state") {DocumentLoadState(treeLoad,vm::reloadTree)}
                else if(listing==null) item("folder-empty") {QuietStateCard(stringResource(R.string.ui_granted_folder),stringResource(R.string.ui_folder_empty),stringResource(R.string.tool_browse_storage),onFolder,icon=Icons.Rounded.Folder)}
                else {
                    item("folder-heading") { Column(verticalArrangement=Arrangement.spacedBy(4.dp)) { Text(listing.name,style=QuietTheme.typography.sectionTitle.copy(textDirection=TextDirection.Content),modifier=Modifier.semantics { heading() });Text(stringResource(R.string.ui_provider_notice),style=QuietTheme.typography.metadata) } }
                    if(listing.writable) item("folder-new") {TextButton(onClick=onNewFolder) {Text(stringResource(R.string.files_new_folder))}}
                    if(listing.entries.isEmpty()) item("folder-no-files") {
                        QuietStateCard(stringResource(R.string.files_state_folder_empty_title),stringResource(R.string.files_state_folder_empty_body),
                            stringResource(R.string.files_state_refresh_folder),vm::reloadTree,icon=Icons.Rounded.FolderOpen)
                    } else if(visibleProviderEntries.isEmpty()) item("folder-filtered-empty") {
                        QuietStateCard(stringResource(R.string.upgrade_no_results),stringResource(R.string.upgrade_no_results_hint),
                            stringResource(R.string.files_state_clear_search),{search=""},icon=Icons.Rounded.Search)
                    }
                    items(visibleProviderEntries,key={it.uri},contentType={"provider-entry"}) {entry->ProviderFileRow(entry,listing.writable,{onBrowse(entry)},{onProviderOpen(entry)},{onProviderImport(entry)},{onCopy(entry)},{onRename(entry)},{onDelete(entry)})}
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
                .widthIn(max=520.dp)
                .padding(start=16.dp,end=16.dp,bottom=12.dp),
            color=pillars.dockSurface,
            shape=QuietTheme.shapes.dockPill,
            border=BorderStroke(1.dp,pillars.dockBorder),
            shadowElevation=10.dp
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=7.dp),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.SpaceEvenly) {
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
    FloatingDragGhost(dragState = dragState, modifier = Modifier.align(Alignment.TopStart))
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
    purgeIds?.let {ids->AlertDialog(onDismissRequest={purgeIds=null},title={Text(stringResource(R.string.ui_purge_title),modifier=Modifier.semantics { heading() })},text={Text(stringResource(R.string.ui_purge_body,ids.size))},confirmButton={TextButton(onClick={purgeIds=null;vm.purgeTrashIds(ids)}) {Text(stringResource(R.string.ui_delete_forever))}},dismissButton={TextButton(onClick={purgeIds=null}) {Text(stringResource(R.string.action_cancel))}})}
    if (pageActionsOpen) {
        PageQuickActionsSheet(
            canSelect = outputs.isNotEmpty(),
            onDismiss = { pageActionsOpen = false },
            onCreateFolder = { folderNameInput = ""; createFolderOpen = true },
            onImport = onImport,
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
            onDelete = { deleteFolderTarget = folder }
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
                vm.createFileFolder(folderNameInput)
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
                    if (activeFolderId == folder.id) activeFolderId = null
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
    Column(Modifier.weight(1f).pressFeedback(),
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
