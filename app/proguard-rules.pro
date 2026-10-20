# The accessibility and notification listener services are named in the manifest.
# Keep their metadata when R8 shrinks the release build.
-keep class com.saintm.smartlauncher.service.** extends android.accessibilityservice.AccessibilityService { *; }
-keep class com.saintm.smartlauncher.service.** extends android.service.notification.NotificationListenerService { *; }
