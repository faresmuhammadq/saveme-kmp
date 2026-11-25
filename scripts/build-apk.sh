#!/usr/bin/env bash
# Builds the signed release APK in Docker (the host needs only Docker and git) from a committed ref,
# never the working tree, like `lab build` does for the backend.
#
#   scripts/build-apk.sh [prod|staging] [--ref <git-ref>] [--install]
#
#   prod (default)  com.fares.saveme,         API host saveme-production.lab.internal
#   staging         com.fares.saveme.staging, API host saveme-staging.lab.internal
#   --ref           what to build (default HEAD); push first if you want the APK to match origin
#   --install       then install it over USB (scripts/install-apk.sh)
#
# Output: dist/saveme-<flavor>-<versionName>-<versionCode>-<sha7>.apk (+ -mapping.txt, for R8 stack traces).
#
# Signing: the release keystore and an env file outside the repo, by default
#   ~/mobile-keys/saveme/saveme-release.jks   (SAVEME_KEYSTORE, to override)
#   ~/mobile-keys/saveme/signing.env          (SAVEME_SIGNING_ENV, to override)
# signing.env holds SAVEME_KEYSTORE_PASSWORD=, SAVEME_KEY_ALIAS=, SAVEME_KEY_PASSWORD= (plain KEY=value lines).
# Every build is signed with the same key, so each APK installs over the previous one without losing data.
#
# Optional: PROD_API_HOST / STAGING_API_HOST change the baked-in default host (the app can still change it).
set -euo pipefail

# shellcheck source=scripts/_builder.sh
source "$(dirname "${BASH_SOURCE[0]}")/_builder.sh"

flavor=prod ref=HEAD install=0
while [[ $# -gt 0 ]]; do
    case "$1" in
        prod|staging) flavor=$1 ;;
        --ref) ref=${2:?--ref needs a git ref}; shift ;;
        --install) install=1 ;;
        -h|--help) sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) die "unknown argument: $1 (see --help)" ;;
    esac
    shift
done

keystore=${SAVEME_KEYSTORE:-$HOME/mobile-keys/saveme/saveme-release.jks}
signing_env=${SAVEME_SIGNING_ENV:-$HOME/mobile-keys/saveme/signing.env}
[[ -f $keystore ]] || die "keystore not found: $keystore"
[[ -f $signing_env ]] || die "signing env not found: $signing_env (SAVEME_KEYSTORE_PASSWORD, SAVEME_KEY_ALIAS, SAVEME_KEY_PASSWORD)"
for key in SAVEME_KEYSTORE_PASSWORD SAVEME_KEY_ALIAS SAVEME_KEY_PASSWORD; do
    grep -qE "^$key=.+" "$signing_env" || die "$key is missing or empty in $signing_env"
done

sha=$(git -C "$REPO" rev-parse --verify --quiet "$ref^{commit}") || die "cannot resolve '$ref' to a commit"
short=${sha:0:7}
# Above every CI build (GitHub: 100 + run, forge: 1000 + run), and rising with each commit.
version_code=$((2000 + $(git -C "$REPO" rev-list --count "$sha")))
version_name=$(git -C "$REPO" show "$sha:gradle.properties" | sed -n 's/^versionName=//p' | tr -d '[:space:]')
[[ $version_name =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "versionName '$version_name' in gradle.properties is not semver"
name="saveme-$flavor-$version_name-$version_code-$short"

if [[ $ref == HEAD && -n $(git -C "$REPO" status --porcelain --untracked-files=no) ]]; then
    log "warning: uncommitted changes are NOT in this build (it uses $short)"
fi

case "$flavor" in
    prod) host=${PROD_API_HOST:-} ;;
    staging) host=${STAGING_API_HOST:-} ;;
esac

image=$(builder_image)
mkdir -p "$DIST"
log "building $name from $ref ($(git -C "$REPO" log -1 --format='%s' "$sha"))"

# label=disable: the keystore and dist/ are bind-mounted without relabelling them for SELinux.
git -C "$REPO" archive --format=tar "$sha" \
    | docker run -i --rm \
        --security-opt label=disable \
        -v saveme-gradle-home:/home/builder/.gradle \
        -v "$keystore:/keys/release.jks:ro" \
        -v "$DIST:/dist" \
        --env-file "$signing_env" \
        -e SAVEME_KEYSTORE_FILE=/keys/release.jks \
        -e FLAVOR="$flavor" -e VERSION_CODE="$version_code" -e NAME="$name" -e HOST="$host" \
        "$image" bash -euo pipefail -c '
            tar -x -C /work
            task="assemble${FLAVOR^}Release"
            ./gradlew ":androidApp:$task" \
                -PversionCode="$VERSION_CODE" \
                ${HOST:+"-P${FLAVOR}ApiHost=$HOST"} \
                --no-daemon --stacktrace
            apk=$(ls androidApp/build/outputs/apk/"$FLAVOR"/release/*.apk | head -n1)
            apksigner=$(ls -d "$ANDROID_HOME"/build-tools/*/apksigner | sort -V | tail -n1)
            "$apksigner" verify --print-certs "$apk" | tee /tmp/certs.txt
            grep -q "certificate SHA-256 digest" /tmp/certs.txt || { echo "APK is not signed"; exit 1; }
            cp "$apk" "/dist/$NAME.apk"
            cp "androidApp/build/outputs/mapping/${FLAVOR}Release/mapping.txt" "/dist/$NAME-mapping.txt"
        '

log "done: dist/$name.apk"
if [[ $install -eq 1 ]]; then
    "$(dirname "${BASH_SOURCE[0]}")/install-apk.sh" "$DIST/$name.apk"
else
    log "install over USB: scripts/install-apk.sh dist/$name.apk   (or send the file to the phone)"
fi
