package com.example.supplementtracker.service

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.example.supplementtracker.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayStoreProduct(
    val productId: String,
    val title: String,
    val description: String,
    val formattedPrice: String
)

enum class PlayBillingNotice {
    PURCHASE_COMPLETED,
    PURCHASE_PENDING,
    PURCHASE_CANCELLED,
    RESTORE_COMPLETED,
    VERIFICATION_FAILED,
    VERIFICATION_NOT_CONFIGURED,
    STORE_UNAVAILABLE
}

data class PlayBillingState(
    val isReady: Boolean = false,
    val isLoading: Boolean = false,
    val products: List<PlayStoreProduct> = emptyList(),
    val purchasingProductId: String? = null,
    val notice: PlayBillingNotice? = null
)

class GooglePlayBillingService(
    context: Context,
    private val entitlementManager: EntitlementManager,
    private val verifier: PurchaseSignatureVerifier = PlayPurchaseVerifier(BuildConfig.PLAY_BILLING_PUBLIC_KEY)
) : PurchasesUpdatedListener {
    private data class PurchaseOption(val details: ProductDetails, val offerToken: String)

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PlayBillingState())
    val state: StateFlow<PlayBillingState> = mutableState.asStateFlow()
    private val purchaseOptions = mutableMapOf<String, PurchaseOption>()
    private var isConnecting = false

    private val billingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun start() {
        if (billingClient.isReady) {
            refresh()
            return
        }
        if (isConnecting) return
        isConnecting = true
        mutableState.update { it.copy(isLoading = true) }
        billingClient.startConnection(connectionListener)
    }

    fun refresh() {
        if (!billingClient.isReady) {
            start()
            return
        }
        queryProducts()
        queryPurchases()
    }

    fun restorePurchases() {
        if (!billingClient.isReady) {
            start()
            mutableState.update { it.copy(notice = PlayBillingNotice.STORE_UNAVAILABLE) }
            reportRestoreResult("store_unavailable")
            return
        }
        queryPurchases(PlayBillingNotice.RESTORE_COMPLETED)
    }

    fun purchase(activity: Activity, productId: String) {
        val option = purchaseOptions[productId] ?: run {
            mutableState.update { it.copy(notice = PlayBillingNotice.STORE_UNAVAILABLE) }
            reportPurchaseResult("store_unavailable", productId)
            return
        }
        mutableState.update { it.copy(purchasingProductId = productId, notice = null) }
        val result = billingClient.launchBillingFlow(activity, billingFlowParams(option))
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            mutableState.update { it.copy(purchasingProductId = null, notice = PlayBillingNotice.STORE_UNAVAILABLE) }
            reportPurchaseResult("store_unavailable", productId)
        }
    }

    fun clearNotice() {
        mutableState.update { it.copy(notice = null) }
    }

    fun close() {
        billingClient.endConnection()
        scope.cancel()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        val attemptedProductId = mutableState.value.purchasingProductId
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> scope.launch { handlePurchaseUpdate(purchases.orEmpty()) }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                mutableState.update { it.copy(purchasingProductId = null, notice = PlayBillingNotice.PURCHASE_CANCELLED) }
                reportPurchaseResult("cancelled", attemptedProductId)
            }
            else -> {
                mutableState.update { it.copy(purchasingProductId = null, notice = PlayBillingNotice.STORE_UNAVAILABLE) }
                reportPurchaseResult("store_unavailable", attemptedProductId)
            }
        }
    }

    private val connectionListener = object : BillingClientStateListener {
        override fun onBillingSetupFinished(result: BillingResult) {
            isConnecting = false
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                mutableState.update { it.copy(isLoading = false, notice = PlayBillingNotice.STORE_UNAVAILABLE) }
                return
            }
            mutableState.update { it.copy(isReady = true) }
            refresh()
        }

        override fun onBillingServiceDisconnected() {
            isConnecting = false
            mutableState.update { it.copy(isReady = false, isLoading = false) }
        }
    }

    private fun queryProducts() {
        mutableState.update { it.copy(isLoading = true) }
        billingClient.queryProductDetailsAsync(productQueryParams()) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                mutableState.update { it.copy(isLoading = false, notice = PlayBillingNotice.STORE_UNAVAILABLE) }
                reportProductsLoaded("store_unavailable")
                return@queryProductDetailsAsync
            }
            applyProductDetails(queryResult.productDetailsList)
        }
    }

    private fun productQueryParams(): QueryProductDetailsParams {
        val products = CommercialProductCatalog.products.map { commercial ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(commercial.productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        return QueryProductDetailsParams.newBuilder().setProductList(products).build()
    }

    private fun applyProductDetails(details: List<ProductDetails>) {
        purchaseOptions.clear()
        val states = details.mapNotNull { detail ->
            val offer = selectOffer(detail) ?: return@mapNotNull null
            purchaseOptions[detail.productId] = PurchaseOption(detail, offer.offerToken)
            PlayStoreProduct(detail.productId, detail.title, detail.description, recurringPrice(offer))
        }
        val order = CommercialProductCatalog.products.map { it.productId }
        mutableState.update {
            it.copy(
                products = states.sortedBy { state -> order.indexOf(state.productId) },
                isLoading = false,
                notice = if (states.isEmpty()) PlayBillingNotice.STORE_UNAVAILABLE else it.notice
            )
        }
        reportProductsLoaded(if (states.isEmpty()) "empty" else "success")
    }

    private fun selectOffer(details: ProductDetails): ProductDetails.SubscriptionOfferDetails? {
        val offers = details.subscriptionOfferDetails.orEmpty()
        return offers.firstOrNull { it.offerId == null } ?: offers.firstOrNull()
    }

    private fun recurringPrice(offer: ProductDetails.SubscriptionOfferDetails): String {
        return offer.pricingPhases.pricingPhaseList.lastOrNull()?.formattedPrice.orEmpty()
    }

    private fun billingFlowParams(option: PurchaseOption): BillingFlowParams {
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(option.details)
            .setOfferToken(option.offerToken)
            .build()
        return BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams)).build()
    }

    private suspend fun handlePurchaseUpdate(purchases: List<Purchase>) {
        val attemptedProductId = mutableState.value.purchasingProductId
        val completed = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        val pending = purchases.any { it.purchaseState == Purchase.PurchaseState.PENDING }
        if (completed.isEmpty()) {
            val notice = if (pending) PlayBillingNotice.PURCHASE_PENDING else PlayBillingNotice.STORE_UNAVAILABLE
            mutableState.update { it.copy(purchasingProductId = null, notice = notice) }
            reportPurchaseResult(if (pending) "pending" else "store_unavailable", attemptedProductId)
            return
        }
        val verified = completed.filter(::isVerifiedPurchase)
        if (verified.isEmpty()) {
            reportVerificationFailure(attemptedProductId)
            return
        }
        verified.forEach(::acknowledgeIfNeeded)
        val productId = verified.firstNotNullOfOrNull { it.products.firstOrNull() } ?: attemptedProductId
        reportPurchaseResult("success", productId)
        queryPurchases(PlayBillingNotice.PURCHASE_COMPLETED)
    }

    private fun queryPurchases(successNotice: PlayBillingNotice? = null) {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                mutableState.update { it.copy(notice = PlayBillingNotice.STORE_UNAVAILABLE) }
                if (successNotice == PlayBillingNotice.RESTORE_COMPLETED) reportRestoreResult("store_unavailable")
                return@queryPurchasesAsync
            }
            applyOwnedPurchases(purchases, successNotice)
        }
    }

    private fun applyOwnedPurchases(purchases: List<Purchase>, successNotice: PlayBillingNotice?) {
        val completed = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        val verified = completed.filter(::isVerifiedPurchase)
        val productIds = verified.flatMap { it.products }
        entitlementManager.replaceFromStore(CommercialEntitlementResolver.resolve(productIds))
        verified.forEach(::acknowledgeIfNeeded)
        val notice = verificationNotice(completed, verified) ?: successNotice
        mutableState.update { it.copy(purchasingProductId = null, notice = notice) }
        if (successNotice == PlayBillingNotice.RESTORE_COMPLETED) {
            reportRestoreResult(restoreResult(notice))
        }
    }

    private fun isVerifiedPurchase(purchase: Purchase): Boolean {
        if (purchase.packageName != appContext.packageName) return false
        if (purchase.products.none(::isKnownProduct)) return false
        return verifier.verify(purchase.originalJson, purchase.signature)
    }

    private fun isKnownProduct(productId: String): Boolean {
        return CommercialProductCatalog.products.any { it.productId == productId }
    }

    private fun verificationNotice(completed: List<Purchase>, verified: List<Purchase>): PlayBillingNotice? {
        if (completed.isEmpty() || verified.isNotEmpty()) return null
        return if (verifier.isConfigured) {
            PlayBillingNotice.VERIFICATION_FAILED
        } else {
            PlayBillingNotice.VERIFICATION_NOT_CONFIGURED
        }
    }

    private fun reportVerificationFailure(productId: String?) {
        val notice = if (verifier.isConfigured) {
            PlayBillingNotice.VERIFICATION_FAILED
        } else {
            PlayBillingNotice.VERIFICATION_NOT_CONFIGURED
        }
        mutableState.update { it.copy(purchasingProductId = null, notice = notice) }
        val result = if (verifier.isConfigured) "verification_failed" else "verification_not_configured"
        reportPurchaseResult(result, productId)
    }

    private fun restoreResult(notice: PlayBillingNotice?): String = when (notice) {
        PlayBillingNotice.VERIFICATION_FAILED -> "verification_failed"
        PlayBillingNotice.VERIFICATION_NOT_CONFIGURED -> "verification_not_configured"
        PlayBillingNotice.RESTORE_COMPLETED -> "success"
        else -> "success"
    }

    private fun reportProductsLoaded(result: String) {
        DiagnosticsReporter.event(
            appContext,
            "billing_products_loaded",
            CommercialTelemetryFields.result(result, "play_store")
        )
    }

    private fun reportPurchaseResult(result: String, productId: String?) {
        DiagnosticsReporter.event(
            appContext,
            "billing_purchase_result",
            CommercialTelemetryFields.result(result, "play_store", productId)
        )
    }

    private fun reportRestoreResult(result: String) {
        DiagnosticsReporter.event(
            appContext,
            "billing_restore_result",
            CommercialTelemetryFields.result(result, "play_store")
        )
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                mutableState.update { it.copy(notice = PlayBillingNotice.STORE_UNAVAILABLE) }
            }
        }
    }
}
