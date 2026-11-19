package com.poseidon.ad.main.strategy.cryptor

import androidx.annotation.IntRange

@Suppress("SpellCheckingInspection")
class Cryptor {

    external fun encrypt(json: String): String
    external fun decrypt(str: String): String
    external fun isPassableByFlowControl(deviceId: String, @IntRange(from = 0, to = 100) scale: Int): Boolean

    companion object {
        init {
            System.loadLibrary("PoseidonCypt")
        }

        fun prewarm() = Unit
    }
}
