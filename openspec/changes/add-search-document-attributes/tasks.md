# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `search`. Tests are written first and
seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps, no system clock (the clock is injected), no
shared mutable state, a temporary directory per test, assertions with messages. Gradle runs go through `heavy` with
`--max-workers=2`, targeted.

Prerequisite: `add-artifact-enrichment-hook` merged. Parallel to it: `add-plugin-api-artifact`, which adds the new keys' types to the
allowlist if they are plugin-facing.

## 1. Proposal

- [x] 1.1 `docs(openspec)`: propose `add-search-document-attributes`. This change and nothing else. Verify:
  `openspec validate add-search-document-attributes --strict --no-interactive` passes and the dash grep from the proposal prints nothing.

## 2. Validation of attribute sets (red, then green)

- [x] 2.1 `test(search)`: failing tests of the validation of D1: an allowed set passes; more than 32 keys, more than 16 values, a value
  over 2000 characters, a set over 8 KiB, a control character, a bidirectional override, a key of another namespace and a key outside
  the name pattern are each refused with its fixed rule name, and the message never contains a value. Verify: they fail.
- [x] 2.2 `feat(search)`: the validation as a pure function in the search package, and the `Attributes` variant of `EnrichmentResult` with
  the namespace check of D2. Verify: the tests of 2.1 pass.

## 3. Capability and write rules (red, then green)

- [x] 3.1 `test(search)`: failing tests with a fake backend: a backend without `ATTRIBUTES` drops the set and counts
  `Skipped("unsupported-backend")`; the write rules of D4 hold for a changed file, a failed enricher, a touch, a reorigin and each delete
  path. Verify: they fail.
- [x] 3.2 `feat(search)`: `SearchCapability` and the default `capabilities` member on `ArtifactSearchBackend`, the boundary drop and the
  write rules in `OutboxWorker` and `RebuildService`. Verify: the tests of 3.1 pass and the existing outbox and rebuild tests pass with
  unchanged assertions.

## 4. Rebuild by enricher version (red, then green)

- [x] 4.1 `test(search)`: failing tests of D5: an unchanged row with the stored version of every matching enricher is not enriched; an
  unchanged row with no stored version, or another one, is enriched with trigger `REBUILD`; a held row is not enriched. Verify: they
  fail.
- [x] 4.2 `feat(search)`: extend `existing` results with the stored `core.enricher.*` keys and the condition in `rowOf`. Verify: the
  tests of 4.1 pass.

## 5. Database backend (red, then green)

- [x] 5.1 `test(search)`: failing contract tests in `src/testFixtures` for the database backend: attributes survive a touch, a reorigin
  and an upsert of the same row only when D4 says so; a delete path removes the child rows in the same transaction. Verify: they fail.
- [x] 5.2 `feat(search)`: the table `search_attribute` in `SearchTables.kt`, created by the existing schema setup, and the writes and
  deletes of D6 in one transaction. Verify: the tests of 5.1 pass and the database contract tests of the earlier changes pass unedited.

## 6. Elasticsearch and OpenSearch (pull requests of their own, one per module)

- [x] 6.1 `test(search)` then `feat(search)` in `reposilite-plugins/elasticsearch-plugin`: the `attributes.*` dynamic template, the
  document field and the declared capability, with the contract tests of group 3 to 5 run against the module. Verify:
  `heavy ./gradlew :elasticsearch-plugin:test --max-workers=2` passes; the integration test uses a Testcontainers instance started
  through `heavy` and removed by its label.
- [x] 6.2 The same for `reposilite-plugins/opensearch-plugin`.

## 7. Meilisearch (pull request of its own)

- [x] 7.1 `feat(search)` in `reposilite-plugins/meilisearch-plugin`: the `attributes` document object and the declared capability without
  filterable settings (those come with `add-search-attribute-facets`). Verify: the contract tests pass against the module.

## 8. Documentation (pull request of its own)

- [x] 8.1 `docs(search)`: a page under `docs/gitbook/references/plugins/` describing the namespaces, the limits, the write rules, the
  rebuild rule by version, the capability table per backend and the `attributes=true` parameter. Verify: the page states that Lucene
  declares nothing until its pull request lands.

## 9. Pull requests

- [x] 9.1 Open the code pull requests in the order of groups 2 to 8, each from a fresh `origin/main` that contains the merged proposal and
  the merged `add-artifact-enrichment-hook`, each small and with no file under `openspec/`. The first one carries the title
  `feat(search): store plugin attributes with indexed artifacts`. The Elasticsearch and OpenSearch pull requests are
  titled `feat(search): store plugin attributes in elasticsearch and opensearch`, the Meilisearch pull request is titled
  `feat(search): store plugin attributes in meilisearch`, and the documentation pull request is titled `docs(search): describe plugin attributes and the response parameter`. Each pull
  request body fills `.github/PULL_REQUEST_TEMPLATE.md` and contains no session link.
