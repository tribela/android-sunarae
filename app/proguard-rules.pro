# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the default proguard-android-optimize.txt.
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-keep class net.kjwon15.noshiftkeyboard.R
-keep class net.kjwon15.noshiftkeyboard.latin.settings.SettingsFragment
-keep class net.kjwon15.noshiftkeyboard.latin.settings.LanguagesSettingsFragment
-keep class net.kjwon15.noshiftkeyboard.latin.settings.SingleLanguageSettingsFragment

# Hangul composition must not be optimized away — R8 fullMode can inline/merge the
# combiner state machine. Keep it intact; release-only jamo separation was traced to
# aggressive optimization here (B-plan fallback if scoring fails).
-keep class net.kjwon15.noshiftkeyboard.hangul.** { *; }
-keep class net.kjwon15.noshiftkeyboard.latin.inputlogic.HangulCompositionSession { *; }

# Strip verbose/diagnostic Log calls from release builds only (R8 optimization).
# Debug builds keep them for on-device diagnosis. Log.e (errors) is deliberately
# left intact so real failures still reach logcat.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
