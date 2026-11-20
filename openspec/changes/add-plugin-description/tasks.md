# Tasks

Each group is one code pull request of one Conventional Commits type, `feat(plugin)`. Tests of a group are written and seen to
fail before the code they test, and they land in the same commit as that code. Every test follows F.I.R.S.T.: no sleeps or waits
by time, no system clock (a `Clock` is injected where time matters), no fixed ports, no shared mutable state, a temporary
directory per test, and assertions with messages. The start-log tests use a captured appender, not console output. No code pull
request contains a file under `openspec/`. Every group that changes what an operator reads (the system information page, the
support bundle or the plugin API reference) also updates the matching page under `docs/gitbook/`.

## 1. Proposal

- [x] 1.1 `docs(openspec): propose add-plugin-description`: this change and nothing else, branched from the remote main branch.
  Verify: `openspec validate add-plugin-description --strict` passes and the dash grep from the proposal prints nothing.

## 2. Annotation and registry

PR title: `feat(plugin): add the optional description to the plugin annotation`

- [x] 2.1 `feat(plugin)`: write failing tests for the normalisation table (whitespace, control characters, the 280 code point cut,
  the empty result), the single WARN line for a cut with a captured appender, a class compiled without the element reading the
  empty text, and the registry lookup for a known and an unknown name. Verify: they fail on the missing classes.
- [x] 2.2 `feat(plugin)`: add the `description` element to `@Plugin` in `com.reposilite.plugin.api`, the pure normaliser in
  `net.onelitefeather.ingot.plugin.description`, and the lookup by name next to the tier lookup in `Extensions`. Document the
  element in the `@Plugin` table of `docs/gitbook/reference/plugin-api.md`. Verify: the tests of 2.1 pass, and the existing plugin
  tests pass unedited.

## 3. System information and support bundle

PR title: `feat(plugin): show the plugin description in the system information`

- [x] 3.1 `feat(plugin)`: write failing tests for `PluginInformation` with and without a description, asserting the serialised JSON
  of `system-info.json` and `plugins.json` (the second plugin is byte-identical to the output before the change), and for the
  redaction of a description that matches a rule (masked, bundle served). Verify: they fail before 3.2.
- [x] 3.2 `feat(plugin)`: add `description` to `PluginInformation` with the field omitted when empty, pass it through the redaction
  check, and update the plugin row of the `docs/gitbook/references/http-api` schema and `docs/gitbook/manage/operate/collect-a-support-bundle.md`.
  Verify: the tests of 3.1 pass, and the OpenAPI document lists the field as optional.

## 4. System information page

PR title: `feat(plugin): show the plugin description on the system information page`

- [ ] 4.1 `feat(plugin)`: write failing component tests for the plugin row with a description (text content, no markup) and without
  one (no extra element). Verify: they fail before 4.2.
- [ ] 4.2 `feat(plugin)`: render the description under the plugin name in `SystemInfoView`, with the existing text style and no new
  control. Run `npm ci`, the touched tests, `npx vitest run src/anchors src/style-gate/gate.test.ts`, the typecheck, eslint on the
  changed files and `npm run anchors:doc`. Verify: the checks pass, the style gate baseline does not grow, and no Tailwind arbitrary
  value is added.

## 5. Store catalogue entry

PR title: `feat(plugin): read the description of a store catalogue entry`

- [x] 5.1 `feat(plugin)`: write failing tests for an entry with and without `description` in `ingot-plugin-catalogue.json` (schema 1
  accepted in both cases). Verify: they fail before 5.2.
- [x] 5.2 `feat(plugin)`: add the optional `description` to `PluginCatalogueEntry`, read through the same normaliser as 2.2. Document
  the field in the catalogue page of `docs/gitbook`. Verify: the tests of 5.1 pass, and the existing catalogue tests pass unedited.

## 6. Pull requests

- [ ] 6.1 Open the code pull requests of groups 2 to 5, in that order, each under the title in its heading, from the remote main
  branch. Each one is opened only after the one before it is merged, so each stays buildable on its own. Verify: each pull request
  exists with its title, and no pull request contains a file under `openspec/`.
