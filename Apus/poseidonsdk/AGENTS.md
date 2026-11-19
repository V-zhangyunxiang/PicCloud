# AGENTS.md

Guide for AI agents working in the Poseidon SDK codebase.

## Project Overview

Poseidon SDK is a cross-platform advertising mediation SDK for Android (iOS planned). It unifies three ad mediation networks (TopOn, AdMob, MAX) behind a single API, handling ad loading, showing, frequency control, CMP/GDPR consent, and analytics reporting.

**Modules:**
- `poseidon/` - Main SDK library (Android library with Rust native crypto)
- `poseidon-cryptor/` - Standalone Java encryption utility
- `app/` - Demo app for testing

**Language:** Kotlin (Android), Rust (native crypto via JNI)

**Build System:** Gradle with version catalog (`gradle/libs.versions.toml`)

---

## Essential Commands

### Build
```bash
# Build all product flavors
./gradlew assembleAllProducts

# Build specific variant (e.g., lumina debug)
./gradlew :poseidon:bundleLuminaDebugAar

# Build demo app (only debug enabled)
./gradlew :app:assembleDebug
```

### Test
```bash
# Unit tests (JUnit 4)
./gradlew :poseidon:testDebugUnitTest

# poseidon-cryptor tests (JUnit 5)
./gradlew :poseidon-cryptor:test
```

### Publish
```bash
# Publish specific product to Maven repository
./script/publish-poseidon-sdk <ProductName>  # e.g., NovaBrowser

# Publish all variants
./gradlew :poseidon:publishAllVariants
```

### Clean (including Rust build cache)
```bash
./gradlew clean  # Also deletes poseidon/src/rust/cryptor/target
```

---

## Architecture

### Layer Structure

```
api/                 → Public API facade (PoseidonAPI, PoseidonSetupProcessor)
main/processor/      → Internal processor that delegates to mediation layers
core/flow/           → AdFlow orchestrates load/show routing
mediation/{admob,max,topon}/  → Network-specific load/show implementations
main/strategy/       → Configuration parsing (PoseidonConfig, StrategyCfg, SceneCfg)
restrict/            → Frequency caps, show limits, GDPR/CMP, content balance
statistics/          → Analytics reporters (Firebase, Adjust)
main/price/          → Revenue tracking, eCPM estimation, paid event dispatch
cloud/               → Firebase Remote Config integration
```

### Control Flow

1. **Initialization**: `PoseidonAPI.register()` → `PoseidonSetupProcessor` → mediation init (TopOn/AdMob/MAX)
2. **Load**: `PoseidonAPI.loadInterstitialAd()` → `AdFlow` → checks new-user ads, notification splash, then `PoseidonAPIProcessor.platformPreload()` → `AdLoaderBase.load()` → network-specific loader
3. **Show**: `PoseidonAPI.showInterstitialAd()` → `PoseidonAPIProcessor` → cache lookup or realtime request → `AdBase` callbacks → revenue/analytics dispatch
4. **Config**: `PoseidonConfig` reads from Firebase Remote Config, embedded release config, or local debug file; decrypts via Rust JNI

### Key Patterns

- **Parameter objects**: All load/show APIs use `Platform*AdLoadParameter` / `Platform*AdShowParameter` classes
- **Cancelable/Destroyable**: Show methods return `Cancelable` for lifecycle management; Banner returns `Destroyable`
- **Reporter pattern**: All analytics extend `BaseReporter`, gated by remote config key `poseidon_analyzer_enable_<eventName>`
- **Singleton state**: `PoseidonConfig`, `PoseidonSetupProcessor` use lazy singleton with lock

---

## Product Flavors & Build Variants

The SDK uses product flavors for different apps (configured in `product_env/`):

| Flavor | Config file | Purpose |
|--------|-------------|---------|
| `lumina` | `lumina_config.properties` | Lumina app |
| `new` | `ember_config.properties` | Demo/testing |

Each flavor requires `release_ad_config` (encrypted config) and `release_admob_app_id` in properties file.

**Build types:**
- `debug` - POSEIDON_DEBUG_SWITCH=true, test AdMob ID, debuggable
- `release` - POSEIDON_DEBUG_SWITCH=false, minified, real config
- `qa` - inherits release but debuggable, uses release dependencies

**AAR naming:** `{version}-{flavor}-{buildType}.aar`

**Gradle properties for custom products:**
```bash
./gradlew assembleAllProducts -PPOSEIDON_SDK_PRODUCTS=lumina,ember
./gradlew assembleAllProducts -PPOSEIDON_SDK_BUILD_TYPES=debug,release
```

---

## Configuration System

### PoseidonConfig

JSON configuration with version-based updates. Structure (see `PoseidonConfig.kt`):

```kotlin
@Serializable
data class PoseidonConfig(
    @SerialName("pos_version") val versionString: String,
    @SerialName("pos_switch") val switch: Int,
    @SerialName("pos_strategy") val strategies: List<StrategyCfg>?,
    @SerialName("pos_scene") val scenes: List<SceneCfg>?,
    @SerialName("pos_mediations") val mediations: MediationsCfg?,
    // ... other fields
)
```

**Config resolution order:**

1. **Debug mode**: Try local file `poseidon_config.json` on SD card first, fall back to release flow
2. **Release mode**: 
   - Embedded release config from assets (set via `release_ad_config` in product properties)
   - Firebase Remote Config key `poseidon_config`
   - Higher version wins

**Encryption:** All configs are AES-256-CBC encrypted via Rust JNI (`Cryptor.decrypt()`).

### Strategy Resolution

`StrategyHub` resolves which mediation to use for a scene:
- Checks `PoseidonConfig.scenes` for scene name
- Gets `strategy_id` from scene
- Finds strategy in `PoseidonConfig.strategies`
- Strategy defines which mediation networks and ad unit IDs to use

---

## Mediation Networks

Three networks supported, identified by constants:

| Constant | Value | Network |
|----------|-------|---------|
| `MEDIATION_TOPON` | 26 | AnyThink/TopOn |
| `MEDIATION_ADMOB` | 2 | Google AdMob |
| `MEDIATION_MAX` | 5 | AppLovin MAX |

**Network adapters:** TopOn includes adapters for Vungle, Unity, Bigo, Facebook, AdMob, AppLovin, Kwai, Pangle, InMobi, Fyber, Maticoo. AdMob and MAX have their own mediation adapters.

**Class pattern:** Each network has `{Network}{AdType}Load` and `{Network}{AdType}Show` classes (e.g., `TopOnInterstitialLoad`, `AdmobBannerShow`).

---

## Ad Types

| Constant | Type | Classes |
|----------|------|---------|
| `AD_TYPE_BANNER` | Banner | `BannerAdBase`, `BannerAdLoad` |
| `AD_TYPE_INTERSTITIAL` | Interstitial | `InterstitialAdBase`, `InterstitialAdLoader` |
| `AD_TYPE_REWARDED` | Rewarded | `RewardedAdBase`, `RewardedAdLoader` |
| `AD_TYPE_OPEN` | App Open | `OpenAdBase`, `OpenAdLoader` |
| `AD_TYPE_NATIVE` | Native | `NativeAdBase`, `NativeAdLoader` |

---

## Rust Native Library

**Location:** `poseidon/src/rust/cryptor/`

**Build:** Integrated via `org.mozilla.rust-android-gradle` plugin, builds for arm, arm64, x86, x86_64

**JNI bindings:**
- `Java_com_poseidon_ad_main_strategy_cryptor_Cryptor_encrypt`
- `Java_com_poseidon_ad_main_strategy_cryptor_Cryptor_decrypt`
- `Java_com_poseidon_ad_main_strategy_cryptor_Cryptor_isPassableByFlowControl`

**Library name:** `libPoseidonCypt.so`

**CLI tools:** `poseidon/src/rust/cryptor_cli/` provides standalone encrypt/decrypt binaries for macOS, Windows, Linux.

---

## Testing

### Unit Tests

- Located in `poseidon/src/test/kotlin/`
- Use JUnit 4 (`debugImplementation libs.junit`)
- Test configuration parsing, resolver logic, utility functions

**Example test pattern:**
```kotlin
@Test
fun `null config yields immediate`() {
    val result = evaluateAdLoadTrafficOutcome(cfg = null, adUnitId = "unit_a")
    assertTrue(result is AdLoadTrafficOutcome.RunImmediately)
}
```

### Debug Configuration

In debug builds, place `poseidon_config.json` on device SD card root to test custom strategies without Firebase.

---

## Analytics Events

All reporter-based events gated by `poseidon_analyzer_enable_<eventName>` remote config.

**Core events (see `doc/poseidon-sdk-event-map.md`):**
- `adv_res` - Ad response (load success/failure)
- `adv_show` - Ad impression/failed to show
- `adv_click` - Ad click
- `adv_hide` - Ad close
- `adv_revenue` - Paid event with revenue
- `mediation_init` - Mediation initialization result

**Direct events (no gate):**
- Firebase `AD_IMPRESSION` via `AdPaidInfo.trackFirebaseAdRevenue()`
- Adjust `trackAdRevenue()` and `trackEvent()` for revenue/attribution

**Auto-attached params:** `poseidon_config_version` added by `Reporter.analyze()` unless already present; `poseidon_sdk_version` (`{libVersion}-{flavor}-{buildType}` from `BuildConfig.POSEIDON_LIB_VERSION`/`POSEIDON_PRODUCT_NAME`/`BUILD_TYPE`) added by `Reporter.analyze()` and by `ReporterWrapper.logEvent()` (covers direct Firebase events) unless already present.

---

## Frequency Control & Restrictions

### Modules

- `main/processor/CriticalPressureBreaker.kt` - Pressure-activated circuit breaker (see below)
- `restrict/unification/` - Global frequency control manager
- `restrict/show/` - Per-ad-type show limits (`AdShowLimit`)
- `restrict/traffic/` - Traffic throttling (`TrafficLimit`)
- `restrict/ctr/` - CTR-based ad auto-close
- `restrict/content/` - Content vs ad balance (pages with ads limits)
- `restrict/new/` - New user first-show special handling
- `restrict/gdpr/` - CMP/UMP consent management

### Traffic Limit Usage

```kotlin
if (TrafficLimit.isPassable(remoteKey = "some_key", defaultScale = 30)) {
    // Sampled error reporting
}
```

### Critical Pressure Breaker

`main/processor/CriticalPressureBreaker.kt` (internal object) trips only on devices inside a
critical pressure state; healthy devices see zero behavior change. Design rule: **at CRITICAL, a
cached ad below the show-gate eCPM bar is destroyed wherever it is found; an ad at/above the bar
survives and may still be shown.**

- Inputs: critical heap band (remote-keyed 90%/16MiB via `poseidon_preload_critical_*`), thread
  count from `/proc/self/status`, native heap, `lastTrimLevel`, blocking-GC delta; signals sampled
  on `PoseidonWorker`, cached 10s, never read on the main thread
- Actuators: eCPM-gated cache scavenge reusing `CacheAd.destroySafely()` across all pools
  (`evictCacheAdsBelowEcpm` in `Cache.kt` + per-pool `scavengeBelowEcpm`), debounced emergency
  `Runtime.gc()` (worker only), `ComponentCallbacks2` trim handling registered in
  `PoseidonSetupProcessor.platformInitialize()`
- Show gate in `PoseidonAPIProcessor.platformShowFullscreenAd`: at CRITICAL, show only a cached
  ad with `cachedEcpm >= poseidon_critical_show_min_ecpm` (default 200.0), else destroy candidates
  and fail with -1058 `ERROR_CODE_CRITICAL_PRESSURE_SHOW_SUPPRESSED`; fullscreen only (native and
  banner shows are not gated; their SDK-initiated preload triggers are)
- Load gate in `PoseidonAPIProcessor.platformPreload`: at
  CRITICAL, host/SDK-initiated loads fail with -1059 `ERROR_CODE_CRITICAL_PRESSURE_LOAD_SUPPRESSED`
- Reporting owner: **`statistics/biz/CriticalPressureReporter.kt`** is the single owner for all
  OOM-circuit-breaker analytics. Every gate decision must fire **both** the host callback
  (`AdLoadListener.onAdLoadFailed` or `FullScreenContentCallback.onAdFailedToShowFullScreenContent`)
  **and** an analytics event carrying the real `sceneName` and the trip reason. Do not emit
  `UnitResponseTracker` directly from guard/breaker/processor for pressure events.
- Rate-limit policy: legacy -1053 `ERROR_CODE_BACKGROUND_LOW_MEMORY_PRELOAD` analytics remain
  throttled to one per 15-minute slot and gated by a 16MiB reporting heap reserve. Breaker-trip
  preload skips (-1055/-1056/-1057) are evidence and are **not** rate-limited.
- Master key `poseidon_pressure_breaker_enabled` defaults to **true**; per-feature sub-switches:
  `_show_gate_enabled`, `_scavenge_enabled`, `_breaker_gc_enabled`
- Full rationale: `doc/fengfu/ember116/oom-mitigation-deep-dive-20260805.md`; build plan:
  `doc/fengfu/ember116/oom-circuit-breaker-implementation-plan-20260805.md`

---

## Important Gotchas

### Build Configuration

1. **QA build type** - Must configure Gradle attribute mapping to use release dependencies:
   ```kotlin
   variant.compileConfiguration?.attributes?.attribute(BuildTypeAttr.ATTRIBUTE, releaseBuildTypeAttr)
   ```

2. **Demo app** - Explicitly disables QA and release variants to prevent using real ad config:
   ```kotlin
   beforeVariants(selector().withBuildType("qa")) { it.enable = false }
   beforeVariants(selector().withBuildType("release")) { it.enable = false }
   ```

3. **Resource obfuscation** - If using AndResGuard/AabResGuard, add `poseidon/topon6.4.69-android-resguard-whitelists.txt` to whitelist

### Runtime Behavior

4. **Config version comparison** - Remote config with higher version overrides embedded; missing version causes fallback

5. **Cancelable usage** - Show methods return `Cancelable`; must call `cancel()` when leaving Activity to prevent showing on destroyed context

6. **Banner size** - Only 320x50 supported; container must be `FrameLayout` with `match_parent` width, `wrap_content` height

7. **CMP geography gate** - GDPR consent only required for users in Europe/EEA; `ConsentGeographyGate` checks before showing form

8. **Firebase dependency** - SDK uses `compileOnly` for Firebase; host app must provide `firebase-analytics` and `firebase-config`

9. **Adjust dependency** - SDK uses `compileOnly` for Adjust; host app must provide `adjust-android` and call `setOnAttributionChangedListener` for TopOn traffic grouping

10. **Session IDs** - Each load request generates unique session ID; used to correlate load→show→close events

### ProGuard

Consumer rules in `poseidon/consumer-rules.pro`. SDK is minified in release builds.

---

## Code Conventions

### Kotlin Style

- Official Kotlin code style (`kotlin.code.style=official`)
- JVM target: 1.8 (SDK), 17 (demo app)
- Use `@SerialName` for JSON field mapping in config classes
- Internal visibility for most classes; `api/` package is public

### Naming Patterns

- `*Cfg` - Configuration data classes
- `*Tracker` - Analytics reporter classes
- `*Resolver` - Decision/outcome resolver classes
- `*Mtg` - "Meeting" classes for coordination logic
- `*Helper` / `*Util` - Utility objects

### Logging

Use `LogHub` from `core/log/`:
```kotlin
LogHub.info(TAG) { "message" }
LogHub.warn(TAG) { "warning message" }
```

Debug logging controlled by `BuildConfig.POSEIDON_DEBUG_SWITCH`.

---

## Documentation References

- `poseidon/README.md` - Integration guide for host apps
- `doc/api/PoseidonAPI.md` - API reference
- `doc/poseidon-sdk-event-map.md` - Complete analytics event map
- `doc/poseidon-config/template/` - Configuration JSON templates

---

## Publishing

Maven repository: `https://maven.apuscn.com/nexus/content/repositories/android-xal-releases/` (snapshots: `-snapshots/`)

Credentials via Gradle properties: `MAVEN_USERNAME`, `MAVEN_PASSWORD`

Version format: `{VERSION_NAME}-{flavor}-{buildType}`

**Script:** `script/publish-poseidon-sdk` publishes debug + release for a product flavor.
