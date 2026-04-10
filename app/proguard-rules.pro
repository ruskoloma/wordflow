# WordFlow ProGuard Rules
-keepattributes *Annotation*
-keep class com.rsln.wordflow.data.local.entity.** { *; }
-keep class com.rsln.wordflow.data.remote.AiModels** { *; }

# Gson
-keepattributes Signature
-keep class com.google.gson.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
