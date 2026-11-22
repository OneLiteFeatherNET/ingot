# Tasks

Each group is one Conventional Commits type and scope, `feat(ui)`, and each group is one code pull request under the title
named in its heading. The groups are merged in order. Each group starts from the remote main branch after the previous group is
merged. No file under `openspec/` is in a code pull request, and no file under `reposilite-backend/` changes in this change.

Every test is written first and seen to fail on the current build before the production code exists. Every test follows
F.I.R.S.T.: no sleeps and no wall-clock waits, no system clock (fake timers only where a test needs time), no network, a fresh
router, component and store per test, and assertions with messages. Transitions are asserted through the props and class names
the components pass, because the test utilities stub Vue `<Transition>`. Layout and focus are asserted in Playwright with role
selectors and `getBoundingClientRect`, with no fixed timeouts. `npm test` runs in `reposilite-frontend`, targeted with a file
pattern while working. Playwright runs with `--workers=2`. Heavy jobs run through `heavy`.

Screenshots: every group that changes what a visitor sees attaches before and after screenshots at 320, 600 and 1440 pixels, in
light and dark. Motion is not visible in a still image, so each group also attaches a short recording of the transition it adds.

## 1. Proposal

- [x] 1.1 `docs(openspec): propose add-dashboard-motion`: this change and nothing else, tasks unchecked, branched from the remote
  main branch. Verify: `openspec validate add-dashboard-motion --strict` passes, and the dash grep prints nothing.

## 2. Group 1: motion primitives

Pull request title: `feat(ui): add the motion primitives for dashboard transitions`

- [x] 2.1 `test(ui)`: in `src/styles/md3/motion-transitions.test.ts`, assert that every named transition class in
  `src/styles/md3/motion-transitions.css` takes its duration from a `--md-sys-motion-duration-*` token or a `duration-md-*`
  utility, and holds no literal duration. Verify: it fails, because the file does not exist.
- [x] 2.2 `test(ui)`: in `src/components/md/MdTransition.test.ts`, assert that each pattern name maps to the expected Vue transition
  name, mode and `appear` flag, that `appear` is false by default, and that the wrapper adds no element of its own (the rendered
  root is the slot child). Verify: it fails, because `MdTransition.vue` does not exist.
- [x] 2.3 `test(ui)`: in `src/components/md/MdTransition.test.ts`, assert that an unknown pattern name is rejected with a message
  and renders the slot without a transition. Verify: it fails, as 2.2 does.
- [x] 2.4 `test(ui)`: in `src/style.test.ts`, assert that the single `prefers-reduced-motion` block of `src/style.css` still covers
  the transition classes and that no other stylesheet of the dashboard sets a transition duration under that media query. Verify:
  it fails for the new classes, which are not yet covered by a test of the base layer.
- [x] 2.5 `test(ui)`: in `src/helpers/motionDirection.test.ts`, assert the direction for two indexes: `1` when the target is later,
  `-1` when it is earlier, and `0` when they are equal, with a fixed list of three entries. Verify: it fails, because the helper
  does not exist.
- [x] 2.6 `feat(ui)`: add `src/styles/md3/motion-transitions.css` with the named classes for the nine patterns of the design
  (`fade-through`, `shared-x`, `shared-y`, `menu`, `dialog`, `sheet`, `snackbar`, `expand`, `fade`), built from the motion tokens.
  Verify: 2.1 passes.
- [x] 2.7 `feat(ui)`: add `src/components/md/MdTransition.vue` as the wrapper of Vue `<Transition>`, and `src/helpers/motionDirection.ts`.
  Verify: 2.2, 2.3 and 2.5 pass.
- [x] 2.8 `test(ui)`: in `src/components/md/MdTransition.test.ts`, assert with a stubbed `matchMedia` that under reduced motion the
  wrapper renders the same slot with no motion class of its own. Then make it pass in `MdTransition.vue`. Verify: 2.4 passes.
- [ ] 2.9 Run `npm test` for the new files through `heavy`, targeted, and the style gate. Verify: the gate passes, and the gate
  scan of the new file finds no literal duration.
- [x] 2.10 Open the code pull request `feat(ui): add the motion primitives for dashboard transitions`, with the template body and the
  recording. Verify: `gh pr view -R OneLiteFeatherNET/ingot` shows the exact title, and the body has no em or en dash.

## 3. Group 2: route transitions and focus

Pull request title: `feat(ui): fade between top-level pages and move focus to the heading`

Starts after group 1 is merged.

- [x] 3.1 `test(ui)`: in `src/router/create.test.ts`, assert that a navigation between two top-level pages moves focus to the target
  decided by the pure function of the focus helper, and that a first navigation, a query change and a hash change move no focus.
  Verify: it fails for the heading case, because focus goes to `main` today.
- [x] 3.2 `test(ui)`: in `src/composables/useFocusOnInsert.test.ts`, assert the target: the first `h1` of the page when it has one,
  `main` otherwise, and `null` when neither exists. Verify: it fails, because the composable does not exist.
- [x] 3.3 `test(ui)`: in `src/pages/IndexPage.test.ts`, assert that the content of `main` is wrapped by `MdTransition` with the
  `fade-through` pattern, that the key changes with the section and not with the query, and that the first mount has no `appear`.
  Verify: it fails, because the content has no transition.
- [x] 3.4 `test(ui)`: in `src/components/plugins/PluginPageView.test.ts`, assert that a plugin page without a `motion` field takes
  `fade-through`, that `motion: 'none'` gives an instant swap with focus still on the heading, and that an unknown value takes
  `fade-through`. Verify: it fails, because the field is not read.
- [x] 3.5 `test(ui)`: in `e2e/navigation.spec.js`, assert that moving from Overview to Search focuses the heading of Search, with
  `toBeFocused()` and no fixed wait, and that a query change keeps focus where it was. Verify: it fails for the heading.
- [x] 3.6 `test(ui)`: in `e2e/navigation.spec.js`, assert under reduced-motion emulation that the page is drawn with no transform on
  its content, and that focus still moves to the heading. Verify: it fails, because the heading receives no focus today.
- [x] 3.7 `test(ui)`: in `e2e/navigation.spec.js`, assert that a second navigation started during the first transition shows only
  the second page, and that no `*-leave-active` class remains after the transition. Verify: it fails until 3.9 lands. It waits on
  the page role and on the absence of the class, never on a time.
- [x] 3.8 `test(ui)`: in `e2e/layout.spec.js`, assert that the side navigation and the header keep their boxes while the page
  transitions, within half a pixel, and that a sheet opened during a transition sits at the window edge. Verify: the sheet case
  fails if the transition wraps the frame, which is why 3.9 wraps the page content only.
- [x] 3.9 `feat(ui)`: wrap the content of `main` in `src/pages/IndexPage.vue` with `MdTransition` (`fade-through`, `out-in`, the
  section as the key). Add no element. Apply the change to the page content only, not to the frame, so that the sheet of the frame is
  not moved. Verify: 3.3 and 3.8 pass.
- [x] 3.10 `feat(ui)`: add `src/composables/useFocusOnInsert.ts` and use it in `src/router/create.ts` and in the page heading. Focus
  is set at the insertion of the page, not in an after-enter hook, and a page without a heading takes `main`. Verify: 3.1, 3.2 and
  3.5 pass.
- [x] 3.11 `feat(ui)`: read the optional `motion` field of the plugin page descriptor in `PluginPageView.vue`, with `route` as the
  default and `none` as the opt-out. Verify: 3.4 passes.
- [ ] 3.12 Run the layout and navigation e2e specs through `heavy`, with `--workers=2`, and the accessibility spec for the pages of the
  change. Verify: they pass, and the axe result of each page is clean.
- [x] 3.13 Attach the before and after screenshots and the recording of the fade, and open the code pull request `feat(ui): fade
  between top-level pages and move focus to the heading`. Verify: `gh pr view -R OneLiteFeatherNET/ingot` shows the exact title.

## 4. Group 3: sibling pages

Pull request title: `feat(ui): slide administration pages by their order in the tree`

Starts after group 2 is merged and after `rework-admin-header-and-repository-tabs` group 2 is merged, because this group replaces
its `animate-md-page-enter` rise.

- [x] 4.1 `test(ui)`: in `src/components/layout/SideNavLayout.test.ts`, assert that a change of entry between two siblings passes
  `shared-x` with the direction of the two tree positions, that a query change passes no direction and keeps the page, and that the
  side navigation is not keyed. Verify: it fails, because the page column has the rise of the older token.
- [x] 4.2 `test(ui)`: in `src/helpers/motionDirection.test.ts`, assert the direction for entries across two groups of the tree, using
  the order of the flat list. Verify: it fails, because the tree order is not passed to the helper yet.
- [x] 4.3 `test(ui)`: in `src/components/layout/SideNavLayout.test.ts`, assert that the page column no longer carries
  `animate-md-page-enter` and carries the `shared-x` class of the pattern. Verify: it fails, as 4.1 does.
- [x] 4.4 `test(ui)`: in `e2e/layout.spec.js`, assert under reduced-motion emulation that the page column has no transform while the
  entry changes, and that the side navigation keeps its position. Verify: it fails until 4.5 lands, because the rise is not removed
  by the reduced-motion rule in the base layer for the new classes.
- [x] 4.5 `feat(ui)`: in `src/components/layout/SideNavLayout.vue`, wrap the page column in `MdTransition` with `shared-x` and the
  direction from `motionDirection`, and remove `animate-md-page-enter` from the page column. Remove the `md-page-enter` keyframes from
  `src/styles/md3/shape-elevation-motion.css` if nothing else uses them. Verify: 4.1 to 4.4 pass.
- [x] 4.6 Scope check: this group changes the page column and, if unused, the keyframes only. The `DESIGN.md` entry is updated in
  group 8. Verify: the diff of this group has no file under `docs/`.
- [ ] 4.7 Run the layout e2e spec through `heavy`, with `--workers=2`, and the unit tests of the layout. Verify: they pass, and the
  axe result of each Administration page is clean.
- [x] 4.8 Attach the before and after screenshots at 320, 600 and 1440 pixels, and the recording of the entry change, and open the code
  pull request `feat(ui): slide administration pages by their order in the tree`. Verify: `gh pr view -R OneLiteFeatherNET/ingot`
  shows the exact title.

## 5. Group 4: menus, dialogs, sheets and snackbars

Pull request title: `feat(ui): animate menus, dialogs, sheets and snackbars from their anchor`

Starts after `unify-dialogs-menus-and-feedback` and `add-md3-component-catalogue` are merged, because both own the markup of these
components.

- [ ] 5.1 `test(ui)`: in `src/components/md/MdDialog.test.ts`, assert that the dialog passes the `dialog` pattern to the modal's
  overlay and content transitions, and that the transition names come from the pattern and not from literal strings. Verify: it
  fails, because the modal uses the library defaults.
- [ ] 5.2 `test(ui)`: in `src/components/md/MdSideSheet.test.ts`, assert that the panel passes the `sheet` pattern, that the scrim
  fades, and that under reduced motion the transition is off, as the existing test states. Verify: it fails for the slide.
- [ ] 5.3 `test(ui)`: in `src/components/md/MdSnackbar.test.ts`, assert the `snackbar` pattern with `out-in` on replacement, that a
  second message is inserted only after the first has left, and that no two messages are drawn at once. Verify: it fails, because
  there is no mode.
- [ ] 5.4 `test(ui)`: in `src/components/md/MdOverflowMenu.test.ts` and `src/components/md/MdSelect.test.ts`, assert that the menu
  passes the `menu` pattern and that its `transform-origin` class follows the placement above or below the trigger. Verify: it fails,
  because the menu has no transition.
- [ ] 5.5 `test(ui)`: in `src/components/md/MdListbox.test.ts`, assert that the list passes the `menu` pattern while keeping `v-show`.
  Verify: it fails, as 5.4 does.
- [ ] 5.6 `test(ui)`: in `src/components/header/MenuPanel.test.ts`, assert that the "More" panel of the bottom bar passes the `menu`
  pattern with a rise of 8 pixels. Verify: it fails, because the panel mounts with `v-if` and no transition.
- [ ] 5.7 `test(ui)`: in `e2e/navigation.spec.js`, assert that the "More" panel and a select open and close with Escape, and that focus
  returns to the trigger, under reduced motion too. Verify: it passes today for the focus, and it fails for the transition classes.
- [ ] 5.8 `feat(ui)`: apply `MdTransition` with the patterns of this group to `MdDialog.vue`, `MdSideSheet.vue`, `MdSnackbar.vue`,
  `MdOverflowMenu.vue`, `MdSelect.vue`, `MdListbox.vue` and the "More" panel in `IndexPage.vue`, and remove the `<style>` block of
  `MdSnackbar.vue` if the catalogue change has not removed it. Verify: 5.1 to 5.6 pass.
- [ ] 5.9 Before 5.8, read the default transition classes of the installed vue-final-modal and note them in the test of 5.1, so the
  test states which default it replaces. Verify: the note names the classes, and the test title names the pattern.
- [ ] 5.10 Run the component tests of the group and the dialog e2e specs through `heavy`, with `--workers=2`. Verify: they pass, and
  focus is returned to the trigger of every dialog and menu.
- [ ] 5.11 Attach the before and after screenshots and the recordings of a menu, a dialog, a sheet and a snackbar replacement, and open
  the code pull request `feat(ui): animate menus, dialogs, sheets and snackbars from their anchor`. Verify: `gh pr view` shows the
  exact title.

## 6. Group 5: tabs

Pull request title: `feat(ui): slide the tab indicator and the tab panel`

Starts after group 4 is merged.

- [x] 6.1 `test(ui)`: in `src/components/md/MdTabs.test.ts`, assert that the indicator of the selected tab is positioned with a
  transform from the tab's offset and width, and that the panel passes `shared-x` with the direction of the two tab indexes. Verify:
  it fails, because the indicator changes colour only.
- [x] 6.2 `test(ui)`: in `src/components/tabs/AppTabs.test.ts`, assert that the wrapper passes the selection and its direction through
  unchanged, and that the `data-ingot-component="app-tabs"` anchor stays on the wrapper. Verify: it fails for the panel direction.
- [ ] 6.3 `test(ui)`: in `src/components/card/SnippetsCard.test.ts`, assert that the tab panel uses `shared-x` from the tokens, that
  no literal `.1s` or `60px` remains in its `<style>` block, and that its reduced-motion behaviour is unchanged. Verify: it fails,
  because the literal values are there.
- [x] 6.4 `test(ui)`: in `src/components/card/CardMenu.test.ts`, assert that the indicator of `CardMenu` slides with a transform and
  takes the token duration. Verify: it fails, because the indicator changes colour only.
- [ ] 6.5 `test(ui)`: in `e2e/system-info.spec.js` and `e2e/repositories.spec.js`, assert that the tab panel of the system information
  and of a repository enters from the side of the new tab, and that the `?tab=` address still follows the selection. Verify: it fails
  for the direction.
- [ ] 6.6 `feat(ui)`: apply the indicator slide and the `shared-x` panel in `MdTabs.vue`, `CardMenu.vue` and `SnippetsCard.vue`, and
  replace the literal values of `SnippetsCard.vue` with the pattern. Verify: 6.1 to 6.4 pass.
- [ ] 6.7 Run the tab tests and the e2e specs of the tabs through `heavy`, with `--workers=2`, and the keyboard tests of the tabs
  (Left, Right, Home, End). Verify: they pass, and the roving focus is unchanged.
- [ ] 6.8 Attach the before and after screenshots and a recording of a tab change in light and dark, and open the code pull request
  `feat(ui): slide the tab indicator and the tab panel`. Verify: `gh pr view` shows the exact title.

## 7. Group 6: expand and collapse

Pull request title: `feat(ui): animate the height of expanding and collapsing sections`

Starts after group 5 is merged.

- [x] 7.1 `test(ui)`: in `src/components/md/MdDisclosure.test.ts`, assert that the content is wrapped in a grid wrapper whose rows
  go from `0fr` to `1fr` on open, that the chevron uses the `short3` token, and that a closing disclosure keeps its `open` attribute
  until the transition has ended, using a fake timer advanced by the token value. Verify: it fails, because the content has no wrapper.
- [ ] 7.2 `test(ui)`: in `src/components/access/nodes/NodeTreeView.test.ts`, assert that a permission tree row opens with the same
  pattern. Verify: it fails, because the row is a native `details` without motion.
- [ ] 7.3 `test(ui)`: in `src/components/support/SystemInfoView.test.ts`, assert that the key tree sections open with the pattern, and
  that a section that is open at load has no transition on the first paint. Verify: it fails for the open case.
- [ ] 7.4 `test(ui)`: in `src/components/renderers/ArrayListRenderer.test.ts` and `src/components/maintenance/RunHistory.test.ts`,
  assert that their `details` use the pattern. Verify: it fails, as 7.2 does.
- [ ] 7.5 `test(ui)`: in `e2e/system-info.spec.js`, assert that the key tree opens and closes with the height animation, the page does
  not move below the section, and reduced motion opens the section at once. Verify: it fails for the height.
- [ ] 7.6 `feat(ui)`: add the expand pattern to `MdDisclosure.vue`, and to `NodeTreeView.vue`, `SystemInfoView.vue`,
  `ArrayListRenderer.vue`, `RunHistory.vue`, `TeamLegacyGrants.vue` and `SsoProviderDialog.vue`, with the grid wrapper inside each
  `details`. Verify: 7.1 to 7.4 pass.
- [ ] 7.7 `feat(ui)`: keep the `open` attribute of a closing disclosure until its transition has ended, in the component that owns it.
  Verify: 7.1 passes, and a second toggle during the close reopens the section without a flicker.
- [ ] 7.8 Run the disclosure tests and the system information e2e spec through `heavy`, with `--workers=2`. Verify: they pass, and the
  axe result of the page is clean.
- [ ] 7.9 Attach the before and after screenshots and a recording of an open and a close, and open the code pull request
  `feat(ui): animate the height of expanding and collapsing sections`. Verify: `gh pr view` shows the exact title.

## 8. Group 7: loading and steps

Pull request title: `feat(ui): fade loaded content over the skeleton and step the sign-in dialog`

Starts after group 6 is merged.

- [x] 8.1 `test(ui)`: in `src/components/md/MdStatePanel.test.ts`, assert that when the state changes from `loading` to content the
  skeleton uses `short2` to leave and the content uses `short4` to enter, with no delay of its own, and that the skeleton's own 200
  millisecond delay is unchanged. Verify: it fails, because the content replaces the skeleton at once.
- [x] 8.2 `test(ui)`: in `src/components/md/MdSkeleton.test.ts`, assert that the skeleton keeps `motion-safe:animate-skeleton-fade-in`
  and gains no transition of its own. Verify: it passes, and it guards the change against a second delay.
- [x] 8.3 `test(ui)`: in `src/components/header/LoginModal.test.ts`, assert that the second step is wrapped in `MdTransition` with
  `shared-y`, that its heading still receives focus on entry, and that a failure inside the step keeps the typed codes until they are
  cleared as today. Verify: it fails, because the step has no transition.
- [ ] 8.4 `test(ui)`: in `src/components/md/MdStatePanel.test.ts`, assert that a list row added after load fades in with `short3`, and
  that a removed row is not animated. Verify: it fails, because no row has a transition.
- [ ] 8.5 `feat(ui)`: apply the content fade in `MdStatePanel.vue`, the `shared-y` step in `LoginModal.vue`, and the row fade in the
  rows that are added after load (`FileList.vue`, `RepositoriesTable.vue`), without a `TransitionGroup` for removals. Verify: 8.1, 8.3
  and 8.4 pass.
- [ ] 8.6 Run the state and login tests and the sign-in e2e spec through `heavy`, with `--workers=2`. Verify: they pass, and the
  second-factor flow still focuses its heading.
- [ ] 8.7 Attach the before and after screenshots of a loading list and of the second step, and open the code pull request
  `feat(ui): fade loaded content over the skeleton and step the sign-in dialog`. Verify: `gh pr view` shows the exact title.

## 9. Group 8: documentation

Pull request title: `feat(ui): document the dashboard motion contract and the plugin opt-out`

Starts after group 7 is merged.

- [ ] 9.1 `test(ui)`: in `src/style.test.ts`, assert that `reposilite-frontend/DESIGN.md` names every pattern of the change and the
  `motion` field of the plugin page descriptor, and that no paragraph of the motion section still describes the removed page rise.
  Verify: it fails, because the section describes the rise.
- [ ] 9.2 Rewrite the `DESIGN.md` motion section from the pattern map of `design.md`, with the token names, the totals and the
  reduced-motion rule. The commit keeps the type `feat(ui)` of this change. Verify: 9.1 passes.
- [ ] 9.3 `feat(ui)`: update `docs/gitbook/references/plugins/dashboard-anchors.md` with the note that the transitions add no element
  and keep the anchors, and `docs/gitbook/manage/plugins/extend-the-dashboard-from-a-plugin.md` with the `motion` field, its values
  and the default. Verify: the pages name the field, and the dash grep prints nothing.
- [ ] 9.4 `feat(ui)`: update the reference of the page descriptor, if one exists under `docs/gitbook/`, and state that the field is
  read by the dashboard and written by the server change `add-plugin-page-motion-option`. Verify: the reference matches the field
  name in the code.
- [ ] 9.5 Open the code pull request `feat(ui): document the dashboard motion contract and the plugin opt-out`. Verify: `gh pr view`
  shows the exact title, and the body has no em or en dash.

## 10. Open the code pull requests

- [ ] 10.1 Open the code pull requests of groups 1 to 8, each under the title named in its heading, in the order of the groups, each
  branched from the remote main branch after the previous one is merged. Each body follows `.github/PULL_REQUEST_TEMPLATE.md` and
  describes the problem as a visitor meets it. Verify: each `gh pr view -R OneLiteFeatherNET/ingot` shows the exact title and no file
  under `openspec/`.
