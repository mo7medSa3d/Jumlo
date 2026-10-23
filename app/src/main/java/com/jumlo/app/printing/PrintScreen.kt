package com.jumlo.app.printing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.automirrored.rounded.ArrowBack
import com.jumlo.app.ui.icons.automirrored.rounded.Redo
import com.jumlo.app.ui.icons.automirrored.rounded.RotateRight
import com.jumlo.app.ui.icons.automirrored.rounded.Undo
import com.jumlo.app.ui.icons.rounded.Close
import com.jumlo.app.ui.icons.rounded.DoneAll
import com.jumlo.app.ui.icons.rounded.Favorite
import com.jumlo.app.ui.icons.rounded.FavoriteBorder
import com.jumlo.app.ui.icons.rounded.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.jumlo.app.R
import com.jumlo.app.ui.components.QuietChip
import com.jumlo.app.ui.components.QuietSwitch
import com.jumlo.app.ui.theme.JumloMotion
import com.jumlo.app.ui.theme.LocalReducedMotion
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.util.rememberAppHaptics
import com.jumlo.app.ui.util.rememberZoomPanState
import com.jumlo.app.ui.util.zoomPan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt

/** Content-only screen: navigation shell owns its top bar, insets and bottom dock. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun PrintScreen(source:PrintSource?,onBack:()->Unit,onPickDocument:()->Unit,modifier:Modifier=Modifier,viewModel:PrintViewModel=hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current
    val haptics=rememberAppHaptics()
    var section by rememberSaveable {mutableIntStateOf(0)}
    var presetDialog by rememberSaveable {mutableStateOf(false)}
    var serviceError by rememberSaveable {mutableStateOf(false)}
    var pageIndex by rememberSaveable(state.source) {mutableIntStateOf(0)}
    LaunchedEffect(source) {viewModel.attach(source)}
    LaunchedEffect(state.orderedPages.size) {pageIndex=pageIndex.coerceIn(0,(state.orderedPages.size-1).coerceAtLeast(0))}
    LaunchedEffect(lifecycle,viewModel) {lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {while(true) {viewModel.refreshJobs();delay(2000)}}}
    LaunchedEffect(state.error) {if(state.error!=null) haptics.error()}
    fun openServices() {haptics.actionClick();try {context.startActivity(Intent(Settings.ACTION_PRINT_SETTINGS));serviceError=false} catch(_:Exception) {serviceError=true}}
    LazyColumn(modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item("header") {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                if(state.source!=null) IconButton(onClick={haptics.actionClick();onBack()},modifier=Modifier.size(48.dp)) {Icon(Icons.AutoMirrored.Rounded.ArrowBack,stringResource(R.string.print_back))}
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.print_title),style=MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.print_subtitle),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.Print,null,Modifier.size(28.dp))
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                QuietChip(section==0,{section=0},stringResource(if(state.source==null) R.string.print_documents else R.string.print_setup))
                QuietChip(section==1,{section=1},stringResource(R.string.print_history))
            }
        }
        if(!state.loaded) item {LinearProgressIndicator(Modifier.fillMaxWidth())}
        if(state.error!=null && !state.rangeError) item("error") {
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.errorContainer)) {Column(Modifier.padding(16.dp)) {
                Text(stringResource(printErrorString(state.error)),color=MaterialTheme.colorScheme.onErrorContainer)
                Row {TextButton(onClick={haptics.actionClick();viewModel.retry()},modifier=Modifier.heightIn(min=48.dp)) {Text(stringResource(R.string.print_retry))}
                    TextButton(onClick=onPickDocument,modifier=Modifier.heightIn(min=48.dp)) {Text(stringResource(R.string.print_choose_document))}}
            }}
        }
        if(serviceError || !state.serviceAvailable) item {Text(stringResource(R.string.print_error_unavailable),color=MaterialTheme.colorScheme.error)}
        if(!state.printServicesInstalled && state.source!=null) item("no-print-service") {
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer)) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.print_no_print_service),style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.onSecondaryContainer)
                Text(stringResource(R.string.print_no_print_service_help),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSecondaryContainer)
                TextButton(onClick=::openServices,modifier=Modifier.heightIn(min=48.dp)) {Text(stringResource(R.string.print_services))}
            }}
        }
        if(section==1) {
            item {Text(stringResource(R.string.print_history_explanation),style=MaterialTheme.typography.bodyMedium)}
            if(state.saved.history.isNotEmpty()) item("print-usage") {
                Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.print_usage_summary,state.saved.history.size,PrintUsage.totalPages(state.saved.history)),style=MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.print_usage_completed,PrintUsage.completedJobs(state.saved.history)),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if(state.historyError) item {Text(stringResource(R.string.print_history_unavailable),color=MaterialTheme.colorScheme.error)}
            if(state.saved.history.isEmpty()) item {QuietEmptyQueue()}
            items(state.saved.history,key={"job-${it.id}"}) {entry ->
                PrintQueueRow(entry,active=entry.id==state.activeJobId,spoolingPages=state.spoolingPages,spoolingTotal=state.spoolingTotal,
                    onCancel={haptics.secondaryAction();viewModel.cancelJob(entry.id)},
                    onRetry={haptics.actionClick();viewModel.retryJob(entry);section=0})
            }
        } else if(state.source==null) {
            item {Button(onClick={haptics.actionClick();onPickDocument()},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {Text(stringResource(R.string.print_choose_document))}}
            item {Text(stringResource(R.string.print_recent),style=MaterialTheme.typography.titleLarge)}
            if(state.saved.recent.isEmpty()) item {Text(stringResource(R.string.print_recent_empty))}
            items(state.saved.recent.sortedByDescending {it.favorite},key={"recent-${PrintSourceIdentity.documentKey(it.source)}"}) {recent ->
                Card(onClick={haptics.actionClick();viewModel.open(recent.source)},modifier=Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp).heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(recent.source.name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                        IconButton(onClick={haptics.selectionToggle();viewModel.favorite(recent.source)},modifier=Modifier.size(48.dp)) {Icon(if(recent.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,stringResource(if(recent.favorite) R.string.print_unfavorite else R.string.print_favorite))}
                    }
                }
            }
        } else {
            item("document") {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {Text(state.source!!.name,style=MaterialTheme.typography.titleMedium)
                        state.info?.let {Text(stringResource(R.string.print_page_count,state.pages.size,state.orderedPages.size),style=MaterialTheme.typography.bodySmall)}}
                    IconButton(onClick={haptics.actionClick();viewModel.open(null)},modifier=Modifier.size(48.dp)) {Icon(Icons.Rounded.Close,stringResource(R.string.print_close_document))}
                }
            }
            item("print-edit-history") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick={haptics.actionClick();viewModel.undo()}, enabled=state.canUndo,
                        modifier=Modifier.heightIn(min=48.dp).weight(1f)) {
                        Icon(Icons.AutoMirrored.Rounded.Undo,null,Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_undo))
                    }
                    FilledTonalButton(onClick={haptics.actionClick();viewModel.redo()}, enabled=state.canRedo,
                        modifier=Modifier.heightIn(min=48.dp).weight(1f)) {
                        Icon(Icons.AutoMirrored.Rounded.Redo,null,Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.visual_edit_redo))
                    }
                }
            }
            if(state.inputErrors.isNotEmpty()) item("invalid-print-input") {
                Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(stringResource(R.string.print_invalid_numeric),
                            color=MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick=viewModel::resetInvalidNumericDrafts,
                            modifier=Modifier.heightIn(min=48.dp)) {
                            Text(stringResource(R.string.print_reset_invalid))
                        }
                    }
                }
            }
            if(state.inspecting) item {LinearProgressIndicator(Modifier.fillMaxWidth())}
            val ordered=state.orderedPages
            val layout=state.layout
            val focusedPage=state.orderedPages.getOrNull(pageIndex)
            if(ordered.isNotEmpty() && layout!=null) {
                item("pages") {
                    PrintPageManager(
                        pages=ordered,
                        selected=remember(state.options.range,ordered.size) {PrintPageSelection.positions(state.options.range,ordered.size).toSet()},
                        focused=pageIndex,
                        onFocus={pageIndex=it},
                        onToggle={position ->
                            val current=PrintPageSelection.positions(state.options.range,ordered.size).toSet()
                            val next=if(position in current) current-position else current+position
                            if(next.isEmpty()) return@PrintPageManager
                            haptics.selectionToggle()
                            viewModel.setPageSelection(next.sorted())
                        },
                        onOrder={permutation ->viewModel.applyPageOrder(permutation)},
                        onSelectAll={viewModel.setPageSelection(ordered.indices.toList())},
                        onRotate={position ->haptics.sliderTick();viewModel.rotatePage(position)},
                        renderThumbnail={ page, description ->
                            PageImage(state.source!!,state.options,page,layout,viewModel,320,Modifier.fillMaxSize(),description)
                        }
                    )
                }
            }
            if(focusedPage!=null && layout!=null) {
                item("preview") {
                    PrintPreview(state.source!!,state.options,focusedPage,layout,viewModel) {margins->viewModel.edit(state.options.copy(margins=margins))}
                    Text(stringResource(R.string.print_preview_hint),style=MaterialTheme.typography.bodySmall)
                }
            }
            item("range") {
                TextField(colors=com.jumlo.app.ui.workspace.borderlessFieldColors(),shape=MaterialTheme.shapes.large,value=state.options.range,onValueChange={viewModel.edit(state.options.copy(range=it))},label={Text(stringResource(R.string.print_range))},placeholder={Text(stringResource(R.string.print_range_example))},supportingText={Text(stringResource(if(state.rangeError) R.string.print_error_range else R.string.print_range_help))},isError=state.rangeError,singleLine=true,modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Ascii))
            }
            item("paper") {
                Text(stringResource(R.string.print_paper),style=MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {PrintPaper.entries.forEach {paper ->QuietChip(state.options.paper==paper,{viewModel.edit(state.options.copy(paper=paper))},stringResource(paperString(paper)))}}
                if(state.options.paper==PrintPaper.CUSTOM) Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    NumberField("customWidth",state.options.customWidthMm,state.numericDrafts["customWidth"],stringResource(R.string.print_width_mm),Modifier.weight(1f),viewModel::setNumericDraft)
                    NumberField("customHeight",state.options.customHeightMm,state.numericDrafts["customHeight"],stringResource(R.string.print_height_mm),Modifier.weight(1f),viewModel::setNumericDraft)
                }
                if(state.options.paper==PrintPaper.SOURCE) Text(stringResource(R.string.print_source_size_help),style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {PrintOrientation.entries.forEach {orientation ->QuietChip(state.options.orientation==orientation,{viewModel.edit(state.options.copy(orientation=orientation))},stringResource(if(orientation==PrintOrientation.PORTRAIT) R.string.print_portrait else R.string.print_landscape))}}
            }
            item("scale") {
                Text(stringResource(R.string.print_scaling),style=MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {PrintScale.entries.forEach {scale->QuietChip(state.options.scale==scale,{viewModel.edit(state.options.copy(scale=scale))},stringResource(when(scale) {PrintScale.FIT->R.string.print_fit;PrintScale.FILL->R.string.print_fill;PrintScale.ACTUAL->R.string.print_actual}))}}
                val label=stringResource(R.string.print_scale_percent,state.options.scalePercent)
                Text(label)
                var sliderEditing by remember {mutableStateOf(false)}
                DisposableEffect(viewModel) { onDispose {
                    if(sliderEditing) viewModel.endContinuousEdit("print-scale",false)
                } }
                Slider(value=state.options.scalePercent.toFloat(),onValueChange={
                    if(!sliderEditing) {viewModel.beginContinuousEdit("print-scale");sliderEditing=true}
                    viewModel.edit(state.options.copy(scalePercent=it.roundToInt()))
                },valueRange=10f..400f,onValueChangeFinished={
                    if(sliderEditing) {viewModel.endContinuousEdit("print-scale",true);sliderEditing=false}
                    haptics.sliderTick()
                },modifier=Modifier.heightIn(min=48.dp).semantics {contentDescription=label;stateDescription=label})
                if(state.options.scale!=PrintScale.FIT || state.options.scalePercent>100) Text(stringResource(R.string.print_clipping_help),style=MaterialTheme.typography.bodySmall)
                if(state.info?.image==true) {
                    NumberField("imageDpi",state.options.imageDpi.toDouble(),state.numericDrafts["imageDpi"],stringResource(R.string.print_image_dpi),Modifier.fillMaxWidth(),viewModel::setNumericDraft)
                    Text(stringResource(R.string.print_image_dpi_help),style=MaterialTheme.typography.bodySmall)
                }
            }
            item("margins") {
                Text(stringResource(R.string.print_margins),style=MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {PrintMarginPreset.entries.forEach {preset->
                    QuietChip(state.options.margins==preset.margins,{viewModel.edit(state.options.copy(margins=preset.margins))},stringResource(preset.label))}}
                Text(stringResource(R.string.print_margins_help),style=MaterialTheme.typography.bodySmall)
                val m=state.options.margins
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    NumberField("marginLeft",m.left*25.4/72,state.numericDrafts["marginLeft"],stringResource(R.string.print_left_mm),Modifier.weight(1f),viewModel::setNumericDraft)
                    NumberField("marginRight",m.right*25.4/72,state.numericDrafts["marginRight"],stringResource(R.string.print_right_mm),Modifier.weight(1f),viewModel::setNumericDraft)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    NumberField("marginTop",m.top*25.4/72,state.numericDrafts["marginTop"],stringResource(R.string.print_top_mm),Modifier.weight(1f),viewModel::setNumericDraft)
                    NumberField("marginBottom",m.bottom*25.4/72,state.numericDrafts["marginBottom"],stringResource(R.string.print_bottom_mm),Modifier.weight(1f),viewModel::setNumericDraft)
                }
            }
            item("color") {
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(stringResource(R.string.print_grayscale),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                    QuietSwitch(state.options.grayscale,{viewModel.edit(state.options.copy(grayscale=it))},stringResource(R.string.print_grayscale))
                }
                Text(stringResource(if(state.options.grayscale) R.string.print_grayscale_help else R.string.print_color_help),style=MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.print_duplex),style=MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {PrintDuplex.entries.forEach {duplex ->QuietChip(state.options.duplex==duplex,{viewModel.edit(state.options.copy(duplex=duplex))},stringResource(when(duplex) {PrintDuplex.NONE->R.string.print_single_sided;PrintDuplex.LONG_EDGE->R.string.print_long_edge;PrintDuplex.SHORT_EDGE->R.string.print_short_edge}))}}
            }
            item("submit") {
                Text(stringResource(R.string.print_system_options),style=MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick={haptics.toolExecute();context.activity()?.let {viewModel.submit(it)} ?: run {serviceError=true}},enabled=state.layout!=null && state.inputErrors.isEmpty() && !state.preparing && !state.inspecting && state.pages.isNotEmpty(),modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {Icon(Icons.Rounded.Print,null);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.print_choose_printer))}
                if(state.preparing) {
                    Spacer(Modifier.height(8.dp))
                    if(state.preparationTotal>0) {LinearProgressIndicator(progress={state.preparedPages.toFloat()/state.preparationTotal},modifier=Modifier.fillMaxWidth());Text(stringResource(R.string.print_preparing_pages,state.preparedPages,state.preparationTotal))}
                    else {LinearProgressIndicator(Modifier.fillMaxWidth());Text(stringResource(R.string.print_preparing))}
                    TextButton(onClick={haptics.secondaryAction();viewModel.cancelPreparation()},modifier=Modifier.heightIn(min=48.dp)) {Text(stringResource(R.string.print_cancel_preparation))}
                }
                if(state.spoolingTotal>0) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress={state.spoolingPages.toFloat()/state.spoolingTotal},modifier=Modifier.fillMaxWidth())
                    Text(stringResource(R.string.print_queue_sending,state.spoolingPages,state.spoolingTotal),style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        if(section==0) {
            item("presets-title") {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(stringResource(R.string.print_presets),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                TextButton(onClick={haptics.actionClick();presetDialog=true},modifier=Modifier.heightIn(min=48.dp)) {Text(stringResource(R.string.print_save_preset))}
            }}
            if(state.saved.presets.isEmpty()) item {Text(stringResource(R.string.print_presets_empty),style=MaterialTheme.typography.bodyMedium)}
            items(state.saved.presets,key={"preset-${it.name}"}) {preset -> Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                FilledTonalButton(onClick={haptics.selectionToggle();viewModel.applyPreset(preset)},modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Text(preset.name)}
                IconButton(onClick={haptics.secondaryAction();viewModel.deletePreset(preset.name)},modifier=Modifier.size(48.dp)) {Icon(Icons.Rounded.Close,stringResource(R.string.print_delete_preset,preset.name))}
            }}
        }
        item("services") {
            HorizontalDivider()
            TextButton(onClick=::openServices,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(stringResource(R.string.print_services))}
            Text(stringResource(R.string.print_services_help),style=MaterialTheme.typography.bodySmall)
        }
    }
    if(presetDialog) {
        var name by rememberSaveable {mutableStateOf("")}
        AlertDialog(onDismissRequest={presetDialog=false},title={Text(stringResource(R.string.print_save_preset))},text={TextField(colors=com.jumlo.app.ui.workspace.borderlessFieldColors(),shape=MaterialTheme.shapes.large,value=name,onValueChange={name=it.take(60)},label={Text(stringResource(R.string.print_preset_name))},singleLine=true)},confirmButton={TextButton(onClick={haptics.actionClick();viewModel.savePreset(name);presetDialog=false},enabled=name.isNotBlank() && state.inputErrors.isEmpty(),modifier=Modifier.heightIn(min=48.dp)) {Text(stringResource(R.string.print_save))}},dismissButton={TextButton(onClick={presetDialog=false},modifier=Modifier.heightIn(min=48.dp)) {Text(stringResource(R.string.print_cancel))}})
    }
}

/**
 * Page manager for the print preview: tap to include or skip a page, long-press and
 * drag to reorder, rotate one page a quarter turn. The range string stays the single
 * source of truth, so the numeric field and the visual selection never disagree.
 */
@Composable
internal fun PrintPageManager(
    pages: List<PrintPage>,
    selected: Set<Int>,
    focused: Int,
    onFocus: (Int) -> Unit,
    onToggle: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onOrder: (List<Int>) -> Unit,
    onRotate: (Int) -> Unit,
    renderThumbnail: @Composable (PrintPage, String) -> Unit
) {
    val haptics = rememberAppHaptics()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val stepPx = with(density) { 94.dp.toPx() }
    val listState = rememberLazyListState()
    var viewport by remember { mutableStateOf(Rect.Zero) }
    var edgeScroll by remember { mutableFloatStateOf(0f) }
    // Working order of view indices while a page is being dragged.
    var live by remember { mutableStateOf<List<Int>?>(null) }
    // The view index of the page under the finger.
    var dragged by remember { mutableStateOf<Int?>(null) }
    var lift by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(edgeScroll) {
        var accumulated = 0f
        while (edgeScroll != 0f) {
            accumulated += listState.scrollBy(edgeScroll)
            if (abs(accumulated) >= stepPx) {
                val order = live ?: break
                val from = dragged ?: break
                val delta = if (accumulated > 0) 1 else -1
                val target = (from + delta).coerceIn(0, order.lastIndex)
                if (target != from) {
                    live = order.toMutableList().apply { add(target, removeAt(from)) }
                    haptics.snap()
                }
                accumulated = 0f
            }
            delay(16)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.print_page_manager_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.print_page_manager_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(
            modifier = Modifier.onGloballyPositioned { viewport = it.boundsInRoot() },
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            // Keyed by strip position: reordering swaps the content, not the nodes, so the
            // ongoing drag gesture is never restarted.
            items(pages.size, key = { it }) { position ->
                val order = live ?: pages.indices.toList()
                val viewIndex = order[position]
                val page = pages[viewIndex]
                val isLifted = dragged != null && dragged == viewIndex
                PrintPageTile(
                    page = page,
                    position = position,
                    selected = position in selected,
                    focused = position == focused,
                    lifted = isLifted,
                    lift = if (isLifted) lift else 0f,
                    description = stringResource(R.string.print_page_description, position + 1, page.sourcePage),
                    renderThumbnail = { renderThumbnail(page, stringResource(R.string.print_page_description, position + 1, page.sourcePage)) },
                    onClick = { onFocus(position); onToggle(position) },
                    onLongPress = {
                        onFocus(position)
                        live = order
                        dragged = viewIndex
                        lift = 0f
                        haptics.dragStart()
                    },
                    onDragStep = { delta ->
                        val current = live ?: return@PrintPageTile
                        val held = dragged ?: return@PrintPageTile
                        val from = current.indexOf(held)
                        val target = (from + delta).coerceIn(0, current.lastIndex)
                        if (from != target && from >= 0) {
                            live = current.toMutableList().apply { add(target, removeAt(from)) }
                            haptics.snap()
                        }
                    },
                    onDragOffset = { delta -> lift = delta },
                    onDragPosition = { point ->
                        edgeScroll = when {
                            point.x < viewport.left + 48f -> if (rtl) 18f else -18f
                            point.x > viewport.right - 48f -> if (rtl) -18f else 18f
                            else -> 0f
                        }
                    },
                    onDragEnd = { commit ->
                        edgeScroll = 0f
                        val order = live
                        live = null
                        dragged = null
                        lift = 0f
                        if (commit && order != null) onOrder(order)
                        haptics.dragEnd()
                    }
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            QuietChip(false, { onRotate(focused) }, stringResource(R.string.print_rotate_page), enabled = focused in pages.indices)
            QuietChip(false, { onSelectAll() }, stringResource(R.string.print_select_all_pages))
            Text(
                stringResource(R.string.print_page_selected_count, selected.size, pages.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun PrintPageTile(
    page: PrintPage,
    position: Int,
    selected: Boolean,
    focused: Boolean,
    lifted: Boolean,
    lift: Float,
    description: String,
    renderThumbnail: @Composable () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDragStep: (Int) -> Unit,
    onDragOffset: (Float) -> Unit,
    onDragPosition: (Offset) -> Unit,
    onDragEnd: (Boolean) -> Unit
) {
    val haptics = rememberAppHaptics()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val stepPx = with(density) { 94.dp.toPx() }
    var displacement by remember(position) { mutableFloatStateOf(0f) }
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .width(84.dp)
            .graphicsLayer {
                translationX = displacement
                if (lifted) { scaleX = 1.06f; scaleY = 1.06f; shadowElevation = 12f }
            }
            .pointerInput(position) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onLongPress() },
                    onDragEnd = { onDragEnd(true); displacement = 0f },
                    onDragCancel = { onDragEnd(false); displacement = 0f },
                    onDrag = { change, drag ->
                        change.consume()
                        onDragPosition(change.position)
                        displacement += drag.x
                        onDragOffset(displacement)
                        val step = (displacement / stepPx).toInt()
                        if (step != 0) {
                            onDragStep(if (rtl) -step else step)
                            displacement -= step * stepPx
                            onDragOffset(displacement)
                        }
                    }
                )
            }
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected || focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
            .semantics {
                this.contentDescription = description
                role = androidx.compose.ui.semantics.Role.Checkbox
                toggleableState = if (selected) ToggleableState.On else ToggleableState.Off
            }
            // Tap selects the page; the long press is consumed by the drag detector above.
            .combinedClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(100.dp)
                .padding(4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            PrintTileImage(renderThumbnail)
            if (page.rotationDegrees != 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(accent, CircleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("${page.rotationDegrees}°", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        Text(
            stringResource(R.string.print_page_number, position + 1),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}

@Composable
private fun PrintTileImage(renderThumbnail: @Composable () -> Unit) {
    // The tile reuses the composed preview pipeline so what is shown is what prints.
    renderThumbnail()
}

@Composable
private fun PrintQueueRow(
    entry: PrintHistoryEntry,
    active: Boolean,
    spoolingPages: Int,
    spoolingTotal: Int,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(entry.source.name,style=MaterialTheme.typography.titleMedium)
        Text(stringResource(jobStateString(entry.state)),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.print_queue_pages_printed,PrintUsage.pages(entry)),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(active && spoolingTotal>0) {
            LinearProgressIndicator(progress={spoolingPages.toFloat()/spoolingTotal},modifier=Modifier.fillMaxWidth())
            Text(stringResource(R.string.print_queue_sending,spoolingPages,spoolingTotal),style=MaterialTheme.typography.bodySmall)
        }
        Text(remember(entry.createdAt) {DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(entry.createdAt))},style=MaterialTheme.typography.bodySmall)
        Row {
            if(PrintHistoryPolicy.canCancel(entry.state)) TextButton(onClick=onCancel,modifier=Modifier.heightIn(min=48.dp)) {Text(stringResource(R.string.print_cancel_job))}
            if(!PrintHistoryPolicy.canCancel(entry.state)) TextButton(onClick=onRetry,modifier=Modifier.heightIn(min=48.dp)) {Text(stringResource(if(entry.state==PrintJobState.FAILED) R.string.print_retry else R.string.print_reuse))}
        }
    }}
}

@Composable
private fun QuietEmptyQueue() {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.DoneAll,null,Modifier.size(32.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.print_no_jobs),style=MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PrintPreview(source:PrintSource,options:PrintOptions,page:PrintPage,layout:PrintLayout,vm:PrintViewModel,onMargins:(PrintMargins)->Unit) {
    // Two fingers can turn the preview; a page is never rotated past readability.
    val zoomState=rememberZoomPanState(1f,6f,allowRotation=true)
    val reduced=LocalReducedMotion.current
    val latestMargins by rememberUpdatedState(options.margins)
    val latestChange by rememberUpdatedState(onMargins)
    val haptics=rememberAppHaptics()
    val accent=MaterialTheme.colorScheme.primary
    val density=LocalDensity.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio((layout.paper.width/layout.paper.height).toFloat()).heightIn(max=600.dp).clipToBounds().background(MaterialTheme.colorScheme.surfaceVariant)) {
            val previewWidth=maxWidth;val previewHeight=maxHeight
            val widthPx=with(density) {previewWidth.toPx()};val heightPx=with(density) {previewHeight.toPx()}
            Box(Modifier.fillMaxSize()
                .zoomPan(zoomState,key="${source.path}:${page.sourcePage}")
                .graphicsLayer {
                    scaleX=zoomState.scale;scaleY=zoomState.scale
                    rotationZ=zoomState.rotation
                    translationX=zoomState.offset.x;translationY=zoomState.offset.y
                }) {
                PageImage(source,options,page,layout,vm,1400,Modifier.fillMaxSize(),stringResource(R.string.print_preview_page,page.sourcePage))
                Canvas(Modifier.fillMaxSize()) {
                    val m=latestMargins
                    val left=(m.left/layout.paper.width*size.width).toFloat();val top=(m.top/layout.paper.height*size.height).toFloat()
                    val right=(m.right/layout.paper.width*size.width).toFloat();val bottom=(m.bottom/layout.paper.height*size.height).toFloat()
                    drawRect(accent,Offset(left,top),Size((size.width-left-right).coerceAtLeast(1f),(size.height-top-bottom).coerceAtLeast(1f)),style=Stroke(1.5.dp.toPx()))
                }
                // absoluteOffset and LTR scope intentionally keep PDF left/right physical in Arabic.
                for(edge in 0..3) {
                    val m=latestMargins
                    val x=when(edge) {0->m.left/layout.paper.width;2->1-m.right/layout.paper.width;else->.5}
                    val y=when(edge) {1->m.top/layout.paper.height;3->1-m.bottom/layout.paper.height;else->.5}
                    val label=stringResource(when(edge) {0->R.string.print_handle_left;1->R.string.print_handle_top;2->R.string.print_handle_right;else->R.string.print_handle_bottom})
                    Box(Modifier.absoluteOffset(x=previewWidth*x.toFloat()-24.dp,y=previewHeight*y.toFloat()-24.dp).size(48.dp)
                        .semantics {contentDescription=label}
                        .pointerInput(edge,layout.paper,widthPx,heightPx) {
                            var originalMargins:PrintMargins?=null
                            detectDragGestures(onDragStart={originalMargins=latestMargins;vm.beginContinuousEdit("print-margin-$edge");haptics.dragStart()},onDragEnd={originalMargins=null;vm.endContinuousEdit("print-margin-$edge",true);haptics.dragEnd()},onDragCancel={vm.endContinuousEdit("print-margin-$edge",false);originalMargins=null}) {change,drag ->
                            change.consume();val old=latestMargins
                            val dx=drag.x/widthPx*layout.paper.width;val dy=drag.y/heightPx*layout.paper.height
                            val next=when(edge) {
                                0->old.copy(left=(old.left+dx).coerceIn(0.0,(layout.paper.width-old.right-1).coerceAtLeast(0.0)))
                                1->old.copy(top=(old.top+dy).coerceIn(0.0,(layout.paper.height-old.bottom-1).coerceAtLeast(0.0)))
                                2->old.copy(right=(old.right-dx).coerceIn(0.0,(layout.paper.width-old.left-1).coerceAtLeast(0.0)))
                                else->old.copy(bottom=(old.bottom-dy).coerceIn(0.0,(layout.paper.height-old.top-1).coerceAtLeast(0.0)))
                            };latestChange(next)
                        }},contentAlignment=Alignment.Center) {Box(Modifier.size(12.dp).background(accent,CircleShape).border(2.dp,Color.White,CircleShape))}
                }
            }
        }
    }
    val zoomLabel=stringResource(R.string.print_preview_zoom,(zoomState.scale*100).roundToInt())
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
        Text(zoomLabel,style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={zoomState.reset()},enabled=zoomState.scale>1.01f || zoomState.offset!=Offset.Zero,modifier=Modifier.heightIn(min=48.dp)) {Text(stringResource(R.string.ui_preview_reset_zoom))}
    }
}
private data class PreviewImage(val bitmap:Bitmap?=null,val error:Boolean=false)
@Composable private fun PageImage(source:PrintSource,options:PrintOptions,page:PrintPage,layout:PrintLayout,vm:PrintViewModel,maxEdge:Int,modifier:Modifier,description:String) {
    var retry by remember {mutableIntStateOf(0)}
    val preview by produceState(PreviewImage(),source,options,page,layout,retry) {
        value=PreviewImage()
        var bitmap:Bitmap?=null
        try {delay(250);bitmap=vm.renderPage(source,options,page,layout,maxEdge);value=PreviewImage(bitmap)}
        catch(cancel:CancellationException) {throw cancel}
        catch(_:Exception) {value=PreviewImage(error=true)}
        // Once published, Compose/RenderThread may retain an earlier frame. Let Bitmap GC own
        // these small previews; only unpublished renderer intermediates are explicitly recycled.
        awaitDispose {}
    }
    Box(modifier.background(Color.White),contentAlignment=Alignment.Center) {
        preview.bitmap?.let {Image(it.asImageBitmap(),description,Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds)}
        if(preview.error) TextButton(onClick={retry++},modifier=Modifier.heightIn(min=48.dp)) {Text(stringResource(R.string.print_preview_retry))}
        else if(preview.bitmap==null) CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp)
    }
}
@Composable private fun NumberField(
    key: String, number: Double, draft: String?, label: String,
    modifier: Modifier, onValidityChanged: (String, String) -> Unit
) {
    val text = draft ?: String.format(java.util.Locale.US,"%.1f",number)
    TextField(colors=com.jumlo.app.ui.workspace.borderlessFieldColors(),shape=MaterialTheme.shapes.large,
        value=text,
        onValueChange={onValidityChanged(key,it)},
        modifier=modifier,
        label={Text(label)},
        singleLine=true,
        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
        isError=PrintDraftPolicy.parse(key,text)==null
    )
}
private tailrec fun Context.activity():Activity?=when(this) {is Activity->this;is ContextWrapper->baseContext.activity();else->null}
private fun paperString(paper:PrintPaper)=when(paper) {PrintPaper.A4->R.string.print_a4;PrintPaper.A5->R.string.print_a5;PrintPaper.LETTER->R.string.print_letter;PrintPaper.LEGAL->R.string.print_legal;PrintPaper.SOURCE->R.string.print_source_size;PrintPaper.CUSTOM->R.string.print_custom}
private fun jobStateString(state:PrintJobState)=when(state) {PrintJobState.CREATED->R.string.print_state_created;PrintJobState.QUEUED->R.string.print_state_queued;PrintJobState.STARTED->R.string.print_state_started;PrintJobState.BLOCKED->R.string.print_state_blocked;PrintJobState.COMPLETED->R.string.print_state_completed;PrintJobState.FAILED->R.string.print_state_failed;PrintJobState.CANCELED->R.string.print_state_canceled;PrintJobState.UNKNOWN->R.string.print_state_unknown}
private fun printErrorString(code:String?)=when(code) {
    "PRINT_RANGE","PRINT_ROTATION","PRINT_PAGE_COUNT"->R.string.print_error_range
    "PRINT_SOURCE_MISSING","PRINT_SOURCE_UNSAFE"->R.string.print_error_source
    "PRINT_PAPER","PRINT_SCALE","PRINT_MARGINS"->R.string.print_error_layout
    "PRINT_PERMISSION"->R.string.print_error_permission
    "PRINT_PASSWORD"->R.string.print_error_password
    "PRINT_UNSUPPORTED"->R.string.print_error_unsupported
    "PRINT_APPEARANCE"->R.string.print_error_appearance
    "PRINT_STORAGE"->R.string.print_error_storage
    "PRINT_UNAVAILABLE"->R.string.print_error_unavailable
    "PRINT_PRESET_NAME"->R.string.print_error_preset
    "PRINT_INVALID_INPUT"->R.string.print_invalid_numeric
    else->R.string.print_error_generic
}
