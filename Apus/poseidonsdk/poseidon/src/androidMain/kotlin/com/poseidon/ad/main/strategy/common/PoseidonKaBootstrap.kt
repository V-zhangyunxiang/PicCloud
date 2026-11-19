package com.poseidon.ad.main.strategy.common

import com.poseidon.ad.common.toolkit.VersionHelper

internal data class PoseidonKaBootstrapDecision(
    val configuration: PoseidonConfig?,
    val winner: PoseidonKaBootstrapWinner,
    val reason: PoseidonKaBootstrapReason,
    val defaultVersion: VersionHelper?,
    val kaVersion: VersionHelper?,
    val kaKey: String?,
    val kaSource: String?
)

internal enum class PoseidonKaBootstrapWinner(val value: String) {
    DEFAULT("default"),
    KA("ka"),
}

internal enum class PoseidonKaBootstrapReason(val value: String) {
    KA_HIGHER("ka_higher"),
    DEFAULT_HIGHER_OR_EQUAL("default_higher_or_equal"),
    KA_MISSING("ka_missing"),
    KA_INVALID("ka_invalid"),
}

internal fun selectPoseidonKaBootstrapConfiguration(
    defaultConfig: PoseidonConfig?,
    kaCandidateState: PoseidonKaCandidateState,
): PoseidonKaBootstrapDecision {
    val defaultVersion = defaultConfig?.version
    val validKaCandidate = (kaCandidateState as? PoseidonKaCandidateState.Valid)?.candidate
    val kaVersion = validKaCandidate?.version

    return when {
        kaCandidateState is PoseidonKaCandidateState.Missing -> {
            PoseidonKaBootstrapDecision(
                configuration = defaultConfig,
                winner = PoseidonKaBootstrapWinner.DEFAULT,
                reason = PoseidonKaBootstrapReason.KA_MISSING,
                defaultVersion = defaultVersion,
                kaVersion = null,
                kaKey = null,
                kaSource = null
            )
        }

        kaCandidateState is PoseidonKaCandidateState.Invalid -> {
            PoseidonKaBootstrapDecision(
                configuration = defaultConfig,
                winner = PoseidonKaBootstrapWinner.DEFAULT,
                reason = PoseidonKaBootstrapReason.KA_INVALID,
                defaultVersion = defaultVersion,
                kaVersion = kaCandidateState.storedVersion?.let(VersionHelper::tryFrom),
                kaKey = kaCandidateState.remoteKey,
                kaSource = null
            )
        }

        defaultVersion == null -> {
            PoseidonKaBootstrapDecision(
                configuration = validKaCandidate?.configuration,
                winner = PoseidonKaBootstrapWinner.KA,
                reason = PoseidonKaBootstrapReason.KA_HIGHER,
                defaultVersion = null,
                kaVersion = kaVersion,
                kaKey = validKaCandidate?.remoteKey,
                kaSource = "cold_start"
            )
        }

        kaVersion == null -> {
            PoseidonKaBootstrapDecision(
                configuration = defaultConfig,
                winner = PoseidonKaBootstrapWinner.DEFAULT,
                reason = PoseidonKaBootstrapReason.KA_INVALID,
                defaultVersion = defaultVersion,
                kaVersion = null,
                kaKey = null,
                kaSource = null
            )
        }

        kaVersion > defaultVersion -> {
            PoseidonKaBootstrapDecision(
                configuration = validKaCandidate.configuration,
                winner = PoseidonKaBootstrapWinner.KA,
                reason = PoseidonKaBootstrapReason.KA_HIGHER,
                defaultVersion = defaultVersion,
                kaVersion = kaVersion,
                kaKey = validKaCandidate.remoteKey,
                kaSource = "cold_start"
            )
        }

        else -> {
            PoseidonKaBootstrapDecision(
                configuration = defaultConfig,
                winner = PoseidonKaBootstrapWinner.DEFAULT,
                reason = PoseidonKaBootstrapReason.DEFAULT_HIGHER_OR_EQUAL,
                defaultVersion = defaultVersion,
                kaVersion = kaVersion,
                kaKey = validKaCandidate.remoteKey,
                kaSource = null
            )
        }
    }
}
