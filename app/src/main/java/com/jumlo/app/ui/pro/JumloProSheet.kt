package com.jumlo.app.ui.pro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jumlo.app.R
import com.jumlo.app.data.billing.JumloBillingManager
import com.jumlo.app.data.billing.ProEntitlement
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.rounded.Bolt
import com.jumlo.app.ui.icons.rounded.Check
import com.jumlo.app.ui.icons.rounded.Close
import com.jumlo.app.ui.icons.rounded.DocumentScanner
import com.jumlo.app.ui.icons.rounded.Lock
import com.jumlo.app.ui.icons.rounded.Security
import com.jumlo.app.ui.settings.PrivacyDialog
import com.jumlo.app.ui.theme.QuietTheme

private val GoldPrimary = Color(0xFFD4A017)
private val GoldOnButton = Color(0xFF1A1200)

/** Scrollable iOS-style Pro paywall dialog with custom geometric emblem, value list, plans, and offline legal dialogs. */
@Composable
fun JumloProSheet(
    entitlement: ProEntitlement,
    onPurchase: (String) -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedId by rememberSaveable { mutableStateOf(JumloBillingManager.SKU_LIFETIME) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    var showTerms by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val pillars = QuietTheme.pillars

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = QuietTheme.shapes.heroCard,
            color = colors.surfaceContainerLow,
            border = BorderStroke(1.2.dp, GoldPrimary.copy(alpha = 0.45f)),
            shadowElevation = 24.dp,
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .testTag("pro.sheet")
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(R.drawable.ic_pro_hero_badge),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(72.dp)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopEnd).size(40.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            stringResource(R.string.action_close),
                            tint = colors.onSurfaceVariant
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.pro_hero_headline),
                        style = QuietTheme.typography.sectionTitle.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = stringResource(R.string.pro_hero_subheadline),
                        style = QuietTheme.typography.metadata,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Surface(
                    shape = QuietTheme.shapes.card,
                    color = pillars.cardSurface,
                    border = BorderStroke(1.dp, pillars.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        ProBenefitRow(Icons.Rounded.Bolt, stringResource(R.string.pro_benefit_offline))
                        ProBenefitRow(Icons.Rounded.DocumentScanner, stringResource(R.string.pro_benefit_ocr))
                        ProBenefitRow(Icons.Rounded.Lock, stringResource(R.string.pro_benefit_vault))
                        ProBenefitRow(Icons.Rounded.Security, stringResource(R.string.pro_benefit_privacy))
                    }
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
                            badge = stringResource(R.string.pro_best_value),
                            selected = selectedId == JumloBillingManager.SKU_LIFETIME,
                            onClick = { selectedId = JumloBillingManager.SKU_LIFETIME },
                            modifier = Modifier.weight(1f)
                        )
                        PlanCard(
                            title = stringResource(R.string.pro_annual),
                            price = entitlement.annualPrice ?: stringResource(R.string.pro_yearly),
                            badge = null,
                            selected = selectedId == JumloBillingManager.SKU_ANNUAL,
                            onClick = { selectedId = JumloBillingManager.SKU_ANNUAL },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = { onPurchase(selectedId) },
                        shape = QuietTheme.shapes.pill,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldPrimary,
                            contentColor = GoldOnButton
                        ),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.pro_unlock_action),
                            style = QuietTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Text(
                        text = stringResource(R.string.pro_billing_note),
                        style = QuietTheme.typography.metadata,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    )
                    TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.pro_restore),
                            style = QuietTheme.typography.metadataStrong,
                            color = colors.onSurfaceVariant
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.pro_legal_privacy),
                        style = QuietTheme.typography.metadata,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier
                            .clip(QuietTheme.shapes.pill)
                            .clickable { showPrivacy = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Text(
                        text = "·",
                        style = QuietTheme.typography.metadata,
                        color = colors.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    Text(
                        text = stringResource(R.string.pro_legal_terms),
                        style = QuietTheme.typography.metadata,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier
                            .clip(QuietTheme.shapes.pill)
                            .clickable { showTerms = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }

    if (showPrivacy) {
        PrivacyDialog(onDismiss = { showPrivacy = false })
    }
    if (showTerms) {
        AlertDialog(
            onDismissRequest = { showTerms = false },
            title = {
                Text(
                    stringResource(R.string.pro_legal_terms),
                    modifier = Modifier.semantics { heading() }
                )
            },
            text = {
                Text(
                    stringResource(R.string.pro_terms_body),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { showTerms = false }) {
                    Text(stringResource(R.string.account_done))
                }
            }
        )
    }
}

@Composable
private fun ProBenefitRow(icon: ImageVector, label: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(GoldPrimary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = GoldPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = label,
            style = QuietTheme.typography.bodyMedium,
            color = colors.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    badge: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val colors = MaterialTheme.colorScheme
    val pillars = QuietTheme.pillars
    Surface(
        onClick = onClick,
        shape = QuietTheme.shapes.card,
        color = if (selected) GoldPrimary.copy(alpha = 0.13f) else pillars.cardSurface,
        border = if (selected) BorderStroke(2.dp, GoldPrimary) else BorderStroke(1.dp, pillars.cardBorder),
        modifier = modifier
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (badge != null) {
                Surface(
                    shape = QuietTheme.shapes.pill,
                    color = GoldPrimary
                ) {
                    Text(
                        text = badge,
                        style = QuietTheme.typography.metadata.copy(fontWeight = FontWeight.Bold),
                        color = GoldOnButton,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            } else {
                Spacer(Modifier.height(20.dp))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    title,
                    style = QuietTheme.typography.metadataStrong,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (selected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                price,
                style = QuietTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = if (selected) GoldPrimary else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
