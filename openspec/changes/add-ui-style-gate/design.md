# Design

## Context

Measured on `main` in `reposilite-frontend/` with plain text searches, before `refactor-md3-token-utilities`. The baselines are generated after that change has merged, so what it fixes is not an entry: the literal window breakpoints it names, `pane-sheet`, the inline `--md-state-opacity` of `MdSelect.vue`, the `bg-scrim/32!` overlays, and the plain class selectors it turns into `@utility` (`.container`, `.skeleton-bars`, `.pane-corner*`, `.router-view-full`, `.md-*`, `.olf-brand-bar`). The gate's first run replaces these numbers with exact ones, and the code PR records them.

- **`<style>` blocks:** seven `.vue` files: `components/browser/FileUpload.vue`, `components/card/SnippetsCard.vue`, `components/md/MdLinearProgress.vue`, `components/md/MdSnackbar.vue`, `components/renderers/ObjectRenderer.vue`, `components/renderers/OptionalRenderer.vue`, `components/settings/SettingsView.vue`.
- **Arbitrary values:** about 180 lines in about 75 files of `.vue` and `.js`, outside tests. Examples: `pane-sheet` in `pages/IndexPage.vue:360` (`min-[600px]:`, `min-[calc(100dvh_-_4rem)]`), `components/md/MdSideSheet.vue:122` (`bg-[color-mix(...)]`).
- **Raw colour and `color-mix()`:** three raw hex literals outside `styles/md3/` and the test files: `components/renderers/MdColourFieldRenderer.vue:28` (help text example), `:62` (picker fallback `#000000`, documented) and `theme/settings.js:77` (warning text example). `color-mix(in srgb, var(--md-sys-color-scrim) 32%, transparent)` appears in seven files: `components/md/MdSideSheet.vue`, `components/identity/DialogShell.vue`, `components/header/LoginModal.vue`, `components/settings/dialog.js`, `components/settings/ConflictDialog.vue`, `components/settings/PasswordDialog.vue`, `components/settings/FactoryResetModal.vue`. The 32 percent literal is a magic value that a later token change replaces.
- **Inline style bindings:** about two dozen `style="..."` and `:style="..."` lines. Dynamic ones include `md/MdLinearProgress.vue:63` (`width: ${percent}%`), `md/MdOverflowMenu.vue:205`, `access/PrincipalActionsMenu.vue:227`, `dashboard/TimeSeriesChart.vue:164`, `browser/FileList.vue:181`, `dashboard/StatTile.vue:128`, `dashboard/StatusLabel.vue:39` and `settings/AppearancePreview.vue:117` (colour swatches), `settings/SettingsView.vue:462` (`pageClearance`). Static literals are the baseline's job: `card/SnippetsCard.vue:258` to `:270` (`style="width: 80%"` and siblings), `md/MdSelect.vue:407` (`zIndex: 'calc(var(--md-sys-z-dialog) + 1)'`) and `md/MdSelect.vue:418` (`--md-state-opacity: 0.12`).
- **Palette classes and type or shape defaults:** a few dozen matches. `shadow-sm|md|lg`: none. `animate-pulse`: one occurrence. `rounded` family: many, and the regex must exclude `rounded-corner-*`, which the MD3 token files define.
- **Dangling hooks, confirmed by search:** `card-editor` (`card/SnippetsCard.vue:250`), `snippet-url` (`card/RepositorySnippet.vue:59`, `:73`, `:90`, `:99`), `pane-sheet` (`pages/IndexPage.vue:360`), `settings-view` (`settings/SettingsView.vue:461`, plus its own `<style>` block), `settings-save-bar` (`settings/SettingsView.vue:648`, and `helpers/useTreeFits.js:31` selects it), `one-of-container` (`store/configuration.js:565`), `file-uploads` (`browser/FileUpload.vue:303`, inside its `<style>` block). The brief's `configuration.js:564` is the line of the `oneOf:` key; the class is on line 565.
- **Plain class selectors in CSS:** `src/style.css` defines `.container` (window margins, pinned by `window-margins.test.js`), `.vfm` (documented), `.dark` (documented), `.skeleton-bars`, `.pane-corner`, `.pane-corner-start`, `.pane-corner-end` and `.router-view-full`. `src/styles/md3/*.css` defines the documented hooks `.md-state-layer`, `.md-focus-ring`, `.md-focus-ring-inset`, `.md-touch-target`, `.olf-brand-bar` and `.dark`. The `.md-*` hooks are the primitives that `DESIGN.md` documents.
- **Inline SVG:** 62 `.vue` files contain `<svg`. `components/icons/` and `components/md/` are the two places that may hold them after this change. `MdIcon` does not exist yet.
- **CI:** the `Frontend` job in `.github/workflows/ci.yml` runs `npm ci` and `npm run eslint` on every pull request. The `Coverage (frontend)` job runs `vitest related` plus the readers listed by `.github/scripts/frontend_scope.sh`, which are all test files that call `readFileSync`, or the full suite when the change touches `src/styles/`, `src/anchors/` or a non-module file. Any change that can add a violation touches `src/`, so the job that runs the gate tests always runs with it.
- **ESLint:** `eslint.config.js` ends with `.map(warnPhase)`, so every rule the file enables reports as a warning. The Vue `flat/recommended` preset enables more rules than the three the brief names; their count is unknown until the warn phase ends, which is why bulk suppressions are needed.
- **Dependencies:** `tailwindcss` 4.3.3 and `@tailwindcss/node` 4.3.3 are in `package-lock.json`; the latter is a dependency of `@tailwindcss/vite`, not a direct one. `eslint` is 10.4.1.

## Goals

- A contributor cannot add a `<style>` block, an arbitrary value, a raw colour, a palette class, a non-MD3 default or an unknown class without a failing test that names the file and the token.
- The existing debt is written down once, in files that a reviewer can read, and each later change deletes what it fixes.
- The check is deterministic and runs without a browser, a server, the network or the clock.
- Anchors (`data-ingot-*`) and plugin styling stay outside the scan.

## Non-goals

- Fixing any violation, renaming any class or changing any component. Those belong to the later changes in the programme.
- Replacing Tailwind or the MD3 token files.
- A general CSS linter. Stylelint is an open question, and the default covers the four CSS files that matter with the scanner.

## Decisions

### D1. Source scan in Vitest, not a new lint tool

The repository already enforces design rules with source-scan Vitest tests that read files with `readFileSync` (`src/style.test.js`, `src/styles/md3/window-margins.test.js`, `src/anchors/anchors.test.js`). The gate uses the same mechanism, so no new tool has to be configured, and its result appears in the same job as the other design tests. The scanner is a pure function over strings; only `sources.js` and `tailwind.js` touch the disk, and they read only.

### D2. Layout of the gate

```
reposilite-frontend/src/style-gate/
  scan.js            pure: scanSource(file, text, rule) -> [{ file, match }]
  rules.js           the rule table: id, title, scope, matcher, allowlist name
  sources.js         lists src/**/*.vue, *.js (not *.test.js, not src/style-gate/), *.css
  tailwind.js        compiles src/style.css with @tailwindcss/node, returns the known selectors
  ratchet.js         pure: compares actual counts with baseline and allowlist
  baseline/          one JSON per rule
  allowlist/         one JSON per rule
  *.test.js          scanner fixtures, ratchet logic, and one repository test per rule
```

`src/style-gate/` is excluded from the scan, so the gate's own regular expressions and fixture strings are never reported.

### D3. Baseline and allowlist are line-independent

A violation's key is its file and its matched text, not its line. A baseline entry is `{ "file": "src/components/md/MdLinearProgress.vue", "match": "<style", "count": 1 }`. Counts handle repeated tokens in one file. A line move leaves the key unchanged, so it never breaks the test, and a second occurrence of a token is a new key only when the file gains one more than the baseline count.

The brief lists the data-driven style bindings by `file:line`. Line numbers move with every edit, so the allowlist uses `file` plus the binding's matched text instead, and the reason says why the value is data-driven.

### D4. Ratchet semantics

For each rule, the test computes actual counts per key, subtracts the allowlisted keys, and compares the result with the baseline. It reports three kinds of problem, each with a message that names the file and the token:

- **Unexpected.** The actual count is above the baseline count, or the key is not in the baseline. The message says the violation is new.
- **Stale.** The baseline count is above the actual count, or the key no longer occurs. The message says to lower or remove the entry in the same pull request.
- **Dead allowlist entry.** An allowlist entry matches no occurrence. The message says to remove it.

The baseline may only shrink. A test cannot read git history, so the test enforces the exact match with the current source, and the shrink-only rule is enforced by review: adding a key by hand is a visible diff that a reviewer rejects. A CI check that compares the baseline total with `origin/main` would enforce the rule automatically, and it is the alternative in the proposal's question 3.

### D5. Rule 6 resolves class tokens with the compiler

Rule 6 asks Tailwind which candidates it can build, instead of parsing `@utility` and `@theme` names by hand. `tailwind.js` calls `compile(styleCss, { base })` from `@tailwindcss/node` and then `build(candidates)` with every class token found in the sources. A token is valid when the output contains a selector for it, which covers Tailwind utilities, the project's `@utility` names (`md-*`, `md-embedded-input`), the `@theme` names (`rounded-corner-*`, `shadow-elevation-*`, `--color-*` roles) and the `@utility container` that `refactor-md3-token-utilities` defines. Anything else is an unknown token and goes to the baseline or to the allowlist as a documented hook.

Two cases need special handling. A token that contains an interpolation (`${` in a JavaScript template or `{{` in a template) cannot be resolved statically, so it is reported as a dynamic class and must be allowlisted by file. Class tokens are found in three contexts: `class` and `:class` attribute values in templates (including the strings inside arrays and ternaries), the string values of object keys named `class`, `classes`, `root` or `className` in `.js` files, and the arguments of `querySelector`, `closest` and `matches` when they select by class.

### D6. Rule 3 covers colours in CSS too

Colour literals (`#hex`, `rgb(`, `rgba(`, `hsl(`, `hsla(`) and `color-mix()` with a numeric percentage are reported in `.vue`, `.js` and `.css` files. The exceptions are `src/styles/md3/color.css` (the token definitions), `src/store/console/ansi.js` (terminal colours that are not dashboard colours) and the two prose examples and the picker fallback in `MdColourFieldRenderer.vue` and `theme/settings.js`. The picker fallback `#000000` is required by the native colour input, which takes `#rrggbb` only, so it stays allowlisted with that reason.

### D7. Rule 4 is a deny list, not an allow list

The forbidden defaults are named explicitly, so the rule stays readable and does not reject a utility a later token change adds. The list is: `text-xs`, `text-sm`, `text-base`, `text-lg`, `text-xl` and `text-2xl` to `text-9xl`; `leading-*`; `font-thin` to `font-black` (the Tailwind weight names); bare `rounded` and `rounded-none` to `rounded-full`, with their side variants; `shadow-sm` to `shadow-2xl` and `shadow-inner`; `animate-pulse`, `animate-bounce` and `animate-ping`; `duration-<number>` and `ease-linear`, `ease-in`, `ease-out` and `ease-in-out`; `z-<number>`; `opacity-<number>` (state layers come from `md-state-layer`, so any other opacity on a colour is a violation); and the palette colour names with any utility prefix (`text-gray-500`, `bg-white`, `border-black/10`). Project tokens with the same prefixes are not matched, because they resolve to `rounded-corner-*`, `shadow-elevation-*`, `md-*` and role names, none of which are on the list.

### D8. Rule 2 and the arbitrary-value allowlist

Rule 2 flags class tokens with square brackets (`prop-[value]`, `[prop:value]`, `[&_selector]:`, `min-[…]:`, `max-[…]:`) and with the `supports-[…]` form. The v4 shorthand `bg-(--variable)` is allowed, because it names a token rather than a value. The allowlist is for `calc(env(safe-area-inset-*))`, which the bottom bar needs until a safe-area token exists, and for grid templates inside primitives, where the template is the component's own layout. Both carry a reason. The safe-area entry is not permanent, per the proposal's question 2.

### D9. Rule 7 and the Tailwind layers

A plain class selector is allowed only for the two documented hooks that remain after `refactor-md3-token-utilities`: the `.dark` colour hook and the `.vfm` library hook. The scanner reads the selector list of every `.css` file and of the `<style>` blocks and `querySelector` calls in `.js` files. The other helpers of `style.css` and `src/styles/md3/` are `@utility` declarations by then, which are not class selectors. The `<style>` blocks outside `src/components/md/` and the `.settings-save-bar` selector of `helpers/useTreeFits.js` are baselined, and the change that replaces each of them removes its entry.

### D10. ESLint leaves the warn phase through suppressions

`warnPhase` and its helper are removed, so every rule that `eslint.config.js` enables reports as an error again. The findings that exist today are recorded with `eslint --suppress-all` into `reposilite-frontend/eslint-suppressions.json`. ESLint then reports only new findings. `eslint --prune-suppressions` removes entries whose finding is gone, and the task runs it so the file cannot go stale. The rule set itself does not change; only the severity does. A parse error or a broken configuration fails the job as before.

ESLint's suppression file is verified against the pinned ESLint 10.4.1 in the first implementation task. If the flag names differ in that version, the task records the actual names in the pull request and uses them.

### D11. Rule 8 and the icon folders

Inline `<svg>` is allowed in `src/components/icons/` and `src/components/md/`. Everywhere else it is a violation, baselined per file. `MdIcon` does not exist, and the catalogue change that adds it moves the baselined icons over, so the baseline shrinks in that change. Rule 8 is on by default, per the proposal's question 5.

### D12. No runtime change and no new visible behaviour

The gate reads source text and runs in Vitest only. The Vite build, the bundle, the server and the Docker images do not change. No screenshot is needed, because no pixel changes.

## Rule table

| Rule id | Rule | Scope | Matcher | Allowlist |
| --- | --- | --- | --- | --- |
| `style-block` | No `<style>` block | `.vue` | `<style` | none |
| `arbitrary-value` | No arbitrary values, properties or variants | `.vue`, `.js` | class tokens with `[` or `supports-[` | grid templates in primitives, safe-area `calc` |
| `raw-colour` | No raw colour literals | `.vue`, `.js`, `.css` | `#hex`, `rgb(`, `hsl(`, `color-mix(` with a percent | `color.css`, `ansi.js`, colour picker |
| `non-md3-default` | No palette colours or non-MD3 defaults | `.vue`, `.js` | the deny list in D7 | none |
| `inline-style` | No literal `style` values | `.vue` | `style=` and `:style=` with literal values | data-driven bindings |
| `class-token` | Every class token resolves | `.vue`, `.js` | class contexts in D5 | documented hooks, dynamic classes |
| `class-selector` | Plain class selectors only where documented | `.css`, `<style>`, `.js` `querySelector` | class selectors | `.dark`, `.vfm` |
| `inline-svg` | Inline `<svg>` only in icon folders | `.vue` | `<svg` | `components/icons/`, `components/md/` |

## Evidence of the ratchet on this repository

The baselines are generated once by the scanner and then reviewed by hand. The review checks the cases this document names: the seven `<style>` files, the seven `color-mix()` files, the six dangling hooks, and the three raw hex occurrences, of which two are prose and one is the documented picker fallback. A generated entry for a token the compiler resolves is a scanner bug and is fixed in the matcher before the baseline is committed.

## Risks

- **False positives.** A regular expression can match prose or a JavaScript index (`arr[0]`). Mitigation: each matcher has fixtures that must pass and fail before the matcher exists (task 2), and a false positive is fixed in the matcher, not by a broad allowlist entry.
- **A large first baseline.** About 180 arbitrary-value lines and many palette classes make the first baseline large. The file is generated, reviewed once, and kept small by later changes. Its size is a cost, not a defect.
- **Tailwind version drift.** `@tailwindcss/node` and `tailwindcss` must stay on the same version. Both are pinned to 4.3.3 in the lockfile, and a Renovate update moves them together, which the task checks.
- **Dynamic class names.** A class built at runtime from a variable cannot be resolved. Such tokens are reported and allowlisted by file, which keeps them visible.
- **ESLint suppression drift.** New findings in a file that already has a suppression are still reported, because suppressions are counted per rule and file. Pruning keeps the counts exact.
- **Merge conflicts on baselines.** Each rule has one JSON file, so two changes that fix different entries of the same rule conflict on one file. The conflict is a textual one and is resolved by keeping both removals.

## Migration plan

1. `refactor-md3-token-utilities` merges first, so every `md-*` and `@utility` name is in the Tailwind theme.
2. This change lands with the generated baselines and the suppressions file. `npm test` and `npm run eslint` pass on the same commit.
3. Each later change removes the entries it fixes, in the same pull request, and the test fails until it does.
4. `migrate-content-to-components` empties the baselines and the suppressions file. After that, the gate holds the dashboard to the rules without any entry.

## Open questions

The open maintainer questions are at the top of `proposal.md`, each with a default. This design follows those defaults.
