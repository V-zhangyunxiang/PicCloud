package com.poseidon.ad.api

import com.poseidon.ad.main.adinfo.ShowCodeDef

/**
 * A listener for receiving notifications during the lifecycle of an ad.
 */
interface AdShowListener {
    /**
     * Called when a click is recorded for an ad.
     */
    fun onAdClicked() {
    }

    /**
     * Called when the user is about to return to the application after clicking on an ad.
     */
    fun onAdClosed() {
    }

    /**
     * Called when an ad show failed.
     */
    fun onAdShowFailed(@ShowCodeDef code: Int, message: String) {
    }

    /**
     * Called when an impression is recorded for an ad.
     */
    fun onAdShowSuccess() {
    }
}