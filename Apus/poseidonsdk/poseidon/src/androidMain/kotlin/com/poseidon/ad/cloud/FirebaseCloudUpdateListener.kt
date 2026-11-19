package com.poseidon.ad.cloud

@Suppress("SpellCheckingInspection")
interface FirebaseCloudUpdateListener {
    fun onUpdate(updatedKeys: Set<String>)
}