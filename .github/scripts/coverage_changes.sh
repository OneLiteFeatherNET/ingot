#!/usr/bin/env bash
# Decides whether a set of changed paths can affect the backend coverage run.
#
# Usage:
#   coverage_changes.sh < changed-paths.txt
#
# Reads one changed path per line on stdin and prints exactly one line on stdout:
#   backend=true   when at least one path is not on the deny list below
#   backend=false  when every path is on the deny list, or the list is empty
#
# The deny list is short on purpose and is a deny list, not an allow list: anything it does not name
# counts as relevant. The backend jar bundles the dashboard, and characterisation tests read files
# outside the Java sources, so guessing wrong in the skip direction would hide a real regression.
# Paths that are skipped are only documentation, OpenSpec proposals and repository metadata.
#
# The skipped paths are echoed to stderr so the workflow log shows what was ignored.
# Requires bash and grep.

set -euo pipefail

DENIED='^(docs/|openspec/|\.github/ISSUE_TEMPLATE/|\.well-known/)|\.md$|^(\.github/CODEOWNERS|\.github/pull_request_template\.md|\.clomonitor\.yml|LICENSE|NOTICE)$'

# grep -v prints the paths that are NOT on the deny list. An empty result is not an error here.
relevant="$(grep -v '^$' | grep -vE "$DENIED" || true)"

if [ -n "$relevant" ]; then
  echo "Paths that need the coverage run:" >&2
  printf '%s\n' "$relevant" | sed 's/^/  /' >&2
  echo "backend=true"
else
  echo "Only documentation or repository metadata changed, skipping the coverage run." >&2
  echo "backend=false"
fi
