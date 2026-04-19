/*
 * Copyright (c) 2026 BoomLeft LLC. All rights reserved.
 *
 * This file is part of boomleft-android-ui. See LICENSE for terms.
 */
package com.boomleft.androidui

/**
 * Compile-time metadata about the linked `boomleft-android-ui` AAR.
 *
 * Consumer apps can reference [VERSION] at runtime to verify they are
 * linked against the expected library version — useful when AARs are
 * dropped into `app/libs/` by filename and version mismatches would
 * otherwise be silent.
 *
 * Keep this constant synchronized with the `VERSION` variable in
 * `ui/build.gradle.kts`. The reproducibility script (`build-aar.sh`)
 * reads the Gradle `VERSION` to tag the output AAR filename; this
 * Kotlin-side constant exists for runtime introspection and should
 * match byte-for-byte.
 */
public object BuildInfo {
    /** Library version (SemVer). Must match `ui/build.gradle.kts` `VERSION`. */
    public const val VERSION: String = "0.1.0"
}
