# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `maven`. Tests are written first
and seen to fail before the code they cover exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock
(any time source is injected as a `java.time.Clock`), no fixed ports, no shared mutable static state, a `@TempDir` per
filesystem test, a fresh server per integration test, and assertions with messages. Gradle runs go through `heavy` with
`--max-workers=2`, targeted and never the full suite. Integration tests run as `:reposilite-backend:integration --tests '<pattern>'`.
No configuration key, environment variable or deployment manifest changes.

Depends on: `gate-plugin-abi` (merged, so the binary report runs on this pull request). Required by `add-repository-format-spi`.

## 1. Proposal

- [ ] 1.1 `docs(openspec)`: propose extract-repository-registry: this change and nothing else. Verify:
  `openspec validate extract-repository-registry --strict` passes and the dash grep from the proposal prints nothing.

## 2. Registry (`test(maven)` then `refactor(maven)`)

- [ ] 2.1 `test(maven)`: write failing tests against the Maven specification fixture: the registry lists the default
  repositories in order with the same instances as `MavenFacade`; a repository added through the settings facade is found
  without restart; a removed one is gone; `storesProxiedFiles` is true only with a storing mirror. Verify: they fail on the
  missing interfaces.
- [ ] 2.2 `refactor(maven)`: add `RepositoryRegistry` and `RegisteredRepository` in `net.onelitefeather.ingot.repository`,
  make `Repository` implement the latter (`storesProxiedFiles` derived from its mirrors), add the internal
  `MavenRepositoryRegistry` and register it from `MavenPlugin.initialize`. Verify: the tests of 2.1 pass.

## 3. Index sink (`test(maven)` then `refactor(maven)`)

- [ ] 3.1 `test(maven)`: write the failing architecture test (ArchUnit, test scope) with rule 1 of design D4, and a test that
  the lazy sink delegates to any facade implementing `ArtifactIndexSinkProvider` and is a no-op without one. Verify: rule 1
  fails on main with the eight Maven imports listed.
- [ ] 3.2 `refactor(maven)`: move `ArtifactIndexSink`, `NoOpArtifactIndexSink` and the lazy sink to
  `net.onelitefeather.ingot.repository.index`, add `ArtifactIndexSinkProvider`, make `SearchFacade` implement it, and update
  imports in main and test sources in the same commit. Verify: the tests of 3.1, `IndexEventListenersTest`,
  `WritePathEventCharacterisationTest` and the search sink tests pass.

## 4. Consumers (`test(maven)` then `refactor(maven)`)

- [ ] 4.1 `test(maven)`: add rules 2 and 3 of design D4 to the architecture test, and integration tests that start the full
  plugin set and exercise each switched consumer once: token visibility for a public and a private repository, the shared
  dashboard path with a repository named `settings`, an anonymous search not returning a private artifact, a cleanup dry run
  listing a repository, and a remote `resolution.invalidated` message clearing a cache entry. Verify: the architecture rules
  fail on main; the behaviour tests pass on main (they pin today's behaviour).
- [ ] 4.2 `refactor(maven)`: switch `AccessTokenPlugin`, `ResourcesFrontendHandler`, `SearchApiEndpoints`,
  `MavenIndexStorage`, `MavenRepositoryEntries`, `SearchPlugin`, the cleanup target enumeration and the redirect storage
  lookup to `RepositoryRegistry`; add `RepositoryCacheCoordinator`, implement it in the internal Maven registry class and
  switch `ClusterPlugin` and `FacadeCacheHooks` to it. Leave every `@Plugin` dependency list unchanged. Verify: the tests of
  4.1 pass, and the existing token, frontend, search, cleanup, redirect and cluster tests pass unedited.

## 5. Documentation (`docs(maven)`)

- [ ] 5.1 `docs(maven)`: update `docs/gitbook/explanation/backend-architecture.md` (registry, coordinator, sink package,
  one-way dependency from search to Maven) and add the registry to the facade table of
  `docs/gitbook/reference/plugin-api.md`, marked as not yet stable for plugins. Update the sink package in
  `docs/superpowers/specs/2026-10-16-artifact-search.md` where it names the type.

## 6. Pull request

- [ ] 6.1 Open the code pull request with the title `refactor(maven): read repositories through a read-only registry`. Read
  `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections (type `refactor`). The description tells the reviewer's story:
  every part of the server had to ask the Maven plugin which repositories exist, which blocks any second format, and now
  they ask a neutral registry with nothing changing for users. Paste the "Plugin API" job summary showing no new
  difference. No session link and no attribution line. Verify: `gh pr view --json title` shows the exact title, the
  commit-style hook accepts the title and every commit, and the body has no em or en dash.
