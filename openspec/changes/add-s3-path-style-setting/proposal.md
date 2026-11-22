# Proposal

## Why

Path-style S3 addresses (`https://host/bucket/key`) are needed by most S3-compatible servers such as MinIO. Today they are switched on
only by the JVM property `reposilite.s3.pathStyleAccessEnabled=true`
(`reposilite-backend/.../storage/s3/S3StorageProviderFactory.kt:59`). The property is process-wide, so it cannot differ between two
repositories, it is not visible in the settings form, and an operator must edit `JAVA_OPTS` for what is really a property of one bucket.

## What Changes

- The S3 storage settings gain `pathStyleAccess`, a boolean that defaults to unset, shown in the repository settings with a description.
- When `pathStyleAccess` is set (true or false), it decides for that repository. When it is unset, the JVM property decides, exactly as
  today. The property stays valid as the fallback.
- Existing settings files, which lack the field, read as unset, so behaviour is unchanged.

### Out of scope

- Removing or deprecating the JVM property.
- The `reposilite.s3.use-s3-v4-signer` property and other S3 client switches.
- Any other storage provider.

## Capabilities

### New Capabilities

- `s3-path-style-setting`: the per-repository field and its precedence over the property.

## Impact

- **Type and scope.** The change ships under `feat(storage)`. Its commits are `test(storage)`, `feat(storage)` and `docs(storage)`, one
  type each. The pull request title is `feat(storage): add a path style setting to the s3 storage`.
- **Breaking marker.** None. The field is additive with an unset default. A configuration without it, and the property alone, behave as
  before, so an existing Reposilite installation needs no edit.
- **Order.** Independent of the other open changes.

## Verification

- Unit tests of the precedence with an injected property lookup (no real system property).
- A settings round trip test: a file without the field loads as unset and is written back without changing meaning.
- `openspec validate add-s3-path-style-setting --strict --no-interactive` passes and
  `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-s3-path-style-setting` prints nothing.
