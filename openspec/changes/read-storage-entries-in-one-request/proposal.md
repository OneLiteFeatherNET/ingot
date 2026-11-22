# Proposal

## Why

A Maven download from an S3 repository costs two requests to the object store: a HEAD to learn the size and modification time,
then a GET for the content. On Ceph RGW with keep-alive connections and without `tcp_nodelay`, every response that carries a body
costs about 45 ms. The second request is where that cost lands, and it is paid once per artifact file, so a build that resolves
many small files (POMs, checksums, module metadata) spends most of its time waiting on the object store.

The storage provider API has no call that returns the content together with its size and modification time. Callers therefore
ask twice. A single GET already carries both values in its response headers, so one request is enough.

The provider API gains one read that returns content, size and modification time together. S3 answers it with one GET. Every
other provider, and every plugin compiled against the older interface, keeps working through a default implementation that does
what the callers do today.

## What Changes

- **New value type.** `net.onelitefeather.ingot.storage.StorageEntry` holds the content stream, the size (or unknown) and the
  modification time (or absent). It is marked `@ExperimentalIngotPluginApi`.
- **New provider call.** `StorageProvider.readEntry(location)` returns `Result<StorageEntry, ErrorResponse>`. It is marked
  `@ExperimentalIngotPluginApi`. The default implementation calls `getFileDetails` and `getFile` as callers do today, so a
  provider that does not override it behaves exactly as before, and a plugin compiled against the older interface still loads
  because the JVM falls back to the default.
- **S3 override.** `S3StorageProvider` answers `readEntry` with one GET and reads size and modification time from the response
  headers. A missing object maps to the same error as today.
- **Maven GET.** The Maven file endpoint uses `readEntry` where it today reads the details and then the content. Responses,
  status codes, headers (length, last modified, entity tag) and ranged reads are unchanged.
- **Commit type.** The change ships under `perf(storage)`. It mixes no other type.

### Out of scope

- Changing HEAD handling, directory listings or the entity tag computation.
- Presigned redirects and ranged reads. They keep their own paths.
- Connection tuning on the object store or the load balancer (`tcp_nodelay`). It is an operator matter.
- Any change to existing provider signatures or to the packages `com.reposilite.*`.

## Capabilities

### New Capabilities

- `storage-entry-read`: the combined read, its default implementation and the single-request behaviour of the S3 provider.

## Impact

- **Operators:** no configuration key, environment variable, volume, manifest, image or plugin change. Nothing here is BREAKING.
  S3 repositories issue one request instead of two for a file download.
- **Plugin authors:** one new method with a default on `StorageProvider` and one new type in `net.onelitefeather.ingot.storage`,
  both experimental. Existing storage plugins compile and load unchanged.
- **Plugin API binary surface:** the dump of `extend-plugin-abi-gate` reports additions only.
- **Code:** `reposilite-backend` (storage package, S3 provider, Maven endpoint).
- **Commits:** the change ships under `perf(storage)`. The code pull request is listed in `tasks.md`. The proposal pull request is:

  `docs(openspec): propose read-storage-entries-in-one-request`

## Verification

- Measurement behind the change: Ceph RGW costs about 45 ms per response that carries a body on keep-alive connections without
  `tcp_nodelay`. Removing the HEAD removes one such round trip per file.
- Tests are written first and run without network, database or system clock. The S3 request count is asserted with a recording
  client, not by timing.
- `openspec validate read-storage-entries-in-one-request --strict` passes, and the dash grep prints nothing.
