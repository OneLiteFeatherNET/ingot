#!/usr/bin/env bash
# Decides whether the Gradle build of the merge queue has to run.
#
# Usage:
#   build_scope.sh < changed-paths.txt
#
# Reads one changed path per line on stdin and prints one line: code=true|false.
#
# On a pull request the job Build calls the shared gradle-build-pr workflow, whose path filter decides whether it
# builds. That workflow cannot run on a merge_group event (its filter step needs a pull request or a checkout), so
# merge-queue.yml reports its check there and uses this script. The pattern is the default `code` filter of the
# pinned gradle-build-pr workflow, translated from globs: Gradle build files, the wrapper, buildSrc/, src/, JVM
# sources and workflows. Keep it in step with that filter when the pin moves.
#
# A path that git quotes (non-ASCII names, for example "a/\303\244.kt") cannot be matched reliably, so it counts as
# code. That is the fail-open direction: the build runs.
# Requires bash and grep.

set -euo pipefail

CODE='\.(gradle|gradle\.kts|java|kt|groovy|scala)$|(^|/)gradle\.properties$|^(gradle|buildSrc|src|\.github/workflows)/|^gradlew(\.bat)?$|^"'

paths="$(grep -v '^$' || true)"
if grep -qE "$CODE" <<< "$paths"; then
  echo "code=true"
else
  echo "code=false"
fi
