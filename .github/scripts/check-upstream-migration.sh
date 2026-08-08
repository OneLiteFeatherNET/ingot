#!/usr/bin/env bash
#
# Upgrade rehearsal against the real upstream image.
#
# check-server-image.sh proves the image honours the contract on an empty directory. This
# one proves the thing operators actually care about: that an existing Reposilite instance
# can be pointed at Ingot without losing anything. It runs the upstream image, produces the
# state a real instance has (database, generated token, published artifact, written
# configuration), hands that directory to our image, and checks what survived.
#
# It ends by handing the directory back to upstream, because "no risk" also means the
# rollback works. Anything this catches is a change nobody could have found by reading a
# diff: the tinylog writer regression it was written for built cleanly, started cleanly and
# served artifacts, and silently wrote no log files at all.
#
# Usage: .github/scripts/check-upstream-migration.sh [our-image-tag] [upstream-image-tag]

set -euo pipefail
# shellcheck source=./lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

OURS="${1:-ingot:ci}"
UPSTREAM="${2:-dzikoysk/reposilite:3.5.28}"
use_image_for_cleanup "$OURS"

DATA="$WORKDIR/data"
mkdir -p "$DATA"

echo "### 1. Upstream $UPSTREAM creates the state"
start_container_with_console upstream "$UPSTREAM" -p 127.0.0.1::8080 -v "$DATA:/app/data"
upstream_url="$(container_url upstream)"
wait_for_http "$upstream_url" upstream

echo "--> generating an access token through the interactive console"
console_send 'token-generate ci-user m'
wait_for_log "$CONSOLE_LOG" "Generated new access token for ci-user"
# The secret is printed on its own line below the announcement, behind the log prefix.
SECRET="$(grep -A1 "Generated new access token for ci-user" "$CONSOLE_LOG" \
    | tail -1 | sed 's/.*INFO | //' | tr -d '\r')"
[ -n "$SECRET" ] || { tail -20 "$CONSOLE_LOG" >&2; fail "no token secret was printed"; }
echo "--> token secret captured"

echo "--> granting it a write route"
# The management permission alone does not grant repository writes; a route does.
console_send 'route-add ci-user / rw'
wait_for_log "$CONSOLE_LOG" "Route .* has been added to token ci-user"

echo "--> publishing an artifact as that token"
echo "upstream artifact payload" > "$WORKDIR/demo.jar"
artifact_v1="releases/com/example/demo/1.0.0/demo-1.0.0.jar"
curl -fsS -u "ci-user:$SECRET" -X PUT --data-binary "@$WORKDIR/demo.jar" \
    "${upstream_url}${artifact_v1}" -o /dev/null \
    || { tail -25 "$CONSOLE_LOG" >&2; fail "the upstream image refused the upload"; }

body="$(curl -fsS "${upstream_url}${artifact_v1}")"
[ "$body" = "upstream artifact payload" ] || fail "upstream did not serve back what was uploaded"

echo "--> recording the configuration as upstream left it"
in_image "$OURS" -v "$DATA:/mnt/data" -- cat /mnt/data/configuration.cdn > "$WORKDIR/config.before"
owner="$(in_image "$OURS" -v "$DATA:/mnt/data" -- stat -c '%u:%g' /mnt/data/reposilite.db)"
echo "--> upstream wrote its database as $owner"

console_stop
stop_container upstream

echo
echo "### 2. Our image takes over the same directory"
start_container ours "$OURS" -p 127.0.0.1::8080 -v "$DATA:/app/data"
ours_url="$(container_url ours)"
wait_for_http "$ours_url" ours

echo "--> the artifact published under upstream is still served"
body="$(curl -fsS "${ours_url}${artifact_v1}")"
[ "$body" = "upstream artifact payload" ] || fail "the artifact did not survive the switch"

echo "--> the token generated under upstream still authenticates"
artifact_v2="releases/com/example/demo/2.0.0/demo-2.0.0.jar"
echo "artifact published after the switch" > "$WORKDIR/demo2.jar"
curl -fsS -u "ci-user:$SECRET" -X PUT --data-binary "@$WORKDIR/demo2.jar" \
    "${ours_url}${artifact_v2}" -o /dev/null \
    || fail "the token from the upstream database was rejected"

echo "--> it did not change a single configured value"
# Both products rewrite this file on startup, so the header comments end up branded
# differently and that is expected. What must never differ is a setting: a changed value
# would silently reconfigure an instance that only meant to change its image.
in_image "$OURS" -v "$DATA:/mnt/data" -- cat /mnt/data/configuration.cdn > "$WORKDIR/config.after"
settings() { grep -vE '^\s*(#|$)' "$1"; }
if ! diff -u <(settings "$WORKDIR/config.before") <(settings "$WORKDIR/config.after") > "$WORKDIR/config.diff"; then
    sed 's/^/      /' "$WORKDIR/config.diff" >&2
    fail "our image rewrote configured values, not just its own comments"
fi

echo "--> it writes the same log files upstream did"
# /var/log/reposilite is part of the contract: log shippers and volume mounts point at it.
logfiles="$(docker exec "$(container_name ours)" sh -c 'ls /var/log/reposilite/ 2>/dev/null | wc -l')"
[ "$logfiles" -ge 2 ] || fail "only $logfiles files in /var/log/reposilite, upstream writes latest.log and a dated one"
docker exec "$(container_name ours)" test -s /var/log/reposilite/latest.log || fail "latest.log is empty"
if docker logs "$(container_name ours)" 2>&1 | grep -q "LOGGER ERROR"; then
    docker logs "$(container_name ours)" 2>&1 | grep "LOGGER ERROR" | head -3 >&2
    fail "the logging backend reported an error"
fi

echo "--> no error while adopting the directory"
if docker logs "$(container_name ours)" 2>&1 | grep -iE "ERROR|exception" | head -5 | grep -q .; then
    docker logs "$(container_name ours)" 2>&1 | grep -iE "ERROR|exception" | head -10 >&2
    fail "our image logged errors while adopting the directory"
fi

stop_container ours

echo
echo "### 3. Rollback: upstream takes the directory back"
start_container rollback "$UPSTREAM" -p 127.0.0.1::8080 -v "$DATA:/app/data"
rollback_url="$(container_url rollback)"
wait_for_http "$rollback_url" rollback

echo "--> upstream still serves the artifact published under our image"
body="$(curl -fsS "${rollback_url}${artifact_v2}")"
[ "$body" = "artifact published after the switch" ] || fail "upstream cannot read what our image wrote"

echo "--> the token still works on upstream too"
curl -fsS -u "ci-user:$SECRET" "${rollback_url}api/maven/details/releases/com/example/demo" -o /dev/null \
    || fail "upstream rejected the token after the round trip"

echo
echo "ALL MIGRATION CHECKS PASSED: upstream -> ingot -> upstream, no data touched."
