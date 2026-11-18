# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `search`. Tests are written first and
seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time (the executor and the
clock are injected and faked), no system clock, no shared mutable state, a temporary directory per test, and assertions with
messages. Gradle runs go through `heavy` with `--max-workers=2`, targeted.

Prerequisite: `add-plugin-api-artifact` (group 2 of this change adds the package to its allowlist).

## 1. Proposal

- [ ] 1.1 `docs(openspec)`: propose `add-artifact-enrichment-hook`. This change and nothing else. Verify:
  `openspec validate add-artifact-enrichment-hook --strict --no-interactive` passes and the dash grep from the proposal prints nothing.

## 2. Contract and registry (red, then green)

- [ ] 2.1 `test(search)`: failing tests of the registry: a matching `apiVersion` registers; another version is refused with a warning
  naming both numbers; a duplicate `id` is refused naming both classes; an `id` outside the pattern is refused; a refused enricher is
  never called. Verify: they fail on the missing types.
- [ ] 2.2 `feat(search)`: add `net.onelitefeather.ingot.search.enrichment` with `ArtifactEnricher`, `ArtifactEnrichmentRequest`,
  `EnrichmentTrigger`, `ArtifactContent`, the sealed `EnrichmentResult` with the top-level `EnrichmentCompleted`, `EnrichmentSkipped` and `EnrichmentFailed` (decision 69), the registration event and the
  registry, marked `@ExperimentalIngotSearchApi`, and add the package and the four types (the sealed result and its three top-level outcomes) to the plugin API allowlist of `add-plugin-api-artifact`.
  Verify: the tests of 2.1 pass and the consumer fixture compiles a sample enricher.

## 3. Content handle and executor (red, then green)

- [ ] 3.1 `test(search)`: failing tests with a fake storage that counts reads: a range beyond the byte budget throws; a range inside it
  is served; a request outside the path of the call is impossible by construction. Verify: they fail.
- [ ] 3.2 `feat(search)`: the content handle over `StorageProvider.getFile(location, offset, length)` with the budget of D4, and the
  bounded stream of `BoundedInputStream`. Verify: the tests of 3.1 pass.
- [ ] 3.3 `test(search)`: failing tests of the executor with a fake executor: a normal return sets the result; an exception becomes
  `EnrichmentFailed("exception: <simple name>")` and never carries the message; a deadline expiry becomes `EnrichmentFailed("timeout")`
  with the task cancelled; a full queue becomes `EnrichmentSkipped("busy")` without a call; `Error` propagates. Verify: they fail.
- [ ] 3.4 `feat(search)`: the executor of D5 owned by the search plugin, with the queue bound and the deadline read from the injected
  clock for the timeout decision. Verify: the tests of 3.3 pass.

## 4. Changed paths (red, then green)

- [ ] 4.1 `test(search)`: failing tests of `OutboxWorker.changedPath`: no enricher matches, so the storage content is not read and the
  row is upserted as before; a matching extension reaches the enricher once with trigger `CHANGED` and the stored checksums; a mirror
  file reaches it with provenance `MIRROR`; an enricher that throws still yields the upsert and one warning, counted as failed.
  Verify: they fail.
- [ ] 4.2 `feat(search)`: call the enrichers matching the extension between the row and `Upsert` in `OutboxWorker.changedPath`, and
  count the results. Verify: the tests of 4.1 pass and the existing outbox tests pass with unchanged assertions.

## 5. Rebuild (red, then green)

- [ ] 5.1 `test(search)`: failing tests of `RebuildService.writeRows`: a new or changed row reaches the enricher with trigger `REBUILD`;
  an unchanged row (same size and modification time) does not; a held (pending) row does not; a failing enricher does not stop the
  page. Verify: they fail.
- [ ] 5.2 `feat(search)`: call the enrichers in `writeRows` for the writable rows that are not unchanged, before `backend.apply`.
  Verify: the tests of 5.1 pass and the rebuild tests pass with unchanged assertions.

## 6. Status (red, then green)

- [ ] 6.1 `test(search)`: failing tests that the index status response carries the `enrichers` block with the counters per id, and that
  the block is empty when no enricher is registered and every existing field is unchanged. Verify: they fail.
- [ ] 6.2 `feat(search)`: add the counters to the status reader and the additive block to the response. Verify: the tests of 6.1 pass.

## 7. Documentation (pull request of its own)

- [ ] 7.1 `docs(search)`: a page under `docs/gitbook/references/plugins/` that describes the hook, the three invocation points, the
  budget and the deadline, the failure rules and the rebuild gap of D3. Verify: the page links the consumer fixture and the
  experimental marker.

## 8. Pull requests

- [ ] 8.1 Open the code pull requests in the order of groups 2 to 7, each from a fresh `origin/main` that contains the merged proposal
  and the merged `add-plugin-api-artifact`, each small and with no file under `openspec/`. The first one carries the title
  `feat(search): let plugins enrich artifacts when they are indexed`, and the documentation pull request is titled
  `docs(search): describe the artifact enrichment hook`. Each pull request body fills `.github/PULL_REQUEST_TEMPLATE.md` and contains
  no session link.
