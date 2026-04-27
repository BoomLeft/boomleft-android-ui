// Android library module for boomleft-android-ui.
//
// Unlike the sibling `privacysuite-ffi` AAR, this module is pure Kotlin —
// no Rust cross-compile, no UniFFI binding generation, no jniLibs. It
// ships Kotlin sources (compiled to classes.jar inside the AAR) and
// nothing else. The output AAR is
// `ui/build/outputs/aar/ui-release.aar`, which `build-aar.sh` copies to
// `build/boomleft-android-ui-<version>.aar`.
//
// Library version lives here; `build-aar.sh` greps the VERSION constant
// below to tag the output filename.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// VERSION marker — the reproducibility script (build-aar.sh) greps this
// literal line to derive the AAR filename. Keep the format
// `val VERSION = "x.y.z"` — single quotes/double-quotes, one-liner.
val VERSION = "0.1.0"

android {
    namespace = "com.boomleft.androidui"
    compileSdk = 34

    defaultConfig {
        minSdk = 29          // GrapheneOS baseline (Android 10+); matches
                             // privacysuite-ffi's AAR and consumer apps.
        @Suppress("DEPRECATION")
        targetSdk = 34

        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
    }

    // Surface the VERSION constant as a BuildConfig field so downstream
    // consumers can sanity-check they linked the expected AAR version
    // (mirrors `BuildInfo.VERSION` exposed in Kotlin source).
    buildFeatures {
        buildConfig = true
    }

    defaultConfig.buildConfigField("String", "LIBRARY_VERSION", "\"$VERSION\"")
}

dependencies {
    // AndroidX Lifecycle provides `DefaultLifecycleObserver`, which the
    // `PrivacyScreenOverlay` uses to hook into ON_PAUSE / ON_RESUME.
    // Declared `compileOnly` so each consumer app supplies its own
    // Lifecycle artifact version — every BoomLeft Kotlin app already
    // pulls `androidx.lifecycle:lifecycle-runtime-*` transitively via
    // AppCompat / Activity / Compose, so there's no shared-artifact
    // conflict risk.
    compileOnly("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    compileOnly("androidx.lifecycle:lifecycle-common:2.10.0")

    // AppCompat is only needed at compile time so we can reference
    // Activity without forcing a specific version on consumers.
    compileOnly("androidx.appcompat:appcompat:1.7.1")

    // Test scope — unit tests for pure-Kotlin logic.
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    testImplementation("androidx.lifecycle:lifecycle-common:2.10.0")
}
