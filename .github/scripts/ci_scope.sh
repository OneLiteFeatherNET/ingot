#!/usr/bin/env bash
# Decides which jobs a set of changed paths needs.
#
# Usage:
#   ci_scope.sh < changed-paths.txt
#
# Reads one changed path per line on stdin and prints exactly seven lines on stdout:
#   backend=true|false       the backend coverage job has to run
#   frontend=true|false      the frontend coverage job has to run
#   frontend_only=true|false every relevant path is under reposilite-frontend/, so the backend job
#                            runs only the tests that serve the dashboard
#   image=true|false         an input of the container images changed (Dockerfiles, entrypoint, compose files)
#   chart=true|false         the Helm chart changed
#   e2e=true|false           code the dashboard end-to-end tests exercise changed (backend or frontend)
#   unit=true|false          code the unit tests compile or serve changed (anything except the chart and image inputs)
#
# image, chart, e2e and unit fail open: a relevant path the script cannot place in one of its known areas
# (an unknown root file, a path git quotes, a Gradle build file, a workflow, a workflow script, the shared test
# module) turns all four on, because the job it would have skipped might be the one that catches the change.
#
# backend is true when at least one path is not on the deny list below. frontend is true when at least
# one relevant path is under reposilite-frontend/ or is the OpenAPI document docs/openapi/ingot-openapi.json.
# frontend_only is true when relevant paths exist and all of them are under reposilite-frontend/. An empty
# change list is false for all three.
#
# The OpenAPI document is the one docs/ path that counts as relevant. The backend OpenApiCompletenessTest reads
# it, and the dashboard api types are generated from it, so a change to it runs both coverage jobs. It is not
# under reposilite-frontend/, so frontend_only stays false and the backend runs its full suite. Other files in
# docs/openapi/ (for example breaking-changes-ignore.txt) stay on the deny list. The document is not a module
# of any known area, so image, chart, e2e and unit fail open like any unknown path.
#
# The deny list is short on purpose and is a deny list, not an allow list: anything it does not name
# counts as relevant. The backend jar bundles the dashboard, and characterisation tests read files
# outside the Java sources, so guessing wrong in the skip direction would hide a real regression.
# Paths that are skipped are only documentation, OpenSpec proposals and repository metadata, except the
# OpenAPI document named above. An empty change list is false for all seven.
#
# A path that git quotes (non-ASCII names, for example "reposilite-frontend/\303\244.vue") does not match
# the frontend prefix, so it counts as backend and the full run is kept. That is the fail-open direction.
#
# The skipped paths are echoed to stderr so the workflow log shows what was ignored.
# Requires bash and grep.

set -euo pipefail

DENIED='^(docs/|openspec/|\.github/ISSUE_TEMPLATE/|\.well-known/)|\.md$|^(\.github/CODEOWNERS|\.github/pull_request_template\.md|\.clomonitor\.yml|LICENSE|NOTICE)$'
OPENAPI='^docs/openapi/ingot-openapi\.json$'
FRONTEND='^reposilite-frontend/'

# grep -v prints the paths that are NOT on the deny list. An empty result is not an error here.
# The OpenAPI document is on the docs/ deny list, so it is added back explicitly.
input="$(grep -v '^$' || true)"
relevant="$(printf '%s\n' "$input" | grep -vE "$DENIED" || true)"
openapi="$(printf '%s\n' "$input" | grep -E "$OPENAPI" || true)"
relevant="$(printf '%s\n%s\n' "$relevant" "$openapi" | grep -v '^$' || true)"

if [ -z "$relevant" ]; then
  echo "Only documentation or repository metadata changed, skipping the coverage jobs." >&2
  printf '%s\n' "backend=false" "frontend=false" "frontend_only=false" \
    "image=false" "chart=false" "e2e=false" "unit=false"
  exit 0
fi

echo "Paths that need the coverage run:" >&2
printf '%s\n' "$relevant" | sed 's/^/  /' >&2

frontend="$(printf '%s\n' "$relevant" | grep -E "$FRONTEND|$OPENAPI" || true)"
outside="$(printf '%s\n' "$relevant" | grep -vE "$FRONTEND" || true)"

if [ -n "$frontend" ]; then frontend_flag=true; else frontend_flag=false; fi
if [ -n "$outside" ]; then frontend_only=false; else frontend_only=true; fi

# The areas a path can belong to. A path that is in none of them selects everything.
IMAGE='^(Dockerfile|\.dockerignore|entrypoint\.sh|docker-compose[^/]*\.yml|reposilite-frontend/Dockerfile)$'
CHART='^charts/'
SOURCE='^(reposilite-backend|reposilite-plugins|reposilite-search-http)/|^reposilite-frontend/'

image=false
chart=false
e2e=false
unit=false
while IFS= read -r path; do
  if printf '%s\n' "$path" | grep -qE "$IMAGE"; then
    image=true
    # The dashboard Dockerfile sits inside the frontend directory but is not dashboard code.
    continue
  fi
  if printf '%s\n' "$path" | grep -qE "$CHART"; then
    chart=true
    continue
  fi
  if printf '%s\n' "$path" | grep -qE "$SOURCE"; then
    e2e=true
    unit=true
    continue
  fi
  # Unknown, a build file, a workflow, a script or the shared test module: every job could be affected.
  image=true
  chart=true
  e2e=true
  unit=true
done <<< "$relevant"

printf '%s\n' "backend=true" "frontend=$frontend_flag" "frontend_only=$frontend_only" \
  "image=$image" "chart=$chart" "e2e=$e2e" "unit=$unit"
