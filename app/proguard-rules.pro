# Keep NotificationListenerService (system-bound service)
-keep class com.profitdriving.NotificationListener { *; }

# Keep FloatingCardService (started via Intent)
-keep class com.profitdriving.FloatingCardService { *; }

# Keep FloatingBubbleService (started via Intent)
-keep class com.profitdriving.FloatingBubbleService { *; }

# Keep RideAccessibilityService (system-bound service)
-keep class com.profitdriving.RideAccessibilityService { *; }

# Keep RideData serialization fields
-keepclassmembers class com.profitdriving.RideData {
    *;
}

# ML Kit Text Recognition
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_latin.** { *; }
