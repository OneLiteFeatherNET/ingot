# Design

All paths are relative to the worktree root. Nexus facts come from the Sonatype documentation as the research report read it
and are marked "expected" until the spike (task 2.1) confirms them. Nothing here was observed on a live Nexus.

## What exists on main and what is missing

- The legacy `reposilite-plugins/migration-plugin` reads Reposilite 2.x `tokens.dat` and has nothing to do with Nexus
  (`reposilite-plugins/migration-plugin/src/main/kotlin/com/reposilite/plugin/migration/MigrationPlugin.kt:23`, plugin name
  `migration`). The new plugin is named `nexus-import` and shares nothing with it. The how-to
  `docs/gitbook/how-to/import-from-other-repository-managers.md` describes a manual copy and says it verifies nothing.
- Typed repositories exist: `RepositoryType` has `HOSTED`, `PROXY` and `GROUP` (`reposilite-backend/src/main/kotlin/com/reposilite/maven/RepositoryType.kt`),
  and `RepositorySettings` carries `type`, `redeployment`, `preserveSnapshots`, `storageProvider` and `proxied`
  (`reposilite-backend/src/main/kotlin/com/reposilite/maven/application/MavenSettings.kt:125-175`). A group lists local
  repositories as `{ "reference": "releases" }`, see `docs/gitbook/how-to/create-a-group-repository.md:29`.
- Writing through the facade exists: `MavenFacade.deployFile(DeployRequest)` (`reposilite-backend/src/main/kotlin/com/reposilite/maven/MavenFacade.kt:142`)
  takes the repository, the path, a `by` string, a stream and `generateChecksums`, refuses non-uploadable repositories, and
  emits a `DeployEvent` (`reposilite-backend/src/main/kotlin/com/reposilite/maven/RepositoryService.kt:92`) which the audit plugin turns into
  `ARTIFACT_DEPLOYED` (`reposilite-backend/src/main/kotlin/com/reposilite/audit/application/AuditPlugin.kt:58-68`). The search index sink hears the same event.
- Repository definitions are written through `SharedConfigurationFacade.updateDomain(name, ifMatch, actor, transform)`
  (`reposilite-backend/src/main/kotlin/com/reposilite/configuration/shared/SharedConfigurationFacade.kt:243`), which checks an entity tag and answers
  `409` when the shared settings file owns the domain (`:197`).
- Read-only mode is merged. `managerOnly` refuses writes while it is on (`reposilite-backend/src/main/kotlin/com/reposilite/shared/ContextDsl.kt:97-118`), and
  `ReadOnlyFacade.isReadOnly()` is public (`reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/readonly/ReadOnlyFacade.kt:40`).
- Bundled plugins exist, with exactly one switch. `PluginLoader.loadBundledPlugins()` returns at once unless `INGOT_METRICS_ENABLED`
  (or `REPOSILITE_METRICS_ENABLED`) is true, and then loads every jar in the directory
  (`reposilite-backend/src/main/kotlin/com/reposilite/plugin/PluginLoader.kt:100-137`). A second bundled jar would be switched on by the metrics
  switch. That is wrong, and it is the one core change this feature cannot avoid.
- The image builds and ships one plugin: `Dockerfile:30` (`shadowJar`), `Dockerfile:86` (`COPY`), `.dockerignore:43`, and the
  module list in `settings.gradle.kts:34`.
- Missing: any Nexus client, any import job, any loss report, and any way for a long job to survive a replica that drops out.

## The Nexus side

The plugin uses only the supported REST v1 surface under `/service/rest/v1`, with HTTP Basic authentication. It does not use the
Configuration API of 3.87+, because it is built for Nexus to Nexus moves, needs a stricter permission and a version header, and
whether it runs on Community is unverified. It works on every edition and on versions before 3.87.

Endpoints and the shapes the MVP expects. The spike records the real ones.

| Need | Request | Expected response fields used |
| --- | --- | --- |
| Reachability | `GET /service/rest/v1/status` | `200`, empty body, no credentials |
| Version, edition | the `Server` response header, for example `Nexus/3.70.1-02 (OSS)` | version string, `OSS` or `PRO` |
| Credential check | `GET /service/rest/v1/repositories` with Basic auth | `401` bad credentials, `403` no permission |
| List repositories | `GET /service/rest/v1/repositories` | array of `name`, `format`, `type` (`hosted`, `proxy`, `group`), `url`, `attributes` |
| Hosted settings | `GET /service/rest/v1/repositories/maven/hosted/{name}` | `online`, `storage.blobStoreName`, `storage.writePolicy` (`ALLOW`, `ALLOW_ONCE`, `DENY`), `cleanup.policyNames`, `maven.versionPolicy`, `maven.layoutPolicy` |
| Proxy settings | `GET /service/rest/v1/repositories/maven/proxy/{name}` | `proxy.remoteUrl`, `proxy.contentMaxAge`, `proxy.metadataMaxAge`, `httpClient.authentication.type` and `username` (no password), `negativeCache`, `routingRule`, `maven.*` |
| Group settings | `GET /service/rest/v1/repositories/maven/group/{name}` | `group.memberNames` in order |
| Assets | `GET /service/rest/v1/assets?repository={name}&continuationToken={t}` | `items[]` with `path`, `downloadUrl`, `fileSize`, `lastModified`, `checksum` (`sha1`, `md5`, maybe more), `continuationToken` (null on the last page) |
| Content | `GET {downloadUrl}` of an asset | the bytes |

Facts the design relies on, each one a spike question:

1. Page size is not fixed (50 on the Pagination page, 100 on the Components page). The client follows `continuationToken` and
   never assumes a size.
2. A token is opaque, and the order is not stable while Nexus is written to. Correctness therefore never depends on the token
   alone, see "Resume".
3. Checksums are expected as `sha1` and `md5`. If the response also has `sha256` or `sha512`, the plugin verifies against them as
   well. A sha256 is always computed locally.
4. Settings endpoints need an administrator read permission and the asset listing needs only browse and read on the repository. A
   credential that can list assets but not read settings is common, so a `403` on settings is a result, not an error: the
   repository's definition becomes `MANUAL` and its content can still be copied.
5. A proxy's remote credential password is not readable. The definition is created without credentials and reported `PARTIAL`.
6. Maven `maven-metadata.xml` and the `.sha1` and `.md5` sidecar files are assets of a maven2 hosted repository, so the listing
   returns them.

## Module and names

`reposilite-plugins/nexus-import-plugin`, following `reposilite-plugins/prometheus-plugin`: the `-plugin` directory suffix, a
`ShadowJar` task named `nexus-import-plugin.jar` written to `reposilite-test/workspace/plugins`. `CLAUDE.md` keeps module
directories and artifactIds as they are and moves only rewritten parts, so the directory follows the existing naming and the code
is new: package `net.onelitefeather.ingot.nexusimport`, plugin annotation `@Plugin(name = "nexus-import", dependencies = ["maven", "shared-configuration", "audit"])`.
The Gradle `group` of plugin subprojects stays `com.reposilite.plugin` (`reposilite-plugins/build.gradle.kts`), because
changing it would be a namespace move that nothing asks for.

## Switch and bundling

`PluginLoader` gets a table of bundled jars and their switches. A jar named in the table loads only when its own switch is true.
`prometheus-plugin.jar` keeps `INGOT_METRICS_ENABLED` with the `REPOSILITE_` fallback and its current precedence rule (an
`INGOT_` value wins over the legacy one, even when it is `false`). `nexus-import-plugin.jar` uses
`INGOT_NEXUS_IMPORT_ENABLED` with the fallback `REPOSILITE_NEXUS_IMPORT_ENABLED`. A jar that the table does not know about follows
the metrics switch, as today, so a drop-in installation that put its own jar there does not change. The existing
`BundledPluginLoadingTest` passes unedited.

A copy in `/app/data/plugins` is always loaded, as for any plugin, and needs no switch. The jar sits in the image and is never
read while the switch is off, so the three checks from `CLAUDE.md` do not change: the entrypoint, the paths and the variables
are untouched, and an unmodified upstream compose file starts.

Further settings, all read from the environment by the plugin, each with its `REPOSILITE_` fallback and `INGOT_` winning:

| Variable | Default | Meaning |
| --- | --- | --- |
| `INGOT_NEXUS_IMPORT_ENABLED` | unset (off) | loads the bundled jar |
| `INGOT_NEXUS_IMPORT_CONCURRENCY` | `4` (1 to 16) | files in flight per job |
| `INGOT_NEXUS_IMPORT_MAX_REQUESTS_PER_SECOND` | `5` (1 to 100) | requests to Nexus per second per job, listing and downloads together |
| `INGOT_NEXUS_IMPORT_AUDIT` | `per-file` | `per-file` or `summary` |
| `INGOT_NEXUS_IMPORT_ALLOWED_HOSTS` | unset (any host) | comma list of host names a connection may point at |

The throttle default is conservative on purpose. Community Edition may count requests against a daily cap (unverified), and a
migration that exhausts it also blocks the builds that still run against Nexus. An unknown value fails the plugin start with a
message naming the variable, as the secret reference switch does.

## Credentials

Rules: never stored in plain text, never logged, never returned.

- A credential is a user name and a password. The password is accepted as a literal or as a secret reference
  (`${env:NAME}`, `${file:PATH}`), resolved by the existing `SecretReferenceResolver`
  (`reposilite-backend/src/main/kotlin/com/reposilite/configuration/shared/SecretReferenceResolver.kt`) when the operator has switched
  references on (`INGOT_SETTINGS_SECRET_REFERENCES`) and allowed the variable or directory.
- A literal password lives only in memory, in a type whose `toString` prints `********` and which is wiped when the job ends. It is
  not written to the job tables, the audit log, a log line, an exception message, an API response or the report. A reference is
  stored as the reference text, never as the resolved value.
- A URL with user information is rejected (`400`), because it would put a secret in a stored value and in logs.
- `Authorization` goes only to the origin of the connection URL. An asset `downloadUrl` on another origin is refused and
  recorded. A redirect to another origin is followed without the header, at most three times.
- TLS: the JVM trust store, with no setting of the plugin's own. In the image that store already contains the operator's private
  CAs when `INGOT_EXTRA_CA_CERTS` is set, which `add-image-extra-ca-bundle` implements and which lands first. A bare jar install
  uses the standard JVM trust store properties. There is no switch that turns verification off. A
  plain `http` URL is accepted but answered with a warning in the connection result, because the password would cross the network in the clear.

HA and credentials. The report notes that "in memory" and "a replica takes over" contradict each other. The resolution is a
state, not a compromise on storage:

- A job created with a **reference** can be taken over by any replica that can resolve the reference, with no operator action.
- A job created with a **literal** password is run by the replica that received the request. If that replica loses the lease or
  restarts, the job becomes `PAUSED` with the reason `CREDENTIALS_NEEDED`. The operator supplies the password again with
  `PUT /api/migration/nexus/jobs/{id}/credentials` on any replica, and that replica takes over. Progress is not lost.

Encrypting passwords in the database was considered and rejected for the MVP: it needs a key that every replica shares, which is
a new secret to manage and the first thing a reviewer asks about.

## Jobs, lease and resume

### Tables

Four plugin-owned tables, created with `SchemaUtils.create` as other modules do
(`reposilite-backend/src/main/kotlin/com/reposilite/cleanup/infrastructure/SqlCleanupRunRepository.kt:62`), only when the plugin starts:

- `nexus_import_job`: id, kind (`DRY_RUN`, `IMPORT`, `VERIFY`), status, pause reason, parameters (JSON without secrets: Nexus URL,
  user name, credential reference, repository selection, conflict policy, audit mode), lease holder token, lease expiry,
  created, started and finished instants, last error.
- `nexus_import_repository`: job id, Nexus name, target name, status, listing cursor (the last `continuationToken`), counters
  (listed, copied, skipped, conflicts, failed, bytes).
- `nexus_import_asset`: job id, repository, path, size, expected md5 and sha1, computed sha256, result, attempt count, error.
  Primary key `(job id, repository, path)`.
- `nexus_import_report`: job id, object type, Nexus id, target, status, reason, consequence, action. These are the loss report rows.

### Lease and fencing

The pattern is the search rebuild's, `net/onelitefeather/ingot/search/runs/RunStore.kt`: a lease expiry on the job row, a fresh
random token per acquisition, and every write of progress a conditional update on that token. A replica that lost its lease cannot
commit again, because its token no longer matches. `RunStore.takeOver` shows the shape (`:107-131`) and `RunStore.progress` the
commit (`:143-158`).

`ScheduledJobClaims` (`reposilite-backend/src/main/kotlin/com/reposilite/scheduling/ScheduledJobClaims.kt`) was considered. It has `lease`,
`takeover`, `extend` and `complete`, but a claim lives in another table than the progress rows, so "I still own the job" and "I
commit this page" cannot be one transaction, and it hands out no fencing token. The run-store pattern has both, so it is used.

Timeline of a lease loss:

1. Replica A runs job 7. Each committed batch extends the lease. A heartbeat extends it also while a large file streams.
2. A stalls for longer than the lease. Replica B's periodic tick finds job 7 with an expired lease and takes it over with a new token.
3. A resumes. Its next commit is a conditional update on the old token, matches no row, and A stops without writing result rows. A
   checks its own lease deadline (injected `Clock`) before it starts every file, so it also stops sending requests to Nexus.
4. A file A was already writing when it lost the lease can still finish. B meets the same path: the file exists, so B compares
   checksums. Same checksum: result `SKIPPED_SAME`. If a repository allows redeployment, both writes carry the same bytes. Neither case needs a lock, because
   every write is idempotent.

A job whose lease holder is gone and whose credential is a literal is paused with `CREDENTIALS_NEEDED`, as above.

### Idempotence

The unit is a file. For each asset the worker decides in this order:

1. The result row is `COPIED` or `SKIPPED_SAME` and the stored size equals the listed size: nothing to do.
2. The target path exists with the same size: read it, compute sha1, and compare with the expected sha1. Equal: `SKIPPED_SAME`.
3. The target path exists and differs: a conflict, handled by the conflict policy.
4. The target path does not exist: download, verify while streaming, write, record `COPIED`.

Running a second job over the same repositories therefore does the minimum, and an interrupted job continues the same way.

### Resume

The listing is the fragile part. The persisted `continuationToken` is only an optimisation. On resume the worker sends it
back; if Nexus refuses it (`400` or `404`), the worker restarts the listing and skips every path whose result row is already
final. At the end of a listing pass the worker compares the set of listed paths with the result rows, so a path that appeared
or vanished during the pass is found by the next pass or by verify, not lost. A path that Nexus answers `404` for after it was
listed is recorded `GONE`, which does not fail the job.

### Read-only mode

A job does not start and does not take over while `ReadOnlyFacade.isReadOnly()` is true. A running job pauses after the batch
in flight with the reason `READ_ONLY` and resumes by itself when read-only is off, as the scheduled runs do
(`reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/search/rebuild/RebuildScheduler.kt`, `readOnly` parameter). The plugin writes through
`MavenFacade`, which does not check read-only mode itself (the check sits in the request dispatch), so the plugin has to check.
The job endpoints are `POST` routes under `managerOnly`, which answers `503` while read-only is on, so a cancel is refused too;
that is documented rather than exempted.

### Concurrency, throttle, retries

`CONCURRENCY` files in flight, one shared token bucket for `MAX_REQUESTS_PER_SECOND`, driven by the injected `Clock` and an
injected sleeper so tests never wait. Retries: `429`, `5xx` and I/O errors with exponential backoff, five attempts, honouring
`Retry-After`. `401` and `403` on a download pause the job with `CREDENTIALS_NEEDED`. A failed file is recorded `FAILED`, and the
job continues; the repository then ends `FAILED` rather than `VERIFIED`.

## Content copy

The write goes through `MavenFacade.deployFile`, so storage, metadata handling, the search index and events behave as for any
deploy. `by` is a session identifier `migration@<job id>`, which `AuditActor.fromSessionIdentifier` reads as actor `migration`
at address `<job id>`.

- **Checksum while streaming.** The download stream is wrapped in a digest stream computing md5, sha1 and sha256. At end of
  stream it compares md5 and sha1 (and sha512 or sha256 when Nexus reports them) with the listing and the size with `fileSize`, and
  throws on a mismatch. The write then fails and stores nothing, because the filesystem upload is atomic (`fix-atomic-filesystem-upload`)
  and the S3 path aborts its multipart upload. The red test for this asserts that a mismatch leaves no file. If a storage ever
  kept a partial file, the plugin deletes the path and records `CHECKSUM_FAILED`.
- **Sidecar files and metadata.** `.md5`, `.sha1` and `maven-metadata.xml` are assets and are copied byte for byte with
  `generateChecksums = false`. They are copied after the other files of their directory so that a metadata deploy does not see a
  half-copied version. Ingot does not regenerate metadata in the MVP.
- **Snapshots.** A deploy of snapshot metadata deletes older builds when `preserveSnapshots` is false
  (`reposilite-backend/src/main/kotlin/com/reposilite/maven/PreservedBuildsListener.kt:43`). Nexus does not do that, so a created hosted
  definition with a SNAPSHOT or MIXED version policy gets `preserveSnapshots = true`, reported `PARTIAL` with the reason. For an
  existing target repository the plugin does not change the setting; the dry run warns if it is false and the repository holds
  snapshots.
- **Hosted only.** Content is copied from Nexus hosted repositories. A proxy has no content to copy; a group has none of its own.
- **Not imported.** `uploader`, `uploaderIp`, `lastDownloaded`, `blobCreated`. `lastModified` is not applied in the MVP because
  `deployFile` has no way to set it; it is listed as lossy.
- **Audit.** `per-file` leaves the normal `ARTIFACT_DEPLOYED` event per file, with actor `migration`. `summary` has the plugin
  register a random bulk session id with the audit plugin, which then skips `ARTIFACT_DEPLOYED` for that exact `by` value. The
  id is not a name a token can have, so a token cannot silence its own audit trail by being called `migration-bulk`. A job
  ends with one `NEXUS_IMPORT_RUN` event in both modes, carrying the counts. This adds one `AuditAction` constant (the name is stored, so it is added and never renamed) and a
  small registration method on `AuditFacade`.

### Conflict policy

A conflict is a path that exists in the target with a different checksum. The policy is a job parameter:

| Policy | Behaviour |
| --- | --- |
| `refuse` (default) | Nothing is overwritten. The conflict is recorded with both checksums, the job goes on with other files, and the repository ends `CONFLICTED` and cannot become `VERIFIED`. |
| `abort` | The first conflict stops that repository (`CONFLICTED`) and the job goes on with the next repository. |
| `overwrite` | The existing file is replaced: deleted with `deleteUnchecked` and written again, both audited. It needs the explicit value in the job request and shows its count in the dry run first. `maven-metadata.xml` and sidecars are replaced together with their artifact. |

The dry run reports the number of conflicts it can see for each policy, so an operator chooses with the number in front of them.
The default never changes a file that exists. An existing target repository is never created again, renamed or retyped.

### Verify

Verify is a job kind that writes nothing. Per repository it compares: the count of assets, equality of the path sets (Nexus
listing against the Ingot storage listing), size per path, and sha1 per path computed from the stored bytes against the Nexus
value. The result is `VERIFIED` or `MISMATCH` with the first differing paths. A full read of the target costs time and is the
default; a selection of repositories limits it.

## Repository definitions

For each selected Nexus repository whose format is `maven2`:

| Nexus | Ingot definition | Status |
| --- | --- | --- |
| hosted | `type = HOSTED`, `visibility = PRIVATE`, `redeployment = (writePolicy == ALLOW)`, filesystem storage | `MAPPED`; `PARTIAL` when `DENY` (no read-only equivalent yet) or a SNAPSHOT policy (see snapshots) |
| proxy | `type = PROXY`, one `proxied` entry with `reference = remoteUrl`, `store = true`, no credentials | `PARTIAL`: cache not copied, credentials, negative cache and auto block lost |
| group | `type = GROUP`, `proxied` loopback references in `memberNames` order | `MAPPED` when all members are Maven repositories that are imported or already exist, `PARTIAL` when members of other formats were dropped |

Visibility is `PRIVATE` always. Nexus anonymous access is not read in the MVP, and the plugin never broadens access. A name that is
taken in Ingot is a collision: the existing repository is untouched, the report row says so, and the plugin proposes `<name>-nexus`
as the target. The operator can pick another target name per repository. A name that is reserved or invalid in Ingot is reported
before anything is written. The definitions of a job are written in one `updateDomain` call with the entity tag read at the start;
a `412` re-reads and retries once, and a `409` (the shared settings file owns the domain) turns every definition into `MANUAL`
with the JSON the operator can paste.

Storage is always the default filesystem provider in the MVP. S3 targets are chosen in the target definition by the operator
afterwards; Nexus blob stores are listed with the type and not mapped.

## The loss report

One row per discovered object: object type, Nexus id, Ingot target, status, reason, consequence, operator action. Statuses
`MAPPED`, `PARTIAL`, `MANUAL`, `UNSUPPORTED`, `EXCLUDED`. Objects in the MVP report: repositories (any format), their
definitions, blob stores (as information), and for every format other than maven2 a row `UNSUPPORTED` with component count and size,
which is the data for the format decision. Security objects, cleanup policies, routing rules, tasks and capabilities are listed
as `MANUAL` or `UNSUPPORTED` with the Ingot equivalent named, only when the credential may read them; otherwise a single row says
the credential could not read them. The dry run also reports the estimated transfer (sum of `fileSize`) and, from a short sample
download, an estimated duration, and the blockers (collisions, unreadable settings, a dead Nexus, a read-only Ingot).

Output: `GET .../report?format=json` and `format=csv`, one audited summary event per run.

## REST API

Under `/api/migration/nexus`, every route `managerOnly()` with no area, which means instance admin
(`reposilite-backend/src/main/kotlin/com/reposilite/shared/ContextDsl.kt:97`), and documented with `@OpenApi` like
`ReadOnlyEndpoints`. No route takes a credential in the query string.

| Route | Purpose |
| --- | --- |
| `POST /connection/test` | probe: reachability, version, edition, which capability the credential has |
| `POST /discover` | repositories, histogram and counts |
| `POST /jobs` | start a job: kind, connection, selection, target names, conflict policy; answers `202` with the id |
| `GET /jobs`, `GET /jobs/{id}` | state, per repository progress, pause reason, lease holder |
| `POST /jobs/{id}/pause`, `/resume`, `/cancel` | operator control |
| `PUT /jobs/{id}/credentials` | supply the password again |
| `GET /jobs/{id}/report` | loss report, `format=json` or `csv` |
| `GET /jobs/{id}/conflicts` | paths with both checksums |
| `DELETE /jobs/{id}` | removes a finished job and its rows |

Progress is read from the tables, so any replica answers. Errors use the problem details body that `add-problem-details`
introduced.

## Testing and the fake Nexus

There is no real Nexus in CI. The `sonatype/nexus3` image is under the Sonatype EULA, which this project does not accept for
automation, so the tests run against a **fake Nexus HTTP server built from documented responses**. It is a small in-process
server on the JDK `HttpServer`, bound to an ephemeral port on the loopback interface and started per test, that replays JSON
fixtures and file bytes held in memory. The fixtures are written by hand from the documented REST responses (Repositories,
Components, Assets and Pagination pages), not recorded from a running instance. What that proves: the client parses the documented
shapes, follows tokens, retries, and the job logic is correct. What it does not prove: that a real Nexus of a given version
answers exactly so. The spike marks each field "documented" or "observed", and every field that is only documented stays listed in
the fixture README so that a maintainer with a licensed instance can check it. The how-to says the first real run should start
with a dry run and a small repository.

The fake can also be made to misbehave on demand, which a real Nexus cannot: `429` bursts, a token that stops being valid, a file
that changes between listing and download, a `403` on settings, a truncated body, a wrong checksum.

Tests follow F.I.R.S.T.: an injected `Clock`, an injected sleeper for the throttle and the backoff, latches instead of sleeps, a
fresh fake server and an H2 or SQLite database in a temporary directory per test, no shared static state. The lease tests run two
workers against one database and a fake clock, and move the clock past the lease to prove the fenced commit.

## Decisions and alternatives

- **REST, not blob store files.** Blob files lose repository and component state, and the layout changes between versions. REST
  is the supported surface and needs read credentials only.
- **Job-row lease, not `ScheduledJobClaims`.** Argued above.
- **Literal password stays in memory.** The cost, a pause after a replica loss, is visible and bounded, and references remove it.
- **Verbatim metadata.** Regenerating metadata would change bytes Nexus served, and the verify pass would flag every one.
- **No `lastModified`.** No facade method sets it. Adding one is a core change this MVP does not need.
- **Conflict default `refuse`.** An import must be repeatable and must never silently change what already serves builds.

## Risks

- A fake is not Nexus. The spike and the README keep the unverified fields visible.
- A very large repository means millions of result rows. Rows are small and indexed by the primary key; `DELETE /jobs/{id}` frees
  them. A retention policy is not part of this change.
- A job that reads every target file for verify competes with live traffic. The throttle covers Nexus only; verify reads are
  bounded by `CONCURRENCY`.
- Listing order is unstable under writes. Mitigated by the reconcile step, and the dry-run advice is to freeze Nexus writes for
  the final pass, which belongs to the cut-over change.
