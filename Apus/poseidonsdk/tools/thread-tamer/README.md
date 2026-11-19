# Thread Tamer

Thread Tamer governs threads in an Android host and its packaged SDKs. The host owns both pieces:

- `plugin/` rewrites bytecode through the Android Gradle Plugin with `InstrumentationScope.ALL`.
- `runtime/` supplies the static bridge, policy, counters, pool shaping, and diagnostic snapshots.

Neither piece imports Poseidon classes. The Poseidon module has no dependency on either piece. A host
can remove Thread Tamer without rebuilding or changing the SDK.

## Reference behavior retained

The reference project routes 31 active call shapes through its runtime. Thread Tamer covers the same
set with a different ABI and JSON schema:

| Call family | Coverage |
|---|---|
| `Thread` construction | Eight public constructor shapes |
| `HandlerThread` construction | Name and name/priority shapes |
| `Thread.start()` | Virtual call redirected with the receiver as a bridge argument |
| `Executors` | Cached, fixed, single, scheduled, single-scheduled, and work-stealing factories |
| `ThreadPoolExecutor` | Four public constructor shapes |
| `ScheduledThreadPoolExecutor` | Four public constructor shapes |

The runtime records creation sources, prefixes governed worker names, caps pool width, lets core
workers expire, removes cancelled scheduled tasks, and lowers selected background SDK priorities.
Snapshots include system, host, vendor, and unknown counts; thread states; top names; new-thread
deltas; SDK family residency; startup creation totals; and interception counters. Queue-backed
pools are width-capped. Unbounded `SynchronousQueue` pools are converted to capped queue-backed
pools so excess work waits instead of spawning unlimited threads or being rejected. The runtime can
reject launches after an emergency thread ceiling. `failClosedAtCeiling` defaults to `false`, so the
default policy does not suppress a thread launch.

The reference config exposes queue, delay, and shared-pool fields that its active runtime does not
use. Thread Tamer keeps renamed counterparts for policy migration. They do not alter execution until
a host opts into a later strategy that consumes them.

## Published consumption

Add the XAL repository to plugin and dependency resolution in the host `settings.gradle`:

```groovy
pluginManagement {
    repositories {
        maven { url 'https://maven.apuscn.com/nexus/content/repositories/android-xal-releases/' }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven { url 'https://maven.apuscn.com/nexus/content/repositories/android-xal-releases/' }
        google()
        mavenCentral()
    }
}
```

Apply the published plugin. It loads its packaged route table, so the host does not need a JSON file:

```groovy
plugins {
    id 'io.threadtamer.weaver' version '1.0.0'
}

threadTamer {
    active.set(true)
    ignoredTypePrefixes.set([
        "io.threadtamer.runtime.",
        "kotlin.", "kotlinx.", "java.", "javax.", "android.", "androidx."
    ])
}

dependencies {
    debugImplementation "io.threadtamer:thread-tamer-runtime:1.0.0-debug"
    qaImplementation "io.threadtamer:thread-tamer-runtime:1.0.0-qa"
    releaseImplementation "io.threadtamer:thread-tamer-runtime:1.0.0-release"
}
```

The host installs its policy before ad SDK initialization:

```kotlin
import io.threadtamer.runtime.ConcurrencyLimits
import io.threadtamer.runtime.ThreadTamer

ThreadTamer.install(
    ConcurrencyLimits(
        emitDiagnostics = false,
        vendorParallelismCeiling = 2,
        failClosedAtCeiling = false
    )
)
```

Applying the plugin to an Android application rewrites app classes and dependency classes. Applying
it to an Android library rewrites that library's project classes. The application remains the owner
of full dependency-graph governance.

## Policy fields

| Field | Effect |
|---|---|
| `active` | Enables runtime shaping after `install()` |
| `emitDiagnostics` | Logs periodic thread counts and counters |
| `launchObservationMillis` | Sets the startup observation window used in diagnostics |
| `warmThreadNotice`, `warmThreadAlarm` | Classify sampled process thread counts |
| `processThreadCeiling`, `overflowAllowance` | Define the optional launch rejection threshold |
| `failClosedAtCeiling` | Rejects non-exempt launches above the emergency threshold |
| `generalParallelismCeiling`, `vendorParallelismCeiling` | Cap queue-backed and finite executor width by caller type |
| `generalTimerWidth`, `vendorTimerWidth` | Cap scheduled executor width |
| `reclaimAfterSeconds` | Sets idle timeout on shaped pools |
| `advisoryLooperNames`, `advisoryCallers` | Select diagnostic `HandlerThread` reports |
| `exemptThreadNames`, `exemptCallers` | Preserve critical platform and tool paths |
| `vendorCallers` | Classify SDK-created threads and pools |

`legacyHostPauseMillis`, `legacyVendorPauseMillis`, `legacyOverflowPauseMillis`,
`legacyPauseCapMillis`, `taskQueueSlots`, and `mergeVendorExecutors` carry policy data without changing
the current execution path. This matches the active reference behavior, which retains those settings
after removing launch delays and default shared-pool collapse.

## Route schema

The plugin JAR contains
`io/threadtamer/gradle/thread-tamer-routes.json`. It uses `schema`, `active`, `bridgeType`, and
`operations`. Each operation has an `origin` call and a `redirect` call. Constructor routes return
the new instance from the bridge; the visitor removes matching `NEW` and `DUP` instructions only
when it can prove a safe allocation sequence. It leaves unusual constructor stack manipulation
untouched.

`threadTamer.ruleBook` remains available as an optional build-specific override. Production hosts
should use the packaged routes unless they also ship matching bridge changes.

## Publishing

`publication.properties` owns the shared group, version, artifact IDs, name, and description. The
publish script uses the same XAL release and snapshot repositories as Poseidon.

Publish all artifacts to Maven Local for validation:

```bash
tools/thread-tamer/publish.sh --local
```

Publish the plugin, plugin marker, and all runtime variants to XAL:

```bash
tools/thread-tamer/publish.sh --remote
```

The remote command reads `MAVEN_USERNAME` and `MAVEN_PASSWORD` from Gradle properties. A version that
ends with `SNAPSHOT` goes to the snapshot repository. Other versions go to the release repository.

Published coordinates for version `1.0.0`:

| Artifact | Coordinate |
|---|---|
| Gradle plugin implementation | `io.threadtamer:thread-tamer-weaver:1.0.0` |
| Gradle plugin marker | `io.threadtamer.weaver:io.threadtamer.weaver.gradle.plugin:1.0.0` |
| Debug runtime | `io.threadtamer:thread-tamer-runtime:1.0.0-debug` |
| QA runtime | `io.threadtamer:thread-tamer-runtime:1.0.0-qa` |
| Release runtime | `io.threadtamer:thread-tamer-runtime:1.0.0-release` |

## Verification

```bash
./gradlew -p tools/thread-tamer/plugin test
./gradlew :tools:thread-tamer:runtime:testDebugUnitTest
tools/thread-tamer/publish.sh --local
./gradlew :app:compileDebugKotlin -x :app:checkDebugAarMetadata
```

The metadata exclusion isolates an existing repository mismatch: current BidMachine and AndroidX
Browser artifacts require compile SDK 35/36 and a newer Android Gradle Plugin, while this repository
uses compile SDK 34 and AGP 8.7.3.

Use `audit/inspect-sdk-threads.sh` to regenerate the JADX evidence from the local Gradle artifact
cache. The audit manifest includes the removed Maticoo binaries so reviewers can compare the old
dependency graph with the new graph.
