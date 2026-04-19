#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# build-aar.sh — reproducibly produce the boomleft-android-ui AAR.
#
# Pipeline:
#   1. Resolve the library version from ui/build.gradle.kts (the `val
#      VERSION = "x.y.z"` line).
#   2. Invoke `:ui:assembleRelease` via the Gradle wrapper.
#   3. Copy the resulting ui-release.aar to
#      build/boomleft-android-ui-<version>.aar.
#   4. Print the SHA-256 so consumer-app drops can verify integrity.
#
# Environment expectations (see the SDK README / ~/.boomleft-env):
#   JAVA_HOME → JDK 17 (Android Gradle Plugin 8.x requirement)
#   ANDROID_HOME → the Android SDK with platform 34 + build-tools 34.0.0
#
# Output:
#   ui/build/outputs/aar/ui-release.aar
#   copied to
#   build/boomleft-android-ui-<version>.aar
#
# Do NOT commit the produced AAR — consumers pull it into their
# `app/libs/` the same way they pull `privacysuite-ffi-<version>.aar`.
# -----------------------------------------------------------------------------

set -euo pipefail

# --- resolve locations --------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GRADLE_ROOT="${SCRIPT_DIR}"
UI_GRADLE_FILE="${GRADLE_ROOT}/ui/build.gradle.kts"

if [[ ! -f "${UI_GRADLE_FILE}" ]]; then
    echo "ERROR: expected ${UI_GRADLE_FILE} to exist." >&2
    exit 1
fi

# Extract the `val VERSION = "x.y.z"` line. We insist on the exact
# literal form so accidental reformatting doesn't silently change the
# AAR filename.
LIB_VERSION="$(
    awk -F'"' '/^val[[:space:]]+VERSION[[:space:]]*=[[:space:]]*"/ { print $2; exit }' \
        "${UI_GRADLE_FILE}"
)"
if [[ -z "${LIB_VERSION}" ]]; then
    echo "ERROR: could not detect library VERSION from ${UI_GRADLE_FILE}" >&2
    echo "       expected a line matching: val VERSION = \"x.y.z\"" >&2
    exit 1
fi

echo "================================================================="
echo " boomleft-android-ui AAR build"
echo " Gradle root:    ${GRADLE_ROOT}"
echo " Output version: ${LIB_VERSION}"
echo "================================================================="

# --- sanity checks ------------------------------------------------------------
if [[ -z "${JAVA_HOME:-}" ]] || [[ ! -d "${JAVA_HOME}" ]]; then
    echo "WARNING: JAVA_HOME is not set. Gradle will try to auto-detect JDK." >&2
fi

if [[ -z "${ANDROID_HOME:-}" ]] && [[ -z "${ANDROID_SDK_ROOT:-}" ]]; then
    echo "ERROR: neither ANDROID_HOME nor ANDROID_SDK_ROOT is set." >&2
    echo "       Source ~/.boomleft-env or export ANDROID_HOME manually." >&2
    exit 1
fi

# --- 1. assemble release AAR via Gradle wrapper -------------------------------
echo ""
echo "[1/2] Assembling release AAR via Gradle..."
cd "${GRADLE_ROOT}"

if [[ -x "./gradlew" ]]; then
    GRADLE_CMD="./gradlew"
else
    echo "ERROR: Gradle wrapper ./gradlew is missing or not executable." >&2
    exit 1
fi

"${GRADLE_CMD}" :ui:assembleRelease

# --- 2. locate + rename the output --------------------------------------------
echo ""
echo "[2/2] Collecting output AAR..."
RAW_AAR="${GRADLE_ROOT}/ui/build/outputs/aar/ui-release.aar"
if [[ ! -f "${RAW_AAR}" ]]; then
    echo "ERROR: Gradle finished but ${RAW_AAR} was not produced." >&2
    exit 1
fi

DIST_DIR="${GRADLE_ROOT}/build"
mkdir -p "${DIST_DIR}"
VERSIONED_AAR="${DIST_DIR}/boomleft-android-ui-${LIB_VERSION}.aar"
cp "${RAW_AAR}" "${VERSIONED_AAR}"

echo ""
echo "================================================================="
echo " Build complete."
echo ""
echo "   Raw AAR      : ${RAW_AAR}"
echo "   Versioned AAR: ${VERSIONED_AAR}"
echo ""
echo " Size:"
ls -l "${VERSIONED_AAR}" | awk '{print "   " $5 " bytes"}'
echo ""
echo " SHA-256:"
sha256sum "${VERSIONED_AAR}" || true
echo ""
echo " To consume from a BoomLeft Kotlin app (Voice, Scratchpad, etc.),"
echo " copy the versioned AAR into the app's app/libs/ directory and"
echo " declare the coordinate in app/build.gradle.kts, e.g.:"
echo ""
echo "   implementation(name = \"boomleft-android-ui-${LIB_VERSION}\", ext = \"aar\")"
echo ""
echo "================================================================="
