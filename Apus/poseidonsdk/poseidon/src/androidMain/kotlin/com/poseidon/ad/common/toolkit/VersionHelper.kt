package com.poseidon.ad.common.toolkit

import com.poseidon.ad.statistics.base.ErrorDetectionReporter


/** 版本号工具类，描述主/次/补丁号 */
data class VersionHelper(val major: Int, val minor: Int, val patch: Int) : Comparable<VersionHelper> {

    /** 逐字段比较版本号 */
    override fun compareTo(other: VersionHelper) =
        compareValuesBy(
            this,
            other,
            { it.major },
            { it.minor },
            { it.patch },
        )

    /** 输出形如 x.y.z 的版本字符串 */
    override fun toString(): String {
        return "$major.$minor.$patch"
    }

    companion object {
        private const val VERSION_CHECKER_ANALYZER = "VersionChecker"

        /** 从字符串尝试解析版本对象 */
        internal fun tryFrom(versionString: String): VersionHelper? = runCatching {
            versionString.split(".").let {
                VersionHelper(
                    major = it[0].toInt(),
                    minor = it[1].toInt(),
                    patch = it[2].toInt(),
                )
            }
        }.onFailure { t ->
            ErrorDetectionReporter(
                name = VERSION_CHECKER_ANALYZER,
                extra = t.message.orEmpty()
            ).analyze()
        }.getOrNull()
    }
}
