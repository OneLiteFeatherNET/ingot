# Proposal

## Why

Ingot unpacks a javadoc jar once per artifact and serves the unpacked files from a local directory under the working
directory, `javadocs/<repository>/<group>/<artifact>/.cache`. When a javadoc jar is deployed, the instance that accepted
the upload deletes that directory (`JavadocPlugin.kt:69-85`). The other replicas never hear about the deploy. Each replica
has its own working directory, so a replica that had already unpacked the artifact keeps serving the javadoc of the old
build until its own copy is removed.

The problem shows where a version can receive a new upload: snapshots, and repositories with `redeployment` enabled. A
release in a repository without redeployment cannot change, so its cached copy stays correct. On the affected artifacts,
a reader gets the old Javadoc from one replica and the new one from another, and the viewer does not show which build it
is displaying.

The cache is local files, not database rows, so the database cannot correct it. The cluster bus already carries the other
local invalidations, such as resolution pins and shared configuration changes, and it is the channel for this one too.

## What Changes

- A deploy of a javadoc jar publishes a `javadoc.invalidated` event on the cluster bus. The event carries the repository
  and the artifact path, which names the directory whose cache is removed. Without a configured bus nothing is published.
- Every other replica removes the same cache directory when it receives the event. The removal stays inside the javadoc
  root of that repository. A path that is not a valid repository location, or that would leave the root, is refused and
  logged, and nothing is deleted.
- The local eviction on the accepting replica stays as it is. Applying a received event does not publish a new event.
- The new event type is `javadoc.invalidated` (enum `JAVADOC_INVALIDATED`) with the payload fields `repository` and
  `path`. A replica that does not know the type ignores the message, as the envelope rules of the bus already require.
- Javadoc caches have no poll and no time bound, unlike the other cached state of the bus. The operator guide
  `docs/gitbook/how-to/configure-cluster-bus.md` says so: a replica that misses the event keeps its copy until the copy is
  removed.
- No setting, environment variable, volume or manifest changes. No reference page under `docs/gitbook/reference/` changes.

## Out of scope

- `INGOT_JAVADOCS_CACHE_DIR` and the size-limited `emptyDir` in the chart (roadmap N16). It is a new setting and therefore
  a `feat(javadocs)` change of its own, proposed later as `add-javadoc-cache-dir`.
- A time bound on javadoc cache entries, or a periodic check of the cached files against the jar. Either would repair a
  missed event by itself, but both cost a re-unpack of large jars. Revisit if missed events show up in practice.
- The race between an eviction and a request that unpacks the same artifact at the same moment. It exists on one replica
  today and this change does not alter it.
- Changes to the chart, the dashboard or the Javadoc page rendering.

## Type and pull request

- Conventional Commits type and scope: `fix(javadocs)`. The change has one type. Test commits use `test(javadocs)` and the
  operator guide uses `docs(cluster)`, as in the other changes of this series.
- Pull request title, squash-merged onto main:

  `fix(javadocs): evict javadoc caches on every replica`

## Capabilities

### New Capabilities

- `javadoc-cache`: which replicas drop an unpacked javadoc copy after a deploy, how a received eviction is confined to the
  repository, and what happens when the bus is missing or an event is lost.

### Modified Capabilities

- None. `openspec/specs/` holds no capability yet. This change adds one event type to the envelope version 1 defined by the
  unarchived change `add-cluster-bus`, and that is described in the new capability instead of a delta to its spec.

## Impact

- **Breaking.** None. No configuration key, environment variable, volume, manifest or plugin changes. A Reposilite
  installation pointed at Ingot behaves as before. A single instance without a bus behaves as before. A cluster with
  mixed versions is safe: a replica without this change ignores the new event type.
- **Behaviour that changes.** On a cluster with a bus, a replica that holds an old unpacked copy of a re-deployed artifact
  drops it when the event arrives, instead of serving it until restart.
- **Dependencies.** `add-cluster-bus` (merged to main, not yet archived). This change adds one value to
  `ClusterEventType` and one method to `ClusterCacheHooks`, so it must be applied on top of that change. No dependency on
  the wave 1 proposals.
- **Code.** `reposilite-backend`: javadocs, the plugin API events, and the cluster package. `docs/gitbook/how-to/`.
