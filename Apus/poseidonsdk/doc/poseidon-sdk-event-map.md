# Poseidon SDK Event Map

This file is source-derived from outbound event emissions in `poseidon/src/androidMain/kotlin/com/poseidon/ad`.

## Audit Basis

Included sinks:

- `Reporter.analyze()` -> Firebase via `FirebaseAnalytics.logEvent(...)`
- direct Firebase calls through `analyticsFactory.obtain(...).logEvent(...)`
- direct Adjust calls through `Adjust.trackEvent(...)`
- direct Adjust revenue calls through `Adjust.trackAdRevenue(...)`

Excluded on purpose:

- demo app code under `app/`
- internal callbacks, logs, and non-analytics control flow
- reporter/analyzer/helper objects that are constructed but never `.analyze()`'d

## Global Rules

- All `Reporter` subclasses emit to Firebase only.
- Every reporter-based event is gated by remote config key `poseidon_analyzer_enable_<eventName>`.
- Reporter defaults:
  - default `false`: `error_detection`, `poseidon_config_version_report`, `consent_management_platform_analyzer`, `notification_splash_ad_preload_analyzer`
  - default `true`: every other `Reporter` event in this file
- `Reporter.analyze()` auto-attaches `poseidon_config_version` when the event payload does not already include it. The attached value can be `null`.
- `Reporter.analyze()` also auto-attaches `poseidon_sdk_version` when the payload does not already include it. The value is `{libVersion}-{productFlavor}-{buildType}` from `BuildConfig.POSEIDON_LIB_VERSION`/`POSEIDON_PRODUCT_NAME`/`BUILD_TYPE`.
- `BizAdResponseTracker` is the only reporter that removes `null` params before logging. Other reporter payloads pass nullable values through as-is.
- Direct Firebase events in `AdPaidInfo`, `AeoCfg`, and `RrcRoasTracker` do not get the reporter gate and do not auto-attach `poseidon_config_version`, but `ReporterWrapper.logEvent()` auto-attaches `poseidon_sdk_version` to them when absent.

## Reporter-Based Firebase Events

### Core Ad Events

| Event name | Params passed | Fired from |
| --- | --- | --- |
| `adv_res` | `session_id`, `scene_name`, `placement_id`, `network_placement_id`, `network_id`, `network_name`, `ad_type`, `mediation_name`, `duration`, `result_code`, `result_message`, `revenue_value`, auto `poseidon_config_version` | `AdLoaderBase.dispatchAdLoadedEvent(...)`, `AdLoaderBase.dispatchAdFailedLoadedEvent(...)` |
| `adv_show` | `session_id`, `scene_name`, `placement_id`, `network_placement_id`, `network_id`, `network_name`, `ad_type`, `mediation_name`, `result_code`, `result_message`, auto `poseidon_config_version` | `AdBase.dispatchAdImpressionEvent(...)` and `AdBase.dispatchAdFailedToShowEvent(...)` |
| `adv_click` | `session_id`, `scene_name`, `placement_id`, `network_placement_id`, `network_id`, `network_name`, `ad_type`, `mediation_name`, auto `poseidon_config_version` | `AdBase.dispatchAdClickedEvent(...)` |
| `adv_hide` | `session_id`, `scene_name`, `placement_id`, `network_placement_id`, `network_id`, `network_name`, `ad_type`, `mediation_name`, auto `poseidon_config_version` | `AdBase.dispatchAdClosedEvent(...)` |
| `adv_revenue` | `session_id`, `scene_name`, `placement_id`, `network_placement_id`, `network_id`, `network_name`, `ad_type`, `mediation_name`, `revenue_value`, `currency`, `precision`, `network_is_connected`, auto `poseidon_config_version` | `AdBase.dispatchOnPaidEvent(...)` |
| `mediation_init` | `mediation_name`, `init_result`, `duration`, auto `poseidon_config_version` | `MediationSetupProcessor.InitContext.reportInitResult(...)` |
| `adv_adpv` | `scene_name`, `ad_type`, `result_code`, `result_message`, auto `poseidon_config_version` | fullscreen `show*Ad(...)` success/failure callbacks in `PoseidonAPIProcessor`, plus banner `loadAndShowBannerAd(...)` failure/success callbacks |
| `kjv_b_l_ad_unit_loaded` | `uuid`, `unit_id`, `error_code`, `error_info`, auto `poseidon_config_version` | `PoseidonAPIProcessor.platformLoadAdWithAnalyzer(...)` |
| `adpv_gap_detection` | `scene_name`, `ad_type`, `attribution_channel_name`, auto `poseidon_config_version` | fullscreen `show*Ad(...)` entrypoints and banner `loadAndShowBannerAd(...)` failure/success callbacks in `PoseidonAPIProcessor` |

### Config, Policy, Consent, And Attribution Events

| Event name | Params passed | Fired from |
| --- | --- | --- |
| `biz_remote_config_recv` | `name=ad_strategy_recv`, `version_name`, auto `poseidon_config_version` | `PoseidonConfig.onUpdate(...)` when a new remote config parses and has a version |
| `poseidon_config_version_report` | `from`, `version`, auto `poseidon_config_version` | `PoseidonConfig.get()`, `PoseidonConfig.onUpdate(...)`, `PoseidonConfig.updateFromBackend(...)` |
| `Total_Ads_Revenue_001` | `value`, `currency`, auto `poseidon_config_version` | `AdPaidInfo.traceFirebaseAdImpressionCumulativeRevenueGreatThan0dot01()` when accumulated revenue reaches `>= 0.01` |
| `ctr_limit_analyzer` | `force_close`, `resumed_activity_name_when_ad_clicked`, `resumed_activity_name_when_ad_impression`, `wait_for_closed_activity_names`, `activity_partial_name_from_remote_config`, auto `poseidon_config_version` | `CtrAdCloser.close(...)` |
| `key_behavior_buying_analyzer` | `stage`, `extra1`, `extra2`, `extra3`, auto `poseidon_config_version` | `PoseidonConfig.onUpdate(...)` campaign-override path |
| `consent_management_platform_analyzer` | exactly one of the CMP keys listed in the CMP variant table below, plus auto `poseidon_config_version` | `ConsentGeographyGate`, `ConsentFlowCoordinator`, `ConsentManagementPlatform.requestConsentInfoUpdate(...)` |
| `notification_splash_ad_preload_analyzer` | `sceneName`, `sessionId`, `type`, `extra`, `passed`, auto `poseidon_config_version` | `NotificationSplashAdManager.preloadAd(...)` |
| `topOn_traffic_grouping_analyzer` | `adUnitId`, `attributionChannelName`, auto `poseidon_config_version` | `TopOnTrafficCoordinator.tryDispatchToTopOn(...)` immediately before `ATSDK.initCustomMap(...)` |
| `tohunga_analyzer` | `attribution_channel_name`, auto `poseidon_config_version` | `AttributionChannelMtg.update(...)` |
| `adjust_attribution_network_analyzer` | `attribution_network_from`, `attribution_network_value`, auto `poseidon_config_version` | `AdjustAttributionNetworkStore` |
| `adjust_attribution_campaign_analyzer` | `attribution_campaign_from`, `attribution_campaign_value`, auto `poseidon_config_version` | `AdjustCampaignStore` |
| `adjust_attribution_analyzer` | `attribution_from`, `attribution_value`, auto `poseidon_config_version` | `TopOnTrafficGrouping.callOnAttributionChanged(...)` after serializing the full Adjust attribution map |
| `error_detection` | `name`, `extra`, auto `poseidon_config_version` | all actually emitted variants are listed in the error table below |

Param mapping notes for misleading keys:

- In `kjv_b_l_ad_unit_loaded`, `uuid` receives the generated load session id, and `unit_id` receives `sceneName`, not an ad unit id.
- In `adpv_gap_detection`, `ad_type` receives the raw `Int` ad type id, not the string returned by `getAdTypeName(...)`.

## Variant Details

### `error_detection` Variants That Actually Emit

`error_detection` is default-disabled and then further filtered by call-site logic when present.

| `name` param | `extra` param source | Fired from | Extra gate beyond `poseidon_analyzer_enable_error_detection` |
| --- | --- | --- | --- |
| `AeoConfigParseError` | thrown message | `AeoCfg.fromRemote()` | sampled with `TrafficLimit.isPassable(...)` only for `FirebaseCloudException` |
| `AdLimitShowConfigParseError` | thrown message | `AdLimitShowCfg.fromRemote()` | sampled with `TrafficLimit.isPassable(...)` only for `FirebaseCloudException` |
| `AdLimitShowManagerErrorAnalyzer` | runtime error message | `AdShowLimitConfigProcessor.handleError(...)` | none |
| `AdLoadTrafficControlConfigParseError` | thrown message | `AdLoadModeControlCfg.fromRemote()` | sampled with `TrafficLimit.isPassable(...)` only for `FirebaseCloudException` |
| `AdRevenueDatabaseError` | persistence failure message | `AdBase.dispatchOnPaidEvent(...)` IO write path | none |
| `AdjustAttributionParseError` | `t.toString()` | `TopOnTrafficGrouping.callOnAttributionChanged(...)` | none |
| `DestroyAdOnDismiss` | destroy exception message or stack trace | `PoseidonAPIProcessor` dismiss callback destroy path | none |
| `DestroyAdOnFailedToShow` | destroy exception message or stack trace | `PoseidonAPIProcessor` failed-to-show destroy path | none |
| `EstimatedEcpmConfigParseError` | thrown message | `EstimatedEcpmCfg.fromRemote()` | sampled with `TrafficLimit.isPassable(...)` only for `FirebaseCloudException` |
| `FAIILC_calculateLimitIntervalInMillisByDay` | `"newUserFirstOpenTime <= 0: <value>"` | `FullscreenAdImpressionIntervalLimit.calculateLimitIntervalInMillisByDay()` | none |
| `FAIILC_isLadybugLimitStrategyEnabled` | `"limitUntilNewUserInstalledInDays <= 0: <value>"` or `"newUserFirstOpenTime <= 0: <value>"` | `FullscreenAdImpressionIntervalLimit.isLadybugLimitStrategyEnabled()` | none |
| `FullscreenAdImpressionIntervalLimitConfigParseError` | thrown message | `FullscreenAdImpressionIntervalLimitConfiguration.fromRemote()` | sampled with `TrafficLimit.isPassable(...)` only for `FirebaseCloudException` |
| `ParseTopOnAdmobExtInfoMapFailed` | exception message or stack trace array string | `TopOnUtil.resolveAdPaidInfo(...)` | none |
| `poseidonConfigFromBackendParseError` | thrown message, `"json is null or empty"`, or `"encryptedPoseidonConfigString is null or empty: json=<payload>"` | `PoseidonConfig.updateFromBackend(...)` and `ConfigHub` | `ConfigHub.updateFromBackend(...)` null/empty cases are additionally sampled with `TrafficLimit.isPassable(...)` |
| `poseidonConfigParseError` | thrown message | `PoseidonConfig.fromRemote()` | none |
| `RrcRoasConfigParseError` | thrown message | `RrcRoasCfg.fromRemote()` | sampled with `TrafficLimit.isPassable(...)` only for `FirebaseCloudException` |
| `TopOnPresetStrategyChecker` | missing `assets/localStrategy` message | `TopOnSetup.initMediation(...)` | none |
| `VersionChecker` | parse exception message | `VersionHelper.tryFrom(...)` | none |

### `notification_splash_ad_preload_analyzer` Type Codes

`notification_splash_ad_preload_analyzer` is default-disabled.

| `type` | Meaning in source | Extra / passed behavior |
| --- | --- | --- |
| `1` | trigger | `extra=null`, `passed=null` |
| `2` | cache check | `extra=hasCacheAdMessage`, `passed=!hasCacheAd` |
| `3` | request interval check | `extra=isRequestIntervalTooShortMessage`, `passed=!isRequestIntervalTooShort` |
| `4` | consecutive load failures check | `extra="adConsecutiveLoadFailuresCount=<count>, maxAdConsecutiveLoadFailuresCountFromRemote=<max>"`, `passed=count < max` |
| `5` | ad fill unused check | `extra="maxAdFilledUnusedCount=<max>, adFillCount=<count>"`, `passed=count < max` |
| `6` | ad impression limit check | `extra=null`, `passed=AdShowLimit.canLoadAd()` |
| `7` | configuration check | `extra=notificationSplashAdConfigCheckMessage`, `passed=configurationPassed` |
| `8` | load | `extra=notificationSplashAd.toString()`, `passed=null` |
| `9` | network check | `extra=null`, `passed=isNetworkAvailable()` |

### `consent_management_platform_analyzer` Variants That Actually Emit

`consent_management_platform_analyzer` is default-disabled.

Each emission carries exactly one of the following payload keys:

| Param key | Value shape | Fired from |
| --- | --- | --- |
| `is_in_europe` | `1` when true, `-1000` when false | `ConsentGeographyGate.runIfNeeded(...)` |
| `cmp_error_id` | `4` | `ConsentGeographyGate.runIfNeeded(...)` failure path only |
| `cmp_error_msg` | `Throwable.message.orEmpty()` | `ConsentGeographyGate.runIfNeeded(...)` failure path only |
| `is_consent_agreed` | `1` when ads can be requested, `-1000` otherwise | `ConsentFlowCoordinator.handleAppStartup(...)`, `ConsentFlowCoordinator.handleShowConsentForm(...)` |
| `consent_form_dialog_show` | `1` when using cached form, `-1000` when load-and-show path is used | `ConsentFlowCoordinator.handleShowConsentForm(...)` |
| `click_agree_from_consent_form` | `1` when `errorCode == null`, `-1000` otherwise | `ConsentFlowCoordinator.handleShowConsentForm(...)` dismiss callback |
| `privacy_options_form_show` | always `1` | `ConsentFlowCoordinator.handleShowPrivacyOptions(...)` |
| `ump_tag_for_under_age_of_consent` | `1` when true, `-1000` when false | `ConsentManagementPlatform.requestConsentInfoUpdate(...)` |

### `key_behavior_buying_analyzer` Stages

| `stage` | `extra1` | `extra2` | `extra3` |
| --- | --- | --- | --- |
| `1` | `updatedKeys.joinToString(",")` | `poseidon_ka_poseidon_config_` | matched updated key or `null` |
| `2` | matched override key | campaign suffix from Firebase key | current Adjust campaign |
| `3` | new config version string | campaign suffix from Firebase key | current Adjust campaign |

### `adjust_attribution_*` `from` Values

| Event | `from` param values seen in source |
| --- | --- |
| `adjust_attribution_network_analyzer` | `onAttributionChanged`, `getAttributionNetworkAsync`, `readjustAttributionNetworkWhenStartup`; emitted only when a non-empty network value changes |
| `adjust_attribution_campaign_analyzer` | `onAttributionChanged`, `getAttributionCampaignAsync`, `readjustAttributionCampaignWhenStartup`; emitted only when a non-empty campaign value changes |
| `adjust_attribution_analyzer` | `callOnAttributionChanged` |

## Direct Firebase Events

| Event name | Params passed | Fired from |
| --- | --- | --- |
| `FirebaseAnalytics.Event.AD_IMPRESSION` | `FirebaseAnalytics.Param.AD_PLATFORM`, `FirebaseAnalytics.Param.AD_SOURCE`, `FirebaseAnalytics.Param.AD_FORMAT`, `FirebaseAnalytics.Param.AD_UNIT_NAME`, `FirebaseAnalytics.Param.VALUE`, `FirebaseAnalytics.Param.CURRENCY` | `AdPaidInfo.trackFirebaseAdRevenue()` |
| `Ad_impression_Revenue` | `FirebaseAnalytics.Param.AD_PLATFORM`, `FirebaseAnalytics.Param.AD_SOURCE`, `FirebaseAnalytics.Param.AD_FORMAT`, `FirebaseAnalytics.Param.AD_UNIT_NAME`, `FirebaseAnalytics.Param.VALUE`, `FirebaseAnalytics.Param.CURRENCY` | `AdPaidInfo.trackFirebaseAdRevenue()` |
| dynamic AEO Firebase event name from `AeoCfg.firebaseEventName` (`pos_firebaseEvent`) | no params (`mapOf()`) | `trackFirebaseAeo(...)` -> `trackAeo()` |
| dynamic RRC Firebase event name from `RrcRoasEcpmPer.firebaseEvent` or `RrcRoasArpuLimit.firebaseEvent` (`pos_firebase_event`) | no params (`mapOf()`) | `trackFirebaseRrcRoas(...)` -> `trackRrcRoas(...)` |

Notes:

- The source does not inline the literal value of `FirebaseAnalytics.Event.AD_IMPRESSION`; it passes the Google constant directly.
- Direct Firebase events do not receive auto `poseidon_config_version`.
- AEO and RRC direct Firebase events only emit when the configured event name is non-empty and the runtime threshold/gating logic decides to report.

## Direct Adjust Emissions

### `Adjust.trackAdRevenue(...)`

Fired from `AdPaidInfo.trackAdjustAdRevenue()`.

Payload mapping:

- source string:
  - TopOn mediation -> `topon_sdk`
  - AdMob mediation -> `admob_sdk`
  - MAX mediation -> `applovin_max_sdk`
- `setRevenue(revenue, currencyCode)`
- `adRevenueNetwork = adSourceId.toString()`
- `adRevenueUnit = adSourcePlacementId`
- `adRevenuePlacement = adUnitId`

### `Adjust.trackEvent(...)` Families

| Token source | Extra payload set before send | Fired from |
| --- | --- | --- |
| dynamic AEO token from `AeoCfg.adjustToken` (`pos_eventToken`) | none | `trackAdjustAeo(...)` -> `trackAeo()` |
| dynamic RRC token from `RrcRoasEcpmPer.adjustToken` or `RrcRoasArpuLimit.adjustToken` (`pos_adjust_token`) | none | `trackAdjustRrcRoas(...)` -> `trackRrcRoas(...)` |
| `AdjustEventTokenProvider.adImpressionToken()` -> `PoseidonConfig.adjustTokens.adImpressionToken` | `setRevenue(revenue, currencyCode)` | `AdPaidInfo.trackAdjustEventWhenAdImpressed()` |
| `AdjustEventTokenProvider.fullscreenImpressionToken()` -> `PoseidonConfig.adjustTokens.fullscreenAdImpressionToken` | `setRevenue(revenue, currencyCode)` | `AdPaidInfo.trackAdjustEventWhenFullscreenAdImpressed()` |
| `AdjustEventTokenProvider.highValueFirstDayToken()` -> `PoseidonConfig.adjustTokens.highValueFirstDayToken` | `setRevenue(revenue, currencyCode)` | `AdPaidInfo.trackAdjustEventWhenHighRevenueFullscreenAdImpressedOnNewUserFirstDayAfterInstalled()` |
| `AdjustEventTokenProvider.highValueNextDayToken()` -> `PoseidonConfig.adjustTokens.highValueNextDayToken` | `setRevenue(revenue, currencyCode)` | `AdPaidInfo.trackAdjustEventWhenHighRevenueFullscreenAdImpressedOnNewUserNextDayAfterInstalled()` |

Direct Adjust gate notes:

- AEO and RRC direct Adjust events only emit when the configured token is non-empty and the runtime threshold/gating logic decides to report.
- The high-value first-day and next-day token events are only considered for fullscreen ad paid events.
- Those high-value token events only emit when `revenue > high_revenue_in_ecpm_unit / 1000`.
- First-day and next-day high-value token events each have their own one-shot shared-preference guard.

## Verified Non-Emitting Analyzer Constructions

These helpers are present in source but do not emit because `.analyze()` is never called:

- `ErrorDetectionReporter(name = "BannerAdBaseError", ...)` in `BannerAdBase.isReady()`, `BannerAdBase.destroy()`, and `BannerAdBase.eCPM()`
- `ErrorDetectionReporter(name = "HasCacheAdError", ...)` in `StrategyHub.hasCacheAd(...)`
- `ConsentManagementPlatformAnalyzer.errorType(3)` and matching `errorMessage(...)` in `ConsentFlowCoordinator.handleAppStartup(...)`
- `ConsentManagementPlatformAnalyzer.errorType(2)` and matching `errorMessage(...)` in `ConsentFlowCoordinator.handleAppStartup(...)`
- `ConsentManagementPlatformAnalyzer.errorType(2)` and matching `errorMessage(...)` in `ConsentFlowCoordinator.handleShowConsentForm(...)`
- `ConsentManagementPlatformAnalyzer.errorType(2)` and matching `errorMessage(...)` in `ConsentFlowCoordinator.handlePrivacyRequirement(...)`

## Source Anchors

- Reporter plumbing: `poseidon/src/androidMain/kotlin/com/poseidon/ad/statistics/BaseReporter.kt`
- Event name and param key constants: `poseidon/src/androidMain/kotlin/com/poseidon/ad/statistics/BizAdStatisticsConstants.kt`
- Core ad reporters: `poseidon/src/androidMain/kotlin/com/poseidon/ad/statistics/core/`
- Biz/policy reporters: `poseidon/src/androidMain/kotlin/com/poseidon/ad/statistics/biz/`
- Direct Firebase/Adjust revenue + token events: `poseidon/src/androidMain/kotlin/com/poseidon/ad/main/price/AdPaidInfo.kt`
- Dynamic AEO events: `poseidon/src/androidMain/kotlin/com/poseidon/ad/main/strategy/aeo/AeoCfg.kt`
- Dynamic RRC events: `poseidon/src/androidMain/kotlin/com/poseidon/ad/main/strategy/rrc/RrcRoasTracker.kt`
