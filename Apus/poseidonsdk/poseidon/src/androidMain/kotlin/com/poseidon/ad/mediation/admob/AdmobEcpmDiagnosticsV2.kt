package com.poseidon.ad.mediation.admob

import android.os.Bundle
import android.os.Parcelable
import android.util.Base64
import com.google.android.gms.ads.ResponseInfo
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.rewarded.RewardedAd
import com.poseidon.ad.common.toolkit.logInfo
import java.lang.reflect.Field
import java.nio.charset.Charset
import java.util.Collections
import java.util.IdentityHashMap

/**
 * QA-only diagnostics for the AdMob response object graph.
 *
 * This class never extracts prices and never reports analytics. It only crawls the
 * GMS response graph and dumps what it sees (candidate price holders, anchor
 * status, parent chains, double revenue fields) to logcat so extraction issues
 * can be diagnosed from QA builds. Invoked by [PartyCrawler] only when
 * `BuildConfig.BUILD_TYPE == "qa"`.
 */
internal object AdmobEcpmDiagnosticsV2 {

    private const val TAG = "AdmobEcpmDiagnosticsV2"
    private val charset: Charset = Charsets.UTF_8

    // Encrypted string literals are intentionally obfuscated to make static analysis harder.
    private val crawlPrefix: String by lazy { decodeReversed("c21nLmRpb3JkbmEuZWxnb29nLm1vYw==") }
    private val responseInfoClassName: String by lazy {
        decodeReversed("b2ZuSWVzbm9wc2VSLnNkYS5zbWcuZGlvcmRuYS5lbGdvb2cubW9j")
    }

    private class DumpRoot(
        val loadedAdSourceInstanceId: String?,
        val responseInfo: ResponseInfo?,
        val interstitialAd: InterstitialAd? = null,
        val rewardedAd: RewardedAd? = null,
        val appOpenAd: AppOpenAd? = null
    )

    private class Dump {
        val lines: MutableList<String> = mutableListOf()
        val doubleFields: MutableList<String> = mutableListOf()
        val candidates: MutableList<String> = mutableListOf()

        fun record(line: String) {
            if (lines.size < MAX_DUMP_LINES) {
                lines += line.take(MAX_DUMP_LINE_LENGTH)
            }
        }
    }

    fun dump(
        sessionId: String?,
        format: String,
        loadedAdSourceInstanceId: String?,
        responseInfo: ResponseInfo?,
        interstitialAd: InterstitialAd? = null,
        rewardedAd: RewardedAd? = null,
        appOpenAd: AppOpenAd? = null
    ) {
        val adSourceInstanceId = loadedAdSourceInstanceId
            ?.takeIf { it.isNotEmpty() }
            ?: responseInfo?.loadedAdapterResponseInfo?.adSourceInstanceId?.takeIf { it.isNotEmpty() }
        val dump = Dump()
        crawl(
            obj = DumpRoot(
                loadedAdSourceInstanceId = loadedAdSourceInstanceId,
                responseInfo = responseInfo,
                interstitialAd = interstitialAd,
                rewardedAd = rewardedAd,
                appOpenAd = appOpenAd
            ),
            visited = Collections.newSetFromMap(IdentityHashMap()),
            adSourceInstanceId = adSourceInstanceId,
            path = "root",
            parentMap = IdentityHashMap(),
            dump = dump
        )
        logInfo(TAG) {
            "sessionId=$sessionId, format=$format, sourceInstanceId=$adSourceInstanceId, " +
                    "doubles=${dump.doubleFields.joinToString("|").ifEmpty { "none" }}, " +
                    "candidates=${dump.candidates.joinToString("|").ifEmpty { "none" }}"
        }
        logChunked(
            prefix = "sessionId=$sessionId, admobEcpmDiagDump",
            value = dump.lines.joinToString("\n")
        )
    }

    private fun crawl(
        obj: Any?,
        visited: MutableSet<Any>,
        adSourceInstanceId: String?,
        path: String,
        parentMap: MutableMap<Any, Any?>,
        dump: Dump
    ) {
        if (obj == null || !visited.add(obj)) return
        dump.record(
            "OBJ path=$path cls=${obj.javaClass.name} nums=${numericFieldsSummary(obj)} " +
                    "ids=${idFieldsSummary(obj, adSourceInstanceId)}"
        )

        for (field in collectFields(obj)) {
            runCatching {
                field.isAccessible = true
                val value = field.get(obj) ?: return@runCatching
                val fieldPath = "$path.${field.name}"

                when (value) {
                    is Number -> {
                        dump.record(
                            "FIELD path=$fieldPath type=${value.javaClass.name.shortClassName()} value=$value"
                        )
                        val parent = parentMap[obj]
                        if (
                            value is Double && value > 0.0 &&
                            parent != null && parent.javaClass.name == responseInfoClassName
                        ) {
                            val entry = "$fieldPath=$value"
                            dump.doubleFields += entry
                            dump.record("DOUBLE_REVENUE $entry")
                        }
                    }

                    is CharSequence -> if (
                        adSourceInstanceId != null &&
                        value.toString().contains(adSourceInstanceId, ignoreCase = true)
                    ) {
                        dump.record(
                            "ID_FIELD path=$fieldPath value=${value.toString().take(MAX_FIELD_VALUE_LENGTH)}"
                        )
                    }
                }

                if (value !is Parcelable || value is Bundle) {
                    when {
                        value is Map<*, *> -> crawlMap(
                            map = value,
                            parent = obj,
                            parentPath = fieldPath,
                            visited = visited,
                            adSourceInstanceId = adSourceInstanceId,
                            parentMap = parentMap,
                            dump = dump
                        )

                        value.javaClass.name.startsWith(crawlPrefix) -> {
                            parentMap[value] = obj
                            crawl(value, visited, adSourceInstanceId, fieldPath, parentMap, dump)
                        }
                    }
                    return@runCatching
                }

                val largestLong = findLargestLong(value)
                val directAnchor = containsAdSourceInstanceId(obj, adSourceInstanceId)
                val ancestorAnchor = containsAdSourceInstanceIdInAncestors(
                    obj = obj,
                    adSourceInstanceId = adSourceInstanceId,
                    parentMap = parentMap
                )
                if (largestLong != null && (directAnchor || ancestorAnchor)) {
                    val entry = "${value.javaClass.name.shortClassName()}@" +
                            "${obj.javaClass.name.shortClassName()}:" +
                            (if (directAnchor) "direct" else "ancestor") +
                            ":${largestLong.first}=${largestLong.second}"
                    dump.candidates += entry
                    dump.record(
                        "CANDIDATE path=$fieldPath cls=${value.javaClass.name} " +
                                "parent=${obj.javaClass.name} largest=${largestLong.first}=${largestLong.second} " +
                                "nums=${numericFieldsSummary(value)}"
                    )
                } else {
                    dump.record(
                        "REJECT path=$fieldPath cls=${value.javaClass.name} " +
                                "parent=${obj.javaClass.name} largest=${largestLong?.first}=${largestLong?.second} " +
                                "directAnchor=$directAnchor ancestorAnchor=$ancestorAnchor " +
                                "nums=${numericFieldsSummary(value)} chain=${parentChainSummary(obj, parentMap)}"
                    )
                }
            }
        }
    }

    private fun crawlMap(
        map: Map<*, *>,
        parent: Any,
        parentPath: String,
        visited: MutableSet<Any>,
        adSourceInstanceId: String?,
        parentMap: MutableMap<Any, Any?>,
        dump: Dump
    ) {
        for (entry in map.entries) {
            val key = entry.key
            if (key != null && key.javaClass.name.startsWith(crawlPrefix)) {
                parentMap[key] = parent
                crawl(
                    key, visited, adSourceInstanceId,
                    "$parentPath{key:${key.javaClass.name.shortClassName()}}", parentMap, dump
                )
            }
            val value = entry.value
            if (value != null && value.javaClass.name.startsWith(crawlPrefix)) {
                parentMap[value] = parent
                crawl(
                    value, visited, adSourceInstanceId,
                    "$parentPath{value:${value.javaClass.name.shortClassName()}}", parentMap, dump
                )
            }
        }
    }

    private fun containsAdSourceInstanceId(obj: Any, adSourceInstanceId: String?): Boolean {
        if (adSourceInstanceId == null) return false
        return runCatching {
            val numericAdSourceInstanceId = adSourceInstanceId.toLongOrNull()
            for (field in collectFields(obj)) {
                field.isAccessible = true
                when (val value = field.get(obj)) {
                    is String -> if (value.equals(adSourceInstanceId, ignoreCase = true)) {
                        return@runCatching true
                    }

                    is CharSequence -> if (
                        value.toString().equals(adSourceInstanceId, ignoreCase = true)
                    ) {
                        return@runCatching true
                    }

                    is Number -> if (
                        numericAdSourceInstanceId != null &&
                        value.toLong() == numericAdSourceInstanceId
                    ) {
                        return@runCatching true
                    }
                }
            }
            false
        }.getOrDefault(false)
    }

    private fun containsAdSourceInstanceIdInAncestors(
        obj: Any,
        adSourceInstanceId: String?,
        parentMap: MutableMap<Any, Any?>
    ): Boolean {
        var current: Any? = parentMap[obj]
        val checked = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        while (current != null && checked.add(current)) {
            if (containsAdSourceInstanceId(current, adSourceInstanceId)) return true
            current = parentMap[current]
        }
        return false
    }

    private fun parentChainSummary(obj: Any, parentMap: MutableMap<Any, Any?>): String? {
        return runCatching {
            val classes = mutableListOf<String>()
            var current: Any? = obj
            val checked = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
            while (current != null && checked.add(current) && classes.size < MAX_PARENT_CHAIN_SIZE) {
                classes += current.javaClass.name.shortClassName()
                current = parentMap[current]
            }
            classes.joinToString(">").takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun collectFields(obj: Any): List<Field> {
        val fields = mutableListOf<Field>()
        var cls: Class<in Any>? = obj.javaClass
        if (cls != null) {
            fields.addAll(cls.declaredFields)
            cls = cls.superclass
            while (cls != null && cls.name.startsWith(crawlPrefix)) {
                fields.addAll(cls.declaredFields)
                cls = cls.superclass
            }
        }
        return fields
    }

    private fun findLargestLong(obj: Any): Pair<String, Long>? {
        return runCatching {
            var maxName = ""
            var maxValue = 0L
            for (field in obj.javaClass.declaredFields) {
                if (field.type != java.lang.Long.TYPE) continue
                field.isAccessible = true
                val value = field.get(obj)
                if (value is Long && value > maxValue) {
                    maxName = field.name
                    maxValue = value
                }
            }
            maxName.takeIf { it.isNotEmpty() }?.let { it to maxValue }
        }.getOrNull()
    }

    private fun numericFieldsSummary(obj: Any): String {
        return runCatching {
            obj.javaClass.declaredFields
                .asSequence()
                .mapNotNull { field ->
                    field.isAccessible = true
                    when (val value = field.get(obj)) {
                        is Int -> "${field.name}:Int=$value"
                        is Long -> "${field.name}:Long=$value"
                        is Float -> "${field.name}:Float=$value"
                        is Double -> "${field.name}:Double=$value"
                        is Short -> "${field.name}:Short=$value"
                        is Byte -> "${field.name}:Byte=$value"
                        else -> null
                    }
                }
                .take(MAX_NUMERIC_FIELD_SUMMARY_SIZE)
                .joinToString(",")
                .ifEmpty { "none" }
        }.getOrDefault("err")
    }

    private fun idFieldsSummary(obj: Any, adSourceInstanceId: String?): String {
        if (adSourceInstanceId == null) return "none"
        return runCatching {
            val numericAdSourceInstanceId = adSourceInstanceId.toLongOrNull()
            collectFields(obj)
                .asSequence()
                .mapNotNull { field ->
                    field.isAccessible = true
                    when (val value = field.get(obj)) {
                        is CharSequence -> if (
                            value.toString().contains(adSourceInstanceId, ignoreCase = true)
                        ) {
                            "${field.name}:String=${value.toString().take(MAX_FIELD_VALUE_LENGTH)}"
                        } else {
                            null
                        }

                        is Number -> if (
                            numericAdSourceInstanceId != null &&
                            value.toLong() == numericAdSourceInstanceId
                        ) {
                            "${field.name}:${value.javaClass.name.shortClassName()}=$value"
                        } else {
                            null
                        }

                        else -> null
                    }
                }
                .take(MAX_ID_FIELD_SUMMARY_SIZE)
                .joinToString(",")
                .ifEmpty { "none" }
        }.getOrDefault("err")
    }

    private fun logChunked(prefix: String, value: String) {
        var index = 0
        var start = 0
        while (start < value.length) {
            val end = minOf(start + MAX_LOG_CHUNK_LENGTH, value.length)
            logInfo(TAG) { "$prefix[$index]=${value.substring(start, end)}" }
            start = end
            index++
        }
    }

    private fun String.shortClassName(): String = substringAfterLast('.')

    private fun decodeReversed(value: String): String {
        return String(Base64.decode(value, Base64.NO_WRAP), charset).reversed()
    }

    private const val MAX_PARENT_CHAIN_SIZE = 6
    private const val MAX_NUMERIC_FIELD_SUMMARY_SIZE = 20
    private const val MAX_ID_FIELD_SUMMARY_SIZE = 10
    private const val MAX_FIELD_VALUE_LENGTH = 120
    private const val MAX_DUMP_LINES = 180
    private const val MAX_DUMP_LINE_LENGTH = 500
    private const val MAX_LOG_CHUNK_LENGTH = 3500
}
