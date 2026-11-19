package com.poseidon.ad.statistics.core

import com.poseidon.ad.main.adinfo.AdTypeDef
import com.poseidon.ad.main.adinfo.getAdTypeName
import com.poseidon.ad.mediation.MediationDef
import com.poseidon.ad.mediation.getMediationNameById
import com.poseidon.ad.mediation.topon.TopOnAdmobExtractor
import com.poseidon.ad.statistics.Reporter

internal class TopOnAdmobBidAnalysisReporter(
    private val sessionId: String,
    private val sceneName: String,
    private val adUnitId: String,
    @AdTypeDef private val adType: Int,
    @MediationDef private val networkId: Int,
    private val sdkReportedRevenue: Double,
    private val sdkReportedPrecision: String?,
    private val extractionResult: TopOnAdmobExtractor.BidExtractionResult,
    private val topAdNetworkPlacementId: String?,
    private val topAdNetworkName: String?
) : Reporter(eventName = NAME) {

    override fun makeParameters(): Map<String, Any?> {
        val extractedRevenue = extractionResult.winnerBidPrice
        return mapOf(
            "session_id" to sessionId,
            "scene_name" to sceneName,
            "placement_id" to adUnitId,
            "ad_type" to getAdTypeName(adType),
            "mediation_name" to getMediationNameById(networkId),
            "sdk_reported_revenue" to sdkReportedRevenue,
            "sdk_reported_precision" to sdkReportedPrecision,
            "extracted_bid_revenue" to extractedRevenue,
            "sdk_vs_extracted_bid_delta" to extractedRevenue
                ?.takeIf { sdkReportedRevenue > 0.0 }
                ?.let { sdkReportedRevenue - it },
            "selected_policy" to if (extractedRevenue != null) {
                TopOnAdmobExtractor.BidSelectionPolicy.MAX_CACHE_BIDPRICE.name
            } else {
                TopOnAdmobExtractor.BidSelectionPolicy.NO_BID_FOUND.name
            },
            "total_caches" to extractionResult.totalCaches,
            "matching_caches" to extractionResult.matchingCaches,
            "winner_cache_index" to extractionResult.winnerIndex,
            "winner_bid_price" to extractionResult.winnerBidPrice,
            "winner_ecpm" to extractionResult.winnerEcpm,
            "top_ad_network_placement_id" to topAdNetworkPlacementId,
            "top_ad_network_name" to topAdNetworkName,
            "cache_inspections" to extractionResult.allInspections.toString().take(MAX_STRING_VALUE_LENGTH)
        ).filterValues { it != null }
    }

    companion object {
        private const val NAME = "poseidon_topon_admob_bid_analysis"
        private const val MAX_STRING_VALUE_LENGTH = 100
    }
}
