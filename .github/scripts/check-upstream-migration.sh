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

OURS="${1:-ingot:ci}"
UPSTREAM="${2:-dzikoysk/reposilite:3.5.28}"
WORK="$(mktemp -d)"
DATA="$WORK/data"
PIPE="$WORK/console"
HOLDER=""

cleanup() {
    docker rm -f upstream-run ours-run rollback-run > /dev/null 2>&1 || true
    [ -n "$HOLDER" ] && kill "$HOLDER" 2>/dev/null || true
    docker run --rm --user 0:0 -v "$WORK:/w" --entrypoint rm "$OURS" -rf /w/data > /dev/null 2>&1 || true
    rm -rf "$WORK"
}
trap cleanup EXIT

fail() { echo "FAIL: $*" >&2; exit 1; }

wait_http() {
    local url="$1" name="$2"
    for _ in $(seq 1 60); do
        curl -fsS "$url" -o /dev/null 2>/dev/null && return 0
        sleep 2
    done
    docker logs "$name" >&2 || true
    fail "$name never answered on $url"
}

mkdir -p "$DATA"
mkfifo "$PIPE"

echo "### 1. Upstream $UPSTREAM creates the state"
sleep infinity > "$PIPE" &
HOLDER=$!
docker run -i --name upstream-run -p 18150:8080 -v "$DATA:/app/data" "$UPSTREAM" < "$PIPE" > "$WORK/upstream.log" 2>&1 &
wait_http "http://127.0.0.1:18150/" upstream-run

echo "--> generating an access token through the interactive console"
echo 'token-generate ci-user m' > "$PIPE"
for _ in $(seq 1 20); do
    grep -q "Generated new access token for ci-user" "$WORK/upstream.log" && break
    sleep 1
done
# The secret is printed on the line after the announcement, behind the log prefix.
SECRET="$(grep -A1 "Generated new access token for ci-user" "$WORK/upstream.log" | tail -1 | sed 's/.*INFO | //' | tr -d '\r')"
[ -n "$SECRET" ] || { tail -20 "$WORK/upstream.log" >&2; fail "no token was generated"; }
echo "--> token secret captured"

echo "--> granting it a write route"
# The management permission alone does not grant repository writes; a route does.
echo 'route-add ci-user / rw' > "$PIPE"
for _ in $(seq 1 20); do
    grep -q "Route .* has been" "$WORK/upstream.log" && break
    sleep 1
done

echo "--> publishing an artifact as that token"
echo "upstream artifact payload" > "$WORK/demo.jar"
curl -fsS -u "ci-user:$SECRET" -X PUT --data-binary "@$WORK/demo.jar" \
    "http://127.0.0.1:18150/releases/com/example/demo/1.0.0/demo-1.0.0.jar" -o /dev/null \
    || { tail -25 "$WORK/upstream.log" >&2; fail "the upstream image refused the upload"; }

UPSTREAM_BODY="$(curl -fsS "http://127.0.0.1:18150/releases/com/example/demo/1.0.0/demo-1.0.0.jar")"
[ "$UPSTREAM_BODY" = "upstream artifact payload" ] || fail "upstream did not serve back what was uploaded"

echo "--> recording the configuration as upstream left it"
docker run --rm --user 0:0 -v "$DATA:/d" --entrypoint cat "$OURS" /d/configuration.cdn > "$WORK/config.before"
docker run --rm --user 0:0 -v "$DATA:/d" --entrypoint ls "$OURS" -la /d > "$WORK/listing.before"
OWNER_BEFORE="$(docker run --rm --user 0:0 -v "$DATA:/d" --entrypoint stat "$OURS" -c '%u:%g' /d/reposilite.db)"
echo "--> upstream wrote its database as $OWNER_BEFORE"

docker stop upstream-run > /dev/null
docker rm upstream-run > /dev/null

echo
echo "### 2. Our image takes over the same directory"
docker run -d --name ours-run -p 18151:8080 -v "$DATA:/app/data" "$OURS" > /dev/null
wait_http "http://127.0.0.1:18151/" ours-run

echo "--> the artifact published under upstream is still served"
OURS_BODY="$(curl -fsS "http://127.0.0.1:18151/releases/com/example/demo/1.0.0/demo-1.0.0.jar")"
[ "$OURS_BODY" = "upstream artifact payload" ] || fail "the artifact did not survive the switch"

echo "--> the token generated under upstream still authenticates"
echo "artifact published after the switch" > "$WORK/demo2.jar"
curl -fsS -u "ci-user:$SECRET" -X PUT --data-binary "@$WORK/demo2.jar" \
    "http://127.0.0.1:18151/releases/com/example/demo/2.0.0/demo-2.0.0.jar" -o /dev/null \
    || fail "the token from the upstream database was rejected"

echo "--> it did not change a single configured value"
# Both products rewrite this file on startup, so the header comments end up branded
# differently and that is expected. What must never differ is a setting: a changed value
# would silently reconfigure an instance that only meant to change its image.
docker run --rm --user 0:0 -v "$DATA:/d" --entrypoint cat "$OURS" /d/configuration.cdn > "$WORK/config.after"
settings() { grep -vE '^\s*(#|$)' "$1"; }
if ! diff -u <(settings "$WORK/config.before") <(settings "$WORK/config.after") > "$WORK/config.diff"; then
    echo "    settings changed:" >&2
    sed 's/^/      /' "$WORK/config.diff" >&2
    fail "our image rewrote configured values, not just its own comments"
fi

echo "--> it writes the same log files upstream did"
# /var/log/reposilite is part of the contract: log shippers and volume mounts point at it.
LOGFILES="$(docker exec ours-run sh -c 'ls /var/log/reposilite/ 2>/dev/null | wc -l')"
[ "$LOGFILES" -ge 2 ] || fail "only $LOGFILES files in /var/log/reposilite, upstream writes latest.log and a dated one"
docker exec ours-run sh -c 'test -s /var/log/reposilite/latest.log' || fail "latest.log is empty"
if docker logs ours-run 2>&1 | grep -q "LOGGER ERROR"; then
    docker logs ours-run 2>&1 | grep "LOGGER ERROR" | head -3 >&2
    fail "the logging backend reported an error"
fi

echo "--> no schema migration or error in the log"
if docker logs ours-run 2>&1 | grep -iE "ERROR|exception|migrat" | grep -v "no errors" | head -5 | grep -q .; then
    docker logs ours-run 2>&1 | grep -iE "ERROR|exception|migrat" | head -10 >&2
    fail "our image logged errors while adopting the directory"
fi

docker stop ours-run > /dev/null
docker rm ours-run > /dev/null

echo
echo "### 3. Rollback: upstream takes the directory back"
docker run -d --name rollback-run -p 18152:8080 -v "$DATA:/app/data" "$UPSTREAM" > /dev/null
wait_http "http://127.0.0.1:18152/" rollback-run

echo "--> upstream still serves the artifact published under our image"
BACK_BODY="$(curl -fsS "http://127.0.0.1:18152/releases/com/example/demo/2.0.0/demo-2.0.0.jar")"
[ "$BACK_BODY" = "artifact published after the switch" ] || fail "upstream cannot read what our image wrote"

echo "--> the token still works on upstream too"
curl -fsS -u "ci-user:$SECRET" "http://127.0.0.1:18152/api/maven/details/releases/com/example/demo" -o /dev/null \
    || fail "upstream rejected the token after the round trip"

echo
echo "ALL MIGRATION CHECKS PASSED: upstream -> ingot -> upstream, no data touched."
