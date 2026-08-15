#!/usr/bin/env bash
# Generate Play Store screenshots for 순아래 키보드 using the Android emulator.
#
# Prerequisites:
#   - Android SDK with emulator + system-image;android-35;google_apis;x86_64
#     (install via: sdkmanager "emulator" "system-images;android-35;google_apis;x86_64")
#   - An AVD named "test" (avdmanager create avd -n test -k "system-images;android-35;google_apis;x86_64")
#   - APK built: ./gradlew assembleRelease
#   - Emulator booted: emulator -avd test -no-window -no-audio -gpu swiftshader_indirect &
#   - adb root available (emulator with google_apis image supports `adb root`)
#
# Usage:
#   bash scripts/gen-play-store-screenshots.sh
#
# Output: metadata/ko-KR/images/phoneScreenshots/{01..06}.png (720x1280)
#
# Scenes (matches Play Store listing order):
#   01 기본 시스템 라이트테마   (system theme, light mode)
#   02 기본 시스템 다크테마     (system theme, dark mode)
#   03 백스페이스가 오른쪽인 설정 (pref_show_backspace_on_right=true)
#   04 시스템 테마 (보더 있음)   (theme id 6 = system_border)
#   05 꾹 눌러서 나온 보조키 상태 (long-press jamo key -> moreKeys popup)
#   06 특수문자 키보드 상태       (?123 / toSymbol key -> symbols layout)
set -euo pipefail

ADB="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
SERIAL="${ANDROID_SERIAL:-emulator-5554}"
PKG="net.kjwon15.noshiftkeyboard"
# The app reads prefs from DEVICE-protected storage (createDeviceProtectedStorageContext),
# i.e. /data/user_de/0/<pkg>/shared_prefs/, NOT /data/data/<pkg>/shared_prefs/.
PREFS="/data/user_de/0/${PKG}/shared_prefs/net.kjwon15.noshiftkeyboard_preferences.xml"
SEARCH_URL="https://www.google.com/search?q=%ED%95%9C%EA%B8%80"

OUT_DIR="$(cd "$(dirname "$0")/.." && pwd)/metadata/ko-KR/images/phoneScreenshots"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

adb() { "$ADB" -s "$SERIAL" "$@"; }

die() { echo "ERROR: $*" >&2; exit 1; }

# --- sanity checks -----------------------------------------------------------
command -v python3 >/dev/null || die "python3 required (for resizing)"
adb get-state >/dev/null 2>&1 || die "emulator $SERIAL not connected"
adb root >/dev/null 2>&1 || true
sleep 2
adb shell "test -d /data/data/$PKG && echo OK" | grep -q OK || die "app not installed; run ./gradlew installDebug or installRelease first"

# --- force our keyboard as the default IME -----------------------------------
# (adb root / restarts can reset default_input_method back to Gboard)
echo "setting $PKG as default IME"
# Gboard gets re-selected after adb root; disable it so our keyboard is the only IME
adb shell "pm disable-user --user 0 com.google.android.inputmethod.latin" >/dev/null 2>&1 || true
adb shell "pm disable-user --user 0 com.google.android.tts" >/dev/null 2>&1 || true
adb shell "ime enable $PKG/.latin.LatinIME" >/dev/null 2>&1 || true
adb shell "ime set $PKG/.latin.LatinIME" >/dev/null 2>&1 || true
adb shell "settings put secure default_input_method $PKG/.latin.LatinIME"
adb shell "settings delete secure selected_input_method_subtype" >/dev/null 2>&1 || true
adb shell "settings get secure default_input_method" | grep -q "$PKG" \
    || die "failed to set default IME to $PKG"

# --- helpers -----------------------------------------------------------------
# write_prefs <theme_id> <backspace_on_right>
# Updates the device-protected prefs XML in place: sets the theme id and the
# backspace-on-right flag while preserving every other existing setting.
write_prefs() {
    local theme="$1" bs_right="$2"
    local dir uid gid
    dir="$(dirname "$PREFS")"
    uid="$(adb shell stat -c %u "/data/data/$PKG" 2>/dev/null | tr -d '\r')"
    gid="$(adb shell stat -c %g "/data/data/$PKG" 2>/dev/null | tr -d '\r')"
    # Pull the current file, edit locally, push back.
    adb shell "mkdir -p $dir"
    adb pull "$PREFS" "$TMP/prefs.xml" >/dev/null 2>&1 || echo '<map></map>' > "$TMP/prefs.xml"
    python3 - "$TMP/prefs.xml" "$theme" "$bs_right" <<'PY'
import re, sys
path, theme, bs = sys.argv[1], sys.argv[2], sys.argv[3]
d = open(path).read()
d = re.sub(r'<string name="pref_keyboard_theme_20140509">[^<]*</string>', '', d)
d = re.sub(r'<boolean name="pref_show_backspace_on_right" value="[^"]*"[^/]*/>', '', d)
entry = ('<string name="pref_keyboard_theme_20140509">%s</string>'
         '<boolean name="pref_show_backspace_on_right" value="%s" />') % (theme, bs)
d = d.replace('<map>', '<map>' + entry, 1)
open(path, 'w').write(d)
PY
    adb push "$TMP/prefs.xml" "$PREFS" >/dev/null 2>&1
    adb shell "chown ${uid:-u0_a209}:${gid:-u0_a209} $PREFS; chmod 660 $PREFS"
    echo "  prefs: theme=$theme backspace_right=$bs_right"
}

# restart_app: force-stop the IME app so new prefs take effect on next start.
# pkill ensures the process is actually gone (am force-stop can be async),
# so the IME restarts fresh and reads the updated prefs file.
restart_app() {
    adb shell "am force-stop $PKG"
    adb shell "pkill -f net.kjwon15.noshiftkeyboard" >/dev/null 2>&1 || true
    sleep 2
}

# shot <name> : screencap + pull
shot() {
    adb shell screencap -p "/sdcard/$1.png"
    adb pull "/sdcard/$1.png" "$TMP/$1.png" >/dev/null 2>&1
    echo "  captured $1"
}

# open_sms: open a Google Messages conversation with a fixed recipient and
# focus the message input field (a plain SMS field, no search suggestions)
open_sms() {
    adb shell "am force-stop com.google.android.apps.messaging" >/dev/null 2>&1 || true
    adb shell "am start -n com.google.android.apps.messaging/.ui.ConversationListActivity" >/dev/null 2>&1
    sleep 5
    adb shell "uiautomator dump /sdcard/sms1.xml" >/dev/null 2>&1 || true
    adb pull /sdcard/sms1.xml "$TMP/sms1.xml" >/dev/null 2>&1 || true

    local y
    # Dismiss first-run screen if present
    y="$(python3 - "$TMP/sms1.xml" <<'PY'
import re, sys
try:
    xml = open(sys.argv[1]).read()
except Exception:
    print(""); raise SystemExit
m = re.search(r'text="Use Messages without an account"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
print((int(m.group(1))+int(m.group(3)))//2, (int(m.group(2))+int(m.group(4)))//2) if m else print("")
PY
)"
    if [ -n "$y" ]; then
        adb shell input tap $y
        sleep 3
    fi

    # The recipient conversation may already exist in the list; open it directly.
    adb shell "uiautomator dump /sdcard/sms2.xml" >/dev/null 2>&1 || true
    adb pull /sdcard/sms2.xml "$TMP/sms2.xml" >/dev/null 2>&1 || true
    y="$(python3 - "$TMP/sms2.xml" <<'PY'
import re, sys
try:
    xml = open(sys.argv[1]).read()
except Exception:
    print(""); raise SystemExit
# Prefer an existing conversation row mentioning the recipient
for pat in [r'text="01012345678"', r'content-desc="[^"]*01012345678[^"]*"']:
    m = re.search(pat + r'[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
    if m:
        print((int(m.group(1))+int(m.group(3)))//2, (int(m.group(2))+int(m.group(4)))//2); raise SystemExit
print("")
PY
)"
    if [ -n "$y" ]; then
        adb shell input tap $y
        sleep 3
    else
        # No existing conversation: Start chat -> type recipient -> Send to
        y="$(python3 - "$TMP/sms2.xml" <<'PY'
import re, sys
try:
    xml = open(sys.argv[1]).read()
except Exception:
    print("845 2200"); raise SystemExit
m = re.search(r'text="Start chat"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
print((int(m.group(1))+int(m.group(3)))//2, (int(m.group(2))+int(m.group(4)))//2) if m else print("845 2200")
PY
)"
        [ -n "$y" ] && adb shell input tap $y && sleep 3
        adb shell input text "01012345678"
        sleep 2
        adb shell "uiautomator dump /sdcard/sms3.xml" >/dev/null 2>&1 || true
        adb pull /sdcard/sms3.xml "$TMP/sms3.xml" >/dev/null 2>&1 || true
        y="$(python3 - "$TMP/sms3.xml" <<'PY'
import re, sys
try:
    xml = open(sys.argv[1]).read()
except Exception:
    print("392 493"); raise SystemExit
m = re.search(r'text="Send to [^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
print((int(m.group(1))+int(m.group(3)))//2, (int(m.group(2))+int(m.group(4)))//2) if m else print("392 493")
PY
)"
        adb shell input tap $y
        sleep 3
    fi

    # Focus the message input field (bottom EditText) -> shows our keyboard
    adb shell "uiautomator dump /sdcard/sms4.xml" >/dev/null 2>&1 || true
    adb pull /sdcard/sms4.xml "$TMP/sms4.xml" >/dev/null 2>&1 || true
    y="$(python3 - "$TMP/sms4.xml" <<'PY'
import re, sys
try:
    xml = open(sys.argv[1]).read()
except Exception:
    print("556 2250"); raise SystemExit
eds = re.findall(r'class="android.widget.EditText"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
if eds:
    x1,y1,x2,y2 = map(int, eds[-1])
    print((x1+x2)//2, (y1+y2)//2)
else:
    print("556 2250")
PY
)"
    adb shell input tap $y
    sleep 3
    # Verify the keyboard is actually shown; retry focus once if not
    if ! adb shell "dumpsys input_method 2>/dev/null | grep -q 'mInputShown=true'"; then
        adb shell input tap $y
        sleep 3
    fi
}


# --- scene 1: 백스페이스 왼쪽, 라이트 -----------------------------------------
echo "[1/4] backspace-left, light theme"
adb shell "cmd uimode night no" >/dev/null
write_prefs 5 false
restart_app
open_sms
shot scene1

# --- scene 2: 백스페이스 왼쪽, 다크 -------------------------------------------
echo "[2/4] backspace-left, dark theme"
adb shell "cmd uimode night yes" >/dev/null
sleep 2
write_prefs 5 false
restart_app
open_sms
shot scene2

# --- scene 3: 백스페이스 오른쪽, 라이트 ---------------------------------------
echo "[3/4] backspace-right, light theme"
adb shell "cmd uimode night no" >/dev/null
write_prefs 5 true
restart_app
open_sms
shot scene3

# --- scene 4: 백스페이스 오른쪽, 다크 -----------------------------------------
echo "[4/4] backspace-right, dark theme"
adb shell "cmd uimode night yes" >/dev/null
sleep 2
write_prefs 5 true
restart_app
open_sms
shot scene4

# --- resize to 720x1280 and install ------------------------------------------
echo "resizing + installing to $OUT_DIR"
mkdir -p "$OUT_DIR"
python3 - "$TMP" "$OUT_DIR" <<'PY'
from PIL import Image
import os, sys
tmp, out = sys.argv[1], sys.argv[2]
# Play Store requirements: PNG, max 8MB, ratio 16:9 or 9:16, sides 1080..7680px.
# Phone source is 1080x2400 (9:16) -> scale to 1080x1920 (9:16).
for i in range(1, 5):
    src = os.path.join(tmp, f"scene{i}.png")
    dst = os.path.join(out, f"{i:02d}.png")
    img = Image.open(src).convert("RGB")
    if img.size == (1080, 2400):
        img = img.resize((1080, 1920), Image.LANCZOS)
    img.save(dst, "PNG")
    print(f"  {os.path.basename(dst)} ({img.size[0]}x{img.size[1]})")
PY

echo "done. Verify: $OUT_DIR"
