# Jumlo — R8 / ProGuard Configuration
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod,RuntimeVisibleAnnotations

# Keep Room Entities & DAOs
-keep class com.jumlo.app.data.local.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# Keep Kotlinx Serialization models, settings, and type-safe Navigation routes
-keepclassmembers class com.jumlo.app.model.** { *; }
-keepclassmembers class com.jumlo.app.data.processing.** { *; }
-keepclassmembers class com.jumlo.app.ui.navigation.** { *; }
-keep @kotlinx.serialization.Serializable class com.jumlo.app.** { *; }
-keepclassmembers class com.jumlo.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

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
