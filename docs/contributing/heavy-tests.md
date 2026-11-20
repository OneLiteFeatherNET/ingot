# Heavy tests

A pull request runs the light checks only: compile, unit tests, lint and dependency scans. The heavy tests need a container, a
built image, a cluster or a browser, and they take minutes to hours. They do not run on an ordinary pull request. A maintainer
starts them with a label, and the release pull request needs one full run before it can be merged.

A documentation, frontend or small backend change needs none of them.

## Starting them on a pull request

Only people with triage or write access can add a label. The workflow is `.github/workflows/heavy.yml`. It reports through one
check, `Heavy tests`, which is green when nothing was asked for and follows the jobs when a label asked for them.

| Label | Job it starts | Duration seen | Run locally |
|---|---|---|---|
| `ci:heavy` | every job below | about two hours | each command below |
| `ci:integration` | `Integration tests (backend)` | about 108 minutes in full | `heavy ./gradlew --no-daemon --max-workers=2 :reposilite-backend:integration --tests 'com.reposilite.maven.*'` |
| `ci:images` | `Container images` and `Helm chart` | 19 to 26 minutes, and 6 to 9 minutes | `heavy ./gradlew --no-daemon --max-workers=2 :reposilite-backend:containerTest -Dingot.previous.image=<image>`, and `helm lint charts/ingot --strict -f charts/ingot/ci/allinone-values.yaml` |
| `ci:ha` | `HA smoke test` | 4 to 9 minutes | `python3 charts/ingot/tests/ha_smoke.py`, with the images loaded into a kind cluster named `ingot-ha` |
| `ci:e2e` | `Dashboard end-to-end` | 4 to 6 minutes | `heavy npm run e2e --prefix reposilite-frontend -- --workers=2` |
| `ci:search` | `Search backends` | about 6 minutes | `heavy ./gradlew --no-daemon --max-workers=2 :reposilite-backend:integration --tests '*OpenSearch*'` |
| `ci:codeql` | `CodeQL` | 1 to 4 minutes | none, it runs on GitHub only |

The label stays on the pull request until a maintainer removes it. Every push to a branch of this repository runs the labelled jobs
again. On a pull request from a fork a push does not rerun them, and `Heavy tests` stays red until a maintainer has looked at the
push and removed and re-added the label. The jobs of a fork pull request run with a read-only token and no secrets.

Adding a label that does not start with `ci:` cancels nothing. Adding or removing a `ci:` label cancels nothing either: the new decision waits for the
running heavy run on the same commit and starts after it. Only a new push cancels the run of the commit it replaces.

## In the merge queue

The merge queue has no labels. There the heavy gate reads the diff of the merge group and starts `Container images`, `Helm chart`
and `Dashboard end-to-end` when their scope changed. `Dashboard end-to-end` runs only for changes the dashboard can see: the
frontend, the OpenAPI document, the HTTP surface of the backend, login, sessions, tokens and access checks, and the files that build or start the server. The other heavy
jobs do not run in the queue. See [Merge queue](merge-queue.md) for the full rule.

## Before a release

The release pull request opened by release-please has a head branch that starts with `release-please--`. `Heavy tests` is red on
it until a maintainer adds `ci:heavy` and every heavy job has passed in full on its head commit. A new push to the release pull
request starts the jobs again, so the result of an earlier commit does not count.

## Running them on main

A maintainer can start the workflow by hand on `main` (Actions, Heavy, Run workflow) to get a baseline. It runs every job in full.
It does not run on a schedule.

## Running them on your machine

Run the commands of the table through `heavy`, the slot runner. It allows four heavy jobs on the machine at once and waits until
enough memory is free. Keep Gradle at `--max-workers=2` and Playwright at `--workers=2`.

Pass `--tests` to the integration task to run only the packages you changed. Without it the task runs the whole suite, which is
what the label on a release pull request runs.

The commands of the table are the ones the jobs run. The jobs themselves also build the dashboard and the server jar first
(`./gradlew :reposilite-backend:shadowJar` and `npm ci --prefix reposilite-frontend`) and install the Playwright browser
(`npx playwright install --with-deps chromium`).
