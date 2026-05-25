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
