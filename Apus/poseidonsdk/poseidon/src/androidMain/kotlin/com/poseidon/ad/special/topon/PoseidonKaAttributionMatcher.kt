package com.poseidon.ad.special.topon

internal object PoseidonKaAttributionMatcher {

    fun resolveEffectiveAttribution(): AttributionPayload {
        return TopOnAttributionSource.resolveForAdLoad()
    }

    fun matchesKeySuffix(
        keySuffix: String,
        attribution: AttributionPayload,
    ): Boolean {
        if (keySuffix.isEmpty()) {
            return false
        }
        return attribution.campaign.matchesKeySuffix(keySuffix) ||
                attribution.channel.matchesKeySuffix(keySuffix)
    }

    fun describe(attribution: AttributionPayload): String {
        return "channel=${attribution.channel},campaign=${attribution.campaign?.extractCampaignIdentifier()}"
    }
}

fun String.extractCampaignIdentifier(): String? {
    val regex = Regex("""_([^(_（\[【]+)(?:\s*[(（\[【]|$)""")
    val match = regex.findAll(this).lastOrNull() ?: return null
    return match.groupValues[1].trim()
}

private fun String.normalizeForKa(): String =
    replace(Regex("[^a-zA-Z0-9_]"), "").lowercase()

private fun String?.matchesKeySuffix(keySuffix: String): Boolean {
    if (this.isNullOrEmpty()) return false
    val normalizedValue = this.normalizeForKa()
    val normalizedSuffix = keySuffix.normalizeForKa()
    return normalizedValue.contains(normalizedSuffix, ignoreCase = true)
}