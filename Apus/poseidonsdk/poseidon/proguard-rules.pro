# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-flattenpackagehierarchy 'poseidon'
-keepparameternames
-keepattributes SourceFile,LineNumberTable,Signature,RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
# Preserve Kotlin metadata so named parameters remain available to SDK consumers.
-keep class kotlin.Metadata { *; }
-keep class com.poseidon.ad.api.** { *; }
-keep interface com.poseidon.ad.api.** { *; }
-keep class com.poseidon.ad.main.processor.PoseidonAPIProcessor { *; }
-keep class com.poseidon.ad.core.processor.Platform*Parameter { *; }
-keep class com.poseidon.ad.core.processor.** { *; }
-keep class com.poseidon.ad.main.show.mixed.NativeMixInterstitialAdActivity { *; }
-keep class com.poseidon.ad.main.show.mixed.NativeMixOpenAdActivity { *; }
-keep class com.poseidon.ad.main.show.mixed.NativeMixRewardedAdActivity { *; }
-keep class com.poseidon.ad.mediation.topon.show.TopOnOpenActivity { *; }
-keep class com.poseidon.ad.common.toolkit.Cancelable { *; }
-keep class com.poseidon.ad.common.toolkit.CancelableKt { *; }
-keep class com.poseidon.ad.restrict.gdpr.PlatformConsentFormShowParameter { *; }
-keep class com.poseidon.ad.common.toolkit.Destroyable { *; }
-keep class com.poseidon.ad.common.toolkit.DestroyableKt { *; }
-keep class com.poseidon.ad.special.topon.TopOnTrafficGrouping { *; }
-keep class com.poseidon.ad.special.topon.TopOnTrafficGroupingKt { *; }
-keep class com.applovin.mediation.adapters.** { *; }

-keep class com.poseidon.ad.main.processor.PlatformHasCacheParameter {*;}
-keep class com.poseidon.ad.main.processor.PlatformInterstitialAdLoadParameter {*;}
-keep class com.poseidon.ad.main.processor.PlatformInterstitialAdShowParameter {*;}
-keep class com.poseidon.ad.main.processor.PlatformRewardedAdLoadParameter {*;}
-keep class com.poseidon.ad.main.processor.PlatformRewardedAdShowParameter {*;}

# SDK API
-keep class com.maticoo.sdk.**{*;}
-keep class com.zmaticoo.sdk.**{*;}
-keep class com.maticooad.sdk.**{*;}
-keep interface com.maticoo.sdk.**{*;}
-keep interface com.zmaticoo.sdk.**{*;}
-keep interface com.maticooad.sdk.**{*;}
-dontwarn com.maticoo.sdk.**
-dontwarn com.zmaticoo.sdk.**

# For Inmobi integration
-keep class com.inmobi.*
-keep public interface com.inmobi.ads.listeners** {*; }
-keep public interface com.inmobi.ads.InMobiInterstitial** {*; }
-keep public interface com.inmobi.ads.InMobiBanner** {*; }
-keep class com.facebook.ads.** { *; }
-keep class com.facebook.ads.redexgen.** { *; }
-dontwarn com.facebook.ads.**

-keepclassmembers class com.poseidon.ad.mediation.admob.PartyCrawler$PartyChair {
<fields>;
 }
