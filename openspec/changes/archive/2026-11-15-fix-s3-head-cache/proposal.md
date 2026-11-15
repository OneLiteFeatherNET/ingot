# Proposal

## Why

With `metadataCacheSettings` enabled, Ingot keeps the answers of S3 HEAD requests in memory, so that a listing or a download
does not ask S3 for the size and modification time of every file. The cache is one per server process, and it only learns
about writes that the same process made.

In a cluster that is not enough. Instance A deploys a version and rewrites `maven-metadata.xml`. Instance B still holds the
old answer for that file, and keeps it for as long as the configured expiry allows, one day by default. B then reports the
size and date of the previous file to every client that asks it. The problem is worst for `-SNAPSHOT` directories, whose
metadata and jars are overwritten on every build, and where a steady stream of reads keeps an entry alive under the
access-based expiry.

The S3 client has a second, smaller problem. It retries a failed request with a fixed exponential schedule (1 second, then
2, 4 and so on, up to 60 seconds, five retries). Instances that lose the same S3 endpoint at the same moment retry in step
and hit it again together. The AWS SDK ships a full-jitter schedule for exactly this, and Ingot uses its own copy of the
exponential one.

## What Changes

- **Q5 Cluster-coherent HEAD cache.** The cache SHALL NOT store HEAD answers for mutable keys. A key is mutable when its file
  name starts with `maven-metadata` (the XML, its checksums and `maven-metadata-*.xml`), or when a directory in it ends with
  `-SNAPSHOT`. Every other key is cached as it is today. When a cluster bus is configured, a cached answer SHALL NOT be used
  more than 300 seconds after it was stored, whatever the creation and access expiry say, and reads do not renew that bound.
  Without a bus the configured expiry applies unchanged. The bound is the same five minutes as the cluster default of the
  resolution cache. No new setting is added, and the optional `STORAGE_CHANGED` event is left out.
- **N12 Retry jitter.** The S3 client uses the SDK's `FullJitterBackoffStrategy` with the same base delay of 1 second, the same
  cap of 60 seconds and the same five retries. The custom `ExponentialBackoffStrategy` is removed.

### Out of scope

- A cluster event that drops cached answers when a write happens on another instance. The five-minute bound limits staleness
  without it, and the event can follow later.
- The default of `metadataCacheSettings`, which stays off. Caching of keys that are not mutable does not change.
- The filesystem storage provider, and the Maven resolution cache, which the cluster bus already bounds (`add-cluster-bus`).
- The retry count, base delay and cap, the retry behaviour of other clients, and any change to S3 request semantics.

## Capabilities

### New Capabilities

- `storage-metadata-cache`: which S3 object metadata may be cached, and for how long in a cluster.
- `s3-retry-backoff`: the delay between two S3 retries of a failed request.

## Impact

- **Type and scope.** Both changes ship under `fix(storage)`. They have one type, so they stay one change. The pull request
  title, squash-merged onto main, is:

  `fix(storage): skip caching mutable S3 metadata and add retry jitter`

  The proposal goes in its own pull request, titled `docs(openspec): propose fix-s3-head-cache`, and is merged before the code
  pull request is opened.
- **Breaking marker.** No breaking change. No configuration key, environment variable, volume, manifest or plugin has to
  change. The metadata cache is off by default, so an installation that does not enable it sees no difference. A retry waits
  a random time up to the same ceiling as before. The title carries no `!` and no commit carries a `BREAKING CHANGE:` footer.
- **Behaviour that changes.** An operator who has the cache on and runs a cluster sees entries expire after at most five
  minutes, and sees mutable metadata read from S3 on every request. Retry delays become random within the same ceiling.
- **Code.** `reposilite-backend`: the `storage/s3` package, the cluster hook that already binds the resolution cache, and the
  matching tests. `docs/gitbook/` for the metadata cache and the retry row.

## Verification

- Targeted tests per commit, run through `heavy` with `--max-workers=2`. No full suite.
- `openspec validate fix-s3-head-cache --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/fix-s3-head-cache` prints nothing.
