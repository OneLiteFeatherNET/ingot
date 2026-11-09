# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `frontend`. Tests are written
first and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system
clock, no shared mutable state, a temporary directory per test where files are involved, and assertions with messages. Backend
integration tests run through `heavy` as `:reposilite-backend:integration --tests '<pattern>'` with `--max-workers=2`, targeted
and never the full suite. Frontend tests run as `npx vitest run <files>`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose fix-dashboard-a11y-and-caching`: this change and nothing else. Verify:
  `openspec validate fix-dashboard-a11y-and-caching --strict` passes and the dash grep from the proposal prints nothing.

## 2. C1 to C3 Cache-Control and the opener policy

- [ ] 2.1 `test(frontend)`: write failing integration tests in `reposilite-backend/src/integration`: `immutable` on an existing
  `/assets/` file; no `immutable` on a missing one; `no-cache` on `/`, `/index.html` and an application path; a Maven answer on a
  path whose name is also a dashboard path keeps Maven's header; `Cross-Origin-Opener-Policy` on the document only; the
  existing `nosniff`, sandbox and dashboard policy headers unchanged. Verify: they fail before 2.2.
- [ ] 2.2 `fix(frontend)`: in `FrontendHandler.kt`, set `Cache-Control` on the success path of the bundled asset, index and
  application routes only; in `SecurityHeaders.kt`, add the opener policy to the dashboard branch of `apply`. Verify: the tests of
  2.1 pass, and the built `index.html` still loads in the backend test that serves it. Also check in the Vite output that every
  file under `assets/` has a content hash in its name, because the immutable header is only safe for hashed names.
- [ ] 2.3 `test(frontend)`: write failing Vitest tests in `reposilite-frontend/server/lib/security-headers.test.js`: the
  document headers contain `Cross-Origin-Opener-Policy: same-origin`, and the asset headers do not. Verify: they fail before 2.4.
- [ ] 2.4 `fix(frontend)`: export the document headers from `server/lib/security-headers.js` and use them in
  `server/routes/[...].js` for the document response only. Verify: the tests of 2.3 pass and the existing security header tests
  still pass unedited.
- [ ] 2.5 `docs(frontend)`: update the `/` and `/assets/<path>` rows in `docs/gitbook/reference/http-api.md` with the caching and
  opener headers. No setting, environment variable or manifest changes, so no other page changes.

## 3. A1 to A5 Accessibility

- [ ] 3.1 `test(frontend)`: write failing Vitest tests:
  - a new `SkipLink.test.js` for the link text, its `href="#main"`, its first position in the document, and that activating it
    focuses the `main` element without changing the address;
  - an assertion in `IndexPage.test.js` that exactly one `main#main[tabindex="-1"]` exists and that the header is outside it;
  - in `router/create.test.js`, using a memory history: a path change focuses `main` after the render; a query-only change and the
    first navigation do not change `document.activeElement`;
  - a test that reads `style.css` through a `?raw` import and finds the `prefers-reduced-motion: reduce` block;
  - a new `TimeSeriesChart.test.js` with ECharts' `init` replaced by a test double, asserting the option has `animation: false`.
  Verify: the landmark, skip link, focus and stylesheet assertions fail before 3.2 to 3.4. The chart test is the one exception: it
  passes on main because the option is already `false`, so it is committed as a regression guard and says so in its message.
- [ ] 3.2 `fix(frontend)`: add `src/components/layout/SkipLink.vue` (visually hidden until focused, click handled in code and
  focusing `main`), render it first in `App.vue`, and wrap the page content of `IndexPage.vue` in `<main id="main" tabindex="-1">`
  outside the header and the navigation. Verify: the tests of 3.1 for the link and the landmark pass and the existing
  `IndexPage` tests pass unedited.
- [ ] 3.3 `fix(frontend)`: in `router/create.js`, extend the existing `router.afterEach` so that a change of path (not query, not
  hash, and not the first navigation) focuses `main` after `nextTick`. Verify: the route focus tests of 3.1 pass and the existing
  router tests pass unedited.
- [ ] 3.4 `fix(frontend)`: add the global `prefers-reduced-motion: reduce` rule to the base layer of `src/style.css`. Verify: the
  stylesheet test of 3.1 passes, and `npm run eslint` reports nothing new on the changed files.

## 4. Pull request

- [ ] 4.1 Open the code pull request with the title `fix(frontend): add bypass blocks, route focus and dashboard caching headers`.
  Read `.github/pull_request_template.md` (or the template directory, whichever exists on main) and fill its sections. The
  description tells the story of the reader who uses a keyboard or a screen reader and of the browser that downloads the dashboard
  again on every visit, without class names. It carries no session link and no attribution line. Verify: `gh pr view --json title`
  shows the exact title, the commit-style hook accepts the title and every commit, and the body has no em or en dash.
