# Tasks

Each group is one or more commits of one Conventional Commits type each, with the scope `frontend`. Tests are written first and seen to
fail before the code they cover exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock, no shared mutable
state, a fresh fixture per test, and assertions with messages. Gradle runs go through `heavy` with `--max-workers=2`, targeted. Frontend
tests run with `npm test` in `reposilite-frontend`, and the browser checks with Playwright at `--workers=2`.

Depends on: `add-plugin-ui-extension-point` (merged). Soft: `add-plugin-reader-pages` (readers use the blocks), `add-search-attribute-facets`
(counts for the filter), `expose-plugin-access-checks` (the viewer filter of list sources), `extend-plugin-abi-gate` (shows the additions).

Each code pull request stays under about 400 changed lines, excluding tests and fixtures, and builds and passes on its own.

## 1. Proposal

- [x] 1.1 `docs(openspec)`: propose add-plugin-ui-browse-blocks. Verify: `openspec validate add-plugin-ui-browse-blocks --strict --no-interactive`
  passes and the dash grep from the proposal prints nothing.

## 2. Code pull request 1: code and link blocks (`feat(frontend)`, `test(frontend)`, `docs(frontend)`)

- [x] 2.1 `test(frontend)`: `LinkPathTest` (unit, backend) with the accepted and refused paths of design D9, and `PluginBlockLinkTest`
  (frontend unit) for `linkAllowed` with the same table. Both fail before 2.2.
- [x] 2.2 `feat(frontend)`: `CodeBlock` and `LinkBlock` as data classes, `linkProblem` in `PluginUiValidator`, and the registration
  checks for their fields. The old constructors stay as they are, and a reflection test in `PluginUiModelShapeTest` asserts their
  parameter lists.
- [x] 2.3 `test(frontend)` and `feat(frontend)`: `PluginBlock.vue` draws `code` (text, label, copy action with `copyToClipboard` and the
  selection fallback, one status region) and `link` (same tab, no `target`). Tests: markup is shown as text, a refused clipboard selects
  the text, a `javascript:` href draws no link.
- [x] 2.4 `docs(frontend)`: the two blocks in the block table of `docs/gitbook/references/plugins/plugin-api.md`, and the sentence that
  says a block carries no link is replaced by the link rules.

## 3. Code pull request 2: list block (`feat(frontend)`, `test(frontend)`, `docs(frontend)`)

- [x] 3.1 `test(backend)`: `ListBlockValidationTest` for a source outside the prefix and the page size limit. `ListSourceIntegrationTest`
  for a list whose source answers items and a cursor, using a fresh server per test. Fail before 3.2.
- [x] 3.2 `feat(frontend)`: `ListBlock` as a data class, its validation, and nothing else on the server.
- [x] 3.3 `test(frontend)` and `feat(frontend)`: the list in `PluginBlock.vue` with the 100-item cut, the skipped item without a title, the
  "Load more" control that sends `cursor`, the empty state, and the per-row link check of design D4 for response data.
- [x] 3.4 `docs(frontend)`: the list in the block table and in `extend-the-dashboard-from-a-plugin.md`, with one worked example.

## 4. Code pull request 3, the complete set (`feat(frontend)`, `test(frontend)`, `docs(frontend)`)

- [x] 4.1 `test(backend)`: `FilterBlockValidationTest`: a filter without a following list is refused, `cursor` as a key is refused,
  a repeated key is refused, more than six controls are refused. Fail before 4.2.
- [x] 4.2 `feat(frontend)`: `FilterBlock` and `FilterControl` as data classes with their validation.
- [x] 4.3 `test(frontend)` and `feat(frontend)`: the filter in `PluginBlock.vue`: a select sends its value as a query parameter, an empty
  value is not sent, a change resets the cursor, a count is shown only with a `facets` entry, and the text field is debounced by the
  dashboard's own helper (no timer in the test; the helper is replaced by a synchronous one).
- [ ] 4.4 `test(frontend)`: browser check (Playwright, `--workers=2`) at 320, 600 and 1440 px for a page with a filter, a list and a code
  block: no horizontal page scroll, the copy status region announces "Copied".
- [ ] 4.5 `docs(frontend)`: the filter in the block table of `plugin-api.md`, and the reserved key `cursor`.

## 5. Pull request

- [x] 5.1 Open code pull requests 1 and 2 under the titles `feat(frontend): accept code and link blocks in plugin pages` and
  `feat(frontend): accept list blocks in plugin pages`, each from a branch of the remote main branch, each with no file under `openspec/`.
- [ ] 5.2 Open code pull request 3, last, under the title `feat(frontend): add list, code, filter and link blocks to plugin pages`. Read
  `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections (type `feat`). The description tells the story of a reader who wants a
  plugin's snippet, copies it with one click and finds the download on the same host. It carries no session link and no attribution line.
  Verify: `gh pr view --json title` shows the exact title, the commit-style hook accepts the title and every commit, and the body has no em
  or en dash.
