# Design

## Inventory

Read from the code on `main` at the time of writing. "Now" describes what a visitor sees today. The motion tokens are in
`reposilite-frontend/src/styles/md3/shape-elevation-motion.css`. The search that produced the list is
`git grep -n -E "animate-|transition|<Transition|duration-md|ease-md"` in `reposilite-frontend/src`.

| Surface | Now | Proposed |
| --- | --- | --- |
| Top-level route changes (Overview, Browse, Search, Access, Analytics, Console, Maintenance, Audit, Administration rail) | `IndexPage.vue` swaps the content of `<main>` in one `v-if` chain with no transition. `create.ts` moves focus to `#main` after the navigation. | Fade through (group 2) |
| Administration side-nav page switches | `SideNavLayout.vue` keys the page column by route name and entry, so it runs `animate-md-page-enter` (200 ms, fade and a 4 pixel rise). The side navigation and the header do not move. A query change keeps the page. | Shared axis X by tree order (group 3), replacing the rise |
| Tab switches (`AppTabs` over `MdTabs`: system information, maintenance, settings, security, repository) | The content swaps at once. The active indicator changes colour (`transition-colors`, 150 ms). | Indicator slides to the selected tab, panel on shared axis X by tab index (group 5) |
| Snippets card tabs (`SnippetsCard.vue`, its own slide) | Slides 60 pixels with `.1s` and `transition: opacity .1s ease, transform .1s ease`, literal values outside the tokens. Direction follows the index already. | Same direction logic on the tokens (group 5) |
| Menus and dropdowns (`MdOverflowMenu` in the account and repository menus, `MdSelect` and `MdListbox` options, `CardMenu` tab-style menu) | `MdOverflowMenu` and `MdSelect` mount with `v-if` and have no transition. `MdListbox` uses `v-show` and has no transition. `CardMenu` changes its indicator colour only. | Scale and fade from the anchor edge (group 4) |
| Dialogs (`MdDialog` and the `VueFinalModal` dialogs, login, settings, access, maintenance) | Dialogs use the transition of vue-final-modal, which the components do not override. The exact default classes are to be confirmed against the installed version before group 4. | Scrim fade, panel fade and scale (group 4) |
| Side sheets (`MdSideSheet`: the Sections sheet from 600 to 1239 pixels, and the failures and review sheets) | Fade only, 200 ms in and 150 ms out, off under reduced motion (`MdSideSheet.vue`). | Scrim fade, panel slides from its edge (group 4) |
| Snackbars and toasts (`MdSnackbar`, `src/store/snackbar.ts`) | `<Transition name="md-snackbar">` fades and moves 8 pixels over 150 ms. A new message replaces the old one under a key with no `mode`, so the leaving and entering messages overlap for 150 ms. | Slide up 16 pixels, leave then enter on replacement (group 4) |
| Expand and collapse (`MdDisclosure`, the `NodeTreeView` rows, the system information `details`, `ArrayListRenderer`, `RunHistory`, `TeamLegacyGrants`, `SsoProviderDialog`) | Native `details` open and close at once. `MdDisclosure` rotates its chevron over 150 ms. | Height from `0fr` to `1fr`, content fades, chevron rotates (group 6) |
| List rows added or removed (`FileList`, `DetailedListEntry`, `RepositoriesTable`, search results) | No transition. Row actions fade on hover (`transition-opacity`). `NodeRow` plays a one-off highlight (`animate-md-node-highlight`). | Added row fades in over `short3` (group 7). Removal stays instant (D10) |
| Mobile navigation (bottom bar `MdNavigationBar` below 600 pixels, the "More" panel above it, and the Sections sheet) | The bar changes colour. The "More" panel mounts with `v-if` and no transition. | "More" panel rises 8 pixels and fades (group 4) |
| Sign-in dialog second step (`LoginModal.vue`) | The step swaps with `v-if` and no transition. Focus goes to the step heading. | Shared axis Y inside the dialog (group 7) |
| Loading to content (`MdStatePanel` loading state, `ViewLoading`, the skeleton of the settings page) | The skeleton bars fade in over 800 ms after a 200 ms delay (`animate-skeleton-fade-in`, `ease-out`). When the content arrives it replaces the skeleton at once. | Skeleton fades out, content fades in over it (group 7) |
| Administration plugin pages (`settings-plugin`, `PluginPageView` in `AdministrationFrame`) | Same as the Administration pages: no route transition. | Same route transition as every page, opt-out through the descriptor (group 2) |

## Pattern map

Each pattern names one Material 3 transition and the tokens it uses. Durations are the `duration-md-*` tokens and easings
are the `ease-md-*` tokens, so a component never writes a number.

| Pattern | Used for | Enter | Leave | Mode |
| --- | --- | --- | --- | --- |
| Fade through | top-level destinations | opacity 0 to 1, `short4` (200 ms), `standard-decelerate` | opacity 1 to 0, `short2` (100 ms), `standard-accelerate` | `out-in` |
| Shared axis X | sibling Administration pages, tab panels | opacity 0 to 1 and `translateX` from 16 px toward 0 (sign of the direction), `short4`, `standard-decelerate` | opacity to 0 and `translateX` 16 px against the direction, `short2`, `standard-accelerate` | `out-in` |
| Shared axis Y | nested steps (login second step, migration steps) | opacity 0 to 1 and `translateY` from 8 px to 0, `short4`, `standard-decelerate` | opacity to 0, `short2`, `standard-accelerate` | `out-in` |
| Container transform | none in this change (see Out of scope) | | | |
| Menu from anchor | menus, select listbox | opacity 0 to 1 and `scale` from 0.92, `transform-origin` at the anchor edge, `short4`, `standard-decelerate` | opacity to 0, `short3` (150 ms), `standard-accelerate` | default |
| Dialog | scrim and panel | scrim opacity 0 to 1, `short4`. Panel opacity 0 to 1 and `scale` 0.95 to 1, `medium1` (250 ms), `emphasized-decelerate` | scrim `short3`. Panel `short4`, `emphasized-accelerate` | default |
| Side sheet | `MdSideSheet` | scrim as the dialog. Panel `translateX` from minus 100% to 0, `medium2` (300 ms), `emphasized-decelerate` | panel `short4`, `emphasized-accelerate` | default |
| Snackbar | `MdSnackbar` | opacity 0 to 1 and `translateY` from 16 px to 0, `short4`, `standard-decelerate` | opacity to 0 and `translateY` to 16 px, `short3`, `standard-accelerate` | `out-in` on replacement |
| Expand and collapse | disclosures | height `0fr` to `1fr` through `grid-template-rows`, `medium2` (300 ms), `standard` | the same in reverse, `medium2` | default |
| Content fade | loaded content over a skeleton | opacity 0 to 1, `short4`, `standard-decelerate`; the skeleton leaves at `short2` | | |
| Indicator slide | the selected tab mark | `translateX` and `scaleX` to the selected tab, `short4`, `standard-decelerate` | | |

Totals: every page transition is at most 300 milliseconds (100 out plus 200 in), as the rule below requires. The dialog
panel runs 250 milliseconds, and the side sheet 300 milliseconds, because they are not page transitions.

## Rules

- **Transform and opacity only.** A transition animates `transform` and `opacity`. The one exception is the height of an
  expand and collapse, which uses `grid-template-rows` on a wrapper and does not move another element.
- **No layout shift.** A transition never changes the box of a neighbour. Moving content by `translate` does not change
  layout. The side navigation, the header and the top bar keep their positions during a page transition, and the e2e
  checks measure this.
- **No motion on first paint.** Vue `<Transition>` gets no `appear` on the route content, the disclosures or the
  dialogs. The first page a visitor opens is drawn at once. The skeleton keeps its own 200 millisecond delay, which is
  not a transition.
- **Page transitions are at most 300 ms in total.** Out and in together.
- **Focus moves to the new page heading, without waiting for the transition.** The focus is set when the new page is
  inserted into the document, which is the start of its enter. It is never set in an `after-enter` hook. The heading
  is the first `h1` of the page, with `tabindex="-1"` set by the page header component. A page without a heading gets
  focus on `main`, as today. A first navigation, a query change and a hash change do not move focus, as
  `dashboard-accessibility` already states. Screen readers hear the page from the focus, and the transition does not
  delay the announcement.
- **Interrupting navigation cancels cleanly.** A navigation that starts during a leave makes the leave finish and then
  inserts the latest page only. The page that was interrupted is never inserted. No transition class is left on an
  element after the transition, and a page with an enter that never ended is not left in the middle of its animation,
  because the next insertion removes its classes.
- **Reduced motion is an instant swap.** Under `prefers-reduced-motion: reduce` every transition of the dashboard is
  instant. The single rule in `src/style.css` already sets every duration to 0.01 milliseconds with `!important`, and
  each pattern uses the same rule. A pattern adds no motion of its own under reduced motion, and no transform is left
  on an element. The focus and the content do not depend on the duration, so they behave the same. This choice keeps one
  reduced-motion rule (`dashboard-design-tokens`, "One reduced-motion rule") and follows
  `dashboard-accessibility`, "Reduced motion is honoured on every page". The fade under reduced motion is not used (D4).
- **Plugin pages take the route transition, and a plugin can opt out.** A plugin page descriptor may carry an optional
  `motion` field with the values `route` (the default) and `none`. With `none`, the transition into that page is an
  instant swap and focus still moves to its heading. An absent field is `route`, so every existing plugin keeps its
  behaviour. The field is read by the dashboard; the server change that adds it is a separate change.
- **Plugin anchors stay on the same elements.** The transitions are applied with the `<Transition>` of Vue, which adds
  classes to the element it wraps and adds no element. The wrapped element is the existing root of the page (the child of
  `<main>`, the page column of `SideNavLayout`, the panel of a dialog). No `data-ingot-*` attribute moves, and no
  selector that names a part, a component or a page changes what it matches.
- **Only token durations and easings.** A motion class names `duration-md-*`, `ease-md-*` or `--md-sys-motion-*` values.
  A literal duration (`.1s`, `300ms`) or a `duration-300` class in a changed file fails the style gate.

### Risks

- **A transform makes a containing block for fixed descendants.** A transform on an ancestor of a `position: fixed`
  element puts that element relative to the ancestor while the transition runs. `MdSideSheet` is `fixed` and is not
  teleported, and it sits inside `AdministrationFrame`. The route transition must therefore wrap the page content and
  never the frame that holds an open sheet, or the sheet must be teleported. The first group checks this with a sheet
  opened during a transition.
- **Overflow during a slide.** A 16 pixel slide can create a horizontal scroll at the edge of the window. The page sheet
  already clips with `overflow-x-clip`, and the group that adds the slide checks the 320 pixel width.
- **`out-in` delays the insertion by the leave.** The new page is inserted 100 milliseconds after the navigation, so the
  focus moves 100 milliseconds after the click. This is the price of a single page at a time. An overlap of the two pages was rejected (D5).
- **Sticky elements inside a transformed ancestor.** A sticky element is sticky within its nearest scrolling ancestor. A
  transform on an ancestor does not change that, but the transform must be removed when the transition ends, which the
  Vue classes do. The side navigation is not inside the transformed page column, and the e2e check measures it.
- **Native `details` closes at once.** A height animation on the close needs the `open` attribute to stay until the
  transition ends. The disclosure component keeps the content open during the leave and removes `open` after it.
- **Snackbar replacement.** The `out-in` replacement makes a new message wait 150 milliseconds. A test pins the order of
  the messages, so that no message is lost.
- **Dialog defaults are not confirmed.** The vue-final-modal defaults are to be read from the installed package before the
  dialog group. If they do not fade, the dialog group sets the transition names on the modal.

## View Transitions API against Vue `<Transition>`

The same-document View Transitions API (`document.startViewTransition`) takes a snapshot of the page before and after a
change and animates between the two snapshots.

**Browser support** (to be checked against MDN and the target browser list in the code PR of group 2): Chromium has the
same-document API since version 111, Safari since 18, and Firefox has it in a recent release. The support is not a
baseline, so the API needs a feature check and a fallback, which here is the instant swap.

**Against Vue `<Transition>`:**

- Vue `<Transition>` covers every surface in the inventory: route content, menus, dialogs, sheets, snackbars, disclosures,
  tab panels and the skeleton. The View Transitions API covers the page only, so the dashboard would need both systems.
- Vue `<Transition>` runs in every browser the dashboard supports, with the same classes. Its classes can be asserted in
  unit tests. The API needs a browser to run, and the unit tests cannot see it.
- The API captures a screenshot of the page and blocks pointer and keyboard input on the page until the transition ends.
  A visitor who types into a filter during a navigation would lose the input. Vue `<Transition>` does not block input.
- The API has no direction cue except through named view transition types, which are set per call and need their own
  feature check. Shared axis needs that cue, so the API would add a second mechanism for direction.
- The API takes a snapshot of the whole document. A plugin stylesheet that sets `view-transition-name` on a plugin element
  would change the snapshot in a way that no test covers.
- Its strength is the shared element. A card that opens into a detail could morph with it. No such card exists in the
  dashboard today (the repository row opens a route with no shared element), so that strength does not pay for the
  second mechanism now.

**Recommendation: Vue `<Transition>` for this change.** One system covers every surface, works the same in every
supported browser, keeps input live, gives a direction cue with two classes, and can be tested without a browser. The
View Transitions API is not adopted in this change. It is the candidate for a later change that adds a shared element
(a container transform from a card into a detail). That change would add it behind a feature check, with the instant
swap as its fallback, and would not replace the Vue transitions.

## Components

- `src/styles/md3/motion-transitions.css` (new, group 1): the named transition classes, one set per pattern, built from
  `--md-sys-motion-*` values and the `duration-md-*` and `ease-md-*` utilities. It holds no literal duration.
- `src/components/md/MdTransition.vue` (new, group 1): a wrapper around Vue `<Transition>` with the props `pattern`
  (`fade-through`, `shared-x`, `shared-y`, `menu`, `dialog`, `sheet`, `snackbar`, `expand`, `fade`), `direction`
  (`-1`, `0` or `1`), `mode` and `appear` (false by default). It maps the pattern to the class names. It renders the
  default slot and adds no element of its own.
- `src/composables/useFocusOnInsert.ts` (new, group 2): the focus of a page heading on insertion, with `main` as the
  fallback (D6). A pure function decides the target, so the test needs no timer.
- `src/helpers/motionDirection.ts` (new, group 3): the direction of a sibling change from two indexes. A pure function.

No new runtime dependency is added.

## Testing

Every test follows F.I.R.S.T. and is written before the code it tests.

- **Unit (Vitest, `npm test`).** Vue `<Transition>` is stubbed by the test utilities, so the tests assert the props the
  components pass to it (the name, the mode, the `appear` flag) and the class names of the shared pattern file. A
  reduced-motion test stubs `matchMedia` and asserts the rule that the single reduced-motion block in `src/style.css`
  still covers the transition classes. The focus target is a pure function, tested with two inputs and no timer. Where
  a test needs time (the out-in order of a snackbar), it uses fake timers and advances them by the token value.
- **Unit: the transition contract.** One test per pattern asserts its duration token, easing token and that it names no
  literal value. One test asserts that no motion class uses a `transition` shorthand with a literal duration.
- **End to end (Playwright, `--workers=2`, through `heavy`).** A route change moves focus to the page heading, with
  `expect(...).toBeFocused()` and no fixed wait. A reduced-motion emulation reads no transform on the page column and no
  transition longer than 0.01 milliseconds. A second navigation during a transition shows only the last page and
  leaves no transition class behind. The side navigation and the header keep the positions that the layout checks
  measure while the page transitions. A sheet opened during a transition is positioned at the window edge.
- **Style gate.** The gate accepts only the token durations and easings in the motion code. It fails on a literal
  `.1s`, `300ms` or `60px` slide in a `<style>` block of a changed file, and on a `duration-<number>` class. The existing
  gate already rejects `duration-<number>` classes; the new rule covers the `<style>` blocks, which the gate does not
  scan today.
- **Accessibility.** The axe run of `e2e/accessibility.spec.js` is clean on every page that gets a transition.

## Decisions

The ten open questions were settled by the maintainer on 2026-11-17, by adopting the recommended default of each. Each
entry names the question, the answer and the alternative that was not adopted. Groups implement these answers; the
tasks and the spec scenarios follow them.

- **D1. Strength of the page transition (question 1, resolved 2026-11-17).** Fade through for top-level pages (100
  milliseconds out, 200 in, no movement), and a 16 pixel shared axis for sibling pages. Not adopted: fade only everywhere,
  which removes the direction cue.
- **D2. Motion on mobile (question 2, resolved 2026-11-17).** The same patterns at every width. The bottom "More" panel
  rises 8 pixels and fades. Not adopted: no route transition below 600 pixels.
- **D3. User setting for reduced motion (question 3, resolved 2026-11-17).** The operating system setting only, through the
  single rule in `src/style.css`. No visitor setting and no new persisted preference. Not adopted: an account menu setting.
- **D4. Fade under reduced motion (question 4, resolved 2026-11-17).** An instant swap, as the single rule gives. No
  exception to that rule and no 100 millisecond fade. Not adopted: a fade that keeps the change visible.
- **D5. Out-in or overlap for pages (question 5, resolved 2026-11-17).** Out-in: the old page leaves, then the new page is
  inserted, and focus lands on the new page at insertion. Not adopted: overlapping the two pages, which needs absolute
  positioning of the outgoing page.
- **D6. Focus target (question 6, resolved 2026-11-17).** The page heading (its first `h1`, with `tabindex="-1"`), with
  `main` as the fallback. This modifies the focus requirement of `dashboard-accessibility`. Not adopted: keeping `main`.
- **D7. Plugin opt-out granularity (question 7, resolved 2026-11-17).** Per page, through the optional `motion` field of
  the page descriptor, with the values `route` (default) and `none`. Not adopted: a per-plugin switch.
- **D8. Expand and collapse technique (question 8, resolved 2026-11-17).** `grid-template-rows` from `0fr` to `1fr` on a
  wrapper, which works in every browser the dashboard supports. `interpolate-size` is not used in this change. It may be
  added later as a progressive enhancement, with the grid as the fallback and no change to the markup.
- **D9. Snippets tabs (question 9, resolved 2026-11-17).** Their literal values move to the tokens, with the same direction
  logic as the other tab panels. No documented exception is kept. Not adopted: keeping the current values.
- **D10. Row removal (question 10, resolved 2026-11-17).** A removed row disappears at once, and an added row fades in over
  the `short3` token (group 7). No transition group is used for removals. Not adopted: a fade-out of removed rows.
