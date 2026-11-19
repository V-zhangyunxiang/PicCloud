package com.poseidon.ad.main.strategy.common

import com.poseidon.ad.common.toolkit.VersionHelper

internal data class ValidPoseidonKaCandidate(
    val remoteKey: String,
    val suffix: String,
    val configuration: PoseidonConfig,
    val version: VersionHelper,
)

internal sealed interface PoseidonKaCandidateState {
    data object Missing : PoseidonKaCandidateState
    data class Invalid(
        val remoteKey: String?,
        val storedVersion: String? = null,
    ) : PoseidonKaCandidateState
    data class Valid(
        val candidate: ValidPoseidonKaCandidate,
    ) : PoseidonKaCandidateState
}
