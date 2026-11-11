# Design

## Starting point on main

- The dashboard is tested only with Vitest and jsdom. `reposilite-frontend/package.json:10` sets `"test": "vitest run"`, and
  `reposilite-frontend/vitest.config.js:25` uses `environment: "jsdom"` with `include` limited to `src/**` and `server/**`
  (`vitest.config.js:26`). No browser test runner is in the dependency list, and nothing in `.github/workflows` starts a browser.
- The component tests mock the layers the regressions live in. `reposilite-frontend/src/navigation/administration.test.js:43` checks
  the breadcrumb through `sideNavOf` in isolation, and `:49` and `:57` check the rail order through `railGroups` with a plain
  predicate. None of them renders the page, the router and the server together.
- The current-entry marking is `aria-current` set per link at `reposilite-frontend/src/components/layout/SideNavLayout.vue:93`, and
  a second `aria-current="page"` at `:156`. A test that counts the marked links inside the side navigation on each page finds
  the duplicate on `/settings/audit` that the operator reported.
- The server serves the dashboard itself. The frontend module is a dependency of the backend (`reposilite-backend/build.gradle.kts:45`),
  and the shaded jar is named `ingot-<version>.jar` (`reposilite-backend/build.gradle.kts:224`). A suite that starts this jar tests the
  same bytes the image ships.
- Startup flags needed by the suite exist: `--working-directory` (`reposilite-backend/src/main/kotlin/com/reposilite/ReposiliteParameters.kt:42`),
  `--port` (`:90`) and `--token name:secret` (`:96`), which creates a temporary token with all permissions. The documented use is in
  `docs/gitbook/how-to/recover-admin-access.md:46`.
- The pull request pipeline does not run the suite. That wiring is `add-dashboard-e2e-ci`, which follows this change.
- `fix-administration-navigation`, `fix-browse-overview` and `fix-dashboard-status` are not on main and no openspec change of those
  names exists yet. Their regressions are therefore specified here as expected failures with markers that the fixes remove.

## Decision 1: Playwright

Playwright (`@playwright/test`) is the default.

- It is maintained by the browser vendors, it starts and stops its own browsers, and it auto-waits on actions and on web-first
  assertions such as `expect(locator).toHaveAttribute(...)`. That removes the sleeps the F.I.R.S.T. rule forbids and the
  retry-until-green loops that hide flakiness.
- It runs on Node, which the `frontend` job already installs (`.github/workflows/ci.yml:115`, Node 24). No second toolchain enters the pipeline.
- It has a trace viewer and screenshots on failure, which give a reviewer the failing state without a debugging session.
- `@axe-core/playwright` plugs into the same test runner for the accessibility gate.

Alternatives:

- Cypress: one origin per test and a runner that lives in the browser. Multi-tab flows and file downloads need workarounds, and the
  parallel mode is a paid service. Rejected.
- WebdriverIO: works, but it brings a Selenium or driver layer and its own waiting model that the suite would have to tune. Rejected.
- Selenium or TestCafe directly: no advantage over Playwright for this project, and weaker auto-waiting. Rejected.
- Vitest with a real browser mode: keeps one runner, but it targets component tests, not a running server, and the test needs a
  process it starts itself. Rejected for this suite. It stays the runner for component tests.

## Decision 2: what the tests run against

The default is the shaded server jar built in the same run, started by the suite's global setup:

- `./gradlew :reposilite-backend:shadowJar` produces `reposilite-backend/build/libs/ingot-<version>.jar`.
- The global setup creates a fresh directory with `mkdtemp`, picks a free loopback port, and starts
  `java -jar <jar> --working-directory <dir> --port <port> --hostname 127.0.0.1 --token e2e-admin:<secret>`. The secret is generated
  per run and passed to the tests through an environment variable of the Playwright process, never written into the repository.
- The server is stopped and the directory is removed in the global teardown.
- No database server is started. SQLite is the default database, and the data directory holds it.

The alternative is the container image built by the `images` job (`ingot:ci`, `.github/workflows/ci.yml:210`). It is closer to what
operators run, and it is the target of the drop-in contract. It is rejected as the default because the test would need Docker, a
second image build and a port mapping, which roughly doubles the runtime. It stays a candidate for a scheduled run, see the open questions.

## Decision 3: browsers

Chromium only. The CI wiring installs it with `npx playwright install --with-deps chromium`. Firefox and WebKit are not in the default.
Adding them later is one entry in the `projects` list of the config; the tests do not depend on the engine.

## Decision 4: where the suite lives

`reposilite-frontend/e2e/` with `reposilite-frontend/playwright.config.js`, and `@playwright/test` in the frontend's `devDependencies`.

- The dashboard owns its npm lockfile and its Node version, so the suite uses the same `npm ci` as the `frontend` job.
- Vitest keeps its `include` at `src/**` and `server/**` (`vitest.config.js:26`), so `*.spec.js` files in `e2e/` are not picked up by
  `npm test`. The ESLint script covers `src/**`, `server/**` and `nitro.config.mjs` only, so the suite gets no lint gate unless one is
  added; the lint configuration covers the spec files in the same change.
- The production build (`vite build`) is driven by `index.html` and does not read `e2e/`, so nothing of the suite reaches the jar.
- `license-check` runs with `--production`, so the dev dependencies do not enter the license gate.

The alternative is a new `e2e/` Gradle or Node module at the repository root. It is rejected: the test subject is the frontend package
and its lockfile, and a root module would duplicate the Node setup.

## Decision 5: seeding through the public REST API only

Each test creates its own data through the public HTTP API with the bootstrap token, and does not reach into the data directory or the
database.

- Repositories are created and deleted through the settings domain endpoints (`PUT /api/settings/domain/{name}`, documented in
  `docs/gitbook/reference/http-api.md:196`). The exact body is taken from `/openapi` in the first red test, not from memory.
- Files are uploaded with the repository protocol (`PUT /<repository>/<path>`, section "Repository protocol" in
  `docs/gitbook/reference/http-api.md`).
- The suite never writes to the SQLite file and never reads it.

## Decision 6: isolation, determinism and time

- One server per Playwright run, not per test. The server is shared by all workers, so every test must be independent of the others.
- Every test derives its names from its own title and its worker index, for example `e2e-w1-browse-download`. Two tests never create
  the same repository, user or token. Names are fixed for a given test, which keeps the failure output repeatable.
- No test asserts on a list that another test can change, such as the full repository list. Assertions look for the entries the test
  created.
- `retries: 0` locally and in CI. A test that needs a retry to pass is a failing test.
- No `waitForTimeout`, no sleeps and no wall-clock reads. The suite waits through auto-waiting actions and web-first assertions.
  Where the server needs time to settle, the test waits on the visible state it expects, never on a duration.
- `trace: 'retain-on-failure'`, `screenshot: 'only-on-failure'`, `video: 'off'`.
- The random secret of the global setup is needed only for the credential, not for any assertion.

## Decision 7: runtime budget

- Target: under 5 minutes for the test steps on the GitHub-hosted runner, with about 20 flows and checks.
- Locally: `heavy npx playwright test --workers=2`. The server is a JVM and Chromium is a second process tree, so the run is a heavy
  job under the slot runner, and two workers keep the memory bound that the `heavy` runner expects.

## Decision 8: accessibility gate

`@axe-core/playwright` runs with the WCAG 2.0 A and AA and WCAG 2.1 A and AA tags on each covered page after the flow reaches its final
state. The test fails on `serious` and `critical` violations. `moderate` and `minor` findings are printed in the report and do not fail
the test. An accepted violation is recorded in the test with a reason, never with a global disable.

## Decision 9: expected-failure markers

A test for a fault whose fix is not on main is wrapped with `test.fail(true, '<change name>')`. Playwright reports it as passed while it
fails, and as a failure when it passes. Each fix change removes the marker of its own tests in its own pull request, so the suite turns
green there and the fault cannot return unnoticed.

| Test | Marker | Fault reported by the operator |
|------|--------|--------------------------------|
| Administration navigation identical on every page, one current entry | `fix-administration-navigation` | Entries differ between `/settings` and `/access`; two entries current on `/settings/audit` |
| Repositories table at 1280 px and 1920 px | `fix-administration-navigation` | Names and addresses break mid-word; row actions wrap |
| Heading and page actions visible while scrolling | `rework-administration-layout` | Heading and actions hidden under the app bar |
| Browse overview snippet and filter | `fix-browse-overview` | Build snippet scrolls sideways; filter field has no label as a filter of the list |
| Dashboard status | `fix-dashboard-status` | Read-only shown as a control; a used value above its limit shown on a card |

The sticky-heading fault is fixed by `rework-administration-layout`, which lets the page header scroll away under a sticky app bar
and gives focus targets a scroll margin. Its marker names that change.

The stray hidden label the operator reported is not covered by a separate test in this change. The accessibility gate and the
navigation test cover the visible side navigation; a dedicated check for stray visually hidden text is an open question.

## Decision 10: the CI wiring is a separate change

The job that runs this suite on pull requests, its required check and its artifact rule are in `add-dashboard-e2e-ci`, type `ci(e2e)`.
This change ships under `test(e2e)` only. The maintainer's rule that a change mixing types is two changes is applied by splitting.

## Open questions with defaults

1. Test target: the jar (default). Open: whether a nightly run should also use the image from the `images` job.
2. Browsers: Chromium only (default). Firefox as a later project.
3. Location: `reposilite-frontend/e2e/` (default). Alternative: a root `e2e/` module.
4. Accessibility threshold: `serious` and `critical` fail (default).
5. Sticky heading marker: names `rework-administration-layout`, the change that fixes it (decided, kept here for traceability).
6. Stray hidden label: no separate test in this change (default). A dedicated check would be added on request.
7. Runtime budget: under 5 minutes for test steps (default). A heavier suite would need a second worker count in CI.
