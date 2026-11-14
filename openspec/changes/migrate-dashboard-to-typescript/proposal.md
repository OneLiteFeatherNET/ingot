# Proposal

## Open maintainer questions

Each question has a default. The change is written against the default; a different answer changes only the tasks named in brackets.

1. **Phase 0 under a second type.** The migration ships under `refactor(ui)`. Phase 0 adds tooling, which is `build(ui)` for `package.json`, the tsconfig files and the Vite and Vitest changes, and `ci(ui)` for the workflow step. The OpenSpec rule that a change mixing types is two changes would split Phase 0 out. Default: keep one proposal. The Phase 0 pull request is titled `build(ui): add typescript tooling to the dashboard`, and every later pull request is `refactor(ui)`. Alternative: a separate proposal `add-dashboard-typescript-tooling` under `build(ui)`, merged before this one, with Phase 0 removed from these tasks. [tasks 0.x, Shipping]
2. **TypeScript major version.** `typescript-eslint` 8.72.1 (the newest line that is at least two weeks old) declares `typescript >=4.8.4 <6.1.0`, so TypeScript 7.0.2 cannot be installed next to it. Default: pin `typescript` 6.0.3, the newest 6.x release (2026-04-16). Alternative: wait for a `typescript-eslint` release that accepts 7.x and migrate the compiler in a separate `build(ui)` change. [tasks 0.2]
3. **Strictness.** Default: `strict: true` in the tsconfig that covers `.ts` files from the first `.ts` file on. `checkJs` stays off during the transition, so JavaScript is not checked until Phase 4. Reason: new `.ts` code must not start lax, and turning strictness on for 667 files at the end would be a larger change than the conversion itself. Alternative: `strict: false` until Phase 4, then flip. [tasks 0.3, 4.2]
4. **API types: generated or hand-written.** Default: generate `src/types/api.ts` from `docs/openapi/ingot-openapi.json` with `openapi-typescript` 7.13.0, check the output in, and add a test that regenerates into a temporary directory and compares. The generated file is committed so the Docker build never needs the repository's `docs/` directory. Alternative: hand-written DTO types per store module, which need no generator and no freshness test but drift silently from the backend. [tasks 1.1]
5. **Options API components.** Default: a component with no `this`, no mixins and no `$options` access is converted to `<script setup lang="ts">`, and its `name` and `inheritAttrs` options move into `defineOptions`. Any other Options API component keeps its shape and is typed with `defineComponent`. Alternative: convert all 17 to script setup in the slice that owns them, which is a larger behaviour risk. [tasks 2.x, each slice]
6. **Whether e2e and fake-api migrate.** Default: yes, in Phase 3. The Playwright specs become `.ts` and are type-checked by a `tsconfig.tests` project. The fake API stays a Node program, so it runs through `tsx` (a pinned dev dependency) and is type-checked in the same way. Alternative: leave `e2e/` and `fake-api/` as JavaScript and exclude them from `allowJs`, which keeps them out of the typecheck gate. [tasks 3.2, 3.3]
7. **What counts as an `any` and how the budget is enforced.** Default: `@typescript-eslint/no-explicit-any` is an error. Each remaining explicit `any` or `as any` carries a `// any: <reason>` comment on the same line, and a test counts them per file against `src/types/any-budget.json`. A count may only fall; raising a number needs a reviewed change to that file. Alternative: rely on the lint rule and its suppressions file alone. [tasks 0.5]
8. **Build comparison.** Default: the built CSS is compared byte for byte. The built JavaScript is not byte-identical after conversion (the Vue compiler emits different helper code for `lang="ts"` blocks), so it is compared by the size of each chunk and by the e2e smoke run, and a growth above 2 percent in any chunk needs a note in the pull request. Alternative: normalise the JavaScript (strip comments and whitespace, compare the parsed output), which catches more but costs a tool. [tasks 0.6, each slice]
9. **Plugin-facing types.** Default: nothing is published for plugin authors in this change. Plugins reach the dashboard through `{{REPOSILITE.*}}` placeholders, the UI and stylesheet extension points and `data-ingot-*` attributes, none of which is a module import from `src/`. Alternative: publish a `.d.ts` for the extension points as part of this change, which is a new public surface and belongs in its own proposal. [tasks 0.7]

## Why

The dashboard is a Vue 3 application of about 670 files under `reposilite-frontend/src/`, and none of them is typed. A field that the backend renames still builds, and the first place it fails is a page that a user opens. The response shapes are described again in each store module and each component that reads them, and the props of a primitive are described only by its template. Review catches what it can, and the end-to-end suite catches the rest a few minutes later.

The codebase is ready for types in the parts that matter most, and the work is mechanical enough to split. 179 of the 217 single-file components already use `<script setup>`, 17 use the Options API, and 313 test files sit next to the code they cover. The contracts that plugins and operators depend on are strings and attributes, not imports, so a type migration can leave them alone. The work is large (667 files) and most of it is a rename plus type annotations, which means many agents can do it at once if each one owns a directory that no other one touches.

The change therefore sets the order of the work and its gates, not only its goal. Tooling comes first and shared types second, so that each component slice can import typed stores and API shapes from the start. Each slice is one short-lived pull request. The gates (typecheck, lint, unit tests, a CSS byte comparison of the build, and the e2e smoke run) make a slice that changes behaviour fail visibly instead of quietly.

## What Changes

The migration runs in five phases. The slices and their file counts are in `design.md`, and each slice is one task group in `tasks.md`.

- **Phase 0, tooling (one pull request, blocks everything else).** TypeScript, `vue-tsc`, `@vue/tsconfig` and `typescript-eslint` are added at exact versions. Tsconfig files cover the application, the tests, the Node side and the server, all with `allowJs` on and `checkJs` off. Path aliases mirror `vite.config.js`. `npm run typecheck` runs `vue-tsc --build --noEmit` and the CI frontend job runs it. Vite and Vitest pick up `.ts` and `.test.ts`. ESLint gets rules for `.ts` and for `.vue` with `lang="ts"`. The style gate scans `.ts` and skips `.test.ts`. `env.d.ts` declares the Vite and global types and the `{{REPOSILITE.*}}` placeholders.
- **Phase 1, shared modules and types.** The API types are generated (question 4). The stores, the router, the helpers, the navigation registry, the theme and the anchor registry are converted in four parallel slices, so that components can import typed names.
- **Phase 2, components.** Fifteen slices, by directory, convert `.vue` files to `<script setup lang="ts">` (question 5) and their tests to `.test.ts`. No slice touches a directory another slice owns.
- **Phase 3, server, fake API, e2e and configs.** Four slices: the Nitro server under `server/`, the fake API, the Playwright suite and the configuration files.
- **Phase 4, strict everywhere.** `allowJs` and the transition settings are removed, `strict` applies to every file, the transition shims go and `eslint-suppressions.json` is emptied.

Behaviour does not change. A slice may rename files, add type annotations and move type-only code. It may not change a component's template, props, emitted events, routes, requests, copy or styles.

## Capabilities

### New Capabilities

- `dashboard-typescript`: the dashboard source is TypeScript under a strict compiler setting, its API shapes come from the committed OpenAPI description, and the count of explicit `any` values only falls. The served dashboard, its URLs, its placeholders, its extension points and its anchors are the same before and after.

## Impact

- **Operators:** nothing to change. The image, the entrypoint, the port, the volume, the environment variables, `{{REPOSILITE.*}}`, the served routes and `src/router/dashboard-segments.json` (which the chart copy compares byte for byte in CI) are unchanged. No configuration key, deployment manifest or stored data changes. This change is not BREAKING.
- **Plugin authors:** nothing to change. Plugin UI and stylesheet extension points and `data-ingot-*` attributes keep their names and their behaviour. No module under `src/` is published.
- **Developers:** new development dependencies (TypeScript, `vue-tsc`, `@vue/tsconfig`, `typescript-eslint`, `openapi-typescript`, and `tsx` in Phase 3), a `typecheck` script, and a typed editor experience. The Node engine (`>=20.19.0`) does not change.
- **Code:** `reposilite-frontend/` only. No file under `reposilite-backend/` changes. `docs/openapi/ingot-openapi.json` is read by the generator and by its freshness test; it is not changed.
- **Documentation:** `reposilite-frontend/DESIGN.md` source list and `README.md` development commands are updated in the slices that move the files they name. The anchor registry (`src/anchors/registry.json`) carries file paths and is updated with each rename that touches an anchored component. `docs/gitbook/` is not touched.

## Out of scope

- Any change of behaviour, visual design, copy, routes, requests or stored data, including bug fixes found while converting a file (those go in their own small pull requests).
- Changes to the Kotlin backend or to the OpenAPI description. The generator reads the description as it is.
- Adoption of TypeScript 7 (blocked, question 2).
- Rewriting the 17 Options API components into composition-style code beyond the trivial conversion in question 5.
- Replacing Vue, Vite, Vitest, Playwright, Nitro, ESLint or Tailwind.
- Changes to the style-gate rules. The gate only gains the `.ts` extension in its scan.
- A published type package for plugin authors (question 9).

## Shipping

- Proposal pull request, under the title `docs(openspec): propose migrate-dashboard-to-typescript`. It contains only the files under `openspec/changes/migrate-dashboard-to-typescript/`, with every task unchecked, branched from the remote main branch. It merges before any code pull request is opened.
- Phase 0 pull request, under the title `build(ui): add typescript tooling to the dashboard` (question 1). Commits: `build(ui)` for the packages and configs, `ci(ui)` for the workflow step. It is the only pull request that touches `package.json` and `package-lock.json` for the migration.
- Phase 1 to 4 code pull requests, one per slice, under the titles given in `tasks.md`, all of the form `refactor(ui): type the <slice> ...`, branched from the remote main branch that already contains the merged proposal and the merged earlier phase. Every commit is `refactor(ui)`: a rename-only commit first, then the commit with the type annotations. A slice never changes a test body (`design.md`, rule 2), so no `test(ui)` commit is needed. No file under `openspec/` is in a code pull request.
- Optional follow-up pull request, only when the maintainer asks for it: `docs(openspec): archive migrate-dashboard-to-typescript`.

## Verification

- `openspec validate migrate-dashboard-to-typescript --strict` passes.
- Every slice pull request passes, on its own: `npm run typecheck`, `npm run eslint`, `npm test` (the unit suite), `npm run build` with the CSS byte comparison and the chunk size report, and the e2e smoke run (`npm run e2e -- --workers=2`, a targeted set of specs listed in the slice).
- The served routes, the `{{REPOSILITE.*}}` placeholders in `index.html`, the `data-ingot-*` attributes and `src/router/dashboard-segments.json` are unchanged, and the anchor test in `src/anchors/anchors.test.js` passes.
- After Phase 4, `src/` contains no `.js` file except the generated and vendored files named in `design.md`, and the explicit `any` count is zero or budgeted per file.
- The dash grep prints nothing in any file of this change.
