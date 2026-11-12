# Proposal

## Why

Ingot opens with the colours it inherited from Reposilite. The primary is a magenta (`#ac1c67`), the containers are pink
and the chart hues lean the same way. On a dashboard that is mostly tables, status cards and graphs, the result reads as
a fan site, not as an administration tool. The maintainer finds it too pink, and an operator who puts Ingot in front of a
customer or an auditor has to explain the colour before explaining the product.

The name is the brief. An ingot is metal that has been cast and cooled, and the product is the thing that holds
artifacts for a team. A calm steel or bronze tone with neutral surfaces fits that, and it does not compete with the status
colours that carry the real meaning (healthy, read-only, failing).

Changing the default is a visible change for every installation that has no seed colour. The change is only acceptable
if an operator who wants the old look can get it back with one setting, and if nothing that is configured or built on top
of the tokens changes meaning.

## What Changes

- **A default Ingot colour.** The built-in scheme takes a steel blue seed in place of the Reposilite magenta. The light and
  dark schemes, the primary, secondary, tertiary and the neutral surfaces are regenerated from that seed. Three candidates are
  described in the design; the default recommendation is steel. The maintainer picks one in review.
- **Neutral surfaces.** Backgrounds and containers stay white and grey, with no tint from the accent. The pink tint of the
  surfaces is removed together with the accent.
- **Contrast is a tested property.** Every text and interface pair used by the dashboard meets WCAG AA (text 4.5 to 1,
  interface 3 to 1) in both schemes. A test fails when a future palette change breaks a pair.
- **Chart hues.** The eight categorical chart tokens are re-derived from the new seed, harmonised as today, and a test checks
  that the first four are distinguishable by lightness and hue from one another.
- **The token names do not change.** Every `--md-sys-color-*` and `--md-ext-color-*` name keeps its meaning. Plugins and
  stylesheets that read the tokens keep working; they receive the new values.
- **The operator seed still wins.** `frontend.appearance.seedColor` keeps overriding the built-in scheme. Operators who want
  the old look set `seedColor` to `#ac1c67`. This is written into the upgrade notes, not into a new setting.
- **Documentation.** The `appearance` field reference in `docs/gitbook/reference/settings-domains.md` states the new default and
  the way back to the magenta. `docs/gitbook/reference/upgrade-changes.md` gets a note that the default colours change on
  upgrade and how to keep the old ones.

### Shipping

Proposal PR `docs(openspec): propose rework-default-theme and use-wide-screens`, shared with `use-wide-screens`. Then one code
PR under the type `feat` and the scope `ui`: `feat(ui): use a steel default theme for the dashboard`. The change adds a
visible look and a documented way back, so it is a feature in the release notes. It is not `fix`, because the old colour was
not a defect; it was a choice that the maintainer no longer wants as the default.

Commits: `test(ui)` for the token snapshot, the contrast tests and the chart tests (written first, red), `feat(ui)` for the
palette and the token values, `docs(ui)` for the reference pages and the upgrade note.

### Not BREAKING

No configuration key, environment variable, route, manifest or stored data changes. The default colour is a presentation
default. An operator who sets a seed keeps exactly the colours that seed produced before. The look of an installation without a
seed changes, and the upgrade note says so. That is a change a reader sees, not a change an operator must act on, so the
change is not marked BREAKING.

### Dependencies

- None on another open change. The chart code in `reposilite-frontend/src/store/palette.js` and the tokens in
  `reposilite-frontend/src/styles/md3/color.css` are read as they are on `main`.
- `use-wide-screens` ships in the same proposal PR but has no code dependency on this change.

### Out of scope

- A dark-only or light-only default, a theme switcher redesign, or new scheme variants beyond the existing `schemeVariant`
  options.
- Logo, favicon and the feather mark. The favicon and the branding fields keep their behaviour.
- Plugin-specific stylesheets. They are not rewritten; their use of the tokens is covered by the unchanged token names.
- Legal and naming questions about the brand.

## Capabilities

### New Capabilities

- `dashboard-theme`: the default scheme of the dashboard, its contrast guarantee, the chart token order and the rule that an
  operator seed wins over the default.

## Impact

- **Operators without a seed:** the dashboard is steel instead of magenta on upgrade. A note in the upgrade page says how to keep
  the old colour (`seedColor` `#ac1c67`).
- **Operators with a seed:** nothing changes.
- **Plugin authors:** the token names are unchanged, so a plugin that reads `--md-sys-color-primary` keeps working and sees the
  new value. A plugin that hard-codes the old magenta keeps its own colour, because this change does not touch it.
- **Code:** `reposilite-frontend/src/styles/md3/color.css` (the built-in values), the chart tokens in the same stylesheet,
  `src/store/palette.js` (only if a new chart token is needed), and tests under `reposilite-frontend/src/`. No file under
  `reposilite-backend/` changes.
- **Documentation:** `docs/gitbook/reference/settings-domains.md` and `docs/gitbook/reference/upgrade-changes.md`.

## Verification

- Each group starts with failing Vitest tests that are seen to fail first: a token snapshot of the built-in scheme, a contrast
  test over every role pair in both schemes, and a chart palette test.
- `npm test` in `reposilite-frontend` passes. The contrast and chart tests need no browser and no network.
- `openspec validate rework-default-theme --strict` passes, and the dash grep prints nothing.
