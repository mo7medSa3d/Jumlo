# Universal Utilities — R8 / ProGuard Configuration
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod

# Keep Room Entities & DAOs
-keep class com.universalutilities.app.data.local.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# Keep Kotlinx Serialization models
-keepclassmembers class com.universalutilities.app.model.** {
    *;
}

# OkHttp & TLS
-dontwarn okhttp3.**
-dontwarn okio.**

# AndroidX Biometric & KeyStore
-keep class androidx.biometric.** { *; }
