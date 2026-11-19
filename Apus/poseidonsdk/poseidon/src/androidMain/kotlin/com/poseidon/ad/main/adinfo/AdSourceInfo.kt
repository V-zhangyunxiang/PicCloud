package com.poseidon.ad.main.adinfo

import com.poseidon.ad.main.price.AdSourceIdDef

/**
 * 描述当前广告源的基础信息，包含 ID、位置信息以及名称。
 */
data class AdSourceInfo(
    @AdSourceIdDef val adSourceId: Int,
    val adSourcePlacementId: String,
    val adSourceName: String? = null,
    val rawSourceId: String? = null,
    val rawSourceInstanceId: String? = null,
)
