# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keep class com.imageai.app.data.network.model.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keep class com.imageai.app.data.network.model.** { *; }

# Coroutines
-keepclassmembers,allow Shrinking,allow Optimizing class * {
    @kotlinx.coroutines.CoroutinesScope kotlinx.coroutines.CoroutinesScope;
}
