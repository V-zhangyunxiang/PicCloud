package com.poseidon.ad.statistics

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.AnyThread
import androidx.core.os.bundleOf
import com.google.firebase.analytics.FirebaseAnalytics

@Suppress("SpellCheckingInspection")
class FirebaseAnalyticsImpl(private val context: Context) :
    com.poseidon.ad.statistics.FirebaseAnalytics {
    override fun logEvent(name: String, parameters: Map<String, Any?>) {
        val safeParameters = firebaseSafeParameters(parameters)
        firebaseAnalyticsInstance(context).logEvent(name, bundleOf(*safeParameters.toList().toTypedArray()))
    }

    override fun setUserProperty(name: String, value: String?) {
        firebaseAnalyticsInstance(context).setUserProperty(name, value)
    }

    companion object {
        @Volatile
        private var firebaseAnalytics: FirebaseAnalytics? = null

        @AnyThread
        @SuppressLint("MissingPermission") // Permission is added by the access party
        private fun firebaseAnalyticsInstance(context: Context): FirebaseAnalytics {
            if (firebaseAnalytics == null) {
                synchronized(FirebaseAnalyticsImpl::class.java) {
                    if (firebaseAnalytics == null) {
                        firebaseAnalytics = FirebaseAnalytics.getInstance(context)
                    }
                }
            }
            return firebaseAnalytics!!
        }
    }
}
