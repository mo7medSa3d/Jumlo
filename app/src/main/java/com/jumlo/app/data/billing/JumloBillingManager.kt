package com.jumlo.app.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

data class ProEntitlement(
    val isPro: Boolean = false,
    val isLifetime: Boolean = false,
    val lifetimePrice: String? = null,
    val annualPrice: String? = null
)

/** Google Play Billing manager supporting Lifetime one-time purchase (INAPP) and Annual subscription (SUBS). */
@Singleton
class JumloBillingManager @Inject constructor(@ApplicationContext private val context: Context) : PurchasesUpdatedListener {
    companion object {
        const val SKU_LIFETIME = "jumlo_pro_lifetime"
        const val SKU_ANNUAL = "jumlo_pro_annual"
    }

    private val _entitlement = MutableStateFlow(ProEntitlement())
    val entitlement: StateFlow<ProEntitlement> = _entitlement.asStateFlow()
    private val productDetailsMap = ConcurrentHashMap<String, ProductDetails>()
    private val entitlementLock = Any()
    private val connecting = AtomicBoolean(false)
    @Volatile private var hasLifetime = false
    @Volatile private var hasAnnual = false

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun connect() {
        if (client.isReady) { queryProducts(); restorePurchases(); return }
        if (!connecting.compareAndSet(false, true)) return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting.set(false)
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                    restorePurchases()
                }
            }
            override fun onBillingServiceDisconnected() {
                connecting.set(false)
            }
        })
    }

    private fun queryProducts() {
        val inAppParams = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(SKU_LIFETIME).setProductType(BillingClient.ProductType.INAPP).build())
        ).build()
        client.queryProductDetailsAsync(inAppParams) { res, list ->
            if (res.responseCode == BillingClient.BillingResponseCode.OK) {
                val item = list.firstOrNull() ?: return@queryProductDetailsAsync
                productDetailsMap[SKU_LIFETIME] = item
                _entitlement.update { it.copy(lifetimePrice = item.oneTimePurchaseOfferDetails?.formattedPrice) }
            }
        }
        val subsParams = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(SKU_ANNUAL).setProductType(BillingClient.ProductType.SUBS).build())
        ).build()
        client.queryProductDetailsAsync(subsParams) { res, list ->
            if (res.responseCode == BillingClient.BillingResponseCode.OK) {
                val item = list.firstOrNull() ?: return@queryProductDetailsAsync
                productDetailsMap[SKU_ANNUAL] = item
                val price = item.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
                _entitlement.update { it.copy(annualPrice = price) }
            }
        }
    }

    fun launchPurchase(activity: Activity, sku: String): Boolean {
        if (!client.isReady) { connect(); return false }
        val details = productDetailsMap[sku] ?: run { queryProducts(); return false }
        val offerToken = if (sku == SKU_ANNUAL) details.subscriptionOfferDetails?.firstOrNull()?.offerToken else null
        if (sku == SKU_ANNUAL && offerToken == null) return false
        val productParam = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply { if (offerToken != null) setOfferToken(offerToken) }
            .build()
        val flowParams = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParam)).build()
        return client.launchBillingFlow(activity, flowParams).responseCode == BillingClient.BillingResponseCode.OK
    }

    fun restorePurchases() {
        if (!client.isReady) { connect(); return }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { res, list ->
            if (res.responseCode == BillingClient.BillingResponseCode.OK) processPurchases(list, BillingClient.ProductType.INAPP)
        }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { res, list ->
            if (res.responseCode == BillingClient.BillingResponseCode.OK) processPurchases(list, BillingClient.ProductType.SUBS)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            processPurchases(purchases, null)
        }
    }

    private fun processPurchases(purchases: List<Purchase>, productTypeScope: String?) {
        var foundLifetime = false
        var foundAnnual = false
        val pendingAcks = mutableListOf<Triple<String, Boolean, Boolean>>()
        for (p in purchases) {
            if (p.purchaseState == Purchase.PurchaseState.PURCHASED) {
                val grantsLifetime = SKU_LIFETIME in p.products
                val grantsAnnual = SKU_ANNUAL in p.products
                if (!p.isAcknowledged) {
                    if (grantsLifetime || grantsAnnual) {
                        pendingAcks += Triple(p.purchaseToken, grantsLifetime, grantsAnnual)
                    }
                } else {
                    if (grantsLifetime) foundLifetime = true
                    if (grantsAnnual) foundAnnual = true
                }
            }
        }
        // Apply acknowledged snapshot before launching async acks so a fast ack callback cannot be overwritten.
        synchronized(entitlementLock) {
            when (productTypeScope) {
                BillingClient.ProductType.INAPP -> hasLifetime = foundLifetime
                BillingClient.ProductType.SUBS -> hasAnnual = foundAnnual
                else -> {
                    if (foundLifetime) hasLifetime = true
                    if (foundAnnual) hasAnnual = true
                }
            }
        }
        publishEntitlement()
        for ((token, grantsLifetime, grantsAnnual) in pendingAcks) {
            val ack = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()
            client.acknowledgePurchase(ack) { ackRes ->
                if (ackRes.responseCode == BillingClient.BillingResponseCode.OK) {
                    synchronized(entitlementLock) {
                        if (grantsLifetime) hasLifetime = true
                        if (grantsAnnual) hasAnnual = true
                    }
                    publishEntitlement()
                }
            }
        }
    }

    private fun publishEntitlement() {
        val (lifetime, pro) = synchronized(entitlementLock) { hasLifetime to (hasLifetime || hasAnnual) }
        _entitlement.update { it.copy(isPro = pro, isLifetime = lifetime) }
    }
}
