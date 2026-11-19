package com.poseidon.ad.main.adinfo

import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor
import com.poseidon.ad.statistics.base.ErrorDetectionReporter

abstract class BannerAdBase : AdBase() {

    final override fun getAdType(): Int = AD_TYPE_BANNER

    override fun isReady(): Boolean {
        val message = "`BannerAdBase.isReady` API is not supported for banner ad."
        if (PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor().debug) {
            error(message)
        } else {
            ErrorDetectionReporter(
                name = "BannerAdBaseError",
                extra = message
            )
            return false
        }
    }

    override fun destroy() {
        val message = "`BannerAdBase.destroy` API is not supported for banner ad."
        if (PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor().debug) {
            error(message)
        } else {
            ErrorDetectionReporter(
                name = "BannerAdBaseError",
                extra = message
            )
        }
    }

    override fun eCPM(): Double? {
        val message = "`BannerAdBase.eCPM` API is not supported for banner ad."
        if (PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor().debug) {
            error(message)
        } else {
            ErrorDetectionReporter(
                name = "BannerAdBaseError",
                extra = message
            )
            return super.eCPM()
        }
    }
}