# Design

This design depends on `extract-search-backend-spi` (the seam), `add-database-full-text-search` (the token function and the word
cases), `add-search-backend-registration` (the public types, the event, the states and the access check of core) and `fix-job-leases`
(the lease API). Names are those of those changes.

## Context

- The seam's contract (`ArtifactSearchBackend`, `IndexAction`, `BackendBusyException`, the states of registration D4) applies
  unchanged. The index is a derived copy of the database, so a rebuild from the database always restores it.
- `ArtifactSearchBackend.joinsDatabaseTransaction` is the flag that decides how the rebuild cursor and the writes relate. A Lucene
  index sits on the local disk, outside the database transaction, so the flag is `false`. The rebuild therefore applies a page and
  commits its cursor afterwards, as an external index does.
- Replicas are detected in two places in the design set. The HA design (`docs/superpowers/specs/2026-08-08-kubernetes-ha-design.md`,
  section 1.1 and 1.2) uses a lease row with an expiry of 60 seconds, renewed every 10 seconds, taken over after expiry, and checked
  again before the write. The registration uses heartbeats per replica (`BACKEND_HEARTBEAT.<instance>`, three minutes) to agree on the
  backend. A heartbeat shows that a replica exists; it cannot exclude a second writer. Only a lease can, so this design uses one.
- The search spec's external-engine section (`docs/superpowers/specs/2026-10-16-artifact-search.md`, section 7.4) describes a backend
  as eventually consistent. Lucene is the exception: a commit made before `apply` returns is visible at once.

## Decisions

### D1: Apache Lucene, no other dependency

The plugin uses Lucene core, Lucene's common analysis module, the faceting module and the grouping module, all Apache 2.0. The
shadow jar carries the `LICENSE` and `NOTICE` files of Lucene. The plugin adds no other run-time library: JSON is not needed,
and the lease and the configuration use the server's classes through `SearchBackendContext`.

Lucene's classes live in the plugin's class loader. The server does not ship Lucene, which the build checks: the task "no class of
`org.apache.lucene` in the server jar" is part of the build group.

### D2: directory, layout and mapping

- The index directory is `INGOT_LUCENE_DIRECTORY`, default `<data>/search/lucene`, where `<data>` is the data directory of the server
  (`/app/data` in the image). The directory lives on the volume the image already uses, so no mount is added.
- The directory holds a Lucene index and, next to it, nothing else. The plugin writes no file outside it.
- Documents: one per file. The Lucene id field is the database path hash (`id`, a `StringField` used as the update term). The fields
  mirror the OpenSearch layout: `repository`, `path` (stored, indexed as keyword and as sort value), `path_lc`, `group_id`,
  `group_id_lc`, `artifact_id`, `artifact_id_lc`, `version`, `version_lc`, `gav` (keyword, the group key), `artifact_key`, `classifier`,
  `extension`, `snapshot`, `origin`, `source_host`, `size_bytes` (long, with point and doc values), `last_modified`, `indexed_at`
  (numeric doc values, so `touch` updates them without re-indexing), `sha1`, `sha256`, and `tokens` (multi-valued keyword).
- The commit user data records `ingot_mapping_version=1`, the index identity (D6) and the lease epoch of the commit (D5). `open()`
  refuses an index whose mapping version differs and reports `OPEN_FAILED` with both versions.

### D3: writes, commits and crash safety

- `apply(actions)` runs in the order of the actions, inside one `IndexWriter` transaction. An upsert is `updateDocument` by the id
  term, so a replay replaces the document. A removal is `deleteDocuments` by the id term. A prefix removal is `deleteDocuments` with a
  query on repository, on the path (the path itself or the prefix with `/`) and on `indexed_at <= notAfter`. Staleness is part of
  the query, so a late replay cannot remove a row that was indexed again.
- A reorigin re-adds each matching document with the new origin. Doc values cannot change an indexed term, so the cost is linear in
  the size of the directory. Reorigins are rare (a mirror is re-declared), and the cost is recorded in the reference page.
- `touch` uses `updateNumericDocValue` on `indexed_at`: no re-indexing.
- At the end of `apply`, the plugin calls `commit()` and then refreshes its searcher synchronously. The batch is durable and visible when
  `apply` returns. Only then does the outbox remove the entries. The order "commit, then remove the entry" gives at-least-once delivery.
- Crash safety. A Lucene commit is atomic: it fsyncs its segments and then writes a new commit point. A process killed before the commit
  point leaves the previous commit, and the next `open()` reads that. The outbox entries of the lost batch were never removed, so they
  are claimed again after their lease expires and applied again; the replay is idempotent (an upsert replaces its document, a removal
  of a missing document does nothing). A power loss has the same
  outcome, because the entries were not acknowledged. No write-ahead log of the plugin's own is needed.
- A commit that fails with an I/O error is rolled back (`rollback()`), and the writer is opened again from the last commit point. An error
  that the disk caused (`No space left on device`, or the free-space floor of D9) is `BackendBusyException` with a retry delay of 60 seconds,
  so the attempt is not counted. Any other error fails the batch, and the attempts count until the dead-letter rule applies.
- A rejected document (a value the analyser or the mapping refuses) fails its batch, as on the other backends.

### D4: one writer, by lease

Exactly one replica may write. The rule is a lease named `search.lucene.writer` with an expiry of 60 seconds, renewed every 10
seconds by the holder, on the lease API of `fix-job-leases`. Its shape is the one of the HA design, section 1.1:

- A replica acquires the lease when it opens. If the lease is held and not expired, the replica is a non-holder.
- The holder renews the lease every 10 seconds on its maintenance tick. The renewal is a short database write, outside any
  transaction that holds the index.
- Before every commit, the holder checks that it still owns the lease at its epoch (D5). A holder that lost the lease (a pause, a
  partition, a takeover) rolls back its batch and becomes a non-holder. Its batch is not counted as an attempt.
- A non-holder reports `Down` with the message "another replica holds the Lucene writer lease", its `apply` throws
  `BackendBusyException` with a retry delay of 10 seconds, and it answers search with `503` through the registration rules. The
  outbox therefore stays with the holder, and no attempt is counted on the entries.

What the operator sees with more than one replica. The second replica never writes, never serves search, and says so on the
Maintenance page and in its log at every state change. Downloads, uploads and deploys work on every replica, because they only write
outbox rows. Search answers on the holder when a request happens to reach it, and with `503` otherwise. Operators with several replicas
behind one Service must therefore not choose this backend; the reference page says it first, and the how-to recommends `replicas: 1`
with a `Recreate` strategy. A second replica reports the reason when it opens and again at each of its attempts to acquire the lease
(every 10 seconds), so it is never silent. The holder does not watch for other replicas; the lease is what keeps them out.

Why a lease and not heartbeats. A heartbeat tells that a replica exists. It cannot stop a second replica that is still draining from
writing its own index, and the registration's three-minute window is too long to protect a local index. The lease has an owner, an
expiry and an epoch, which is what a fence needs.

### D5: takeover and the rebuild it requires

- When a lease expires, another replica takes it, and the epoch of the lease increases by one. The epoch changes only when the owner
  changes; a holder that renews keeps its epoch.
- Each commit records the epoch of the holder that made it. On acquisition the replica compares that recorded epoch with the lease's
  epoch. If they differ, the replica does not know which writes the previous holder applied and acknowledged, so it starts a full rebuild
  (trigger `BACKEND`) before it serves search. Its state is `BUILDING` until the rebuild completes.
- If the epoch is the same, the replica's index is the one the lease describes, and it serves at once. A restart of the holder on a
  persistent volume therefore keeps its index; a restart on an empty volume is a new index, handled by D6.
- The takeover window is 60 seconds at most. The outbox entries claimed by the dead holder are claimed again after their lease expires,
  so no entry is lost. Entries that the dead holder applied and acknowledged are in its index only; the rebuild after the takeover
  restores them from the database.

### D6: index identity and the rebuild cursor

Because `joinsDatabaseTransaction` is `false`, the rebuild cursor lives in the database while the index lives on the local disk. Two
cases must not skip rows: a pod restarted on an empty volume, and an index directory deleted by an operator while the cursor sits in
the database at page 500.

- The index identity is a random UUID written into the commit user data when the index is created. `indexIdentity()` returns it.
- The core stores the identity with the rebuild cursor (as `add-search-backend-registration` stores `LAST_FULL_REBUILD`). A change of
  the identity resets the cursor to the first page. This is the additive method of this change: `indexIdentity(): String? = null` on
  `ArtifactSearchBackend`, with the reset rule in the core. A backend that returns null keeps today's behaviour, so the database is
  unaffected.
- A page of the rebuild is applied to Lucene first, then its cursor is committed in the database. A crash in between applies the same
  page again, which is idempotent.

### D7: queries and the access value

The query builder takes `ArtifactSearchQuery` and `SearchAccess` and builds a Lucene `Query`. Lucene can express every access
rule natively, so this backend is the one that applies the access value with the same meaning as the database:

- Each `RepositoryAccess` is a `TermQuery` on `repository` and a path condition: `All` adds nothing; `Exact` is a `TermQuery` on
  `path` or a `PrefixQuery` on `path` with `p/`; `Subtree` is the same with the joined segments; `Legacy` is a `PrefixQuery` on
  `path_lc` without a segment boundary, as the database does; `Conjunction` is a `BooleanQuery` with `MUST` clauses; `Never` is
  `MatchNoDocsQuery`.
- `Precedence` is translated into the first-match rule: for each allowed rule `i`, the condition of `i` must hold and no condition of an
  earlier rule may hold; the allowed rules are combined with `SHOULD`.
- Words and coordinates use the rules of `add-database-full-text-search` on `tokens`, `group_id_lc`, `artifact_id_lc` and `version_lc`.
- Facets are counted per facet kind with the other kinds' filters only, as the database does.
- Groups. A collector keeps the distinct `gav` values of the matches in a bounded map (the window of the page, at most 10 100), with the
  display values, the maximum `last_modified`, the sum of `size_bytes`, the file count and the repositories with origin. The shared
  comparator of the database engine sorts the window, and the page is sliced from it.
- The total is the number of distinct groups counted by a collector that stops at `TOTAL_CAP + 1`. It is exact up to the cap.
- Files are a `TopFieldDocs` search sorted by `path` and `repository`, with `searchAfter` for the rebuild and the verify walk. Paging by
  page number is exact at any depth, because the collector is not limited by a result window.
- The core check of registration D6 still runs on every returned row, and its counter must stay zero.

### D8: testing and named exceptions

- The contract suite of the seam runs in-process in the unit source set of the plugin, against a `@TempDir` index. No container and no
  sleep: the lease tests use a fake lease store and an injected `Clock`, and the poll of a fake takeover is an explicit method call.
- Unit tests: the variable rules, the directory rule (no path outside the data directory unless `INGOT_LUCENE_DIRECTORY` is set), the
  query builder against golden query strings, the first-match rule, the free-space guard with an injected store, the I/O error mapping,
  the epoch comparison and the identity rule.
- Two-instance tests: two plugin instances share one fake lease store and two temporary directories. Exactly one writes; the other
  reports down, drains nothing and writes nothing. A takeover after an expired lease causes a rebuild on the new holder.
- Crash test: the writer stops between the commit and the outbox removal. After the restart the entries are applied again, and the
  count of documents equals the count of rows.
- Named exceptions of the contract suite: none are expected. If a case cannot pass, it is listed in the reference page with the reason
  and is not skipped silently.

### D9: disk and failures

- The free-space floor of `INGOT_LUCENE_MIN_FREE_BYTES` (default 1 GiB) is checked before each batch with `FileStore.getUsableSpace()`.
  Below it, `apply` throws `BackendBusyException` with a retry delay of 60 seconds and health reports `Down` with "low disk space". Growth
  stops; downloads and uploads are not blocked by the plugin, because the plugin only writes its own index.
- The index is opened off the request threads, as the seam requires. A corrupt index (a `CorruptIndexException` or an unsupported
  format) is `OPEN_FAILED` with the path and the exception class. Nothing is deleted.
- A missing index directory is created at open, and its state is `MISSING` until the rebuild completes, as today.
- Memory: the writer's RAM buffer is Lucene's default (16 MB), and the plugin sets no heap of its own. The benchmark of
  `add-database-full-text-search` runs against this backend, and the heap and the disk numbers go into the code pull request.

### D10: security

- The index holds the paths of every repository, including `HIDDEN` and `PRIVATE` ones, as the database does. It is as sensitive as
  the database and is protected by the same file permissions as the data directory. The reference page says so.
- The plugin never returns a row outside the caller's access value, and the core check counts any row that fails it.
- No credential is involved, so no secret is read and none can leak. The status block shows the directory and the index size, not the
  paths.

## Risks

- **A second replica is the most likely operator mistake.** The design makes it visible within 10 seconds, but it cannot make search
  work on two replicas. The reference page and the how-to say so before the configuration steps.
- **A rebuild after every takeover** can be slow for a large index. The takeover is rare, and the Maintenance page shows `BUILDING`.
- **Lucene upgrades.** A new Lucene major can refuse an old index format. The plugin reports `OPEN_FAILED` and the operator rebuilds; the
  upgrade note says so.
- **Free space on a shared volume** is protected only by the floor. An index that is large relative to the volume can still fill it
  between two checks. The floor is an upper bound on the damage, not a quota.
