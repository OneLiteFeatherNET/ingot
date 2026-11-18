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
#   e2e=true|false           something the dashboard end-to-end tests can see changed (see below)
#   unit=true|false          code the unit tests compile or serve changed (anything except the chart and image inputs)
#
# image, chart, e2e and unit fail open: a relevant path the script cannot place in one of its known areas
# (an unknown root file, a path git quotes, a Gradle build file, a workflow, a workflow script, the shared test
# module) turns all four on, because the job it would have skipped might be the one that catches the change. The
# one exception is e2e for the load tests and for workflows and workflow scripts the suite does not run in (below).
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
# e2e is narrower than unit, because "Dashboard end-to-end" is the slowest job the merge queue starts. It is true
# for a path the suite can see:
#   - the dashboard, its end-to-end specs, harness and fixtures and the Playwright config (reposilite-frontend/)
#   - the OpenAPI document docs/openapi/ingot-openapi.json
#   - the HTTP surface of the backend the dashboard calls: Kotlin files under src/main/kotlin in a web/, api/ or
#     frontend/ package, or named *Endpoint*.kt or *Routes.kt, and src/main/resources (static files, service files)
#   - login, sessions, tokens and access checks, which every signed-in page of the suite goes through:
#     com/reposilite/auth/, com/reposilite/token/, net/onelitefeather/ingot/identity/ (login, session, authorization),
#     net/onelitefeather/ingot/search/access/ and com/reposilite/maven/RepositorySecurityProvider.kt
#   - what builds or starts the server for the suite: the root and backend Gradle build files, gradle/, buildSrc/,
#     the heavy workflow, this script and heavy_gate.py
#   - any path the script cannot place (fail-safe, as for the other flags)
# It is false for the rest of the backend and its tests, the plugins (the suite loads no plugin of this repository,
# only its own fixture), the search sidecar, the chart, the image inputs (the suite starts the jar, not the image),
# loadtest/, the other workflows and workflow scripts, documentation and OpenSpec proposals. Those paths keep every
# other flag they selected before.
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
# What the dashboard end-to-end suite can see. A path in SOURCE or in the fallback below turns e2e on only when it
# matches here, or, in the fallback, when it is not on the NO_E2E list.
E2E='^reposilite-frontend/|^docs/openapi/ingot-openapi\.json$'
E2E="$E2E"'|^reposilite-backend/(build\.gradle\.kts$|src/main/resources/)'
E2E="$E2E"'|^reposilite-backend/src/main/kotlin/(.*/)?(web|api|frontend)/'
E2E="$E2E"'|^reposilite-backend/src/main/kotlin/(.*/)?[^/]*(Endpoint[^/]*|Routes)\.kt$'
E2E="$E2E"'|^reposilite-backend/src/main/kotlin/(com/reposilite/(auth|token)|net/onelitefeather/ingot/(identity|search/access))/'
E2E="$E2E"'|^reposilite-backend/src/main/kotlin/com/reposilite/maven/RepositorySecurityProvider\.kt$'
E2E="$E2E"'|^(build\.gradle\.kts|settings\.gradle\.kts|gradle\.properties|gradlew|gradlew\.bat)$|^(gradle|buildSrc)/'
E2E="$E2E"'|^\.github/workflows/heavy\.yml$|^\.github/scripts/(ci_scope\.sh|heavy_gate\.py)$'
# Paths outside SOURCE that the script can place and that the suite cannot see.
NO_E2E='^(loadtest|\.github/workflows|\.github/scripts)/'

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
    unit=true
    if printf '%s\n' "$path" | grep -qE "$E2E"; then e2e=true; fi
    continue
  fi
  # Unknown, a build file, a workflow, a script or the shared test module: every job could be affected, except that
  # the end-to-end suite cannot see the load tests or the workflows and scripts it does not run in.
  image=true
  chart=true
  unit=true
  if printf '%s\n' "$path" | grep -qE "$E2E" || ! printf '%s\n' "$path" | grep -qE "$NO_E2E"; then
    e2e=true
  fi
done <<< "$relevant"

printf '%s\n' "backend=true" "frontend=$frontend_flag" "frontend_only=$frontend_only" \
  "image=$image" "chart=$chart" "e2e=$e2e" "unit=$unit"
