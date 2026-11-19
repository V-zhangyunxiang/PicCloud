-keep class com.poseidon.ad.main.strategy.cryptor.Cryptor {
    java.lang.String encrypt(java.lang.String);
    java.lang.String decrypt(java.lang.String);
    boolean isPassableByFlowControl(java.lang.String,int);
}

# >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>Guava<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
# Copy from https://github.com/google/guava/blob/master/proguard/base.pro
# Note: We intentionally don't add the flags we'd need to make Flags and Enums
# work. That's because the Proguard configuration required to make them work on
# optimized code would preclude lots of optimization, like converting enums
# into ints.

# Throwables uses internal APIs for lazy stack trace resolution
# ProGuard is intelligent enough to spot the use of reflection onto this, so we
# don't need to add the flags we'd need to make them work.
-dontnote sun.misc.SharedSecrets
-keep class sun.misc.SharedSecrets {
  *** getJavaLangAccess(...);
}
-dontnote sun.misc.JavaLangAccess
-keep class sun.misc.JavaLangAccess {
  *** getStackTraceElement(...);
  *** getStackTraceDepth(...);
}

# FinalizableReferenceQueue calls this reflectively
# ProGuard is intelligent enough to spot the use of reflection onto this, so we
# only need to keep the names, and allow it to be stripped out if unused.
-keepnames class com.google.common.base.internal.Finalizer {
  *** startFinalizer(...);
}
# However, it cannot "spot" that this method needs to be kept IF the class is.
-keepclassmembers class com.google.common.base.internal.Finalizer {
  *** startFinalizer(...);
}
-keepnames class com.google.common.base.FinalizableReference {
  void finalizeReferent();
}
-keepclassmembers class com.google.common.base.FinalizableReference {
  void finalizeReferent();
}

# Copy from https://github.com/google/guava/blob/master/proguard/cache.pro
# Striped64 uses this
-dontwarn sun.misc.Unsafe

# Striped64 appears to make some assumptions about object layout that
# really might not be safe. This should be investigated.
-keepclassmembers class com.google.common.cache.Striped64 {
  *** base;
  *** busy;
}
-keepclassmembers class com.google.common.cache.Striped64$Cell {
  <fields>;
}

# Copy from https://github.com/google/guava/blob/master/proguard/collect.pro
-dontwarn java.lang.SafeVarargs

# The nested FieldSettersHolder class looks these up.
#
# We use -keepclassmembernames because we want for ImmutableMultimap and its
# fields to be stripped if unused: -keepclassmembernames says that, *if* you're
# keeping the fields, you need to leave their names untouched. (Anyone who is
# using ImmutableMultimap will certainly be using its fields. So we don't need
# to worry that an ImmutableMultimap user will have the fields optimized away.)
#
# This configuration is untested....
#
-keepclassmembernames class com.google.common.collect.ImmutableMultimap {
  *** map;
  *** size;
}
# similarly:
-keepclassmembernames class com.google.common.collect.ConcurrentHashMultiset {
  *** countMap;
}
# similarly:
-keepclassmembernames class com.google.common.collect.ImmutableSetMultimap {
  *** emptySet;
}
# similarly:
-keepclassmembernames class com.google.common.collect.AbstractSortedMultiset {
  *** comparator;
}
# similarly:
-keepclassmembernames class com.google.common.collect.TreeMultiset {
  *** range;
  *** rootReference;
  *** header;
}

# Copy from https://github.com/google/guava/blob/master/proguard/concurrent.pro
# Futures.getChecked, in both of its variants, is incompatible with proguard.

# Used by AtomicReferenceFieldUpdater and sun.misc.Unsafe
-keepclassmembers class com.google.common.util.concurrent.AbstractFuture** {
  *** waiters;
  *** value;
  *** listeners;
  *** thread;
  *** next;
}
-keepclassmembers class com.google.common.util.concurrent.AtomicDouble {
  *** value;
}
-keepclassmembers class com.google.common.util.concurrent.AggregateFutureState {
  *** remaining;
  *** seen;
}

# Since Unsafe is using the field offsets of these inner classes, we don't want
# to have class merging or similar tricks applied to these classes and their
# fields. It's safe to allow obfuscation, since the by-name references are
# already preserved in the -keep statement above.
-keep,allowshrinking,allowobfuscation class com.google.common.util.concurrent.AbstractFuture** {
  <fields>;
}

# AbstractFuture uses this
-dontwarn sun.misc.Unsafe

# MoreExecutors references AppEngine
-dontnote com.google.appengine.api.ThreadManager
-keep class com.google.appengine.api.ThreadManager {
  static *** currentRequestThreadFactory(...);
}
-dontnote com.google.apphosting.api.ApiProxy
-keep class com.google.apphosting.api.ApiProxy {
  static *** currentRequestEnvironment (...);
}

-dontwarn java.lang.SafeVarargs

# Copy from https://github.com/google/guava/blob/master/proguard/hash.pro
# LittleEndianByteArray uses this
-dontwarn sun.misc.Unsafe

# Striped64 appears to make some assumptions about object layout that
# really might not be safe. This should be investigated.
-keepclassmembers class com.google.common.hash.Striped64 {
  *** base;
  *** busy;
}
-keepclassmembers class com.google.common.hash.Striped64$Cell {
  <fields>;
}

# Copy from https://github.com/google/guava/blob/master/proguard/io.pro
-keep class java.lang.Throwable {
  *** addSuppressed(...);
}

# Copy from https://github.com/google/guava/blob/master/proguard/primitives.pro
# UnsignedBytes uses this
-dontwarn sun.misc.Unsafe

# Fix: https://github.com/google/guava/issues/3227
-keepnames class com.google.common.** { *; }
# >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>Guava<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

# >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>TopOn<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
# Vungle
-dontwarn com.vungle.ads.**
-keepclassmembers class com.vungle.ads.** {
  *;
}
-keep class com.vungle.ads.**



# Google
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**




# START OkHttp + Okio
# JSR-305 annotations are for embedding nullability information.
-dontwarn javax.annotation.**


# A resource is loaded with a relative path so the package of this class must be preserved.
-adaptresourcefilenames okhttp3/internal/publicsuffix/PublicSuffixDatabase.gz


# Animal Sniffer compileOnly dependency to ensure APIs are compatible with older versions of Java.
-dontwarn org.codehaus.mojo.animal_sniffer.*


# OkHttp platform used only on JVM and when Conscrypt and other security providers are available.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**


# Animal Sniffer compileOnly dependency to ensure APIs are compatible with older versions of Java.
-dontwarn org.codehaus.mojo.animal_sniffer.*


# END OkHttp + Okio


# START Protobuf
-dontwarn com.google.protobuf.**
-keepclassmembers class com.google.protobuf.** {
 *;
}
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }


# END Protobuf
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.mbridge.** {*; }
-keep interface com.mbridge.** {*; }
-keep class android.support.v4.** { *; }
-dontwarn com.mbridge.**
-keep class **.R$* { public static final int mbridge*; }
-keep public class com.mbridge.* extends androidx.** { *; }
-keep public class androidx.viewpager.widget.PagerAdapter{ *; }
-keep public class androidx.viewpager.widget.ViewPager.OnPageChangeListener{ *; }
-keep interface androidx.annotation.IntDef{ *; }
-keep interface androidx.annotation.Nullable{ *; }
-keep interface androidx.annotation.CheckResult{ *; }
-keep interface androidx.annotation.NonNull{ *; }
-keep public class androidx.fragment.app.Fragment{ *; }
-keep public class androidx.core.content.FileProvider{ *; }
-keep public class androidx.core.app.NotificationCompat{ *; }
-keep public class androidx.appcompat.widget.AppCompatImageView { *; }
-keep public class androidx.recyclerview.*{ *; }
-keep class com.bytedance.sdk.** { *; }
-keep class com.applovin.mediation.bigo.adapters.BigoAdsNewMediationAdapter { *; }
# >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>TopOn<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
# Room ProGuard rules
-keep class androidx.room.** { *; }
-keep interface androidx.room.** { *; }
-dontwarn androidx.room.**



-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
#noinspection ShrinkerUnresolvedReference
#unity
-keep class com.google.android.gms.ads.** {public *;}
-keep class com.google.android.gms.appset.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
#For AmazonAps integration
-keep class com.amazon.device.ads.DtbThreadService {
    static *;
}
-keep public interface com.amazon.device.ads** {*; }
#For AppLovin integration
-keepclassmembers class com.applovin.sdk.AppLovinSdk {
    static *;
}
-keep public interface com.applovin.sdk** {*; }
-keep public interface com.applovin.adview** {*; }
-keep public interface com.applovin.mediation** {*; }
-keep public interface com.applovin.communicator** {*; }
# MAX WebTrackerManager ANR workaround: keep the no-op stub and the base class it extends.
-keep class com.applovin.impl.MaxWebTrackerNoOp { *; }
-keep class com.applovin.impl.n8 { *; }
#For Bytedance integration
-keep public interface com.bytedance.sdk.openadsdk** {*; }
#For Facebook integration
-keepclassmembers class com.facebook.ads.internal.AdSdkVersion {
    static *;
}
-keepclassmembers class com.facebook.ads.internal.settings.AdSdkVersion {
    static *;
 }
-keepclassmembers class com.facebook.ads.BuildConfig {
    static *;
 }
-keep public interface com.facebook.ads** {*; }
#For Fairbid
-keep public interface com.fyber.fairbid.ads.interstitial** {*; }
-keep public interface com.fyber.fairbid.ads.rewarded** {*; }
-keep class com.fyber.offerwall.*
#For Fivead
-keep public interface com.five_corp.ad** {*; }
#For Fyber(Inneractive) integration
-keep public interface com.fyber.inneractive.sdk.external** {*; }
-keep public interface com.fyber.inneractive.sdk.activities** {*; }
-keep public interface com.fyber.inneractive.sdk.ui** {*; }
#For HyprMX integration
-keepclassmembers class com.hyprmx.android.sdk.utility.HyprMXProperties {
    static *;
}
-keepclassmembers class com.hyprmx.android.BuildConfig {
    static *;
}
-keep public interface com.hyprmx.android.sdk.activity** {*; }
-keep public interface com.hyprmx.android.sdk.graphics** {*; }
# For Inmobi integration
-keep class com.inmobi.*
-keep public interface com.inmobi.ads.listeners** {*; }
-keep public interface com.inmobi.ads.InMobiInterstitial** {*; }
-keep public interface com.inmobi.ads.InMobiBanner** {*; }
#For Maio integration
-keep public interface jp.maio.sdk.android.MaioAdsListenerInterface {*; }
# For Mintergral integration
-keep public interface com.mbridge.msdk.out** {*; }
-keep public interface com.mbridge.msdk.videocommon.listener** {*; }
-keep public interface com.mbridge.msdk.interstitialvideo.out** {*; }
-keep public interface com.mintegral.msdk.out** {*; }
-keep public interface com.mintegral.msdk.videocommon.listener** {*; }
-keep public interface com.mintegral.msdk.interstitialvideo.out** {*; }
#For MyTarget integration
-keep class com.my.target.** {*;}
#For Ogury integration
-keep public interface io.presage.interstitial** {*; }
-keep public interface io.presage.interstitial.PresageInterstitialCallback {*; }
#For Pubnative integration
-keep public interface net.pubnative.lite.sdk.interstitial.HyBidInterstitialAd** {*; }
-keep public interface net.pubnative.lite.sdk.rewarded.HyBidRewardedAd** {*; }
-keep public interface net.pubnative.lite.sdk.views.HyBidAdView** {*; }
#For Smaato integration
-keep public interface com.smaato.sdk.interstitial** {*; }
-keep public interface com.smaato.sdk.video.vast** {*; }
-keep public interface com.smaato.sdk.banner.widget** {*; }
-keep public interface com.smaato.sdk.core.util** {*; }
# For Tapjoy integration
-keep public interface com.tapjoy.** {*; }
# For Tencent integration
-keep public interface com.qq.e.ads.interstitial2** {*; }
-keep public interface com.qq.e.ads.interstitial3** {*; }
-keep public interface com.qq.e.ads.rewardvideo** {*; }
-keep public interface com.qq.e.ads.rewardvideo2** {*; }
-keep public interface com.qq.e.ads.banner2** {*; }
-keep public interface com.qq.e.comm.adevent** {*; }
#For Verizon integration
-keepclassmembers class com.verizon.ads.edition.BuildConfig {
    static *;
}
-keep public interface com.verizon.ads.interstitialplacement** {*; }
-keep public interface com.verizon.ads.inlineplacement** {*; }
-keep public interface com.verizon.ads.vastcontroller** {*; }
-keep public interface com.verizon.ads.webcontroller** {*; }
#For Vungle integration
-keep public interface com.vungle.warren.PlayAdCallback {*; }
-keep public interface com.vungle.warren.ui.contract** {*; }
-keep public interface com.vungle.warren.ui.view** {*; }
#For AndroidX
-keep class androidx.localbroadcastmanager.content.LocalBroadcastManager { *;}
-keep class androidx.recyclerview.widget.RecyclerView { *;}
-keep class androidx.recyclerview.widget.RecyclerView$OnScrollListener { *;}
#For Android
-keep class * extends android.app.Activity

# GDPR consent form
-keep class com.poseidon.ad.restrict.gdpr.PlatformConsentFormShowParameter { *; }

# Destroyable interface
-keep interface com.poseidon.ad.common.toolkit.Destroyable { *; }

# All processor parameter classes used by consumers
-keep class com.poseidon.ad.core.processor.** { *; }
-keep class com.poseidon.ad.main.processor.** { *; }
-keep class com.poseidon.ad.main.processor.PlatformHasCacheParameter {*;}
-keep class com.poseidon.ad.main.processor.PlatformInterstitialAdLoadParameter {*;}
-keep class com.poseidon.ad.main.processor.PlatformInterstitialAdShowParameter {*;}
-keep class com.poseidon.ad.main.processor.PlatformRewardedAdLoadParameter {*;}
-keep class com.poseidon.ad.main.processor.PlatformRewardedAdShowParameter {*;}

# API classes
-keep class com.poseidon.ad.api.** { *; }

# Native ad view
-keep class com.poseidon.ad.main.adinfo.custom.NativeTemplateView { *; }

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
