# boomleft-android-ui

Android-only Kotlin library (distributed as an AAR) that consolidates the
UI-layer privacy primitives shared across the BoomLeft Android apps
(Voice, Scratchpad, Scanner, and any future Kotlin-ish BoomLeft app).

This is a sibling to
[`PrivacySuite-Core-SDK`](https://github.com/mkfnch/PrivacySuite-Core-SDK);
the core SDK supplies the cross-platform Rust crypto + networking primitives
(AEAD, Argon2id, DoH, OHTTP, Tor), while `boomleft-android-ui` supplies the
Android view-layer privacy helpers that don't belong in a cross-platform
Rust crate.

## What's in the box

| Primitive              | Status                    | Notes                                                      |
| ---------------------- | ------------------------- | ---------------------------------------------------------- |
| `PrivacyScreenOverlay` | Shipping (v0.1.0)         | Lifecycle-aware `FLAG_SECURE` + opaque overlay on pause    |
| `SecureClipboard`      | Planned (Phase 3)         | Auto-clear timer + Android 13+ `EXTRA_IS_SENSITIVE`        |
| `SafeNotification`     | Planned (Phase 3)         | Auto-redact sensitive notifications on the lockscreen      |
| Canonical `PrivacySuiteBridge.kt` | Planned (Phase 3) | Handle-based UniFFI wrapper                                |
| `SecureWebViewClient`  | Planned (Phase 4)         | Hardened WebView bits extracted from Blackout              |

## Requirements

- **minSdk 29** (Android 10+ — GrapheneOS baseline, matches the family)
- **compileSdk 34**
- **Kotlin 2.1.0**, AGP 8.7.3, Gradle 8.11.1
- AndroidX Lifecycle (consumer-supplied; declared `compileOnly`)

## Consuming the AAR

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(name = "boomleft-android-ui-0.1.0", ext = "aar")
    // Required transitive (this AAR declares Lifecycle as compileOnly):
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
}

repositories {
    flatDir { dirs("libs") }
}
```

Then drop `boomleft-android-ui-0.1.0.aar` into `app/libs/`.

### `PrivacyScreenOverlay` usage

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(PrivacyScreenOverlay(this))
        setContentView(R.layout.activity_main)
    }
}
```

`FLAG_SECURE` is applied to the window immediately, and an opaque overlay
is drawn whenever the Activity is backgrounded. See the KDoc on
`PrivacyScreenOverlay` for the full API surface.

## Building from source

```bash
# Produces build/boomleft-android-ui-<version>.aar and prints its SHA-256.
./build-aar.sh
```

Environment expectations:

- `JAVA_HOME` pointing at JDK 17
- `ANDROID_HOME` pointing at an SDK with platform-34 + build-tools 34.0.0
- Source `~/.boomleft-env` on a BoomLeft dev machine to pick these up.

## Versioning

SemVer. Tagged as `v<version>` on `main`. The tag is the source of truth;
consumer apps pin by dropping a specific `boomleft-android-ui-<version>.aar`
into their `app/libs/` directory.

## License

Proprietary — BoomLeft LLC. See [LICENSE](./LICENSE).

For licensing inquiries: mike@mkfnch.com
