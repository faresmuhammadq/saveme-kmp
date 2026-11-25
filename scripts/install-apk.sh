#!/usr/bin/env bash
# Installs an APK on the phone over USB with adb from the builder image (nothing installed on the host).
#
#   scripts/install-apk.sh [path/to.apk]     default: the newest APK in dist/
#
# The phone needs Developer options > USB debugging on. The first time, accept the "Allow USB debugging?"
# prompt on the phone; adb's key is kept in the saveme-adb-keys volume, so it is asked once.
# `install -r` keeps the app's data; it works only over an APK signed with the same key.
set -euo pipefail

# shellcheck source=scripts/_builder.sh
source "$(dirname "${BASH_SOURCE[0]}")/_builder.sh"

apk=${1:-}
if [[ -z $apk ]]; then
    apk=$(ls -t "$DIST"/*.apk 2>/dev/null | head -n1) || true
    [[ -n $apk ]] || die "no APK in dist/; run scripts/build-apk.sh first or pass a path"
fi
[[ -f $apk ]] || die "not a file: $apk"
apk=$(realpath "$apk")

image=$(builder_image)
log "installing $(basename "$apk") — plug in the phone and allow USB debugging if asked"

# Root and --privileged: adb needs the raw USB device nodes, which only root can open without host udev rules.
docker run --rm --privileged --user root \
    -v /dev/bus/usb:/dev/bus/usb \
    -v saveme-adb-keys:/root/.android \
    -v "$(dirname "$apk"):/apk:ro" \
    -e APK="/apk/$(basename "$apk")" \
    "$image" bash -euo pipefail -c '
        adb start-server >/dev/null
        adb wait-for-device
        adb devices -l
        adb install -r "$APK"
        adb kill-server >/dev/null 2>&1 || true
    '
log "installed"
