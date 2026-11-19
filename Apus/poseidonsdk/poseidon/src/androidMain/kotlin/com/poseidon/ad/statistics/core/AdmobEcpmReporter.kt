package com.poseidon.ad.statistics.core

import com.poseidon.ad.common.toolkit.VersionHelper
import com.poseidon.ad.main.price.AdSourceIdDef
import com.poseidon.ad.mediation.admob.PartyCrawler
import com.poseidon.ad.statistics.Reporter

internal class AdmobEcpmReporter(
    private val sessionId: String,
    private val sceneName: String,
    private val adUnitId: String,
    private val adSourcePlacementId: String?,
    @param:AdSourceIdDef private val adSourceId: Int?,
    private val adSourceName: String?,
    private val rawAdSourceId: String?,
    private val poseidonConfigVersion: VersionHelper?,
    private val extractionResult: PartyCrawler.ExtractionResult
) : Reporter(eventName = NAME) {

    override fun makeParameters(): Map<String, Any?> {
        return mapOf(
            PARAMETER_SESSION_ID_STRING to sessionId,
            PARAMETER_SCENE_NAME_STRING to sceneName,
            PARAMETER_AD_UNIT_ID_STRING to adUnitId,
            PARAMETER_AD_SOURCE_PLACEMENT_ID_STRING to adSourcePlacementId,
            PARAMETER_AD_SOURCE_ID_INT to adSourceId,
            PARAMETER_AD_SOURCE_NAME_STRING to adSourceName,
            PARAMETER_POSEIDON_CONFIG_VERSION_STRING to poseidonConfigVersion?.toString(),
            PARAMETER_STATUS_STRING to extractionResult.status,
            PARAMETER_REASON_STRING to extractionResult.reason,
            PARAMETER_RAW_AD_SOURCE_ID_STRING to rawAdSourceId?.safeParamValue(),
            PARAMETER_RAW_AD_SOURCE_INSTANCE_ID_STRING to
                    extractionResult.adSourceInstanceId?.safeParamValue(),
            PARAMETER_AD_SOURCE_INSTANCE_ID_ORIGIN_STRING to
                    extractionResult.adSourceInstanceIdOrigin?.safeParamValue(),
            PARAMETER_EXT_PRICE_DOUBLE to extractionResult.extPrice,
            PARAMETER_CANDIDATE_COUNT_INT to extractionResult.candidateCount,
            PARAMETER_ANCHOR_MODE_STRING to extractionResult.anchorMode?.safeParamValue(),
            PARAMETER_BEST_CANDIDATE_STRING to extractionResult.bestCandidateClass?.safeParamValue(),
            PARAMETER_BEST_PARENT_STRING to extractionResult.bestParentClass?.safeParamValue(),
            PARAMETER_PRICE_FIELD_STRING to extractionResult.bestPriceField?.safeParamValue()
        ).filterValues { it != null }
    }

    override fun resolvePoseidonConfigVersion(): VersionHelper? = poseidonConfigVersion

    private fun String.safeParamValue(): String = take(MAX_STRING_VALUE_LENGTH)

    companion object {
        private const val NAME = "poseidon_admob_ext"
        private const val MAX_STRING_VALUE_LENGTH = 100
        private const val PARAMETER_SESSION_ID_STRING = "session_id"
        private const val PARAMETER_SCENE_NAME_STRING = "unit_id"
        private const val PARAMETER_AD_UNIT_ID_STRING = "placement_id"
        private const val PARAMETER_AD_SOURCE_PLACEMENT_ID_STRING = "network_placementId"
        private const val PARAMETER_AD_SOURCE_ID_INT = "network_name"
        private const val PARAMETER_AD_SOURCE_NAME_STRING = "network_source_name"
        private const val PARAMETER_POSEIDON_CONFIG_VERSION_STRING = "poseidon_config_version"
        private const val PARAMETER_STATUS_STRING = "status"
        private const val PARAMETER_REASON_STRING = "reason"
        private const val PARAMETER_RAW_AD_SOURCE_ID_STRING = "raw_ad_source_id"
        private const val PARAMETER_RAW_AD_SOURCE_INSTANCE_ID_STRING =
            "raw_ad_source_instance_id"
        private const val PARAMETER_AD_SOURCE_INSTANCE_ID_ORIGIN_STRING =
            "ad_source_instance_id_origin"
        private const val PARAMETER_EXT_PRICE_DOUBLE = "ext_price"
        private const val PARAMETER_CANDIDATE_COUNT_INT = "candidate_count"
        private const val PARAMETER_ANCHOR_MODE_STRING = "anchor_mode"
        private const val PARAMETER_BEST_CANDIDATE_STRING = "best_candidate_cls"
        private const val PARAMETER_BEST_PARENT_STRING = "best_parent_cls"
        private const val PARAMETER_PRICE_FIELD_STRING = "price_field"
    }
}
