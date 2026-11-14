# Design

This design assumes #257, #260, #261, #264, #266 and #268 are merged first, and builds on them. It does not repeat them.

## Starting point

On main today, plus what the six pull requests in flight change:

- `ci.yml` runs on pull requests and pushes. #264 narrows a push to the jobs main needs (coverage, e2e, two amd64 image builds), adds a
  `schedule` (`23 3 * * *`) and `workflow_dispatch`, and keys `concurrency` on the pull request number.
- `Build / Detect changes` is the first job of the shared workflow `gradle-build-pr.yml` (dorny/paths-filter). `Detect coverage changes`
  runs `coverage_changes.sh`, a deny list (docs, OpenSpec, templates, `*.md`, repository metadata), and emits `backend`, `frontend` and
  `frontend_only`. `coverage-backend` and `coverage-frontend` skip on its outputs. `release-please.yml` has its own `changes` job (`Detect
  image changes`), a second deny list for push events. All three fail open: no usable range means everything runs.
- #257 adds `select_backend_tests.py`: on a pull request, one hop of imports from the changed domain selects unit and integration tests,
  infrastructure packages, non-Kotlin paths and anything unmapped select the full suite, and a script failure means `mode=full`.
- #260 excludes `integration` from `Build / Build`, so it runs once, in `Coverage (backend)`. #261 gives that job four forks.
- The ruleset "Protect main" (id 27016692, read with `gh api repos/OneLiteFeatherNET/ingot/rulesets/27016692`) requires `Build / Aggregate
  test results`, `Frontend`, `Coverage`, `Container images`, `Licenses`, `Vulnerabilities` and `Helm chart`. `Coverage` is an `always()`
  aggregate of the two coverage jobs.
- Neither the Gradle build cache nor the configuration cache is enabled (`org.gradle.caching` is unset, no `FROM-CACHE` lines in recent
  logs), and `setup-gradle` runs read-only.

### Durations from recent runs

Taken with `gh run view <id> --json jobs`, as `completedAt - startedAt`. Runs: 43602939872 (docs only), 43597271962, 43592176277,
43591697374, 43543230928 (code), 43600280741 (search backends), 43614490072 (CodeQL).

| Job | Seen | Notes |
|---|---|---|
| `Build / Build` | 6737 s, 7413 s | includes the integration suite until #260 |
| `Coverage (backend)` | 4920 s to 6999 s | unit about 144 s, integration about 108 min |
| `Coverage (frontend)` | 209 s | |
| `Container images` | 1152 s to 1572 s | also runs on docs-only pull requests today |
| `HA smoke test` | 267 s to 560 s | |
| `Helm chart` | 378 s to 510 s | |
| `Dashboard end-to-end` | 254 s to 365 s | |
| `Plugin API` | 187 s to 274 s | report only |
| `Vulnerabilities` | 101 s to 124 s | |
| `SBOM` | 53 s to 70 s | |
| `Licenses` | 58 s to 59 s | |
| `Frontend` | 24 s to 35 s | |
| `Detect coverage changes`, `Build / Detect changes` | 3 s to 9 s | |
| `Search backends` | 330 s | own workflow, path filtered, nightly 03:17 |
| `CodeQL (java-kotlin)`, `(javascript-typescript)` | 242 s, 60 s | own workflow, push, pull request, weekly |

## Light and heavy

The split follows what the maintainer named: heavy is what needs Docker-in-Docker, a built image, a cluster, a browser, or minutes of
container start-up. Light is compile, unit tests, lint, static checks and dependency scans.

| Class | Job | Budget (job wall clock, queue excluded) | Basis |
|---|---|---|---|
| Light | `Detect scope` (extended `Detect coverage changes`) | 30 s | 3 to 9 s |
| Light | `Build / Build` + `Aggregate test results` (compile and unit tests, no integration) | 300 s | unit 144 s, compile and setup not measured |
| Light | `Coverage (backend)` (selected or full unit tests, no integration on pull requests) | 300 s | same |
| Light | `Coverage (frontend)` | 270 s | 209 s |
| Light | `Frontend` | 90 s | 24 to 35 s |
| Light | `Licenses` | 120 s | 59 s |
| Light | `Vulnerabilities` | 180 s | 101 to 124 s |
| Light | `SBOM` | 120 s | 53 to 70 s |
| Light | `Plugin API` | 300 s | 187 to 274 s, the tightest light job |
| Light | `DCO sign-off`, `OpenAPI breaking changes` | 300 s | not measured |
| Light | `Light checks` (aggregate) | 30 s | |
| Heavy | integration suite (`Integration tests (backend)`) | not budgeted | about 108 min |
| Heavy | `Container images` (build, container tests, upgrade rehearsal, arm64, Trivy) | not budgeted | 19 to 26 min |
| Heavy | `HA smoke test` | not budgeted | 4.5 to 9 min |
| Heavy | `Dashboard end-to-end` | not budgeted | 4 to 6 min |
| Heavy | `Helm chart` | not budgeted | 6 to 8.5 min |
| Heavy | `Search backends` (search backend container jobs) | not budgeted | 5.5 min |
| Heavy | `CodeQL` | not budgeted | 4 min and 1 min |
| Heavy | `Heavy tests` (aggregate) | 30 s | |

The light budget is a ceiling per job, and all light jobs run in parallel, so the pull request's light critical path is the slowest job,
at most 300 s. `Dashboard end-to-end`, `Plugin API`, `Helm chart` and `Search backends` are close to the line by duration, so the budget
alone does not decide them: e2e, Helm and search start containers or a browser and belong with the other heavy jobs, `Plugin API` stays
light because it needs no container and is report only. If it exceeds 300 s it moves to heavy (open question 7).

## Decision 1: the manual trigger is a label

Heavy jobs are automated CI jobs that a maintainer starts by putting a label on the pull request.

**Mechanism.** `heavy.yml` is a separate workflow from `ci.yml`.

```yaml
on:
  pull_request:
    branches: [ main ]
    types: [ opened, reopened, synchronize, labeled, unlabeled ]
  workflow_dispatch:        # main only, on demand, see below
permissions:
  contents: read
concurrency:
  group: heavy-${{ github.event.pull_request.number || github.ref }}-${{ <see below> }}
  cancel-in-progress: ${{ github.event_name == 'pull_request' }}
```

`ci.yml` keeps the default `pull_request` types (`opened`, `synchronize`, `reopened`), so a label event never starts a light run and
never cancels one. Only `heavy.yml` listens to `labeled`.

**Labels.** `ci:heavy` runs every heavy job. Per-area labels run one area: `ci:integration`, `ci:e2e`, `ci:images`, `ci:ha`, `ci:search`,
`ci:codeql`. `Helm chart` runs with `ci:images`. Each heavy job's condition is "the pull request carries `ci:heavy` or its area
label, or it is a release pull request". The per-area labels are cheap to add (one extra term in an `if`), so the first iteration ships
all of them; the default set is open question 1.

**Rerun on new pushes.** The label stays until a maintainer removes it, and `synchronize` reruns the labelled jobs on every push. That is
the default because a one-off run on an earlier commit says nothing about the final head. Fork pull requests differ, see below.

**Who may label.** GitHub lets only users with triage or write access add a label, so the label is the permission check. Fork authors can
open a pull request but cannot label it. No comment parsing and no permission API call is needed.

**Fork safety, without `pull_request_target`.** `heavy.yml` uses plain `pull_request`. For a fork pull request GitHub withholds secrets
and gives the token read-only scope, even when a maintainer added the label, so labelled fork code runs with nothing to steal. The heavy
jobs use no secret: images are built and not pushed, Trivy and Testcontainers need none, and `contents: read` is the only permission. No
job in `heavy.yml` may use `secrets.*`, `pull_request_target` or `workflow_run` with write scope. A test in the shape suite asserts that.
One extra rule for forks: `synchronize` alone does not rerun heavy jobs on a fork pull request, because the label was given to the code
the maintainer reviewed. A new push needs the maintainer to remove and re-add the label (open question 2). Same-repository branches
rerun automatically.

**Concurrency, so a label event does not cancel the normal run.** `ci.yml` and `heavy.yml` are different workflows with different groups,
so a label event cannot cancel a light run. Inside `heavy.yml`, an unrelated label (`bug`) would start a run in the same group and cancel
a heavy run in progress. The group therefore ends in a suffix: `run` for opened, reopened, synchronize and for `labeled` or `unlabeled`
events whose label name starts with `ci:`, and the unique `github.run_id` for any other label. A foreign label gets a group of its own and
cancels nothing. Removing `ci:heavy` while a run is in progress lands in the same group, cancels it, and the new run finds no label and
skips every heavy job.

### Alternatives

| Alternative | Why not the default |
|---|---|
| `/heavy` comment through `issue_comment` | The event runs from the default branch with write token and secrets, so it needs a permission check (`gh api .../collaborators/<user>/permission`) and a manual checkout of the pull request ref: the classic `pull_request_target` hazard. Comments are also not shown as a state on the pull request. Kept as a later option if labels prove too coarse. |
| `workflow_dispatch` with a pull request number | Runs the workflow from a branch the dispatcher chooses, attaches no status check to the pull request head unless the job posts one with a write token, and needs the number typed in. Kept for main only (on-demand full run). |
| GitHub merge queue | Runs the heavy jobs at merge for every pull request, which contradicts "once, before a release", needs a ruleset change and a `merge_group` trigger on every required workflow. Reconsider if the release gate proves too late. |
| `pull_request_target` with the label | Runs trusted workflow code against untrusted checkout with secrets available. Rejected: nothing heavy needs a secret. |

## Decision 2: heavy runs once, before the release, as a required gate

The release is cut by merging the release-please pull request. The gate is therefore a required status check on that pull request.

- **Detecting the release pull request.** Its head ref starts with `release-please--`. `release-please.yml` opens it with a GitHub App
  token (lines 50 to 64), so its checks run normally and are not left in `action_required`.
- **Trigger.** Default: a maintainer puts `ci:heavy` on the release pull request when the release is ready to cut. On the release pull
  request the label means every heavy job in full, including the full integration suite, never the selection. The alternative, an
  automatic run on every update of the release pull request, restarts a roughly two hour run each time something merges to main while the
  release pull request is open (open question 3).
- **Gate.** The aggregate `Heavy tests` is required. On a release pull request it is red unless every heavy job ran in full on the head
  commit and passed. Without the label it fails with "run the heavy tests: add ci:heavy". A release therefore cannot be cut without one
  green full heavy run on exactly the commit that is released.
- **Nightly.** Off by default (open question 4). If switched on: `schedule` in `heavy.yml` on main at 04:23 UTC (after the `ci.yml` cron
  of #264), full run, and a failure opens or updates one issue labelled `ci:red-main`, titled with the date, with the run link. The job
  needs `issues: write` on that one job only, and the issue is assigned to nobody. The code owner team in `.github/CODEOWNERS`
  (`ingot-maintainers`) is notified by `@`-mentioning it in the issue body.
- **On demand.** `workflow_dispatch` on `heavy.yml`, main only, runs everything in full, for a maintainer who wants a baseline.
- **Existing schedules.** `search-backends.yml` (nightly) and `codeql.yml` (weekly, and on push to main) are security and compatibility
  baselines that predate this change and keep their own triggers (open question 5).

## Decision 3: required checks

**Does a job skipped through `if:` satisfy a required check?** Yes. GitHub documents it in "Troubleshooting required status checks":
"Successful check statuses are `success`, `skipped`, and `neutral`", and in its table of skipped causes: a workflow skipped by path
filtering, branch filtering or a commit message leaves its checks "Pending" and blocks merging ("Avoid requiring workflows that can be
skipped"), whereas "A job is skipped by a conditional" reports "Success". The same page warns that "A job depends on a failed job: the
dependent job is skipped and may not block merging", with the fix "Use `always()` with `needs` for required checks that depend on other
jobs". The repository already relies on it: run 43602939872 shows `Build / Aggregate test results`, `Coverage (backend)` and `Coverage
(frontend)` as `skipped` on a docs-only pull request that merged.

Two consequences shape the design:

1. `heavy.yml` and `ci.yml` must have no `paths:` or `branches-ignore` filter at the workflow level, or their checks would stay pending.
   Every skip is a job-level `if`. (The existing `paths:` filter of `search-backends.yml` is replaced for that reason.)
2. A job behind a failed gate is skipped and therefore green. Every heavy job uses the pattern that `coverage-backend` already uses
   (`!cancelled() && (needs.gate.result != 'success' || <wanted>)`), so a failed scope step runs the job, and each aggregate checks the
   gate's result as well.

**Aggregates.**

- `Light checks` (new, required): `if: always()`, `needs` every light job. Green when each result is `success` or `skipped`. It writes a
  summary table of each job's duration against its budget. Over budget is a warning, not a failure (open question 6).
- `Heavy tests` (new, required): `if: always()`, `needs` every heavy job and the heavy gate. It is green on an ordinary pull request that
  requested nothing and prints "heavy tests not requested". With a label it is green only when every requested job is `success`. On a
  release pull request it is green only when every heavy job ran in full and passed.
- `Build / Aggregate test results`, `Frontend`, `Coverage`, `Licenses`, `Vulnerabilities`: stay required, keep their names, stay light.
- `Container images` and `Helm chart`: stop being required. They are heavy, run through `heavy.yml`, and a failure reaches the merge
  through `Heavy tests`. Keeping them required would be harmless (a skip is green) but would suggest a guarantee that no longer exists. The
  job names do not change, so an operator who wants them required again can.

**Does merging need a heavy run for certain paths?** Not by default. That is Decision 5 and open question 8.

## Decision 4: how selection fails safe

The scope step (`ci_scope.sh`, extending `coverage_changes.sh`) answers for each light job whether its input changed. It keeps the
existing rule: a deny list of documentation and metadata, so anything it does not know counts as relevant. These select everything, never
a subset:

- an unmapped or unknown path, a path git quotes, an empty or unusable diff range, a script failure, a non-pull-request event;
- `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/**`, `buildSrc/**`, any `*.gradle.kts`;
- `.github/workflows/**` and `.github/scripts/**`, which select every light job and, because of Decision 1, also request the heavy jobs
  that the edited workflow defines only when the maintainer labels the pull request;
- shared test fixtures and `reposilite-test/**`, and test resources outside one domain;
- anything #257 maps to `mode=full` (infrastructure packages, non-Kotlin backend paths, selection above 60 percent).

#257's measured sample matters here: only 4 of 20 pull requests ran partially, and 12 of the full runs came from non-Kotlin paths. That is
acceptable under this design because the full unit suite is about 2.4 minutes. The selection is an optimisation of the compile and test
part of the 5 minute budget and not a precondition of it, so every fail-safe fallback to "full" still lands inside the budget (open
question 9 covers the unmeasured compile time).

A docs-only change selects nothing. A change confined to `reposilite-frontend/` selects the frontend jobs and the backend tests that serve
the dashboard (the existing `frontend_only` rule), and no other backend test.

## Decision 5: image, entrypoint, chart and database paths

`CLAUDE.md` says the `Container images` job is the drop-in contract check and that "if it fails, the change is wrong". The maintainer
decision removes the automatic heavy run for these paths. **The risk, plainly:** a change to the `Dockerfile`, `entrypoint.sh`, `charts/`
or the database layer can break an unmodified upstream compose file, adoption of an existing `/app/data`, or `PUID`, `PGID` and
`REPOSILITE_OPTS`, and nobody finds out until the release pull request's heavy run. The fix then lands under release pressure and the
bad commit is already on main.

A cheap light substitute, only if it fits the budget:

- **`Image smoke`** (light, conditional): runs when the image scope flag is set (`Dockerfile`, `.dockerignore`, `entrypoint.sh`,
  `docker-compose*.yml`, `reposilite-frontend/Dockerfile`). Builds one architecture (amd64) with the layer cache of #266 and #268, starts
  it with a temporary volume owned by another uid and `PUID`/`PGID` set, waits for the healthcheck, and asserts the process runs as the
  service account and `REPOSILITE_OPTS` took effect. It does not run the arm64 build, the upgrade rehearsal or Trivy.
- **`Chart lint`** (light, conditional): `helm lint` and `helm template` for `charts/ingot` without a cluster.
- **Database layer**: `ReposiliteMigrations.kt`, `net/onelitefeather/ingot/schema/**` and the repository classes are covered by the unit
  suite that always runs for a backend change. The Testcontainers database matrix (MariaDB, PostgreSQL) stays heavy.

Feasibility is unmeasured: the image build compiles the backend and the dashboard inside the Dockerfile, and a cold run of the whole
`Container images` job is about 19 to 26 minutes. A warm amd64-only build and a start may or may not fit 300 s. The substitute is built
only if a measurement shows it fits (open question 8); otherwise the risk above stands, and the guard is the release gate plus the label
`ci:images`, which a reviewer should add on any such pull request.

## Decision 6: the local developer story

A developer runs the same suites the label would run, through the slot runner of the repository's contributing rules:

```
heavy ./gradlew --no-daemon --max-workers=2 :reposilite-backend:integration --tests 'com.reposilite.maven.*'
heavy ./gradlew --no-daemon --max-workers=2 :reposilite-backend:containerTest -Dingot.previous.image=<image>
heavy npm run e2e --prefix reposilite-frontend          # Playwright, --workers=2
```

`select_backend_tests.py` also prints the `integration=` patterns the label would run, so
`git diff --name-only origin/main...HEAD | python3 .github/scripts/select_backend_tests.py` tells the author which `--tests` to pass. The
guide `docs/contributing/heavy-tests.md` (location to be confirmed against the existing docs tree) lists each label, the job it starts,
its duration and the local command, and states that a docs, frontend or small backend change needs none of them.

## How we get to 5 minutes

The light path today is not measured end to end. What is known: the unit suite runs in about 2.4 minutes, and the whole `Build / Build`
job took 112 minutes only because it ran integration, which #260 and this change remove from pull requests. Everything below is applied in
this order and stops as soon as the light critical path is inside 300 s.

1. **Unit-only light build.** `Build / Build` and `Coverage (backend)` run compile and unit tests on pull requests, no integration. This
   alone removes about 106 minutes. `Build / Build` and `Coverage (backend)` both run the unit suite today, so one of them pays for
   nothing new; whether to drop the duplicate is open question 10.
2. **Gradle build cache in CI.** No `org.gradle.caching` and no `FROM-CACHE` today. Enable it for CI only (`--build-cache`, so local
   builds and the drop-in contract are untouched), and let `setup-gradle` write the cache from main (`cache-read-only: ${{ github.ref
   != 'refs/heads/main' }}`) so pull requests read what main wrote. #264 keeps the main jobs that write it. Expected gain: compile and
   unchanged test tasks come from cache. **Not measured.**
3. **Gradle configuration cache.** `--configuration-cache` in CI, trialled on `test`. It saves the configuration phase of a multi-module
   build, but plugin compatibility is unknown, and an incompatible plugin makes the build fail rather than slow. Enable only after a
   green trial; fall back to off. **Not measured.**
4. **Cached dependencies.** `setup-gradle` already caches the dependency store, and the frontend jobs use the npm cache. Verify the
   cache hit rate of a pull request run; a cold cache makes the budget unreachable. **Not measured.**
5. **Selection from #257.** Fewer test classes after compile. It does not shorten compile, and its fallback to full is within budget
   (Decision 4).
6. **Splitting unit tests across parallel jobs.** If compile plus 2.4 minutes of tests exceeds 300 s, shard `:reposilite-backend:test` by
   package into two or three matrix jobs (`--tests` patterns produced by the selection script). Each shard pays checkout, setup and
   compile again, so this helps only with the build cache from step 2. Only if steps 1 to 5 fail.
7. **Moving slow unit tests to heavy.** Rank tests by time from the JUnit XML of a run, tag the slowest with `@Tag("slow")`, exclude the
   tag from `test`, and run it in the heavy workflow. Only if a few tests dominate. **Per-test timings not taken.**

**Measurements not taken, to be recorded in the first code pull request:** the cold and warm wall clock of `Build / Build` and
`Coverage (backend)` without integration; the compile time of the backend; the Gradle cache hit rate of a pull request run; the
configuration cache trial; per-test unit timings; the duration of `DCO sign-off` and `OpenAPI breaking changes`; the duration of a warm
amd64-only image build and start.

## Conflicts with open pull requests

All edit `.github/workflows/ci.yml` or `.github/scripts/` and need a rebase order, which is why this change assumes them merged first.

| PR | Overlap | Handling |
|---|---|---|
| #257 | edits `coverage-backend` and adds the selection script | the first code pull request after it extends `select_backend_tests.py` output; the integration step moves out of `coverage-backend` into `heavy.yml` |
| #260 | `gradle-task` of the `build` job | unchanged here, relied on |
| #261 | `INGOT_TEST_FORKS` env on `coverage-backend` | moves with the integration job into `heavy.yml` |
| #264 | `if:` conditions, `schedule`, `concurrency` of `ci.yml` | the scope step and `Light checks` add to its conditions; the push rules stay |
| #266, #268 | the `images` job and its cache | the job moves to `heavy.yml` unchanged except its trigger |
| #303, #318 | `search-backends.yml` | `ci(heavy): start the search backends job from a label` is opened after both merge |
| `add-dashboard-e2e-ci`, `add-ha-smoke-test` | define the e2e and HA jobs | triggers change, contents do not |

## Open questions, with defaults

1. **Which labels ship?** Default: `ci:heavy` plus `ci:integration`, `ci:e2e`, `ci:images`, `ci:ha`, `ci:search`, `ci:codeql`.
2. **Do fork pushes rerun a labelled heavy run?** Default: no, the maintainer re-adds the label after reviewing the push.
3. **Release pull request: automatic run on every update, or label when ready?** Default: label when ready, the aggregate stays red
   until then.
4. **Nightly heavy on main?** Default: off, per the maintainer. If on, a red run opens one issue labelled `ci:red-main`.
5. **Do the schedules of `search-backends.yml` (nightly) and `codeql.yml` (weekly, push) stay?** Default: yes, they are baselines that
   predate this change.
6. **Is an over-budget light job a failure?** Default: warning in the `Light checks` summary only, so a slow runner does not block merges.
7. **Does `Plugin API` stay light if it passes 300 s?** Default: no, it moves to heavy.
8. **Is an automatic heavy or light-substitute run for image, entrypoint, chart and database paths wanted after all?** Default: no
   automatic heavy run; build `Image smoke` and `Chart lint` only if a measurement fits 300 s. Risk as in Decision 5.
9. **Compile time and cache hit rate fit the budget?** Default: assume yes until the first code pull request measures them; if not, steps
   6 and 7 of "How we get to 5 minutes".
10. **Drop the unit run in `Build / Build` or in `Coverage (backend)`?** Default: keep both until the budget is measured.
11. **Does the push to main keep running the full integration suite for the Codecov base (#264)?** Default: yes, it blocks nobody.
12. **Is `Container images` kept required as well as `Heavy tests`?** Default: removed from the required list.
