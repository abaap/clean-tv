#!/usr/bin/env bash
# Install TV Remote on the TV and give it what it needs, saying what each grant is for.
#
#   RELAY_URL=https://tv-remote.<you>.workers.dev TV_HOST=<tv ip> addons/tv-remote/install.sh   # install, point it at your relay
#   TV_HOST=<tv ip> addons/tv-remote/install.sh                 # install the APK you built with -PrelayUrl
#   TV_HOST=<tv ip> addons/tv-remote/install.sh --uninstall
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
TOOLS="$HERE/../../tools"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
PY="$TOOLS/venv/bin/python"
[ -x "$PY" ] || PY="$TOOLS/venv/Scripts/python.exe"
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=io.github.ptrinh.tvremote
SVC=$PKG/$PKG.KeyService
APK="$HERE/app/build/outputs/apk/release/app-release.apk"
tv() { "$PY" -I "$TOOLS/tv.py" "$@"; }

if [ "${1:-}" = --uninstall ]; then
  # Take the keyboard service out of the enabled list, keeping any others, then remove the app.
  tv "s=\$(settings get secure enabled_accessibility_services); s=\$(echo \":\$s:\" | sed 's#:$SVC:#:#; s#^:##; s#:\$##'); if [ -z \"\$s\" ]; then settings delete secure enabled_accessibility_services; else settings put secure enabled_accessibility_services \$s; fi; pm uninstall $PKG"
  exit 0
fi

if [ ! -f "$APK" ]; then
  [ -f "$HERE/local.properties" ] || { [ -n "${ANDROID_HOME:-}" ] && echo "sdk.dir=$ANDROID_HOME" > "$HERE/local.properties"; } || true
  if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
  (cd "$HERE" && ./gradlew assembleRelease -q --console=plain) \
    || { echo "build failed (JDK 17 and Android SDK 34 needed)" >&2; exit 1; }
fi
[ -f "$APK" ] || { echo "no APK: build it first (cd addons/tv-remote && ./gradlew assembleRelease)" >&2; exit 1; }

"$PY" -I "$TOOLS/b64push.py" "$APK" /data/local/tmp/tvremote.apk >/dev/null
OUT=$(tv 'pm install -r /data/local/tmp/tvremote.apk; rm -f /data/local/tmp/tvremote.apk')
echo "$OUT"
case "$OUT" in *Success*) ;; *) echo "ERROR: pm install failed" >&2; exit 1 ;; esac

echo "Display over other apps: the lock timer's countdown, and opening the message, lock and live-view screens while another app is in front."
tv "appops set $PKG SYSTEM_ALERT_WINDOW allow"
echo "Screen capture without a prompt (PROJECT_MEDIA): the live view, only while a paired phone watches."
tv "appops set $PKG PROJECT_MEDIA allow"
echo "Usage access (GET_USAGE_STATS): Screen time by app."
tv "appops set $PKG GET_USAGE_STATS allow"
echo "Write secure settings: ADB on/off from the phone's Settings."
tv "pm grant $PKG android.permission.WRITE_SECURE_SETTINGS"
echo "Accessibility service (TV Remote keyboard): typing from the phone, Back/Home, and standby without IP control."
# Add the service to the enabled list without switching off the ones already there.
tv "s=\$(settings get secure enabled_accessibility_services); case :\$s: in *:$SVC:*) ;; *) if [ -z \$s ] || [ \$s = null ]; then s=$SVC; else s=\$s:$SVC; fi; settings put secure enabled_accessibility_services \$s;; esac; settings put secure accessibility_enabled 1"
# TCL's auto-start gate (the op doesn't exist elsewhere), so the connection comes back after a reboot.
tv "appops set $PKG AUTO_START allow 2>/dev/null; appops set $PKG APP_AUTO_START allow 2>/dev/null; appops write-settings 2>/dev/null; true" >/dev/null
if [ -n "${RELAY_URL:-}" ]; then
  echo "Relay: $RELAY_URL (saved on the TV; a new one only needs this script again, no rebuild)."
  tv "am broadcast -n $PKG/.RelayReceiver --es url '$RELAY_URL'" | grep -q "result=0" || echo "WARNING: the TV refused the relay address $RELAY_URL" >&2
fi
tv "am start -n $PKG/.MainActivity >/dev/null; echo opened"
echo "Installed. TV Remote is open on the TV: scan the QR code with your phone. Press Home and answer Show card for the card."
