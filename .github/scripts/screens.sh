#!/bin/bash
# Installs the APK on the emulator, walks through every screen and saves PNG screenshots in ./shots.
set -x
mkdir -p shots
# What does the engine answer from this machine, outside the app?
URL='https://script.google.com/macros/s/AKfycbwtSiaDgdsQyJHgC6ZAXv66XGfCFjEICVFBfsz12LIpc25ee_F4Na8EEC6QOLEHobKB/exec?action=data&key=RS5NYfswFosn7rseUuG_x5WqIdbvPeQ0'
curl -sS -L -m 60 -D shots/curl_headers.txt "$URL" | head -c 1500 > shots/curl_body.txt
APK=app/build/outputs/apk/release/app-release.apk
adb install -r "$APK"
adb shell am start -n com.chitranjan.watchlist/.MainActivity
sleep 20   # first load fetches real prices from the Google Sheet engine

# Close "isn't responding" dialogs from the emulator's own apps so they don't cover the screen.
clear_anr() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  if grep -q "isn't responding" /tmp/ui.xml; then tap "Wait" 1; fi
}
shot() { clear_anr; adb exec-out screencap -p > "shots/$1.png"; }

# Tap the centre of the first element whose text or description matches.
tap() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  b=$(python3 - "$1" <<'PY'
import re, sys
xml = open('/tmp/ui.xml', encoding='utf-8').read()
want = sys.argv[1]
for m in re.finditer(r'<node [^>]*>', xml):
    n = m.group(0)
    t = re.search(r' text="([^"]*)"', n); d = re.search(r'content-desc="([^"]*)"', n)
    if (t and t.group(1) == want) or (d and d.group(1) == want):
        x1, y1, x2, y2 = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups())
        print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
)
  if [ -n "$b" ]; then adb shell input tap $b; else echo "NOT FOUND: $1"; fi
  sleep "${2:-2}"
}

# Like tap, but matches text or description that starts with the given words.
tap_prefix() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  b=$(python3 - "$1" <<'PY'
import re, sys
xml = open('/tmp/ui.xml', encoding='utf-8').read()
want = sys.argv[1]
for m in re.finditer(r'<node [^>]*>', xml):
    n = m.group(0)
    t = re.search(r' text="([^"]*)"', n); d = re.search(r'content-desc="([^"]*)"', n)
    if (t and t.group(1).startswith(want)) or (d and d.group(1).startswith(want)):
        x1, y1, x2, y2 = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups())
        print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
)
  if [ -n "$b" ]; then adb shell input tap $b; else echo "NOT FOUND: $1"; fi
  sleep "${2:-2}"
}

# Bring the app back to the front if a Back press closed it.
front() { adb shell am start -n com.chitranjan.watchlist/.MainActivity >/dev/null 2>&1; sleep 2; }

shot 01_watchlist
adb shell input swipe 540 1800 540 700 400; sleep 2; shot 02_watchlist_scrolled
adb shell input swipe 540 700 540 2000 300; sleep 1
tap "Side by side"; shot 03_side_by_side
tap "BSE"; shot 04_bse_only
tap "All"
tap "SBIN" 8; shot 05_stock_detail
tap "1Y" 2; shot 06_stock_1y
adb shell input swipe 540 1800 540 600 400; sleep 2; shot 07_stock_scrolled
adb shell input keyevent KEYCODE_BACK; sleep 2; front
tap "Markets"; shot 08_markets
tap "Search" 3; adb shell input text "baroda"; sleep 2; shot 09_search_baroda
adb shell input keyevent KEYCODE_BACK; sleep 1; front
tap "Settings"; shot 10_settings
adb shell input swipe 540 1800 540 600 400; sleep 2; shot 11_settings_scrolled
adb shell input swipe 540 600 540 2000 300; sleep 1
tap "Dark" 2; tap "Watchlist" 3; shot 12_watchlist_dark
tap "Side by side"; shot 13_side_by_side_dark
tap "BHEL" 10; shot 14_bhel_detail_dark
adb shell input keyevent KEYCODE_BACK; sleep 1; front
tap "Settings"; tap "Light" 2; tap "Emerald" 2; tap "Watchlist" 3; shot 15_light_emerald

# Insights: today, exchanges, sectors, then the history chart (the engine downloads each stock's history once).
tap "Insights" 45; shot 16_insights
adb shell input swipe 540 1800 540 700 400; sleep 2; shot 17_insights_sectors
adb shell input swipe 540 1800 540 700 400; sleep 2; shot 18_insights_history
adb shell input swipe 540 1800 540 700 400; sleep 2; shot 19_insights_bottom
for i in 1 2 3 4; do adb shell input swipe 540 600 540 2200 250; done; sleep 1
tap "1Y" 3; shot 20_insights_1y
# Rename a list from Settings.
tap "Settings"; adb shell input swipe 540 1800 540 700 400; sleep 1
tap_prefix "Rename" 2; shot 21_rename_dialog
adb shell input keyevent KEYCODE_BACK; sleep 1; front
adb logcat -d -s WatchlistEngine:* AndroidRuntime:E > shots/logcat.txt
ls -la shots
