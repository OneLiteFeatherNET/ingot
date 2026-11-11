# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `search`. Tests are written
first and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no
system clock (any time source is injected as a `java.time.Clock`), no fixed ports, no shared mutable state, a temporary
directory per test, and assertions with messages. Gradle runs go through `heavy`, with `--max-workers=2`, targeted and
never the full suite. Unit tests run as `:reposilite-backend:test --tests '<pattern>'`, integration tests as
`:reposilite-backend:integration --tests '<pattern>'`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose extract-search-backend-spi`: this change and nothing else. Verify:
  `openspec validate extract-search-backend-spi --strict` passes and the dash grep from the proposal prints nothing.

## 2. Contract suite and core tests (red)

- [ ] 2.1 `test(search)`: write the abstract `ArtifactSearchBackendContract` with one test per behaviour: replayed upsert
  leaves one row; removing a path removes only that path; a prefix removal keeps rows indexed after `notAfter`; a reorigin
  touches rows below one directory only; `pathsAt` pages stay stable; `search`, `facets` and `files` never return a row
  outside `SearchAccess`; `sample` returns existing rows and advances its cursor. Add the subclasses that run it against
  an in-memory fake, SQLite and H2 file databases in `@TempDir`. Verify: they fail, because the interface does not exist.
- [ ] 2.2 `test(search)`: write failing core tests with the fake backend: a tick applies the actions and completes the
  entries; a failed apply releases the entries with the existing backoff; the rebuild commits the cursor in the same
  transaction when `joinsDatabaseTransaction` is true; the rebuild applies before it commits the cursor when the flag is
  false; startup rules use `count()`. Verify: they fail.
- [ ] 2.3 `test(search)`: write a failing registry test: `database` resolves, an unknown id resolves to nothing and keeps the
  disabled facade and its warning. Verify: it fails.

## 3. Seam and database backend (green)

- [ ] 3.1 `refactor(search)`: add the internal `ArtifactSearchBackend` and move `IndexAction`, `StoredRow`, `SampledRow` and
  `SamplePage` into `net.onelitefeather.ingot.search.backend`. Add `DatabaseSearchBackend` that delegates to
  `DatabaseArtifactSearchEngine`, `DatabaseArtifactIndex` and `IndexRowStore`. Verify: the contract suite of 2.1 passes for
  every database subclass.
- [ ] 3.2 `refactor(search)`: switch `OutboxWorker`, `RebuildService`, `VerifyService`, `StartupRules` and `SearchIndexAdmin`
  from the concrete classes to the interface, replace `ArtifactIndexWriter` and the sampling by row id with the seam's
  operations, and add the registry with the single entry `database` used by `SearchPlugin`. Verify: the tests of 2.2 and
  2.3 pass, and the existing search unit tests pass with unchanged assertions.
- [ ] 3.3 `refactor(search)`: keep the schema code in the same transaction and order, with the backend's `ensure` creating
  `search_artifact` inside the marker transaction. Verify: `SearchSchemaTest` passes unedited.

## 4. Integration parity

- [ ] 4.1 `test(search)`: add `MariaDbDatabaseSearchBackendContractTest` and `PostgresDatabaseSearchBackendContractTest` in
  the integration source set on the shared containers. Verify: `heavy ./gradlew :reposilite-backend:integration --tests
  '*DatabaseSearchBackendContractTest*' --max-workers=2` passes.
- [ ] 4.2 `refactor(search)`: confirm that no operator documentation changes are needed by checking `docs/gitbook` for
  references to the moved classes, and record the result in the pull request description. No configuration key, variable,
  route or table changes, so no page under `docs/gitbook/` is edited.

## 5. Code pull request

- [ ] 5.1 Open the code pull request under the title `refactor(search): move the database index behind an internal backend
  interface`, from a branch of the remote main branch that already contains the merged proposal, containing the code only and
  no file under `openspec/`.
