# Jumlo — R8 / ProGuard Configuration
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod,RuntimeVisibleAnnotations

# Keep Room Entities & DAOs
-keep class com.jumlo.app.data.local.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# Keep Kotlinx Serialization models, settings, and type-safe Navigation routes
-keep @kotlinx.serialization.Serializable class com.jumlo.app.** { *; }
-keep,includedescriptorclasses class com.jumlo.app.**$$serializer { *; }
-keepclassmembers class com.jumlo.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class com.jumlo.app.**$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Hilt workers instantiated via HiltWorkerFactory reflection
-keep @androidx.hilt.work.HiltWorker class com.jumlo.app.** { *; }
-keep class com.jumlo.app.worker.ActiveTaskTracker { *; }

# PDFBox-Android (Apache-2.0)
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**

# zip4j (Apache-2.0)
-keep class net.lingala.zip4j.** { *; }
-dontwarn net.lingala.zip4j.**

# ML Kit Text Recognition
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# OkHttp & TLS
-dontwarn okhttp3.**
-dontwarn okio.**

# youtubedl-android, FFmpeg & youtubedl_common
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**

# Preserve raw resource IDs for youtubedl-android
-keep class **.R$raw { *; }

# Jackson Databind (used by youtubedl-android)
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**

# Apache Commons (used by youtubedl-android)
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**
-keep class org.apache.commons.io.** { *; }
-dontwarn org.apache.commons.io.**

# Strip verbose/debug/info logs in release builds; keep Log.w/e/wtf intact
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Tesseract4Android / Leptonica JNI
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
-dontwarn com.googlecode.tesseract.android.**
-dontwarn com.googlecode.leptonica.android.**
