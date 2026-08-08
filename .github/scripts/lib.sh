#!/usr/bin/env bash
#
# Shared helpers for the container checks in this directory.
#
# Source it, do not execute it:
#     # shellcheck source=./lib.sh
#     source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
#
# It gives every check the same start: a temporary working directory, containers that are
# torn down however the script exits, host ports that cannot collide with a parallel run,
# and one way of waiting for a server instead of three slightly different loops.

set -euo pipefail

# Unique per run, so two checks, or two branches on one runner, never fight over a name.
readonly RUN_ID="$$"
readonly RUN_LABEL="ingot-check=$RUN_ID"
WORKDIR="$(mktemp -d)"
readonly WORKDIR

# Which image can delete files a root-owned server left behind. Set with
# use_image_for_cleanup, empty when the check never runs anything as root.
_ROOT_OWNED_IMAGE=""

fail() {
    echo "FAIL: $*" >&2
    exit 1
}

# Containers are found by label rather than by a shell array. Names collected in a variable
# do not survive a command substitution, and every one of these helpers is naturally called
# from one, so the array silently stayed empty and containers outlived the run.
_cleanup() {
    local status=$? ids
    ids="$(docker ps -aq --filter "label=$RUN_LABEL" 2>/dev/null || true)"
    if [ -n "$ids" ]; then
        # shellcheck disable=SC2086  # word splitting is what turns the list into arguments
        docker rm -f $ids > /dev/null 2>&1 || true
    fi
    # Files written by a server running as root cannot be deleted by this user, so they go
    # the same way they arrived. This needs a shell: rm refuses to act on a bare "." and
    # there is no globbing without one.
    if [ -n "$_ROOT_OWNED_IMAGE" ]; then
        docker run --rm --user 0:0 -v "$WORKDIR:/workdir" --entrypoint sh \
            "$_ROOT_OWNED_IMAGE" -c 'rm -rf /workdir/..?* /workdir/.[!.]* /workdir/*' \
            > /dev/null 2>&1 || true
    fi
    rm -rf "$WORKDIR"
    exit "$status"
}
trap _cleanup EXIT

use_image_for_cleanup() {
    _ROOT_OWNED_IMAGE="$1"
}

# container_name <role>
#
# The name is derived from the role rather than returned by start_container, so callers
# never have to capture it and can stay out of subshells entirely.
container_name() {
    echo "ingot-check-${1}-${RUN_ID}"
}

# start_container <role> <image> [docker run args...]
#
# To reach it over HTTP, pass `-p 127.0.0.1::<port>` and read the address back with
# container_url. Letting Docker choose the host port is the only way to be sure it is free.
start_container() {
    local role="$1" image="$2"
    shift 2
    local name
    name="$(container_name "$role")"
    docker rm -f "$name" > /dev/null 2>&1 || true
    docker run -d --name "$name" --label "$RUN_LABEL" "$@" "$image" > /dev/null
}

# start_container_with_console <role> <image> [docker run args...]
#
# Same, but keeps stdin attached to a fifo so console_send can drive the interactive
# console, and captures the container's output in console_log.
#
# The first access token can only be created through that console, which reads from the
# container's stdin. A detached container has stdin open with nothing attached, and closing
# it after one command ends the session, so the write end is held open by a writer that
# outlives every command.
start_container_with_console() {
    local role="$1" image="$2"
    shift 2
    local name
    name="$(container_name "$role")"
    CONSOLE_PIPE="$WORKDIR/${role}.fifo"
    CONSOLE_LOG="$WORKDIR/${role}.log"
    mkfifo "$CONSOLE_PIPE"
    # Held open for the lifetime of the run, and reaped by the label cleanup's sibling: the
    # writer exits on its own when the fifo goes with WORKDIR.
    sleep 86400 > "$CONSOLE_PIPE" &
    CONSOLE_HOLDER=$!

    docker rm -f "$name" > /dev/null 2>&1 || true
    docker run -i --name "$name" --label "$RUN_LABEL" "$@" "$image" \
        < "$CONSOLE_PIPE" > "$CONSOLE_LOG" 2>&1 &

    local deadline=$(( SECONDS + 60 ))
    while (( SECONDS < deadline )); do
        [ -n "$(docker ps -q --filter "name=^${name}$")" ] && return 0
        sleep 1
    done
    fail "$name never started"
}

console_send() {
    echo "$1" > "$CONSOLE_PIPE"
}

console_stop() {
    [ -n "${CONSOLE_HOLDER:-}" ] && kill "$CONSOLE_HOLDER" 2>/dev/null || true
    CONSOLE_HOLDER=""
}

stop_container() {
    docker rm -f "$(container_name "$1")" > /dev/null 2>&1 || true
}

# container_url <role> [path] [container-port]
#
# Where the container actually ended up, now that the host port was chosen by Docker. The
# container port defaults to 8080 but is not always that: a check passing
# REPOSILITE_OPTS="--port 8123" has to publish and ask for 8123, or it waits on a port
# nothing is listening on.
container_url() {
    local name port hostport
    name="$(container_name "$1")"
    port="${3:-8080}"
    hostport="$(docker port "$name" "$port/tcp" | head -1)"
    [ -n "$hostport" ] || fail "$name published no host port for container port $port"
    echo "http://127.0.0.1:${hostport##*:}${2:-/}"
}

# wait_for_http <url> <role> [seconds]
#
# Gives up with the container's own logs rather than a bare timeout, and stops early when
# the container is already gone, which is the common failure and used to cost the full wait.
wait_for_http() {
    local url="$1" name port_check
    name="$(container_name "$2")"
    local deadline=$(( SECONDS + ${3:-120} ))
    while (( SECONDS < deadline )); do
        if curl -fsS "$url" -o /dev/null 2>/dev/null; then
            return 0
        fi
        port_check="$(docker ps -q --filter "name=^${name}$")"
        if [ -z "$port_check" ]; then
            docker logs "$name" >&2 2>/dev/null || true
            fail "$name exited before it answered on $url"
        fi
        sleep 2
    done
    docker logs "$name" >&2 2>/dev/null || true
    fail "$name did not answer on $url in time"
}

# wait_for_log <file> <pattern> [seconds]
wait_for_log() {
    local file="$1" pattern="$2"
    local deadline=$(( SECONDS + ${3:-30} ))
    while (( SECONDS < deadline )); do
        grep -q "$pattern" "$file" 2>/dev/null && return 0
        sleep 1
    done
    tail -25 "$file" >&2 2>/dev/null || true
    fail "waited for '$pattern' and it never appeared"
}

# in_image <image> [docker run args...] -- <entrypoint> [args...]
#
# Runs one tool from the image under test against a mounted directory, as root.
#
# Reading and chowning the data directory needs root and tools a host may not have, and the
# ids involved belong to nobody on the host. Borrowing the image under test avoids both a
# sudo prompt and a second image to pull. Root is pinned deliberately: what the image starts
# as is itself under test, and a helper inheriting that would turn a failed assertion into a
# confusing setup error.
in_image() {
    local image="$1"
    shift
    local args=()
    while [ "$#" -gt 0 ] && [ "$1" != "--" ]; do
        args+=("$1")
        shift
    done
    [ "${1:-}" = "--" ] || fail "in_image: missing -- before the command"
    shift
    local entrypoint="$1"
    shift
    docker run --rm --user 0:0 --label "$RUN_LABEL" "${args[@]}" \
        --entrypoint "$entrypoint" "$image" "$@"
}
