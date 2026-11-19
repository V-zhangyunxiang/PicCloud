package com.poseidon.ad.cloud

@Suppress("SpellCheckingInspection")
interface FirebaseCloud {
    fun getString(key: String): String

    fun getBoolean(key: String): Boolean

    fun addOnConfigUpdateListener(configUpdateListener: FirebaseCloudUpdateListener)

    fun onAttach()

    fun callOnFetch()
}