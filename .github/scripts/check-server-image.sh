#!/usr/bin/env bash
#
# Asserts that the server image is still a drop-in replacement for the upstream Reposilite
# image, which is a hard rule rather than a goal: pointing an existing deployment at Ingot
# has to cost one changed image reference and nothing else.
#
# Every check below stands for a deployment that used to work and would break silently. A
# build that succeeds proves only that the Dockerfile parses; all of these fail at runtime.
#
# Usage: .github/scripts/check-server-image.sh [image-tag]

set -euo pipefail

IMAGE="${1:-ingot:ci}"
WORKDIR="$(mktemp -d)"
CONTAINERS=()

cleanup() {
    for container in "${CONTAINERS[@]:-}"; do
        [ -n "$container" ] || continue
        docker rm -f "$container" > /dev/null 2>&1 || true
    done
    # The data directories belong to ids this user does not have, so they are removed the
    # same way they were created.
    in_image -v "$WORKDIR:/mnt/work" --entrypoint rm "$IMAGE" -rf /mnt/work/data-root /mnt/work/data-unprivileged > /dev/null 2>&1 || true
    rm -rf "$WORKDIR"
}
trap cleanup EXIT

fail() {
    echo "FAIL: $*" >&2
    exit 1
}

# Setup and inspection run inside the image, so the checks need no tooling on the host and
# no sudo to touch directories owned by ids this user does not have. They are pinned to root
# rather than left on the image default: what the image starts as is under test further
# down, and a helper that inherits it turns a failing assertion into a confusing setup
# error.
in_image() {
    docker run --rm --user 0:0 "$@"
}

# Waits for the server to answer, and gives up with logs rather than a bare timeout.
wait_for_http() {
    local url="$1" container="$2"
    for _ in $(seq 1 60); do
        if curl -fsS "$url" -o "$WORKDIR/response.html" 2>/dev/null; then
            return 0
        fi
        if [ -z "$(docker ps -q --filter "name=^${container}$")" ]; then
            docker logs "$container" >&2 || true
            fail "$container exited before it answered on $url"
        fi
        sleep 2
    done
    docker logs "$container" >&2 || true
    fail "$container never answered on $url"
}

echo "==> The jar is reachable under its upstream name"
# A deployment that overrides entrypoint or command names the jar itself.
resolved="$(in_image --entrypoint readlink "$IMAGE" -f /app/reposilite.jar)"
[ "$resolved" = "/app/ingot.jar" ] || fail "/app/reposilite.jar resolves to '$resolved'"

echo "==> uid and gid 1000 are free for PUID and PGID"
# The base image ships an `ubuntu` account holding them, and 1000 is the most common PUID
# there is. Occupied means the entrypoint dies on "id already in use".
if in_image --entrypoint getent "$IMAGE" passwd 1000 > /dev/null 2>&1; then
    fail "uid 1000 is taken inside the image, so PUID=1000 cannot work"
fi

echo "==> A root start adopts a foreign-owned volume and honours PUID, PGID and REPOSILITE_OPTS"
# This is the upgrade path: an existing data directory, owned by whoever owned it before,
# handed to the image unchanged. Only the root entrypoint can take it over.
mkdir -p "$WORKDIR/data-root"
in_image -v "$WORKDIR/data-root:/mnt/data" --entrypoint chown "$IMAGE" -R 4242:4242 /mnt/data

CONTAINERS+=("ingot-dropin-root")
docker run -d --name ingot-dropin-root \
    -p 18123:8123 \
    -v "$WORKDIR/data-root:/app/data" \
    -e PUID=1000 \
    -e PGID=1000 \
    -e REPOSILITE_OPTS="--port 8123" \
    "$IMAGE" > /dev/null

# The port only answers if the pre-Ingot spelling of the options variable was read.
wait_for_http "http://127.0.0.1:18123/" ingot-dropin-root

owner="$(in_image -v "$WORKDIR/data-root:/mnt/data" --entrypoint stat "$IMAGE" -c '%u:%g' /mnt/data/configuration.cdn)"
[ "$owner" = "1000:1000" ] || fail "the server wrote its configuration as $owner, so PUID/PGID were ignored"

echo "==> The server still serves the dashboard itself"
# The split-container work must not have turned the default deployment into one that needs
# a second image to show anything.
grep -qi "<div id=\"app\"" "$WORKDIR/response.html" || fail "the response is not the dashboard: $(head -c 200 "$WORKDIR/response.html")"
if grep -q '{{REPOSILITE\.' "$WORKDIR/response.html"; then
    grep -o '{{REPOSILITE\.[A-Z_]*}}' "$WORKDIR/response.html" | sort -u >&2
    fail "the dashboard still contains placeholders, so its asset URLs cannot resolve"
fi

asset="$(grep -o 'src="[^"]*index-[^"]*\.js"' "$WORKDIR/response.html" | head -1 | cut -d'"' -f2)"
[ -n "$asset" ] || fail "the dashboard references no bundle"
curl -fsS -o /dev/null "http://127.0.0.1:18123${asset}" || fail "the dashboard bundle $asset does not resolve"

docker rm -f ingot-dropin-root > /dev/null

echo "==> An unprivileged start still works"
# Not part of the upstream contract, but the reason the image ships the account and the
# recommended setup for a volume that is already owned correctly.
mkdir -p "$WORKDIR/data-unprivileged"
in_image -v "$WORKDIR/data-unprivileged:/mnt/data" --entrypoint chown "$IMAGE" -R 977:977 /mnt/data

CONTAINERS+=("ingot-dropin-unprivileged")
docker run -d --name ingot-dropin-unprivileged \
    --user 977:977 \
    -p 18124:8080 \
    -v "$WORKDIR/data-unprivileged:/app/data" \
    --security-opt no-new-privileges:true \
    "$IMAGE" > /dev/null

wait_for_http "http://127.0.0.1:18124/" ingot-dropin-unprivileged

echo "All drop-in checks passed."
