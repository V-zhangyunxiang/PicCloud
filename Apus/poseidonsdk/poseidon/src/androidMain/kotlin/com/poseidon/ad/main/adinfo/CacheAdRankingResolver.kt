package com.poseidon.ad.main.adinfo

import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor
import com.poseidon.ad.main.price.Bidder
import com.poseidon.ad.main.price.priorityByAdSourceId

internal data class BidderSelectionResult(
    val bidders: List<Bidder>,
    val winner: Bidder?,
)

/**
 * Reads the high-eCPM threshold once from remote config.
 */
internal fun readHighEcpmThreshold(): Double {
    return PoseidonSetupBaseProcessor.poseidonSetupProcessor().remoteConfig
        .getString("high_ecpm_threshold")
        .toDoubleOrNull() ?: 150.0
}

/**
 * Pure comparison: determines ordering between two cache ads.
 * All volatile fields are pre-extracted; threshold is passed in.
 *
 * Returns negative if left < right, positive if left > right, zero if equal.
 */
internal fun compareCacheAdRank(
    leftEcpm: Double?,
    rightEcpm: Double?,
    leftNumber: Int,
    rightNumber: Int,
    @AdTypeDef leftAdType: Int,
    @AdTypeDef rightAdType: Int,
    leftAdSourceId: Int,
    rightAdSourceId: Int,
    threshold: Double,
): Int {
    val leCPM = leftEcpm?.takeIf { it > 0 }
    val reCPM = rightEcpm?.takeIf { it > 0 }

    return if (leCPM == null) {
        if (reCPM == null) {
            tieBreak(leftNumber, rightNumber, leftAdType, rightAdType, leftAdSourceId, rightAdSourceId)
        } else {
            if (reCPM >= threshold) -1
            else tieBreak(leftNumber, rightNumber, leftAdType, rightAdType, leftAdSourceId, rightAdSourceId)
        }
    } else {
        if (reCPM == null) {
            if (leCPM >= threshold) 1
            else tieBreak(leftNumber, rightNumber, leftAdType, rightAdType, leftAdSourceId, rightAdSourceId)
        } else {
            leCPM.compareTo(reCPM)
        }
    }
}

private fun tieBreak(
    leftNumber: Int,
    rightNumber: Int,
    @AdTypeDef leftAdType: Int,
    @AdTypeDef rightAdType: Int,
    leftAdSourceId: Int,
    rightAdSourceId: Int,
): Int {
    return if (leftAdType == rightAdType) {
        leftNumber.compareTo(rightNumber).takeIf { it != 0 }
            ?: priorityByAdSourceId(leftAdSourceId).compareTo(priorityByAdSourceId(rightAdSourceId))
    } else {
        priorityByAdType(leftAdType).compareTo(priorityByAdType(rightAdType))
    }
}

/**
 * Single-pass winner selection across a list of ready cache ads.
 * Reads threshold once, then iterates candidates without sorting.
 */
internal fun selectTopCacheAd(
    candidates: List<CacheAd<out AdBase>>,
    threshold: Double,
): CacheAd<out AdBase>? {
    var best: CacheAd<out AdBase>? = null
    for (candidate in candidates) {
        val current = best
        if (current == null) {
            best = candidate
        } else {
            val cmp = compareCacheAdRank(
                leftEcpm = candidate.cachedEcpm,
                rightEcpm = current.cachedEcpm,
                leftNumber = candidate.number,
                rightNumber = current.number,
                leftAdType = candidate.cachedAdType,
                rightAdType = current.cachedAdType,
                leftAdSourceId = candidate.cachedAdSourceId,
                rightAdSourceId = current.cachedAdSourceId,
                threshold = threshold,
            )
            if (cmp > 0) {
                best = candidate
            }
        }
    }
    return best
}

/**
 * Single-pass winner selection across a list of bidders.
 * Reads threshold once, then iterates candidates without sorting.
 * Returns the winning bidder and the full candidate list (for notify logic).
 */
internal fun selectTopBidder(
    candidates: List<Bidder>,
    threshold: Double,
): Bidder? {
    var best: Bidder? = null
    for (candidate in candidates) {
        val current = best
        if (current == null) {
            best = candidate
        } else {
            val cAd = candidate.getCacheAd()
            val bAd = current.getCacheAd()
            val cmp = compareCacheAdRank(
                leftEcpm = cAd.cachedEcpm,
                rightEcpm = bAd.cachedEcpm,
                leftNumber = cAd.number,
                rightNumber = bAd.number,
                leftAdType = cAd.cachedAdType,
                rightAdType = bAd.cachedAdType,
                leftAdSourceId = cAd.cachedAdSourceId,
                rightAdSourceId = bAd.cachedAdSourceId,
                threshold = threshold,
            )
            if (cmp > 0) {
                best = candidate
            }
        }
    }
    return best
}

internal fun selectPrimaryBidderAgainstCompetitors(
    primaryBidder: Bidder?,
    competitorBidders: List<Bidder?>,
    clientBiddingEnabled: Boolean,
    highEcpmThreshold: Double,
): BidderSelectionResult {
    val bidders = if (clientBiddingEnabled) {
        buildList {
            addAll(competitorBidders.filterNotNull())
            primaryBidder?.let(::add)
        }
    } else {
        competitorBidders.forEach { competitor ->
            competitor?.notifyLoose()
        }
        listOfNotNull(primaryBidder)
    }
    return BidderSelectionResult(
        bidders = bidders,
        winner = selectTopBidder(
            candidates = bidders,
            threshold = highEcpmThreshold,
        ),
    )
}
