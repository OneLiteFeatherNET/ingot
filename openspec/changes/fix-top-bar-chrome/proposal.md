# Proposal

## Why

Every page of the dashboard opens with the top bar: the instance name on the left and the account menu on the right. Under
it runs a thin gradient stripe across the whole width. The maintainer looked at the Administration page on staging and
called that stripe odd, and it is easy to see why. It has no job. It does not separate the bar from the content: from 600
pixels the rail and the top bar form one frame in the `surface-container` role, and the content is a `surface` sheet with
rounded corners that already sets itself apart from that frame by tone. It does not mark a state, and it can be mistaken for
a progress indicator that never finishes. In a Windows high contrast theme it is painted in the system highlight colour and
then reads like a selection under the bar.

The design contract does not describe what the code builds either. `reposilite-frontend/DESIGN.md` still says the top bar is
`surface` and changes tone once the window has scrolled, that the rail is 80 pixels and `surface`, and that the brand band is
drawn on the Overview hero. The code has moved on: the frame of #189 keeps the bar and the rail in `surface-container` at every
scroll position, the rail is 96 pixels, and no hero draws the band.

The same top bar has a second, quieter problem: the instance name is an `h1`. The Administration frame draws its own `h1`
as well, so every Administration page has two level one headings, and a screen reader user who jumps to the first heading
lands on the instance name instead of the page. Taking the `h1` away from the bar must not leave other pages without one:
the Dashboard and the Account page start at `h2` today, and so do the "Page not found" and "no access" messages.

## What Changes

- **No stripe under the top bar.** The gradient rule is removed from the top bar on every page. The `.olf-brand-bar` class,
  its forced colours rule and the `--olf-gradient` token stay, because the theme runtime still sets the gradient from the
  operator's seed colour and the class stays available as a utility.
- **The frame stays as built.** The top bar keeps the `surface-container` role at every scroll position, the same role as the
  rail. The content stays a `surface` sheet with rounded corners, and the sticky corner pieces keep finishing those corners at
  the bar's bottom edge. There is no tone change on scroll, no border and no shadow: the sheet's tone is the edge.
- **One level one heading per page.** The instance name in the top bar is a link inside the `banner` landmark, not a heading.
  It keeps `data-ingot-component="default-header"` and `data-ingot-part="title"` on the same element, so plugin stylesheets
  that use the documented attributes keep working. The Dashboard gets an `h1` "Dashboard"; the title "My account" of the
  Account page, the title "Page not found" and the title of the no access message become `h1`, with the same text and look.
- **The design contract matches the code.** `DESIGN.md` describes the top bar and the rail as the frame they are
  (`surface-container`, 96 pixel rail, the `surface` content sheet), says the brand gradient is not drawn in the chrome, and
  replaces "Known gaps: None at present" with the real list.

Not BREAKING. No configuration key, environment variable, deployment manifest, route or stored data changes. The look of the
top bar changes (the stripe is gone), and the release notes say so under the dashboard.

### Shipping

Proposal PR `docs(openspec): propose the administration area redesign`, shared with
`rework-administration-information-architecture`, `add-administration-overview` and `rework-administration-side-navigation`.
Then one code PR under the type `fix` and the scope `ui`:

`fix(ui): drop the brand rule under the top bar`

The stripe and the double heading are defects against the written design contract and the accessibility rules of the
dashboard, so the change is a fix. Commits: `test(ui)` for the red tests, `fix(ui)` for the components, `docs(ui)` for
`DESIGN.md`. No file under `openspec/` is in the code PR.

### Dependencies

- None. This change can ship first and on its own.
- `rework-default-theme` changes the colour values the tokens carry. It does not touch the rule, the bar or the frame roles, so
  the two do not conflict. Whichever lands second rebases on the other.

### Out of scope

- The Administration page header, its heading text and its breadcrumb. They are in
  `rework-administration-information-architecture`.
- A brand band anywhere else, for example on the Browse hero. See open question 1.
- A search field, a menu or any new control in the top bar.
- A tone change of the top bar on scroll. The frame already separates bar and content.
- Colour values. They belong to `rework-default-theme`.
- The geometry of the navigation rail, the bottom bar and the content sheet.

## Capabilities

### New Capabilities

- `app-chrome`: the top bar of the dashboard, its place in the navigation frame and the heading semantics of every page.

## Impact

- **Operators:** nothing to change. The stripe under the top bar is gone.
- **Plugin authors:** the `default-header` anchor and its `title` part stay on the same element. The element of the title
  changes from `h1` to a link. A plugin stylesheet that selects `header h1` instead of the documented attribute would lose its
  target; the release notes name this.
- **Code:** `reposilite-frontend/src/components/header/DefaultHeader.vue`, `components/dashboard/DashboardView.vue`,
  `components/account/AccountView.vue`, `components/util/NotFoundView.vue`, the no access message in `pages/IndexPage.vue`, their
  tests, and `e2e/navigation-chrome.spec.js`. `src/styles/md3/color.css`, `src/style.css` and `MdNavigationRail.vue` do not
  change. No file under `reposilite-backend/` changes.
- **Documentation:** `reposilite-frontend/DESIGN.md`.

## Verification

- The tests named in the tasks are written first and seen to fail.
- `npm test` in `reposilite-frontend` passes, `src/styles/md3/color.test.js` included and unchanged.
- The Playwright cases in `e2e/navigation-chrome.spec.js` pass at 320 and 1280 pixels.
- `openspec validate fix-top-bar-chrome --strict` passes, and the dash grep prints nothing.

## Open questions

1. **Brand band elsewhere.** Default: the gradient is not drawn in the chrome at all. Alternative: draw the documented band
   at the top edge of the Browse hero only. The default is implemented; the alternative would be a separate change.
2. **Visible Dashboard heading.** Default: a visible `h1` "Dashboard" above the getting started card, styled like the Account
   title. Alternative: the same `h1` visually hidden, so the page keeps its current look.
