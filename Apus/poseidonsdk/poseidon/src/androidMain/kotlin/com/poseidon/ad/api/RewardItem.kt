package com.poseidon.ad.api

/**
 * This class describes a reward credited to a user for interacting with [com.poseidon.ad.main.adinfo.RewardedAdBase]
 * @param amount the reward amount.
 * @param type the type of the reward.
 */
data class RewardItem(
    val amount: Int,
    val type: String
) {
    companion object {
        val UNKNOWN: RewardItem = RewardItem(amount = 1, type = "unknown")
    }
}