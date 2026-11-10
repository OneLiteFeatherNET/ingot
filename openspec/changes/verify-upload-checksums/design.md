# Design

## What exists today

- Deploy goes `MavenEndpoints.deployFile` (`reposilite-backend/src/main/kotlin/com/reposilite/maven/infrastructure/MavenEndpoints.kt:236-241`)
  to `RepositoryService.deployFile` (`.../maven/RepositoryService.kt:84`). It checks `acceptsDeploymentOf` (`Repository.kt:69`:
  redeployment allowed, or a metadata file, or the path is free) and then calls `storageProvider.putFile`.
- A checksum file is stored like any other file. Nothing compares it with the artifact. A grep of the maven package finds
  digests only in the entity tag (`MavenFacade.kt:102-120`, reads a stored checksum), in server-side generation
  (`Repository.kt:78-97`, opt-in by the header `X-Generate-Checksums`) and in the mirror code. No check of uploads exists.
- `Checksum` (`.../maven/api/Checksum.kt:6`) already has `MD5`, `SHA1`, `SHA256` and `SHA512` with a streaming `generate`.
  The check reuses it, so no new digest code and no change to the generation loop.
- The filesystem provider writes to a temporary file next to the target and commits with an atomic move
  (`storage/filesystem/FileSystemStorageProvider.kt:158-198`, from `fix-atomic-filesystem-upload`). The S3 provider stores an
  object in one put (`storage/s3/S3StorageProvider.kt:119`). Either way a stored file is whole.
- `DeployEvent` is emitted after a successful store and the audit plugin turns it into `ARTIFACT_DEPLOYED`
  (`audit/application/AuditPlugin.kt:58-69`). The audit actions are the enum `audit/api/AuditAction.kt`.

## Decisions

### D1 The order problem: check the checksum file when it arrives, only if the artifact is already there

Maven Resolver and Gradle send the artifact first and each checksum after it. Some tools send only checksums, some never send
any, and a few may send them first. Three designs were possible.

1. **Check on the checksum upload, against the stored artifact** (chosen). Needs no state, no waiting and no hook in the
   storage. The artifact is committed, so the digest is of a stable file.
2. **Check on the artifact upload, against stored checksum files.** Catches the checksum-first order, but the artifact is
   already committed when the data arrives, so a mismatch would need a rollback or a pre-commit hook in both providers. It is
   the right follow-up, not the first step.
3. **Hold the checksum file as pending until the artifact arrives.** Needs a pending area that survives restarts and is shared
   by replicas, and a timeout policy. Far too much for a check.

Consequences of choosing 1, all stated in the how-to:

- A checksum file whose artifact is absent is accepted and not checked. The rule is the same in `warn` and `reject`, because
  "reject what cannot be verified" would break clients that deploy checksums first and give no safety in return for them.
- A client that sends no checksum files is not affected. Requiring them is out of scope.
- An artifact that never gets its checksum file is never checked. The policy protects the pair, not the artifact alone.
- Whether Maven Resolver and Gradle really deploy the artifact first on every path (also `maven-metadata.xml`, POM and
  module files) is confirmed by an integration test with the real clients that are available to the build (task 3.1). If a
  client turns out to send checksums first, the policy leaves it unchecked, and design option 2 becomes the next change.

Metadata files (`maven-metadata.xml`) are redeployed on every release and `acceptsDeploymentOf` allows that. Their checksum
files are verified like any other. A redeploy of the metadata file followed by its new checksum file is the normal pattern, so
the order works. A concurrent deploy of two versions of the same metadata file is last-writer-wins today and stays so.

### D2 Where the check sits

In `RepositoryService.deployFile`, after `acceptsDeploymentOf` and before `putFile`, so that a `409 Redeployment is not
allowed` still wins over a checksum answer and the `400` is only given for an upload that would otherwise be stored. A new
small class `UploadChecksumVerifier` holds the logic and takes the repository and the request, so it is testable without HTTP.
It runs only when the effective policy is not `ignore` and the file name has one of the four suffixes.

### D3 Reading the upload, digesting the stored file

The upload body of a checksum file is read into memory, capped at 1024 bytes. A longer body is a malformed checksum and is
refused under `reject`. The bytes are kept and stored from memory afterwards, so the stream is read once. Parsing takes the
first whitespace-separated token, requires hex of the length of the algorithm (32, 40, 64, 128) and compares in lower case. The
stored artifact is read with `storageProvider.getFile` and digested with `Checksum.generate`, streaming, never buffered whole.

### D4 Atomicity

The check reads a committed file and decides before anything is written, so the refusal leaves no state behind: no temporary
file, no partial checksum, no half pair. The accepted case stores the checksum file through the normal provider path, which
is atomic on the filesystem and a single put on S3. No lock is added.

Remaining race: with `redeployment` on, a second upload of the artifact can replace it between the digest and the checksum
commit, and the stored checksum then belongs to the older bytes. Writers are not serialised across replicas (see
`fix-atomic-filesystem-upload`, known limits), so this is the same last-writer-wins as for the artifact itself. The change
does not claim to close it, and the how-to says so.

### D5 S3 cost

On S3 the digest needs one read of the artifact per checksum file, so four checksum files read it four times. That is egress and
latency that grow with the artifact size, and an operator with large artifacts and a small bill should choose `ignore` or
only one checksum algorithm in the client. The object ETag is not usable, because it is not an MD5 of the content for
multipart uploads. Caching digests per artifact is rejected: it adds shared state for an opt-in feature. The how-to states the
cost. Whether a read goes through the HEAD or listing cache of `CachableS3Client` is checked in task 3.1: the check must read
the object, not a cached entry.

### D6 Policy values and precedence

`ignore`, `warn`, `reject`, in the vocabulary of the mirror policy. The repository value wins, then
`INGOT_UPLOAD_CHECKSUM_POLICY` (with the `REPOSILITE_` fallback and the system property), then `ignore`. The key is
nullable so that "not set" is visible and the instance default can apply. A value that is not one of the three is a startup
error for the variable and a settings validation error for the key, each naming the key. Proxies and groups take no uploads
(`Repository.kt` `acceptsUploads`), so the key has no effect there and is not refused.

### D7 Audit and metric

A new `UploadChecksumMismatchEvent` is emitted by the verifier. The audit plugin records `UPLOAD_CHECKSUM_MISMATCH` with
actor from the session identifier, repository, path and details `algorithm` and `outcome`. A new enum entry is appended at the
end of `AuditAction`, since names are stored. Consumers of the audit API that do not know the name show it as text, as for
any new action. The deploy outcome counter (`operationCounters.recordDeploy`, `MavenFacade.kt:145`) gets an additional label
value `checksum_mismatch`, used only when the verifier refuses.

## Known limits

- Checksum-first uploads are not checked (D1).
- The race in D4 with redeployment on.
- On S3 the check costs reads (D5).
- Artifacts stored before the policy was set are not checked (no backfill).

## Rollback

The policy is off by default. After a rollback to Reposilite or an older Ingot, stored files are unchanged: the change never
writes a file of its own. The one new stored thing is the key `uploadChecksumPolicy` in the shared maven settings, and task 6.1
checks that the upstream image starts with that key present.
