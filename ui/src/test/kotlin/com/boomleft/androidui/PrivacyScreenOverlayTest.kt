/*
 * Copyright (c) 2026 BoomLeft LLC. All rights reserved.
 *
 * This file is part of boomleft-android-ui. See LICENSE for terms.
 */
package com.boomleft.androidui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Headless JVM-side sanity checks for `boomleft-android-ui`.
 *
 * Most of [PrivacyScreenOverlay]'s behavior (FLAG_SECURE toggling,
 * View attach/detach, Activity lifecycle wiring) requires the Android
 * runtime and an actual Activity, so those are deferred to instrumented
 * (`androidTest`) coverage in a future phase. What we can verify on the
 * host JVM:
 *
 * - The library version advertised in Kotlin ([BuildInfo.VERSION]) is
 *   well-formed SemVer so downstream consumers that pin by version
 *   string don't silently link the wrong AAR.
 * - [PrivacyScreenOverlay]'s class loader can find `DefaultLifecycleObserver`
 *   so a packaging regression (missing compileOnly Lifecycle dep) is
 *   caught without needing to build against an Android device.
 */
class PrivacyScreenOverlayTest {

    @Test
    fun `BuildInfo VERSION is non-empty semver-shaped`() {
        val version = BuildInfo.VERSION
        assertNotNull(version)
        assertTrue(
            "version $version should match MAJOR.MINOR.PATCH",
            version.matches(Regex("^\\d+\\.\\d+\\.\\d+(?:-[A-Za-z0-9.-]+)?$")),
        )
    }

    @Test
    fun `BuildInfo VERSION matches expected initial release`() {
        // Sanity: Phase 2 of the migration ships v0.1.0. If this test
        // starts failing, the Gradle VERSION and the Kotlin constant
        // have drifted — check `ui/build.gradle.kts` vs BuildInfo.kt.
        assertEquals("0.1.0", BuildInfo.VERSION)
    }

    @Test
    fun `PrivacyScreenOverlay class is loadable and declares DefaultLifecycleObserver`() {
        // Reflectively confirm the port wires itself into the AndroidX
        // Lifecycle observer contract. If the compileOnly Lifecycle
        // dep drops out of build.gradle.kts, this will fail to resolve.
        val clazz = Class.forName("com.boomleft.androidui.PrivacyScreenOverlay")
        val observerIface = Class.forName("androidx.lifecycle.DefaultLifecycleObserver")
        assertTrue(
            "PrivacyScreenOverlay must implement DefaultLifecycleObserver",
            observerIface.isAssignableFrom(clazz),
        )
    }
}
