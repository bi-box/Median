# System WebView reflects over these exact support-library protocol names.
-keep,allowoptimization interface org.chromium.support_lib_boundary.** { *; }
-keepnames interface org.chromium.support_lib_boundary.**

# WebView invokes methods annotated with JavascriptInterface reflectively.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keepattributes RuntimeVisibleAnnotations
-dontwarn org.chromium.**
-allowaccessmodification
-overloadaggressively
