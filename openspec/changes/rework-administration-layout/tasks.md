# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `ui`. Every test is written first
and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps, no system clock, no network,
a fresh `can`, store and router per test, assertions with messages. The tests check structure and classes only. The
visual result at 1280 and 1920 pixels is covered by `add-dashboard-e2e-tests`, not by these tasks.

This change starts after `fix-administration-navigation` is merged, because the frame it changes is built there.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose rework-administration-layout`: this change and nothing else. Verify:
  `openspec validate rework-administration-layout --strict` passes and the dash grep prints nothing.

## 2. Red tests for the frame

- [ ] 2.1 `test(ui)`: write failing tests for the breakpoints of `AdministrationFrame.vue`: the side navigation is an index
  (narrow) below 1240 pixels and beside the page from 1240; the page is capped at 1600 pixels from 1920 pixels. Assert the
  classes the frame draws, not the pixels. Verify: they fail on the current 600-pixel rule (`SideNavLayout.vue`, `narrow`).
- [ ] 2.2 `test(ui)`: write failing tests that the page header row is not sticky, and that the side navigation is the only
  sticky element of the page content. Verify: they fail on `SettingsView.vue` if its header carries a sticky class, and on
  any view that draws its own header.
- [ ] 2.3 `test(ui)`: write failing tests that the side navigation's sticky top is the top bar height plus 16 pixels and its
  maximum height is the viewport minus that top and 16 pixels. Verify: they fail before 4.1.

## 3. Red tests for names, addresses and the table

- [ ] 3.1 `test(ui)`: write failing tests for names and addresses: the name link and the address code carry the truncation
  classes and no `break-all`; each has `title` set to the full value; the name link's accessible name is the full name.
  Verify: they fail on `RepositoriesTable.vue:193` and `:236`.
- [ ] 3.2 `test(ui)`: write failing tests for the Actions menu: each row has one button named "Actions for <name>" with
  `aria-haspopup="menu"`; opening it shows the items named "Edit <name>", "Copy address of <name>" and "Delete <name>"; the first
  item receives focus; Escape closes it and returns focus to the button; Enter and Space open it; arrow keys move between
  items. Verify: they fail, because `MdOverflowMenu.vue` does not exist yet.
- [ ] 3.3 `test(ui)`: write failing tests for column priority: Name, Type and Actions carry no hiding class; Storage, Version
  policy, Visibility, Write policy and Address carry the responsive hiding classes of their breakpoint in the order of the
  design. The Write policy column is absent when the schema lacks the field, as today. Verify: they fail on the current table,
  which hides nothing.

## 4. Fixes

- [ ] 4.1 `feat(ui)`: change `SideNavLayout.vue` and `AdministrationFrame.vue` to the four widths, move the page header row
  above the columns, make the side navigation sticky with the stated top and maximum height from 1240 pixels, and set the
  content cap. Verify: the tests of 2.1 to 2.3 pass.
- [ ] 4.2 `feat(ui)`: change `RepositoriesTable.vue` to truncate names and addresses, set their `title`, remove `break-all`,
  and hide the columns by priority. Verify: the tests of 3.1 and 3.3 pass.
- [ ] 4.3 `feat(ui)`: add `components/md/MdOverflowMenu.vue` and use it for the row actions of `RepositoriesTable.vue`, keeping
  the Delete confirmation dialog. Verify: the tests of 3.2 pass, and the existing `RepositoriesAccessibility.test.js` and
  `RepositoriesView.test.js` pass with only the listed changes (the Edit, Copy and Delete buttons are now inside the menu).

## 5. Documentation

- [ ] 5.1 `docs(ui)`: update the layout section of `docs/gitbook/reference/dashboard-pages.md` (the four widths, the header,
  the side navigation) and the Repositories section of `docs/gitbook/reference/repository-settings.md` (the Actions menu, the
  truncated names and addresses, the columns shown at each width). Verify: `grep -rn "Edit\b.*Copy address.*Delete" docs/gitbook`
  finds no description of three inline buttons, and the dash grep prints nothing.

## 6. Layout checks

- [ ] 6.1 The visual layout at 1280 and 1920 pixels (the three-column squeeze, the mid-word breaks, the wrapped header, the
  header under the top bar) is checked by `add-dashboard-e2e-tests`. This task links that test and records the result in the
  pull request. It is not a manual check in this change. Verify: the pull request names the e2e test cases that cover 1280 and
  1920 pixels, or links the follow-up issue when they do not exist yet.

## 7. Pull request

- [ ] 7.1 Open the code pull request under the title `feat(ui): add a responsive administration layout with an actions menu`,
  from the remote main branch that already contains the merged proposal and `fix-administration-navigation`, with no file under
  `openspec/`. Fill the repository's pull request template.
