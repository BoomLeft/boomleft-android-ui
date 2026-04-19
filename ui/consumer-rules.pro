# Consumer ProGuard/R8 rules contributed by the boomleft-android-ui AAR.
#
# These rules are shipped inside the AAR and automatically applied to any
# consumer app that depends on this library. Consumer apps do NOT need to
# copy these into their own proguard-rules.pro.
#
# Two concerns:
#
# 1. Log stripping. The BoomLeft family enforces a "no logs in release
#    builds" discipline (see PrivacySuite-Core-SDK SECURITY.md). Strip all
#    android.util.Log calls so stack traces cannot leak through logcat if
#    a consumer app is ever minified without its own log rules.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static java.lang.String getStackTraceString(java.lang.Throwable);
}

# 2. Keep the library's public API surface so minification doesn't rename
#    classes or methods that consumers call through reflection-safe entry
#    points.
-keep public class com.boomleft.androidui.** { public *; }

# 3. Kotlin inline-function metadata. If we ever add `inline fun` to the
#    public API, consumers need Kotlin metadata preserved on the callsite.
-keepattributes KotlinMetadata, *Annotation*, InnerClasses, Signature, EnclosingMethod
