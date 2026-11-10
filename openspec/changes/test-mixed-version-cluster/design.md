# Design

## Context

Verified on this worktree:

- Every table is created or extended with Exposed `SchemaUtils.createMissingTablesAndColumns`, called from nine places in
  `reposilite-backend/src/main/`, for example `PermissionNodeMigration.kt:59` and `SqlScheduledJobClaims.kt`. That call adds
  what is missing and drops nothing. No `dropColumn`, `ALTER TABLE` or `SchemaUtils.drop` exists in the main source set.
- Added columns so far are nullable or carry a default: `revision` on the settings table is `long("revision").default(0L)`
  (`SqlConfigurationRepository.kt:44`, with a comment that existing rows start at revision 0), and the lease columns of
  `scheduled_job_claim` are nullable (`docs/gitbook/reference/upgrade-changes.md`, table section).
- Two data migrations run once and claim a marker row first: `LegacyAccessTokenMigration` (ledger rows, copies legacy tokens,
  `LegacyAccessTokenMigration.kt`) and `PermissionNodeMigration` (marker `002-permission-nodes`, line 56; it deletes the
  converted team grants at line 104 and all instance roles at line 122). A legacy token written after the first run is picked
  up by the next start (comment at `LegacyAccessTokenMigration.kt` on the ledger).
- The envelope is `{"v":1,...}`. `ClusterEnvelopeCodec.decode` answers `Ignored("unknown envelope version")` when `v` is not
  `VERSION` (`ClusterEnvelope.kt:72-75`), `Ignored("unknown event type")` for an unknown type (line 77), and never throws.
  Unknown extra fields of the payload are not inspected, so they pass. `ClusterReceiver.accept` counts an ignored message
  and returns `null` (`ClusterReceiver.kt`, `ignore()`).
- Shared settings carry a `revision` and are written by a single conditional update
  (`SqlConfigurationRepository.kt:103-110`). A release without the column writes the content without raising it.
- `SettingsSecrets.mask` withholds a domain it has no type for (`SettingsSecrets.kt:108-116`): an unknown domain in a
  document is tolerated by the masking. Whether the loader of the old release tolerates it is checked by the test.
- The existing container test `ClusterBusImageTest.previous release serves beside the new image`
  (`reposilite-backend/src/containerTest/kotlin/com/reposilite/image/ClusterBusImageTest.kt:70-98`) starts the previous
  release, then the current image, without a bus and without storage in common, and asserts four HTTP answers.
- `ImageUnderTest.previous` reads `ingot.previous.image`, whose default in `reposilite-backend/build.gradle.kts:374` is the
  fixed tag `ghcr.io/onelitefeathernet/ingot:1.1.0`. The latest release is `v1.1.1`.
- The container job of `ci.yml` runs the container tests with `./gradlew :reposilite-backend:containerTest`
  (`.github/workflows/ci.yml`, "Run the container tests").

## Decisions

### 1. The rule, as the code allows it

| Layer | Allowed inside N and N+1 | Not allowed | Why the code makes it safe |
|---|---|---|---|
| Schema | New table, new nullable column, new column with a default | Drop, rename, type change, new `NOT NULL` column without a default | N inserts without the new column, so a column that needs a value breaks N |
| Data | A migration that copies rows and leaves the source in place | A migration that deletes what N still reads | N reads the source rows until it is replaced |
| Bus | New event type, new payload field | New envelope version, removing a payload field N reads | `decode` ignores unknown versions and types |
| Settings | New domain, new field with a default | Renaming a domain or a field | Unknown content must be tolerated by N, checked by the test |
| Jobs | Leases and claims added as nullable columns | Relying on N honouring a lease | N ignores leases (`upgrade-changes.md`, mixed versions note) |

The permission node migration breaks the "data" row for a pair whose N already reads nodes. That pair does not exist yet,
because 1.1.x has no permission nodes. The rule states it, and the first release after 1.2.0 must either defer the deletes
or accept that the rule is narrower for that step.

### 2. The test runs in the container suite, not on kind

The question is two different images on one database. The suite already has `ImageUnderTest.previous`, a MariaDB helper, a
NATS helper and an HTTP helper (`ClusterBusImageTest.kt`). Kind adds a cluster that proves nothing more about the pair, and
`add-ha-smoke-test` already covers the chart. A new class `MixedVersionClusterTest` next to `ClusterBusImageTest` reuses the
helpers. The existing case stays: it is the cheapest "both start" check.

### 3. Shared repository storage is a bind-mounted temporary directory

Deploys must be visible on both replicas, so both containers mount one directory at the repository path, owned by the service
account through `PUID` and `PGID`. A `@TempDir` per test keeps the tests independent. S3 as the shared store is covered by the
smoke test and the database matrix change, and does not distinguish N from N+1.

### 4. The schema check reads `information_schema`

After the previous release has started and again after the current build has started, the test lists
`(table, column, is_nullable, column_default, extra)` of the database. For every column that exists only in the second
listing, the check fails if it is `NOT NULL` with no default and not auto-increment. A column that disappeared or changed type
also fails. This is a real database check, so it catches a change that an Exposed mapping alone hides. It needs no knowledge of
the table list, so a new table is covered without editing the test.

### 5. The bus case injects, it does not mix transports

The previous release has no bus, so a mixed bus cannot be observed between N and N+1. The test therefore starts two current
replicas on one NATS broker and publishes, from the test with a plain NATS client over the broker's HTTP monitoring port or a
second container command, one message with `"v":2` and one with `"v":1` and an unknown `type`. Afterwards the status of the
receiving replica must show the messages as ignored, no failure recorded, and the replica must still apply a real event. This
is the behaviour N+1 needs when it receives from an N+2 that speaks a newer envelope. The golden wire test (decision 6) covers
the codec in milliseconds, and this case covers the wired receiver.

### 6. A golden test pins the wire format without a container

`ClusterEnvelopeCodecGoldenTest` in the normal `test` source set holds one fixed JSON string per event type, decodes it and
asserts the type, the origin and the payload fields. A second test adds an unknown payload field and expects `Accepted`. A
third expects `Ignored` for `"v":2`. It needs no network and no clock, and it fails the moment a change alters the wire
format of version 1.

### 7. The previous image is the latest release

`ingot.previous.image` keeps its property name and its role. The CI container job resolves the latest release tag (the
highest `v*` tag of the repository, through `git describe` over the tags the checkout already fetched) and passes it as
`-Dingot.previous.image=ghcr.io/onelitefeathernet/ingot:<tag>`. The default in `build.gradle.kts` is raised to `1.1.1` for local
runs. A developer can still override it. The image is taken from the registry, so a pull request needs no secret.

Alternative rejected: pin by digest in the build file. A digest would stay N after a release, and the point is that N moves.

## Risks and edge cases

- **N holds no schema lock.** The previous release creates tables at start without a lock, so the order matters: N is started
  first, N+1 second, and N is restarted last. The test names that order, because the schema lock of N+1 cannot protect against
  an N that starts concurrently. This is the limit "do not start two releases at the same moment" in the documentation.
- **Concurrent deploys of one artifact across N and N+1** are not guarded: the metadata lock exists only in N+1
  (`fix-cluster-metadata-lock`), so `maven-metadata.xml` can lose a version when both write at once. The test deploys
  different artifacts concurrently and asserts both succeed. It does not assert the metadata case and the rule says so.
- **First-start duration.** The status of the instance list fills one minute after a start. Bounds are taken from
  `ClusterBusImageTest.STATUS_BOUND`, with latches and no `Thread.sleep`.
- **Previous image not pullable.** The test is skipped with a clear message through a JUnit assumption only when the registry
  is unreachable. A pull that answers "not found" fails, because the tag came from the repository's own tags.
- **Slow suite.** The class starts three to four containers. It runs inside the existing container job and counts against the
  existing timeout. The two-container shape keeps it near the existing case.

## Migration plan

None. No behaviour, configuration or data changes. The tests run in the existing "Container images" job. If that job exceeds
its timeout, raise it in the same pull request.

## Test plan

F.I.R.S.T., as the `tasks.md` rules require. The golden test is Fast, Independent and Repeatable. The container test is an
integration test: it relaxes Fast, and keeps Independent (a network, database and directory per test), Repeatable (no wall
clock reads, no fixed ports, pinned images) and Self-validating (every assertion carries a message). Waits are bounded polls
with a latch. The tests are written first and seen to fail: for the schema check by adding a `NOT NULL` column without a
default to a throw-away table in the database between the two listings, for the bus check by publishing a `"v":1` message with
an unknown type and asserting it is applied.
