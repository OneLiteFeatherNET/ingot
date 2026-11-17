# Proposal

## Why

The maintainer's report is short: animations and transitions between menus and pages are missing. A visitor who moves
from Browse to Search, from one Administration entry to the next, from one tab to the other, or who opens a menu, sees
the new state appear in the same frame as the click. Nothing says where the new state came from, which direction it
moved in, or that the old one has gone. The dashboard already has a small motion vocabulary (the MD3 duration and easing
tokens, a linear progress animation, a snackbar fade, a skeleton fade and one page enter on the Administration pages), but
it is used in one place for pages and in none for menus, tabs, expansion or the mobile navigation.

The inventory behind this proposal is in `design.md`, section "Inventory". In short: of the eleven kinds of state change
that a visitor meets, one has a page transition (the Administration page column when the entry changes), a few have a
small fade or a colour change (the snackbar, the side sheet, the disclosure chevron), and the rest switch instantly. The
existing transitions are not uniform either: the snippets tabs slide with literal 100 millisecond and 60 pixel values
instead of the tokens, which makes them the first thing a motion contract has to cover.

The motion has to be done carefully, because the dashboard is built to stay still. Its layout is measured by tests, its
plugin anchors are selectors that plugins depend on, and it must keep its focus order and its reduced-motion behaviour.
A motion change that moves boxes, adds wrappers or waits for an animation before focus moves would break one of those.
This change therefore starts with a contract (the patterns, the durations, the rules) and implements it in small groups.

## What Changes

- A motion contract for the dashboard, written as requirements in the new capability `dashboard-motion`. It names the
  Material 3 pattern for each kind of state change (fade through, shared axis X, shared axis Y, container transform,
  scale and fade from an anchor, fade and scale for dialogs, slide up for snackbars, height animation for expand and
  collapse) and the token durations and easings each one uses.
- A shared set of named transition classes and one wrapper component built from the existing motion tokens, so that a
  component says which pattern it uses and never writes a duration. The tokens stay in
  `src/styles/md3/shape-elevation-motion.css`.
- Route transitions for the top-level destinations: Overview, Browse, Search, Access, Analytics, Console, Maintenance,
  Audit and the Administration entries. Plugin pages get the same transition, and a plugin page can opt out.
- Focus after an in-app navigation moves to the page heading, as soon as the new page is in the document. It does not
  wait for the transition to end. This modifies the focus requirement of `dashboard-accessibility`, which currently
  names `main`. The `main` element stays the fallback for a page without a heading.
- Sibling pages (the Administration entries, the tabs of a page) slide by their order: the direction follows the index,
  so Back moves the other way.
- Menus, the listbox of a select, dialogs, side sheets, the mobile "More" panel and snackbars get their pattern. The
  snackbar and the dialog keep the placement and the structure that the catalogue and dialog changes give them.
- Tab indicators slide to the selected tab. Expand and collapse (the details disclosure, the key tree, the system
  information rows) animate their height with `grid-template-rows` from `0fr` to `1fr`, with a fallback to the native
  behaviour where the technique is not available.
- Loading to content: a loaded state fades in over the skeleton, without a delay that the visitor waits on.
- Reduced motion keeps its single rule in `src/style.css`. Under `prefers-reduced-motion: reduce` every transition is an
  instant swap. No pattern adds a motion of its own that the rule does not cover.
- Tests are part of each group: unit tests for the transition contract and the reduced-motion path, end-to-end checks
  for the focus and the layout, and a style gate that accepts only token durations.

## Capabilities

### New Capabilities

- `dashboard-motion`: the patterns, the durations and easings, the route, focus and reduced-motion rules, the plugin page
  opt-out, and the surfaces that must move.

### Modified Capabilities

- `dashboard-accessibility`: the focus requirement "Focus moves to the page on in-app navigation" changes its target from
  `main` to the page heading, with `main` as the fallback, and states that focus does not wait for the transition.

Nothing else changes. The reduced-motion requirement of `dashboard-accessibility` and the motion token requirements of
`dashboard-design-tokens` stay as they are and are referred to, not copied.

## Impact

- **Operators:** nothing to configure. The dashboard moves between states, and the motion can be turned off with the
  operating system setting that already switches off animations. There is no new setting in this change.
- **Plugin authors:** plugin pages take the route transition. A plugin page can opt out through an optional `motion`
  field of its page descriptor. That field is added by a separate server change (see Dependencies). Until it exists,
  every plugin page animates, and no plugin can opt out. Plugin anchors (`data-ingot-*`) keep their elements and their
  names, and no wrapper element is added.
- **Configuration and data:** no configuration key, environment variable, deployment manifest, route, permission node or
  stored data changes. No file under `reposilite-backend/` changes in this change. Nothing here is BREAKING.
- **Code:** `reposilite-frontend/src/styles/md3/` (named transition classes), `src/components/md/` (the transition
  wrapper and the primitives that use it), `src/pages/IndexPage.vue` (the route transition on the content of `main`),
  `src/router/create.ts` (the focus target after a navigation), `src/components/layout/SideNavLayout.vue` (sibling
  direction), `src/components/tabs/AppTabs.vue` and `src/components/md/MdTabs.vue`, `src/components/md/MdDisclosure.vue`
  and the other disclosures, `src/components/card/SnippetsCard.vue` (its literal values move to the tokens), and the
  loading states of `src/components/md/MdStatePanel.vue`.
- **Documentation:** `reposilite-frontend/DESIGN.md` gets the motion contract in place of the single page-enter
  paragraph, and `docs/gitbook/references/plugins/dashboard-anchors.md` and
  `docs/gitbook/manage/plugins/extend-the-dashboard-from-a-plugin.md` get the route transition and the opt-out.

## Verification

- Every group starts with failing tests that are seen to fail on the current build.
- Unit tests run with `npm test` in `reposilite-frontend`, targeted while working, without timers and without wall-clock
  waits. Vue Transition is stubbed by the test utilities, so the tests assert the transition names, modes and classes the
  components pass, and the reduced-motion path through a stubbed `matchMedia`.
- End to end through Playwright (`--workers=2`, run through `heavy`): a route change moves focus to the heading, a
  reduced-motion emulation has no transform, a second navigation during a transition shows only the last page, and the
  side navigation and the header keep their positions while the page transitions.
- The style gate accepts only token durations and easings in the motion code, and fails on a literal `.1s`, `60px` slide
  or a `duration-300` class in a changed file.
- `openspec validate add-dashboard-motion --strict` passes, and the dash grep prints nothing.

## Dependencies

- `add-md3-component-catalogue` owns the markup of `MdSnackbar`, `MdSkeleton` and `MdLinearProgress` and removes the
  `<style>` block of the snackbar. Snackbar motion (group 4) starts after it is on `main`.
- `unify-dialogs-menus-and-feedback` owns the dialog frame, the end side sheet and the menu item contract. Dialog and
  menu motion (group 4) starts after it is on `main`.
- `rework-admin-header-and-repository-tabs` group 2 adds `animate-md-page-enter`, a fade with a 4 pixel rise for the
  Administration page column. This change replaces the rise with the sibling direction of group 3, so group 3 starts after
  that group is merged. Until then, this change does not touch the page column.
- A separate server change, `add-plugin-page-motion-option`, adds the optional `motion` field to the plugin page
  descriptor. It is a `feat` of the server and is not part of this change, which keeps this change to one type.

## Shipping

This change ships under the Conventional Commits type `feat` and the scope `ui`. Every code pull request of this change
carries that type and scope. The docs of the change are updated in the code pull request of the group that they describe,
and the last group updates the plugin docs, so the change stays one type.

Proposal PR: `docs(openspec): propose add-dashboard-motion`, containing only the files under
`openspec/changes/add-dashboard-motion/`, tasks unchecked, branched from the remote main branch. It is merged before the
first code pull request is opened.

Code pull requests, in this order, each under the title named in its group:

| Order | Group | Pull request title |
| --- | --- | --- |
| 1 | Motion primitives | `feat(ui): add the motion primitives for dashboard transitions` |
| 2 | Route transitions and focus | `feat(ui): fade between top-level pages and move focus to the heading` |
| 3 | Sibling pages | `feat(ui): slide administration pages by their order in the tree` |
| 4 | Menus, dialogs, sheets and snackbars | `feat(ui): animate menus, dialogs, sheets and snackbars from their anchor` |
| 5 | Tabs | `feat(ui): slide the tab indicator and the tab panel` |
| 6 | Expand and collapse | `feat(ui): animate the height of expanding and collapsing sections` |
| 7 | Loading and steps | `feat(ui): fade loaded content over the skeleton and step the sign-in dialog` |
| 8 | Documentation | `feat(ui): document the dashboard motion contract and the plugin opt-out` |

Each code pull request is one revertable change of the list above. Specs and code are separate pull requests.

## Out of Scope

- Shared element transitions (container transform from a card into a detail). No such card exists today, and the
  Repository row opens a route without a shared element. The View Transitions API is evaluated in `design.md` and is not
  adopted in this change.
- Motion on list rows that are added or removed, apart from the fade of an added row. Removing a row with motion would
  reflow the list and move the rows under the pointer.
- Motion in the charts. The chart rule (`dashboard-accessibility`, "Charts do not animate") stays.
- The server side of the plugin opt-out, and any new setting for reduced motion.
- Changes to the content, labels, order or permissions of any page or menu.
- Legal and commercial questions.

## Open Maintainer Questions

The questions for the maintainer, each with a default that the tasks implement, are at the end of `design.md`.
