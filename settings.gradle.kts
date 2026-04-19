// Android Gradle settings for the boomleft-android-ui AAR build.
//
// This library ships pure-Kotlin UI privacy primitives (FLAG_SECURE overlay,
// secure clipboard, safe notifications) for consumption by the Kotlin-native
// BoomLeft apps (Voice, Scratchpad, Scanner). It is the sibling to
// `PrivacySuite-Core-SDK`'s `privacysuite-ffi` AAR — same toolchain, same
// plugin pins, no Rust/UniFFI pipeline (this library is Kotlin-only).
//
// The AAR is built by `build-aar.sh` which invokes `:ui:assembleRelease`
// and copies the result to `build/boomleft-android-ui-<version>.aar`.
// Consumer apps drop that file into their own `app/libs/` directory.

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "boomleft-android-ui"
include(":ui")
