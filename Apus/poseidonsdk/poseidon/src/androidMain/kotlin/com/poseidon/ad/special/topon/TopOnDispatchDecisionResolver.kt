package com.poseidon.ad.special.topon

/**
 * Result of pre-checking whether a TopOn custom map dispatch is needed.
 */
internal data class TopOnDispatchVerdict(
    val normalizedChannel: String?,
    val normalizedCampaign: String?,
    val requiresDispatch: Boolean,
)

/**
 * Pure function: determines whether the TopOn custom map needs updating,
 * based on normalized attribution values vs last-sent values.
 * No side effects, no SP/remote reads.
 */
internal fun evaluateTopOnDispatchNeed(
    rawChannel: String?,
    rawCampaign: String?,
    sentChannel: String?,
    sentCampaign: String?,
): TopOnDispatchVerdict {
    val normalizedChannel = rawChannel.sanitizeForTopOn()
    val normalizedCampaign = rawCampaign.sanitizeForTopOn()
    if (normalizedChannel.isNullOrEmpty()) {
        return TopOnDispatchVerdict(
            normalizedChannel = null,
            normalizedCampaign = null,
            requiresDispatch = false,
        )
    }
    return TopOnDispatchVerdict(
        normalizedChannel = normalizedChannel,
        normalizedCampaign = normalizedCampaign,
        requiresDispatch = normalizedChannel != sentChannel || normalizedCampaign != sentCampaign,
    )
}

private val topOnSanitizer = Regex("[^a-zA-Z0-9_]")

private fun String?.sanitizeForTopOn(): String? {
    if (this.isNullOrEmpty()) return null
    val cleaned = replace(topOnSanitizer, "")
    return cleaned.takeUnless(String::isEmpty)
}
