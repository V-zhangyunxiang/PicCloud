package com.poseidon.ad.main.load

import com.poseidon.ad.api.AdShowListener

@Suppress("SpellCheckingInspection")
interface InnerAdLoadAndShowCallback<T> : InnerAdLoadCallback<T>, AdShowListener