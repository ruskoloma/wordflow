# WordFlow ProGuard Rules
-keepattributes *Annotation*
-keep class com.rsln.wordflow.data.local.entity.** { *; }
-keep class com.rsln.wordflow.data.remote.** { *; }

# Gson
-keepattributes Signature
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Tink references compile-time Error Prone annotations that are not needed at runtime.
-dontwarn com.google.errorprone.annotations.**
