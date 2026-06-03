# Retrofit
-keepattributes Signature
-keepattributes *Annotation*

# Kotlin Serialization
-keepclassmembers class com.example.aimobileagent.data.remote.dto.** {
    *** Companion;
}

# Room
-keep class * extends androidx.room.RoomDatabase
