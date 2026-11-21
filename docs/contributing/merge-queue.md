# Merge queue

`main` is merged through GitHub's merge queue. Without it, every merge put each other open pull request behind `main`, its
author had to update the branch, and CI ran again before the next merge. Merging several approved pull requests one after the
other took hours of waiting.

## What a contributor does

Nothing changes until the pull request is approved. Then press **Merge when ready** (or **Add to merge queue**) instead of
updating the branch yourself. GitHub builds a temporary merge group from `main` plus the pull requests ahead of yours in the
queue plus yours, runs the required checks on that commit, and squash-merges when they pass. If a check fails, the pull
request leaves the queue with the failing run linked, and the entries behind it are rebuilt without it.

You do not have to keep the branch up to date with `main`. The queue tests the combination that will land.

## Which checks run in the queue

Every workflow that reports a required check also starts on the `merge_group` event. A merge group has no labels and no
pull request, so each job decides from the diff between the base and the head of the merge group, the same way it decides
from the diff of a pull request:

| Required check | Workflow | In the merge queue |
|---|---|---|
| `Build / Aggregate test results` | `merge-queue.yml` | Compile and unit tests on JDK 17 when a Gradle file, a JVM source or a workflow changed, otherwise green without building. On a pull request the same check comes from the shared Gradle workflow in `ci.yml`, which cannot run on a merge group. |
| `Frontend`, `Licenses`, `Vulnerabilities` | `ci.yml` | Run when their scope changed, skipped (green) otherwise, as on a pull request. |
| `Coverage` | `ci.yml` | Runs the backend and frontend tests the change reaches, as on a pull request. Coverage is uploaded to Codecov from `main` only. |
| `Container images` | `heavy.yml` | Runs when a Dockerfile, the entrypoint or a compose file changed, or a path the scope cannot place (a build file, a workflow). |
| `Helm chart` | `heavy.yml` | Runs when the chart or an image input changed, or a path the scope cannot place. |

`Dashboard end-to-end` also runs in the queue, but only when the change touches something the dashboard can see.
`.github/scripts/ci_scope.sh` decides that with its `e2e` flag:

| Change | `Dashboard end-to-end` |
|---|---|
| `reposilite-frontend/`, including the end-to-end specs, their harness and fixtures and `playwright.config.ts` | runs |
| The OpenAPI document `docs/openapi/ingot-openapi.json` | runs |
| Backend HTTP surface: Kotlin files under `reposilite-backend/src/main/kotlin` in a `web/`, `api/` or `frontend/` package, or named `*Endpoint*.kt` or `*Routes.kt`, and `reposilite-backend/src/main/resources/` | runs |
| Login, sessions, tokens and access checks: `com/reposilite/auth/`, `com/reposilite/token/`, `net/onelitefeather/ingot/identity/` (login, session, authorization), `net/onelitefeather/ingot/search/access/` and `com/reposilite/maven/RepositorySecurityProvider.kt` under `reposilite-backend/src/main/kotlin` | runs |
| What builds or starts the server: the root Gradle files, `gradle/`, `buildSrc/`, `reposilite-backend/build.gradle.kts`, `heavy.yml`, `ci_scope.sh`, `heavy_gate.py` | runs |
| Any path the script cannot place | runs (fail-safe) |
| Other backend code and backend tests | skipped |
| `reposilite-plugins/` (the suite loads no plugin of this repository) and `reposilite-search-http/` | skipped |
| The chart, the image inputs (the suite starts the jar, not the image), `loadtest/` | skipped |
| Other workflows and workflow scripts, documentation, OpenSpec proposals | skipped |

A plugin that gains a dashboard part, or a backend endpoint whose file name does not match, does not start the suite in the queue;
add the `ci:e2e` label to the pull request instead. The integration suite, the HA smoke test, the search backends and CodeQL do not
run in the queue; they stay on their labels (see [Heavy tests](heavy-tests.md)).

Nothing in the queue writes to a pull request: no comment, no Codecov upload, no image push.

## Turning it on (maintainers)

The workflows report on `merge_group` already, so the queue can be enabled at any time in the ruleset "Protect main"
(Settings, Rules, Rulesets) with **Require merge queue** and these settings:

- Merge method: **Squash and merge**
- Build concurrency: **3**
- Minimum group size: **1**, maximum group size: **5**
- Wait time to meet minimum group size: **5 minutes** (the default)
- Require all queue entries to pass required checks: **on**
- Status check timeout: **60 minutes** (the default)

The required status checks of the ruleset stay as they are. Strict "up to date" stays off; the queue replaces it.
