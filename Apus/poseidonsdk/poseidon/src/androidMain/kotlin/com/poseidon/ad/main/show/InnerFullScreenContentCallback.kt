package com.poseidon.ad.main.show

import com.poseidon.ad.main.adinfo.ShowCodeDef

/**
 * Callback to be invoked when ads show and dismiss full screen content,
 * such as a fullscreen ad experience or an in-app browser.
 */
interface InnerFullScreenContentCallback {
    /**
     * Called when a click is recorded for an ad.
     */
    fun onAdClicked() {
    }

    /**
     * Called when the ad dismissed full screen content.
     */
    fun onAdDismissedFullScreenContent() {
    }

    /**
     * Called when the ad failed to show full screen content.
     */
    fun onAdFailedToShowFullScreenContent(@ShowCodeDef code: Int, message: String) {
    }

    /**
     * Called when the ad showed the full screen content.
     */
    fun onAdShowedFullScreenContent() {
    }
}