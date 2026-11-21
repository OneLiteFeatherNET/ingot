# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `search`. Tests are written first and
seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps, no system clock (the clock is injected), no
shared mutable state, a temporary directory per test, assertions with messages. Gradle runs go through `heavy` with
`--max-workers=2`, targeted.

Prerequisite: `add-search-document-attributes` merged, and through it `add-artifact-enrichment-hook`.

## 1. Proposal

- [x] 1.1 `docs(openspec)`: propose `add-search-attribute-facets`. This change and nothing else. Verify:
  `openspec validate add-search-attribute-facets --strict --no-interactive` passes and the dash grep from the proposal prints nothing.

## 2. Declarations (red, then green)

- [x] 2.1 `test(search)`: failing tests of the registry: a declaration outside the enricher namespace is refused; a key declared by two
  enrichers is refused naming both classes; a declared key is in the catalogue with its three flags. Verify: they fail.
- [x] 2.2 `feat(search)`: `AttributeDeclaration`, `declaredAttributes` with the empty default, and the catalogue in the registry.
  Verify: the tests of 2.1 pass.

## 3. Query model and refusals (red, then green)

- [x] 3.1 `test(search)`: failing tests of the query validation: a filter, sort or facet on an undeclared key is refused with
  `undeclared-attribute`; a filter on a key declared without `filterable` is refused; more than 4 keys, 16 values or 8 facet keys is
  refused; a feature whose capability the fake backend lacks is refused with `unsupported-search-capability` before the backend is called.
  Verify: they fail.
- [x] 3.2 `feat(search)`: the model types `AttributeFilter`, `AttributeSort` and the facet keys on `ArtifactSearchQuery`, the parsing of
  `attribute`, `attributeSort` and `attributeFacets` in `api/SearchApiEndpoints.kt`, and the validation. Verify: the tests of 3.1 pass and
  existing search route tests pass with unchanged assertions.

## 4. Database backend (red, then green)

- [x] 4.1 `test(search)`: failing contract tests for the database backend: OR within a key and AND across keys; multi-select: a selected value
  keeps its alternatives in the facet; counts exclude a row of a private repository for an anonymous viewer; sort puts missing values
  last and breaks ties by the existing order; the sort of a 10 000-row fixture finishes within the measured budget recorded in the test
  (no wall-clock sleep, the budget is a count of queries). Verify: they fail.
- [x] 4.2 `feat(search)`: the `EXISTS` filters, the grouped facet counts and the scalar sort subquery of D4 in `DatabaseArtifactSearchEngine`.
  Verify: the tests of 4.1 pass and the existing facet tests pass unedited.

## 5. Elasticsearch and OpenSearch (red, then green, one pull request per module)

- [ ] 5.1 `test(search)` then `feat(search)` in `reposilite-plugins/elasticsearch-plugin`: the filter, the filtered terms aggregation with
  the access clause, the sort with `missing: _last`, and the declared capabilities, run through the contract tests of group 4. Verify:
  `heavy ./gradlew :elasticsearch-plugin:test --max-workers=2` passes; the Testcontainers instance is removed by its label.
- [ ] 5.2 The same for `reposilite-plugins/opensearch-plugin`.

## 6. Meilisearch (red, then green)

- [ ] 6.1 `test(search)`: failing tests with a fake Meilisearch client: a declaration change produces one settings update; the state is
  pending until the task completes; a filter while pending answers `503` with `Retry-After`; facet distribution excludes the facet's own
  key. Verify: they fail.
- [ ] 6.2 `feat(search)` in `reposilite-plugins/meilisearch-plugin`: derive the filterable and sortable settings from the declarations, the
  filter string, `facetDistribution` and the pending state. Verify: the tests of 6.1 pass.

## 7. Documentation (pull request of its own)

- [ ] 7.1 `docs(search)`: a page under `docs/gitbook/references/plugins/` with the parameters, the counting rule, the capability table per
  backend, the file-count note of Q5 and the `503` of a pending Meilisearch settings change. Verify: the page is linked from the search
  reference.

## 8. Pull requests

- [ ] 8.1 Open the code pull requests in the order of groups 2 to 7, each from a fresh `origin/main` that contains the merged proposal and
  the merged `add-search-document-attributes`, each small and with no file under `openspec/`. The first one carries the title
  `feat(search): filter and facet on plugin attributes`. The engine pull requests are titled
  `feat(search): filter and facet on plugin attributes in the search engines`, one per module with the modules named in its description,
  and the documentation pull request is titled `docs(search): describe attribute filters and facets`. Each pull request body fills
  `.github/PULL_REQUEST_TEMPLATE.md` and contains no session link.
