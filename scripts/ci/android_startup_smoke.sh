#!/usr/bin/env bash
# The emulator runner executes script lines independently. Keep this startup
# test in one executable script so shell variables and conditionals persist.
set -euo pipefail

PACKAGE="oss.krtirtho.spotube.dev"
ACTIVITY="oss.krtirtho.spotube.MainActivity"
APK="build/app/outputs/flutter-apk/app-dev-debug.apk"

test -s "$APK"
adb install -r "$APK"
adb logcat -c
adb shell am force-stop "$PACKAGE" || true
adb shell am start -W -n "$PACKAGE/$ACTIVITY"
sleep 25

if ! adb shell pidof "$PACKAGE"; then
  echo "::error::Spotube process exited immediately after launch"
  adb logcat -d -b crash | tail -n 200 || true
  exit 1
fi

if adb logcat -d -b crash | grep -F "Process: $PACKAGE"; then
  echo "::error::Fatal Spotube Android crash detected after launch"
  adb logcat -d -b crash | tail -n 200 || true
  exit 1
fi

echo "Spotube original startup process remains alive on Android 16"
adb shell dumpsys activity activities | grep -F "$ACTIVITY" | head -n 5 || true
