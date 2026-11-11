# Tasks

This change ships as one code pull request, `fix(ui): make the browse snippet readable and scope the repository filter`.
Its commits use the types `test` and `fix` with the scope `ui`, one type per commit. Each test is written first and seen to
fail before the code it covers exists. Every test follows F.I.R.S.T.: no sleeps or timers, no system clock, no shared
mutable state, no order dependence, and assertions with messages that name the behaviour.

Frontend tests run as `npx vitest run <files>` in `reposilite-frontend`. The full frontend suite runs as `npm test` before the
pull request is opened. No file under `reposilite-backend/` changes, so no backend run is needed.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose fix-browse-overview`: this change (proposal, design, capability spec, tasks) and nothing
  else. Verify: `openspec validate fix-browse-overview --strict` passes, and `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/fix-browse-overview`
  prints nothing.

## 2. Snippet card (defects D1 to D4 of `design.md`)

- [ ] 2.1 `test(ui)`: write failing tests before the snippet changes:
  - `components/card/RepositorySnippet.test.js`: the URL value carries the class `snippet-url` with `white-space: normal` and
    `overflow-wrap: anywhere` (read the class list, no layout), and the text content of the snippet has no zero-width space,
    no `<wbr>` and no added line break (D2);
  - `components/card/SnippetsCard.test.js`: on the repository path `/browse/releases` the heading contains "Repository details:
    releases" (replaces the assertion at line 170 and 197 with the new heading, keeping the collapsed-state assertion); on the
    root path the heading is "Use a repository" and the caption names `<repository>` (D3); the status region exists before any
    click and is empty (D4); after a successful copy its text is "Copied" and it is the same element (D4);
  - `components/overview/RepositoryOverview.test.js`: the snippet card follows the repository section in the DOM order (D1).
  Verify: `npx vitest run src/components/card src/components/overview` fails on each new assertion, and the failure messages
  name the behaviour. Commit the failing tests only after they are seen to fail locally; the fix commit follows.
- [ ] 2.2 `fix(ui)`: implement D1 (the card follows the list in the DOM, the grid placement unchanged), D2 (the URL span in
  `RepositorySnippet.vue` and the `snippet-url` class with the wrap rule), D3 (the heading and the root caption in
  `SnippetsCard.vue`, the caption outside the `pre` element) and D4 (one status region with its text set from `copyState`).
  Verify: the tests of 2.1 pass. The existing `SnippetsCard.test.js` and `RepositorySnippet.test.js` tests pass, except the two
  heading assertions that 2.1 changed on purpose. List those changes in the pull request.

## 3. Repository filter, type chips and rows (defects D5 to D8 of `design.md`)

- [ ] 3.1 `test(ui)`: write failing tests in `components/overview/RepositoryOverview.test.js` before the filter changes:
  - the field has the accessible name "Filter repositories" and the supporting text of D5, and no element with `role="search"`
    is an ancestor of it;
  - typing `tag:ci` shows only repositories with the tag `ci`, and the status region reads "N of M repositories";
  - the status region is in the DOM while the list loads;
  - with text that matches nothing, the empty state has a link "Search artifacts for <text>" whose target is the search route
    with `q` set to the text; with blank text and a tag chip that matches nothing, the link is absent and "Clear filters" is shown (D6);
  - the type chips are in two groups named "Repository type" (Hosted, Proxy, Group) and "Derived type" (Mixed), and the
    Derived group has the sentence of D7 linked by `aria-describedby`;
  - selecting Mixed writes `type=mixed` to the address (the assertion at line 454 keeps its value and changes only its group);
  - the row's type badge and visibility label are in one element that is placed after the text column and has `flex-wrap`
    (D8, checked by class and order, not by layout).
  Verify: `npx vitest run src/components/overview` fails on each new assertion. Update the chip assertion at line 435 only where
  the groups change, and list it in the pull request.
- [ ] 3.2 `fix(ui)`: implement D5 to D8 in `components/overview/RepositoryOverview.vue`: replace `SearchBar` with an outlined
  `MdTextField` (label, supporting text, leading filter icon), apply `parseSearch` from `store/maven/repositories.js` to the
  text, keep the status region rendered while loading, add the artifact search link to the empty state, split the type chips
  into two groups, and wrap the trailing marks for widths below 600px. `components/search/SearchBar.vue` stays unchanged, because
  the artifact search page still uses it. Verify: the tests of 3.1 pass, and `components/search/SearchBar.test.js` passes unchanged.

## 4. Documentation

- [ ] 4.1 `docs(ui)`: check `docs/gitbook/reference/dashboard-pages.md` (the row for `/`, line 22, says "Browse. Lists the
  repositories.") and `docs/gitbook/reference/repository-types.md` (line 63 documents `mixed`). The Browse page row does not name
  the filter, so it changes only if this change adds a sentence to it. If the Derived type group is added, add one sentence to
  `repository-types.md` that the Browse page groups `mixed` as a derived type. Verify: `grep -rn "Search by id" docs/gitbook`
  prints nothing, and every route named on the changed pages exists in `reposilite-frontend/src/router/routes.js`.

## 5. Verification before the pull request

- [ ] 5.1 Visual widths are covered by `add-dashboard-e2e-tests`, which is not on origin/main yet. That change runs the
  320px, 1280px and 1920px checks of the proposal (no horizontal scrollbar for the URL, the row marks inside the row at 320px,
  the card below the list below 1024px). Verify: if it is on main when the pull request is opened, its run is linked in the
  pull request. If it is not, the pull request carries one screenshot per width, taken by hand (open question Q5 of `design.md`).
- [ ] 5.2 Confirm the staging overlap (open question Q6 of `design.md`): check that the staging build contains `fa0c8d1a1`
  or later. If it does, the copy overlap is a new defect and gets its own failing test before any fix. Verify: the answer is
  written in the pull request description.
- [ ] 5.3 Run `npm test` and the project lint in `reposilite-frontend` once all groups are green. Verify: both pass with no
  test skipped that was passing before.

## 6. Pull request

- [ ] 6.1 `fix(ui)`: open the code pull request with the title `fix(ui): make the browse snippet readable and scope the repository filter`.
  Read `.github/pull_request_template.md` and fill its sections: the user story (a visitor copies a build URL on a wide screen,
  a visitor filters for an artifact name), the verification (the test names of 2.1 and 3.1, and the result of 5.1), and the
  reviewer notes (the changed expectations of 2.1 and 3.1, and the answer of 5.2). Verify: `gh pr view --json title` shows the
  exact title, the commit-style hook accepts the title and every commit, and the body has no em or en dash and no session link.
