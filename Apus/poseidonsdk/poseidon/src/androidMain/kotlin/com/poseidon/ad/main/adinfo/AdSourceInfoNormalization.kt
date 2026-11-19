package com.poseidon.ad.main.adinfo

import com.poseidon.ad.main.price.AD_SOURCE_ID_Admob
import com.poseidon.ad.main.price.AdSourceIdDef

private val ADMOB_AD_UNIT_ID_REGEX = Regex("""ca-app-pub-\d+/\d+""")

internal fun normalizeAdmobPlacementId(raw: String?): String {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return value
    ADMOB_AD_UNIT_ID_REGEX.find(value)?.value?.let { return it }
    return value.substringBefore("/cak=").substringBefore("?").ifEmpty { value }
}

internal fun normalizePlacementIdByAdSource(
    @AdSourceIdDef adSourceId: Int,
    placementId: String?
): String {
    return when (adSourceId) {
        AD_SOURCE_ID_Admob -> normalizeAdmobPlacementId(placementId)
        else -> placementId.orEmpty()
    }
}
