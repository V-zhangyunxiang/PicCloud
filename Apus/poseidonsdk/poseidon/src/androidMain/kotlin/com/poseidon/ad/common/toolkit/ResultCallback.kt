package com.poseidon.ad.common.toolkit

import com.poseidon.ad.main.adinfo.CodeDef
import com.poseidon.ad.main.adinfo.ShowCodeDef

internal sealed interface ResultCallback<out T> {
    data class Success<out T>(val value: T) : ResultCallback<T>
    data class Failure<out T>(@CodeDef val code: Int, val message: String) : ResultCallback<T>
}

sealed interface ShowResultCallback {
    data object Success : ShowResultCallback
    data class Failure(@ShowCodeDef val code: Int, val message: String) : ShowResultCallback
}