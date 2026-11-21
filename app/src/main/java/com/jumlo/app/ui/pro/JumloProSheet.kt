package com.jumlo.app.ui.pro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jumlo.app.R
import com.jumlo.app.data.billing.JumloBillingManager
import com.jumlo.app.data.billing.ProEntitlement
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.rounded.AutoAwesome
import com.jumlo.app.ui.icons.rounded.Check
import com.jumlo.app.ui.icons.rounded.Close
import com.jumlo.app.ui.theme.QuietTheme

private val GoldLight = Color(0xFFFFE082)
private val GoldPrimary = Color(0xFFD4A017)
private val GoldDeep = Color(0xFF9A6B00)
private val GoldOnButton = Color(0xFF1A1200)

/** Centered iOS 27 Luxury Gold Pro popup dialog with Crown emblem, clear Pro value, and Lifetime / Annual plans. */
@Composable
fun JumloProSheet(
    entitlement: ProEntitlement,
    onPurchase: (String) -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedId by remember { mutableStateOf(JumloBillingManager.SKU_LIFETIME) }
    val colors = MaterialTheme.colorScheme
    val pillars = QuietTheme.pillars
    val goldGradient = remember { Brush.horizontalGradient(listOf(GoldLight, GoldPrimary, GoldDeep)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = QuietTheme.shapes.heroCard,
            color = colors.surfaceContainerLow,
            border = BorderStroke(1.5.dp, GoldPrimary.copy(alpha = 0.55f)),
            shadowElevation = 24.dp,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .testTag("pro.sheet")
        ) {
            Column(
                Modifier.fillMaxWidth().padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(Modifier.fillMaxWidth()) {
                    // Gold Crown emblem centered
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(goldGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = GoldOnButton,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopEnd).size(40.dp)
                    ) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.action_close), tint = colors.onSurfaceVariant)
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.pro_title),
                        style = QuietTheme.typography.sectionTitle.copy(fontWeight = FontWeight.Bold),
                        color = GoldPrimary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.pro_subtitle),
                        style = QuietTheme.typography.metadata,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                if (entitlement.isPro) {
                    Surface(shape = QuietTheme.shapes.pill, color = GoldPrimary.copy(alpha = 0.16f)) {
                        Text(
                            stringResource(R.string.pro_active_badge),
                            style = QuietTheme.typography.metadataStrong.copy(fontWeight = FontWeight.Bold),
                            color = GoldPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PlanCard(
                            title = stringResource(R.string.pro_lifetime),
                            price = entitlement.lifetimePrice ?: stringResource(R.string.pro_one_time),
                            selected = selectedId == JumloBillingManager.SKU_LIFETIME,
                            onClick = { selectedId = JumloBillingManager.SKU_LIFETIME },
                            modifier = Modifier.weight(1f)
                        )
                        PlanCard(
                            title = stringResource(R.string.pro_annual),
                            price = entitlement.annualPrice ?: stringResource(R.string.pro_yearly),
                            selected = selectedId == JumloBillingManager.SKU_ANNUAL,
                            onClick = { selectedId = JumloBillingManager.SKU_ANNUAL },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = { onPurchase(selectedId) },
                        shape = QuietTheme.shapes.pill,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = GoldOnButton),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.pro_unlock_action),
                            style = QuietTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.pro_restore), color = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanCard(title: String, price: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val pillars = QuietTheme.pillars
    Surface(
        onClick = onClick,
        shape = QuietTheme.shapes.card,
        color = if (selected) GoldPrimary.copy(alpha = 0.14f) else pillars.cardSurface,
        border = if (selected) BorderStroke(2.dp, GoldPrimary) else null,
        modifier = modifier
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = QuietTheme.typography.metadataStrong, color = colors.onSurface, modifier = Modifier.weight(1f))
                if (selected) Icon(Icons.Rounded.Check, null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
            }
            Text(price, style = QuietTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = if (selected) GoldPrimary else colors.onSurface)
        }
    }
}
