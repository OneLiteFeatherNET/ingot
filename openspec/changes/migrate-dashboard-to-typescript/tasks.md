# Tasks

Each group names its Conventional Commits type and scope. A group is one pull request, and its last task opens that pull request under the title given. Commits inside a group use the group's type. The gates named in `design.md` (rules 6 and 7, D9) apply to every group. Tests follow F.I.R.S.T.: no sleeps, no system clock (inject `Clock` or a fixed date), no shared mutable state, a `@TempDir`-style temporary directory per test, and the failing test is written and seen to fail before the production change. No group changes a configuration key, environment variable or deployment manifest, so `docs/gitbook/` is not touched.

Before any slice starts, check `git diff --name-only origin/main...<pr>` for each open pull request named in `design.md` (conflict table). Do not start a slice whose files an open pull request still changes, unless that pull request is merged.

## 0. Phase 0: tooling (blocks everything else)

Group type: `build(ui)`, except 0.10 which is `ci(ui)`. Pull request: `build(ui): add typescript tooling to the dashboard`.

- [x] 0.1 Re-check the pins in `design.md` with `npm view <package> time --json`. Each pin must be at least 14 days old on the day of the pull request; if one is not, take the newest release that is. Record the chosen versions and dates in the pull request description.
- [x] 0.2 Add `typescript` 6.0.3, `vue-tsc`, `@vue/tsconfig` and `typescript-eslint` as exact devDependencies of `reposilite-frontend/package.json`, and update `package-lock.json`. Run `npm ci` and the license check. Record the peer ranges (question 2). The peer cap of `typescript-eslint` is handled with an npm `overrides` entry in `package.json`
  (decided 2026-11-18), named in this pull request and removed with task 5.2. If #214 changes `package.json`, rebase onto it first.
- [x] 0.3 Add `tsconfig.json` (solution file), `tsconfig.app.json`, `tsconfig.tests.json` and `tsconfig.node.json` as in `design.md` D2: `allowJs: true`, `checkJs: false`, `strict: true`, `moduleResolution: "bundler"`. Confirm the `@vue/tsconfig` export names it extends.
- [x] 0.4 Add `npm run typecheck` (`vue-tsc --build --noEmit`). With no `.ts` source yet, the check passes only because of 0.7. Add a red test first that Vitest collects a `.test.ts` file under `src/` (a fixture file in the test's own temporary directory, not a committed one), then change `vitest.config.js` `include` to `src/**/*.test.{js,ts}` and `server/**/*.test.{js,ts}`.
- [x] 0.5 Add the `any` budget (question 7). Write `src/types/any-budget.test.ts` first, with fixtures built in a temporary directory: it must fail for a file over its entry, for a file without an entry, and for an entry above the count. Then add `src/types/any-budget.json` with an empty object and make the test pass.
- [x] 0.6 ESLint: add the `typescript-eslint` configuration for `**/*.ts` and for `*.vue` files with `<script lang="ts">`, with `@typescript-eslint/no-explicit-any` as an error. Keep the warning-to-error wrapper. Check that `npm run eslint` still reports the current code clean, and that the `eslint "src/**"` glob matches `.ts` files.
- [x] 0.7 Add `src/env.d.ts` for the Vite client types, the global test helpers and the `{{REPOSILITE.*}}` placeholders that plugins also use (declared as string constants, not as runtime values). Confirm the declarations do not change the production bundle.
- [x] 0.8 Style gate and anchors: write a failing test first in `src/style-gate/scan.test.js` (or a new `.test.js` beside it, the file is not converted yet) that a `.ts` file with a raw colour is reported. Then add `.ts` to `SCANNED_EXTENSIONS` in `src/style-gate/sources.js`, exclude `.test.ts` next to `.test.js`, and add `.ts` to the filter in `src/anchors/checks.js` (line 315). The baseline and allowlist files are not changed.
- [x] 0.9 Classify the 22 SFCs that contain neither `<script setup>` nor `export default {` and list them in the pull request. The template-only icons among them are converted in 2.C02 (decided 2026-11-18); the others get no edits. Confirm the count of 179 and 17.
- [x] 0.10 CI: add a `npm run typecheck` step to the frontend job in `.github/workflows/ci.yml`, after `npm ci` and before the build, in the job that runs `npm run eslint`. Group type `ci(ui)`.
- [x] 0.11 Record the baseline for D9 in the pull request description: the CSS file of `npm run build` (name without its hash, byte size and SHA-256), and the size of every JavaScript chunk.
- [x] 0.12 Run `npm run typecheck`, `npm run eslint`, `npx vitest run --maxWorkers=2`, `npm run build`, and `npm run e2e -- --workers=2` with the baseline set. Use `heavy` for the e2e run. Open the pull request `build(ui): add typescript tooling to the dashboard`.

## 1. Phase 1: shared API types and modules

Each group is one pull request. Group type: `refactor(ui)`. Phase 1 starts after the Phase 0 pull request is merged.

### 1.A `P1-0` API types and import spelling

- [x] 1.A.1 Remove the explicit `.js` extension from the 88 relative imports in 44 files (D6). One commit, no rename, no other change. Run the unit suite.
- [x] 1.A.2 Write `src/types/api.test.ts` first: it regenerates the types from `docs/openapi/ingot-openapi.json` into a temporary directory and expects the committed `src/types/api.ts` to match. Watch it fail (the generator and the file do not exist yet).
- [x] 1.A.3 Add `scripts/generate-api-types.mjs` (`openapi-typescript` 7.13.0, exact devDependency, added in this group) and generate `src/types/api.ts`. Commit the generated file unchanged. Add an `npm run types:api` script. Make the freshness test pass.
- [x] 1.A.4 Open the pull request `refactor(ui): type the api response shapes`.

### 1.B `P1-1` store core

- [x] 1.B.1 Rename `src/store/*.js` and `src/store/console/` with `git mv`, including their `.test.js` files (21 modules, 31 tests). Commit only the renames.
- [x] 1.B.2 Add types (store state, action signatures, exported names) that use `src/types/api.ts`. Convert the tests to `.test.ts`. Keep test bodies unchanged (rule 2).
- [x] 1.B.3 Run the gates, including `src/anchors` (the anchor test runs against the renamed registry `file` fields).
- [x] 1.B.4 Open the pull request `refactor(ui): type the store core modules`.

### 1.C `P1-2` store migration and Maven

- [x] 1.C.1 Rename `src/store/migration/` and `src/store/maven/` with `git mv` (14 modules, 9 tests). Commit the renames only.
- [x] 1.C.2 Add types and convert the tests as in 1.B.2.
- [x] 1.C.3 Run the gates.
- [x] 1.C.4 Open the pull request `refactor(ui): type the migration and maven store modules`.

### 1.D `P1-3` router, navigation, theme and anchors

- [x] 1.D.1 Wait for #214 and #219 to merge (both edit `src/navigation/registry.js`). Rename `src/router/`, `src/navigation/`, `src/theme/` and `src/anchors/` (14 modules, 20 tests). `src/router/dashboard-segments.json` stays JSON and byte-identical (CI compares it with the chart copy).
- [x] 1.D.2 Add types. The route names and paths are not changed.
- [x] 1.D.3 Run the gates, plus `cmp` of `src/router/dashboard-segments.json` against `charts/ingot/files/dashboard-segments.json`.
- [x] 1.D.4 Open the pull request `refactor(ui): type the router, navigation, theme and anchor modules`.

### 1.E `P1-4` helpers, style gate and styles

- [x] 1.E.1 Rename `src/helpers/`, `src/style-gate/` and `src/styles/` (14 modules, 19 tests). `src/style-gate/baseline` and `allowlist` JSON stay as they are, except for path keys that change (none expected).
- [x] 1.E.2 Add types and convert the tests.
- [x] 1.E.3 Run the gates, plus the style gate test, which must still report the same findings as the baseline.
- [x] 1.E.4 Open the pull request `refactor(ui): type the helpers and style gate`.

## 2. Phase 2: component slices

Each slice is one pull request with the title given. Within a slice the commits are: (a) the rename only (`git mv` of every `.vue`, `.js` and `.test.js` file in the slice), (b) the types and the `.test.ts` conversion. The rules of `design.md` apply: one owner per directory, no template change, Options API handled as in D10 and rule 4, the registry and suppression keys updated in the same commit as the rename.

Each slice starts only after the Phase 1 pull requests are merged and after any open pull request named for it in `design.md` is merged. A slice that cannot finish in two working days is split by file list (rule 8), and the split is written into this file before the second half starts.

### 2.C01 md primitives (34 files) - `refactor(ui): type the md primitives`

Starts after #238 (md3 tokens), #214 and #219 are merged.

- [x] 2.C01.1 Rename `src/components/md/` (17 `.vue`, 17 `.test.js`). Commit the renames only.
- [x] 2.C01.2 Add `defineProps<...>()` and `defineEmits<...>()` to each primitive, types for its slots, and convert the tests. Keep each primitive's prop names and defaults (the catalogue change owns the API).
- [x] 2.C01.3 Update `src/anchors/registry.json` `file` fields and run `npm run anchors:doc`. Run the gates, plus the md and menu specs of the e2e baseline, with the md screenshot tests from #238 if they are on `main`.
- [x] 2.C01.4 Open the pull request `refactor(ui): type the md primitives`.

### 2.C02 icons (36 files) - `refactor(ui): type the icon components`

- [x] 2.C02.1 Rename `src/components/icons/` (33 `.vue`, 1 `.js`, 2 `.test.js`). Commit the renames only.
- [x] 2.C02.2 Add `defineProps<...>()` for the icon size and colour props, and convert the tests and the one module. Convert the
  template-only icons (23 by the maintainer's count, decided 2026-11-18): each gets `<script setup lang="ts">`, with a props type only
  where its template reads a prop, and its template and rendered markup do not change.
- [x] 2.C02.3 Run the gates. The CSS comparison must be byte-identical (icons carry no CSS).
- [x] 2.C02.4 Open the pull request `refactor(ui): type the icon components`.

### 2.C03 layout, header, page shell (40 files) - `refactor(ui): type the layout and page shell components`

Starts after #238 (`layout`, `IndexPage.vue`) and #214 (`MenuPanel.vue`) are merged, and after #219 (`IndexPage.vue`).

- [x] 2.C03.1 Rename `src/components/layout/`, `header/`, `footer/`, `overview/`, `console/`, `src/pages/`, `src/App.vue`, `src/main.js` and `src/style.test.js`. Commit the renames only.
- [x] 2.C03.2 Add types and convert the tests. `src/style.test.js` reads `src/style.css` by path, which stays where it is, so its body does not change.
- [x] 2.C03.3 Keep `IndexPage.vue`'s `useMediaQuery` literal exactly as it is (no change of breakpoints).
- [x] 2.C03.4 Run the gates, including the layout, navigation and accessibility e2e specs.
- [x] 2.C03.5 Open the pull request `refactor(ui): type the layout and page shell components`.

### 2.C04 shared leaves (40 files) - `refactor(ui): type the shared leaf components`

Starts after #238 (`tokens`) is merged.

- [x] 2.C04.1 Rename `src/components/util/`, `tokens/`, `card/`, `support/` and `plugins/`. Commit the renames only.
- [x] 2.C04.2 Add types and convert the tests. `plugins/` is the plugin UI host: its props and emits do not change, and the extension point names it reads stay as strings.
- [x] 2.C04.3 Run the gates, plus the plugin stylesheet e2e spec.
- [x] 2.C04.4 Open the pull request `refactor(ui): type the shared leaf components`.

### 2.C05 search (10 files) - `refactor(ui): type the search components`

Starts after #238 (1 file) is merged.

- [x] 2.C05.1 Rename `src/components/search/`. Commit the renames only.
- [x] 2.C05.2 Add types and convert the tests and the two modules.
- [x] 2.C05.3 Run the gates.
- [x] 2.C05.4 Open the pull request `refactor(ui): type the search components`.

### 2.C06 dashboard (40 files) - `refactor(ui): type the dashboard components`

Starts after #238 (2 files) is merged.

- [x] 2.C06.1 Rename `src/components/dashboard/`. Commit the renames only.
- [x] 2.C06.2 Add types, including the statistics and cluster status shapes from `src/types/api.ts`, and convert the tests.
- [x] 2.C06.3 Run the gates, plus the dashboard e2e spec.
- [x] 2.C06.4 Open the pull request `refactor(ui): type the dashboard components`.

### 2.C07 access panels and dialogs (53 files) - `refactor(ui): type the access panels and dialogs`

Starts after #238 (1 file) is merged. Covers `src/components/access/` top level only (`check/` and `nodes/` belong to C08).

- [x] 2.C07.1 Rename the top-level files of `src/components/access/`. Commit the renames only.
- [x] 2.C07.2 Add types, including principal and team shapes from `src/types/api.ts`, and convert the tests.
- [x] 2.C07.3 Run the gates, plus the access e2e specs present in the baseline.
- [x] 2.C07.4 Open the pull request `refactor(ui): type the access panels and dialogs`.

### 2.C08 access check and node editor (34 files) - `refactor(ui): type the access check and node editor components`

- [x] 2.C08.1 Rename `src/components/access/check/` and `src/components/access/nodes/`. Commit the renames only.
- [x] 2.C08.2 Add types to the 12 modules (node grammar, tree and templates) first, then the 8 components, and convert the 14 tests. The node grammar is a parser: its tests must pass unchanged.
- [x] 2.C08.3 Run the gates.
- [x] 2.C08.4 Open the pull request `refactor(ui): type the access check and node editor components`.

### 2.C09 identity and account (19 files) - `refactor(ui): type the identity and account components`

Starts after #238 (`account`, 2 files) is merged.

- [x] 2.C09.1 Rename `src/components/identity/` and `account/`. Commit the renames only.
- [x] 2.C09.2 Add types and convert the tests.
- [x] 2.C09.3 Run the gates, plus the sign-in and account specs.
- [x] 2.C09.4 Open the pull request `refactor(ui): type the identity and account components`.

### 2.C10 settings (83 files) - `refactor(ui): type the settings components`

Largest slice. At start, split by file list if it exceeds two working days (rule 8): suggested halves are the `.vue` views and dialogs, and the `.js` helpers with their tests. Record the split here before the second half.

- [x] 2.C10.1 Rename `src/components/settings/` (including `fixtures/routes.js`). Commit the renames only.
- [x] 2.C10.2 Add types to the helper modules (domain, collections, import, export, encryption, wizard) first, then the 20 components, and convert the 47 tests. Settings import and export formats do not change; their tests must pass unchanged.
- [x] 2.C10.3 Update `DESIGN.md` and the registry `file` fields for moved anchored components. Run the gates, plus the settings-related e2e specs.
- [ ] 2.C10.4 Open the pull request `refactor(ui): type the settings components`.

### 2.C11 schema renderers (35 files) - `refactor(ui): type the schema renderers`

- [x] 2.C11.1 Rename `src/components/renderers/`. Commit the renames only.
- [x] 2.C11.2 Add types to the renderer props (the JSON Forms renderer contract, typed with the `@jsonforms/core` types). The `any` values that the JSON Forms API forces are kept with a `// any:` reason and budgeted per file (D5).
- [x] 2.C11.3 Run the gates, plus the settings e2e specs that use the renderers.
- [x] 2.C11.4 Open the pull request `refactor(ui): type the schema renderers`.

### 2.C12 maintenance and audit (34 files) - `refactor(ui): type the maintenance and audit components`

- [x] 2.C12.1 Rename `src/components/maintenance/` and `audit/`. Commit the renames only.
- [x] 2.C12.2 Add types and convert the tests.
- [x] 2.C12.3 Run the gates.
- [x] 2.C12.4 Open the pull request `refactor(ui): type the maintenance and audit components`.

### 2.C13 repositories (21 files) - `refactor(ui): type the repository components`

- [x] 2.C13.1 Rename `src/components/repositories/`, including `fixtures/repositories.js`. Commit the renames only.
- [x] 2.C13.2 Add types and convert the tests.
- [x] 2.C13.3 Run the gates, plus the repositories and repositories-crud e2e specs.
- [x] 2.C13.4 Open the pull request `refactor(ui): type the repository components`.

### 2.C14 browser (21 files) - `refactor(ui): type the browser components`

Starts after #238 (3 files) is merged.

- [x] 2.C14.1 Rename `src/components/browser/`. Commit the renames only.
- [x] 2.C14.2 Add types and convert the tests. Upload queue and listing error modules keep their behaviour.
- [x] 2.C14.3 Run the gates, plus the browse and browse-overview e2e specs.
- [x] 2.C14.4 Open the pull request `refactor(ui): type the browser components`.

### 2.C15 migration (25 files) - `refactor(ui): type the migration components`

Starts after #238 (1 file) is merged. The store half of the migration is typed in 1.C.

- [x] 2.C15.1 Rename `src/components/migration/`. Commit the renames only.
- [x] 2.C15.2 Add types and convert the tests. `MigrationDialog.vue` keeps its scrim value and its markup.
- [x] 2.C15.3 Run the gates.
- [x] 2.C15.4 Open the pull request `refactor(ui): type the migration components`.

## 3. Phase 3: server, fake API, e2e and configuration

Starts after all Phase 2 pull requests are merged, except e2e specs that a Phase 2 slice owns (those are converted in 3.S03 and listed there). 3.S04 also starts only after 3.S02 is merged, because the scripts it converts run through `tsx`. Group type: `refactor(ui)`.

### 3.S01 server (16 files) - `refactor(ui): type the nitro server`

- [x] 3.S01.1 Rename `server/lib/`, `server/routes/[...].js` and `server/plugins/dashboard.js`, and their five tests. Commit the renames only. Nitro loads `.ts` natively; keep the route file name pattern `[...].ts`.
- [x] 3.S01.2 Add types. The security headers and the placeholder handling in `index-html.js` do not change their output: the test for each stays unchanged.
- [x] 3.S01.3 Run the gates, plus `npm run build:dashboard` and compare the built server output with the baseline.
- [x] 3.S01.4 Open the pull request `refactor(ui): type the nitro server`.

### 3.S02 fake API (15 files) - `refactor(ui): type the fake api`

- [x] 3.S02.1 Rename `fake-api/fake-ingot-backend.js` to `.ts`, and the 14 `.cjs` fixtures to `.ts` modules if they are imported, otherwise to `.json` if they are data. Commit the renames only.
- [x] 3.S02.2 Add `tsx` (4.23.15, exact devDependency) and change the `fake-api` npm script to run the `.ts` file through it. Add types.
- [x] 3.S02.3 Run the e2e baseline against the fake API started by `npm run fake-api`. Stop the server afterwards.
- [x] 3.S02.4 Open the pull request `refactor(ui): type the fake api`.

### 3.S03 e2e suite (21 files) - `refactor(ui): type the e2e suite`

- [x] 3.S03.1 Rename `e2e/*.spec.js` (13) and `e2e/support/` (8). Commit the renames only. Also rename every `e2e/` spec that a Phase 2 slice has not yet converted, listed in the pull request.
- [x] 3.S03.2 Add types, using the Playwright types. `test.fail(true, '<change>')` markers keep their text.
- [x] 3.S03.3 Run the full e2e suite once with `heavy npm run e2e -- --workers=2`, not the smoke set alone, because this slice touches every spec.
- [ ] 3.S03.4 Open the pull request `refactor(ui): type the e2e suite`.

### 3.S04 configuration and scripts (6 files) - `refactor(ui): type the build and test configuration`

- [x] 3.S04.1 Rename `vite.config.js`, `vitest.config.js`, `playwright.config.js`, `eslint.config.js`, `nitro.config.mjs`, `scripts/anchors-doc.mjs` and `scripts/generate-api-types.mjs`. Change the `anchors:doc` and `types:api` npm scripts to run the `.ts` files through `tsx`. Commit the renames only.
- [ ] 3.S04.2 Add types. The Vite plugins for the mount point and the dashboard placeholders keep their output: `index.html` after `npm run build` is compared byte for byte with the baseline.
- [ ] 3.S04.3 Run the gates, the build comparison and `npm run anchors:doc`, and confirm the generated doc is unchanged.
- [ ] 3.S04.4 Open the pull request `refactor(ui): type the build and test configuration`.

## 4. Phase 4: strict everywhere

Group type: `refactor(ui)`. Starts after all earlier pull requests are merged. The maintainer decided (2026-11-23) to split the phase into
small pull requests, one per item and, for the suppressions, one per ESLint rule. Every pull request is independent of the others
except 4.3.R, which runs last, and each one leaves `npm run typecheck`, `npm run eslint` and the unit suite green on its own. A pull
request removes only the suppressions of its own rule from `eslint-suppressions.json` (`npx eslint --prune-suppressions` limited to
that rule, or by hand), fixes the code instead of suppressing it again, and changes no behaviour. The counts below are those of
`reposilite-frontend/eslint-suppressions.json` when this split was written; a pull request re-counts before it starts.

### 4.1 The `any` budget

- [x] 4.1.1 Confirm the count of explicit `any` values under `src/` is zero or only the budgeted reasons, then remove `src/types/any-budget.json` and its test, and reword the remaining `// any:` comments into typed code where the count allows.
- [x] 4.1.2 Open the pull request `refactor(ui): remove the any budget of the dashboard`.

### 4.2 JavaScript settings

- [x] 4.2.1 Set `allowJs: false` in every tsconfig, remove `checkJs` and the transition comments, and confirm `strict: true` in all four files. Run `npm run typecheck`.
- [x] 4.2.2 Open the pull request `refactor(ui): disallow javascript in the dashboard tsconfig files`.

### 4.3 Suppressions, one pull request per rule

Pull request title pattern: `refactor(ui): fix the <rule> findings of the dashboard`. Each pull request also names its rule in the description.

- [x] 4.3.1 `@typescript-eslint/require-await` (104): drop `async` where nothing is awaited, or return the promise. Open the pull request.
- [x] 4.3.2 `@typescript-eslint/no-unsafe-assignment` (147): type the source of each assignment. Open the pull request.
- [x] 4.3.3 `@typescript-eslint/no-unsafe-argument` (63): type the argument or its source. Open the pull request.
- [x] 4.3.4 `@typescript-eslint/unbound-method` (29): bind the method or use an arrow function. Open the pull request.
- [x] 4.3.5 `@typescript-eslint/no-floating-promises` (27): await, return or mark the promise with `void` where fire and forget is intended. Open the pull request.
- [x] 4.3.6 `@typescript-eslint/no-unsafe-call` (25) and `@typescript-eslint/no-unsafe-return` (1): type the callee and the return value. Open the pull request.
- [x] 4.3.7 `@typescript-eslint/no-base-to-string` (16), `@typescript-eslint/restrict-template-expressions` (6), `@typescript-eslint/prefer-promise-reject-errors` (1) and `@typescript-eslint/await-thenable` (1): the small type-aware rules, one pull request because each is a one-line fix. Open the pull request.
- [x] 4.3.8 The `vue/*` template formatting rules (`html-indent` 19, `html-closing-bracket-newline` 7, `max-attributes-per-line` 4, `first-attribute-linebreak` 3, `multiline-html-element-content-newline` 2, `html-self-closing` 2, `html-closing-bracket-spacing` 1): run the formatter on the listed files only. Open the pull request.
- [x] 4.3.9 `no-unused-vars` (1), `@typescript-eslint/no-unused-vars` (1) and `vue/no-v-html` (1): remove the variable, or keep `v-html` with a reason beside it if the content is a trusted constant. Open the pull request.
- [x] 4.3.R After 4.3.1 to 4.3.9 are merged: confirm `eslint-suppressions.json` is `{}` (delete the leftover keys of any rule not listed above), and remove the `.js` entries from the Vitest, style-gate and anchor patterns that no longer match any file. Open the pull request `refactor(ui): remove the eslint suppressions and javascript patterns`.

### 4.4 Remaining JavaScript and the closing check

- [x] 4.4.1 List any `.js` file left under `src/`, `server/`, `e2e/` or `fake-api/` in the pull request. The expected list is empty; any vendored or generated file needs a sentence of reason and no conversion.
- [ ] 4.4.2 Run every gate, plus the full e2e suite and the build comparison against the Phase 0 baseline, with `heavy`. Open the pull request `refactor(ui): confirm the dashboard builds as before the typescript migration`, which changes no file unless a gate finds a difference.

## 5. Follow-ups (not part of the code pull requests)

- [ ] 5.1 Only when the maintainer asks: archive this change in a pull request titled `docs(openspec): archive migrate-dashboard-to-typescript`, with the task boxes ticked there and nowhere else.
- [ ] 5.2 TypeScript 7 adoption, once `typescript-eslint` accepts it (question 2): a separate `build(ui)` change.
