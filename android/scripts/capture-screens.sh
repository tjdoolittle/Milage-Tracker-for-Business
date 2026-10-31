#!/usr/bin/env bash
# Install the debug build on a running emulator and capture store screenshots with sample data.
set -uo pipefail
PKG=com.tjdoolittle.mileagelog
APK=android/app/build/outputs/apk/debug/app-debug.apk
OUT=android/shots
mkdir -p "$OUT"
adb wait-for-device
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
  adb shell am start -W -n "$PKG/.ui.MainActivity" --ez demo true "$@" >/dev/null
  sleep 6
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
ls -la "$OUT"
