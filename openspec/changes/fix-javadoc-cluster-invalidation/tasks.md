# Tasks

Each group is one or more commits of one Conventional Commits type each. Tests are written first and seen to fail before
the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock, a temporary
directory per test for the cache, a fake `ClusterCacheHooks` and the in-memory cluster hub instead of a real broker, no
shared mutable state, and assertions with messages. Gradle runs go through `HEAVY_SLOTS=1 heavy`, with
`--no-daemon --max-workers=2`, targeted with `--tests`, and never the full suite.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose fix-javadoc-cluster-invalidation`: this change and nothing else, branched from the
  remote main branch. Verify: `openspec validate fix-javadoc-cluster-invalidation --strict` passes and the dash grep from
  the proposal prints nothing.

## 2. Failing tests

- [ ] 2.1 `test(javadocs)`: write failing tests in `reposilite-backend/src/test/kotlin/com/reposilite/javadocs/JavadocEvictionTest.kt`:
  evicting an artifact removes its `.cache` tree and keeps a sibling artifact; a path that leaves the repository root
  (`../other`, `/etc`) deletes nothing and is refused; an unknown repository deletes nothing; evicting an absent cache is a
  no-op. Write failing tests in `reposilite-backend/src/test/kotlin/net/onelitefeather/ingot/cluster/ClusterJavadocEventsTest.kt`:
  a local javadoc deploy publishes `javadoc.invalidated` with `repository` and `path`; a jar without a javadoc suffix publishes
  nothing; a received event calls the hook once and publishes nothing; an event carrying this instance's own identifier is
  ignored. Verify: they fail, first on the missing symbols, then on the behaviour.

## 3. Eviction and bus wiring

- [ ] 3.1 `fix(javadocs)`: move the deploy eviction of `JavadocPlugin` into an internal `JavadocFacade.evictArtifact(repository,
  artifactRoot)` that checks the root with `deleteRecursivelyInside`. Parse a received path with `Location.ofRequest`.
  Emit a new experimental core event `JavadocInvalidatedEvent(repository, path)` in
  `reposilite-backend/src/main/kotlin/com/reposilite/plugin/api/ClusterEvents.kt` after the local eviction. Verify: the
  tests of 2.1 for the eviction pass.
- [ ] 3.2 `fix(javadocs)`: add `JAVADOC_INVALIDATED("javadoc.invalidated", setOf("repository", "path"))` to
  `ClusterEventType`. Publish it in `ClusterCacheEvents.install` on `JavadocInvalidatedEvent`. Add
  `javadocInvalidated(repository, path)` to `ClusterCacheHooks`, implemented in `FacadeCacheHooks` through the facade lookup,
  and apply the received event through it. Fakes of `ClusterCacheHooks` gain the new method and nothing else changes.
  Verify: the tests of 2.1 for the bus pass, and the existing cluster tests pass.

## 4. Operator guide

- [ ] 4.1 `docs(cluster)`: in `docs/gitbook/how-to/configure-cluster-bus.md`, state that javadoc caches are the one kind of state
  without a poll or a time bound. A replica that misses `javadoc.invalidated` keeps its unpacked copy until the copy is removed,
  by a restart on an empty working directory or by the next deploy that replica receives. Verify: the page's links still resolve
  and no other page changes.

## 5. Pull request

- [ ] 5.1 Open the code pull request from a branch of the remote main branch that already contains the merged proposal, with the
  title `fix(javadocs): evict javadoc caches on every replica`. Fill in the sections of the repository's pull request template and
  do not add session attribution. This task is the last one.
