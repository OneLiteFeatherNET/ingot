# Tasks

Each group is one or more commits of one Conventional Commits type. Every test is written first and seen to fail before the
production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock, no network (the client is a
fake), a fresh store, `can`, plugin manifest and router per test, no shared mutable module state, assertions with messages.
`npm test` runs in `reposilite-frontend`, targeted with a file pattern while working, through `heavy`. Playwright runs through
`heavy` at 320 and 1280 pixels. No file under `reposilite-backend/` changes.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose the administration area redesign`: this change and its three siblings, nothing else.
  Verify: `openspec validate rework-administration-information-architecture --strict` passes and the dash grep prints nothing.

## 2. Tree test (gate, no code)

- [ ] 2.1 Run the tree test of design D9 with at least five operators on a text tree of D3: the six tasks, success and time per
  task. Change only `GROUP_HEADINGS` and the labels of D3 if a task fails, and run the test again for that task. Verify: the result
  table is ready to paste into the code pull request.

## 3. Red tests for the tree

- [ ] 3.1 `test(ui)`: in `src/navigation/administration.test.js`, assert for a full administrator the headings Artifacts, Access,
  Instance, Monitoring and support, and the entries and order of the "Administration tree" requirement. Verify: it fails on the
  headings Repository, Security, System, Support and on Search and Audit under System.
- [ ] 3.2 `test(ui)`: assert that a visitor with the settings node but without the audit node sees Audit settings and not Audit
  log, and the reverse. Verify: it fails on today's placement.
- [ ] 3.3 `test(ui)`: in `src/navigation/contributions.test.js`, a fake manifest with one page per group value, one page with
  `group: "Billing"` (a value the server refuses, so only a fake manifest can carry it), and one plugin section per section
  group: assert the heading of each, the Plugins fallback last for the unexpected value, and that plugin entries follow the
  built-in ones. Verify: it fails on the headings. The backend test `PluginUiValidatorTest` "should refuse an unknown menu
  group" stays unchanged and green.
- [ ] 3.4 `test(ui)`: in `src/navigation/registry.test.js`, assert every built-in entry has a label and a non-empty description,
  that the label of `web`, `frontend`, `statistics`, `search`, `cleanup` and `audit` comes from the frontend map while an unknown
  domain uses its schema title, and that every route name and tree key is unchanged (snapshot of the key list). Verify: it fails on
  the missing descriptions and labels.
- [ ] 3.5 `test(ui)`: in `src/navigation/registry.test.js`, assert `administrationLocation` for fixed node sets, computed once
  against today's tree and kept as a table: settings node gives `settings`; console and dashboard give `console`; audit and
  console give `audit`; maintenance and dashboard give `maintenance`; every single node gives the same route as before. Verify: it
  passes before the change and is kept as the guard; it fails if the Monitoring group is ordered Analytics before Console.

## 4. Red tests for the frame

- [ ] 4.1 `test(ui)`: in `src/components/layout/AdministrationFrame.test.js`, assert one `h1` with the page label, `main` labelled
  by it, the breadcrumb Administration (link) then the group heading (no link), the description line, no empty description element
  when the description is empty, and "Provided by the acme-mirror plugin" on a plugin page. Verify: it fails on the fixed heading
  "Administration".
- [ ] 4.2 `test(ui)`: on a detail page (`/access/users/alice`), assert the trail Administration, Access, Users (link) and the `h1`
  alice. Verify: it fails.
- [ ] 4.3 `test(ui)`: in `src/components/layout/SideNavLayout.test.js`, assert each group is a list labelled by its visible heading
  through `aria-labelledby`, and that no group heading is a heading element. Verify: it fails on `aria-label` without a visible
  reference.
- [ ] 4.4 `test(ui)`: in `src/components/settings/UnsavedChangesBar.test.js` and the `SettingsView` test, assert the save bar's
  supporting text and the save confirmation name the 10 seconds, and that the description of `/settings/javadoc` does not.
  Verify: it fails on `SettingsView.vue:416`.
- [ ] 4.5 `test(ui)`: shortcut tests: Audit log and Audit settings link to each other only with the matching node; Analytics links
  to Usage statistics; Maintenance links to Search index and Cleanup policies; a user detail links to
  `/access/check?principal=<name>`. Verify: they fail.
- [ ] 4.6 `test(ui)`: in `e2e/navigation-chrome.spec.js:93`, replace `heading 'Administration', level 1` with the page name on
  `/access/users` and `/settings/web`, and the first crumb Administration; keep the `nav` named Administration that
  `e2e/navigation.spec.js:34` and `:62` select by. Add a 320 pixel case: back arrow and last crumb on one line, no horizontal
  scroll. Verify: it fails against the current build.

## 5. Implementation

- [ ] 5.1 `feat(ui)`: add `GROUP_HEADINGS`, `DOMAIN_GROUPS` and `DOMAIN_LABELS` to `registry.js`, move the entries as in the
  requirement, return `{ id, heading, links }` from `adminGroups`, and map `contributedHeading` through the headings. Remove
  `REPOSITORY_DOMAINS`. Verify: 3.1 to 3.3 pass, and 3.5 still passes.
- [ ] 5.2 `feat(ui)`: add the descriptions and filter keywords of design D3 to the registry entries, move `ADMIN_DESCRIPTIONS` out
  of `IndexPage.vue`. Verify: 3.4 passes.
- [ ] 5.3 `feat(ui)`: rework `AdministrationFrame.vue`: breadcrumb, `h1` from the current entry, manifest or detail name,
  description, plugin line, `main` labelled by the `h1`. Feed it from `SettingsView`, `AccessView`, `IndexPage` and the plugin page.
  Verify: 4.1, 4.2 and 4.6 pass.
- [ ] 5.4 `feat(ui)`: label each group of `SideNavLayout.vue` by its visible heading. Verify: 4.3 passes.
- [ ] 5.5 `feat(ui)`: move the propagation notice to `UnsavedChangesBar.vue` and the save confirmation. Verify: 4.4 passes.
- [ ] 5.6 `feat(ui)`: add the in-page shortcuts of design D6. Verify: 4.5 passes.

## 6. Documentation

- [ ] 6.1 `docs(ui)`: add the section "Administration information architecture" to `reposilite-frontend/DESIGN.md`: one home per
  page, two levels, the two order constraints of design D8, the label and description rules, the page header anatomy, and no filled button in the
  header of a page that can show a save bar. Verify: the dash grep prints nothing.
- [ ] 6.2 `docs(gitbook)`: update `docs/gitbook/reference/dashboard-pages.md` ("Administration tree", the page heading),
  `docs/gitbook/how-to/review-audit-log.md` (where the Audit log sits and its heading), and add the table "group value, heading
  shown" next to `docs/gitbook/reference/plugin-api.md:331`, keeping the four documented strings. Verify: `grep -rn "under
  Security\|heading \*\*Administration" docs/gitbook` finds no stale text, and the dash grep prints nothing.

## 7. Check

- [ ] 7.1 Run `npm test` and the Playwright suites `navigation.spec.js`, `navigation-chrome.spec.js`, `accessibility.spec.js` and
  `layout.spec.js` through `heavy`, at 320 and 1280 pixels. Verify: all pass, and each changed expectation of an existing test is
  listed in the pull request.

## 8. Pull request

- [ ] 8.1 Open the code pull request under the title `feat(ui): group and name the administration pages by operator task`, from
  the remote main branch that already contains the merged proposal, with no file under `openspec/`. Fill the repository's pull
  request template and paste the tree test result.
