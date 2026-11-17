# ==============================================================================
# LOCKIN R8 ProGuard Configuration
# ==============================================================================

# Keep standard Android framework components declared in AndroidManifest.xml
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep Accessibility Service component and lifecycle
-keep public class com.lockin.app.LockAccessibilityService { *; }

# Keep Activity entry points
-keep public class com.lockin.app.MainActivity { *; }
-keep public class com.lockin.app.BlockActivity { *; }

# Keep internal logic objects
-keep class com.lockin.app.LockPrefs { *; }
-keep class com.lockin.app.UsageCalculator { *; }

# Strip verbose logging in release builds for privacy and anti-reverse engineering
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
