# Spec Delta

## Purpose

The dashboard is styled with Tailwind utilities on top of the Material Design 3 tokens. A source scan fails the frontend unit
tests when a file adds a style that bypasses the tokens or a class that no stylesheet defines. Existing violations are recorded in
a baseline that may only shrink, and each change that fixes one removes its entry.

## ADDED Requirements

### Requirement: No style block in a component

A `.vue` file under `reposilite-frontend/src/` SHALL NOT contain a `<style>` block, except the occurrences recorded in the baseline
`src/style-gate/baseline/style-block.json`.

#### Scenario: New style block

- **WHEN** a component gains a `<style>` block that is not in the baseline
- **THEN** the gate test fails and its message names the file and the `<style` token

#### Scenario: Fixed style block

- **WHEN** a change removes the `<style>` block of a file listed in the baseline
- **THEN** the gate test fails until the file's entry is removed from the baseline in the same change

### Requirement: No arbitrary values

Class tokens in `.vue` and `.js` files under `reposilite-frontend/src/` SHALL NOT use arbitrary values, arbitrary properties,
arbitrary variants or the `min-[…]`, `max-[…]` and `supports-[…]` forms. The v4 shorthand `bg-(--name)` for a named token is allowed.
Only the exceptions in `src/style-gate/allowlist/arbitrary-value.json` are allowed, each with a reason.

#### Scenario: Breakpoint written inline

- **WHEN** a template class contains `min-[600px]:flex`
- **THEN** the gate test fails and its message names the file and the token `min-[600px]:flex`

#### Scenario: Documented exception

- **WHEN** a template class contains `calc(env(safe-area-inset-bottom))` inside an arbitrary value listed in the allowlist with a reason
- **THEN** the gate test passes for that token

### Requirement: No raw colour literals

Files under `reposilite-frontend/src/` SHALL NOT contain a colour literal (`#` followed by three, four, six or eight hex digits,
`rgb(`, `rgba(`, `hsl(`, `hsla(`) or a `color-mix()` function with a numeric percentage, outside `src/styles/md3/color.css` and the
exceptions in `src/style-gate/allowlist/raw-colour.json`. Colours come from role utilities such as `text-on-surface` and
`bg-surface-container`.

#### Scenario: Hex in a template

- **WHEN** a component writes `class="bg-[#3A5F85]"` or `style="color: #3A5F85"`
- **THEN** the gate test fails and its message names the file and the literal `#3A5F85`

#### Scenario: Overlay with a literal percentage

- **WHEN** a component writes `color-mix(in srgb, var(--md-sys-color-scrim) 32%, transparent)`
- **THEN** the gate test reports the occurrence unless the baseline records it

### Requirement: No palette colours

Class tokens SHALL NOT name a Tailwind palette colour, with any utility prefix (for example `text-gray-500`, `bg-white`, `border-black`).
Colours come from the role utilities of the MD3 colour tokens.

#### Scenario: Palette colour

- **WHEN** a component uses `text-gray-500`
- **THEN** the gate test fails and its message names the file and the token `text-gray-500`

### Requirement: No non-MD3 type, shape, motion, elevation or z-index defaults

Class tokens SHALL NOT use the Tailwind defaults for type, shape, elevation, motion, z-index or opacity. The forbidden names
are listed in the scenarios of this requirement, and each has an MD3 replacement.

#### Scenario: Type and shape defaults

- **WHEN** a class token is `text-xs` to `text-9xl`, `leading-*`, `font-thin` to `font-black`, bare `rounded`, or `rounded-none` to `rounded-full`
- **THEN** the gate test fails and suggests `md-*` type classes or `rounded-corner-*`

#### Scenario: Elevation, motion, z-index and opacity defaults

- **WHEN** a class token is `shadow-sm` to `shadow-2xl`, `shadow-inner`, `animate-pulse`, `animate-bounce`, `animate-ping`, `duration-<number>`,
  `ease-linear`, `ease-in`, `ease-out`, `ease-in-out`, `z-<number>` or `opacity-<number>`
- **THEN** the gate test fails and suggests `shadow-elevation-*`, the motion tokens, the z-index tokens or `md-state-layer`

#### Scenario: Bare rounded corner

- **WHEN** a component uses `rounded-lg`
- **THEN** the gate test fails and its message suggests `rounded-corner-*`

#### Scenario: MD3 corner accepted

- **WHEN** a component uses `rounded-corner-large` or `rounded-tl-corner-extra-large`
- **THEN** the gate test passes for that token

### Requirement: No literal inline style

A `style` attribute or `:style` binding in a `.vue` file SHALL NOT set a literal size, colour, z-index or custom property value.
Values computed from data are allowed when the binding is listed in `src/style-gate/allowlist/inline-style.json` with a reason.

#### Scenario: Literal width

- **WHEN** a component writes `style="width: 80%"`
- **THEN** the gate test fails unless the occurrence is in the baseline

#### Scenario: Data-driven width

- **WHEN** a component binds `:style="{ width: `${percent}%` }"`, and the binding is in the allowlist with a reason
- **THEN** the gate test passes for that binding

### Requirement: Every class token resolves

Every class token in a `class` or `:class` attribute, in a class-valued string of a `.js` file, and in a `querySelector`, `closest` or
`matches` selector SHALL resolve to a Tailwind utility, a project `@utility` or `@theme` name, or a hook that the allowlist names with a
reason. The test resolves tokens with the Tailwind compiler applied to `src/style.css`. A token that is built at runtime from a
variable SHALL be listed in the allowlist by file, with a reason.

#### Scenario: Dangling hook

- **WHEN** a component uses the class `card-editor` and no stylesheet defines it
- **THEN** the gate test fails unless `card-editor` is in the baseline, and the message names the file and the token

#### Scenario: Known utility

- **WHEN** a component uses `md-body-small` or `bg-surface-container`
- **THEN** the gate test passes for that token

#### Scenario: Runtime-built class

- **WHEN** a component builds `text-${tone}` at runtime
- **THEN** the gate test fails unless the file is in the allowlist with a reason

### Requirement: Plain class selectors only where documented

A plain class selector SHALL appear only in `src/style.css` and in `src/styles/md3/*.css`, and in those files only for the documented hooks
(`.dark` and `.vfm`; the other helpers are `@utility` declarations after `refactor-md3-token-utilities`) or for a selector that
the baseline records. A `querySelector`,
`closest` or `matches` call in a `.js` file SHALL NOT select by a class outside the same list, unless the baseline records it.

#### Scenario: Class selector in a view

- **WHEN** a `<style>` block or a `.css` file outside the documented list selects `.settings-view`
- **THEN** the gate test fails and its message names the file and the selector

#### Scenario: Selector in code

- **WHEN** a `.js` file calls `document.querySelector('.settings-save-bar')`
- **THEN** the gate test fails unless the occurrence is in the baseline, and the proposal names `data-test` as the replacement

### Requirement: Inline SVG only in the icon folders

An inline `<svg>` element SHALL appear only in `src/components/icons/` and `src/components/md/`. Elsewhere each occurrence is recorded in the
baseline `src/style-gate/baseline/inline-svg.json`.

#### Scenario: New inline icon

- **WHEN** a view gains an inline `<svg>` that is not in the baseline
- **THEN** the gate test fails and its message names the file and suggests `MdIcon`

### Requirement: Baseline and allowlist are exact

For each rule, the actual count of every (file, matched text) pair SHALL equal its count in the baseline file, after the allowlisted
occurrences are removed. Every allowlist entry SHALL match at least one occurrence and carry a non-empty reason.

#### Scenario: New violation

- **WHEN** a pull request adds a violation without a baseline entry
- **THEN** the gate test fails and its message says the violation is new

#### Scenario: Fixed but not removed

- **WHEN** a pull request removes a violation but leaves its baseline entry
- **THEN** the gate test fails and its message says to remove or lower the entry in the same pull request

#### Scenario: Dead allowlist entry

- **WHEN** an allowlist entry matches no occurrence
- **THEN** the gate test fails and its message says to remove the entry

#### Scenario: Baseline does not grow

- **WHEN** a pull request adds an entry to a baseline file
- **THEN** the reviewer rejects the entry, because the baseline may only shrink; the test cannot see git history, so this is a review rule

### Requirement: Anchors and plugin styling are not scanned

Attributes whose names start with `data-ingot-` SHALL NOT be reported by any rule. Plugin stylesheets, which load into the `ingot-plugins`
cascade layer from the server, are not part of `src/` and SHALL NOT be scanned.

#### Scenario: Anchor attribute

- **WHEN** a component carries `data-ingot-component="repository-detail"` and `data-ingot-part="header"`
- **THEN** no rule reports either attribute

#### Scenario: Plugin stylesheet

- **WHEN** a plugin stylesheet names a class outside the design system
- **THEN** the gate does not scan it

### Requirement: Lint findings fail the check

The `reposilite-frontend/eslint.config.js` configuration SHALL NOT map rule severities to `warn`. Every enabled rule SHALL report as an
error. Findings that exist before the change SHALL be recorded in `reposilite-frontend/eslint-suppressions.json`, and the file SHALL be
pruned so that it lists no finding that no longer occurs.

#### Scenario: New lint error

- **WHEN** a pull request adds a Vue file with an unused variable
- **THEN** `npm run eslint` exits with an error that names the file and the rule

#### Scenario: Suppressed finding

- **WHEN** an existing finding is listed in the suppressions file
- **THEN** `npm run eslint` does not report it

#### Scenario: Stale suppression

- **WHEN** a suppressed finding no longer occurs
- **THEN** `npx eslint --prune-suppressions` removes its entry, and the entry is removed in the same pull request

### Requirement: The gate runs on pull requests that touch the frontend

The gate's test files SHALL run on every pull request that changes a file under `reposilite-frontend/src/` or `server/`, because
they read source text from disk and are therefore listed as readers by `.github/scripts/frontend_scope.sh`. The gate SHALL NOT
change the workflow files.

#### Scenario: Component change

- **WHEN** a pull request changes one `.vue` file under `src/`
- **THEN** the `Coverage (frontend)` job runs the gate's test files alongside the related tests

#### Scenario: Design-only change

- **WHEN** a pull request changes only `reposilite-frontend/DESIGN.md`
- **THEN** the `Coverage (frontend)` job runs the full frontend suite, because the change is not a module that the scope script can place,
  and the gate runs inside that suite

### Requirement: No change to shipped behaviour

The change SHALL NOT alter the rendered output of any view, the built bundle, the server, the backend or the container images. Only
`reposilite-frontend/src/style-gate/`, `reposilite-frontend/eslint.config.js`, `reposilite-frontend/eslint-suppressions.json`, the
dependency files of `reposilite-frontend` (for the `@tailwindcss/node` devDependency) and `reposilite-frontend/DESIGN.md` change.

#### Scenario: Built dashboard unchanged

- **WHEN** the change is compared with its base commit
- **THEN** no file under `reposilite-frontend/src/` outside `src/style-gate/` differs
