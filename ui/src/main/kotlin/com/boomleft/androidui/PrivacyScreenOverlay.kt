/*
 * Copyright (c) 2026 BoomLeft LLC. All rights reserved.
 *
 * This file is part of boomleft-android-ui. See LICENSE for terms.
 */
package com.boomleft.androidui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
 *    casual OS-level capture of sensitive UI.
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
 * but becomes a no-op; calling `setEnabled(true)` re-arms it.
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
 *                     maximize contrast with any cached frame.
 */
public class PrivacyScreenOverlay
@JvmOverloads
constructor(
    private val activity: Activity,
    private val overlayColor: Int = Color.BLACK,
) : DefaultLifecycleObserver {

    /**
     * The overlay view attached to the Activity's decor content root
     * while paused. Lazily created on first pause; detached on resume.
     * Null when no overlay is currently attached.
     */
    private var overlayView: View? = null

    /**
     * Runtime on/off switch. When `false`, the observer callbacks are
     * no-ops and `FLAG_SECURE` is cleared. See [setEnabled].
     */
    private var enabled: Boolean = true

    init {
        // Apply FLAG_SECURE up front. Consumer apps typically add this
        // observer in `onCreate`, which means the flag takes effect
        // before the first frame is ever rendered.
        applySecureFlag(secure = true)
    }

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
     *   on the next `ON_PAUSE` event (we do not retro-attach; the
     *   existing Activity state drives the next transition).
     *
     * @param value `true` to arm both defenses; `false` to disable them.
     */
    public fun setEnabled(value: Boolean) {
        enabled = value
        if (value) {
            applySecureFlag(secure = true)
        } else {
            applySecureFlag(secure = false)
            detachOverlay()
        }
    }

    /** @return `true` if the privacy screen is currently armed. */
    public fun isEnabled(): Boolean = enabled

    /**
     * Called by the AndroidX Lifecycle machinery when the Activity
     * transitions to `ON_PAUSE`. Attaches the opaque overlay.
     */
    override fun onPause(owner: LifecycleOwner) {
        if (!enabled) return
        attachOverlay()
    }

    /**
     * Called by the AndroidX Lifecycle machinery when the Activity
     * transitions to `ON_RESUME`. Removes the overlay if present.
     */
    override fun onResume(owner: LifecycleOwner) {
        if (!enabled) return
        detachOverlay()
    }

    /**
     * Called when the owning Lifecycle is destroyed. Guarantees no
     * dangling overlay view is left attached to a destroyed Activity's
     * view hierarchy.
     */
    override fun onDestroy(owner: LifecycleOwner) {
        detachOverlay()
    }

    // ----- internals ---------------------------------------------------

    /**
     * Toggle `WindowManager.LayoutParams.FLAG_SECURE` on the Activity
     * window. A no-op if the Activity is finishing.
     */
    private fun applySecureFlag(secure: Boolean) {
        if (activity.isFinishing) return
        val window = activity.window ?: return
        if (secure) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
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
            background = ColorDrawable(overlayColor)
            // FrameLayout params give us full-bleed coverage.
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            // Swallow touches so nothing beneath reacts while paused.
            isClickable = true
            isFocusable = true
        }

        contentRoot.addView(view)
        overlayView = view
    }

    /**
     * Remove the overlay from the Activity's content root if present.
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
