#!/usr/bin/env bash
# Runs a feature's Maestro flows (.maestro/<feature>/*.yaml) on a connected Android device or
# emulator, with the dev flavor pointed at the local saveme-backend.
#
# Each flow declares the account it needs in a header comment:
#   # e2e-seed: none | verified-user | unverified-user
#   #           | unverified-user+verify-link | verified-user+reset-link
#   #           | verified-user+plus | verified-user+plus-grace | verified-user+plus-lifetime
#   #           | verified-user+plus-lifetime+redeem-code ...  (a redeem suffix, see below)
#   # Any seed above may end in +partner (e.g. verified-user+plus+partner, spec 013 sharing): the runner also
#   # creates a second VERIFIED account on Plus (E2E_PARTNER_EMAIL / E2E_PARTNER_PASSWORD), the invitee.
# The runner creates a fresh account per flow (unique address, so the shared dev database needs no
# reset) and passes it to Maestro as E2E_EMAIL / E2E_PASSWORD, plus E2E_API (the backend base URL
# as seen from this machine, for .maestro/common/api.js). Accounts are created through the
# backend's RegisterAccount use case with artisan, not over HTTP: auth/register is throttled to
# 10 an hour.
# Redeem seeds (spec 015, client C11) are suffixes after the base seed and before +partner; each ends by
# exporting E2E_CODE, the mailed Redeem Code, to the flow. The runner submits a claim for the plan Plus · Monthly at
# its own price and approves it through ClaimService (as the back office would), then reads the code from the
# approval mail in the dev mail catcher (MAILPIT_API, default http://mailpit.test/api/v1; up to 30 s):
#   +redeem-code          the code was sent to the flow's own account address
#   +redeem-code-other    the code was sent to a different address (e2e+<run>-<flow>-other@example.com)
#   +redeem-code-used     as +redeem-code, then the code row is marked redeemed by someone else
#   +redeem-code-expired  as +redeem-code, then the code row's expiry is moved into the past
# The seeds never run in CI; they are written for the device run the owner schedules.
#
# The +…-link seeds also issue two mailed links through the backend's LinkRepository (the same
# secret a mail would carry, no mail reading): E2E_OLD_LINK, already superseded, and E2E_LINK,
# live. Both use the app's dev link host (http://localhost:<port>) so Maestro's openLink goes
# through the real deep-link intake. The secrets belong to throwaway dev accounts; Maestro writes
# them into build/e2e debug output, which is git-ignored.
#
# verified-user+plus also grants Plus · Monthly for 30 days (backend OperatorSubscriptions::grant, the
# audited use case the back office will call; no HTTP endpoint grants) before the app launches.
# +plus-lifetime grants it with no end date; +plus-grace grants it and then puts the subscription in
# its grace period for 3 days (no use case lapses a subscription on demand, so the row is updated).
# A flow that must see an entitlement change while the app stays open is split in two files:
# <name>.part1.yaml (carries the e2e-seed header) and <name>.part2.yaml with the header line
#   # e2e-between: grant-plus
# The runner runs part 1, grants Plus to the same account, then runs part 2 WITHOUT resetting the
# app. Part 2 should tap the Plan screen's refresh (plan.refresh), because a foreground return can
# fall inside the 60 s refresh floor. A flow is named by its base (<name>) for --flow.
# A flow can have up to nine parts (<name>.part1 … <name>.part9); each part after the first names
# the step the runner takes before it. Besides grant-plus there are two for offline flows (spec 006):
#   # e2e-between: network-off   cuts the phone's route to the backend (adb reverse --remove), so the
#                                app sees "no connection" while the host-side api.js still works
#   # e2e-between: network-on    restores it
# The route is restored after every flow, even a failed one.
#
# Usage: run-e2e.sh <feature-dir-name> [--skip-install] [--flow <name>]
# Env:   E2E_BACKEND_CONTAINER (default: saveme-backend-main)
#        E2E_BACKEND_PORT      (default: 43871)
#        MAILPIT_API           (default: http://mailpit.test/api/v1; only the +redeem-code* seeds read it)
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
        -h|--help) sed -n '2,36p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
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
    [[ -f "${flows[0]}" ]] || flows=("$flow_dir/${only_flow%.yaml}.part1.yaml")
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

# A phone carries its owner's settings, which the run changes and puts back on exit:
# - an autofill service (a password manager) answers every sign-in with a "Save password?" sheet
#   over the app, so it is switched off for the run;
# - animations are switched off so taps don't land mid-transition.
# The screen's stay-awake setting goes back to off.
setting() { adb -s "$serial" shell settings get "$1" "$2" 2>/dev/null | tr -d '\r'; }
orig_autofill=$(setting secure autofill_service)
orig_hide_errors=$(setting global hide_error_dialogs)
orig_anim=()
for key in window_animation_scale transition_animation_scale animator_duration_scale; do
    orig_anim+=("$key=$(setting global "$key")")
done
restore_device() {
    adb -s "$serial" reverse "tcp:$PORT" "tcp:$PORT" >/dev/null 2>&1 || true
    if [[ -n "$orig_autofill" && "$orig_autofill" != null ]]; then
        adb -s "$serial" shell settings put secure autofill_service "$orig_autofill" >/dev/null 2>&1 || true
    fi
    for pair in "${orig_anim[@]}"; do
        value="${pair#*=}"; [[ -z "$value" || "$value" == null ]] && value=1.0
        adb -s "$serial" shell settings put global "${pair%%=*}" "$value" >/dev/null 2>&1 || true
    done
    adb -s "$serial" shell settings put global hide_error_dialogs "${orig_hide_errors/null/0}" >/dev/null 2>&1 || true
    adb -s "$serial" shell svc power stayon false >/dev/null 2>&1 || true
}
trap restore_device EXIT
adb -s "$serial" shell settings put secure autofill_service null >/dev/null 2>&1 || true
# A slow emulator can raise "<system app> isn't responding" over the app under test; hide those.
adb -s "$serial" shell settings put global hide_error_dialogs 1 >/dev/null 2>&1 || true
for key in window_animation_scale transition_animation_scale animator_duration_scale; do
    adb -s "$serial" shell settings put global "$key" 0 >/dev/null 2>&1 || true
done

# Maestro reinstalls its on-device driver when it thinks it is stale. Phones that confirm every USB
# install on screen (Xiaomi HyperOS "Install via USB") refuse that unattended
# (INSTALL_FAILED_USER_RESTRICTED), so once both driver packages are on the device, keep them.
# Set E2E_REINSTALL_DRIVER=1 to let Maestro reinstall them (after a Maestro upgrade).
driver_args=()
if [[ "${E2E_REINSTALL_DRIVER:-0}" != 1 ]] \
    && adb -s "$serial" shell pm list packages dev.mobile.maestro | grep -q 'dev.mobile.maestro$' \
    && adb -s "$serial" shell pm list packages dev.mobile.maestro.test | grep -q 'dev.mobile.maestro.test$'; then
    driver_args=(--no-reinstall-driver)
fi

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

# Grants Plus · Monthly for 30 days to <email> through the backend's operator use case (spec 005 R-12).
# The plan id is looked up by its backend code (plus_monthly); codes are not exposed to clients, but
# this runs inside the backend. The operator id is a fixed UUID: the audit log's actor_id is a uuid.
E2E_OPERATOR_ID="00000000-0000-7000-8000-0000000000e2"
grant_plus() {
    local email="$1" days="${2:-30}" grace="${3:-0}"
    docker exec "$CONTAINER" php artisan tinker --execute="
        \$id = \App\Modules\Identity\Infrastructure\Models\User::where('email_lookup', app(\App\Modules\Identity\Domain\Ports\AddressLookup::class)->for('$email'))->value('id');
        \$plan = \App\Modules\Billing\Infrastructure\Models\Plan::where('code', 'plus_monthly')->value('id');
        app(\App\Modules\Billing\Application\Subscriptions\OperatorSubscriptions::class)->grant('$E2E_OPERATOR_ID', \$id, \$plan, $days === 0 ? null : $days, 'e2e');
        if ($grace) {
            \App\Modules\Billing\Infrastructure\Models\Subscription::where('user_id', \$id)->update(['status' => 'grace', 'grace_until' => now()->addDays(3)]);
        }
        echo 'granted';
    " 2>&1 | grep -q granted
}

# Spec 015: submits a claim for <address> (Plus · Monthly at its own price) and approves it through ClaimService, then
# prints the claim id. Approval queues the mail that carries the code (run by the container's worker).
approve_claim_for() {
    local address="$1"
    docker exec "$CONTAINER" php artisan tinker --execute="
        \$planId = \App\Modules\Billing\Infrastructure\Models\Plan::where('code', 'plus_monthly')->value('id');
        \$price = \App\Modules\Billing\Domain\Claims\ClaimPrice::of(app(\App\Modules\Billing\Domain\Ports\PlanRepository::class)->find(\$planId));
        \$service = app(\App\Modules\Billing\Application\Claims\ClaimService::class);
        \$claim = \$service->submit(new \App\Modules\Billing\Application\Claims\SubmitClaim('$address', \$planId, \$price['amount_minor'], \$price['currency'], 'E2E' . random_int(1000000000, 9999999999), 'en'));
        \$service->approve('$E2E_OPERATOR_ID', \$claim->id);
        echo 'claim=' . \$claim->id;
    " 2>&1 | grep -oE 'claim=[0-9a-f-]+' | head -1 | cut -d= -f2-
}

# Spec 015: waits up to 30 s for the approval mail to <address> in the dev mail catcher and prints the code in it
# (XXXX-XXXX-XXXX-XXXX). The code is stored only as a hash, so the mail is the one place to read it.
read_mailed_code() {
    local address="$1" api="${MAILPIT_API:-http://mailpit.test/api/v1}" code="" id="" i
    for i in $(seq 1 30); do
        id=$(curl -fs --max-time 5 --get --data-urlencode "query=to:$address" "$api/search" \
            | python3 -c 'import sys, json; m = json.load(sys.stdin).get("messages") or []; print(m[0]["ID"] if m else "")' 2>/dev/null || true)
        if [[ -n "$id" ]]; then
            code=$(curl -fs --max-time 5 "$api/message/$id" \
                | python3 -c 'import sys, json, re; m = re.search(r"[0-9A-Z]{4}(?:-[0-9A-Z]{4}){3}", json.load(sys.stdin).get("Text", "")); print(m.group(0) if m else "")' 2>/dev/null || true)
            [[ -n "$code" ]] && break
        fi
        sleep 1
    done
    printf '%s' "$code"
}

# Spec 015: <kind> is redeem-code | redeem-code-other | redeem-code-used | redeem-code-expired. Prints the mailed code.
# -used: the code row is marked redeemed by someone else (an address is unique to one account, so no real second
# redeemer can exist; this row edit is the only way to reach REDEEM_CODE_USED end to end). -expired: expiry in the past,
# status left issued as before the daily sweep.
seed_redeem_code() {
    local email="$1" kind="$2" run="$3" name="$4" address claim_id code
    address="$email"
    [[ "$kind" == redeem-code-other ]] && address="e2e+$run-$(tr '[:upper:]' '[:lower:]' <<< "$name")-other@example.com"
    claim_id=$(approve_claim_for "$address")
    [[ -n "$claim_id" ]] || return 1
    code=$(read_mailed_code "$address")
    [[ -n "$code" ]] || return 1
    case "$kind" in
        redeem-code-used)
            docker exec "$CONTAINER" php artisan tinker --execute="
                \App\Modules\Billing\Infrastructure\Models\RedeemCode::where('claim_id', '$claim_id')->update([
                    'status' => 'redeemed', 'redeemed_by' => (string) \Illuminate\Support\Str::uuid7(),
                    'subscription_id' => (string) \Illuminate\Support\Str::uuid7(), 'redeemed_at' => now(),
                ]);
                echo 'marked';
            " 2>&1 | grep -q marked || return 1
            ;;
        redeem-code-expired)
            docker exec "$CONTAINER" php artisan tinker --execute="
                \App\Modules\Billing\Infrastructure\Models\RedeemCode::where('claim_id', '$claim_id')->update(['expires_at' => now()->subDay()]);
                echo 'marked';
            " 2>&1 | grep -q marked || return 1
            ;;
    esac
    printf '%s' "$code"
}

# Runs one Maestro file with the current env_args; <label> names its report and log.
run_maestro() {
    local file="$1" label="$2"
    maestro --device "$serial" test "${driver_args[@]}" "$file" "${env_args[@]}" \
        --format junit --output "$out_dir/$label.xml" \
        --debug-output "$out_dir/debug/$label" > "$out_dir/$label.log" 2>&1
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

# The phone reaches the backend only through adb reverse (see above), so removing the reverse is
# the one lever that makes the app offline while the host keeps talking to the backend.
network_off() { adb -s "$serial" reverse --remove "tcp:$PORT" >/dev/null 2>&1 || true; }
network_on() { adb -s "$serial" reverse "tcp:$PORT" "tcp:$PORT" >/dev/null 2>&1 || true; }

# --- Run --------------------------------------------------------------------------------------
# A single-flow rerun keeps the other flows' results.
if [[ -n "$only_flow" ]]; then
    n="${only_flow%.yaml}"; rm -rf "$out_dir/$n".* "$out_dir/debug/$n" "$out_dir/debug/$n.part"*
else
    rm -rf "$out_dir"
fi
mkdir -p "$out_dir"
run_id=$(date +%Y%m%d%H%M%S)
echo "Accounts: e2e+$run_id-<flow>@example.com"
failed=0
for flow in "${flows[@]}"; do
    [[ "$flow" =~ \.part[2-9]\.yaml$ ]] && continue # run right after its part 1
    name=$(basename "$flow" .yaml); name="${name%.part1}"
    later_parts=()
    if [[ "$flow" == *.part1.yaml ]]; then
        for n in 2 3 4 5 6 7 8 9; do
            candidate="${flow%.part1.yaml}.part$n.yaml"
            [[ -f "$candidate" ]] || break
            later_parts+=("$candidate")
        done
    fi
    network_on # a previous flow may have left the route cut
    seed=$(grep -m1 -oE '^# e2e-seed: *[a-z+-]+' "$flow" | sed -E 's/^# e2e-seed: *//' || true)
    want_partner=false
    if [[ "$seed" == *+partner ]]; then want_partner=true; seed="${seed%+partner}"; fi
    redeem_kind=""
    if [[ "$seed" =~ ^(.*)\+(redeem-code(-other|-used|-expired)?)$ ]]; then seed="${BASH_REMATCH[1]}"; redeem_kind="${BASH_REMATCH[2]}"; fi
    env_args=(-e "E2E_API=http://127.0.0.1:$PORT")
    case "${seed:-none}" in
        none) ;;
        verified-user|unverified-user|unverified-user+verify-link|verified-user+reset-link|verified-user+plus|verified-user+plus-grace|verified-user+plus-lifetime)
            email="e2e+$run_id-$(tr '[:upper:]' '[:lower:]' <<< "$name")@example.com"
            verified=$([[ "$seed" == verified-user* ]] && echo true || echo false)
            if ! seed_account "$email" "$verified"; then
                echo "FAIL $name (seeding $seed failed)"; failed=1; continue
            fi
            env_args+=(-e "E2E_EMAIL=$email" -e "E2E_PASSWORD=$PASSWORD")
            case "$seed" in
                *+plus) grant_args=(30 0) ;;
                *+plus-lifetime) grant_args=(0 0) ;;
                *+plus-grace) grant_args=(30 1) ;;
                *) grant_args=() ;;
            esac
            if [[ ${#grant_args[@]} -gt 0 ]] && ! grant_plus "$email" "${grant_args[@]}"; then
                echo "FAIL $name (granting Plus failed)"; failed=1; continue
            fi
            if [[ -n "$redeem_kind" ]]; then
                if ! code=$(seed_redeem_code "$email" "$redeem_kind" "$run_id" "$name") || [[ -z "$code" ]]; then
                    echo "FAIL $name (seeding $redeem_kind failed)"; failed=1; continue
                fi
                env_args+=(-e "E2E_CODE=$code")
            fi
            if [[ "$want_partner" == true ]]; then
                partner_email="e2e+$run_id-$(tr '[:upper:]' '[:lower:]' <<< "$name")-partner@example.com"
                if ! seed_account "$partner_email" true || ! grant_plus "$partner_email" 30 0; then
                    echo "FAIL $name (seeding the +partner account failed)"; failed=1; continue
                fi
                env_args+=(-e "E2E_PARTNER_EMAIL=$partner_email" -e "E2E_PARTNER_PASSWORD=$PASSWORD")
            fi
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
    if ! run_maestro "$flow" "$name"; then
        echo "FAIL $name (log: ${out_dir#"$REPO_ROOT"/}/$name.log, debug: ${out_dir#"$REPO_ROOT"/}/debug/$name)"
        failed=1
        network_on
        continue
    fi
    part_ok=1
    part_no=1
    for part in "${later_parts[@]}"; do
        part_no=$((part_no + 1))
        between=$(grep -m1 -oE '^# e2e-between: *[a-z-]+' "$part" | sed -E 's/^# e2e-between: *//' || true)
        case "${between:-none}" in
            none) ;;
            grant-plus)
                if ! grant_plus "$email"; then
                    echo "FAIL $name (granting Plus before part $part_no failed)"; part_ok=0; break
                fi
                ;;
            network-off) network_off ;;
            network-on) network_on ;;
            *) echo "FAIL $name (unknown e2e-between '$between')"; part_ok=0; break ;;
        esac
        # No reset: each later part continues in the same app session.
        if ! run_maestro "$part" "$name.part$part_no"; then
            echo "FAIL $name part $part_no (log: ${out_dir#"$REPO_ROOT"/}/$name.part$part_no.log, debug: ${out_dir#"$REPO_ROOT"/}/debug/$name.part$part_no)"
            part_ok=0
            break
        fi
    done
    network_on
    if [[ $part_ok -eq 1 ]]; then
        if [[ ${#later_parts[@]} -gt 0 ]]; then echo "PASS $name ($part_no parts)"; else echo "PASS $name"; fi
    else
        failed=1
    fi
done

exit $failed
