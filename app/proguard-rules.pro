# Keep Media3 ExoPlayer DRM + DASH + HLS reflective lookups.
-keep class androidx.media3.exoplayer.dash.** { *; }
-keep class androidx.media3.exoplayer.hls.** { *; }
-keep class androidx.media3.exoplayer.drm.** { *; }

# Media3 module manifest reflection.
-keep class androidx.media3.exoplayer.ExoPlayer$Builder { *; }

# OkHttp is referenced reflectively by media3-datasource-okhttp.
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin metadata.
-keep class kotlin.Metadata { *; }

# ML Kit Translate uses reflection internally.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_translate.** { *; }
-dontwarn com.google.mlkit.**
