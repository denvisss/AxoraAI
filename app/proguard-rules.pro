# Gson DTOs: field names are used for (de)serialization, must not be renamed.
-keep class com.axora.app.data.network.** { *; }
-keepclassmembers class com.axora.app.data.network.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes Exceptions

# Gson
-keep class com.google.gson.** { *; }
-dontwarn sun.misc.**

# Retrofit / OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Tink (androidx.security:security-crypto)
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**