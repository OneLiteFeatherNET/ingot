#!/usr/bin/env bash
#
# Starts the dashboard image the way it is meant to be run and checks the things a build
# cannot tell you. Both real bugs this image has had were of that shape: an nginx that
# rendered no server block and answered nothing, and asset URLs that never resolved.
#
# It is started under the full hardening the compose file applies, so a change that quietly
# needs a writable filesystem or a capability fails here rather than in someone's cluster.
#
# Usage: .github/scripts/check-dashboard-image.sh [image-tag]

set -euo pipefail

IMAGE="${1:-ingot-dashboard:ci}"
CONTAINER="ingot-dashboard-check"
WORKDIR="$(mktemp -d)"

cleanup() {
    docker rm -f "$CONTAINER" > /dev/null 2>&1 || true
    rm -rf "$WORKDIR"
}
trap cleanup EXIT

fail() {
    echo "FAIL: $*" >&2
    docker logs "$CONTAINER" >&2 2>/dev/null || true
    exit 1
}

echo "==> It starts read only, unprivileged and with no capabilities"
# The tmpfs mounts need the image's own uid. Without it they land owned by root, nginx
# cannot render its configuration into conf.d, and it comes up serving nothing at all
# instead of failing outright, which is the confusing failure this guards against.
docker run -d --name "$CONTAINER" -p 18140:8080 \
    --read-only \
    --tmpfs /tmp:uid=101,gid=101 \
    --tmpfs /var/cache/nginx:uid=101,gid=101 \
    --tmpfs /var/run:uid=101,gid=101 \
    --tmpfs /etc/nginx/conf.d:uid=101,gid=101 \
    --cap-drop ALL \
    --security-opt no-new-privileges:true \
    "$IMAGE" > /dev/null

for _ in $(seq 1 30); do
    if curl -fsS http://127.0.0.1:18140/ -o "$WORKDIR/index.html" 2>/dev/null; then
        break
    fi
    if [ -z "$(docker ps -q --filter "name=^${CONTAINER}$")" ]; then
        fail "the container exited before it answered"
    fi
    sleep 2
done
[ -s "$WORKDIR/index.html" ] || fail "the dashboard never answered"

echo "==> Nothing in it runs as root"
# nginx-unprivileged already does this; the assertion is here so a base image swap cannot
# take it away unnoticed.
runtime_user="$(docker exec "$CONTAINER" id -u)"
[ "$runtime_user" = "101" ] || fail "the dashboard runs as uid $runtime_user, expected 101"

echo "==> The page it serves is complete"
# An unsubstituted placeholder means the script tag points at a path that does not exist,
# and the app never starts.
if grep -q '{{REPOSILITE\.' "$WORKDIR/index.html"; then
    grep -o '{{REPOSILITE\.[A-Z_]*}}' "$WORKDIR/index.html" | sort -u >&2
    fail "index.html still contains placeholders, so its asset URLs cannot resolve"
fi

# Follow the bundle the page actually asks for, rather than assuming a name.
asset="$(grep -o 'src="[^"]*index-[^"]*\.js"' "$WORKDIR/index.html" | head -1 | cut -d'"' -f2)"
[ -n "$asset" ] || fail "the page references no bundle"
echo "    dashboard asks for $asset"
curl -fsS -o /dev/null "http://127.0.0.1:18140${asset}" || fail "the bundle $asset does not resolve"

echo "All dashboard checks passed."
