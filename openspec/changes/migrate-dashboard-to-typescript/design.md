# Design

## Measured starting point

Counted on origin/main at the time of the proposal, under `reposilite-frontend/`:

| Area | Vue SFCs | Source `.js` | Test `.test.js` | Other |
|---|---|---|---|---|
| `src/components/` | 215 | 73 | 231 | |
| `src/store/` | 0 | 35 | 40 | |
| `src/router/` | 0 | 8 | 11 | `dashboard-segments.json` (JSON, kept) |
| `src/helpers/` | 0 | 9 | 7 | |
| `src/navigation/`, `src/theme/`, `src/anchors/` | 0 | 6 | 9 | `anchors/registry.json` |
| `src/style-gate/`, `src/styles/` | 0 | 5 | 12 | style-gate baseline and allowlist JSON |
| `src/pages/`, `src/App.vue`, `src/main.js`, `src/style.test.js` | 2 | 1 | 3 | |
| `server/` | 0 | 11 | 5 | Nitro routes and plugins |
| `fake-api/` | 0 | 1 | 0 | plus 14 `.cjs` fixtures |
| `e2e/` | 0 | 21 | 0 | 13 specs and 8 support modules |
| Root configs and `scripts/` | 0 | 6 | 0 | `vite`, `vitest`, `playwright`, `eslint`, `nitro` |

Totals: 217 SFCs (179 use `<script setup>`, 17 use the Options API, and 22 use neither pattern and are classified in Phase 0), 313 unit test files under `src/`, no `.ts` file, no `tsconfig`, no `lang="ts"`. 88 relative imports in 44 files name a `.js` extension explicitly, and 704 name `.vue`.

The `.vue` extension in imports stays. The explicit `.js` extension is cosmetic, see decision D6.

## Pins

Versions are chosen so that each is at least two weeks old on the day its Phase 0 pull request is opened. The dates below are from the registry at proposal time and are re-checked then.

| Package | Pin (candidate) | Released | Note |
|---|---|---|---|
| `typescript` | 6.0.3 | 2026-04-16 | 7.0.2 is not installable with `typescript-eslint`, see D1 |
| `vue-tsc` | 3.3.14 | 2026-10-29 | peer `typescript >=5.0.0` |
| `@vue/tsconfig` | 0.9.1 | 2026-03-24 | base tsconfig for Vue projects |
| `typescript-eslint` | 8.72.1 | 2026-10-26 | 8.72.2 (2026-11-09) is too new; peer `typescript >=4.8.4 <6.1.0` |
| `openapi-typescript` | 7.13.0 | 2026-02-11 | generator for D4 |
| `tsx` | 4.23.15 | 2026-09-20 | Phase 3 only, runs the fake API |

Phase 0 re-checks each date and each peer range with `npm view` and records them in the pull request.

## Decisions

**D1. TypeScript 6.0.3, not 7.** `typescript-eslint` 8.72.1 declares `typescript <6.1.0`, so npm refuses 7.0.2 with the rule set we need. Phase 0 pins 6.0.3 and records the blocker. Moving to 7 is a separate `build(ui)` change once `typescript-eslint` accepts it.

**D2. Project layout.** Four tsconfig files, all extending `@vue/tsconfig/tsconfig.dom.json` or `tsconfig.node.json`, with `moduleResolution: "bundler"` so extensionless imports and Vite's resolution match:

- `tsconfig.app.json`: `src/**` (including `.vue`), `allowJs: true`, `checkJs: false`, `strict: true`, `jsx` off, `types` for `vite/client` and `env.d.ts`.
- `tsconfig.tests.json`: `src/**/*.test.ts`, `e2e/**`, the same settings plus the Vitest and Playwright globals.
- `tsconfig.node.json`: `vite.config.js`, `vitest.config.js`, `playwright.config.js`, `eslint.config.js`, `nitro.config.mjs`, `scripts/**`, `server/**`.
- `tsconfig.json` as a solution file that references the three, so `vue-tsc --build` checks all of them.

Path aliases mirror the `resolve.alias` entries of `vite.config.js`, if it has any; Phase 0 reads the whole file to confirm which exist. `npm run typecheck` runs `vue-tsc --build --noEmit`. Phase 0 also confirms the export names of the `@vue/tsconfig` base files it extends.

**D3. Strictness.** `strict: true` from the first `.ts` file. With `checkJs: false`, JavaScript is untouched until Phase 4, so the setting only bites new and converted `.ts` and `.vue` files. Phase 4 sets `allowJs: false` and removes the remaining transition settings.

**D4. API types.** `src/types/api.ts` is generated from `docs/openapi/ingot-openapi.json` by `scripts/generate-api-types.mjs` (`openapi-typescript`, default output, no hand edits). The file is committed. The freshness test lives in `src/types/api.test.ts`, regenerates into a `@TempDir`, and asserts equality with the committed file. Its one read outside the temporary directory is the committed OpenAPI file, the same kind of input the style gate reads from `src/`. The test does not use the network or the system clock. The Docker build does not run the generator, because the Dockerfile context does not contain `docs/`.

Stores and components import named schema types from `src/types/api.ts`, never from the generated file's internal paths.

**D5. The `any` budget.** `@typescript-eslint/no-explicit-any` is an error. Remaining explicit `any` or `as any` must sit on a line with a `// any: <reason>` comment. `src/types/any-budget.json` maps each file to its allowed count. A unit test (`src/types/any-budget.test.ts`) counts the tokens in every `.ts` and `.vue` file under `src/` and fails when a file has more `any` than its entry, when a file with `any` has no entry, or when an entry is higher than the count. The last rule makes a fall visible: a slice that removes an `any` lowers the entry in the same pull request, so the budget only shrinks. Phase 4 deletes the budget file and the test once the count is zero.

**D6. Extensionless imports.** Renames need no importer edits: TypeScript with bundler resolution and Vite both map `./x.js` to `./x.ts`. The 88 explicit `.js` specifiers in 44 files are still removed once, in the first commit of `P1-0`, so the tree uses one spelling afterwards. That commit changes no behaviour and is reviewed on its own. Vue imports keep `.vue`.

**D7. ESLint.** `typescript-eslint` recommended type-aware rules apply to `.ts`, and `vue-eslint-parser` with `parserOptions.parser` set to the TypeScript parser applies to `<script lang="ts">`. The existing "raise warnings to errors" wrapper in `eslint.config.js` stays and covers the new rules. `eslint-suppressions.json` is keyed by file path, so every rename rewrites that file's keys in the same commit; the file is sorted by key, so each rename is a small, reviewable diff. Phase 4 empties the file.

**D8. Style gate and anchors.** `src/style-gate/sources.js` adds `.ts` to `SCANNED_EXTENSIONS` and excludes `.test.ts` next to `.test.js`. The baseline and allowlist JSON files key on paths, so a renamed file's entries are rewritten in its slice. `src/anchors/checks.js` (the `.js` or `.vue` filter at line 315) gains `.ts` in Phase 0, together with the style gate. `src/anchors/registry.json` carries a `file` for each anchored element; a slice that renames an anchored component updates that `file` and runs `npm run anchors:doc`.

**D9. Build comparison.** Every slice records, in its pull request, the output of `npm run build` before and after:

- The CSS bundle must be byte-identical, after normalising only the content hash in the file name.
- The JavaScript chunks are listed with their sizes before and after. A chunk that grows by more than 2 percent needs a sentence in the pull request. Byte equality is not required for JavaScript, because the Vue compiler's output for `lang="ts"` differs in helper code.
- The e2e smoke set must pass with no changed snapshots. For a component slice it is the specs that render the slice's components, named in its pull request; the baseline set for every slice is `dashboard`, `layout`, `navigation`, `repositories` and `sign-in`.

**D10. What never changes in a slice.** Template markup, props and their defaults, emitted events, `data-ingot-*` attributes, route names and paths, request URLs and bodies, user-visible copy, and CSS classes. A slice may add `defineProps<...>()`, `defineEmits<...>()`, type annotations, type-only imports, `defineOptions({ name, inheritAttrs })` for the Options API conversion (question 5), and `satisfies` or `as const` where the value does not change at runtime.

## Slice rules

These apply to every code pull request in Phases 1 to 4.

1. **One owner per directory.** A slice changes only the files in its paths. Edits to files outside them are limited to the anchor registry `file` field (D8), the `DESIGN.md` source list, and the eslint suppression keys of files it renames. A rename needs no importer edit (D6).
2. **Tests move without edits.** A slice renames `x.test.js` to `x.test.ts` and adds types. A test body changes only when the test would not compile otherwise, and then the change is listed in the pull request. No test is deleted or skipped.
3. **Rename first.** The first commit of each slice is `git mv` only, with no content change, so that `git log --follow` and rebase rename detection keep working for other open pull requests. The second commit adds types. Both are `refactor(ui)`.
4. **Options API.** A component is converted to `<script setup lang="ts">` only when it has no `this`, mixins, `$options`, `$refs` read through `this`, or `inheritAttrs` surprise. The others are typed with `defineComponent` and keep their options object.
5. **No `any` except with a reason** (D5), and no `@ts-ignore`. `@ts-expect-error` is allowed only with a reason and only in a test.
6. **Gates before push.** `npm run typecheck`, `npm run eslint`, `npx vitest run --maxWorkers=2` for the whole unit suite, `npm run build` with the comparison of D9, and the e2e smoke set of the slice with `npm run e2e -- --workers=2`. Heavy jobs run through `heavy` (the slot runner) with at most four in flight machine-wide.
7. **Rebase before merge.** A slice branch is rebased on the current `origin/main` immediately before it is marked ready. If the rebase changes a file's content, the gates run again.
8. **Short-lived.** A slice is one pull request that is open for at most two working days. A slice that cannot finish in that time is split by file list, not by phase.

## Waves and merge order

| Wave | Content | Starts when |
|---|---|---|
| 0 | Phase 0 tooling pull request | the proposal is merged |
| 1a | `P1-0` API types and the `.js`-extension removal (D6) | Phase 0 is merged |
| 1b | `P1-1` to `P1-4` modules, in parallel | `P1-0` is merged |
| 2 | Component slices `C01` to `C15`, in waves of at most four | `P1-1` to `P1-4` are merged, and the open pull request named for the slice is merged (see the conflict table) |
| 3 | `S01` to `S04` server, fake API, e2e and configs | all of Phase 2 is merged, except the e2e specs that a Phase 2 slice owns |
| 4 | Strictness, shim removal, empty suppressions, budget deletion | all earlier phases are merged |

The waves are deliberately coarse. Within a wave, slices run in parallel only when their paths do not overlap, which the slice table guarantees.

## Phase 1 slices

| Slice | Paths | JS | Tests | Total | Notes |
|---|---|---|---|---|---|
| `P1-0` | `src/types/` (new), `scripts/generate-api-types.mjs`, D6 import edit | 0 | 0 | new | two commits: the D6 import edit (44 files, one line each, no rename), then the generated file and its freshness test |
| `P1-1` | `src/store/*.js`, `src/store/console/` | 21 | 31 | 52 | store core and console connection |
| `P1-2` | `src/store/migration/`, `src/store/maven/` | 14 | 9 | 23 | migration wizard and Maven repository model |
| `P1-3` | `src/router/`, `src/navigation/`, `src/theme/`, `src/anchors/` | 14 | 20 | 34 | `registry.js` is edited by #219 and #214 |
| `P1-4` | `src/helpers/`, `src/style-gate/`, `src/styles/` | 14 | 19 | 33 | the style gate's `.ts` scan is in this slice |

## Phase 2 slices

Counts are by file, measured on origin/main. "Vue" counts `.vue` files. "JS" counts non-test modules. "Tests" counts `*.test.js`. Pull request titles are `refactor(ui): type the <slice> components`, or the wording in `tasks.md`.

| Slice | Paths | Vue | JS | Tests | Total | Open pull requests that touch it |
|---|---|---|---|---|---|---|
| `C01` md primitives | `src/components/md/` | 17 | 0 | 17 | 34 | #238 (16 files), #214 (`MdOverflowMenu`, `MdNavigationBar`), #219 (`MdNavigationBar`) |
| `C02` icons | `src/components/icons/` | 33 | 1 | 2 | 36 | none |
| `C03` layout and chrome | `src/components/layout/`, `header/`, `footer/`, `overview/`, `console/`, `src/pages/`, `src/App.vue`, `src/main.js`, `src/style.test.js` | 16 (14 components, `App.vue`, `IndexPage.vue`) | 4 (3 components, `main.js`) | 20 | 40 | #238 (`layout`, `IndexPage`), #214 (`MenuPanel`), #219 (`IndexPage`) |
| `C04` shared leaves | `src/components/util/`, `tokens/`, `card/`, `support/`, `plugins/` | 19 | 3 | 18 | 40 | #238 (`tokens`) |
| `C05` search | `src/components/search/` | 4 | 2 | 4 | 10 | #238 (1 file) |
| `C06` dashboard | `src/components/dashboard/` | 14 | 6 | 20 | 40 | #238 (2 files) |
| `C07` access panels and dialogs | `src/components/access/*` (top level only) | 22 | 9 | 22 | 53 | #238 (1 file) |
| `C08` access check and nodes | `src/components/access/check/`, `access/nodes/` | 8 | 12 | 14 | 34 | none |
| `C09` identity and account | `src/components/identity/`, `account/` | 9 | 1 | 9 | 19 | #238 (`account`, 2 files) |
| `C10` settings | `src/components/settings/` (including `fixtures/`) | 20 | 16 | 47 | 83 | none; largest slice, may be split by file list at start |
| `C11` renderers | `src/components/renderers/` | 16 | 3 | 16 | 35 | none |
| `C12` maintenance and audit | `src/components/maintenance/`, `audit/` | 12 | 6 | 16 | 34 | none |
| `C13` repositories | `src/components/repositories/` | 7 | 4 | 10 | 21 | none |
| `C14` browser | `src/components/browser/` | 8 | 4 | 9 | 21 | #238 (3 files) |
| `C15` migration | `src/components/migration/` | 12 | 3 | 10 | 25 | #238 (1 file) |
| **Total** | | **217** | **74** | **234** | **525** | |

Phase 1 covers the other 63 non-test modules and 79 test files of `src/`, so the phases together cover the measured 217 SFCs, 137 non-test modules and 313 test files of `src/`.

### Conflict strategy for open pull requests

- A slice whose files are touched by an open pull request does not start until that pull request is merged. Currently that is `C01`, `C03`, `C04`, `C05`, `C06`, `C07`, `C09`, `C14` and `C15` (for #238), and `C01`, `C03` and `P1-3` (for #214 and #219).
- The md3 token pull request (#238) and the catalogue change (`add-md3-component-catalogue`) are both listed in the affected slices. A slice is not reordered by a later pull request: if a new pull request touches a slice's files after the slice has started, the slice rebases and runs its gates again.
- `package.json` and `package-lock.json` change only in Phase 0 and in `S02`/`S04` (for `tsx`). #214 edits `package.json`, so Phase 0 waits for #214 or rebases on it.
- The `DESIGN.md` source list is edited by the slice that moves a file it names, in that slice's rename commit. It is not edited in a separate docs pull request.

## Phase 3 slices

| Slice | Paths | Files | Notes |
|---|---|---|---|
| `S01` server | `server/lib/` (9 modules, 5 tests), `server/routes/`, `server/plugins/` | 16 | Nitro reads `.ts` natively; the server tests join the unit run |
| `S02` fake API | `fake-api/fake-ingot-backend.js` and the 14 `.cjs` fixtures | 15 | converted to `.ts`, run through `tsx`; the `fake-api` npm script changes in this slice |
| `S03` e2e | `e2e/*.spec.js` (13), `e2e/support/` (8) | 21 | `tsconfig.tests.json`; the smoke set is the baseline set of D9 (`dashboard`, `layout`, `navigation`, `repositories`, `sign-in`), plus any spec the slice touches |
| `S04` configs and scripts | `vite.config.js`, `vitest.config.js`, `playwright.config.js`, `eslint.config.js`, `nitro.config.mjs`, `scripts/*.mjs` (the scripts run through `tsx`, so the slice starts after `S02`) | 6 | the Vite config keeps the `{{REPOSILITE.*}}` and `dashboard` build plugins byte-identical in behaviour; `eslint.config.js` loses its `.js`-only block |

Phase 3 does not change the Dockerfile, the chart or the workflow beyond the typecheck step that Phase 0 already adds.

## Phase 4

Phase 4 is a single small pull request, or one per item if the maintainer asks: set `allowJs: false`, `checkJs` removed, `strict` on the tests and node configs, delete `src/types/any-budget.json` and its test once the count is zero, empty `eslint-suppressions.json`, remove the transition comments, and remove the `.js` entries from the `include` patterns. `src/` may keep JavaScript only for the vendored or generated files that cannot be typed, each listed in `design.md` at the time with a reason. Expected: none.

## Risks

- **Vue compiler output.** `lang="ts"` changes compiled output. D9 covers the CSS, the chunk sizes and the e2e snapshots. A subtle change in `defineProps` default handling would show up in the unit tests of the component.
- **Anchor registry churn.** Many slices edit `registry.json`. Keys are sorted, and each slice regenerates the anchor docs, so conflicts are small but frequent. The rebase rule of the slice rules applies.
- **Long tail of `any`.** Third-party types (`@jsonforms/*`, `vue-upload-component`, `echarts`) are weakly typed. Their wrappers get the `any` comment and a per-file budget entry, rather than a global exemption.
- **Parallel agents on a shared machine.** Four slices in flight at once is the limit; `heavy` enforces the memory floor.
