# Proposal

## Why

Every page of the dashboard opens with the top bar: the instance name on the left and the account menu on the right. Under
it runs a thin gradient stripe across the whole width. The maintainer looked at the Administration page on staging and
called that stripe odd, and it is easy to see why. It has no job. It does not separate the bar from the content (the bar
already has its own surface), it does not mark a state, and it can be mistaken for a progress indicator that never finishes. In a
Windows high contrast theme it is painted in the system highlight colour and then reads like a selection under the bar.

The stripe is also not what the design contract asks for. `reposilite-frontend/DESIGN.md` says the brand band belongs at
the top edge of a section, and that the top bar separates itself from the content by changing its tone once the window has
scrolled. That tone change was never built, and the stripe took its place.

The same top bar has a second, quieter problem: the instance name is an `h1`. The Administration frame draws its own `h1`
as well, so every Administration page has two level one headings, and a screen reader user who jumps to the first heading
lands on the instance name instead of the page.

## What Changes

- **No stripe under the top bar.** The gradient rule is removed from the top bar on every page. The `.olf-brand-bar` class,
  its forced colours rule and the `--olf-gradient` token stay, because charts and the theme runtime still use the gradient,
  and the operator seed colour keeps driving it.
- **The bar separates by tone.** At rest the top bar uses the `surface` role. Once the window has scrolled it takes
  `surface-container`, with the `short4` colour transition of the motion tokens. There is no border and no shadow. With
  `prefers-reduced-motion` the change is instant.
- **One level one heading.** The instance name in the top bar is a link inside the `banner` landmark, not a heading. It keeps
  `data-ingot-component="default-header"` and `data-ingot-part="title"` on the same element, so plugin stylesheets that use the
  documented attributes keep working.
- **The design contract matches the code.** `DESIGN.md` describes the top bar as built, says the brand gradient is not drawn
  in the chrome, corrects the rail width (96 pixels, not 80) and replaces "Known gaps: None at present" with the real list.

Not BREAKING. No configuration key, environment variable, deployment manifest, route or stored data changes. The look of the
top bar changes, and the release notes say so under the dashboard.

### Shipping

Proposal PR `docs(openspec): propose the administration area redesign`, shared with
`rework-administration-information-architecture`, `add-administration-overview` and `rework-administration-side-navigation`.
Then one code PR under the type `fix` and the scope `ui`:

`fix(ui): drop the brand rule under the top bar`

The stripe and the double heading are defects against the written design contract, so the change is a fix. Commits:
`test(ui)` for the red tests, `fix(ui)` for the component, `docs(ui)` for `DESIGN.md`. No file under `openspec/` is in the
code PR.

### Dependencies

- None. This change can ship first and on its own.
- `rework-default-theme` changes the colour values the tokens carry. It does not touch the rule or the bar, so the two do
  not conflict. Whichever lands second rebases on the other.

### Out of scope

- The Administration page header, its heading text and its breadcrumb. They are in
  `rework-administration-information-architecture`.
- A brand band anywhere else, for example on the Browse hero. See open question 1.
- A search field, a menu or any new control in the top bar.
- Colour values. They belong to `rework-default-theme`.
- The navigation rail and the bottom bar.

## Capabilities

### New Capabilities

- `app-chrome`: the top bar of the dashboard, its separation from the content and its heading semantics.

## Impact

- **Operators:** nothing to change. The stripe under the top bar is gone, and the bar changes tone when the page scrolls.
- **Plugin authors:** the `default-header` anchor and its `title` part stay on the same element. The element of the title
  changes from `h1` to a link. A plugin stylesheet that selects `header h1` instead of the documented attribute would lose its
  target; the release notes name this.
- **Code:** `reposilite-frontend/src/components/header/DefaultHeader.vue` and its test, `e2e/navigation-chrome.spec.js`.
  `src/styles/md3/color.css` does not change. No file under `reposilite-backend/` changes.
- **Documentation:** `reposilite-frontend/DESIGN.md`.

## Verification

- The tests named in the tasks are written first and seen to fail.
- `npm test` in `reposilite-frontend` passes, `src/styles/md3/color.test.js` included and unchanged.
- The Playwright cases in `e2e/navigation-chrome.spec.js` pass at 320 and 1280 pixels.
- `openspec validate fix-top-bar-chrome --strict` passes, and the dash grep prints nothing.

## Open questions

1. **Brand band elsewhere.** Default: the gradient is not drawn in the chrome at all. Alternative: draw the documented band
   at the top edge of the Browse hero only. The default is implemented; the alternative would be a separate change.
