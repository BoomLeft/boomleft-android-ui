/*
 * Copyright (c) 2026 BoomLeft LLC. All rights reserved.
 *
 * This file is part of boomleft-android-ui. See LICENSE for terms.
 */
package com.boomleft.androidui

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Lifecycle-aware privacy screen for sensitive Activities.
 *
 * Two layered defenses are applied together:
 *
 * 1. **`FLAG_SECURE`** is set on the Activity window whenever the
 *    overlay is [enabled]. This blocks:
 *    - screenshots (both MediaProjection APIs and hardware key capture),
 *    - screen recording,
 *    - and content rendering into the recent-apps switcher thumbnail
 *      and on non-secure external displays.
 *    On GrapheneOS this is the canonical mechanism for preventing
 *    casual OS-level capture of sensitive UI. The flag is reapplied
 *    on every `ON_RESUME` so that buggy or hostile host code that
 *    clears it cannot leave the window unprotected.
 *
 * 2. **Opaque overlay on pause.** Even with `FLAG_SECURE`, some device
 *    OEMs have historically shown a blurred / cached frame in the
 *    app-switcher animation during the `ON_PAUSE → ON_STOP` transition.
 *    To defeat that, an opaque `View` is attached to the Activity's
 *    content root when the Activity's lifecycle transitions to `ON_PAUSE`
 *    and removed on `ON_RESUME`. The overlay uses the same `FrameLayout`
 *    root the Activity already owns, so no extra windows or permissions
 *    are required.
 *
 * ## Usage
 *
 * ```kotlin
 * class MyActivity : ComponentActivity() {
 *     private lateinit var privacyScreen: PrivacyScreenOverlay
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         privacyScreen = PrivacyScreenOverlay(this)
 *         lifecycle.addObserver(privacyScreen)
 *
 *         setContentView(R.layout.activity_main)
 *     }
 * }
 * ```
 *
 * To disable at runtime (for example, in a debug build or when the user
 * opts out), call [setEnabled] with `false`. The observer stays attached
 * but becomes a no-op; calling `setEnabled(true)` re-arms it and, if the
 * Activity is currently paused, immediately re-attaches the overlay so
 * there is no protection gap during the disable→enable→pause window.
 *
 * ## Thread safety
 *
 * All public methods must be called on the main thread. The class holds
 * a strong reference to [activity] — do not retain a reference to this
 * observer past the Activity's `onDestroy`. Registering it via
 * `lifecycle.addObserver(...)` as shown above handles that automatically,
 * because the observer is garbage-collected with the Lifecycle.
 *
 * ## Design notes vs. the Scratchpad original
 *
 * Scratchpad inlined this pattern directly into `MainActivity.onCreate`,
 * using a Compose `DisposableEffect` + `LifecycleEventObserver` and a
 * Compose `Box` as the overlay. That implementation is Compose-bound,
 * which is fine for Scratchpad but not portable to View-based Kotlin
 * apps (Voice, Scanner) that also want the primitive. This class uses
 * plain `View`s and `DefaultLifecycleObserver` so any Activity —
 * Compose, Views, or a mix — can adopt it identically.
 *
 * @param activity the Activity whose window gets `FLAG_SECURE` and whose
 *                 content root receives the pause overlay. Must not be
 *                 finishing.
 * @param overlayColor the solid color painted over the content during
 *                     the paused state. Defaults to opaque black to
 *                     maximize contrast with any cached frame. **Must be
 *                     fully opaque** (alpha == 0xFF); a translucent color
 *                     would silently leak the underlying UI through the
 *                     overlay and is rejected with `IllegalArgumentException`.
 */
public class PrivacyScreenOverlay
@JvmOverloads
constructor(
    private val activity: Activity,
    private val overlayColor: Int = Color.BLACK,
) : DefaultLifecycleObserver {

    init {
        // Reject translucent overlay colors at construction time. A
        // partially-transparent overlay is a security defect, not a
        // styling choice — refuse rather than silently degrade the
        // privacy guarantee.
        requireOpaqueOverlayColor(overlayColor)
        // Apply FLAG_SECURE up front. Consumer apps typically add this
        // observer in `onCreate`, which means the flag takes effect
        // before the first frame is ever rendered.
        applySecureFlag(secure = true)
    }

    /** The overlay view attached to the content root while paused, or null. */
    private var overlayView: View? = null

    /** Tracks the most recent lifecycle pause/resume edge (main-thread only). */
    private var paused: Boolean = false

    /** Runtime on/off switch (main-thread only). See [setEnabled]. */
    private var enabled: Boolean = true

    /**
     * Enable or disable the privacy screen at runtime.
     *
     * When disabled:
     * - `FLAG_SECURE` is cleared, permitting screenshots again.
     * - Any currently attached overlay is removed immediately.
     * - Subsequent lifecycle events (`ON_PAUSE` / `ON_RESUME`) are ignored.
     *
     * When re-enabled:
     * - `FLAG_SECURE` is re-applied.
     * - If the Activity is currently paused, the overlay is attached
     *   immediately so there is no exposure window.
     *
     * @param value `true` to arm both defenses; `false` to disable them.
     */
    public fun setEnabled(value: Boolean) {
        enabled = value
        if (value) {
            applySecureFlag(secure = true)
            if (paused) attachOverlay()
        } else {
            applySecureFlag(secure = false)
            detachOverlay()
        }
    }

    /** @return `true` if the privacy screen is currently armed. */
    public fun isEnabled(): Boolean = enabled

    override fun onResume(owner: LifecycleOwner) {
        paused = false
        if (!enabled) return
        // Defense-in-depth: reapply FLAG_SECURE in case host code (or a
        // misbehaving library) cleared it while we were paused.
        applySecureFlag(secure = true)
        detachOverlay()
    }

    override fun onPause(owner: LifecycleOwner) {
        paused = true
        if (!enabled) return
        attachOverlay()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        detachOverlay()
    }

    // ----- internals ---------------------------------------------------

    /**
     * Toggle `WindowManager.LayoutParams.FLAG_SECURE` on the Activity
     * window. A no-op if the Activity is finishing or has no window.
     */
    private fun applySecureFlag(secure: Boolean) {
        if (activity.isFinishing) return
        val window = activity.window ?: return
        if (secure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    /**
     * Install an opaque overlay covering the Activity's content. Uses
     * the Activity's decor content root (`android.R.id.content`) so the
     * overlay sits above any Compose / View content the Activity has set
     * but below system bars — we want the user's own UI hidden, not the
     * status bar.
     */
    private fun attachOverlay() {
        if (overlayView != null) return
        if (activity.isFinishing) return

        val contentRoot = activity
            .findViewById<ViewGroup>(android.R.id.content)
            ?: return

        val view = View(activity).apply {
            setBackgroundColor(overlayColor)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            // Swallow input so nothing beneath reacts while paused.
            isClickable = true
            isFocusable = true
            // Hide both the overlay and everything beneath it from
            // accessibility services. A paused Activity should not be
            // explorable through TalkBack while the privacy screen is up.
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            contentDescription = null
        }

        contentRoot.addView(view)
        overlayView = view
    }

    /**
     * Remove the overlay from its parent if present.
     */
    private fun detachOverlay() {
        val view = overlayView ?: return
        (view.parent as? ViewGroup)?.removeView(view)
        overlayView = null
    }

    // NOTE: FLAG_SECURE behavior + real overlay attach/detach can only
    // be verified on an Android runtime (emulator or device). See the
    // unit tests for what is validated headlessly. A full verification
    // belongs in an instrumented test (androidTest) — deliberately not
    // added in this initial Phase 2 port; will land alongside the first
    // consumer-app migration PR that adds an emulator to CI.
}

/**
 * Reject translucent ARGB ints. Top-level + `internal` so the policy
 * is unit-testable on the host JVM without standing up an Activity
 * (the rest of [PrivacyScreenOverlay] requires the Android runtime).
 *
 * @throws IllegalArgumentException if [color]'s alpha channel is < 0xFF.
 */
internal fun requireOpaqueOverlayColor(color: Int) {
    val alpha = (color ushr 24) and 0xFF
    require(alpha == 0xFF) {
        "overlayColor must be fully opaque (alpha == 0xFF); got alpha=$alpha"
    }
}
