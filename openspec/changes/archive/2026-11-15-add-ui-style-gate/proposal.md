# Proposal

## Open maintainer questions

Each question has a default. The change is written to the default; the alternative is what the maintainer chooses instead.

1. **Stylelint: yes or no.** Default: no. The gate reads `src/**/*.css` as text (rules 3 and 7), which covers the two checks stylelint would add, with no new package, config file or second baseline. Alternative: add stylelint with a class selector pattern rule and a colour rule for `src/styles/md3/`. That adds two packages, a config and its own baseline, and is worth it only if the CSS grows beyond the six stylesheets that exist today.
2. **`calc(env(safe-area-inset-*))` in arbitrary values.** Default: allowlisted with a written reason, not permanently. The entry names the token change that replaces it, and that change removes the entry. Alternative: no exception at all, so the three occurrences sit in the baseline until tokenised.
3. **Where CI runs the gate.** Default: this change edits no CI file. The gate tests call `readFileSync`, so `.github/scripts/frontend_scope.sh` already lists them as readers, and they run in the `Coverage (frontend)` job on every pull request that touches frontend source. Violations can only appear through such a change, so the gate cannot be skipped. The `Frontend` job already runs `npm run eslint`, which fails on errors once the warn phase ends. Alternative: a follow-up `ci(frontend)` change that adds a named `Style gate` step to the `Frontend` job and a check that baseline counts never grow against `origin/main`. It is a separate change because one change ships under one Conventional Commits type.
4. **Existing ESLint findings.** Default: findings that the removed warn phase turns into errors go into ESLint's bulk suppressions file, `reposilite-frontend/eslint-suppressions.json`, generated with `--suppress-all`. No source file changes in this change, which keeps it `build(ui)` only. Alternative: fix the mechanical findings (semicolons, unused variables) first in a separate `style(ui)` change, then suppress what remains.
5. **Rule 8, inline `<svg>` outside the icon folders.** Default: enforced now. Inline icons outside `components/icons/` and `components/md/` are baselined, and the baseline shrinks as icons move to `MdIcon`. Alternative: keep the rule switched off until `MdIcon` exists, as the brief suggests, and enable it in the change that adds it.

## Why

The dashboard is styled with Tailwind CSS 4 on top of the Material Design 3 token set in `reposilite-frontend/src/styles/md3/`. `DESIGN.md` asks contributors to use role colours and `md-*` classes, and that rule is enforced by review only. A first grep finds bypasses in many files: seven `<style>` blocks in `.vue` files, about 180 lines with arbitrary values in about 75 files (such as `min-[600px]:` and `bg-[color-mix(...)]`), seven `color-mix()` overlays with a literal percentage, palette classes such as `text-gray-*`, and class names that no stylesheet defines. The exact counts come from the first run of the gate, which is authoritative and replaces this estimate. Every new bypass is a small duplicate of a token, and they add up to the inconsistencies the maintainer reports: breadcrumbs that render badly on subpages, a repository title that sits too far left, and surfaces that barely separate from the background.

ESLint cannot catch these. `reposilite-frontend/eslint.config.js` wraps every rule in `warnPhase` (marked "remove to restore"), so lint never fails, and there is no Stylelint and no Tailwind lint plugin. The repository already enforces design rules with source-scan Vitest tests (`src/style.test.js`, `src/styles/md3/*.test.js`, `src/anchors/anchors.test.js`). This change follows that pattern for the whole dashboard.

The gate lands before the cleanup on purpose. A checked-in baseline records the current violations, the test fails on any new one, and each later change in the programme deletes the entries it fixes. The baseline may only shrink. `migrate-content-to-components` ends by emptying it, and the gate then keeps the dashboard pure Tailwind on MD3 tokens.

## What Changes

- **Source-scan gate.** A new folder `reposilite-frontend/src/style-gate/` holds a pure scanner, one rule table and one Vitest test per rule. The scanner reads `src/**/*.vue`, `src/**/*.js` (excluding `*.test.js` and the gate itself) and `src/**/*.css`. It never touches the network, the clock or a directory outside `src/`.
- **Eight rules.** (1) no `<style>` block in a `.vue` file; (2) no arbitrary values, arbitrary properties or arbitrary variants; (3) no raw colour literals; (4) no Tailwind palette colours and no non-MD3 type, shape, motion, elevation or z-index defaults; (5) no inline `style` with literal sizes or colours; (6) every class token resolves to a Tailwind utility, a project `@utility` or `@theme` name, or an allowlisted hook; (7) plain class selectors only where documented; (8) inline `<svg>` only in the icon folders.
- **Baseline and allowlist, one JSON file per rule.** `src/style-gate/baseline/<rule>.json` lists each current violation as `{ file, match, count }`, where `match` is the matched text, not a line number, so line moves do not break it. `src/style-gate/allowlist/<rule>.json` lists documented exceptions as `{ file, match, reason }`. A reason is mandatory.
- **Honest ratchet.** For every (file, match) pair the actual count must equal the baseline count. More violations than the baseline fail the test, naming the file and the token. Fewer violations than the baseline also fail, with a message to remove the entry, so a fix cannot leave a stale entry behind. An allowlist entry that matches nothing fails too.
- **Class tokens resolve against the real stylesheet.** Rule 6 compiles `src/style.css` with `@tailwindcss/node` (4.3.3, the version the lockfile already installs through `@tailwindcss/vite`, added as a direct devDependency) and asks the compiler which candidates produce CSS. Known dangling hooks enter the baseline: `card-editor`, `snippet-url`, `settings-view`, `settings-save-bar`, `one-of-container` and `file-uploads`. The seventh, `pane-sheet`, is removed by `refactor-md3-token-utilities` before this change, so it is not an entry. Tests that select by these names move to `data-test` in the change that removes each entry.
- **Lint phase ends.** `warnPhase` and its helper are removed from `eslint.config.js`, so every configured rule fails again. Findings that exist today are recorded in `reposilite-frontend/eslint-suppressions.json` with `--suppress-all`, and the file is pruned with `--prune-suppressions` in the same change so it cannot go stale. Source files do not change.
- **Anchors and plugins are out of scope for the scan.** `data-ingot-component` and `data-ingot-part` attributes are never flagged. Plugin stylesheets live in the `ingot-plugins` cascade layer and are not part of `src/`, so they are not scanned.

## Shipping

This change ships under the type `build` and the scope `ui`. The proposal PR is merged before the code PR is opened, and the code PR is branched from the remote `main` that contains the merged proposal.

- Proposal PR: `docs(openspec): propose add-ui-style-gate`, containing only `openspec/changes/add-ui-style-gate/`.
- Code PR title: `build(ui): fail frontend checks on custom css and token bypasses`.

Sequencing with siblings: `refactor-md3-token-utilities` is a hard dependency and merges first, because rule 6 must resolve the `md-*` and `@utility` names it moves into the Tailwind theme. The gate then lands before `add-md3-component-catalogue`, `fix-surface-hierarchy-and-page-header`, `unify-dialogs-menus-and-feedback` and `migrate-content-to-components`, each of which removes the baseline entries it fixes in the same pull request.

### Out of scope

- Fixing any violation. The baseline records them; the later changes remove them.
- Any visual change, any runtime code, any dependency other than `@tailwindcss/node`.
- Kotlin and backend lint, and the Playwright and browser suites.
- Stylelint, unless the maintainer answers question 1 with yes.
- A CI step or a check that baselines never grow against `origin/main`, unless the maintainer chooses the alternative in question 3.
- Tokenising the `color-mix(... 32%, transparent)` scrim overlay (seven occurrences) or the safe-area `calc`. Both are recorded here. The scrim goes in `add-md3-component-catalogue` (`MdSideSheet`, `DialogShell` as `MdDialog`) and `unify-dialogs-menus-and-feedback` (the other five files and `SCRIM_STYLE`); the safe-area `calc` waits for a safe-area token.
- Changing the `.github/` workflows. The `Frontend` job already runs ESLint on every pull request.

## Capabilities

### New Capabilities

- `dashboard-style-enforcement`: the eight source rules, the baseline and allowlist format, the ratchet semantics, the ESLint severity after the warn phase and the exemption rules for anchors and plugins.

## Impact

- **Breaking marker.** Not BREAKING. No configuration key, environment variable, deployment manifest, volume, route, stored data or plugin API changes, and the Docker images are untouched. The title carries no `!` and no commit carries a `BREAKING CHANGE:` footer. No `docs/gitbook/` page documents a setting that changes, so no gitbook page is updated.
- **Visible change.** None. The dashboard renders the same bytes, so no before and after screenshots are needed at 320, 600 or 1440 pixels. The change is verified by tests and the lint run.
- **Behaviour for contributors.** A pull request that adds a `<style>` block, a palette class or a raw colour fails `npm test`, and a lint error fails `npm run eslint`. The failure names the file and the token. A pull request that removes a violation must also remove its baseline entry, or it fails too.
- **Code.** `reposilite-frontend/src/style-gate/` (new), `reposilite-frontend/eslint.config.js`, `reposilite-frontend/eslint-suppressions.json` (new), `reposilite-frontend/package.json` and `package-lock.json` (`@tailwindcss/node` as a devDependency at 4.3.3). Nothing else under `src/` changes.
- **Documentation.** `reposilite-frontend/DESIGN.md`, section "Rules for contributors": one bullet pointing to the gate and to the rule that the baseline may only shrink.

## Verification

- Every red test in the tasks is seen to fail before the scanner and the baseline exist.
- `npm test -- src/style-gate` in `reposilite-frontend` passes, and so does the full `npm test` run through `heavy`.
- `npm run eslint` exits 0 and prints no findings, because the suppressions file holds them all. A scratch file with a palette class, a `<style>` block and a semicolon, checked and then deleted, makes the gate and ESLint fail.
- `npx eslint --prune-suppressions` changes nothing on the final branch.
- `openspec validate add-ui-style-gate --strict` passes, and the dash check prints nothing.
