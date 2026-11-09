# Design

## Decisions

### The event names the artifact directory, not the jar

Eviction on the accepting replica already works on the artifact root (`javadocs/<repository>/<group>/<artifact>`), because
one artifact has several version directories and the whole tree goes. The event carries that same location, `repository`
and `path`. A receiver then needs no knowledge of jar names or suffixes, and both sides share one eviction function on
`JavadocFacade`.

### The receiver validates the path twice

The payload comes from the bus, so it is not trusted. The receiver parses `path` with `Location.ofRequest`, which refuses
`..`, absolute paths and control characters. It then resolves the directory and calls `deleteRecursivelyInside` against the
repository's javadoc root, which refuses any target that is not strictly inside it. A refused event deletes nothing and is
logged through the failure facade. Tests cover `../` and absolute paths, an unknown repository, and an absent cache.

### No repair by polling

The bus is described as a speed-up in the operator guide, and every other kind of cached state on it is bounded by a poll
or a lifetime. Javadoc caches are not, because a time bound would unpack every large jar again at intervals. The cost of
this choice is that a replica which misses the event serves its old copy until the copy is removed. The operator guide
states it. If it shows up in practice, the follow-up is a content check of the cached index against the jar's checksum on
the next request.

### Publication happens after the write

The deploy event is emitted after the artifact is stored, and the bus publishes from the same event. A receiver therefore
never removes a cache before the new jar is readable from shared storage. The window in which a replica serves the old
copy is the bus latency, which is accepted under the best-effort contract.
