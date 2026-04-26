# Consumer ProGuard/R8 rules contributed by the boomleft-android-ui AAR.
#
# These rules are shipped inside the AAR and automatically applied to any
# consumer app that depends on this library. Consumer apps do NOT need to
# copy these into their own proguard-rules.pro.
#
# Three concerns:
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

# 2. Keep ONLY the documented top-level public API. Using `**` would
#    keep every nested package's public symbols, including future
#    `internal`-by-convention subpackages we might add (e.g.,
#    `com.boomleft.androidui.detail.*`). The single-`*` form keeps
#    only types declared directly in the root package — exactly the
#    library's stable surface.
-keep public class com.boomleft.androidui.* { public *; }

# 3. Kotlin metadata for inline + reified callsites in consumer code.
#    KotlinMetadata is required if any public API ever becomes `inline
#    fun`; the rest preserve generic + annotation info that consumers
#    sometimes read reflectively (e.g., DI frameworks).
-keepattributes KotlinMetadata, *Annotation*, InnerClasses, Signature, EnclosingMethod
