package com.jumlo.app.ui.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jumlo.app.BuildConfig
import com.jumlo.app.R
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.automirrored.rounded.ChevronRight
import com.jumlo.app.ui.icons.rounded.AutoAwesome
import com.jumlo.app.ui.icons.rounded.CheckCircle
import com.jumlo.app.ui.icons.rounded.Close
import com.jumlo.app.ui.icons.rounded.Description
import com.jumlo.app.ui.icons.rounded.Lock
import com.jumlo.app.ui.icons.rounded.Security
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun JumloAboutDialog(
    onReplayGuide: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val colors = MaterialTheme.colorScheme
    val pillars = QuietTheme.pillars
    var copied by remember { mutableStateOf(false) }
    var openLicenseAsset by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showLicensesList by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = QuietTheme.shapes.heroCard,
            color = colors.surfaceContainerLow,
            border = BorderStroke(1.dp, pillars.cardBorder),
            shadowElevation = 20.dp,
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterStart),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = QuietTheme.shapes.iconTile,
                            color = colors.primaryContainer,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Image(
                                    painter = painterResource(R.drawable.ic_prism_brand_mark),
                                    contentDescription = stringResource(R.string.app_name),
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = QuietTheme.typography.screenTitle.copy(fontWeight = FontWeight.Bold),
                                color = colors.onSurface,
                                modifier = Modifier.semantics { heading() }
                            )
                            Text(
                                text = stringResource(
                                    R.string.about_version_label,
                                    BuildConfig.VERSION_NAME,
                                    BuildConfig.VERSION_CODE
                                ),
                                style = QuietTheme.typography.metadata.copy(textDirection = TextDirection.Ltr),
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                    Surface(
                        onClick = onDismiss,
                        shape = CircleShape,
                        color = colors.surfaceContainerHigh,
                        modifier = Modifier.align(Alignment.TopEnd).size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.action_close), tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Version & Device copy chip
                Surface(
                    onClick = {
                        val info = buildString {
                            append("Jumlo v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
                            append("Flavor: ${BuildConfig.FLAVOR} / ${BuildConfig.BUILD_TYPE}\n")
                            append("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
                        }
                        clipboard.setText(AnnotatedString(info))
                        copied = true
                    },
                    shape = QuietTheme.shapes.pill,
                    color = colors.primaryContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Rounded.CheckCircle else Icons.Rounded.Description,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(if (copied) R.string.about_info_copied else R.string.about_copy_info),
                            style = QuietTheme.typography.metadataStrong,
                            color = colors.primary
                        )
                    }
                }

                // Privacy & On-Device Promise Card
                Surface(
                    shape = QuietTheme.shapes.card,
                    color = pillars.cardSurface,
                    border = BorderStroke(1.dp, pillars.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Security, null, tint = colors.primary, modifier = Modifier.size(18.dp))
                            Text(
                                text = stringResource(R.string.about_promise_title),
                                style = QuietTheme.typography.metadataStrong,
                                color = colors.onSurface
                            )
                        }
                        Text(
                            text = stringResource(R.string.about_promise_body),
                            style = QuietTheme.typography.metadata,
                            color = colors.onSurfaceVariant
                        )
                    }
                }

                // Built-in Workspaces Card
                Surface(
                    shape = QuietTheme.shapes.card,
                    color = pillars.cardSurface,
                    border = BorderStroke(1.dp, pillars.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = colors.primary, modifier = Modifier.size(18.dp))
                            Text(
                                text = stringResource(R.string.about_capabilities_title),
                                style = QuietTheme.typography.metadataStrong,
                                color = colors.onSurface
                            )
                        }
                        Text(
                            text = stringResource(R.string.about_capabilities_body),
                            style = QuietTheme.typography.metadata,
                            color = colors.onSurfaceVariant
                        )
                    }
                }

                // Action Links List
                Surface(
                    shape = QuietTheme.shapes.card,
                    color = pillars.cardSurface,
                    border = BorderStroke(1.dp, pillars.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        AboutActionRow(
                            icon = Icons.Rounded.AutoAwesome,
                            title = stringResource(R.string.quick_start_replay),
                            subtitle = null,
                            onClick = { onDismiss(); onReplayGuide() }
                        )
                        HorizontalDivider(color = pillars.cardBorder.copy(alpha = 0.6f))
                        AboutActionRow(
                            icon = Icons.Rounded.Lock,
                            title = stringResource(R.string.account_privacy),
                            subtitle = null,
                            onClick = onOpenPrivacy
                        )
                        HorizontalDivider(color = pillars.cardBorder.copy(alpha = 0.6f))
                        AboutActionRow(
                            icon = Icons.Rounded.Description,
                            title = stringResource(R.string.about_licenses_title),
                            subtitle = stringResource(R.string.about_licenses_subtitle),
                            onClick = { showLicensesList = true }
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.about_footer),
                    style = QuietTheme.typography.metadata,
                    color = colors.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }

    if (showLicensesList) {
        AlertDialog(
            onDismissRequest = { showLicensesList = false },
            title = { Text(stringResource(R.string.about_licenses_title), modifier = Modifier.semantics { heading() }) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val fontsTitle = stringResource(R.string.about_license_fonts)
                    val iconsTitle = stringResource(R.string.about_license_icons)
                    AboutActionRow(
                        icon = Icons.Rounded.Description,
                        title = fontsTitle,
                        subtitle = "licenses/IBM-Plex-OFL.txt",
                        onClick = { openLicenseAsset = fontsTitle to "licenses/IBM-Plex-OFL.txt" }
                    )
                    HorizontalDivider()
                    AboutActionRow(
                        icon = Icons.Rounded.Description,
                        title = iconsTitle,
                        subtitle = "licenses/material-symbols-Apache-2.0.txt",
                        onClick = { openLicenseAsset = iconsTitle to "licenses/material-symbols-Apache-2.0.txt" }
                    )
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.about_license_libraries),
                        style = QuietTheme.typography.metadataStrong,
                        color = colors.onSurface,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = "AndroidX, CameraX & Jetpack Compose (Apache 2.0)\n" +
                            "Apache PDFBox / pdfbox-android (Apache 2.0)\n" +
                            "zip4j AES Archive Engine (Apache 2.0)\n" +
                            "OkHttp & Coil (Apache 2.0)\n" +
                            "Tesseract OCR / tesseract4android (Apache 2.0)\n" +
                            "Google ML Kit Document Scanner, OCR & Subject Segmentation (Google APIs Terms)",
                        style = QuietTheme.typography.metadata,
                        color = colors.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicensesList = false }) { Text(stringResource(R.string.account_done)) }
            }
        )
    }

    openLicenseAsset?.let { (title, path) ->
        val text by produceState<String>("...", path) {
            value = withContext(Dispatchers.IO) {
                runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }
                    .getOrDefault("")
            }
        }
        AlertDialog(
            onDismissRequest = { openLicenseAsset = null },
            title = { Text(title, modifier = Modifier.semantics { heading() }) },
            text = {
                Text(
                    text = text,
                    style = QuietTheme.typography.metadata.copy(
                        fontFamily = FontFamily.Monospace,
                        textDirection = TextDirection.Ltr
                    ),
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { openLicenseAsset = null }) { Text(stringResource(R.string.action_close)) }
            }
        )
    }
}

@Composable
private fun AboutActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = colors.primary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = QuietTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = colors.onSurface)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = QuietTheme.typography.metadata.copy(textDirection = TextDirection.Content),
                    color = colors.onSurfaceVariant
                )
            }
        }
        Icon(Icons.AutoMirrored.Rounded.ChevronRight, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}
