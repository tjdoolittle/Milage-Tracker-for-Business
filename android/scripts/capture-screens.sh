#!/usr/bin/env bash
# Install the debug build on a running emulator and capture store screenshots with sample data.
set -uo pipefail
PKG=com.tjdoolittle.mileagelog
APK=android/app/build/outputs/apk/debug/app-debug.apk
OUT=android/shots
mkdir -p "$OUT"
adb wait-for-device
# Let the emulator finish starting up, and keep system "isn't responding" popups out of the shots.
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done
adb shell settings put global hide_error_dialogs 1
adb shell settings put secure anr_show_background 0 2>/dev/null || true
sleep 45
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
adb install -r "$APK"

# Clean status bar: 9:41, full battery, full signal, no notifications.
adb shell settings put global sysui_demo_allowed 1
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
demo enter
demo clock -e hhmm 0941
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4
demo network -e mobile show -e datatype none -e level 4
demo notifications -e visible false

shot() {
  name="$1"; shift
  adb shell am force-stop "$PKG"
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
  adb shell am start -W -n "$PKG/.ui.MainActivity" --ez demo true "$@" >/dev/null
  sleep 6
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
  sleep 1
  adb exec-out screencap -p > "$OUT/$name.png"
  echo "captured $name"
}

adb shell cmd uimode night no
shot 1-drive-detected --ez active true
shot 2-review --es screen editor
shot 3-home
shot 4-settings --es screen settings
adb shell cmd uimode night yes
shot 5-dark --ez active true
adb shell cmd uimode night no

# Sample tax report (debug build writes it from the demo data).
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.ui.MainActivity" --ez demo true --ez pdf true >/dev/null
sleep 6
adb pull "/sdcard/Android/data/$PKG/files/sample-report.pdf" "$OUT/sample-report.pdf" || echo "no sample report"

# Smoke test the minified release build: install it, open each screen, fail on a crash.
REL=$(ls android/app/build/outputs/apk/release/*.apk 2>/dev/null | head -1)
if [ -n "$REL" ]; then
  adb uninstall "$PKG" >/dev/null 2>&1 || true
  adb install -r "$REL"
  adb logcat -c
  adb shell am start -W -n "$PKG/.ui.MainActivity" >/dev/null; sleep 6
  adb exec-out screencap -p > "$OUT/release-home.png"
  adb shell input keyevent KEYCODE_BACK; sleep 2
  adb shell am start -W -n "$PKG/.ui.MainActivity" >/dev/null; sleep 3
  adb shell am force-stop "$PKG"
  adb shell monkey -p "$PKG" --throttle 300 -s 7 200 >/dev/null 2>&1 || true
  sleep 2
  adb logcat -d > "$OUT/release-logcat.txt"
  if grep -q "Process: $PKG" "$OUT/release-logcat.txt"; then
    echo "Release build crashed"; grep -B2 -A40 "Process: $PKG" "$OUT/release-logcat.txt" | head -80; exit 1
  fi
  echo "release smoke test passed"
fi
ls -la "$OUT"
