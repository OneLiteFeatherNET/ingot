# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `search`. Tests are written first and
seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock (the
`Clock` is injected into the lease, the free-space guard and the breaker), no fixed ports, no shared mutable state, a temporary
directory per test, and assertions with messages. The whole suite of the plugin runs in-process, so it runs as
`:reposilite-plugins:lucene-plugin:test --tests '<pattern>'` with no container and no `heavy` job; the Gradle run keeps
`--max-workers=2`.

## 0. Preconditions in another pull request (not part of this change's commits)

- [ ] 0.1 `feat(search)`: the lease API of `fix-job-leases` carries an epoch that increases when the owner changes (design D4 and D5).
  Verify: its tests cover takeover after expiry with the epoch increasing and renewal keeping it.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-lucene-search-plugin`: this change and nothing else. Verify:
  `openspec validate add-lucene-search-plugin --strict` passes and the dash grep from the proposal prints nothing.

## 2. Core seam addition (red, then green)

- [ ] 2.1 `test(search)`: failing tests that the rebuild cursor is reset when `indexIdentity()` changes, that a backend returning
  null keeps the cursor, and that the database backend returns null. Verify: they fail.
- [ ] 2.2 `feat(search)`: add `fun indexIdentity(): String? = null` to `ArtifactSearchBackend` with its documentation, store the
  identity with the rebuild cursor, and reset the cursor on a change. Verify: the tests of 2.1 pass and the database tests pass
  unedited.

## 3. Configuration and directory (red, then green)

- [ ] 3.1 `test(search)`: failing unit tests for the variable rules: `INGOT_` wins over `REPOSILITE_`; the default directory is
  `<data>/search/lucene`; `INGOT_LUCENE_DIRECTORY` replaces it; the minimum free space defaults to 1 GiB and rejects negative values;
  no directory is created outside the configured one; a directory that is a regular file is `OPEN_FAILED` naming the variable.
  Verify: they fail on the missing classes.
- [ ] 3.2 `feat(search)`: add the configuration parser, the factory `lucene` with apiVersion 1, the registration on
  `ArtifactSearchBackendRegistrationEvent`, and the open that creates the directory and the index (mapping version and identity in
  the commit user data). The jar is not added to `PluginLoader.BUNDLED_SWITCHES`. Verify: the tests of 3.1 pass.
- [ ] 3.3 `test(search)` then `feat(search)`: an index with another mapping version is `OPEN_FAILED` naming both versions; an index
  that cannot be read is `OPEN_FAILED` with the path and the exception class and is not deleted. Verify: the tests pass.

## 4. Writes, commits and crash safety (red, then green)

- [ ] 4.1 `test(search)`: failing unit tests on a `@TempDir` index: an upsert replaces its document (one document after two writes of
  the same file); a removal of a missing path does nothing; a prefix removal keeps a row indexed after `notAfter`; `touch` changes
  `indexed_at` without changing other fields; a reorigin changes the origin of the directory only; `apply` returns only after the
  commit, and a fresh reader sees the batch. Verify: they fail.
- [ ] 4.2 `test(search)`: failing tests for the I/O mapping: a commit that fails with "No space left on device" is
  `BackendBusyException` with 60 seconds and no attempt; another I/O error is a batch failure; the writer is rolled back and reopened
  from the last commit. Verify: they fail.
- [ ] 4.3 `feat(search)`: add the writer, the apply transaction, the commit, the synchronous searcher refresh, the reorigin and the I/O
  mapping. Verify: the tests of 4.1 and 4.2 pass.
- [ ] 4.4 `test(search)` then `feat(search)`: the crash case of the design: the writer is closed after a commit and before the outbox
  removal is simulated by the caller; the entries are applied again and the count of documents equals the count of rows. Verify: the
  test passes.

## 5. Single writer and takeover (red, then green)

- [ ] 5.1 `test(search)`: failing tests with a fake lease store and an injected `Clock`: two instances on two directories, one
  writes and the other reports `Down` with the reason, drains nothing and writes nothing; the second instance reports the reason at
  its open and at its next acquisition attempt; a holder that lost its lease at the epoch check rolls its batch back and writes nothing
  more. Verify: they fail.
- [ ] 5.2 `test(search)`: failing tests of the takeover: after the expiry of the lease, the new holder with a different epoch in its
  commit user data starts a full rebuild and is `BUILDING` until it completes; the same epoch serves at once. Verify: they fail.
- [ ] 5.3 `feat(search)`: add the lease handling of the design (acquisition, renewal every 10 seconds on the maintenance tick, the
  epoch check before every commit, the non-holder state) and the rebuild trigger on takeover. Verify: the tests of 5.1 and 5.2 pass.

## 6. Queries and the access value (red, then green)

- [ ] 6.1 `test(search)`: failing golden tests of the Lucene query builder: one golden query string per shape (words, coordinates,
  each filter, facet with its own-kind exclusion, the group window, the total, the latest version, the file list), the access
  translation for every `PathPredicate` variant with the rows of `SearchAccessParityTest`, and the first-match rule with a denial.
  Verify: they fail.
- [ ] 6.2 `feat(search)`: add the query builder, the group collector with the bounded window, the capped total collector and the
  file search with `searchAfter`. The shared comparator and `latestOf` of the database engine are used, not copied. Verify: the tests of
  6.1 pass, and the database tests pass unedited.
- [ ] 6.3 `test(search)` then `feat(search)`: the contract suite of the seam runs in-process against the plugin in the unit source set,
  with every case of the database engine and the named exceptions of the design (none expected). Verify: the suite passes, and the
  core access check counter stays zero.

## 7. Disk, status and build (red, then green)

- [ ] 7.1 `test(search)` then `feat(search)`: the free-space guard with an injected store: below the floor `apply` is busy with 60
  seconds and health is `Down` with "low disk space"; above it the batch is applied. The Maintenance block shows the directory size and
  the index identity without the paths. Verify: the tests pass.
- [ ] 7.2 `build(search)`: add `:reposilite-plugins:lucene-plugin` to `settings.gradle.kts` with the Lucene modules, shadow the jar with
  `LICENSE` and `NOTICE` of Lucene, and add a build check that the server jar contains no `org.apache.lucene` class. Verify:
  `./gradlew :reposilite-plugins:lucene-plugin:test --max-workers=2` passes, and the check fails when a Lucene class is put into the
  server jar.

## 8. Documentation

- [ ] 8.1 `docs(search)`: add `docs/gitbook/reference/lucene-search-backend.md` with every variable and its default, the index location
  and layout, the commit and crash behaviour, the single-writer rule with the statement that more than one replica cannot use this
  backend, the rebuild after a takeover, the access rule and the security responsibility for the directory's permissions.
- [ ] 8.2 `docs(search)`: add `docs/gitbook/how-to/use-lucene-for-search.md` with the steps for one replica, a persistent volume, the
  `Recreate` strategy, the switch of `searchEngine`, the initial rebuild and the way back to the database. Link both pages from
  `docs/gitbook/SUMMARY.md`.
- [ ] 8.3 `docs(search)`: add the variables to `docs/gitbook/reference/environment-variables.md`, the backend selection to
  `docs/gitbook/reference/configuration-and-startup.md`, and the release to `docs/gitbook/reference/upgrade-changes.md`. State that the
  plugin is installed by hand or from the plugin store and is not part of the image.

## 9. Code pull request

- [ ] 9.1 Open the code pull request under the title `feat(search): add an embedded lucene backend plugin`, from a branch of the
  remote main branch that already contains the merged proposal and the merged precondition of section 0, with no file under
  `openspec/`.
