#!/usr/bin/env bash
# Runs a feature's Maestro flows (.maestro/<feature>/*.yaml) on a connected Android device or
# emulator, with the dev flavor pointed at the local saveme-backend.
#
# Each flow declares the account it needs in a header comment:
#   # e2e-seed: none | verified-user | unverified-user
#   #           | unverified-user+verify-link | verified-user+reset-link
# The runner creates a fresh account per flow (unique address, so the shared dev database needs no
# reset) and passes it to Maestro as E2E_EMAIL / E2E_PASSWORD, plus E2E_API (the backend base URL
# as seen from this machine, for .maestro/common/api.js). Accounts are created through the
# backend's RegisterAccount use case with artisan, not over HTTP: auth/register is throttled to
# 10 an hour.
# The +…-link seeds also issue two mailed links through the backend's LinkRepository (the same
# secret a mail would carry, no mail reading): E2E_OLD_LINK, already superseded, and E2E_LINK,
# live. Both use the app's dev link host (http://localhost:<port>) so Maestro's openLink goes
# through the real deep-link intake. The secrets belong to throwaway dev accounts; Maestro writes
# them into build/e2e debug output, which is git-ignored.
#
# Usage: run-e2e.sh <feature-dir-name> [--skip-install] [--flow <name>]
# Env:   E2E_BACKEND_CONTAINER (default: saveme-backend-main)
#        E2E_BACKEND_PORT      (default: 43871)
# Exit:  0 all flows passed (or none exist), 1 a flow failed, 2 preflight failed.

set -euo pipefail
export LC_ALL=C

REPO_ROOT="$(git rev-parse --show-toplevel)"
CONTAINER="${E2E_BACKEND_CONTAINER:-saveme-backend-main}"
PORT="${E2E_BACKEND_PORT:-43871}"
PASSWORD="E2ePass-$(date +%s)"

feature="" skip_install=0 only_flow=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --skip-install) skip_install=1; shift ;;
        --flow) only_flow="$2"; shift 2 ;;
        -h|--help) sed -n '2,17p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) feature="$1"; shift ;;
    esac
done
[[ -z "$feature" ]] && { echo "ERROR: usage: run-e2e.sh <feature-dir-name> [--skip-install] [--flow <name>]" >&2; exit 2; }

fail_preflight() { echo "PREFLIGHT FAILED: $1" >&2; exit 2; }

# --- Preflight --------------------------------------------------------------------------------
command -v maestro >/dev/null \
    || fail_preflight "maestro not on PATH. Install: curl -fsSL https://get.maestro.mobile.dev | bash"
command -v adb >/dev/null || fail_preflight "adb not on PATH (Android SDK platform-tools)"
# Maestro Studio holds the on-device driver; CLI runs then die with DeviceServerDiedException.
pgrep -f maestro-studio >/dev/null \
    && fail_preflight "Maestro Studio is running; quit it (including its tray icon) first"

devices=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
[[ -z "$devices" ]] \
    && fail_preflight "no device attached. Start one: \`android emulator start <avd>\` (list: \`android emulator list\`) or plug in a phone"
[[ $(wc -l <<< "$devices") -gt 1 ]] && [[ -z "${ANDROID_SERIAL:-}" ]] \
    && fail_preflight "several devices attached; choose one with ANDROID_SERIAL=<serial>"
serial="${ANDROID_SERIAL:-$devices}"

curl -fs -o /dev/null --max-time 5 "http://127.0.0.1:$PORT/up" \
    || fail_preflight "backend not answering on http://127.0.0.1:$PORT/up. Start the saveme-backend dev container ($CONTAINER)"
docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null | grep -q true \
    || fail_preflight "container $CONTAINER not running (needed to seed accounts; set E2E_BACKEND_CONTAINER)"

flow_dir="$REPO_ROOT/.maestro/$feature"
out_dir="$REPO_ROOT/build/e2e/$feature"
shopt -s nullglob
flows=("$flow_dir"/*.yaml)
if [[ -n "$only_flow" ]]; then
    flows=("$flow_dir/${only_flow%.yaml}.yaml")
    [[ -f "${flows[0]}" ]] || fail_preflight "no flow ${flows[0]#"$REPO_ROOT"/}"
fi

echo "# E2E run: $feature"
echo "Device: $serial   Backend: 127.0.0.1:$PORT ($CONTAINER)"
if [[ ${#flows[@]} -eq 0 ]]; then
    echo "No flows in .maestro/$feature/ — nothing to run."
    exit 0
fi

# Keep the screen on and unlocked: an asleep device renders nothing and every flow fails.
adb -s "$serial" shell svc power stayon true >/dev/null 2>&1 || true
adb -s "$serial" shell input keyevent KEYCODE_WAKEUP
adb -s "$serial" shell wm dismiss-keyguard >/dev/null 2>&1 || true
# Gboard's "Try out your stylus" onboarding pops over text fields on some emulators (foldables)
# and swallows inputText.
adb -s "$serial" shell settings put secure stylus_handwriting_enabled 0 >/dev/null 2>&1 || true

# --- Build and install ------------------------------------------------------------------------
# Every device reaches the backend through adb reverse on localhost. An emulator could use
# 10.0.2.2, but Android 17 (API 37) blocks apps from private-network addresses unless they hold
# the local-network permission; loopback is exempt.
api_host="localhost:$PORT"
adb -s "$serial" reverse "tcp:$PORT" "tcp:$PORT" >/dev/null
if [[ $skip_install -eq 0 ]]; then
    echo "Installing devDebug with devApiHost=$api_host ..."
    "$REPO_ROOT/gradlew" -q -p "$REPO_ROOT" :androidApp:installDevDebug -PdevApiHost="$api_host" \
        || fail_preflight "installDevDebug failed"
fi

# --- App reset -------------------------------------------------------------------------------
# Every flow starts from a fresh install state. `pm clear` is refused on some devices (Xiaomi
# HyperOS: CLEAR_APP_USER_DATA), and reinstalling needs a tap on the phone there, so fall back
# to wiping the app's own data with run-as (the dev debug build is debuggable).
APP_ID="com.fares.saveme"
reset_app() {
    adb -s "$serial" shell pm clear "$APP_ID" >/dev/null 2>&1 && return 0
    adb -s "$serial" shell am force-stop "$APP_ID"
    # run-as starts in the app's data dir; "lib" there is a system-owned symlink.
    adb -s "$serial" shell "run-as $APP_ID sh -c 'for f in * .[!.]*; do [ \"\$f\" = lib ] || [ ! -e \"\$f\" ] || rm -rf \"\$f\"; done'" \
        || { echo "ERROR: could not reset $APP_ID (pm clear refused, run-as failed)" >&2; return 1; }
}

# --- Seeding ----------------------------------------------------------------------------------
# Registers <email> through the backend use case, then marks it verified when asked.
seed_account() {
    local email="$1" verified="$2"
    docker exec "$CONTAINER" php artisan tinker --execute="
        \$e = '$email';
        app(\App\Modules\Identity\Application\Registration\RegisterAccount::class)->handle(\$e, '$PASSWORD', null, null, 'en');
        if ($verified) {
            \App\Modules\Identity\Infrastructure\Models\User::where('email_lookup', app(\App\Modules\Identity\Domain\Ports\AddressLookup::class)->for(\$e))
                ->update(['email_verified_at' => now()]);
        }
        echo 'seeded';
    " 2>&1 | grep -q seeded
}

# Issues a live <kind> link (VerifyAddress | ResetPassword) for <email> and prints its secret.
# Registration queues a welcome mail that issues its own verification link (database queue, run
# by the container's worker). If that job ran after this, it would supersede our link, so wait up
# to 10 s for the welcome link to exist first.
issue_link() {
    local email="$1" kind="$2"
    docker exec "$CONTAINER" php artisan tinker --execute="
        \$id = \App\Modules\Identity\Infrastructure\Models\User::where('email_lookup', app(\App\Modules\Identity\Domain\Ports\AddressLookup::class)->for('$email'))->value('id');
        if ('$kind' === 'VerifyAddress') {
            for (\$i = 0; \$i < 40 && !\App\Modules\Identity\Infrastructure\Models\IdentityLink::where('user_id', \$id)->where('kind', 'verify_address')->exists(); \$i++) { usleep(250000); }
        }
        echo 'secret=' . app(\App\Modules\Identity\Domain\Ports\LinkRepository::class)->issue(\$id, \App\Modules\Identity\Domain\Account\LinkKind::$kind);
    " 2>&1 | grep -oE 'secret=[^[:space:]]+' | head -1 | cut -d= -f2-
}

# --- Run --------------------------------------------------------------------------------------
# A single-flow rerun keeps the other flows' results.
if [[ -n "$only_flow" ]]; then
    n="${only_flow%.yaml}"; rm -rf "$out_dir/$n.xml" "$out_dir/$n.log" "$out_dir/debug/$n"
else
    rm -rf "$out_dir"
fi
mkdir -p "$out_dir"
run_id=$(date +%Y%m%d%H%M%S)
failed=0
for flow in "${flows[@]}"; do
    name=$(basename "$flow" .yaml)
    seed=$(grep -m1 -oE '^# e2e-seed: *[a-z+-]+' "$flow" | sed -E 's/^# e2e-seed: *//' || true)
    env_args=(-e "E2E_API=http://127.0.0.1:$PORT")
    case "${seed:-none}" in
        none) ;;
        verified-user|unverified-user|unverified-user+verify-link|verified-user+reset-link)
            email="e2e+$run_id-$(tr '[:upper:]' '[:lower:]' <<< "$name")@example.com"
            verified=$([[ "$seed" == verified-user* ]] && echo true || echo false)
            if ! seed_account "$email" "$verified"; then
                echo "FAIL $name (seeding $seed failed)"; failed=1; continue
            fi
            env_args+=(-e "E2E_EMAIL=$email" -e "E2E_PASSWORD=$PASSWORD")
            if [[ "$seed" == *-link ]]; then
                if [[ "$seed" == *verify-link ]]; then kind=VerifyAddress path=verify-email; else kind=ResetPassword path=reset-password; fi
                old=$(issue_link "$email" "$kind")
                live=$(issue_link "$email" "$kind")
                if [[ -z "$old" || -z "$live" ]]; then
                    echo "FAIL $name (issuing $kind links failed)"; failed=1; continue
                fi
                env_args+=(-e "E2E_OLD_LINK=http://localhost:$PORT/$path?token=$old" -e "E2E_LINK=http://localhost:$PORT/$path?token=$live")
            fi
            ;;
        *) echo "FAIL $name (unknown e2e-seed '$seed')"; failed=1; continue ;;
    esac

    if ! reset_app; then
        echo "FAIL $name (could not reset the app)"; failed=1; continue
    fi
    if maestro --device "$serial" test "$flow" "${env_args[@]}" \
        --format junit --output "$out_dir/$name.xml" \
        --debug-output "$out_dir/debug/$name" > "$out_dir/$name.log" 2>&1; then
        echo "PASS $name"
    else
        echo "FAIL $name (log: ${out_dir#"$REPO_ROOT"/}/$name.log, debug: ${out_dir#"$REPO_ROOT"/}/debug/$name)"
        failed=1
    fi
done

exit $failed
