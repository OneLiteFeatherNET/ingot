# Proposal

## Why

The Administration tree is the operator's map, and at two widths it gets in the way.

On a wide screen the tree sits beside the page in its own scroll box. With about twenty entries it is taller than a 1080 pixel
window, so it grows a second scrollbar inside a page that already scrolls. The staging screenshot shows it: a short, separate
scrollbar next to the page's own. On a trackpad or a touch screen the inner scrollbar is hidden until used, so the list looks
complete while its last group, Monitoring and support, is cut off. Keyboard and switch users have to learn that the list
scrolls separately from the page.

On a tablet or a narrow laptop window (600 to 1239 pixels) the tree is an index page instead. To move from LDAP to Users the
operator goes back to the index, finds the group again and picks the entry: three steps for a sibling. The index rows carry
only a label, so the index gives no more hint than the label itself.

## What Changes

- **One scroll container.** From 1240 pixels the tree stays sticky beside the page only while its full height fits between the
  top bar and the save bar. When it does not fit, it scrolls with the page. It never has a scroll box of its own. The decision is
  made when the window or the tree changes size, never while scrolling, so the tree does not jump.
- **Index rows with a description.** Below 1240 pixels, where the tree is an index page, each row shows the entry's one-line
  description under its label. Plugin rows show their manifest description; a row without a description shows its label only.
- **A Sections sheet from 600 to 1239 pixels.** The page header row gets a "Sections" button beside the back arrow. It opens the
  same tree in a modal side sheet named "Administration pages", with the current entry marked and, once
  `add-administration-overview` is in, the filter at the top. Choosing an entry opens it and closes the sheet. Escape or the close
  button closes it and returns focus to the button. Below 600 pixels nothing changes: the index is the page.

Not BREAKING. No configuration key, environment variable, deployment manifest, route or stored data changes. The 1240 and 600
pixel breakpoints stay.

### Shipping

Proposal PR `docs(openspec): propose the administration area redesign`, shared with `fix-top-bar-chrome`,
`rework-administration-information-architecture` and `add-administration-overview`. Then one code PR under the type `feat` and the
scope `ui`:

`feat(ui): keep the administration tree in reach at every width`

The sheet is new behaviour and the scroll change replaces a behaviour that `rework-administration-layout` specified on purpose, so
the change is a feature, not a fix. Commits: `test(ui)` for the red tests, `feat(ui)` for the layout and the sheet, `docs(ui)` for
`DESIGN.md`, `docs(gitbook)` for the reference page. No file under `openspec/` is in the code PR.

### Dependencies

- `rework-administration-information-architecture` must be merged first: the index rows show its descriptions.
- `add-administration-overview` should be merged first, so the sheet carries the filter. Without it the sheet shows the tree
  without the filter; nothing breaks.
- `rework-administration-layout` (merged): this change MODIFIES its requirement "Side navigation does not overlap bars"
  (capability `administration-layout`), which asked the tree to scroll inside itself.
- `use-wide-screens`: touches the same frame for the content cap; no requirement overlaps.

### Out of scope

- Collapsible groups, a drawer, an expanded rail, tabs between routes.
- Changing the 600 and 1240 pixel breakpoints.
- A sheet below 600 pixels.
- The tree's content, labels and order (`rework-administration-information-architecture`).

## Capabilities

### Modified Capabilities

- `administration-layout`: the sticky rule of the side navigation, the index rows and the sheet below 1240 pixels.

## Impact

- **Operators:** nothing to change. No second scrollbar; on a tablet a sibling page is two taps away.
- **Plugin authors:** nothing to change. Plugin entries appear in the sheet and the index like every other entry. The new sheet
  component gets an `experimental` anchor name.
- **Code:** `reposilite-frontend/src/components/layout/SideNavLayout.vue`, `components/layout/AdministrationFrame.vue` (Sections
  button), a new `components/md/MdSideSheet.vue`, a new `src/helpers/treeFits.js` (pure fit decision) and its composable,
  `src/anchors/registry.json` (the sheet's name). No file under `reposilite-backend/` changes.
- **Documentation:** `reposilite-frontend/DESIGN.md` (adaptive navigation, the side sheet primitive),
  `docs/gitbook/reference/dashboard-pages.md` ("Layout at four widths").

## Verification

- Every red test in the tasks is seen to fail first.
- `npm test` in `reposilite-frontend` passes; the Playwright suite `layout.spec.js` passes at 320, 900, 1366 by 768, 1920 by 1080
  and 2560 by 1440 pixels.
- `openspec validate rework-administration-side-navigation --strict` passes, and the dash grep prints nothing.

## Open questions

1. **Sections sheet.** Default: shipped from 600 to 1239 pixels. Alternative: keep back to index only.
2. **A tree that does not fit at 1080 pixels.** Default: it scrolls with the page. Alternative: show only the current group open,
   which needs a collapse control and hides entries.
3. **Sheet on phones.** Default: none below 600 pixels; the index is the page. Alternative: the same sheet from the bottom.
