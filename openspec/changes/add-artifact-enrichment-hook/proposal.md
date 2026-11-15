# Proposal

## Why

A plugin that wants to describe the artifacts it finds in a repository has no place to do it. The only events that touch files are
`DeployEvent` (after a deployment, with repository, coordinates and uploader, and no content: `com/reposilite/maven/api/DeploymentApi.kt:35`)
and the read events of `LookupApi.kt`. Neither fires when a file is indexed by a rebuild, and neither fires when a proxied file is
stored from an upstream mirror (`maven/MirrorService.kt:292-301` stores the file and calls the index sink directly). So an artifact
that arrives through a proxy, or that existed before the plugin was installed, can never be seen by a plugin.

The portal plugins planned for Gradle and Minecraft plugins need exactly this moment: after a file is indexed, with a bounded way to
read its bytes, on every path a file can take into the index. The write side already has one seam for this: `ArtifactIndexSink` (`repository/index/ArtifactIndexSink.kt:23`) is the interface the
write paths call, and it promises never to throw. This proposal adds a public, experimental hook behind that seam and keeps the
promise.

## What Changes

- A public, experimental interface `ArtifactEnricher` in `net.onelitefeather.ingot.search.enrichment`, marked
  `@ExperimentalIngotSearchApi`. A plugin registers an enricher through the plugin event bus, the same way it registers a search
  backend (`ArtifactSearchBackendRegistrationEvent`, `search/backend/SearchBackendApi.kt:96`).
- The core calls every registered enricher whose declared extensions match a file, on the three paths a file enters the index:
  a deployment or any other change (through the outbox), a proxied file stored from an upstream (through the same outbox), and a
  rebuild of existing artifacts (through `RebuildService`).
- The enricher receives the stored coordinates, the provenance (hosted or mirror), the checksums known so far, and a read-only
  content handle with ranged reads. The core enforces a byte budget and a deadline per call. Nothing is read when no enricher
  matches.
- A failure, a timeout, an exhausted budget or a full executor never fails a deployment, a proxied download or the indexing of the
  file. The row is written without the enricher's output and the failure is counted.
- Status counters per enricher in the existing index status response, as an additive block.
- In this change an enricher can return only "completed", "skipped" or "failed". It cannot yet write attributes. Writing attributes is
  `add-search-document-attributes`, which extends the result type additively.

### Out of scope

- Writing attributes, filtering, faceting or sorting on them (`add-search-document-attributes`, `add-search-attribute-facets`).
- Enrichment on the request thread of a deployment or a download. It is never on the request path.
- Enrichment of directories, of metadata files other than the declared extensions, or of files removed from storage.
- Any change to the search backends (Elasticsearch, OpenSearch, Meilisearch, Lucene, database). The hook sits before the backend.
- A configuration key. The budget and the deadline are constants in this change (open question Q2 in the design).
- Exposing storage to plugins outside the content handle. Plugins do not get the storage facade.

## Capabilities

### New Capabilities

- `artifact-enrichment`: the enricher interface, its registration, the three invocation points, the content handle and its budget,
  the failure rules, and the status counters.

## Impact

- **Type and scope.** The change ships under `feat(search)`. Its commits are `test(search)`, `feat(search)` and `docs(search)`, one
  type each. The pull request titles are:

  `feat(search): let plugins enrich artifacts when they are indexed` (the hook and the invocation points)

  `docs(search): describe the artifact enrichment hook` (the plugin API reference)

- **Breaking marker.** No breaking change. With no enricher registered, nothing reads storage, nothing changes in a row and the status
  response gains only an empty block. No configuration key, environment variable, volume, manifest, image or plugin has to change.
- **Binary surface.** New public types under `net.onelitefeather.ingot.search.enrichment`, marked experimental. No type of
  `com.reposilite.*` gains a member. The new package is added to the plugin API allowlist of `add-plugin-api-artifact` in the code
  pull request that introduces it.
- **Order.** Depends on `add-plugin-api-artifact`: the new package must be on the allowlist and the consumer fixture must compile
  against it, or the portal plugins cannot use the hook. `add-search-document-attributes` depends on this change, because it extends
  the result type and the invocation points defined here. `add-search-attribute-facets` depends on that change. This change does not
  depend on `expose-plugin-access-checks`.

## Verification

- Unit tests of the registry, the invocation rules, the budget, the deadline and the executor with a fake clock, a fake executor and a
  fake storage that counts reads. No test sleeps or reads the system clock.
- Integration tests with the database backend: a deployment, a proxied file and a rebuild each reach the enricher once with the right
  provenance, and a failing enricher leaves the row searchable.
- `openspec validate add-artifact-enrichment-hook --strict --no-interactive` passes and
  `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-artifact-enrichment-hook` prints nothing.
