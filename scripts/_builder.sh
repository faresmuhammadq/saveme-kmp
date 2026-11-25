# Sourced by build-apk.sh and install-apk.sh: shared paths and the Docker builder image.
# shellcheck shell=bash

REPO=$(git -C "$(dirname "${BASH_SOURCE[0]}")/.." rev-parse --show-toplevel)
DOCKERFILE="$REPO/docker/android-builder.Dockerfile"
DIST="$REPO/dist"

log() { printf '[apk] %s\n' "$*"; }
die() { printf '[apk] ERROR: %s\n' "$*" >&2; exit 1; }

command -v docker >/dev/null || die "docker is required"

# The image is tagged with a hash of its Dockerfile, so editing the Dockerfile builds a fresh one
# and an unchanged Dockerfile reuses the existing image.
builder_image() {
    local tag image
    tag=$(sha256sum "$DOCKERFILE" | cut -c1-12)
    image="saveme-android-builder:$tag"
    if ! docker image inspect "$image" >/dev/null 2>&1; then
        log "building the builder image $image (first time: downloads the JDK and Android SDK)" >&2
        docker build -t "$image" -t saveme-android-builder:latest \
            --build-arg "UID=$(id -u)" --build-arg "GID=$(id -g)" \
            -f "$DOCKERFILE" "$REPO/docker" >&2
    fi
    printf '%s' "$image"
}
