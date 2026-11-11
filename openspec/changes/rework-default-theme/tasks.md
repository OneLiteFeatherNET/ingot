# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `ui`. Every test is written first and
seen to fail before the production values exist. Every test follows F.I.R.S.T.: no sleeps, no network, no system clock, a fresh
theme and palette read per test, assertions with messages. The contrast and chart tests read the token values from the stylesheet
and compute the ratios in code; they need no browser.

The seed is chosen in review (design, open question 1). Tasks 3.1 and 3.2 take the chosen seed. The candidates in the design are
the defaults.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose rework-default-theme and use-wide-screens`: the two changes and nothing else. Verify:
  `openspec validate rework-default-theme --strict` and `openspec validate use-wide-screens --strict` pass, and the dash grep
  prints nothing.

## 2. Red tests

- [ ] 2.1 `test(ui)`: write a token snapshot test for the built-in light and dark schemes. It asserts every `--md-sys-color-*`
  and `--md-ext-color-*` name that exists today is still defined, and the primary of each scheme equals the chosen seed's
  primary. Verify: it fails on the current magenta values.
- [ ] 2.2 `test(ui)`: write a contrast test over every role pair listed in design D2, in both schemes: text pairs at least 4.5 to 1,
  `outline` on `surface` at least 3 to 1. Add a colour-blind simulation case for the status hues: the success, warning and error
  colours must remain distinguishable by lightness or by the paired word under protanopia, deuteranopia and tritanopia. Verify: it
  fails on the current values where a pair is below its threshold, and the simulation case is recorded in the pull request with
  its result.
- [ ] 2.3 `test(ui)`: write a chart palette test: the first four categorical tokens differ from one another by at least 15 in
  lightness or 30 degrees of hue, in both schemes, and no categorical token equals a status colour. Verify: it fails on any token
  that does not meet the rule.

## 3. Values

- [ ] 3.1 `feat(ui)`: generate the tonal set of the chosen seed with the Material theme builder, replace the built-in values in
  `reposilite-frontend/src/styles/md3/color.css` for the light and dark schemes, and remove the pink tint of the neutral roles. Keep
  every token name. Verify: the tests of 2.1 and 2.2 pass.
- [ ] 3.2 `feat(ui)`: re-derive the eight chart tokens from the chosen seed in the same stylesheet. Verify: the test of 2.3 passes,
  and `palette.js` needs no change unless a token name changed (it must not).
- [ ] 3.3 `test(ui)`: confirm that a set `seedColor` still wins: a test with `seedColor` `#ac1c67` produces the magenta scheme that
  the derivation gives today, and an empty seed leaves the built-in scheme in place. Verify: the existing appearance tests pass
  unchanged; add only the missing case.

## 4. Documentation

- [ ] 4.1 `docs(ui)`: update the `appearance` field table in `docs/gitbook/reference/settings-domains.md` to state the new built-in
  default and the way back to the magenta (`seedColor` `#ac1c67`). Verify: the page names the built-in default and the seed for the
  old look; the dash grep prints nothing.
- [ ] 4.2 `docs(ui)`: add the upgrade note to `docs/gitbook/reference/upgrade-changes.md`: the default colours change, an installation
  without a seed looks steel, and `seedColor` `#ac1c67` keeps the old colours. Verify: the note names the field, not an environment
  variable, and the dash grep prints nothing.

## 5. Pull request

- [ ] 5.1 Open the code pull request under the title `feat(ui): use a steel default theme for the dashboard` (or the title that
  names the chosen seed), from the remote main branch that already contains the merged proposal, with no file under `openspec/`.
  Fill the repository's pull request template and record the simulation result of task 2.2 and the contrast table of the chosen
  seed in the description.
