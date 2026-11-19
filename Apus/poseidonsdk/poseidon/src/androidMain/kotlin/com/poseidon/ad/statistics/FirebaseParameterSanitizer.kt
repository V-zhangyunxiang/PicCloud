package com.poseidon.ad.statistics

internal const val FIREBASE_MAX_STRING_PARAMETER_LENGTH = 100

/**
 * Firebase drops an over-length string parameter and reports error 4. Bound every string at the
 * transport boundary so one diagnostic value cannot silently become null.
 */
internal fun firebaseSafeParameters(parameters: Map<String, Any?>): Map<String, Any?> {
    return parameters.mapValues { (_, value) ->
        if (value is String) value.firebaseSafeParameterValue() else value
    }
}

internal fun String.firebaseSafeParameterValue(): String {
    if (length <= FIREBASE_MAX_STRING_PARAMETER_LENGTH) return this
    val bounded = take(FIREBASE_MAX_STRING_PARAMETER_LENGTH)
    return if (bounded.lastOrNull()?.let(Character::isHighSurrogate) == true) {
        bounded.dropLast(1)
    } else {
        bounded
    }
}
