package com.poseidon.ad.common.storage.sp

/**
 * Describes what needs to be persisted after a daily counter check.
 */
internal data class DailyCounterUpdatePlan(
    val dateValue: String,
    val counterValue: Int,
    val needsPersistDate: Boolean,
    val needsPersistCounter: Boolean,
)

/**
 * Pure function: computes which SP writes are needed for a daily counter.
 *
 * @param savedDate     the last-stored date string (nullable if never written)
 * @param currentDate   today's date string
 * @param savedCounter  the last-stored counter value
 * @param applyIncrement whether to increment the counter after normalization
 */
internal fun computeDailyCounterPlan(
    savedDate: String?,
    currentDate: String,
    savedCounter: Int,
    applyIncrement: Boolean,
): DailyCounterUpdatePlan {
    val dateExpired = savedDate.isNullOrEmpty() || savedDate != currentDate
    val normalizedCounter = if (dateExpired) 0 else savedCounter
    val finalCounter = if (applyIncrement) normalizedCounter + 1 else normalizedCounter
    val counterChanged = dateExpired || applyIncrement
    return DailyCounterUpdatePlan(
        dateValue = currentDate,
        counterValue = finalCounter,
        needsPersistDate = dateExpired,
        needsPersistCounter = counterChanged,
    )
}
