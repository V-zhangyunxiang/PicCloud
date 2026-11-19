package com.poseidon.ad.main.adinfo

import androidx.annotation.IntDef

const val CODE_SUCCESS = 200
/** Exception code: null configuration */
const val ERROR_CODE_NULL_CONFIGURATION = -1000
/** Exception code: null scene */
const val ERROR_CODE_NULL_SCENES = -1001
/** Exception code: global ad switch off */
const val ERROR_CODE_GLOBAL_AD_SWITCH_OFF = -1002
/** Exception code: scene ad switch off */
const val ERROR_CODE_SCENE_AD_SWITCH_OFF = -1003
/** Exception code: scene not configured */
const val ERROR_CODE_SCENE_NOT_CONFIGURED = -1004
/** Exception code: null ad units */
const val ERROR_CODE_NULL_AD_UNITS = -1005
/** Exception code: unsupported ad network */
const val ERROR_CODE_UNSUPPORTED_AD_NETWORK = -1006
/** Exception code: unsupported ad type */
const val ERROR_CODE_UNSUPPORTED_AD_TYPE = -1007
/** Exception code: network unavailable */
const val ERROR_CODE_NETWORK_UNAVAILABLE = -1008
/** Exception code: app in background */
const val ERROR_CODE_APP_IN_BACKGROUND = -1009
/** Exception code: null strategy */
const val ERROR_CODE_NULL_STRATEGY = -1010
/** Exception code: show interval limit */
@Suppress("unused")
const val ERROR_CODE_SHOW_INTERVAL_LIMIT = -1011
/** Exception code: already callback ad loaded, but query cache ad failed */
const val ERROR_CODE_CALLBACK_AD_LOADED_BUT_QUERY_CACHE_AD_FAILED = -1012
/** Exception code: cache ad type mismatch */
const val ERROR_CODE_CACHE_AD_TYPE_MISMATCH = -1013
/** Exception code: activity destroyed */
const val ERROR_CODE_ACTIVITY_DESTROYED = -1014
/** Exception code: load interval limit */
const val ERROR_CODE_LOAD_INTERVAL_LIMIT = -1015
/** Exception code: unknown exception */
const val ERROR_CODE_UNKNOWN = -1016
/** Exception code: three-party ad is null, may call destroyed ad */
const val ERROR_CODE_NULL_THIRD_PARTY_AD = -1017
/** Exception code: three-party ad not ready */
const val ERROR_CODE_THIRD_PARTY_AD_NOT_READY = -1018
/** Exception code: ad network initialization failed */
const val ERROR_CODE_NETWORK_INITIALIZATION_FAILED = -1019
/** Exception code: ad unit is null */
const val ERROR_CODE_NULL_AD_UNIT = -1020
/** Exception code: ad load canceled */
const val ERROR_CODE_AD_LOAD_CANCELED = -1021
/** Exception code: no cached ad, then load failed */
const val ERROR_CODE_NO_CACHE_THEN_LOAD_FAILED = -1022
/** Exception code: no cached ad, then load canceled */
const val ERROR_CODE_NO_CACHE_THEN_LOAD_CANCELED = -1023
/** Exception code: no cached ad, then not allow load */
const val ERROR_CODE_NO_CACHE_DISALLOWED_LOAD = -1024
/** Exception code: create ad network failed */
const val ERROR_CODE_FAILED_TO_CREATE_AD_NETWORK = -1025
/** Exception code: ad max impression count limit */
const val ERROR_CODE_AD_MAX_IMPRESSION_COUNT_LIMIT = -1026
/** Exception code: notification splash ad config check failed */
const val ERROR_CODE_NOTIFICATION_SPLASH_AD_CONFIG_CHECK_FAILED = -1027
/** Exception code: notification splash ad no cache */
const val ERROR_CODE_NOTIFICATION_SPLASH_AD_NO_CACHE = -1028
/** Exception code: notification splash ad no cache and fallback strategy id is null */
const val ERROR_CODE_NOTIFICATION_SPLASH_AD_NO_CACHE_AND_FALLBACK_STRATEGY_ID_IS_NULL = -1029
/** Exception code: fullscreen ad impression interval limit */
const val ERROR_CODE_FULLSCREEN_AD_IMPRESSION_INTERVAL_LIMIT = -1030
/** Exception code: ad type mismatch */
const val ERROR_CODE_AD_TYPE_MISMATCH = -1031
/** Exception code: empty url */
const val ERROR_CODE_EMPTY_URL = -1032
/** Exception code: ad is loading */
const val ERROR_CODE_AD_IS_LOADING = -1033
/** Exception code: ctr limit */
const val ERROR_CODE_CTR_LIMIT = -1040
/** Exception code: Ad limit Show */
const val ERROR_CODE_AD_LIMIT_SHOW = -1041
/** Exception code: Ad network not init complete */
const val ERROR_CODE_NETWORK_NO_INITIALIZATION = -1060
/** Exception code: Frequency control - global display limit exceeded */
const val ERROR_CODE_FREQUENCY_CONTROL_GLOBAL_DISPLAY_LIMIT = -1042
/** Exception code: Frequency control - global click limit exceeded */
const val ERROR_CODE_FREQUENCY_CONTROL_GLOBAL_CLICK_LIMIT = -1043
/** Exception code: Frequency control - global interval too short */
const val ERROR_CODE_FREQUENCY_CONTROL_GLOBAL_INTERVAL_LIMIT = -1044
/** Exception code: Frequency control - ad type display limit exceeded */
const val ERROR_CODE_FREQUENCY_CONTROL_AD_TYPE_DISPLAY_LIMIT = -1045
/** Exception code: Frequency control - ad type click limit exceeded */
const val ERROR_CODE_FREQUENCY_CONTROL_AD_TYPE_CLICK_LIMIT = -1046
/** Exception code: Frequency control - ad type interval too short */
const val ERROR_CODE_FREQUENCY_CONTROL_AD_TYPE_INTERVAL_LIMIT = -1047
/** Exception code: Frequency control - scene display limit exceeded */
const val ERROR_CODE_FREQUENCY_CONTROL_SCENE_DISPLAY_LIMIT = -1048
/** Exception code: Frequency control - scene click limit exceeded */
const val ERROR_CODE_FREQUENCY_CONTROL_SCENE_CLICK_LIMIT = -1049
/** Exception code: Frequency control - scene interval too short */
const val ERROR_CODE_FREQUENCY_CONTROL_SCENE_INTERVAL_LIMIT = -1050
/** Exception code: Frequency control - new user first cold start blocked */
const val ERROR_CODE_FREQUENCY_CONTROL_NEW_USER_FIRST_COLD_START_BLOCKED = -1051
/** Exception code: Ad content over limit */
const val ERROR_CODE_AD_CONTENT_IMBALANCE = -1052
const val ERROR_CODE_BACKGROUND_LOW_MEMORY_PRELOAD = -1053
/** Exception code: US background preload blocked between 02:00 and 06:00 local time */
const val ERROR_CODE_US_NIGHT_BACKGROUND_PRELOAD = -1054
/** Exception code: preload skipped - critical pressure, thread count trip */
const val ERROR_CODE_CRITICAL_PRESSURE_THREAD_PRELOAD = -1055
/** Exception code: preload skipped - critical pressure, native heap trip */
const val ERROR_CODE_CRITICAL_PRESSURE_NATIVE_PRELOAD = -1056
/** Exception code: preload skipped - critical pressure, trim level or blocking-GC storm trip */
const val ERROR_CODE_CRITICAL_PRESSURE_TRIM_GC_PRELOAD = -1057
/** Exception code: show suppressed at critical pressure, no cached ad clears the eCPM exemption bar */
const val ERROR_CODE_CRITICAL_PRESSURE_SHOW_SUPPRESSED = -1058
/** Exception code: host-requested load suppressed at critical pressure */
const val ERROR_CODE_CRITICAL_PRESSURE_LOAD_SUPPRESSED = -1059

@IntDef(
    CODE_SUCCESS,
    ERROR_CODE_NULL_CONFIGURATION,
    ERROR_CODE_NULL_SCENES,
    ERROR_CODE_GLOBAL_AD_SWITCH_OFF,
    ERROR_CODE_SCENE_AD_SWITCH_OFF,
    ERROR_CODE_SCENE_NOT_CONFIGURED,
    ERROR_CODE_NULL_AD_UNITS,
    ERROR_CODE_UNSUPPORTED_AD_NETWORK,
    ERROR_CODE_UNSUPPORTED_AD_TYPE,
    ERROR_CODE_NETWORK_UNAVAILABLE,
    ERROR_CODE_APP_IN_BACKGROUND,
    ERROR_CODE_NULL_STRATEGY,
    ERROR_CODE_LOAD_INTERVAL_LIMIT,
    ERROR_CODE_UNKNOWN,
    ERROR_CODE_NETWORK_INITIALIZATION_FAILED,
    ERROR_CODE_NETWORK_NO_INITIALIZATION,
    ERROR_CODE_NULL_AD_UNIT,
    ERROR_CODE_AD_LOAD_CANCELED,
    ERROR_CODE_FAILED_TO_CREATE_AD_NETWORK,
    ERROR_CODE_AD_MAX_IMPRESSION_COUNT_LIMIT,
    ERROR_CODE_AD_IS_LOADING,
    ERROR_CODE_EMPTY_URL,
    ERROR_CODE_FREQUENCY_CONTROL_GLOBAL_DISPLAY_LIMIT,
    ERROR_CODE_FREQUENCY_CONTROL_GLOBAL_CLICK_LIMIT,
    ERROR_CODE_FREQUENCY_CONTROL_GLOBAL_INTERVAL_LIMIT,
    ERROR_CODE_FREQUENCY_CONTROL_AD_TYPE_DISPLAY_LIMIT,
    ERROR_CODE_FREQUENCY_CONTROL_AD_TYPE_CLICK_LIMIT,
    ERROR_CODE_FREQUENCY_CONTROL_AD_TYPE_INTERVAL_LIMIT,
    ERROR_CODE_FREQUENCY_CONTROL_SCENE_DISPLAY_LIMIT,
    ERROR_CODE_FREQUENCY_CONTROL_SCENE_CLICK_LIMIT,
    ERROR_CODE_FREQUENCY_CONTROL_SCENE_INTERVAL_LIMIT,
    ERROR_CODE_FREQUENCY_CONTROL_NEW_USER_FIRST_COLD_START_BLOCKED,
    ERROR_CODE_AD_CONTENT_IMBALANCE,
    ERROR_CODE_BACKGROUND_LOW_MEMORY_PRELOAD,
    ERROR_CODE_US_NIGHT_BACKGROUND_PRELOAD,
    ERROR_CODE_CRITICAL_PRESSURE_THREAD_PRELOAD,
    ERROR_CODE_CRITICAL_PRESSURE_NATIVE_PRELOAD,
    ERROR_CODE_CRITICAL_PRESSURE_TRIM_GC_PRELOAD,
    ERROR_CODE_CRITICAL_PRESSURE_SHOW_SUPPRESSED,
    ERROR_CODE_CRITICAL_PRESSURE_LOAD_SUPPRESSED
)
annotation class CodeDef


@IntDef(
    ERROR_CODE_NETWORK_UNAVAILABLE,
    ERROR_CODE_APP_IN_BACKGROUND,
    ERROR_CODE_NULL_CONFIGURATION,
    ERROR_CODE_GLOBAL_AD_SWITCH_OFF,
    ERROR_CODE_NULL_SCENES,
    ERROR_CODE_SCENE_NOT_CONFIGURED,
    ERROR_CODE_SCENE_AD_SWITCH_OFF,
    ERROR_CODE_NULL_STRATEGY,
    ERROR_CODE_CALLBACK_AD_LOADED_BUT_QUERY_CACHE_AD_FAILED,
    ERROR_CODE_CACHE_AD_TYPE_MISMATCH,
    ERROR_CODE_ACTIVITY_DESTROYED,
    ERROR_CODE_NULL_THIRD_PARTY_AD,
    ERROR_CODE_THIRD_PARTY_AD_NOT_READY,
    ERROR_CODE_UNKNOWN,
    ERROR_CODE_NO_CACHE_THEN_LOAD_FAILED,
    ERROR_CODE_NO_CACHE_THEN_LOAD_CANCELED,
    ERROR_CODE_NO_CACHE_DISALLOWED_LOAD,
    CODE_SUCCESS,
    ERROR_CODE_AD_MAX_IMPRESSION_COUNT_LIMIT,
    ERROR_CODE_NOTIFICATION_SPLASH_AD_CONFIG_CHECK_FAILED,
    ERROR_CODE_NOTIFICATION_SPLASH_AD_NO_CACHE,
    ERROR_CODE_FULLSCREEN_AD_IMPRESSION_INTERVAL_LIMIT,
    ERROR_CODE_EMPTY_URL,
    ERROR_CODE_CTR_LIMIT,
    ERROR_CODE_AD_LIMIT_SHOW,
    ERROR_CODE_FREQUENCY_CONTROL_GLOBAL_DISPLAY_LIMIT,
    ERROR_CODE_FREQUENCY_CONTROL_GLOBAL_CLICK_LIMIT,
    ERROR_CODE_FREQUENCY_CONTROL_GLOBAL_INTERVAL_LIMIT,
    ERROR_CODE_FREQUENCY_CONTROL_AD_TYPE_DISPLAY_LIMIT,
    ERROR_CODE_FREQUENCY_CONTROL_AD_TYPE_CLICK_LIMIT,
    ERROR_CODE_FREQUENCY_CONTROL_AD_TYPE_INTERVAL_LIMIT,
    ERROR_CODE_FREQUENCY_CONTROL_SCENE_DISPLAY_LIMIT,
    ERROR_CODE_FREQUENCY_CONTROL_SCENE_CLICK_LIMIT,
    ERROR_CODE_FREQUENCY_CONTROL_SCENE_INTERVAL_LIMIT,
    ERROR_CODE_FREQUENCY_CONTROL_NEW_USER_FIRST_COLD_START_BLOCKED,
    ERROR_CODE_BACKGROUND_LOW_MEMORY_PRELOAD,
    ERROR_CODE_US_NIGHT_BACKGROUND_PRELOAD,
    ERROR_CODE_CRITICAL_PRESSURE_THREAD_PRELOAD,
    ERROR_CODE_CRITICAL_PRESSURE_NATIVE_PRELOAD,
    ERROR_CODE_CRITICAL_PRESSURE_TRIM_GC_PRELOAD,
    ERROR_CODE_CRITICAL_PRESSURE_SHOW_SUPPRESSED,
    ERROR_CODE_CRITICAL_PRESSURE_LOAD_SUPPRESSED
)
annotation class ShowCodeDef
