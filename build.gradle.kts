// Root Gradle build for the boomleft-android-ui AAR project.
//
// Plugin versions are pinned to match the PrivacySuite-Core-SDK
// `privacysuite-ffi` AAR (AGP 8.7.3, Kotlin 2.1.0) so that consumer
// apps (boomleft-voice, boomleft-scratchpad, boomleft-scanner) link
// this library alongside `privacysuite-ffi-<version>.aar` without
// plugin-version drift.
plugins {
    id("com.android.library") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
}
