#!/usr/bin/env bash
# Decides how much of the frontend unit suite a change needs.
#
# Usage:
#   frontend_scope.sh <out-dir> < name-status.txt
#
# Reads `git diff --name-status` lines on stdin: a status letter, a TAB, then the repository-relative path.
# Prints one line on stdout:
#   frontend_scope=related|full
#
# related means `vitest related` can find every test the change reaches. It then writes two lists into
# <out-dir>, paths relative to reposilite-frontend/:
#   related.txt   the changed .js and .vue files, which vitest maps through its module graph
#   readers.txt   test files that read source text from disk (readFileSync). vitest cannot see those reads,
#                 so the workflow always runs them next to the related set.
#
# full is printed, and no list is written, whenever the change could reach a test the module graph does not
# show. The reasons are echoed to stderr. The rules, in order:
#   - no changed paths at all
#   - a deleted, renamed or copied file, since its importers may still name the old path
#   - any path outside reposilite-frontend/ (the workflow and the backend are not mapped by vitest)
#   - anything under reposilite-frontend/ that is neither src/ nor server/: package.json, the lockfile,
#     vite and vitest config, eslint, index.html, nitro, e2e and fake-api
#   - src/styles/ and src/anchors/, which tests read as text, and src/main.js, the entry point
#   - any file that is not .js or .vue (stylesheets, JSON, SVG, ...), since no import graph covers them
#
# The workflow adds two more rules that need a run to decide: a push to main runs in full, and a related run
# that reaches no test through the graph runs in full rather than nothing.
#
# Requires bash, grep and sed.

set -euo pipefail

out_dir="${1:?usage: frontend_scope.sh <out-dir> < name-status.txt}"
frontend_dir="${FRONTEND_DIR:-reposilite-frontend}"
prefix="$frontend_dir/"

mkdir -p "$out_dir"

full() {
  echo "$1, running the full frontend suite." >&2
  echo "frontend_scope=full"
  exit 0
}

related=()
seen=0
while IFS=$'\t' read -r status path _; do
  [ -z "${path:-}" ] && continue
  seen=1

  case "$status" in
    M | A) ;;
    *) full "Path $path has status $status" ;;
  esac

  case "$path" in
    "$prefix"*) ;;
    *) full "Path $path is outside $frontend_dir" ;;
  esac

  rel="${path#"$prefix"}"

  case "$rel" in
    src/* | server/*) ;;
    *) full "Path $rel is not under src or server" ;;
  esac

  case "$rel" in
    src/styles/* | src/anchors/* | src/main.js) full "Path $rel is read as a whole or is the entry point" ;;
  esac

  case "$rel" in
    *.js | *.vue) related+=("$rel") ;;
    *) full "Path $rel is not a module vitest maps" ;;
  esac
done

if [ "$seen" -eq 0 ]; then
  full "No changed paths were given"
fi

if [ "${#related[@]}" -eq 0 ]; then
  full "No changed module was found"
fi

printf '%s\n' "${related[@]}" > "$out_dir/related.txt"

# Test files that read source text from disk. Their reads are invisible to the module graph.
if [ -d "$frontend_dir/src" ] || [ -d "$frontend_dir/server" ]; then
  grep -rl --include='*.test.js' 'readFileSync' "$frontend_dir/src" "$frontend_dir/server" 2>/dev/null \
    | sed "s#^$prefix##" | sort > "$out_dir/readers.txt" || true
fi

echo "Changed modules for vitest related:" >&2
sed 's/^/  /' "$out_dir/related.txt" >&2
echo "frontend_scope=related"
