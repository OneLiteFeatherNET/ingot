#!/usr/bin/env bash
# Decides which coverage jobs a set of changed paths needs.
#
# Usage:
#   coverage_changes.sh < changed-paths.txt
#
# Reads one changed path per line on stdin and prints exactly three lines on stdout:
#   backend=true|false       the backend coverage job has to run
#   frontend=true|false      the frontend coverage job has to run
#   frontend_only=true|false every relevant path is under reposilite-frontend/, so the backend job
#                            runs only the tests that serve the dashboard
#
# backend is true when at least one path is not on the deny list below. frontend is true when at least
# one relevant path is under reposilite-frontend/. frontend_only is true when relevant paths exist and all
# of them are under reposilite-frontend/. An empty change list is false for all three.
#
# The deny list is short on purpose and is a deny list, not an allow list: anything it does not name
# counts as relevant. The backend jar bundles the dashboard, and characterisation tests read files
# outside the Java sources, so guessing wrong in the skip direction would hide a real regression.
# Paths that are skipped are only documentation, OpenSpec proposals and repository metadata.
#
# A path that git quotes (non-ASCII names, for example "reposilite-frontend/\303\244.vue") does not match
# the frontend prefix, so it counts as backend and the full run is kept. That is the fail-open direction.
#
# The skipped paths are echoed to stderr so the workflow log shows what was ignored.
# Requires bash and grep.

set -euo pipefail

DENIED='^(docs/|openspec/|\.github/ISSUE_TEMPLATE/|\.well-known/)|\.md$|^(\.github/CODEOWNERS|\.github/pull_request_template\.md|\.clomonitor\.yml|LICENSE|NOTICE)$'
FRONTEND='^reposilite-frontend/'

# grep -v prints the paths that are NOT on the deny list. An empty result is not an error here.
relevant="$(grep -v '^$' | grep -vE "$DENIED" || true)"

if [ -z "$relevant" ]; then
  echo "Only documentation or repository metadata changed, skipping the coverage jobs." >&2
  printf '%s\n' "backend=false" "frontend=false" "frontend_only=false"
  exit 0
fi

echo "Paths that need the coverage run:" >&2
printf '%s\n' "$relevant" | sed 's/^/  /' >&2

frontend="$(printf '%s\n' "$relevant" | grep -E "$FRONTEND" || true)"
outside="$(printf '%s\n' "$relevant" | grep -vE "$FRONTEND" || true)"

if [ -n "$frontend" ]; then frontend_flag=true; else frontend_flag=false; fi
if [ -n "$outside" ]; then frontend_only=false; else frontend_only=true; fi

printf '%s\n' "backend=true" "frontend=$frontend_flag" "frontend_only=$frontend_only"
