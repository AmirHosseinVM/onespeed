# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# ---- Xray / libv2ray native (Go) bridge -----------------------------------
# These are called across the JNI boundary by the Go runtime; if R8 renames
# or strips them the app crashes as soon as the VPN service tries to start.
-keep class go.** { *; }
-keep class libv2ray.** { *; }
-keepclassmembers class * {
    native <methods>;
}

# ---- Gson-serialized model classes -----------------------------------------
# ProfileItem / SubscriptionItem / etc. are (de)serialized via Gson reflection
# on field names — if fields get renamed, previously-saved profiles silently
# fail to decode ("no profile stored for guid=...", "invalid config").
-keep class com.v2ray.ang.dto.** { *; }
-keep class ir.onespeed.app.data.** { *; }
-keepclassmembers class com.v2ray.ang.dto.** { *; }
-keepclassmembers class ir.onespeed.app.data.** { *; }

-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken

# ---- MMKV (native storage) -------------------------------------------------
-keep class com.tencent.mmkv.** { *; }

# ---- App core / service classes referenced from the manifest / other processes ----
-keep class com.v2ray.ang.** { *; }
-keep class ir.onespeed.app.** { *; }

# ---- okhttp / okio (reflection-based edge cases) ---------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ---- Kotlin coroutines / WorkManager reflection ----------------------------
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
-keep class androidx.work.** { *; }

# Keep the line number information for readable crash reports; hide the
# original file name in the (unlikely) case a stack trace leaks externally.
-renamesourcefileattribute SourceFile
