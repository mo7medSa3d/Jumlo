package com.jumlo.app.ui.pro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.data.billing.JumloBillingManager
import com.jumlo.app.data.billing.ProEntitlement
import com.jumlo.app.ui.components.QuietPrimaryButton
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.rounded.VerifiedUser
import com.jumlo.app.ui.theme.QuietTheme

/** Clean iOS 27 Jumlo Pro upgrade sheet highlighting Lifetime One-Time Purchase, 100% Offline Privacy & Zero Watermarks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JumloProSheet(
    entitlement: ProEntitlement,
    onPurchase: (String) -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val pillars = QuietTheme.pillars
    var selectedSku by remember { mutableStateOf(JumloBillingManager.SKU_LIFETIME) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = pillars.cardSurface,
        shape = QuietTheme.shapes.sheet,
        modifier = Modifier.testTag("pro.sheet")
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.VerifiedUser, null, tint = colors.primary, modifier = Modifier.size(28.dp))
                Text(stringResource(R.string.pro_title), style = QuietTheme.typography.sectionTitle, color = colors.onSurface)
            }
            Text(stringResource(R.string.pro_privacy_badge), style = QuietTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlanCard(
                    title = stringResource(R.string.pro_lifetime_label),
                    price = entitlement.lifetimePrice ?: "—",
                    selected = selectedSku == JumloBillingManager.SKU_LIFETIME,
                    modifier = Modifier.weight(1f)
                ) { selectedSku = JumloBillingManager.SKU_LIFETIME }
                PlanCard(
                    title = stringResource(R.string.pro_annual_label),
                    price = entitlement.annualPrice ?: "—",
                    selected = selectedSku == JumloBillingManager.SKU_ANNUAL,
                    modifier = Modifier.weight(1f)
                ) { selectedSku = JumloBillingManager.SKU_ANNUAL }
            }
            QuietPrimaryButton(
                text = stringResource(R.string.pro_continue),
                onClick = { onPurchase(selectedSku) },
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = onRestore, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.pro_restore))
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun PlanCard(title: String, price: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pillars = QuietTheme.pillars
    Surface(
        onClick = onClick,
        shape = QuietTheme.shapes.card,
        color = if (selected) colors.primaryContainer.copy(alpha = 0.45f) else pillars.cardSurface,
        border = if (selected) BorderStroke(2.dp, colors.primary) else null,
        modifier = modifier
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = QuietTheme.typography.metadataStrong, color = colors.onSurface)
            Text(price, style = QuietTheme.typography.sectionTitle.copy(fontWeight = FontWeight.Bold), color = colors.primary)
        }
    }
}
