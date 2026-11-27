package com.jumlo.app.ui.pro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jumlo.app.R
import com.jumlo.app.data.billing.JumloBillingManager
import com.jumlo.app.data.billing.ProEntitlement
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.rounded.AutoAwesome
import com.jumlo.app.ui.icons.rounded.Bolt
import com.jumlo.app.ui.icons.rounded.CheckCircle
import com.jumlo.app.ui.icons.rounded.Close
import com.jumlo.app.ui.icons.rounded.DocumentScanner
import com.jumlo.app.ui.icons.rounded.Lock
import com.jumlo.app.ui.icons.rounded.Security
import com.jumlo.app.ui.icons.rounded.Star
import com.jumlo.app.ui.settings.PrivacyDialog
import com.jumlo.app.ui.theme.QuietTheme

private val GoldLight = Color(0xFFFFE58F)
private val GoldPrimary = Color(0xFFD4A017)
private val GoldDeep = Color(0xFFB47B00)
private val GoldOnButton = Color(0xFF1A1200)

/** Scrollable luxury champagne-gold Pro upgrade sheet with bespoke crown emblem, benefit cards, and plan selector. */
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
    val isDark = colors.background.luminance() < 0.5f

    val sheetBg = if (isDark) Color(0xFF12100B) else colors.surfaceContainerLow
    val goldSweep = Brush.verticalGradient(
        listOf(
            GoldPrimary.copy(alpha = if (isDark) 0.22f else 0.14f),
            Color.Transparent
        )
    )
    val buttonGradient = Brush.horizontalGradient(listOf(GoldLight, GoldPrimary, Color(0xFFC88A04)))

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = QuietTheme.shapes.heroCard,
            color = sheetBg,
            border = BorderStroke(1.25.dp, GoldPrimary.copy(alpha = 0.52f)),
            shadowElevation = 24.dp,
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 22.dp)
                .widthIn(max = 430.dp)
                .fillMaxWidth()
                .testTag("pro.sheet")
        ) {
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .background(goldSweep)
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        Surface(
                            shape = QuietTheme.shapes.pill,
                            color = GoldPrimary.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Text(
                                text = stringResource(R.string.pro_eyebrow),
                                style = QuietTheme.typography.badge.copy(letterSpacing = 1.2.sp),
                                color = if (isDark) GoldLight else GoldDeep,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            onClick = onDismiss,
                            shape = CircleShape,
                            color = colors.onSurface.copy(alpha = 0.08f),
                            modifier = Modifier.align(Alignment.CenterEnd).size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Close,
                                    stringResource(R.string.action_close),
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Image(
                        painter = painterResource(R.drawable.ic_pro_crown),
                        contentDescription = null,
                        modifier = Modifier.size(84.dp)
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.pro_hero_headline),
                            style = QuietTheme.typography.screenTitle.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = stringResource(R.string.pro_hero_subheadline),
                            style = QuietTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }

                    Surface(
                        shape = QuietTheme.shapes.card,
                        color = if (isDark) Color(0xFF1A1710) else pillars.cardSurface,
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.24f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ProBenefitRow(Icons.Rounded.DocumentScanner, stringResource(R.string.pro_benefit_offline))
                            HorizontalDivider(color = GoldPrimary.copy(alpha = 0.12f))
                            ProBenefitRow(Icons.Rounded.AutoAwesome, stringResource(R.string.pro_benefit_ocr))
                            HorizontalDivider(color = GoldPrimary.copy(alpha = 0.12f))
                            ProBenefitRow(Icons.Rounded.Lock, stringResource(R.string.pro_benefit_vault))
                            HorizontalDivider(color = GoldPrimary.copy(alpha = 0.12f))
                            ProBenefitRow(Icons.Rounded.Security, stringResource(R.string.pro_benefit_privacy))
                        }
                    }

                    if (entitlement.isPro) {
                        Surface(
                            shape = QuietTheme.shapes.card,
                            color = GoldPrimary.copy(alpha = 0.16f),
                            border = BorderStroke(1.5.dp, GoldPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.ic_pro_crown),
                                    contentDescription = null,
                                    modifier = Modifier.size(30.dp)
                                )
                                Text(
                                    stringResource(R.string.pro_active_badge),
                                    style = QuietTheme.typography.sectionTitle,
                                    color = if (isDark) GoldLight else GoldDeep
                                )
                            }
                        }
                    } else {
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PlanCard(
                                title = stringResource(R.string.pro_lifetime),
                                subtitle = stringResource(R.string.pro_lifetime_subtitle),
                                price = entitlement.lifetimePrice ?: "$14.99",
                                badge = stringResource(R.string.pro_best_value),
                                selected = selectedId == JumloBillingManager.SKU_LIFETIME,
                                isDark = isDark,
                                onClick = { selectedId = JumloBillingManager.SKU_LIFETIME },
                                modifier = Modifier.fillMaxWidth()
                            )
                            PlanCard(
                                title = stringResource(R.string.pro_annual),
                                subtitle = stringResource(R.string.pro_annual_subtitle),
                                price = entitlement.annualPrice ?: "$4.99 / yr",
                                badge = null,
                                selected = selectedId == JumloBillingManager.SKU_ANNUAL,
                                isDark = isDark,
                                onClick = { selectedId = JumloBillingManager.SKU_ANNUAL },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Surface(
                            onClick = { onPurchase(selectedId) },
                            shape = QuietTheme.shapes.pill,
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .clip(QuietTheme.shapes.pill)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(buttonGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Rounded.Star, null, tint = GoldOnButton, modifier = Modifier.size(20.dp))
                                    Text(
                                        text = stringResource(
                                            if (selectedId == JumloBillingManager.SKU_LIFETIME) R.string.pro_unlock_lifetime
                                            else R.string.pro_unlock_annual
                                        ),
                                        style = QuietTheme.typography.button.copy(fontWeight = FontWeight.Bold),
                                        color = GoldOnButton
                                    )
                                }
                            }
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
                                color = if (isDark) GoldLight else GoldDeep
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
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
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(GoldPrimary.copy(alpha = 0.16f)),
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
    subtitle: String,
    price: String,
    badge: String?,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val colors = MaterialTheme.colorScheme
    val pillars = QuietTheme.pillars
    val selectedBg = GoldPrimary.copy(alpha = if (isDark) 0.16f else 0.12f)
    val baseBg = if (isDark) Color(0xFF1A1710) else pillars.cardSurface
    Surface(
        onClick = onClick,
        shape = QuietTheme.shapes.card,
        color = if (selected) selectedBg else baseBg,
        border = if (selected) BorderStroke(2.dp, GoldPrimary) else BorderStroke(1.dp, pillars.cardBorder),
        modifier = modifier
    ) {
        Row(
            Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.Bolt,
                contentDescription = null,
                tint = if (selected) GoldPrimary else colors.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.size(22.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = QuietTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (badge != null) {
                        Surface(shape = QuietTheme.shapes.pill, color = GoldPrimary) {
                            Text(
                                text = badge,
                                style = QuietTheme.typography.badge.copy(fontWeight = FontWeight.Bold),
                                color = GoldOnButton,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    style = QuietTheme.typography.metadata,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = price,
                style = QuietTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = if (selected) (if (isDark) GoldLight else GoldDeep) else colors.onSurface,
                maxLines = 1
            )
        }
    }
}
