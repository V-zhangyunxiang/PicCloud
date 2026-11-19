package com.poseidon.ad.cloud

class FirebaseCloudException(message: String) : RuntimeException(message)

fun throwRemoteConfigurationFetchException(message: String): Nothing {
    throw FirebaseCloudException(message)
}