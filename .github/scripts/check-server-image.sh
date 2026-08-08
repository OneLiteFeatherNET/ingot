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
# shellcheck source=./lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

IMAGE="${1:-ingot:ci}"
use_image_for_cleanup "$IMAGE"

echo "==> The jar is reachable under its upstream name"
# A deployment that overrides entrypoint or command names the jar itself.
resolved="$(in_image "$IMAGE" -- readlink -f /app/reposilite.jar)"
[ "$resolved" = "/app/ingot.jar" ] || fail "/app/reposilite.jar resolves to '$resolved'"

echo "==> uid and gid 1000 are free for PUID and PGID"
# The base image ships an `ubuntu` account holding them, and 1000 is the most common PUID
# there is. Occupied means the entrypoint dies on "id already in use".
if in_image "$IMAGE" -- getent passwd 1000 > /dev/null 2>&1; then
    fail "uid 1000 is taken inside the image, so PUID=1000 cannot work"
fi

echo "==> A root start adopts a foreign-owned volume and honours PUID, PGID and REPOSILITE_OPTS"
# This is the upgrade path: an existing data directory, owned by whoever owned it before,
# handed to the image unchanged. Only the root entrypoint can take it over.
data="$WORKDIR/data"
mkdir -p "$data"
in_image "$IMAGE" -v "$data:/mnt/data" -- chown -R 4242:4242 /mnt/data

start_container server "$IMAGE" \
    -p 127.0.0.1::8123 \
    -v "$data:/app/data" \
    -e PUID=1000 \
    -e PGID=1000 \
    -e REPOSILITE_OPTS="--port 8123"
# The port published here is the one REPOSILITE_OPTS asks the server to listen on, and it
# is not the image default. An answer therefore proves the pre-Ingot spelling of the
# variable was read: had it been ignored, the server would be on 8080 and this port dead.
url="$(container_url server / 8123)"
wait_for_http "$url" server

owner="$(in_image "$IMAGE" -v "$data:/mnt/data" -- stat -c '%u:%g' /mnt/data/configuration.cdn)"
[ "$owner" = "1000:1000" ] || fail "the server wrote its configuration as $owner, so PUID/PGID were ignored"

echo "==> The server still serves the dashboard itself"
# The split-container work must not have turned the default deployment into one that needs
# a second image to show anything.
page="$WORKDIR/index.html"
curl -fsS "$url" -o "$page"
grep -q '<div id="app"' "$page" || fail "the response is not the dashboard: $(head -c 200 "$page")"
if grep -q '{{REPOSILITE\.' "$page"; then
    grep -o '{{REPOSILITE\.[A-Z_]*}}' "$page" | sort -u >&2
    fail "the dashboard still contains placeholders, so its asset URLs cannot resolve"
fi

asset="$(grep -o 'src="[^"]*index-[^"]*\.js"' "$page" | head -1 | cut -d'"' -f2)"
[ -n "$asset" ] || fail "the dashboard references no bundle"
curl -fsS -o /dev/null "${url%/}${asset}" || fail "the dashboard bundle $asset does not resolve"

stop_container server

echo "==> An unprivileged start still works"
# Not part of the upstream contract, but the reason the image ships the account and the
# recommended setup for a volume that is already owned correctly.
unpriv_data="$WORKDIR/data-unprivileged"
mkdir -p "$unpriv_data"
in_image "$IMAGE" -v "$unpriv_data:/mnt/data" -- chown -R 977:977 /mnt/data

start_container unprivileged "$IMAGE" \
    --user 977:977 \
    -p 127.0.0.1::8080 \
    -v "$unpriv_data:/app/data" \
    --security-opt no-new-privileges:true
wait_for_http "$(container_url unprivileged)" unprivileged

echo "All drop-in checks passed."
