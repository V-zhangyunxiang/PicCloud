package com.poseidon.ad.api

/**
 * Interface definition for a callback to be invoked when the user earned a reward.
 */
interface OnUserEarnedRewardListener {
    /**
     * Called when the user earned a reward. The app is responsible for crediting the user with the reward.
     * @param reward the reward to grant the user.
     */
    fun onUserEarnedReward(reward: RewardItem)
}