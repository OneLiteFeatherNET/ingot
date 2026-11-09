# Proposal

## Why

The dashboard is the one page of Ingot that people open every day, and it is also the page that is hardest to use with
a keyboard or a screen reader and the one the browser re-downloads most often.

A keyboard user who opens the dashboard has to Tab through the whole top bar and the navigation before reaching the
content. The page has no `main` landmark, so a screen reader user cannot jump to the content either (WCAG 2.2 success
criteria 2.4.1 Bypass Blocks and 2.4.3 Focus Order). When a user moves to another page inside the dashboard, focus is not
moved to the new page, so a screen reader user hears nothing about the change. Reduced motion is
honoured by some components and not by others, so a user who has asked for less motion still gets the animations of the
rest of the page (criterion 2.3.3, Level AAA).

The browser side is the other half. The embedded dashboard that the backend serves sends no `Cache-Control` at all, so the hashed bundles under `/assets/`
are left to the browser's default caching and the index document has no stated policy. The Nitro dashboard server, which the split deployment uses, already sends the caching values that belong
here (`reposilite-frontend/server/lib/security-headers.js`), so the two deployments answer the same request differently.
Neither deployment sends `Cross-Origin-Opener-Policy`, so a page that opens the dashboard in a popup shares its browsing
context group with the opener.

## What Changes

- **C1 Cache-Control on the embedded dashboard.** `/assets/<path>` answers with
  `Cache-Control: public, max-age=31536000, immutable` when the file exists. The dashboard document (`/`, `/index.html`
  and the application paths that the backend answers with the dashboard) answers with `Cache-Control: no-cache`. A missing
  asset gets no caching header. Responses that Maven answers keep the headers Maven sets.
- **C2 Cross-Origin-Opener-Policy on the dashboard document.** The dashboard document carries
  `Cross-Origin-Opener-Policy: same-origin`, next to the policy it already has. No other response gets it.
- **C3 Nitro parity.** The Nitro dashboard server sends `Cross-Origin-Opener-Policy: same-origin` on its document response,
  so both servers answer the document with the same caching and opener headers. Its caching values stay as they are.
- **A1 Main landmark.** The page content of the dashboard is wrapped in `<main id="main" tabindex="-1">`. The header and
  the navigation stay outside it.
- **A2 Skip link.** The first focusable element of the page is a link "Skip to main content" that targets `#main`. It is
  visually hidden until it receives focus. Activating it moves focus to `main` without changing the URL.
- **A3 Focus on in-app navigation.** When the router changes the path inside the dashboard, focus moves to `main` once
  the new page has rendered. The first page load does not move focus, and a change of the query alone (the filters that
  `url-state` keeps in the address) does not move it either.
- **A4 Global reduced motion.** A rule in the base layer of `src/style.css` shortens animation and transition durations to
  0.01ms and removes the iteration of animations for every element while `prefers-reduced-motion: reduce` matches.
  Existing per-component rules keep working.
- **A5 Charts.** The ECharts option keeps `animation: false`, so charts never animate, with or without the preference.
  `TimeSeriesChart.vue` already sets this; the change adds a test that pins it and makes no production change for it.

### Out of scope

- Other differences between the two servers. The Nitro server sends `Referrer-Policy: same-origin` and a `form-action 'self'`
  directive that the backend does not send (`SecurityHeaders.kt:95` and `DASHBOARD_POLICY`). Those are recorded for a
  separate change. The parity tests here cover caching and the opener policy only.
- The custom frontend directory (`CustomFrontendHandler`). Operator-supplied files keep the headers they have today.
- Response headers on `/api`, repository content and the Maven routes.
- A full WCAG audit, an automated axe run (roadmap N15) and a Playwright suite. Those need their own change.
- Changes to the dashboard layout, colours or copy beyond the skip link text.
- Any `Cache-Control` for Maven or repository paths.

## Capabilities

### New Capabilities

- `dashboard-delivery`: the caching and opener headers of the embedded dashboard and the Nitro dashboard server.
- `dashboard-accessibility`: the main landmark, the skip link, focus on in-app navigation, reduced motion and chart animation.

## Impact

- **Type and scope.** The change ships under `fix(frontend)`. Part of the diff is in `reposilite-backend`
  (`com.reposilite.frontend` and `net.onelitefeather.ingot.web`). It stays in one change because it is the same
  dashboard and the same type. The frontend scope already names backend changes to the dashboard, such as
  `fix(frontend): vary colliding repository paths on fetch metadata`. Commits use the types `test`, `fix` and `docs`,
  each with the scope `frontend`. The pull request title, squash-merged onto main, is:

  `fix(frontend): add bypass blocks, route focus and dashboard caching headers`

- **Breaking marker.** No breaking change. No configuration key, environment variable, volume, manifest or plugin has to
  change. A Reposilite installation pointed at Ingot behaves as before for every Maven and Gradle client and for every
  plugin. The title carries no `!` and no commit carries a `BREAKING CHANGE:` footer.
- **Behaviour that changes by default.** Browsers cache the hashed bundles for a year and revalidate the document on each
  load. Dashboard popups no longer share a browsing context group with their opener. Keyboard and screen reader users get
  a skip link, a landmark and focus on navigation. Users with reduced motion see fewer animations.
- **Opt-in.** Nothing is opt-in. The change is a fix with no switch.
- **Code.** `reposilite-backend` (`frontend/infrastructure/FrontendHandler.kt`, `web/SecurityHeaders.kt`), the Nitro dashboard
  server (`reposilite-frontend/server/lib`, `server/routes`), the dashboard (`reposilite-frontend/src`) and
  `docs/gitbook/reference/http-api.md`.
- **Coordination.** `harden-http-security` (merged) already sets the dashboard headers in `SecurityHeaders.kt`. The opener
  header goes into the same dashboard branch; the spec deltas do not conflict because they name different headers.

## Verification

- Targeted tests per commit. Backend integration tests run through `heavy` as
  `:reposilite-backend:integration --tests '<pattern>'` with `--max-workers=2`, never the full suite.
- Frontend tests run with `npx vitest run` on the changed files. Each new test is seen to fail before its production
  change.
- `openspec validate fix-dashboard-a11y-and-caching --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/fix-dashboard-a11y-and-caching` prints nothing.
