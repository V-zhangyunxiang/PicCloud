package com.poseidon.ad.common.storage.db

import com.poseidon.ad.main.adinfo.AdTypeDef

/**
 * 广告收入记录实体，描述单次回传的主要字段。
 */
internal data class AdRevenueRecord(
    val revenue: Double,
    val eCPM: Double,
    val currencyCode: String,
    val precision: Int,
    val networkId: Int,
    val adSourceId: Int,
    val adSourcePlacementId: String,
    val adUnitId: String,
    @AdTypeDef val adType: Int,
    val createTime: Long,
)
