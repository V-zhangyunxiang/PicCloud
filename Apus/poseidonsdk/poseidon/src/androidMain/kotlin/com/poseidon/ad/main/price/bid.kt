package com.poseidon.ad.main.price

import com.poseidon.ad.main.adinfo.AdBase
import com.poseidon.ad.main.adinfo.CacheAd

internal abstract class Bidder : Comparable<Bidder> {

    open fun notifyWin() {}
    open fun notifyLoose() {}

    override fun compareTo(other: Bidder): Int {
        return getCacheAd().compareTo(other.getCacheAd())
    }

    override fun toString(): String {
        return getCacheAd().toString()
    }

    abstract fun getCacheAd(): CacheAd<out AdBase>

    open fun allowPreloadWhenFullscreenAdDismissed(): Boolean = false
    open fun allowPreloadWhenAdFailedToShow(): Boolean = false
}