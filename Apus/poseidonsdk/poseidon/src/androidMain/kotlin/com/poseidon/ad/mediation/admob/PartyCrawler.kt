package com.poseidon.ad.mediation.admob

import android.os.Bundle
import android.os.Parcelable
import android.util.Base64
import androidx.annotation.Keep
import com.google.android.gms.ads.AdapterResponseInfo
import com.google.android.gms.ads.ResponseInfo
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.rewarded.RewardedAd
import com.poseidon.ad.BuildConfig
import com.poseidon.ad.api.PoseidonSetupProcessor
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.common.toolkit.runOnMainThread
import com.poseidon.ad.main.adinfo.AdSourceInfo
import com.poseidon.ad.main.load.Parameter
import com.poseidon.ad.main.price.AdSourceIdDef
import com.poseidon.ad.statistics.core.AdmobEcpmReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.nio.charset.Charset
import java.util.Collections
import java.util.IdentityHashMap

internal object PartyCrawler {

    private const val TAG = "AdmobEcpmExtractor"
    private val charset: Charset = Charsets.UTF_8

    // Keep load-time extraction independent from the SDK-wide serial background executor.
    private val extractionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Encrypted string literals are intentionally obfuscated to make static analysis harder.
    private val hardcodedCrawlPrefix: String by lazy { decodeReversed("c21nLmRpb3JkbmEuZWxnb29nLm1vYw==") }
    private val loadedAdapterGetter: String by lazy { decodeReversed("b2ZuSWVzbm9wc2VScmV0cGFkQWRlZGFvTHRlZw==") }
    private val adSourceInstanceIdGetter: String by lazy {
        decodeReversed("ZEllY25hdHNuSWVjcnVvU2RBdGVn")
    }

    /**
     * Package roots that identify objects belonging to the GMS ad stack.
     *
     * The hardcoded prefix only matches while GMS names are untouched. When a host
     * app runs R8, GMS classes are renamed or repackaged, so the accepted roots are
     * derived at runtime from the actual (possibly renamed) [ResponseInfo] class.
     * Class literals are rewritten consistently by the shrinker; hardcoded strings
     * are not. The hardcoded prefix stays as a fallback for untouched GMS builds.
     */
    private val crawlPrefixes: List<String> by lazy {
        val runtimePackage = ResponseInfo::class.java.`package`?.name
        val runtimeRoot = when {
            runtimePackage.isNullOrEmpty() -> null
            runtimePackage.endsWith(".ads") -> runtimePackage.removeSuffix(".ads")
            runtimePackage.count { it == '.' } >= 2 -> runtimePackage.substringBeforeLast('.')
            else -> runtimePackage
        }
        listOfNotNull(runtimeRoot, hardcodedCrawlPrefix)
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun isCrawlable(cls: Class<*>): Boolean {
        val name = cls.name
        return crawlPrefixes.any { name.startsWith("$it.") }
    }

    private val isQaBuild: Boolean by lazy { BuildConfig.BUILD_TYPE == "qa" }

    internal data class ExtractionResult(
        val format: String,
        val extPrice: Double,
        val status: String,
        val reason: String,
        val adSourceInstanceId: String?,
        val adSourceInstanceIdOrigin: String?,
        val candidateCount: Int,
        val anchorMode: String?,
        val bestCandidateClass: String?,
        val bestParentClass: String?,
        val bestPriceField: String?,
        val bestPriceMicros: Long?,
        val precisionLabels: String?,
        val exceptionClass: String? = null,
        val exceptionMessage: String? = null
    ) {
        companion object {
            fun skippedPositiveRevenue(format: String): ExtractionResult = ExtractionResult(
                format = format,
                extPrice = 0.0,
                status = STATUS_SKIPPED,
                reason = REASON_POSITIVE_REVENUE,
                adSourceInstanceId = null,
                adSourceInstanceIdOrigin = null,
                candidateCount = 0,
                anchorMode = null,
                bestCandidateClass = null,
                bestParentClass = null,
                bestPriceField = null,
                bestPriceMicros = null,
                precisionLabels = null
            )
        }
    }

    data class ReportContext(
        val parameter: Parameter,
        val adSourceInfo: AdSourceInfo?,
        @param:AdSourceIdDef val adSourceId: Int,
        val revenue: Double?
    )

    private data class CandidateInfo(
        val value: Any,
        val parent: Any,
        val anchorMode: String
    )

    // Precise revenue is reported by GMS as a Double (milli-dollars) on a direct
    // child object of the public, non-obfuscated ResponseInfo class. It stays
    // stable across GMS versions unlike the obfuscated Parcelable price fields.
    private data class DoubleRevenueCandidate(
        val owner: Any,
        val parent: Any,
        val fieldName: String,
        val valueMillis: Double
    )

    private data class AdSourceInstanceIdResolution(
        val value: String,
        val origin: String
    )

    private data class FormatSpecificPrice(
        val priceOwner: Any?,
        val parent: Any,
        val rawPriceMicros: Double
    )

    private class CrawlResult {
        val parcelableCandidates: MutableMap<Any, CandidateInfo> = IdentityHashMap()
        val doubleRevenueCandidates: MutableList<DoubleRevenueCandidate> = mutableListOf()
    }

    // Fields are only read reflectively by the crawl; @Keep stops host-side R8
    // from stripping them as write-only. Needs no consumer rule to take effect.
    @Keep
    private class PartyChair(
        val loadedAdSourceInstanceId: String?,
        val responseInfo: ResponseInfo?,
        val interstitialAd: InterstitialAd? = null,
        val rewardedAd: RewardedAd? = null,
        val appOpenAd: AppOpenAd? = null
    )

    fun resolveExtPriceAsync(
        format: String,
        revenue: Double?,
        sessionId: String? = null,
        loadedAdSourceInstanceId: String? = null,
        responseInfo: ResponseInfo?,
        interstitialAd: InterstitialAd? = null,
        rewardedAd: RewardedAd? = null,
        appOpenAd: AppOpenAd? = null,
        allowAncestorAnchor: Boolean = false,
        completionOnMainThread: Boolean = false,
        completion: (ExtractionResult) -> Unit
    ) {
        if ((revenue ?: 0.0) > 0.0) {
            completion(ExtractionResult.skippedPositiveRevenue(format))
            return
        }
        extractionScope.launch {
            val result = runCatching {
                extractExtPrice(
                    root = PartyChair(
                        loadedAdSourceInstanceId = loadedAdSourceInstanceId,
                        responseInfo = responseInfo,
                        interstitialAd = interstitialAd,
                        rewardedAd = rewardedAd,
                        appOpenAd = appOpenAd
                    ),
                    format = format,
                    allowAncestorAnchor = allowAncestorAnchor
                )
            }.getOrElse { t ->
                exceptionResult(format, t)
            }
            if (isQaBuild) {
                runCatching {
                    AdmobEcpmDiagnosticsV2.dump(
                        sessionId = sessionId,
                        format = format,
                        loadedAdSourceInstanceId = loadedAdSourceInstanceId,
                        responseInfo = responseInfo,
                        interstitialAd = interstitialAd,
                        rewardedAd = rewardedAd,
                        appOpenAd = appOpenAd
                    )
                }
            }
            if (completionOnMainThread) {
                runOnMainThread {
                    completion(result)
                }
            } else {
                completion(result)
            }
        }
    }

    fun report(result: ExtractionResult, reportContext: ReportContext) {
        logInfo(TAG) {
            "sessionId=${reportContext.parameter.sessionId}, format=${result.format}, " +
                    "status=${result.status}, reason=${result.reason}, " +
                    "sourceInstanceId=${result.adSourceInstanceId}, " +
                    "sourceInstanceIdOrigin=${result.adSourceInstanceIdOrigin}, " +
                    "candidates=${result.candidateCount}, anchorMode=${result.anchorMode}, " +
                    "priceMicros=${result.bestPriceMicros}, priceField=${result.bestPriceField}"
        }

        AdmobEcpmReporter(
            sessionId = reportContext.parameter.sessionId,
            sceneName = reportContext.parameter.sceneName,
            adUnitId = reportContext.parameter.adUnitId,
            adSourcePlacementId = reportContext.adSourceInfo?.adSourcePlacementId,
            adSourceId = reportContext.adSourceId,
            adSourceName = reportContext.adSourceInfo?.adSourceName,
            rawAdSourceId = reportContext.adSourceInfo?.rawSourceId,
            poseidonConfigVersion = reportContext.parameter.poseidonConfigVersion,
            extractionResult = result
        ).analyze()
    }

    private fun extractExtPrice(
        root: PartyChair,
        format: String,
        allowAncestorAnchor: Boolean
    ): ExtractionResult {
        val formatSpecificPrice = if (admobEcpmFormatSpecificEnabled()) {
            extractFormatSpecificPrice(root)
        } else {
            null
        }
        val adSourceInstanceIdResolution = extractAdSourceInstanceId(root)

        if (formatSpecificPrice != null) {
            val extPrice = roundRevenueScale6(formatSpecificPrice.rawPriceMicros / 1000.0)
            if (extPrice > 0.0) {
                return ExtractionResult(
                    format = format,
                    extPrice = extPrice,
                    status = STATUS_SUCCESS,
                    reason = REASON_OK,
                    adSourceInstanceId = adSourceInstanceIdResolution?.value,
                    adSourceInstanceIdOrigin = adSourceInstanceIdResolution?.origin,
                    candidateCount = 1,
                    anchorMode = ANCHOR_FORMAT_SPECIFIC,
                    bestCandidateClass = formatSpecificPrice.priceOwner?.javaClass?.name,
                    bestParentClass = formatSpecificPrice.parent.javaClass.name,
                    bestPriceField = FORMAT_PRICE_FIELD,
                    bestPriceMicros = formatSpecificPrice.rawPriceMicros.toLong(),
                    precisionLabels = extractFormatPrecisionLabels(formatSpecificPrice.priceOwner)
                        .joinToString(",")
                        .takeIf { it.isNotEmpty() }
                )
            }
        }

        val resolvedAdSourceInstanceId = adSourceInstanceIdResolution ?: return emptyResult(
            format = format,
            reason = REASON_NO_AD_SOURCE_INSTANCE_ID,
            adSourceInstanceId = null
        )
        val adSourceInstanceId = resolvedAdSourceInstanceId.value

        val crawl = CrawlResult()
        collectCandidates(
            obj = root,
            visited = Collections.newSetFromMap(IdentityHashMap()),
            crawl = crawl,
            adSourceInstanceId = adSourceInstanceId,
            allowAncestorAnchor = allowAncestorAnchor
        )

        if (admobEcpmDoubleRevenueEnabled()) {
            logInfo(TAG) {
                "Trying response info approach for double!"
            }
            val doubleCandidate = crawl.doubleRevenueCandidates.maxByOrNull { it.valueMillis }
            if (doubleCandidate != null) {
                return doubleRevenueResult(
                    format = format,
                    candidate = doubleCandidate,
                    adSourceInstanceId = adSourceInstanceId,
                    adSourceInstanceIdOrigin = resolvedAdSourceInstanceId.origin
                )
            }
        } else {
            logInfo(TAG) {
                "response info approach deactivated"
            }
        }

        if (!admobEcpmTravolaEnabled()) {
            logInfo(TAG) {
                "Trying parceable approach !"
            }
            return failureResult(
                format = format,
                reason = REASON_TRAVOLA_DISABLED,
                adSourceInstanceId = adSourceInstanceId,
                adSourceInstanceIdOrigin = resolvedAdSourceInstanceId.origin,
                candidateCount = crawl.parcelableCandidates.size
            )
        }

        val candidates = crawl.parcelableCandidates
        if (candidates.isEmpty()) {
            return failureResult(
                format = format,
                reason = REASON_NO_CANDIDATE,
                adSourceInstanceId = adSourceInstanceId,
                adSourceInstanceIdOrigin = resolvedAdSourceInstanceId.origin,
                candidateCount = 0
            )
        }

        var bestCandidate: CandidateInfo? = null
        var bestPrice: Pair<String, Long>? = null
        for (candidate in candidates.values) {
            val price = findLargestLong(candidate.value) ?: continue
            if (bestPrice == null || price.second > bestPrice.second) {
                bestCandidate = candidate
                bestPrice = price
            }
        }

        val candidate = bestCandidate
        val price = bestPrice
        if (candidate == null || price == null) {
            return failureResult(
                format = format,
                reason = REASON_NO_PRICE_LONG,
                adSourceInstanceId = adSourceInstanceId,
                adSourceInstanceIdOrigin = resolvedAdSourceInstanceId.origin,
                candidateCount = candidates.size
            )
        }

        val extPrice = if (price.second > 0L) roundRevenueScale6(price.second / 1000.0) else 0.0
        return ExtractionResult(
            format = format,
            extPrice = extPrice,
            status = if (extPrice > 0.0) STATUS_SUCCESS else STATUS_FAILURE,
            reason = if (extPrice > 0.0) REASON_OK else REASON_NON_POSITIVE_PRICE,
            adSourceInstanceId = adSourceInstanceId,
            adSourceInstanceIdOrigin = resolvedAdSourceInstanceId.origin,
            candidateCount = candidates.size,
            anchorMode = candidate.anchorMode,
            bestCandidateClass = candidate.value.javaClass.name,
            bestParentClass = candidate.parent.javaClass.name,
            bestPriceField = price.first,
            bestPriceMicros = price.second,
            precisionLabels = extractPrecisionLabels(candidate.value).joinToString(",")
                .takeIf { it.isNotEmpty() }
        )
    }

    private fun extractFormatSpecificPrice(root: PartyChair): FormatSpecificPrice? {
        root.interstitialAd?.let { ad ->
            return extractFormatSpecificPrice(ad, INTERSTITIAL_FORMAT_PATH)
        }
        root.rewardedAd?.let { ad ->
            return extractFormatSpecificPrice(ad, REWARDED_FORMAT_PATH)
        }
        return null
    }

    private fun extractFormatSpecificPrice(
        ad: Any?,
        fieldPath: List<String>
    ): FormatSpecificPrice? = runCatching {
        var current = ad
        for (fieldName in fieldPath) {
            current = readField(current, fieldName)
        }
        val parent = (current as? List<*>)?.firstOrNull() ?: return@runCatching null
        val priceOwner = readField(parent, FORMAT_PRICE_OWNER_FIELD)
        val rawPriceMicros = readField(priceOwner, FORMAT_PRICE_FIELD)
            .toString()
            .toDouble()
        FormatSpecificPrice(
            priceOwner = priceOwner,
            parent = parent,
            rawPriceMicros = rawPriceMicros
        )
    }.getOrNull()

    private fun readField(obj: Any?, name: String): Any? {
        if (obj == null) return obj
        val field = runCatching { obj.javaClass.getDeclaredField(name) }
            .getOrElse { obj.javaClass.superclass?.getDeclaredField(name) }
        field?.isAccessible = true
        return field?.get(obj)
    }

    private fun extractFormatPrecisionLabels(obj: Any?): List<String> = runCatching {
        listOf(
            precisionLabel(readField(obj, "a").toString().toInt()),
            precisionLabel(readField(obj, "b").toString().toInt())
        )
    }.getOrDefault(emptyList())

    private fun doubleRevenueResult(
        format: String,
        candidate: DoubleRevenueCandidate,
        adSourceInstanceId: String,
        adSourceInstanceIdOrigin: String
    ): ExtractionResult {
        return ExtractionResult(
            format = format,
            extPrice = roundRevenueScale6(candidate.valueMillis),
            status = STATUS_SUCCESS,
            reason = REASON_OK,
            adSourceInstanceId = adSourceInstanceId,
            adSourceInstanceIdOrigin = adSourceInstanceIdOrigin,
            candidateCount = 1,
            anchorMode = ANCHOR_RESPONSE_INFO_DOUBLE,
            bestCandidateClass = candidate.owner.javaClass.name,
            bestParentClass = candidate.parent.javaClass.name,
            bestPriceField = candidate.fieldName,
            bestPriceMicros = (candidate.valueMillis * 1000.0).toLong(),
            precisionLabels = extractPrecisionLabels(candidate.owner).joinToString(",")
                .takeIf { it.isNotEmpty() }
        )
    }

    private fun extractAdSourceInstanceId(root: PartyChair): AdSourceInstanceIdResolution? {
        root.loadedAdSourceInstanceId?.takeIf { it.isNotEmpty() }?.let {
            return AdSourceInstanceIdResolution(it, AD_SOURCE_INSTANCE_ID_ORIGIN_PASSED_IN)
        }
        root.responseInfo?.loadedAdapterResponseInfo?.adSourceInstanceId?.takeIf { it.isNotEmpty() }
            ?.let {
                return AdSourceInstanceIdResolution(
                    it,
                    AD_SOURCE_INSTANCE_ID_ORIGIN_RESPONSE_INFO
                )
            }
        return extractAdSourceInstanceIdReflectively(root)?.takeIf { it.isNotEmpty() }?.let {
            AdSourceInstanceIdResolution(it, AD_SOURCE_INSTANCE_ID_ORIGIN_REFLECTION)
        }
    }

    private fun extractAdSourceInstanceIdReflectively(obj: Any): String? {
        return runCatching {
            // Type-based lookup: survives host-side renaming of GMS internals.
            val target = obj.javaClass.declaredFields.firstNotNullOfOrNull { field ->
                if (!ResponseInfo::class.java.isAssignableFrom(field.type)) {
                    null
                } else {
                    field.isAccessible = true
                    field.get(obj)
                }
            } ?: return@runCatching null

            val method1: Method = target.javaClass.methods.firstOrNull { method ->
                method.parameterCount == 0 &&
                        AdapterResponseInfo::class.java.isAssignableFrom(method.returnType)
            } ?: target.javaClass.getMethod(loadedAdapterGetter)
            method1.isAccessible = true
            val adapterResponseInfo = method1.invoke(target) ?: return@runCatching null

            val method2: Method =
                adapterResponseInfo.javaClass.getMethod(adSourceInstanceIdGetter)
            method2.isAccessible = true
            method2.invoke(adapterResponseInfo) as? String
        }.getOrNull()
    }

    private fun collectCandidates(
        obj: Any?,
        visited: MutableSet<Any>,
        crawl: CrawlResult,
        adSourceInstanceId: String,
        allowAncestorAnchor: Boolean,
        parentMap: MutableMap<Any, Any?> = IdentityHashMap()
    ) {
        // The node budget only matters when a host repackaged every class into one
        // shared root package, which widens the crawl beyond the GMS graph.
        if (obj == null || visited.size >= MAX_CRAWL_NODES || !visited.add(obj)) return

        for (field in collectFields(obj)) {
            runCatching {
                field.isAccessible = true
                val value = field.get(obj) ?: return@runCatching

                if (value is Double && value > 0.0 && value <= MAX_DOUBLE_REVENUE_MILLIS) {
                    val parent = parentMap[obj]
                    if (parent != null && ResponseInfo::class.java.isAssignableFrom(parent.javaClass)) {
                        crawl.doubleRevenueCandidates += DoubleRevenueCandidate(
                            owner = obj,
                            parent = parent,
                            fieldName = field.name,
                            valueMillis = value
                        )
                    }
                }

                if (value !is Parcelable || value is Bundle) {
                    when {
                        value is Map<*, *> -> crawlMap(
                            map = value,
                            parent = obj,
                            visited = visited,
                            crawl = crawl,
                            adSourceInstanceId = adSourceInstanceId,
                            allowAncestorAnchor = allowAncestorAnchor,
                            parentMap = parentMap
                        )

                        isCrawlable(value.javaClass) -> {
                            parentMap[value] = obj
                            collectCandidates(
                                value,
                                visited,
                                crawl,
                                adSourceInstanceId,
                                allowAncestorAnchor,
                                parentMap
                            )
                        }
                    }
                    return@runCatching
                }

                val largestLong = findLargestLong(value)
                val anchorMode = findAdSourceInstanceIdAnchorMode(
                    obj = obj,
                    adSourceInstanceId = adSourceInstanceId,
                    parentMap = parentMap,
                    allowAncestorAnchor = allowAncestorAnchor
                )
                if (largestLong != null && anchorMode != null) {
                    crawl.parcelableCandidates[value] =
                        CandidateInfo(value = value, parent = obj, anchorMode = anchorMode)
                }
            }
        }
    }

    private fun crawlMap(
        map: Map<*, *>,
        parent: Any,
        visited: MutableSet<Any>,
        crawl: CrawlResult,
        adSourceInstanceId: String,
        allowAncestorAnchor: Boolean,
        parentMap: MutableMap<Any, Any?>
    ) {
        for (entry in map.entries) {
            val key = entry.key
            if (key != null && isCrawlable(key.javaClass)) {
                parentMap[key] = parent
                collectCandidates(
                    key,
                    visited,
                    crawl,
                    adSourceInstanceId,
                    allowAncestorAnchor,
                    parentMap
                )
            }
            val value = entry.value
            if (value != null && isCrawlable(value.javaClass)) {
                parentMap[value] = parent
                collectCandidates(
                    value,
                    visited,
                    crawl,
                    adSourceInstanceId,
                    allowAncestorAnchor,
                    parentMap
                )
            }
        }
    }

    private fun findAdSourceInstanceIdAnchorMode(
        obj: Any,
        adSourceInstanceId: String,
        parentMap: MutableMap<Any, Any?>,
        allowAncestorAnchor: Boolean
    ): String? {
        if (containsAdSourceInstanceId(obj, adSourceInstanceId)) {
            return ANCHOR_DIRECT
        }
        if (!allowAncestorAnchor) {
            return null
        }

        var current: Any? = parentMap[obj]
        val checked = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        while (current != null && checked.add(current)) {
            if (containsAdSourceInstanceId(current, adSourceInstanceId)) return ANCHOR_ANCESTOR
            current = parentMap[current]
        }
        return null
    }

    private fun collectFields(obj: Any): List<Field> {
        val fields = mutableListOf<Field>()
        var cls: Class<in Any>? = obj.javaClass
        if (cls != null) {
            fields.addAll(cls.declaredFields)
            cls = cls.superclass
            while (cls != null && isCrawlable(cls)) {
                fields.addAll(cls.declaredFields)
                cls = cls.superclass
            }
        }
        return fields
    }

    private fun containsAdSourceInstanceId(obj: Any, adSourceInstanceId: String): Boolean {
        return runCatching {
            val numericAdSourceInstanceId = adSourceInstanceId.toLongOrNull()
            for (field in collectFields(obj)) {
                field.isAccessible = true
                when (val value = field.get(obj)) {
                    is String -> if (value.equals(
                            adSourceInstanceId,
                            ignoreCase = true
                        )
                    ) return@runCatching true

                    is CharSequence -> if (value.toString()
                            .equals(adSourceInstanceId, ignoreCase = true)
                    ) return@runCatching true

                    is Number -> if (
                        numericAdSourceInstanceId != null &&
                        value.toLong() == numericAdSourceInstanceId
                    ) return@runCatching true
                }
            }
            false
        }.getOrDefault(false)
    }

    private fun findLargestLong(obj: Any): Pair<String, Long>? {
        return runCatching {
            var maxName = ""
            var maxValue = 0L
            for (field in obj.javaClass.declaredFields) {
                field.isAccessible = true
                val value = field.get(obj)
                if (value is Int || value is Long) {
                    val longValue = value.toString().toLong()
                    if (longValue > maxValue) {
                        maxName = field.name
                        maxValue = longValue
                    }
                }
            }
            maxName.takeIf { it.isNotEmpty() }?.let { it to maxValue }
        }.getOrNull()
    }

    private fun extractPrecisionLabels(obj: Any): List<String> {
        return runCatching {
            obj.javaClass.declaredFields
                .asSequence()
                .filter { it.type == Integer.TYPE }
                .mapNotNull { field ->
                    field.isAccessible = true
                    (field.get(obj) as? Int)?.let(::precisionLabel)
                }
                .drop(1)
                .take(1)
                .toList()
        }.getOrDefault(emptyList())
    }

    private fun precisionLabel(value: Int): String {
        return when (value) {
            1 -> "estimated"
            2 -> "provided"
            3 -> "precise"
            else -> "unknown"
        }
    }

    private fun admobEcpmDoubleRevenueEnabled(): Boolean {
        return remoteSwitchEnabled(REMOTE_DOUBLE_REVENUE_KEY)
    }

    private fun admobEcpmFormatSpecificEnabled(): Boolean {
        return false
//        return remoteSwitchEnabled(REMOTE_FORMAT_SPECIFIC_KEY)
    }

    private fun admobEcpmTravolaEnabled(): Boolean {
        return remoteSwitchEnabled(REMOTE_PARCEABLE_STEP_KEY)
    }

    private fun remoteSwitchEnabled(key: String): Boolean {
        return runCatching {
            PoseidonSetupProcessor.getInstance().remoteConfig
                .getString(key)
                .toBooleanStrictOrNull() ?: true
        }.getOrDefault(true)
    }

    private fun emptyResult(
        format: String,
        reason: String,
        adSourceInstanceId: String?
    ): ExtractionResult {
        return ExtractionResult(
            format = format,
            extPrice = 0.0,
            status = STATUS_FAILURE,
            reason = reason,
            adSourceInstanceId = adSourceInstanceId,
            adSourceInstanceIdOrigin = null,
            candidateCount = 0,
            anchorMode = null,
            bestCandidateClass = null,
            bestParentClass = null,
            bestPriceField = null,
            bestPriceMicros = null,
            precisionLabels = null
        )
    }

    private fun failureResult(
        format: String,
        reason: String,
        adSourceInstanceId: String,
        adSourceInstanceIdOrigin: String,
        candidateCount: Int
    ): ExtractionResult {
        return ExtractionResult(
            format = format,
            extPrice = 0.0,
            status = STATUS_FAILURE,
            reason = reason,
            adSourceInstanceId = adSourceInstanceId,
            adSourceInstanceIdOrigin = adSourceInstanceIdOrigin,
            candidateCount = candidateCount,
            anchorMode = null,
            bestCandidateClass = null,
            bestParentClass = null,
            bestPriceField = null,
            bestPriceMicros = null,
            precisionLabels = null
        )
    }

    private fun exceptionResult(format: String, throwable: Throwable): ExtractionResult {
        return ExtractionResult(
            format = format,
            extPrice = 0.0,
            status = STATUS_FAILURE,
            reason = REASON_EXCEPTION,
            adSourceInstanceId = null,
            adSourceInstanceIdOrigin = null,
            candidateCount = 0,
            anchorMode = null,
            bestCandidateClass = null,
            bestParentClass = null,
            bestPriceField = null,
            bestPriceMicros = null,
            precisionLabels = null,
            exceptionClass = throwable.javaClass.name,
            exceptionMessage = throwable.message
        )
    }

    private fun decodeReversed(value: String): String {
        return String(Base64.decode(value, Base64.NO_WRAP), charset).reversed()
    }

    private const val STATUS_SUCCESS = "success"
    private const val STATUS_FAILURE = "failure"
    private const val STATUS_SKIPPED = "skipped"
    private const val REASON_OK = "ok"
    private const val REASON_NO_AD_SOURCE_INSTANCE_ID = "no_ad_source_instance_id"
    private const val REASON_NO_CANDIDATE = "no_candidate"
    private const val REASON_NO_PRICE_LONG = "no_price_long"
    private const val REASON_NON_POSITIVE_PRICE = "non_positive_price"
    private const val REASON_POSITIVE_REVENUE = "positive_revenue"
    private const val REASON_TRAVOLA_DISABLED = "parceable_step_disabled"
    private const val REASON_EXCEPTION = "exception"
    private const val ANCHOR_DIRECT = "direct"
    private const val ANCHOR_ANCESTOR = "ancestor"
    private const val ANCHOR_FORMAT_SPECIFIC = "format_specific"
    private const val ANCHOR_RESPONSE_INFO_DOUBLE = "rs_info_double"
    private const val AD_SOURCE_INSTANCE_ID_ORIGIN_PASSED_IN = "passed_in"
    private const val AD_SOURCE_INSTANCE_ID_ORIGIN_RESPONSE_INFO = "response_info"
    private const val AD_SOURCE_INSTANCE_ID_ORIGIN_REFLECTION = "reflection"
    private const val REMOTE_DOUBLE_REVENUE_KEY = "ps_admob_ecpm_rs_info_enabled"
    private const val REMOTE_FORMAT_SPECIFIC_KEY = "ps_admob_ecpm_format_specific_enabled"
    private const val REMOTE_PARCEABLE_STEP_KEY = "ps_admob_ecpm_parceable_enabled"
    private const val FORMAT_PRICE_OWNER_FIELD = "ae"
    private const val FORMAT_PRICE_FIELD = "d"
    private val INTERSTITIAL_FORMAT_PATH = listOf("zzc", "zza", "a", "a", "i", "b", "a")
    private val REWARDED_FORMAT_PATH = listOf("zzb", "zza", "b", "i", "b", "a")
    private const val MAX_DOUBLE_REVENUE_MILLIS = 1_000_000.0
    private const val MAX_CRAWL_NODES = 700 //todo use remote to cap
}
