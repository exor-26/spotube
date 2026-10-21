#!/usr/bin/env bash
set -euo pipefail

# Called once by android-emulator-runner. Keep shell state across all steps.
PACKAGE_ID="oss.krtirtho.spotube.dev"
LAUNCH_ACTIVITY="oss.krtirtho.spotube.MainActivity"
APK="build/app/outputs/flutter-apk/app-dev-debug.apk"

if [[ ! -s "$APK" ]]; then
  echo "Missing signed APK: $APK"
  exit 1
fi

# Keep a persistent crash log in the Actions artifact if launch fails.
trap 'adb logcat -d -b crash > android-startup-crash.log || true' EXIT

adb install -r "$APK"
adb logcat -c
adb shell am start -W -n "$PACKAGE_ID/$LAUNCH_ACTIVITY"

# Check startup twice to avoid racing initial plugin registration.
sleep 12
adb shell pidof "$PACKAGE_ID" || true
sleep 15

if ! adb shell pidof "$PACKAGE_ID"; then
  echo "Spotube exited during startup; Android crash log follows."
  adb logcat -d -b crash | tail -n 180
  exit 1
fi

if adb logcat -d -b crash | grep -A 12 -F "Process: $PACKAGE_ID"; then
  echo "Detected a fatal crash in the Spotube process."
  exit 1
fi

echo "Spotube survived Android 16 startup with its original onboarding."
adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity|$PACKAGE_ID" | head -n 30 || true
