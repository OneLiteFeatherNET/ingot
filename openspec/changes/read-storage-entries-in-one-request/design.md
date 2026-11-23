# Design

## D1. One call, additive

`readEntry(location)` is added to `StorageProvider` next to `getFile`, in the style of `presignedGetUrl`: a default
implementation keeps every existing provider and plugin working. The default is `getFileDetails(location)` followed by
`getFile(location)`, mapped into a `StorageEntry`. A directory or a missing file yields the same error the callers get today.

## D2. The value type

`StorageEntry(content: InputStream, size: Long, lastModified: Instant?)` lives in `net.onelitefeather.ingot.storage`, because it
is new API, and carries `@ExperimentalIngotPluginApi`. Size uses the existing `UNKNOWN_LENGTH` constant when a provider cannot
tell. The caller owns and closes the stream.

## D3. S3

The S3 provider issues one GET and reads `Content-Length` and `Last-Modified` from the response. If the existing client caches
details, the entry fills that cache so a following details call is free. Error mapping is shared with `getFile`.

## D4. Caller

The Maven file endpoint calls `readEntry` for non-ranged reads. Ranged reads keep `getFile(location, offset, length)` because
the range needs the size first. Headers, status codes and the entity tag are produced from the same values as before.

## D5. Risks

A provider override that streams lazily must not leak the connection when the caller never reads the body. The caller closes
the stream in every path, and a test covers the error path.
