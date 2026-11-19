package io.threadtamer.runtime

/**
 * Host-owned policy for the standalone concurrency guard.
 *
 * The defaults are deliberately conservative: vendor executors are made reclaimable and bounded,
 * while thread creation is observed rather than rejected. The host can replace the policy before
 * it initializes any third-party SDK.
 */
data class ConcurrencyLimits(
    val active: Boolean = true,
    val emitDiagnostics: Boolean = false,
    val launchObservationMillis: Long = 30_000L,
    val warmThreadNotice: Int = 32,
    val warmThreadAlarm: Int = 72,
    val legacyHostPauseMillis: Long = 40L,
    val legacyVendorPauseMillis: Long = 140L,
    val legacyOverflowPauseMillis: Long = 20L,
    val legacyPauseCapMillis: Long = 400L,
    val processThreadCeiling: Int = 96,
    val overflowAllowance: Int = 12,
    val failClosedAtCeiling: Boolean = false,
    val taskQueueSlots: Int = 256,
    val generalParallelismCeiling: Int = 4,
    val vendorParallelismCeiling: Int = 2,
    val generalTimerWidth: Int = 2,
    val vendorTimerWidth: Int = 1,
    val mergeVendorExecutors: Boolean = false,
    val reclaimAfterSeconds: Long = 30L,
    val samplePeriodMillis: Long = 1_000L,
    val advisoryLooperNames: Set<String> = setOf("ThreadPoolForeg", "ThreadPoolSingl"),
    val advisoryCallers: Set<String> = setOf(
        "com.facebook.",
        "com.google.android.gms.ads.",
        "com.applovin.",
        "com.fyber."
    ),
    val exemptThreadNames: Set<String> = setOf(
        "main",
        "Finalizer",
        "FinalizerDaemon",
        "ReferenceQueueDaemon",
        "FinalizerWatchdogDaemon",
        "Signal Catcher",
        "RenderThread",
        "Binder:"
    ),
    val exemptCallers: Set<String> = setOf(
        "android.",
        "androidx.",
        "java.",
        "kotlin.",
        "kotlinx.",
        "io.threadtamer.runtime."
    ),
    val vendorCallers: Set<String> = setOf(
        "com.thinkup.",
        "com.anythink.",
        "com.google.android.gms.ads.",
        "com.google.android.gms.internal.ads.",
        "com.applovin.",
        "com.facebook.",
        "com.unity3d.",
        "com.ironsource.adqualitysdk.",
        "com.vungle.",
        "com.fyber.",
        "com.digitalturbine.",
        "com.pangle.",
        "com.bytedance.",
        "com.pgl.",
        "com.apm.",
        "com.bykv.",
        "sg.bigo.",
        "com.smartdigimkttech.",
        "com.aggregatortech.",
        "com.tramini.",
        "com.varbto.",
        "com.iab.omid.",
        "io.bidmachine.",
        "org.chromium.",
        "okhttp3."
    )
)

data class ConcurrencySnapshot(
    val processThreads: Int,
    val daemonThreads: Int,
    val vendorThreads: Int,
    val hostThreads: Int,
    val systemThreads: Int,
    val unclassifiedThreads: Int,
    val interceptedLaunches: Int,
    val rejectedLaunches: Int,
    val constructedWorkers: Int,
    val constructedExecutors: Int,
    val startupWorkers: Int,
    val handlerAdvisories: Int,
    val stateCounts: Map<String, Int>,
    val topThreadNames: Map<String, Int>,
    val vendorFamilies: Map<String, Int>,
    val newlyObserved: List<String>,
    val firstSeenOrigins: Map<Long, String>
)
