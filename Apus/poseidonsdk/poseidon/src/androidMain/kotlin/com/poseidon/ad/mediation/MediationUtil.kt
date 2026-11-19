package com.poseidon.ad.mediation

import androidx.annotation.IntDef

internal const val MEDIATION_TOPON = 26
internal const val MEDIATION_ADMOB = 2
internal const val MEDIATION_MAX = 5

@IntDef(
    MEDIATION_TOPON,
    MEDIATION_ADMOB,
    MEDIATION_MAX
)
internal annotation class MediationDef

internal fun isValidMediationId(mediationId: Int): Boolean {
    return runCatching {
        matchMediationId(
            mediationId = mediationId,
            topOn = { true },
            admob = { true },
            max = { true }
        )
    }.getOrDefault(false)
}

internal fun <T> matchMediationId(
    @MediationDef mediationId: Int,
    topOn: () -> T,
    admob: () -> T,
    max: () -> T,
): T {
    return when (mediationId) {
        MEDIATION_TOPON -> topOn()
        MEDIATION_ADMOB -> admob()
        MEDIATION_MAX -> max()
        else -> error("Unknown networkId: $mediationId.")
    }
}

internal fun getMediationNameById(@MediationDef networkId: Int): String {
    return matchMediationId(
        mediationId = networkId,
        topOn = { "topOn" },
        admob = { "admob" },
        max = { "max" },
    )
}
