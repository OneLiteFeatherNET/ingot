# Artifact search

Status: proposed. Decision record: ADR-INGT-0002 (proposed, Outline). This document specifies
the recursive artifact search of Ingot: the index tables in the existing database, the way the
index is kept current, the query and permission model, the REST API, the engine abstraction,
the dashboard page and the rollout. It refines ADR-INGT-0002 and departs from it in six places,
all listed in section 12. The ADR stays unchanged until its owner updates it in Outline after
review of this document.

The feature ships in 1.3.0, not in 1.2.0 (section 9.5).

## 1. Goals and non-goals

### 1.1 Goals

Today no endpoint searches artifacts or files by name or path. The only searches are the
repository list (`GET /api/maven/repositories?q=&tag=`, an in-memory filter over repository
names and tags), the statistics phrase search and the audit `q` search. The overview search in
the dashboard filters the repository list on the client. A user who knows `commons-lang3` but
not the repository it lives in has to open repositories one by one.

The goal is a Nexus-style search across all Maven repositories of the instance, including
proxy repositories, with filters, sorting and paging, that never shows content the caller may
not browse.

Product decisions this design holds (made by the maintainers, not reopened here):

- Search is recursive over all Maven repositories, with filter options.
- The default backend stores its index in tables of the existing database. No extra service is
  needed. An external engine (OpenSearch, Elasticsearch, Solr) is optional later, as a plugin
  behind the same interface (section 7).
- Mirror and proxy cache copies are searchable by default, next to hosted artifacts. Every
  result states where it comes from (section 2.4).
- The feature ships in 1.3.0.

User-visible features:

| Feature | Behaviour |
|---------|-----------|
| Search box and results page | Route `/search` in the dashboard, search box on top, results below, full query in the address (shareable). |
| Free text | Matches groupId prefix, artifactId prefix or exact version, per token (section 4.2). Coordinates form `group:artifact:version` is accepted. |
| Filter: repository | Repeatable. |
| Filter: groupId | Prefix, case-insensitive. |
| Filter: artifactId | Prefix, case-insensitive. |
| Filter: version | Exact, or prefix with `versionPrefix`. Version ranges are not part of v1. |
| Filter: classifier | Exact, repeatable (`sources`, `javadoc`, none). |
| Filter: extension | Repeatable. Packaging is not indexed in v1, so `extension` is the proxy for it (`jar`, `pom`, `war`, `module`). A real packaging filter needs POM parsing and is a later item (section 11). |
| Filter: release or snapshot | `snapshot=true`, `false` or `all` (default). |
| Filter: origin | `hosted`, `mirror` or `all` (default). Narrowing only. |
| Filter: last modified | `modifiedAfter`, `modifiedBefore` (ISO-8601 instants). |
| Filter: size | `sizeMin`, `sizeMax` in bytes. |
| Filter: checksum | `sha1` exact, finds only rows whose checksum is populated (section 11, Q-6). |
| Sort and paging | `groupId`, `artifactId`, `version`, `modified`, `size`; ascending or descending; 0-based pages, size 20 by default, at most 100. |
| Result rows | One row per GAV (group, artifact, version): the repositories it lives in with their origin, the latest version of the artifact, the number of files, the last modification. |
| Facets | Counts per repository, extension, classifier, origin and snapshot state, at most 20 buckets each, computed on what the caller may see. |
| Files of a GAV | A row expands into its files (path, size, date, checksum, origin). |
| Deep links | Every GAV and every file links to the existing `/browse` route. |
| Source of cached copies | A result that is a mirror copy says "cached from <proxy repository>" and, when known, the upstream host. |
| Index status | Managers see state, lag, last rebuild and run history in the Maintenance page and can start a rebuild or a verify run. |

### 1.2 Non-goals

- Relevance ranking. Results are sorted by an explicit key, never by score.
- Searching inside files (class names, POM text, javadoc). Only coordinates and file properties.
- Reading POM files. Name, description, packaging and dependencies are not indexed in v1.
- Version ranges and "newer than" filters.
- Infix and word matching (`core` finding `spring-core`). Prefix matching only, see section 4.2.
- Changing any existing endpoint, any existing table or the plugin event contract.
- Search over non-Maven content, javadoc archives and the statistics or audit data.
- Cross-instance search. One instance searches its own repositories.

### 1.3 Relationship to other documents

| Document | What this design takes from it |
|----------|--------------------------------|
| Identity model (`2026-10-15-identity-model.md`) | The permission rules (sections 5.2 to 5.5), the legacy matching quirks, the concurrency guard for table creation (4.3), the style of the admin API (7) and of the test list (10). |
| Kubernetes HA design (`2026-08-08-kubernetes-ha-design.md`) | "Locks belong in the database": every coordination here is a row in the database, never a JVM lock or an in-memory queue. The statistics buffer (Stage 1.3) is the anti-pattern this design avoids: an in-memory buffer loses data on every rolling update. |
| Cleanup policies (`cleanup/`) | The daily schedule model (`HH:mm`, claimed by a key `name:<UTC date>`), the runs table with statuses `SUCCEEDED`, `PARTIAL`, `FAILED` and triggers `MANUAL`, `SCHEDULED`. |
| ADR-INGT-0001 (accepted) | `PermissionEvaluator` is the single source of permission decisions. The row filter of section 5 is derived from it and pinned to it by a parity test. |

## 2. Data model

All tables are created with Exposed `SchemaUtils` on start, like the other stores, under the
same concurrency guard as the identity tables (section 9.1). Names are lower case with the
prefix `search_`. Nothing existing is altered, except one nullable column on
`mirror_cache_entry` (section 2.4).

### 2.1 Granularity: one row per file

The index has one row per stored file, in `search_artifact`. A GAV (a Maven artifact version)
is not stored. The result list groups files into GAVs at query time with `GROUP BY` over the
coordinate columns (section 4.3).

Why per file and not per GAV:

- Every filter the users asked for is a property of a file: extension, classifier, size,
  modification time, checksum. A GAV table would need to duplicate those as arrays or JSON,
  which no dialect indexes portably.
- A write is a single file. Deploying `foo-1.0-sources.jar` touches exactly one row, with no
  read-modify-write of an aggregate. That keeps replays idempotent (section 3.3).
- Deleting a file or a whole directory removes rows by path, without recomputing groups.

The price is a larger table: about eight rows per GAV on a typical installation (jar, pom,
module, sources, javadoc, and their signatures). Sizing is in section 4.7.

### 2.2 Path rules: from a repository path to coordinates

The coordinates come from the directory structure and are authoritative. The path inside a
repository (no repository name, no leading slash) is split into segments
`s[0] ... s[n-1]`.

| Part | Rule |
|------|------|
| Minimum | `n >= 4`: at least one group segment, artifact directory, version directory, file. Anything shorter is not a row. |
| `version` | `s[n-2]`, the version directory. |
| `artifactId` | `s[n-3]`. |
| `groupId` | `s[0] ... s[n-4]` joined with `.`. |
| `snapshot` | the version directory ends with `-SNAPSHOT` (case-sensitive, as Maven does). |
| Not a row | file names that start with `maven-metadata` (the helper `isMetadataOrChecksumFileName` of the mirror ledger, reused so the two agree), checksum files (`.md5`, `.sha1`, `.sha256`, `.sha512`, which also covers `.asc.sha1`). |
| Row | everything else under a version directory that matches the file name rule below, including signature files (`.asc`). |

The file name must start with `<artifactId>-<fileVersion>`, where `fileVersion` is:

- for a release: the version directory;
- for a snapshot: either the version directory (`foo-1.0-SNAPSHOT.jar`) or the version with
  `-SNAPSHOT` replaced by a deployment timestamp (`foo-1.0-20260101.120000-3.jar`). The
  timestamp is `yyyyMMdd.HHmmss-<build number>` and is matched with
  `\d{8}\.\d{6}-\d+`. The `version` column keeps the directory name (`1.0-SNAPSHOT`), so all
  timestamped builds of one snapshot group under one GAV.

A file whose name does not start with that prefix is not a row. It is counted as `unparsed` in
the rebuild statistics. This keeps stray files (a `README`, an editor backup) out of the
results rather than guessing coordinates for them.

The remainder `R` after the prefix is split into classifier and extension:

```
R starts with "."   -> classifier = null, extension = R without the leading dot
                       (so "foo-1.0.tar.gz" has extension "tar.gz", "foo-1.0.pom.asc" has "pom.asc")
R starts with "-"   -> T = R without the leading dash
                       1. if T ends with ".asc": peel it, remember "asc"
                       2. if T ends with a compound extension (tar.gz, tar.bz2, tar.xz, tar.zst):
                          extension = that, classifier = the rest before the dot
                       3. otherwise extension = text after the last dot, classifier = text before it
                       4. if "asc" was peeled: extension = extension + ".asc"
otherwise           -> not a row (no extension)
```

Examples, which become the table-driven unit test (task 3):

| Path inside the repository | groupId | artifactId | version | classifier | extension | snapshot |
|----------------------------|---------|------------|---------|------------|-----------|----------|
| `org/apache/commons/commons-lang3/3.14.0/commons-lang3-3.14.0.jar` | `org.apache.commons` | `commons-lang3` | `3.14.0` | none | `jar` | no |
| `.../commons-lang3-3.14.0-sources.jar` | same | same | same | `sources` | `jar` | no |
| `.../commons-lang3-3.14.0-sources.jar.asc` | same | same | same | `sources` | `jar.asc` | no |
| `.../commons-lang3-3.14.0.pom.asc` | same | same | same | none | `pom.asc` | no |
| `com/example/app/1.0/app-1.0-bin.tar.gz` | `com.example` | `app` | `1.0` | `bin` | `tar.gz` | no |
| `com/example/app/1.0/app-1.0.tar.gz` | `com.example` | `app` | `1.0` | none | `tar.gz` | no |
| `com/example/app/1.0/app-1.0-jdk1.8.jar` | `com.example` | `app` | `1.0` | `jdk1.8` | `jar` | no |
| `com/example/app/1.0-SNAPSHOT/app-1.0-20260101.120000-3.jar` | `com.example` | `app` | `1.0-SNAPSHOT` | none | `jar` | yes |
| `com/example/app/1.0-SNAPSHOT/app-1.0-20260101.120000-3-sources.jar` | `com.example` | `app` | `1.0-SNAPSHOT` | `sources` | `jar` | yes |
| `com/example/app/1.0-SNAPSHOT/app-1.0-SNAPSHOT.jar` | `com.example` | `app` | `1.0-SNAPSHOT` | none | `jar` | yes |
| `junit/junit/4.13.2/junit-4.13.2.jar` | `junit` | `junit` | `4.13.2` | none | `jar` | no |
| `com/example/app/1.0/app-1.0.jar.sha1` | not a row (checksum) | | | | | |
| `com/example/app/maven-metadata.xml` | not a row (too short, metadata) | | | | | |
| `com/example/app/1.0/notes.txt` | not a row (prefix mismatch, counted as unparsed) | | | | | |

Known limits of the rule, accepted for v1: a classifier that itself ends in a compound
extension spelling (`foo-1.0-x.tar.gz` is read as classifier `x`, extension `tar.gz`), and a
file layout that does not follow the Maven repository layout (a flat `libs/foo.jar`) is not
searchable. Both are visible in the `unparsed` counter, so an operator can see how many files
the index ignores.

### 2.3 `search_artifact`

```
search_artifact
  id              bigint pk, auto-increment
  repository      varchar(128)   binary collation          repository name
  path            varchar(512)   binary collation          path inside the repository, no leading slash
  path_lc         varchar(512)   binary collation          lower-cased path (legacy route matching, section 5.3)
  path_hash       char(64)       SHA-256 hex of repository + NUL + path
  group_id        varchar(255)
  group_id_lc     varchar(255)   binary collation
  artifact_id     varchar(255)
  artifact_id_lc  varchar(255)   binary collation
  version         varchar(255)
  version_lc      varchar(255)   binary collation
  classifier      varchar(255) null
  extension       varchar(64)    lower-cased
  snapshot        boolean        directory name ends with -SNAPSHOT
  origin          varchar(8)     HOSTED | MIRROR
  source_host     varchar(255) null   upstream host of a MIRROR row, null otherwise or when unknown
  size_bytes      bigint
  last_modified   bigint         epoch milliseconds, UTC
  sha1            char(40) null
  sha256          char(64) null
  indexed_at      bigint         epoch milliseconds, UTC, set by every upsert

  unique (repository, path_hash)
```

Remarks:

- **Time columns are epoch milliseconds.** `mirror_cache_entry` and the settings revision use
  the same representation. It avoids timezone and `TIMESTAMP` precision differences between
  SQLite, MariaDB, MySQL, PostgreSQL and H2. The API renders them as ISO-8601 instants.
- **`path_hash` is the upsert key.** A unique index on the 64-character hash is narrow and
  identical on every dialect, and the hash includes the repository. The unique constraint is
  `(repository, path_hash)` so the repository stays the leading column for repository-scoped
  scans. The plain `path` stays for prefix deletes and for display.
- **No visibility column.** Whether a repository is PUBLIC, HIDDEN or PRIVATE is read from the
  current repository settings when a query is compiled (section 5). A visibility change
  therefore applies on the next request without a rebuild, and there is nothing in the index
  that can go stale in a way that leaks content.
- **`path_lc` is an addition to the column list of the ADR.** The legacy route matching is case
  insensitive (identity model 5.4), and a case-insensitive prefix on a deep path cannot use an
  index without a shadow column. The cost is one more 512-character column and one index.
- **`snapshot` is a column** because it is a filter and a facet. It is derived, not independent:
  `version` ends with `-SNAPSHOT`. Queries that group by `version_lc` therefore never aggregate
  it (`MAX` over a boolean does not exist on PostgreSQL); the snapshot flag of a GAV is read from
  its version string.

### 2.4 Mirror and proxy cache copies

A cached copy lives in the same storage and at the same path as a hosted artifact. The only
marking is `mirror_cache_entry` in the database: one row per version directory
(repository, path, `cached_at`, `last_accessed_at`). A directory has a row only while it holds
nothing but cached content; a deployment into it removes the row.

The index mirrors that marking per version directory:

- `origin = MIRROR` for every file in a version directory that has a ledger row, `HOSTED`
  otherwise. A cached copy is an ordinary row. It is found, filtered, faceted and linked like
  any other.
- Mirror rows are returned by default, together with hosted rows. `origin=hosted` and
  `origin=mirror` narrow the result. This follows the maintainers' decision and overrules the
  default-off recommendation of the ADR (section 12).
- Search shows what **this instance holds**. A proxy repository that has never fetched
  `com.example:app:1.0` has no row for it, and search will not find it. The UI says so on the
  empty state of a query that matches only mirror repositories (section 8.3).
- Visibility is the visibility of the repository the copy lives in. A cached copy in a PRIVATE
  proxy repository is visible to exactly the callers who may browse that path, nothing more
  (section 5).

**Where the copy came from.** The result names the proxy repository (the `repositories` entry
with `origin: MIRROR`), and the UI writes "cached from <repository>". The upstream host is a
second, optional piece of information. The code does not record it today: `MirrorCacheEntryTable`
has the columns `repository`, `path`, `cached_at`, `last_accessed_at`, and
`MirrorService.putMirroredFile` does not receive the host. The host is known one frame above,
where `searchInRemoteRepositories` loops over the eligible hosts and calls the fetch lambda with
the `host` that answered. The smallest change that records it:

1. Add a nullable column `source_host varchar(255)` to `mirror_cache_entry`. It is additive:
   existing rows get null, nothing is rewritten. `SchemaUtils.create` does not add columns to an
   existing table, so the ledger init uses the missing-columns variant that the other stores use
   for added columns (to be confirmed against the Exposed 1.3.0 API in task 9), guarded by the
   same concurrency guard as the other table creations.
2. Pass the host through `putMirroredFile(repository, gav, data, host)` and
   `MirrorCacheService.recordStored(..., sourceHost)` to `insertIfAbsent`. Both fetch lambdas
   (`fetchFromRemoteHosts` and `fetchAndStoreFromRemoteHosts`) already have `host` in scope.
3. Store the host name only (`URI(host).host`, plus the port when it is not the default), never
   the path, query or user info, truncated to 255 characters. Credentials of a mirror live in its
   `authorization` setting and never enter the ledger or the index.

Consequences and limits, stated so nobody reads more into the field than it holds:

- The host is that of the **first** fetch that created the directory row (`insertIfAbsent` keeps
  the first row). Later files in the same directory fetched from another host keep the first
  host. It is provenance of the version directory, not of every file.
- Rows cached before 1.3.0 have no host. The field is null and the UI shows only the repository.
- Exposure: the host of a mirror is configuration. For callers who are not signed in it is not
  returned (the field is null for anonymous requests); signed-in callers see it on the files
  endpoint. Repository names are returned as for browse.

The index copies `source_host` from the ledger when it writes a MIRROR row, and
`REORIGIN_DIRECTORY` (section 3.4) rewrites `origin` and `source_host` together.

### 2.5 `search_outbox`

```
search_outbox
  id             bigint pk, auto-increment
  repository     varchar(128)   binary collation
  path           varchar(512)   binary collation     file path, or directory path for the two directory ops
  op             varchar(24)    CHANGED_PATH | REMOVED_PREFIX | REORIGIN_DIRECTORY
  created_at     bigint         epoch milliseconds
  lease_owner    varchar(64) null
  lease_until    bigint null    epoch milliseconds
  attempts       int            default 0
  last_error     varchar(512) null
```

The outbox is a durable queue (section 3.2). Writers only insert; the workers claim and delete.

### 2.6 `search_rebuild_run`

```
search_rebuild_run
  id                  bigint pk, auto-increment
  kind                varchar(8)    REBUILD | VERIFY
  trigger             varchar(12)   MANUAL | SCHEDULED | STARTUP | GAP
  status              varchar(12)   RUNNING | COMPLETED | FAILED | CANCELLED
  started_at          bigint
  finished_at         bigint null
  lease_owner         varchar(64) null
  lease_until         bigint null
  run_started_at      bigint        cut-off for the stale-row cleanup (section 3.6)
  repositories        varchar(2048) JSON array of names, or "*"
  cursor_repository   varchar(128) null
  cursor_path         varchar(512) null    opaque provider cursor, not necessarily a path
  processed           bigint        entries read from storage
  upserted            bigint
  deleted             bigint
  unparsed            bigint
  skipped_too_long    bigint
  errors              bigint
  last_error          varchar(512) null
```

Rebuild runs and verify runs share this table. A column `kind varchar(8)` (`REBUILD` or
`VERIFY`) tells them apart, and the DDL above lists it first among the descriptive columns. A
verify run uses the same trigger names (`MANUAL`, `SCHEDULED`) and the same statuses; its
counters mean "rows checked" (`processed`), "rows fixed" (`upserted`) and "rows deleted"
(`deleted`). The history in the Maintenance panel is the last 20 rows of either kind.

### 2.7 `search_state`

```
search_state
  key           varchar(64) pk
  value         varchar(512) null
  updated_at    bigint
  lease_until   bigint null
```

Keys:

| Key | Meaning |
|-----|---------|
| `heartbeat` | written by the worker of any replica once a minute (section 3.7) |
| `last_full_rebuild` | epoch milliseconds of the last COMPLETED rebuild, absent if none |
| `rebuild.active` | run id of the live rebuild, with `lease_until`: the single-run lock (section 3.6) |
| `verify.active` | the same for a verify run |
| `dropped_enqueues` | counter of enqueue failures since the last completed rebuild |

### 2.8 Limits and where they bite

| Column | Limit | If exceeded |
|--------|-------|-------------|
| `repository` | 128 characters (the limit of the mirror ledger) | all files of that repository are skipped, counted in `skipped_too_long`, shown as a warning in the status |
| `path`, `path_lc` | 512 characters (the limit of the mirror ledger) | the file is skipped and counted in `skipped_too_long` |
| `group_id`, `artifact_id`, `version`, `classifier` | 255 characters | the file is skipped and counted |
| `extension` | 64 characters | the file is skipped and counted |

The skip is explicit and counted, never a truncation: a truncated coordinate would index a file
under a wrong name.

### 2.9 Indexes

| Index | Columns | Serves |
|-------|---------|--------|
| unique | `(repository, path_hash)` | upsert target, lookups by path |
| `ix_search_gav` | `(group_id_lc, artifact_id_lc, version_lc)` | groupId prefix, artifactId within group, `GROUP BY` of the result list, latest version per artifact |
| `ix_search_artifact` | `(artifact_id_lc)` | artifactId prefix without a groupId |
| `ix_search_version` | `(version_lc)` | exact version |
| `ix_search_repo_origin` | `(repository, origin)` | repository and origin filters, facets |
| `ix_search_sha1` | `(sha1)` | checksum lookup |
| `ix_search_modified` | `(last_modified)` | date range, sort by modified |
| `ix_search_extension` | `(extension)` | extension filter and facet |
| `ix_search_path` | `(repository, path)` | directory deletes, the new-model path prefix of grants |
| `ix_search_path_lc` | `(repository, path_lc)` | the legacy case-insensitive path prefix of grants |

Outbox: `(lease_until, id)` for claiming and `(repository, path)` for the rebuild check.

**Dialect notes.**

- **Collation.** Range queries on a text column only equal prefix matching when the collation
  orders by code point. MySQL and MariaDB default to case- and accent-insensitive collations
  (`utf8mb4_unicode_ci`, `utf8mb4_general_ci`), under which `a` equals `A` and `e` equals `é`;
  PostgreSQL with `en_US.UTF-8` orders by locale and ignores punctuation at the first level.
  Both break `x >= 'ab' AND x < 'ac'` as a prefix test. The columns marked "binary collation"
  are therefore declared per dialect: `utf8mb4_bin` on MySQL and MariaDB, `COLLATE "C"` on
  PostgreSQL, nothing on SQLite (`BINARY` is the default) and H2 (case-sensitive by default).
  The DDL is emitted by the table definition, not left to the database default.
- **MySQL and MariaDB index key length.** InnoDB with the `DYNAMIC` row format allows 3072 bytes
  per index key; `utf8mb4` counts 4 bytes per character. `(repository, path)` is
  `(128 + 512) * 4 = 2560` bytes and `(repository, path_lc)` the same, both below the limit.
  This is why `repository` is capped at 128 and not 255: with 255 the key would be 3068 bytes,
  too close to the limit to survive a column change. `ix_search_gav` is
  `(255 * 3) * 4 = 3060` bytes, which is under the limit but only just. If the dialect test
  (section 10.3) shows `Specified key was too long` on an older server, the fallback is a
  prefix index on `version_lc` (`version_lc(128)`), which is safe because the query adds an
  exact comparison on the full column afterwards. The test creates the schema on MariaDB and
  MySQL containers to prove this before release.
- **PostgreSQL.** Indexes are plain B-trees. Because the columns are `COLLATE "C"`, the
  default operator class supports the range predicates. No `text_pattern_ops` is needed.
- **SQLite.** A single connection (pool size 1) means every index costs write time on the one
  connection that deploys also use. The index count is a trade-off; see section 4.7.
- **H2.** Compares strings by UTF-16 code unit, so characters above U+FFFF sort differently
  from code point order. H2 is a test and experimental dialect; the effect is limited to
  coordinates that contain such characters.

### 2.10 Case-insensitive prefix and exact match

The `_lc` columns hold the lower-cased value (`String.lowercase(Locale.ROOT)`). A prefix `p`
(already lower-cased by the caller) is matched with a half-open range, not with `LIKE`:

```
group_id_lc >= :p AND group_id_lc < :next
```

where `next` is `p` with its last code point incremented by one (`com.exa` becomes `com.exb`;
a last code point of U+10FFFF is dropped and the previous one incremented). This is the same
statement on every dialect and uses the B-tree directly. `LIKE 'p%'` does not: SQLite uses an
index for `LIKE` only when `case_sensitive_like` is configured to match the column collation,
MySQL treats the pattern through the collation, PostgreSQL needs `text_pattern_ops` or the C
collation for it, and H2 and escape handling (`%` and `_` inside a coordinate) differ. The
range form needs no escaping at all, so a coordinate containing `_` or `%` cannot change the
meaning of the query.

Exact matches (a version, a classifier filter on the lower-cased column) compare the same
lower-cased column with `=`. The original case is returned to the client, taken from the
mixed-case column (`MIN(group_id)` in grouped queries).

## 3. Indexing

### 3.1 What keeps the index current

Four mechanisms, from cheap to expensive:

1. **Events and direct calls** enqueue small rows into `search_outbox` (this section).
2. **Workers** on every replica drain the outbox and write `search_artifact` (3.2, 3.3).
3. **Verify**, a sampled nightly comparison with storage (3.8).
4. **Rebuild**, a full resumable walk of storage per repository (3.6), on first start, after a
   detected gap, on request and optionally on a schedule.

### 3.2 Durable outbox, not an in-process queue

ADR-INGT-0002 proposes an in-process write queue. This design uses a table instead
(deviation D-1, section 12).

An in-process queue loses its content when the process stops. A rolling update stops every
pod, so every deploy accepted in the last seconds before the stop would be missing from the
index until the next rebuild. That is the same failure as the statistics buffer, which is not
flushed on shutdown (HA design 1.3). The index would need a compensating rebuild after every
restart. A queue that survives the pod and is visible to every replica removes the problem
instead of repairing it.

How it works:

- **Writers insert only.** A deploy, delete, mirror store or prune inserts one small row
  (`repository`, `path`, `op`, `created_at`) in its own short transaction. No index write
  happens on the request thread. On SQLite with a pool of one connection an insert is a
  millisecond statement; the expensive work (reading storage, writing index rows) happens in
  the worker.
- **Workers run on every replica.** Each replica drains the same table. They claim batches with
  a lease (3.4), so a row is processed by one worker at a time, and a crashed worker's rows are
  picked up by another replica after the lease expires.
- **The database is already a hard dependency of the request path** (tokens, settings, the
  mirror ledger). Inserting into it adds no new failure mode.

The design avoids an in-memory result or queue cache of any kind. Search requests read the
database directly, so every replica answers from the same state.

### 3.3 The outbox carries paths, not state

An entry says "look at this path again", never "this is what the file looks like". The worker
re-reads storage for the path and makes the index agree:

```
process(CHANGED_PATH repo, path P):
  coords = parse(P)                          # section 2.2
  if coords is null:                         # not a row (metadata, checksum, stray file)
      delete row by path_hash (no-op if absent); return
  details = storage.getFileDetails(P)
  if not found or is a directory:
      delete row by path_hash; return
  origin, host = ledger lookup for the version directory of P     # HOSTED if no row
  sha1, sha256 = read sidecar P.sha1 / P.sha256 (at most 128 bytes) if present, else null
  upsert row(size = details.size, last_modified = details.mtime, origin, host, sha1, sha256,
             indexed_at = now)
```

Because the worker reads the current state, three things hold:

- **Replays are idempotent.** Processing the same entry twice, or after a crash between the
  upsert and the delete of the entry, gives the same row.
- **Ordering across replicas does not matter.** Two entries for one path processed by two
  replicas in either order both end with the state storage has at that moment. The only
  ordering that can matter is a delete racing a re-deploy, which is covered in 3.5.
- **A burst collapses.** Entries for the same (repository, path, op) in one claimed batch are
  processed once and all their ids deleted. A deploy with its checksum sidecars produces up to
  five entries for one row and costs one storage read.

`REMOVED_PREFIX` is processed as follows, with `cutoff = now()` taken before the storage check:

```
process(REMOVED_PREFIX repo, path D):
  cutoff = now
  if storage has nothing at D and nothing under D/:
      delete rows where repository = repo
                    and (path = D or (path >= D + "/" and path < D + "0"))
                    and indexed_at <= cutoff                # batched by id, 1000 per statement
  else:
      for each row under D (batched): re-check existence, delete the missing ones
```

(`"0"` is the code point after `"/"`, which turns the prefix `D/` into a half-open range.) The
`indexed_at <= cutoff` condition closes the delete-versus-redeploy race: a row written by a
concurrent re-deploy after the check has a newer `indexed_at` and survives. It assumes the
clocks of the replicas differ by less than the time between the check and the delete; a larger
skew can resurrect or lose a row until the next verify (the clock-skew assumption of 3.4).

`REORIGIN_DIRECTORY` rereads the ledger entry of directory `D` and sets `origin` and
`source_host` on all rows below `D`:

```
UPDATE search_artifact SET origin = :o, source_host = :h
 WHERE repository = :r AND path >= :D/ AND path < :D0
```

### 3.4 Claiming with a lease

The claim uses the pattern that already exists in this code base for single-winner decisions
(`insertIgnore` on a primary key in `scheduled_job_claim`, a conditional `UPDATE` on the
settings revision), extended with an expiry, which `scheduled_job_claim` lacks:

```
claim(batchSize):
  now = clock.millis()
  ids = SELECT id FROM search_outbox
         WHERE attempts < 10 AND (lease_until IS NULL OR lease_until < :now)
         ORDER BY id LIMIT :batchSize                      # candidates only, no lock taken
  claimed = []
  transaction:
    for id in ids:
        n = UPDATE search_outbox
               SET lease_owner = :me, lease_until = :now + :leaseMs, attempts = attempts + 1
             WHERE id = :id AND (lease_until IS NULL OR lease_until < :now)
        if n == 1: claimed += id                           # the affected row count decides
  return claimed
```

- **No `UPDATE` with a subquery on the same table.** MySQL and MariaDB reject
  `UPDATE t SET ... WHERE id IN (SELECT id FROM t ...)`. Selecting candidates first and then
  updating row by row works on every dialect, and the conditional `WHERE` makes the update the
  arbiter: if two workers selected the same id, one of them updates zero rows.
- **`attempts` is incremented at claim time**, so an entry that crashes the process still counts.
  After 10 attempts the entry is skipped by the claim query and counts as a dead letter in the
  status (section 4.8). The next verify or rebuild repairs the row it describes.
- **Success deletes the entry** (`DELETE ... WHERE id = :id AND lease_owner = :me`). If the
  delete affects zero rows the entry was taken over after an expired lease; the work was
  idempotent, so nothing is lost.
- **Failure releases with backoff**: `lease_until = now + min(2^attempts * 5 s, 10 min)`,
  `last_error` set (truncated to 512 characters).
- **Lease length and heartbeat.** The lease is 60 seconds. A worker that is still busy extends
  the lease of its claimed ids every 20 seconds with a conditional update
  (`WHERE lease_owner = :me AND id IN (...)`). If the update affects fewer rows than ids, some
  entries were taken over and the worker drops them from its batch.
- **Leases are an efficiency measure, not a correctness requirement.** Because processing is
  idempotent and path based (3.3), a duplicate processing caused by an expired lease, a long GC
  pause or a clock error costs a repeated storage read and nothing else. This is the difference
  to `scheduled_job_claim`, which has no lease, no expiry and no fencing, where a claimant that
  dies skips the day. No fencing token is needed here.
- **Clock-skew assumption.** `lease_until` is compared with the clock of the claiming replica.
  Replicas are assumed to agree within a few seconds (NTP, the normal state of a cluster). With
  a skew larger than the lease a live lease looks expired to the other replica, which then
  processes the entry too; the result is repeated work, not a wrong index. All times come from
  an injected `Clock`, which is also what makes the transitions testable.
- **Polling.** The worker polls every 2 seconds while the previous poll found work, and backs
  off to 10 seconds when idle. A poll is one indexed query. The batch is 200 entries.
- **Transactions stay short.** The worker reads storage outside any transaction, then writes
  its batch of upserts in one transaction (at most 200 rows). On SQLite, where the pool holds a
  single connection, this is what keeps request threads from waiting on the worker.

### 3.5 Hooks: where entries come from

The event bus dispatches by exact event class and has no exception isolation: a listener that
throws breaks the dispatch to later listeners and the caller (the deploy request). The index
listeners therefore catch `Throwable`, log, count the failure in `dropped_enqueues` and return.
They never throw.

`ArtifactIndexSink` is the internal interface that call sites use. It has three methods:
`changed(repository, path)`, `removed(repository, path)` and `reorigin(repository, directory)`.
Each inserts one outbox row. When search is disabled the sink is a no-op.

| Write path | What happens today | Hook | Entry |
|------------|--------------------|------|-------|
| Deploy of a file (`RepositoryService.deploy`, L86-89) | `putFile`, then `DeployEvent` | listener on `DeployEvent` | `CHANGED_PATH` of the path; a checksum path maps to its base file, a metadata path is ignored |
| Delete (`deleteUnchecked`, L119-129) | `removeFile`, then `DeleteEvent` | listener on `DeleteEvent`, also when `cleanupRunId` is set | `REMOVED_PREFIX` of the path (a file or a directory) |
| Checksum sidecars (`.md5`, `.sha1`, `.sha256`, `.sha512`, L92-101) | written after the event, no event | direct `sink.changed` after the sidecar write, for the base file | `CHANGED_PATH` of the base file, so `sha1` is picked up |
| POM generated by `generatePom` (`MetadataService`, `putFile` of the `.pom`) | written with no event | direct `sink.changed` after the write | `CHANGED_PATH` |
| Mirror cache write (`MirrorService.putMirroredFile`) | `putFile`, then `recordStored` in the ledger, no event | direct `sink.changed` **after** `recordStored` | `CHANGED_PATH` |
| Deploy into a mirror-cached directory (`MavenFacade` L73, `forgetVersion`) | ledger row removed | direct `sink.reorigin` after the ledger delete | `REORIGIN_DIRECTORY` |
| Cleanup of cached copies and releases | `deleteUnchecked` with `cleanupRunId` | covered by the `DeleteEvent` listener | `REMOVED_PREFIX` |
| Snapshot pruning (`PreservedBuildsListener`) | `removeFile` directly, no event | direct `sink.removed` | `REMOVED_PREFIX` |
| Metadata writes and reconciles (`MetadataService`) | `maven-metadata.xml` and its checksums | none: not a row | none |

Notes on the table:

- **`generatePom` is a silent artifact write.** It stores a `.pom` file under the version
  directory with no event, so without a hook a generated POM would be missing from the index.
- **Order of calls at the mirror write.** The sink call comes after `recordStored` so that the
  worker, which reads the ledger when it processes the entry, sees the row and indexes the file
  with `origin = MIRROR` and the recorded host.
- **Snapshot pruning calls the sink directly, not through `DeleteEvent`.** Emitting
  `DeleteEvent` from `PreservedBuildsListener` would be the cleaner model, but it changes
  behaviour that other code relies on: the audit listener would start recording pruned builds
  (it skips events with a `cleanupRunId`, and pruning has none), javadoc cache eviction would
  run, and third-party plugins listening to `DeleteEvent` would see events they never saw. A
  direct call to the index sink leaves the public plugin event contract and the audit output
  exactly as they are. If a later change
  wants pruning in the audit log, it can emit the event and drop the direct call in one commit.
  The pruning listener also rethrows when the metadata update fails after the file was removed;
  the sink call is placed before that rethrow so the index is told even on that path.
- **Ordering caveats.** `DeployEvent` fires before the checksum sidecars are written, and
  `DeleteEvent` fires before the metadata is reconciled. A worker may process the deploy entry
  before the sidecar exists and index the row with `sha1 = null`. The sidecar write enqueues a
  second `CHANGED_PATH` for the same row, which fills the checksum in. The path-based outbox
  makes both orders end in the same state, so the caveat is harmless.
- **Redeployment.** An overwrite of an existing non-metadata file is refused with 409 unless the
  repository allows redeployment. When it is allowed, the new `size`, `last_modified` and
  checksum replace the old row on the upsert.
- **A failed enqueue** (database error) is logged, counted and does not fail the deploy. The
  index misses that change until the next verify or rebuild, and the status shows `DEGRADED`
  with the counter. This is the same trade-off as any best-effort index; the rare case is
  repaired rather than blocking uploads.

### 3.6 Rebuild

A rebuild reads storage and writes the index. It exists for the first start, for gaps, for
recovery and for operators who want certainty.

**Storage access.** The existing `StorageProvider.getFiles` lists one directory level, and
`walkFiles` (`StorageWalk.kt`) calls `getFileDetails` per directory. On S3 that is a
`ListObjectsV2` with a delimiter per directory plus a `HEAD` and a `LIST` for the details: about
two sequential calls per directory, and each directory is read fully into memory. For a bucket
with 800 000 objects that is a long walk and a large number of requests.

The rebuild needs a flat listing with size and modification time in the response:

```kotlin
interface StorageEntrySource {
    /** Entries below [prefix] in the provider's own total order, strictly after [after]. */
    fun listEntries(prefix: Location, after: String?, limit: Int): Result<StorageEntryPage, ErrorResponse>
}
data class StorageEntry(val path: Location, val size: Long, val lastModified: Instant)
data class StorageEntryPage(val entries: List<StorageEntry>, val next: String?)   // next = null at the end
```

- **S3:** one `ListObjectsV2` per page under the repository prefix **without** a delimiter,
  `MaxKeys = limit` (at most 1000), `StartAfter = after`. Each response carries `Size` and
  `LastModified` per key, so no `HEAD` per file is needed: one request per 1000 files. The
  repository prefix includes the shared-bucket prefix rule (`S3StorageProviderSettings`).
- **File system:** a depth-first walk with children sorted, so the order is deterministic.
  Size and modification time come from the attributes read during the walk. Resuming after a
  cursor skips the siblings that sort before the cursor path at each level, which costs a
  `readdir` per level and not a rescan.
- The interface is separate from `StorageProvider` and implemented by the two built-in
  providers. A third-party provider that does not implement it is wrapped in a
  `WalkingEntrySource` that uses `walkFiles`, slow but correct. This keeps the plugin contract
  unchanged. The build passes `-jvm-default=no-compatibility`, so a default method on
  `StorageProvider` would also have been binary compatible; the separate interface is chosen
  because the S3 and file system semantics differ enough that a shared default would hide the
  cost.
- The cursor is opaque. Each provider defines its own order and returns its own token (a key for
  S3, a path for the file system). The engine stores it as `cursor_path` and hands it back
  unchanged.

**Algorithm.**

```
rebuild(run):                                    # run holds the single-run lease
  for repo in repositories(run) sorted by name, from run.cursor_repository:
      ledger = mirror ledger entries of repo, read page by page by version directory
      after  = (repo == run.cursor_repository) ? run.cursor_path : null
      loop:
          page = source.listEntries(repo.root, after, 500)
          rows = page.entries.map(parse + origin lookup)    # counts unparsed and too long
          pending = SELECT path FROM search_outbox WHERE repository = repo AND path IN (...)
          rows = rows without pending paths
          existing = SELECT rows by path_hash IN (...)       # to keep sha1 and sha256
          transaction:                                       # 500 rows, short
              upsert rows (keep sha1/sha256 if size and last_modified are unchanged, else null)
              UPDATE run SET cursor_repository, cursor_path = page.next, counters,
                             lease_until = now + leaseMs     # heartbeat in the same transaction
          after = page.next; if null: break
      delete rows where repository = repo and indexed_at < run.run_started_at      # batched by id
  delete rows of repositories no longer configured
  mark run COMPLETED; set last_full_rebuild
```

Properties:

- **Resumable.** The cursor is committed with every batch. A run that dies is resumed by the
  next claimant from `(cursor_repository, cursor_path)`.
- **Batches of 500 rows per transaction.** Short transactions keep SQLite's single connection
  available to request threads.
- **No `GET` per file.** The rebuild does not read files, so `sha1` and `sha256` are not
  computed. Rows that already exist keep their checksums while `size` and `last_modified` are
  unchanged; new rows get null. A manual option to read the sidecars during a rebuild is a later
  item (section 11).
- **Pending entries win.** The rebuild does not write a path that has a pending outbox entry:
  the entry will be applied by a worker with fresher information. The consequence is a
  documented race window: a delete that happens **while** the rebuild is past that path's
  position can be undone if the rebuild listed the file before the delete and wrote it after
  the delete's outbox entry was already consumed. The row then exists for a file that does not
  and stays until the next verify or rebuild. The window is as wide as one batch (500 files) plus
  the processing time of the entry, and only a delete issued in exactly that window is affected.
- **Stale rows go at the end.** After a repository completes, rows with `indexed_at` older than
  the run start in that repository are deleted: they were not seen in the walk, so the file is
  gone. Rows of repositories that no longer exist are deleted at the end of the run.
- **One run across all replicas.** The run is started by a conditional update on the
  `rebuild.active` row of `search_state`:

  ```
  UPDATE search_state SET value = :runId, lease_until = :now + :leaseMs, updated_at = :now
   WHERE key = 'rebuild.active' AND (lease_until IS NULL OR lease_until < :now)
  ```

  Exactly one replica sees one affected row. A second request while the lease is live is refused
  (HTTP 409 at the API). The single-row conditional update is atomic on every dialect, which a
  "count live runs after insert" check is not under snapshot isolation. The lease is 2 minutes
  and is extended in the same transaction as the cursor.
- **Resuming a dead run.** Every replica's worker loop checks, every 30 seconds, whether a run is
  `RUNNING` with an expired lease. The first replica whose conditional update wins takes the run
  over and continues from the cursor.
- **Cancel.** `CANCELLED` is set by a manager request; the worker checks the status between
  batches.

Statuses are `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`; triggers are `MANUAL`, `SCHEDULED`,
`STARTUP`, `GAP`. A repository that fails (storage unavailable) is recorded in `last_error`,
counted in `errors`, and the run continues with the next repository and ends `FAILED` if any
repository could not be completed. A failed repository's stale rows are not deleted.

### 3.7 Startup and gaps

On start, after the schema exists:

- **Empty index.** If `search_artifact` has no row and `last_full_rebuild` is absent, a
  `STARTUP` rebuild is queued and the state is `BUILDING`. This is the first boot after the
  upgrade. The server serves requests meanwhile; searches return what has been indexed so far
  and say so (section 4.8).
- **Heartbeat.** Every minute the worker of any replica writes `heartbeat`. On start, if rows
  exist and the heartbeat is absent, or older than 10 minutes, a `GAP` rebuild is queued. The
  reason is that Ingot 1.2 pods (during a rolling upgrade) or an installation that was
  temporarily downgraded wrote artifacts without indexing them.
- **Cost of the rule.** A single-node installation that is stopped for more than 10 minutes
  starts a rebuild on the next start, although nothing was missed. The rebuild runs in the
  background, keeps the index readable and costs one storage walk. The shared setting
  `gapRebuild` (default true) turns the rule off for operators who prefer to start rebuilds by
  hand.
- **Rolling upgrade.** While 1.2 and 1.3 pods run side by side, writes by 1.2 pods are not
  indexed. They are picked up by the next rebuild or by verify sampling. The release notes say
  so, and recommend a manual rebuild after the last 1.2 pod is gone (section 9.4).

### 3.8 Verify

The nightly verify job checks that the index agrees with storage for a sample of rows and
repairs what it finds:

```
verify(run):
  sample = rows chosen by a random start id and consecutive ids, size N (default 2000)
  for row in sample:
      details = storage.getFileDetails(row.path)
      if missing: delete the row                                  # missing from storage
      else if size or last_modified differ: enqueue CHANGED_PATH   # drift, repaired by a worker
      else if origin or source_host differ from the ledger: fix the row
  delete rows of repositories that no longer exist
  report drift = (missing + differing) / N
```

The random source is injected, so the test sample is fixed. The nightly job is claimed with
`scheduled_job_claim` under the key `search-verify:<UTC date>`. A manual verify is claimed
through the `verify.active` row (the same conditional update as the rebuild lock), so a manual
run and a scheduled run cannot overlap, which is the gap that manual cleanup runs have today.
Verify refuses to start while a rebuild is live.

A drift above 5 percent sets the state `DEGRADED` and shows a recommendation to rebuild in the
status. Verify never starts a rebuild by itself. The sample size and the run time are shared
settings (domain `search`, section 3.9). Verify catches rows for files that no longer exist and
rows whose properties changed. It does not find files missing from the index; only a rebuild
does.

### 3.9 Scheduling

There is no unconditional nightly full rebuild (deviation D-4). A full walk of a large S3
bucket is slow and costs requests, and the outbox already keeps the index current. What runs
automatically:

| Job | Default | Claim | Why |
|-----|---------|-------|-----|
| Outbox workers | always | lease per entry | keep the index current |
| Verify | on, 02:30 UTC, sample 2000 | `search-verify:<UTC date>` | detect drift cheaply |
| Full rebuild | manual only | `rebuild.active` lease | explicit cost |
| Scheduled rebuild | off; optional `rebuildSchedule` | `search-rebuild:<UTC date>` then `rebuild.active` | for operators who want a periodic guarantee |

The schedule lives in the shared settings domain `search`, modelled on the cleanup policy
schedule (`HH:mm`, once a day, claimed by a `name:<UTC date>` key):

```
search (shared settings domain)
  verifyEnabled      boolean   default true
  verifyAt           string    default "02:30"      UTC, HH:mm
  verifySampleSize   int       default 2000
  rebuildSchedule    { enabled: false, at: "03:30", everyDays: 7 }
  gapRebuild         boolean   default true
```

One difference from the cleanup scheduler, which starts a policy only when the current minute
equals `HH:mm` (a restart during that minute skips the day): the search scheduler starts a job
when the current time is **at or after** `at` and no claim exists for the UTC date. A scheduled
rebuild whose claimant dies is not lost either, because the claim only creates the run row; the
resume rule of 3.6 continues it.

Local configuration decides whether the feature exists at all, and shared settings decide how it
behaves (section 7.3).

### 3.10 Multiple replicas

- All replicas feed and drain the same tables. Search requests read the database directly with
  no in-memory result cache, so every replica returns the same results.
- The rebuild, the verify run and the nightly claim are single-winner through the database
  (3.6, 3.8).
- The outbox lease makes workers cooperate without a coordinator.
- **Stage 1 blockers of the HA design.** Three items there are still open: schema
  initialisation is not serialised (1.1), metadata writes are not serialised (1.2), and the
  statistics buffer is not flushed on shutdown (1.3). This design depends on the first one only:
  the search tables are created at start, and concurrent `CREATE TABLE IF NOT EXISTS` from
  several pods is what 1.1 describes. It is handled here by creating the tables under the same
  guard pattern as the identity tables (identity model 4.3: a marker row inserted with
  `insertIgnore` as the first statement, the second starter sees zero inserted rows), and it
  becomes unnecessary the day 1.1 lands. The design does not depend on 1.2: the index never
  writes metadata, and a lost metadata update does not change which files exist. It does not
  depend on 1.3: no counter of this feature is buffered in memory (the enqueue failure counter
  and the heartbeat are database rows).
- The in-memory structures the design adds are local caches of immutable facts only (the parsed
  `SearchAccess` of a request lives for the request). Nothing is cached across requests.

## 4. Query

### 4.1 Parameters

| Parameter | Type | Semantics |
|-----------|------|-----------|
| `q` | string, at most 200 characters | tokens, see 4.2 |
| `groupId` | string | prefix on `group_id_lc` |
| `artifactId` | string | prefix on `artifact_id_lc` |
| `version` | string | exact on `version_lc` |
| `versionPrefix` | string | prefix on `version_lc`; cannot be combined with `version` (400) |
| `repository` | repeatable | exact repository name; unknown or not permitted names simply match nothing |
| `extension` | repeatable | exact, lower-cased |
| `classifier` | repeatable | exact, case-sensitive; the value `-` selects files without a classifier |
| `sha1` | 40 hex characters | exact on `sha1` (lower-cased); matches populated rows only |
| `snapshot` | `true`, `false`, `all` | default `all` |
| `origin` | `hosted`, `mirror`, `all` | default `all` |
| `modifiedAfter`, `modifiedBefore` | ISO-8601 instant | on `last_modified`, after inclusive, before exclusive |
| `sizeMin`, `sizeMax` | bytes | inclusive |
| `sort` | `groupId`, `artifactId`, `version`, `modified`, `size` | default `groupId` |
| `order` | `asc`, `desc` | default `asc` |
| `page` | int, 0-based | default 0 |
| `size` | int | default 20, at most 100 |

All filters combine with AND. Repeated values of one parameter combine with OR. A filter on a
file property (extension, classifier, size, date, checksum) selects files; a GAV appears when
at least one of its files matches, and its `fileCount` counts the matching files. Unknown
parameters are ignored, invalid values return 400 with the parameter name.

Paging stops at `page * size + size <= 10 000`. A request beyond that returns 400 with the
message "Result window is limited to 10000 entries, narrow the filters". The total is computed
up to a cap of 10 001 groups: when more exist, `totalCapped` is true and `total` is 10 001.

### 4.2 Free text

`q` is split on whitespace into at most 5 tokens, lower-cased. Every token must match (AND). A
token matches a GAV when any of these holds:

- `group_id_lc` starts with the token;
- `artifact_id_lc` starts with the token;
- `version_lc` equals the token.

A token of the form `group:artifact[:version]` is a coordinates query: the parts are matched
against groupId (prefix), artifactId (prefix) and version (exact), each part optional
(`:commons-lang3`, `org.apache:`). Combining `q` with the explicit parameters ANDs them.

Prefix means the start of the whole value. `core` finds an artifact called `core-api` but not
`spring-core`. Infix and word matching need a tokenised index or trigrams, which are not
portable across the five dialects with plain B-trees; they are an open question (section 11)
and the first thing to measure after release.

### 4.3 Grouping and the result query

The result list is a `GROUP BY` over the coordinate columns, restricted to the permitted rows
(section 5) and the filters:

```sql
SELECT group_id_lc, artifact_id_lc, version_lc,
       MIN(group_id) AS group_id, MIN(artifact_id) AS artifact_id, MIN(version) AS version,
       COUNT(*) AS file_count, MAX(last_modified) AS last_modified, SUM(size_bytes) AS total_size
  FROM search_artifact
 WHERE <access predicate> AND <filters>
 GROUP BY group_id_lc, artifact_id_lc, version_lc
 ORDER BY group_id_lc, artifact_id_lc, version_lc DESC       -- default sort, total order
 LIMIT :size OFFSET :offset
```

- `MIN(group_id)` returns the original case. With a binary collation it is deterministic. No
  boolean is aggregated; the snapshot flag of a GAV comes from its version string.
- The grouped columns are the unique key of a GAV, so the ordering is total and paging is
  stable. Every sort key appends the three grouped columns as tie-breakers.
- `sort=version` orders by `version_lc` as text (`1.10` before `1.9`). Semantic ordering is
  applied only to `latestVersion` (4.6); sorting a page of thousands of versions semantically
  would need the whole set in memory.
- `sort=modified` orders by `MAX(last_modified)` of the matching files, `sort=size` by their
  `SUM(size_bytes)`.
- **Total**: `SELECT COUNT(*) FROM (SELECT 1 FROM search_artifact WHERE ... GROUP BY ... LIMIT 10001) t`.
- **Repositories of the page**: one more query for the page only. It selects
  `(group_id_lc, artifact_id_lc, version_lc, repository, origin, COUNT(*))` grouped by those
  columns, for the (at most 100) GAVs of the page, written as an `OR` of
  `(g = ? AND a = ? AND v = ?)` terms. Row-value `IN (...)` is avoided because its syntax and
  index use differ between dialects. `GROUP_CONCAT` and `STRING_AGG` are avoided for the same
  reason; the repositories are assembled in Kotlin.
- A statement timeout of 10 seconds applies to each query (JDBC `queryTimeout`). A timeout
  returns 503 with `Retry-After` and the hint to narrow the filters. Together with the grant-set
  guard of 5.3 it is the only 503 of the feature.

### 4.4 Facets

Facets count **files** per bucket, over the permitted rows. Each facet applies all active
filters except its own, so a chip row shows the alternatives to the current choice
(multi-select behaviour):

```sql
SELECT extension, COUNT(*) FROM search_artifact
 WHERE <access predicate> AND <all filters except extension>
 GROUP BY extension ORDER BY COUNT(*) DESC, extension LIMIT 20
```

Facets: `repository`, `extension`, `classifier` (null counted as bucket `-`), `origin`,
`snapshot`. At most 20 buckets each, ordered by count. They are computed on the permitted set
only, so the name of a repository the caller cannot see never appears (section 5.4). Counting
files and not GAVs is a deliberate simplification: `COUNT(DISTINCT a, b, c)` does not exist on
every dialect. The UI labels the counts "files". Facets are skipped with `facets=false`.

### 4.5 Files of a GAV

The expand endpoint (section 6.2) is a plain indexed lookup on `(group_id_lc, artifact_id_lc,
version_lc)` with the access predicate, ordered by `path`, paged by `page` and `size`
(default 50, at most 200).

### 4.6 Latest version

For each artifact (groupId, artifactId) on the page, `latestVersion` is the highest version in
the **permitted** set, ignoring the user's filters, preferring releases:

1. one query for the page's artifacts, `SELECT group_id_lc, artifact_id_lc, version_lc,
   MIN(version) ... GROUP BY ...` with the access predicate, an `OR` of artifacts, read as a
   stream;
2. in Kotlin, versions are ordered with Maven `ComparableVersion`; the highest non-snapshot
   version wins, and if the artifact has only snapshots the highest snapshot wins.

`ComparableVersion` is not on the classpath today (no code in the repository uses it). The
design adds `org.apache.maven:maven-artifact` (Apache 2.0, small, no transitive weight beyond
plexus-utils) in its own build commit (task 15) rather than porting the algorithm, because the
ordering rules of Maven (qualifiers, `RC`, `SNAPSHOT`, numeric segments) are easy to get subtly
wrong.

Cost cap: at most 5 000 versions are read per artifact and at most 100 000 rows per request.
An artifact above the cap gets `latestVersion: null`; the rest of the row is unaffected. The
query is index-only on `ix_search_gav`.

### 4.7 Performance targets and index size

These are design targets to be measured before release. They are **not** measured here.

| Setup | Target |
|-------|--------|
| 100 000 GAVs (about 800 000 file rows), PostgreSQL or MariaDB | p95 under 500 ms for a filtered query with facets |
| the same on SQLite (single connection) | p95 under 1.5 s |
| rebuild, file system | about 10 000 rows per second |
| rebuild, S3 | bounded by `ListObjectsV2` pages of 1 000 keys; 800 000 objects are 800 requests, minutes rather than hours |

**Index size estimate.** One row is about 250 bytes of payload (the coordinates twice in two
cases, the hash, the path twice) plus the indexes. With ten indexes of 40 to 80 bytes per entry
(the path indexes are the widest), a row costs roughly 0.8 KB in total, so 800 000 rows are
about 650 MB, and 100 000 rows about 80 MB. The path, `path_lc` and their two indexes are about
60 percent of that. The estimate is deliberately conservative; the measured size goes into the
release notes. Reducing it is possible by dropping `ix_search_extension` and
`ix_search_modified` if measurement shows the planner prefers `ix_search_repo_origin` and the
GAV index anyway.

The ADR's review trigger stays: when the p95 of a filtered query exceeds 1 second on the
largest known installation, an external engine is evaluated (section 7.4).

### 4.8 Index status in every response

`indexStatus` is computed per request from three small queries (a state row, the oldest outbox
row by id, a count of dead letters), without caching:

| State | Meaning |
|-------|---------|
| `READY` | a full rebuild has completed; outbox lag and drift are within limits |
| `BUILDING` | no full rebuild has completed yet; results are partial |
| `MISSING` | the index is empty and no rebuild is queued (disabled workers, a failed first run) |
| `DEGRADED` | usable, but lag over 5 minutes, dead letters, dropped enqueues, drift over 5 percent, or the last rebuild `FAILED` |

`lagSeconds` is the age of the oldest unprocessed outbox row (0 when empty), and
`lastFullRebuild` the instant of the last completed rebuild. A later rebuild does not change a
`READY` index back to `BUILDING`.

## 5. Permissions

Search must not leak the existence or the content of HIDDEN or PRIVATE repositories. The rule
is: **a row is returned exactly when the caller could browse that path.** The reference is
`RepositorySecurityProvider.canBrowseResource`, the directory browse, and not the download
check (see 5.5).

### 5.1 Rules per row

| Repository visibility | Row returned |
|-----------------------|--------------|
| PUBLIC | to everyone: anonymous callers and any token. Mirrors `canBrowseResource`. |
| HIDDEN or PRIVATE | only when the caller has `READ` or higher on `/repository/path`, evaluated with the semantics of `PermissionEvaluator.allows(subject, AccessRequest("/repo/path", READ))`. Anonymous callers: none. |
| any | an instance admin sees every row; a token whose scopes lack `READ` sees only PUBLIC rows; a disabled subject that reaches the compiler sees nothing (defence in depth, `PermissionEvaluator` denies everything). |

Over HTTP a disabled principal never reaches the compiler: `BasicAuthenticator` rejects its
credentials, so the request is anonymous and sees PUBLIC rows only, like any failed credential.

Visibility is read from the repository settings at query time (see 2.3, no stored visibility).
Browse precedent: the directory listing filters each child with `canBrowseResource`
(`MavenFacade` L133-142, `MavenEndpoints` L96-104); search applies the same decision to rows.

### 5.2 `SearchAccess`

The permission decision is compiled once per request into a value object that holds no
`PermissionEvaluator` types, so an external engine can translate it without depending on the
evaluator:

```kotlin
data class SearchAccess(val repositories: List<RepositoryAccess>)

data class RepositoryAccess(val repository: String, val scope: Scope) {
    sealed interface Scope {
        object All : Scope                                   // every path in the repository
        data class Prefixes(
            val exact: List<String>,                          // case-sensitive, segment-aware (new model)
            val caseInsensitive: List<String>,                // legacy routes, plain startsWith, lower-cased
        ) : Scope
    }
}
```

An empty `repositories` list matches nothing. The object has an in-memory predicate
`matches(repository, path)` used by the parity test, and a renderer to SQL (5.3).

### 5.3 Compilation

For each configured repository `R`, the compiler (a pure function of the subject, the repository
list with visibility and the evaluator) decides:

```
compile(subject, repositories):
  if subject.disabled: return none
  for R in repositories:
      if R.visibility == PUBLIC:                          scope = All
      elif subject.instanceAdmin:                         scope = All
      elif subject.scopes != null and READ !in scopes:    skip
      else:
          exact = []; ci = []
          for g in subject.grants with READ-capable role:
              new-model grant (READ, DEPLOY, MAINTAIN, ADMIN):
                  if g.repository is null or == R.name:
                      if g.pathPrefix is blank: scope = All; break
                      exact += trimmed g.pathPrefix
              legacy grant (LEGACY_READ):
                  P = lower(g.pathPrefix)                  # a full route like /releases/com/ex
                  if lower("/" + R.name) startsWith P:       scope = All; break      # prefix inside the repo name
                  elif P startsWith lower("/" + R.name + "/"):
                      ci += P without the "/name/" part      # path-level prefix, may be empty
          if scope not All and (exact or ci non-empty): scope = Prefixes(exact, ci)
```

Repository-level matching is decided in Kotlin against the known repository list. Only the
path-level part reaches SQL. The rendered predicate is:

```sql
-- repositories with scope All
repository IN (:all)
-- one block per repository with Prefixes
OR (repository = :r AND (
      path = :p OR (path >= :p/ AND path < :p0)                  -- exact (new model), segment-aware
   OR path_lc >= :q AND path_lc < :qnext                          -- legacy, no segment boundary
   ))
```

- New-model prefixes match whole segments and are case-sensitive (`pathInRepo == p` or starts
  with `p/`, the `withinPrefix` rule), so the SQL is the equality or the range
  `[p + "/", p + "0")`.
- Legacy prefixes match with plain `startsWith` ignoring case and **no segment boundary**
  (`/releases/com/example` also matches `/releases/com/examples/...`). That is why the index has
  `path_lc` and a range on it; the quirk is kept on purpose because migrated tokens must keep
  it (identity model 5.4).
- **Size cap.** A subject with many path-limited grants produces many terms. Terms subsumed by a
  shorter prefix of the same repository are dropped first. If more than 200 terms per repository
  or 1 000 in total remain, the request fails with 503 and a clear message ("grant set too large
  for search"). This is far above any observed token and is a guard, not a feature.
- Compilation costs one pass over the subject's grants and the repository list per request. The
  result is not cached (grants change with team edits; staleness is a leak, not an inconvenience).

### 5.4 Facets, counts and latest versions

The access predicate is the first conjunct of every query in section 4: the grouped result, the
total, the facets, the repositories of the page, the files of a GAV and the latest-version query.
Nothing is computed on all rows and filtered afterwards. A repository the caller may not see
does not appear in the facet buckets, in `repositories` of a row, in the total or in the lag
of a search that matches only its content. The only global values are `indexStatus.state` and
`lagSeconds`, which carry no repository information.

### 5.5 Download versus browse

Downloading an artifact of a HIDDEN repository by its known GAV is open to anonymous callers
(`canAccessResource` returns OK for HIDDEN, existing behaviour, not changed here). Search follows
**browse**, not download: a HIDDEN repository's content is not discoverable by anonymous search,
although a caller who knows the path can still fetch it. If the maintainers later want HIDDEN to
be searchable, it is a one-line change in the compiler (HIDDEN joins the PUBLIC case) and a
documented product decision.

### 5.6 Why not `canListRepository`

The repository-level check `canListRepository` is not used as the row filter:

- For HIDDEN repositories it admits any holder of a new-model `READ` grant, **including a
  path-limited one**, because `canListHidden` accepts READ-or-higher grants of any path
  (identity model 5.3). Such a caller may list the repository name but may browse only the
  granted subpath. A repository-level gate would return every row of the repository.
- A path-limited grant does not reach the repository root, so `canSee` and `allows(READ, "/repo")`
  disagree for the same subject. Only the path-level rule is correct for rows.
- The doc comment at `RepositorySecurityProvider.kt:41-43` says HIDDEN is listed to managers
  only. The code admits READ holders. The comment is stale and is listed under adjacent findings
  (section 14).

### 5.7 Parity test

The compiled predicate and `PermissionEvaluator.allows` must agree. The test (task 13, written
red before the compiler of task 14) generates subjects and paths with fixed seeds: roles and
combinations of repository-wide and path-limited new-model grants, legacy read and write routes,
instance admin, disabled subject, scoped tokens, PUBLIC, HIDDEN and PRIVATE repositories, paths
at the edge of prefixes (`com/example`, `com/examples`, case variants, trailing slashes). For each
pair it asserts `SearchAccess.matches(repo, path) == (visibility == PUBLIC || allows(READ, "/repo/path"))`.
A second layer runs the rendered SQL on H2 and SQLite over the same rows and asserts the same
result set as the in-memory predicate, which proves the rendering and not only the model.

## 6. API

All endpoints are additive; no existing endpoint changes. They are annotated with `@OpenApi`
like their neighbours (tag `Search`). The path family is `/api/maven/search`, next to
`/api/maven/repositories` and `/api/maven/details`, and `/api/search` stays free for a possible
global search (deviation D-2).

### 6.1 `GET /api/maven/search`

Open to anonymous callers; the subject is the optional authenticated principal, exactly as for
`GET /api/maven/repositories` (a failed credential counts as anonymous). Parameters: section 4.1.

```json
{
  "items": [
    {
      "groupId": "org.apache.commons",
      "artifactId": "commons-lang3",
      "version": "3.14.0",
      "latestVersion": "3.17.0",
      "snapshot": false,
      "repositories": [
        { "name": "releases", "origin": "HOSTED" },
        { "name": "central-proxy", "origin": "MIRROR" }
      ],
      "fileCount": 8,
      "lastModified": "2026-09-30T08:14:02Z",
      "browseLink": "/browse/releases/org/apache/commons/commons-lang3/3.14.0"
    }
  ],
  "page": 0,
  "size": 20,
  "total": 143,
  "totalCapped": false,
  "facets": {
    "repository": [ { "value": "releases", "count": 612 } ],
    "extension":  [ { "value": "jar", "count": 301 }, { "value": "pom", "count": 150 } ],
    "classifier": [ { "value": "-", "count": 150 }, { "value": "sources", "count": 75 } ],
    "origin":     [ { "value": "HOSTED", "count": 700 }, { "value": "MIRROR", "count": 90 } ],
    "snapshot":   [ { "value": "false", "count": 760 }, { "value": "true", "count": 30 } ]
  },
  "indexStatus": { "state": "READY", "lagSeconds": 0, "lastFullRebuild": "2026-10-20T03:30:12Z" }
}
```

- `repositories` lists each repository that holds the GAV, with its origin. A GAV that exists in a
  hosted repository and in a proxy shows both. The UI renders a `MIRROR` entry as
  "cached from <name>" (section 8).
- `browseLink` is `/browse/<repository>/<directory>` for the first repository in the list, the
  route of the dashboard (`browse`, path `/browse/:qualifier(.*)*`). Segments are percent-encoded.
  Each entry of `repositories` has its own link in the expand response.
- Errors use `ErrorResponse`: 400 invalid parameter or result window exceeded, 404 when search is
  disabled, 503 on a statement timeout or an oversize grant set.

### 6.2 `GET /api/maven/search/files`

Parameters: `groupId`, `artifactId`, `version` (required, exact, case-insensitive), `origin`
(default `all`), `repository` (repeatable), `page`, `size`.

```json
{
  "items": [
    {
      "repository": "central-proxy",
      "path": "org/apache/commons/commons-lang3/3.14.0/commons-lang3-3.14.0-sources.jar",
      "name": "commons-lang3-3.14.0-sources.jar",
      "classifier": "sources",
      "extension": "jar",
      "size": 650400,
      "lastModified": "2026-02-01T10:00:00Z",
      "origin": "MIRROR",
      "sourceHost": "repo.maven.apache.org",
      "sha1": "8d2f...",
      "browseLink": "/browse/central-proxy/org/apache/commons/commons-lang3/3.14.0/commons-lang3-3.14.0-sources.jar"
    }
  ],
  "page": 0, "size": 50, "total": 8
}
```

`sourceHost` is null for HOSTED rows, for copies cached before 1.3.0 and for anonymous callers
(section 2.4). The access predicate applies as for the search.

### 6.3 Index administration

All three require an instance admin (`managerOnly`, `ContextDsl`), like the Maintenance endpoints.

| Method and path | Result |
|-----------------|--------|
| `GET /api/maven/search/index` | status: `state`, `lagSeconds`, `lastFullRebuild`, `rows`, `outbox {pending, deadLetters, droppedEnqueues}`, `activeRun`, `runs` (last 20), `engine` |
| `POST /api/maven/search/index/rebuild` | body `{repositories?: [name]}`, starts a rebuild; 202 with `{runId}`; 409 if a run is live |
| `POST /api/maven/search/index/verify` | starts a verify run; 202 with `{runId}`; 409 if a rebuild or verify is live |
| `POST /api/maven/search/index/runs/{id}/cancel` | cancels a live run; 204 |

### 6.4 When search is disabled

With `INGOT_LOCAL_SEARCHENABLED=false` every endpoint above answers **404**, as if the route did
not exist. 503 would tell clients and monitors that a transient fault is retried; the feature is
absent by configuration, not unavailable. The dashboard treats 404 as "search is turned off on
this instance" and says so on the page.

### 6.5 OpenAPI shape

```yaml
/api/maven/search:
  get:
    tags: [Search]
    summary: Search artifacts across all repositories the caller may browse
    parameters:
      - { name: q, in: query, schema: { type: string, maxLength: 200 } }
      - { name: groupId, in: query, schema: { type: string } }
      - { name: artifactId, in: query, schema: { type: string } }
      - { name: version, in: query, schema: { type: string } }
      - { name: versionPrefix, in: query, schema: { type: string } }
      - { name: repository, in: query, schema: { type: array, items: { type: string } }, style: form, explode: true }
      - { name: extension, in: query, schema: { type: array, items: { type: string } }, style: form, explode: true }
      - { name: classifier, in: query, schema: { type: array, items: { type: string } }, style: form, explode: true }
      - { name: snapshot, in: query, schema: { type: string, enum: [true, false, all], default: all } }
      - { name: origin, in: query, schema: { type: string, enum: [hosted, mirror, all], default: all } }
      - { name: sort, in: query, schema: { type: string, enum: [groupId, artifactId, version, modified, size], default: groupId } }
      - { name: order, in: query, schema: { type: string, enum: [asc, desc], default: asc } }
      - { name: page, in: query, schema: { type: integer, minimum: 0, default: 0 } }
      - { name: size, in: query, schema: { type: integer, minimum: 1, maximum: 100, default: 20 } }
    responses:
      "200": { description: One page of GAVs, content: { application/json: { schema: { $ref: "#/components/schemas/ArtifactSearchPage" } } } }
      "400": { description: Invalid parameter or result window exceeded }
      "404": { description: Search is disabled }
      "503": { description: Statement timeout or oversize grant set }
```

## 7. Engine abstraction

### 7.1 Interfaces

The names are fixed. They live in `net.onelitefeather.ingot.search`; no new Gradle module, all
code stays in `reposilite-backend`, as for the identity model (section 9 there).

```kotlin
/** The plugin-facing contract: everything an engine must do. */
interface ArtifactSearchEngine {
    val id: String

    // index writes, called by the outbox worker, the rebuild and the verify job
    fun upsert(records: List<ArtifactFileRecord>)
    fun removePath(repository: String, path: String)
    fun removePrefix(repository: String, prefix: String, notAfter: Instant)
    fun reorigin(repository: String, directory: String, origin: Origin, sourceHost: String?)
    fun deleteOlderThan(repository: String, before: Instant): Long
    fun existing(repository: String, paths: Collection<String>): Map<String, ArtifactFileRecord>
    fun sample(seed: Long, size: Int): List<ArtifactFileRecord>
    fun repositoriesInIndex(): Set<String>
    fun count(): Long

    // reads, called by the REST layer
    fun search(query: ArtifactSearchQuery, access: SearchAccess): ArtifactSearchPage
    fun facets(query: ArtifactSearchQuery, access: SearchAccess): SearchFacets
    fun files(query: ArtifactFilesQuery, access: SearchAccess): ArtifactFilePage
    fun status(): IndexStatus
}

/** The internal call sites use this; it only enqueues and never throws. */
interface ArtifactIndexSink {
    fun changed(repository: String, path: String)
    fun removed(repository: String, path: String)
    fun reorigin(repository: String, directory: String)
}
```

- `SearchAccess` (5.2) is a plain value object. An engine translates it into its own query
  language; it never receives subjects, grants or evaluator types.
- The rebuild and the verify job talk to the engine through the same contract, reading storage
  through `StorageEntrySource` (3.6). They do not know whether the engine is the database.
- The outbox is **not** part of the engine. It is platform infrastructure in the database and
  feeds whichever engine is active (7.4).

### 7.2 `DatabaseArtifactSearchEngine`

The default implementation, in `net.onelitefeather.ingot.search.database`, uses Exposed against
the tables of section 2 and the queries of section 4. It owns the dialect details (collations,
upserts with explicit keys: `ON CONFLICT` on SQLite and PostgreSQL, `ON DUPLICATE KEY UPDATE` on
MariaDB and MySQL, `MERGE` on H2; statistics already uses upserts, `SqlStatisticsRepository`
L166-187).

Package layout:

```
net/onelitefeather/ingot/search/
  model/               ArtifactFileRecord, Origin, ArtifactSearchQuery, ArtifactSearchPage, IndexStatus
  parsing/             ArtifactPathParser (pure, section 2.2)
  access/              SearchAccess, SearchAccessCompiler (pure, section 5)
  outbox/              OutboxStore, OutboxWorker, ArtifactIndexSink implementations
  rebuild/             RebuildService, VerifyService, SearchScheduler, StorageEntrySource adapters
  database/            tables, DatabaseArtifactSearchEngine, ComparableVersion ordering
  application/         SearchPlugin (wiring, listeners, routes), local configuration keys
  api/                 SearchApiEndpoints, DTOs
```

### 7.3 Configuration

| Where | Key | Default | Meaning |
|-------|-----|---------|---------|
| Local configuration | `searchEnabled` | `true` | feature switch; false hides the endpoints and stops the workers, tables stay |
| Local configuration | `searchEngine` | `database` | engine id |
| Shared settings, domain `search` | `verifyEnabled`, `verifyAt`, `verifySampleSize`, `rebuildSchedule`, `gapRebuild` | section 3.9 | behaviour, changed at runtime and replicated by the 10-second settings poll |

The environment variable names follow `LocalConfigurationFactory`, which matches the part after
the `INGOT_LOCAL_` (or `REPOSILITE_LOCAL_`) prefix against the **property names** of
`LocalConfiguration` ignoring case, with no underscore handling (`sslEnabled` is
`INGOT_LOCAL_SSLENABLED`). The properties `searchEnabled` and `searchEngine` therefore read
`INGOT_LOCAL_SEARCHENABLED` and `INGOT_LOCAL_SEARCHENGINE`, and the system properties
`ingot.local.searchEnabled` and `ingot.local.searchEngine`. Names with an underscore
(`SEARCH_ENABLED`) would not match and would be logged as an unknown property. Task 7 verifies
this mapping against the factory before the keys are documented. The legacy `REPOSILITE_LOCAL_`
spelling is read as a fallback, like every other key, so the drop-in contract holds.

### 7.4 External engines

An OpenSearch, Elasticsearch or Solr engine is a plugin that implements
`ArtifactSearchEngine` and registers a factory through a new, additive plugin event
(`ArtifactSearchEngineRegistryEvent`, in the plugin API next to the maven events). The server
picks the factory whose id equals `searchEngine`. Nothing in the core changes when no plugin is
present.

- The outbox keeps feeding the engine: workers call `upsert`, `removePrefix` and `reorigin` on
  whichever engine is active.
- An external engine is **eventually consistent**: a write is visible after its refresh
  interval. `lagSeconds` covers the outbox only; the engine reports its own refresh delay in
  `status()`.
- The verify job compares storage with **that engine** (`sample` reads from it), so drift
  between storage and an external index is detected the same way.
- `SearchAccess` is translated into a filter clause of the engine. The parity test of 5.7 is
  part of the contract test an engine plugin runs against its own implementation.
- The database tables stay the queue and the run ledger even when the index lives elsewhere; the
  `search_artifact` table is simply unused in that configuration.

## 8. UI (MD3, Vue 3)

### 8.1 Route and layout

- Route `search`, path `/search`, in `router/routes.js` with `meta: { section: 'search' }` (open
  to everyone, anonymous included), a navigation entry next to the overview, and `/search` added
  to `DashboardPaths.APP_ROUTES` (the backend allowlist that answers `index.html`). Adding the
  segment reserves `search` like the other dashboard routes: a repository called `search` keeps
  answering non-browser clients and only a top-level browser navigation gets the dashboard. The
  startup warning (`reservedRepositoryWarnings`) names it; nothing is rejected.
- The search box at the top is the same component as the overview search. Today that bar is
  inline in `RepositoryOverview.vue` (the M3 search bar, 56 px pill, `role="search"`), so the
  component is first extracted in its own `refactor(ui)` commit (task 19) and reused by both
  pages.
- A filter panel with MD3 filter chips: repository, extension, classifier, origin (hosted, all,
  mirror), snapshot state (all, releases, snapshots). Range inputs for size and last modified.
  A sort select (field and direction). On narrow screens the panel is a modal sheet.
- **The full query is in the address** (`router` url-state: `q`, every filter, `sort`, `order`,
  `page`, `size`), so a search is shareable and the back button works. The request is built from
  the address, not from component state.
- Results are a list of GAV rows: coordinates, `latestVersion` (a "newer" badge when it differs),
  repository chips with the origin, `fileCount`, last modified, and a link to `/browse`. A
  mirror chip reads "cached from <repository>"; the files view adds the upstream host when the
  API returns it. A row expands into its files (calls `GET /api/maven/search/files` on demand);
  each file and each GAV links to the browse route.

### 8.2 Keyboard

| Key | Behaviour |
|-----|-----------|
| `/` | focuses the search box, unless the focus is already in an input, textarea or select |
| `Enter` | submits the query and resets `page` to 0 |
| `Escape` | in the box: clears it and blurs; in the filter sheet: closes it |
| `ArrowDown`, `ArrowUp` | in the box: moves to the first or last result; between results: next or previous row |
| `ArrowRight`, `ArrowLeft` | on a result row: expand or collapse its files |
| `Tab` | order: search box, filter chips, range inputs, sort, result rows (one tab stop per row, arrows inside), pagination |

### 8.3 States

| State | Presentation |
|-------|--------------|
| Loading | skeleton rows with the final row height; the box stays interactive |
| Results | rows, facet counts on the chips, pagination with the capped total ("more than 10 000") |
| No results | explains which filters are active, offers "reset filters"; if the query matched only mirror repositories, adds "search shows what this instance holds; a proxy repository lists an artifact after it has fetched it once" |
| Index `BUILDING` | banner with progress from the status ("indexing, N of M repositories"), results may be partial |
| Index `MISSING` | explains that nothing is indexed yet; managers get a link to Maintenance |
| Index `DEGRADED` | quiet notice ("results may lag by N minutes") |
| Error | message and a retry button; a 400 for the result window shows the hint |
| Anonymous | a note that some repositories are hidden and signing in may show more, **without naming them** |
| Search disabled (404) | "Search is turned off on this instance" |

### 8.4 Maintenance

The `/maintenance` page (manager only) gets an index panel beside `StorageUsagePanel` and
`CleanupPoliciesPanel`: state, outbox lag, pending and dead-letter counts, last full rebuild,
row count, the run history (status, trigger, counters), and the actions "Rebuild index" and
"Verify sample" (`POST ...` with a confirmation for the rebuild, disabled while a run is live).
The panel polls the status while a run is live.

### 8.5 Accessibility

WCAG 2.1 AA from the existing tokens (text on surface, chip states, error text). A visible focus
ring on every control (the existing `md-focus-ring` utilities). A live region (`aria-live="polite"`)
announces the result count after each search ("143 results"). Chips have `aria-pressed`; the
expandable row uses `aria-expanded` and a labelled region. Sorting and filters are reachable
without a pointer. Touch targets follow the existing `md-touch-target` rule.

## 9. Migration and rollback

### 9.1 Tables

Four new tables (`search_artifact`, `search_outbox`, `search_rebuild_run`, `search_state`) and
one nullable column (`mirror_cache_entry.source_host`, section 2.4). They are created on start
with `SchemaUtils`, like the other stores. The creation runs under the same concurrency guard as
the identity tables (identity model 4.3): the first statement inserts a marker
(`search_state`, key `schema-1`) with `insertIgnore`, and a starter that sees zero inserted rows
skips the DDL. Several pods starting at once therefore serialise on the unique key (PostgreSQL,
MySQL, MariaDB) or the write lock (SQLite, H2), and no exception is used for control flow. When
the HA design's schema serialisation (1.1) lands, the guard becomes redundant and can go.

No existing table is changed except the one added column, nothing is dropped, no data is
migrated.

### 9.2 Rollback and re-upgrade

Ingot 1.2.x never reads or writes the search tables and the extra ledger column, so a rollback
needs no step. Files deployed while 1.2.x runs are not indexed. On the next 1.3 start the gap
rule (3.7) notices the old heartbeat and queues a `GAP` rebuild. The tables are never dropped by
the application; an operator who abandons the feature can drop them by hand.

### 9.3 Drop-in

No configuration change is needed. The feature is on by default and the first boot after the
upgrade starts a rebuild in the background. The only visible change besides the new endpoints
and page is the reserved segment `search` (8.1). `INGOT_LOCAL_SEARCHENABLED=false` turns the
feature off (404, section 6.4) and stops the workers; the tables remain.

### 9.4 Release notes and rolling upgrade

The notes for 1.3.0 state:

- The first start builds the index in the background. The time depends on the number of files
  (roughly 100 seconds per million files on a local disk at the targeted speed; S3 is bound by
  listing requests). Search works meanwhile and shows `BUILDING`.
- During a rolling upgrade, artifacts written by 1.2 pods are not indexed until the next
  rebuild or verify sample. Start a manual rebuild after the last 1.2 pod is gone, or enable the
  schedule.
- Mirror cache copies are searchable and are marked with their proxy repository.
- A repository named `search` shares its path segment with the dashboard route.
- `INGOT_LOCAL_SEARCHENABLED=false` disables the feature.

### 9.5 Version

Versions are derived by release-please from the Conventional Commits on `main`
(`release-type: simple`, manifest at `1.1.1`). The open release PR
`release-please--branches--main--components--ingot` (#22, "chore(main): release 1.2.0") already
proposes 1.2.0, a minor bump, from the commits merged so far. A `feat(search)` commit that is
merged **before** #22 is released joins that release and search would ship in 1.2.0. So the
control is the merge order, not a version edit: the search pull request is merged only after #22
is merged and 1.2.0 is tagged. The next release PR is then a minor bump to 1.3.0 because of the
`feat` commits. No file carries a hand-edited version (`build.gradle.kts`, `docker-compose*.yml`
and the frontend `package.json` are touched only by release-please).

### 9.6 Staging

Before the release: enable on a copy of a large installation (its database and a read-only view
of its storage), run the rebuild, measure the numbers of section 4.7 on PostgreSQL, MariaDB and
SQLite, record the index size, and compare the first-start duration with the release-note
estimate. The release does not proceed if the p95 target on SQLite is missed by more than a
factor of two without a documented mitigation.

## 10. Tests

Every test follows F.I.R.S.T.: a fresh database per test (`DatabaseConnectionFactory` with
`sqlite --temporary` or `h2 --temporary`, or a file in a `@TempDir`), a unique in-memory
database name per test, an injected `Clock` and a seeded `Random`, no sleeps, no wall-clock
waits, assertions with messages, one behaviour per test named after that behaviour. Time moves
by advancing the fake clock and by calling the worker tick explicitly. No test asserts a log
line.

### 10.1 Unit tests (no database, no storage)

- `ArtifactPathParser`: the table of section 2.2 as a table-driven test, including snapshots with
  timestamps, compound extensions, signatures, checksums, metadata, too-short paths, over-long
  values.
- `SearchAccessCompiler` and `SearchAccess.matches`: pure.
- `ComparableVersion` ordering of `latestVersion`, including releases over snapshots and the
  cost cap.
- Prefix range upper bound (`next`), including a last code point at the maximum.
- Cursor encoding and the rebuild's cursor logic over a fake `StorageEntrySource`.
- Outbox state transitions with a fixed clock: claim, extend, expire, take over, success delete,
  failure backoff, dead letter after 10 attempts.
- Query building: a given `ArtifactSearchQuery` renders the expected predicate and parameters.

### 10.2 Integration tests (H2 and SQLite in the default build)

- Schema creation twice, and two connections creating it at once (the guard of 9.1).
- Upsert idempotency: the same record twice gives one row; a changed size replaces it.
- Grouping, paging stability, total cap, facet counts excluding their own filter.
- Prefix search with `_` and `%` in coordinates, original case returned, case-insensitive match.
- `REMOVED_PREFIX` removes a file, a directory and nothing of a sibling that shares a name prefix
  (`com/example/app` versus `com/example/app2`); the `indexed_at` cut-off keeps a row written
  after the check.
- `REORIGIN_DIRECTORY` flips origin and host; a deploy into a cached directory ends HOSTED.
- Rebuild over a real temporary file system tree, with a fake S3-style entry source for the
  paging; stale-row cleanup; a pending outbox path is not overwritten.
- Each silent write path has a test that the outbox receives the entry: checksum sidecars, the
  generated POM, the mirror cache write (with the ledger row present when the entry is
  processed), cleanup deletes with a `cleanupRunId`, snapshot pruning, and a deploy into a cached
  directory (`REORIGIN_DIRECTORY`). Task 1 pins today's behaviour first (red where the hook is
  missing).
- The mirror host: a fetch from the second of two hosts records the second host; a pre-existing
  ledger row keeps the first.
- REST over HTTP (`ReposiliteSpecification` local variant): anonymous, token and manager calls;
  400 for the window cap; 404 when disabled.

### 10.3 Dialect tests

MariaDB and PostgreSQL (and MySQL) run through Testcontainers in a separate Gradle profile; the
repository already depends on `testcontainers-mariadb`, `-mysql` and `-postgresql`, and the
integration source set has the `ReposiliteSpecification` extensions. They prove what H2 and
SQLite cannot:

- the schema creates on MariaDB and MySQL with `utf8mb4` and the declared collations (the index
  key limit of 2.9, including `ix_search_gav`), and on PostgreSQL with `COLLATE "C"`;
- the range prefix returns the same rows as the in-memory predicate on every dialect;
- the claim (`UPDATE ... WHERE id = ? AND lease`) is exclusive under concurrent connections;
- the upserts of each dialect.

### 10.4 Visibility matrix

One test class, one behaviour per test, over a fixture with a PUBLIC, a HIDDEN and a PRIVATE
repository, hosted and mirror rows in each:

| Subject | Expected |
|---------|----------|
| anonymous | PUBLIC rows only (hosted and mirror); facets and totals count only those |
| token with READ on a path of a PRIVATE repository | only rows under that path; no other path, no repository-wide facet count |
| token with a new-model grant on `com/example` | `com/example/...` and not `com/examples/...` |
| legacy route token `/private/com/ex` | prefix rows including `com/examples` and case variants (the pinned quirk) |
| legacy route `/` | every row |
| token with HIDDEN READ on the repository | HIDDEN rows visible to it, still none for anonymous |
| scoped token without READ | PUBLIC rows only |
| disabled principal | over HTTP: treated as anonymous (rejected credentials), PUBLIC rows only; compiler unit test: a disabled subject gets no access at all |
| instance admin | everything |
| mirror row in a PRIVATE proxy | visible exactly like a hosted row of that repository |

### 10.5 Multi-replica tests

Two engine instances (two workers, two clocks that can differ by a set skew) on one database:

- claim exclusivity: ten entries, two workers, every entry processed once, and an entry whose
  lease expired is taken over;
- rebuild: the second `start` is refused (409) while the first lease is live; after a simulated
  crash (the run is cancelled after N batches and its lease left to expire) the next claimant
  resumes from the cursor and finishes with the same rows as an uninterrupted run;
- the nightly verify claim: only one of two schedulers runs;
- both instances return the same search result at the same moment.

### 10.6 Frontend

Vitest component tests (`vitest.config.js`, `src/**/*.test.js`, jsdom) for the search page: URL
state round trip, keyboard handling, each state of 8.3, the live region text, and the
`cached from` rendering. The extracted search bar keeps the tests the overview already has.

## 11. Open questions

| # | Question | Recommendation |
|---|----------|----------------|
| Q-1 | SQLite FTS5 and infix matching | Not in v1. Coordinate search needs prefix matching only. Probe FTS5 availability in the bundled `sqlite-jdbc 3.49.1.0` in a test in the phase that adds descriptions or POM names; trigrams for PostgreSQL (`pg_trgm`) would need the same per-dialect decision. |
| Q-2 | Rebuild schedule | Manual plus the optional `rebuildSchedule`; no nightly default (section 3.9). |
| Q-3 | POM parsing for packaging, name and description | Defer. It needs an XXE-safe parser (the only XML library is Jackson `XmlMapper`) and a size cap, and the stub POM generator makes the data uneven. |
| Q-4 | Version ranges | Defer. Needs a range grammar and a semantic comparator in SQL or an extra column. |
| Q-5 | Relevance sorting | None in v1; explicit sort keys only. |
| Q-6 | `sha1` population | Filled from the sidecar when the entry is processed. A rebuild keeps existing values and does not read files. A `sha1` search finds only populated rows, which the documentation states. An opt-in "read sidecars" rebuild is a later option. |
| Q-7 | Durable outbox versus in-process queue | Durable outbox (D-1). |
| Q-8 | API path | `/api/maven/search` (D-2). |
| Q-9 | Infix and word matching (`core` for `spring-core`) | Measure after release; likely a word table or trigram index per dialect. |
| Q-10 | Should `HIDDEN` repositories be searchable by anonymous callers | No (search follows browse, 5.5); a product decision if wanted. |

## 12. Deviations from ADR-INGT-0002

The ADR (proposed, last updated 2026-10-15) recommends index tables in the existing database and
rejects a walk per request, an embedded Lucene per pod and a mandatory external engine. This
design keeps that decision. It departs from the ADR in the following points. The ADR must be
updated in Outline by its owner after review; this document does not change it.

| # | ADR | This design | Why |
|---|-----|-------------|-----|
| D-1 | In-process write queue | Durable outbox table with leases | An in-process queue loses entries on every restart and pod kill, the failure the HA design names for statistics. The table is visible to all replicas and survives them. |
| D-2 | `GET /api/search` | `GET /api/maven/search` | Matches the existing `/api/maven` family and keeps `/api/search` free for a global search. |
| D-3 | Mirror copies: searchable only on explicit request, never by default (open question) | Mirror copies are searchable by default, marked with their origin and proxy repository | Decision of the maintainers. The ADR's default-off recommendation is overruled. The origin filter and facet remain as an optional narrowing. |
| D-4 | Open: rebuild schedule | No nightly full rebuild by default; a sampled nightly verify; manual rebuild; optional schedule | A full S3 walk is costly and the outbox keeps the index current. |
| D-5 | Permissions as the ADR describes (written before the identity model existed in code) | A row filter compiled from the caller's grants with the identity evaluator's rules; `canListRepository` is not used | The evaluator is the source of truth now (ADR-INGT-0001). Repository-level listing is wrong for path-limited grants (5.6). |
| D-6 | UI follows the MD3 PR | The MD3 UI is on `main` already; it is not a precondition | The branch `feat/identity-and-md3-ui` is merged. |

Two further additions are not deviations but are not in the ADR either: the column `path_lc`
(legacy matching, 2.3) and `source_host` on `mirror_cache_entry` (2.4).

## 13. Task breakdown

Implementation order. Each task is small and carries exactly one Conventional Commit type; the
scope is `search` unless noted. Production code starts with a failing test (red, green,
refactor). A change of behaviour of an existing path that needs a refactor is its own
`refactor` commit. Commit messages are English, lower case, no trailing period, no trailers.

| # | Commit title | What it changes, and its test first |
|---|--------------|--------------------------------------|
| 1 | `test(maven): pin the write paths that bypass deploy and delete events` | Characterisation tests for checksum sidecars, the generated POM, `putMirroredFile`, snapshot pruning, the ledger removal on deploy and cleanup deletes: today they emit no event or an event with a given shape. No production change. |
| 2 | `feat(storage): list files with size and modification time` | Adds `StorageEntrySource` with the file system implementation (sorted DFS, resume by cursor), the S3 implementation (flat `ListObjectsV2`, `StartAfter`, no delimiter) and the `WalkingEntrySource` fallback. Tests first: ordering, cursor resume, size and mtime, paging with a fake S3 client. |
| 3 | `test(search): cover path parsing for releases, snapshots and classifiers` | The table of 2.2 as a table-driven test; fails until task 4. |
| 4 | `feat(search): parse repository paths into artifact coordinates` | `ArtifactPathParser`, `ArtifactFileRecord`, the limits of 2.8. Makes task 3 pass. |
| 5 | `feat(search): add index tables for files, outbox and rebuild runs` | Exposed tables, per-dialect collations, indexes, the creation guard of 9.1. Test first: creation twice, concurrent creation, index key length on the dialect profile. |
| 6 | `test(search): outbox claims are exclusive across engine instances` | Two workers on one database claim disjoint batches; an expired lease is taken over. Fails until task 7. |
| 7 | `feat(search): durable outbox with lease-based workers` | `OutboxStore`, `OutboxWorker`, `ArtifactIndexSink`, `SearchPlugin` skeleton, local keys `searchEnabled` and `searchEngine` (mapping verified against `LocalConfigurationFactory`), the shared settings domain `search`, heartbeat. Pure state-transition tests with a fixed clock first. |
| 8 | `feat(maven): enqueue index updates for deploy and delete` | Listeners on `DeployEvent` and `DeleteEvent` (including cleanup runs) that never throw. Test first: a throwing database does not break the deploy. |
| 9 | `feat(maven): record the upstream host of cached mirror copies` | Nullable `source_host` on `mirror_cache_entry`, passed through `putMirroredFile` and `recordStored`, ledger read methods for the index. Test first: second host recorded, first host kept. |
| 10 | `feat(maven): enqueue index updates for checksum, mirror and pruning writes` | Sink calls at the checksum sidecar write, `generatePom`, `putMirroredFile` (after `recordStored`), `forgetVersion` (reorigin) and `PreservedBuildsListener` (direct, before the rethrow). The tests of task 1 become assertions on the outbox. |
| 11 | `feat(search): rebuild the index per repository with a resumable cursor` | `RebuildService`, the run lock, stale cleanup, startup and gap rules, schedule. Test first: resume after a simulated crash, pending outbox paths not overwritten. |
| 12 | `feat(search): verify sampled rows against storage nightly` | `VerifyService`, the claim `search-verify:<UTC date>`, drift report, removed repositories. Test first with a seeded sample. |
| 13 | `test(search): assert the row filter matches the permission evaluator` | The parity test of 5.7 against `PermissionEvaluator`; fails until task 14. |
| 14 | `feat(search): compile caller grants into a row filter` | `SearchAccessCompiler`, the SQL rendering, the term cap. Makes task 13 pass. |
| 15 | `build(deps): add maven-artifact for version ordering` | Adds `org.apache.maven:maven-artifact` for `ComparableVersion`. No behaviour; one dependency and its license note. |
| 16 | `feat(search): query files and groups with filters, facets and paging` | `DatabaseArtifactSearchEngine` reads, grouping, facets, latest version, window cap. Tests first per filter and for paging stability. |
| 17 | `feat(search): expose search, index status and rebuild over REST` | `SearchApiEndpoints`, OpenAPI annotations, `managerOnly` admin routes, 404 when disabled, 409 rules. HTTP tests first. |
| 18 | `test(search): cover visibility and multi-replica behaviour end to end` | The matrix of 10.4 and the replica tests of 10.5 on top of the REST layer. |
| 19 | `refactor(ui): extract the search bar from the repository overview` | Moves the M3 search bar of `RepositoryOverview.vue` into a shared component; no behaviour change; the existing overview tests stay green. |
| 20 | `feat(ui): search page with filter chips and URL state` | Route `/search`, `DashboardPaths` entry, the page, keyboard and states of section 8. Vitest tests first. |
| 21 | `feat(ui): index status and rebuild actions in maintenance` | The index panel of 8.4. Vitest tests first. |
| 22 | `docs(site): add the artifact search guide` | User guide (what is searched, filters, the cached-copy marking, sha1 limits, the rebuild, the disabled switch) and the operator notes of 9.4, in `reposilite-site`. |
| 23 | Open the pull request titled `feat(search): search artifacts across repositories` | Not a commit. The title is a valid Conventional Commit because the pull request is squash-merged and the title becomes the commit on `main`. The body is English, follows the repository's pull request conventions, and has no attribution lines. |

Updating ADR-INGT-0002 is not a task: it happens in Outline, outside this repository.

## 14. Adjacent findings

Found while reading the code for this design. They are listed, not fixed, and none changes the
search design.

- `RepositorySecurityProvider.kt:41-43`: the doc comment says HIDDEN repositories are listed to
  managers only; the code admits READ holders (identity model 5.3). The comment is stale.
- `MetadataService`: `PomDetails` values (`groupId`, `artifactId`,
  `version`) are interpolated raw into the generated POM (`maven/api/MetadataApi.kt:36-40`,
  `MetadataService.kt:110-112`). A caller who may deploy can inject XML into the stored POM. Any
  future POM parsing for search (Q-3) must treat stored POMs as untrusted input.
- `StatisticsEndpoint.kt:52`: the statistics `limit` parameter is hard-coded.
- Manual cleanup runs are not claimed. The `running` guard is an in-memory set
  (`CleanupService.kt:74,91-94`), so a manual and a scheduled run can overlap across replicas.
  The search runs use a database lock row for manual runs to avoid this.
- `scheduled_job_claim` has no lease or expiry: a claimant that dies after claiming skips that
  job for the day. The search rebuild avoids the consequence by making the claim create a
  resumable run row.
- The cleanup scheduler starts a policy only when the current minute equals `HH:mm`
  (`CleanupScheduler.kt`); a restart in that minute skips the day.
