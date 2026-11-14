# Proposal

## Why

Every pull request pays for the whole pipeline, whatever it changed. Measured on recent successful runs of `ci.yml`:

- A docs-only pull request (run 43602939872, `docs(support): add a support policy and support window`) correctly skips the build and
  both coverage jobs, and still runs `Container images` (26 minutes), `HA smoke test` (9 minutes), `Helm chart` (7 minutes),
  `Dashboard end-to-end` (5 minutes), `Plugin API`, `Vulnerabilities`, `SBOM` and `Licenses`. Nothing in the diff can change their result.
- A backend pull request (runs 43592176277 and 43543230928) holds a runner for `Build / Build` (112 to 124 minutes) and
  `Coverage (backend)` (82 to 117 minutes). Both run the Testcontainers integration suite, which takes about 108 minutes, while the unit
  suite takes about 2.4 minutes. The author waits roughly two hours for a one-package change.

Work in flight already attacks parts of this: #257 selects the backend tests a change can reach, #260 runs the integration suite only in
the coverage job, #261 gives it four forks, #264 stops repeating the checks after a merge, #266 and #268 stop the image builds from
flooding and rebuilding the cache. None of them settles the policy: which checks a pull request runs by default, which are too heavy for
that, how a maintainer asks for them, and what protects a release. This change records that policy and the wiring for it.

The maintainer decided:

1. A pull request runs only tests for what it changed. Documentation runs no backend or frontend test, a frontend-only change runs no
   backend test, and a backend change runs only the backend tests it can reach.
2. The light checks of a pull request finish in at most 5 minutes wall clock, runner queue time excluded.
3. Heavy tests run once, shortly before a release: on the release-please pull request, as a required gate before the release is cut.
   They do not run on ordinary pull requests and they do not run nightly by default. They stay automated CI jobs. The label `ci:heavy`
   remains as an optional manual trigger on any pull request.
4. There is no automatic heavy run for image, entrypoint, chart or database paths. That is recorded as an open question with its risk.

## What Changes

- **Light and heavy are named.** Every job of `ci.yml` is classified, and every light job gets a time budget (`design.md`). A new required
  aggregate `Light checks` is green when all light jobs are, and it reports each job's duration against its budget.
- **Change-scoped light jobs.** One scope step, built on the existing `Detect coverage changes` job and its deny list, tells every light
  job whether its inputs changed. Unknown paths, build files, `gradle/`, `buildSrc`, workflow files and shared test fixtures select
  everything.
- **Heavy jobs in `heavy.yml`.** The integration suite, `Container images`, `HA smoke test`, `Dashboard end-to-end`, `Helm chart`,
  `Search backends` and `CodeQL` run from a new workflow that starts on the labels `ci:heavy` and per-area labels, and on the
  release-please pull request.
- **Release gate.** A new required aggregate `Heavy tests` is green on an ordinary pull request that asked for nothing, follows the heavy
  jobs when a label asked for them, and is red on a release-please pull request until a full heavy run has passed on its head commit.
- **Required checks.** `Container images` and `Helm chart` leave the required list and are covered by `Heavy tests`. `Light checks` and
  `Heavy tests` join it. The other required checks keep their names.
- **Getting to 5 minutes.** Gradle build cache and configuration cache in CI, cached dependencies, unit-only builds, the selection of #257,
  and sharding or tagging slow unit tests, each applied only if a measurement shows it is needed.
- **Developer story.** A short guide for running each heavy suite locally through the slot runner.

### Out of scope

- What the tests assert, which tests exist, and the selection rules themselves (`select-backend-tests-by-package`, #257).
- The image cache, the single image build and the concurrency of `ci.yml` (#266, #268, #264), the fork count (#261), and moving the
  integration suite into one job (#260).
- A nightly heavy run. It is an open question and off by default.
- Merge queue, `pull_request_target` workflows, `/heavy` comments and `workflow_dispatch` on pull requests. They are alternatives in
  `design.md`.
- Any image, jar, chart, configuration key or environment variable.

## Capabilities

### New Capabilities

- `change-scoped-ci`: a pull request runs only the jobs and tests its changed paths can reach, any doubt selects everything, and the light
  checks stay inside their time budget.
- `heavy-ci-trigger`: heavy jobs are started by labels or by the release pull request, report through one aggregate, and gate a release.

## Impact

- **Type and scope.** The change ships under `ci(heavy)`, with `test(ci)` for tests that sit alone and `docs(contributing)` for the guide.
  Every code pull request is a single type. The title of the first one, squash-merged onto main, is:

  `ci(heavy): detect the scope of a pull request for every job`

  The full list is in `tasks.md`.
- **Breaking marker.** No breaking change. No image, jar, chart, configuration key, environment variable or runtime path changes, so the
  drop-in rule of `CLAUDE.md` is untouched. The one weakening is stated plainly in `design.md`, Decision 5: image, entrypoint, chart and
  database regressions surface at release time unless the light substitute is built.
- **Repository settings (maintainer, not files).** Create the labels of `design.md`, add `Light checks` and `Heavy tests` to the required
  checks of "Protect main", and remove `Container images` and `Helm chart` from it.
- **Depends on, and assumes merged first.** #257, #260, #261, #264, #266, #268.
- **Conflicts.** Every open pull request that edits `.github/workflows/` or `.github/scripts/`: #257, #260, #261, #264, #266, #268 and
  #303 and #318 (both edit `search-backends.yml`). See `design.md`.

## Verification

- `openspec validate run-changed-tests-and-manual-heavy-ci --strict` passes.
- A dash grep over this directory for U+2013 and U+2014 prints nothing.
- On the code pull requests: a docs-only pull request finishes in under two minutes, a backend pull request's light checks finish in at
  most 5 minutes, and a pull request labelled `ci:heavy` runs the heavy jobs.
