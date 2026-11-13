# Tasks

Each group is one or more commits of one Conventional Commits type. Every test is written first and seen to fail before the
production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock, no network (the client is a
fake that counts calls), a fresh store, `can`, plugin manifest and router per test, assertions with messages. The filter's
matching is a pure function tested with plain input and output. `npm test` runs in `reposilite-frontend`, targeted with a file
pattern while working, through `heavy`. Playwright runs through `heavy` at 320 and 1280 pixels. No file under
`reposilite-backend/` changes.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose the administration area redesign`: this change and its three siblings, nothing else.
  Verify: `openspec validate add-administration-overview --strict` passes and the dash grep prints nothing.

## 2. Red tests for the tree

- [ ] 2.1 `test(ui)`: in `src/navigation/administration.test.js`, assert Overview is the first entry for a visitor with the
  settings node and absent without it, and that `currentTreeKey` gives `page:overview` for `/settings` and `settings:javadoc` for
  `/settings/javadoc`. Verify: it fails, because there is no Overview entry and the hub has no current key.
- [ ] 2.2 `test(ui)`: in a new `src/navigation/adminFilter.test.js`, assert the matches of the spec scenarios ("web", "nginx",
  "rebuild index", "retention", "logo", "acme" on a fake plugin page with `group: "Security"` whose title does not contain the
  id, "xyz"), that the current entry is always kept, that empty groups are dropped, and that order follows the tree, Overview
  first. Verify: it fails, because `adminFilter.js` does not exist.
- [ ] 2.3 `test(ui)`: in `src/components/layout/SideNavLayout.test.js`, assert the labelled search field, the live region text for
  one, two and no matches, Escape clearing with focus kept, and Enter calling the router with the first match. Verify: it fails.
- [ ] 2.4 `test(ui)`: assert the Unsaved mark and the accessible name "Javadoc, unsaved changes" for a draft in Javadoc, and the mark
  on Repositories for a draft in `maven`. Verify: it fails.
- [ ] 2.5 `test(ui)`: in `src/navigation/contributions.test.js`, a fake manifest with plugin sections in `Repository` and `System`:
  assert each section is still listed under Artifacts or Instance after `SETTINGS_GROUPS` is gone. Verify: it passes before the
  removal and is kept as the guard.
- [ ] 2.6 `test(ui)`: in `src/components/layout/SideNavLayout.test.js`, with `narrow` set and Overview current, assert that the
  page slot and the index are both drawn, the index lists Overview first with `aria-current="page"`, and no back arrow is drawn; with
  `narrow` set and `settings:javadoc` current, assert the back arrow to `/settings` and no index. Verify: it fails, because today a
  current entry hides the index below 1240 pixels.

## 3. Red tests for Overview

- [ ] 3.1 `test(ui)`: in `src/components/settings/SettingsHub.test.js`, assert Needs attention rows for drafts, "Review changes"
  opening the review sheet, a conflict row, "Nothing needs attention." with no draft, At a glance counts rendered together after the
  saved settings load (none while loading), a plugin collection count, the dashboard link only with the dashboard node, no link that
  duplicates a tree entry, and the `settings-hub` anchor on the root. Verify: it fails on the card grid.
- [ ] 3.2 `test(ui)`: in the `SettingsView` test, assert Import settings and Download as JSON on `/settings` only, not on
  `/settings/javadoc` or `/settings/repositories`, no filled button in the header, and that an import marks domains as unsaved without
  a server call. Verify: it fails on the actions slot of every settings page.
- [ ] 3.3 `test(ui)`: with the fake client, assert that opening `/settings` makes no request that the current hub does not make.
  Verify: it passes before and after, as the guard against new reads.
- [ ] 3.4 `test(ui)`: e2e: in `e2e/navigation.spec.js` and `e2e/layout.spec.js`, replace hub card assertions with Overview (current
  entry, Needs attention, no card grid) at 320 and 1280 pixels; at 320 and 900 pixels assert the order header, Needs attention,
  index with Overview marked current, At a glance, and no back arrow on `/settings`; add a filter case ("nginx" opens Reverse
  proxy). In `e2e/accessibility.spec.js:41`, change the settle marker of `/settings/repositories` to the Repositories table's
  accessible name and add `/settings` with "Import settings". Verify: they fail against the current build.
- [ ] 3.5 `test(ui)`: in `src/components/layout/AdministrationFrame.test.js`, assert on `/settings` the `h1` Overview and a
  breadcrumb with the single crumb Administration that is not a link, and on `/settings/audit` the crumb Administration as a link to
  `/settings` followed by Monitoring and support as text. Verify: it fails on the `h1` Administration of the landing page.

## 4. Implementation

- [ ] 4.1 `feat(ui)`: add the Overview entry and its current key to `registry.js`. Verify: 2.1 passes.
- [ ] 4.2 `feat(ui)`: add `src/navigation/adminFilter.js` and render the field and the live region in `SideNavLayout.vue`. Verify:
  2.2 and 2.3 pass.
- [ ] 4.3 `feat(ui)`: pass the draft keys to `SideNavLayout` and draw the Unsaved mark. Verify: 2.4 passes.
- [ ] 4.4 `feat(ui)`: replace the card grid in `SettingsHub.vue` and `settingsHub.js` with Needs attention, At a glance and the
  dashboard link; keep the anchor on the root; drop the "Sign-in and security live in Access" line. Verify: 3.1 and 3.3 pass.
- [ ] 4.5 `feat(ui)`: render Import settings and Download as JSON on Overview only. Verify: 3.2 passes.
- [ ] 4.6 `feat(ui)`: remove `SETTINGS_GROUPS`, `SETTINGS_FALLBACK_GROUP`, `SECTION_SETTINGS_GROUPS` and `settingsGroupOf`. Verify:
  2.5 still passes and nothing else imports them.
- [ ] 4.7 `feat(ui)`: draw the page and the index together on `/settings` below 1240 pixels in `SideNavLayout.vue`, without a
  back arrow, and give Overview its single plain crumb in `AdministrationFrame.vue`. Verify: 2.6 and 3.5 pass.

## 5. Documentation

- [ ] 5.1 `docs(gitbook)`: in `docs/gitbook/reference/dashboard-pages.md`, replace "Settings hub" with "Overview" (what it shows,
  that Import and Download live there, that the card grid is gone and the `settings-hub` anchor stays), and describe the filter under
  "Administration tree". Verify: `grep -rn "card" docs/gitbook/reference/dashboard-pages.md` finds no hub card text, and the dash
  grep prints nothing.

## 6. Check

- [ ] 6.1 Run `npm test` and the Playwright suites `navigation.spec.js`, `layout.spec.js`, `repositories.spec.js` and
  `accessibility.spec.js` through `heavy`, at 320 and 1280 pixels. Verify: all pass, and each changed expectation of an existing
  test is listed in the pull request.

## 7. Pull request

- [ ] 7.1 Open the code pull request under the title `feat(ui): replace the settings card grid with an administration overview`,
  from the remote main branch that already contains the merged proposal and the merged code of
  `rework-administration-information-architecture`, with no file under `openspec/`. Fill the repository's pull request template.
