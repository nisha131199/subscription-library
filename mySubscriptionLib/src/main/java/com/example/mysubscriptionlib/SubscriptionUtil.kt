package com.example.mysubscriptionlib

import android.app.Activity
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsParams.Product
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.collect.ImmutableList

class SubscriptionUtil(private val productId: String, private val planIds: ArrayList<String>, private val context: Activity, private val listener: InAppSubscriptionListener): PurchasesUpdatedListener {

    private val tag = "Billing In-App Subscription"

    private var billingClient: BillingClient? = null
    private val productDetails: MutableList<ProductDetails> = mutableListOf()

    init {
        startEstablish()
    }

    private fun startEstablish() {
        billingClient = BillingClient
            .newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                Log.e(tag, "${billingResult.responseCode}")
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // The BillingClient is ready. You can query purchases here.
                    Log.e(tag, "connection established")
                    queryProductDetails()
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.e(tag, "connection not establishing, retry")
                startEstablish()
            }
        })
    }

    private fun queryProductDetails() {
        val productList = arrayListOf<Product>()
        productList.add(
            Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient?.queryProductDetailsAsync(
            queryProductDetailsParams
        ) { billingResult, productDetailsList ->
            Log.e(tag, "${billingResult.responseCode}")
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.e(tag, "product details, ${productDetailsList.size}")
                productDetails.clear()
                productDetailsList.map { product ->
                    product.subscriptionOfferDetails?.forEach {
                        if (planIds.contains(it.basePlanId)) {
                            productDetails.add(product)
                            Log.e(tag, "adding plan details ${it.basePlanId}")
                        }
                    }
                }
                listener.productDetails(productDetails)
            } else {
                Log.e(tag, "code = " + billingResult.responseCode)
            }
        }
    }

    fun launchBilling(productDetail: ProductDetails, basePlanId: String?) {
        val offerToken =
            productDetail.subscriptionOfferDetails?.filter { it.basePlanId.equals(basePlanId, true) }
                ?.get(0)?.offerToken ?: ""
        Log.e(tag, "product details ${productDetail.productId}")
        val productDetailsParamsList = ImmutableList.of(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetail)
                .setOfferToken(offerToken)
                .build()
        )
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()
        billingClient?.launchBillingFlow(context, billingFlowParams)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, list: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK
            && list != null
        ) {
            Log.e(tag, "Purchase done")
            for (purchase in list) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            Log.e(tag, "ITEM_ALREADY_OWNED")
            list?.let {
                listener.subscriptionPurchased(it[0])
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.e(tag, "Purchase canceled")
            listener.subscriptionPurchasedCancelled()
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.SERVICE_DISCONNECTED) {
            Log.e(tag, "SERVICE DISCONNECTED")
            listener.subscriptionNotPurchased()
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE) {
            Log.e(tag, "SERVICE UNAVAILABLE")
            listener.subscriptionNotPurchased()
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE) {
            Log.e(tag, "BILLING UNAVAILABLE")
            listener.subscriptionNotPurchased()
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_UNAVAILABLE) {
            Log.e(tag, "ITEM UNAVAILABLE")
            listener.subscriptionNotPurchased()
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.DEVELOPER_ERROR) {
            Log.e(tag, "DEVELOPER ERROR")
            listener.subscriptionNotPurchased()
        } else {
            // Handle any other error codes.
            Log.e(tag, "Some other error")
            listener.subscriptionNotPurchased()
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        Log.e(tag, "${purchase.purchaseState}")
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient?.acknowledgePurchase(
                    acknowledgePurchaseParams
                ) { billingResult ->
                    Log.e(tag, "${billingResult.responseCode}")
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        //in-app subscription activated
                        listener.subscriptionPurchased(purchase)
                    }
                }
            } else {
                listener.subscriptionPurchased(purchase)
            }
        } else {
            listener.subscriptionNotPurchased()
        }
    }
}

interface InAppSubscriptionListener {
    fun subscriptionPurchased(purchase: Purchase)
    fun subscriptionNotPurchased()
    fun subscriptionPurchasedCancelled()
    fun productDetails(productDetails: MutableList<ProductDetails>)
}