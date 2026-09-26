# Project-specific ProGuard / R8 rules for release minify.

# Keep app models used via reflection / serialization-style prefs.
-keep class com.mousy.windfall.data.model.** { *; }

# Compose
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# Coil 3
-keep class coil3.** { *; }
-dontwarn coil3.**
-keep class okio.** { *; }

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Kotlin coroutines / serialization helpers
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler

# Keep Application entry used by baseline / startup
-keep class com.mousy.windfall.GalleryApplication { *; }
-keep class com.mousy.windfall.MainActivity { *; }
