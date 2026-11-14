# Spec Delta

## Purpose

The dashboard source under `reposilite-frontend/` is TypeScript under a strict compiler setting, its response shapes come from the committed OpenAPI description, and the number of explicit `any` values only falls. The served dashboard does not change: its routes, its placeholders, its extension points and its anchors behave as they did before the migration.

## ADDED Requirements

### Requirement: Strict type checking of dashboard source

The dashboard SHALL type-check with `vue-tsc` under `strict: true` for every `.ts` file and every `.vue` file with `lang="ts"`, and the frontend CI job SHALL run `npm run typecheck`. A `.js` file under `src/` SHALL NOT be type-checked until the transition ends, and at the end of the transition no `.js` file under `src/` SHALL remain outside the files listed as vendored or generated.

#### Scenario: A new typed component fails on a type error

- **WHEN** a `.vue` file with `lang="ts"` passes a string to a prop declared as a number
- **THEN** `npm run typecheck` fails and the frontend CI job is red

#### Scenario: JavaScript is not checked during the transition

- **WHEN** a `.js` module under `src/` contains a type error and the transition is not finished
- **THEN** `npm run typecheck` still passes, because `checkJs` is off

#### Scenario: The transition ends

- **WHEN** the final migration pull request is merged
- **THEN** no `.js` file remains under `src/`, `server/`, `e2e/` or `fake-api/` except those listed as vendored or generated, and `allowJs` is off

### Requirement: API shapes come from the OpenAPI description

The dashboard SHALL declare its API response and request shapes in `src/types/api.ts`, generated from `docs/openapi/ingot-openapi.json` by the committed generator and committed without manual edits. A unit test SHALL fail when the committed file differs from a fresh generation from the committed description.

#### Scenario: The description changes without the generated file

- **WHEN** a change to `docs/openapi/ingot-openapi.json` is not followed by a regeneration of `src/types/api.ts`
- **THEN** the freshness test fails with a message that names the regeneration command

#### Scenario: The generated file is used by stores

- **WHEN** a store module reads a response shape
- **THEN** it imports the named type from `src/types/api.ts`, and does not declare a copy of the shape

### Requirement: Explicit any values are budgeted per file

Every explicit `any` in `src/` SHALL carry a `// any: <reason>` comment on the same line, and the count of explicit `any` values per file SHALL equal the entry for that file in `src/types/any-budget.json`. The unit suite SHALL fail when a file has more `any` values than its entry, when an entry is higher than the count, or when a file with `any` has no entry. The budget SHALL only shrink, and it SHALL be deleted when the count reaches zero.

#### Scenario: A new any without a reason

- **WHEN** a pull request adds an `any` to a file without a reason comment
- **THEN** the lint rule and the budget test fail

#### Scenario: A removed any lowers the budget

- **WHEN** a pull request removes the last `any` of a file that has a budget entry
- **THEN** the budget test fails until the entry is removed in the same pull request

### Requirement: Dashboard behaviour and contracts are unchanged by the migration

A migration pull request SHALL NOT change the rendered behaviour of the dashboard, and the built stylesheet SHALL stay byte-identical to the baseline of its phase, apart from the content hash in its file name.

#### Scenario: The stylesheet differs after a conversion

- **WHEN** a slice changes the built CSS apart from the hash in its file name
- **THEN** the slice is not ready to merge

#### Scenario: The contract names keep their values

- **WHEN** any migration pull request is built
- **THEN** the routes, the `{{REPOSILITE.*}}` placeholders, the `data-ingot-*` attributes, the plugin extension points, the anchor registry and `src/router/dashboard-segments.json` are unchanged, and the segment file is byte-identical to `charts/ingot/files/dashboard-segments.json`

#### Scenario: The smoke set passes

- **WHEN** a pull request runs the end-to-end smoke set of its slice
- **THEN** every spec passes without a changed snapshot

#### Scenario: A chunk grows

- **WHEN** a built JavaScript chunk grows by more than 2 percent
- **THEN** the pull request states the growth and its reason in its description
