# Design

## One change or two

The roadmap item pairs a backend caching fix with frontend accessibility work. They stay in one change. Both are small
(effort S), both touch the same dashboard, and both ship under `fix(frontend)`. A split would make two pull requests for
two dozen lines each. The backend part goes into the classes that already serve the dashboard, not into a new module.

## Where each header is set

- `Cache-Control` for assets and the document belongs in `ResourcesFrontendHandler`, where the bundled resources are
  answered (`FrontendHandler.kt`, the asset route and the index and application routes). The status code decides the
  header: it is set only when the resource is found. Otherwise a missing asset would be cached for a year.
- `Cross-Origin-Opener-Policy` belongs in `SecurityHeaders.apply`, in the branch that already handles the dashboard
  document (`markDashboard`). That keeps every dashboard header in one place, and the branch is reached only by the
  document, not by assets.

## Why the Nitro side gets COOP too

The roadmap says the Nitro server already sets the dashboard headers. It sets the caching values
(`security-headers.js`, `CACHE_CONTROL`), but no opener policy. The parity the roadmap asks for therefore needs a new
value on the Nitro side, and the change adds it to the document response only. The parity test is two-sided: the Nitro
test reads the fixture, and the Kotlin integration test asserts the same literal values. A shared fixture file read
from both sides would need a path from the Gradle test into the frontend directory, which is more fragile than two
literals with a comment that names the other side.

## Skip link: a click handled in code

The skip link is a plain `<a href="#main">` with a click handler that calls `preventDefault()` and focuses `main`.
A default anchor click would go through the browser's fragment navigation, which the router's history listener sees as a
popstate. The router does not need that navigation, and the legacy hash mapping in `router/legacy.js` should not be
asked to handle a fragment that is not its own.

## Focus on navigation: path changes only

Focus moves to `main` in `router.afterEach`, after `nextTick`, and only when the path differs from the previous one.
Three cases must not move focus:

- the first navigation of a page load, which is the page the user just opened;
- a change of the query alone, which `url-state.js` makes each time a filter changes, and which would otherwise pull
  focus away from the control the user is using;
- a hash-only change.

The `afterEach` hook already sets the document title, so the focus call sits next to it and uses the same `to` and `from`.

## Reduced motion: one global rule, no per-component changes

The global rule in `style.css` uses 0.01ms rather than `none` for durations. Components that wait for a `transitionend`
or `animationend` event still receive it, so no component hangs. Per-component rules remain as they are; the global rule
does not replace them.

## Charts

`TimeSeriesChart.vue` already passes `animation: false` to ECharts, unconditionally. The preference therefore needs no
branch. The change adds a test that pins the option, so a later refactor cannot turn animation back on.
