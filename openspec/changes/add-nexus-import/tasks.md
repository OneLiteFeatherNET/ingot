# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `migration`. Tests are written first
and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock (any
time source and any sleeper is injected), no fixed ports (the fake Nexus binds port 0), no shared mutable state, a temporary
directory per test, and assertions with messages. Gradle runs go through `heavy`, with `--max-workers=2`, targeted and never the
full suite. Unit tests run as `:reposilite-plugins:nexus-import-plugin:test --tests '<pattern>'`, backend tests as
`:reposilite-backend:test --tests '<pattern>'`. There is no real Nexus in CI (the image is under the Sonatype EULA); every test
that needs Nexus runs against the fake, which replays fixtures built from documented responses.

Depends on `add-image-extra-ca-bundle`, which lands first: the plugin has no CA setting and uses the JVM trust store that the image extends.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-nexus-import`: this change and nothing else. Verify:
  `openspec validate add-nexus-import --strict` passes and the dash grep from the proposal prints nothing.

## 2. Spike: the Nexus REST endpoints

- [ ] 2.1 `docs(migration)`: check each endpoint of the table in `design.md` ("The Nexus side") against the Nexus 3 documentation:
  status, repositories, the three Maven settings endpoints, assets, components and the pagination page. For each one record the
  request, the response fields the MVP reads, the permission it needs and whether the field is "documented" or "observed". Write
  the result as `reposilite-plugins/nexus-import-plugin/src/test/resources/nexus/README.md`. Answer the six facts listed in the
  design (page size, token stability, checksum fields, settings permission, proxy credentials, sidecar assets). Verify: every row
  of the design table has an entry, and any difference from the design is listed with the section of the design to correct.
- [ ] 2.2 `test(migration)`: add the fixtures as JSON files under `src/test/resources/nexus/`: repository list with maven2, npm
  and docker entries; hosted, proxy and group settings; two asset pages with a `continuationToken`; a `403` body; a `401` body; a
  `429` with `Retry-After`. Each file starts from the documented shape. Verify: a test parses every fixture with the production
  model classes once they exist (task 3.2), and until then a schema check of the fixture keys passes.
- [ ] 2.3 If the spike changes a shape or a decision, open a separate `docs(openspec)` update of this change before 3.1. The
  design is not edited in the code pull request.

## 3. Module and the fake Nexus

- [ ] 3.1 `test(migration)`: write the fake Nexus server (JDK `HttpServer`, port 0, per-test instance) that serves the fixtures and
  file bytes, can inject `429`, `5xx`, a stale token, a changed file, a truncated body and a wrong checksum, and records every
  request with its method. Write failing tests for the Nexus client against it: paging by token without a page size, `Authorization`
  only to the connection origin, a foreign `downloadUrl` refused, a redirect to another origin without the header, retries with
  `Retry-After` on a fake clock and a fake sleeper, only `GET` and `HEAD` ever sent. Verify: they fail on the missing classes.
- [ ] 3.2 `feat(migration)`: add the module with the `@Plugin` class, the Nexus client, the model classes and the redacting
  credential type. Verify: the tests of 3.1 and the fixture parsing of 2.2 pass.
- [ ] 3.3 `build(migration)`: add `nexus-import-plugin` to the module list in `settings.gradle.kts`, add its `build.gradle.kts` with
  the `ShadowJar` task writing `nexus-import-plugin.jar`, and add it to `.dockerignore`. Verify: `:reposilite-plugins:nexus-import-plugin:shadowJar`
  builds the jar and the server build is unchanged without the plugin directory.

## 4. Switch per bundled jar

- [ ] 4.1 `test(migration)`: write failing tests in `BundledPluginLoadingTest` style: the importer switch loads only the importer;
  the metrics switch loads only the metrics jar; `REPOSILITE_NEXUS_IMPORT_ENABLED` is the fallback; a set `INGOT_` value wins even
  when `false`; an unknown jar follows the metrics switch. Verify: they fail before 4.2.
- [ ] 4.2 `feat(migration)`: add the table of bundled jars and switches to `PluginLoader`. Verify: the new tests pass and the
  existing `BundledPluginLoadingTest` cases pass unedited.
- [ ] 4.3 `build(migration)`: add the jar to the shadow build line (`Dockerfile:30`) and the `COPY` into `/app/bundled-plugins`
  (`Dockerfile:86`). Verify: the "Container images" job passes: an unmodified upstream compose file starts, an existing
  `/app/data` is adopted, `PUID`, `PGID` and `REPOSILITE_OPTS` take effect, and the importer routes answer `404` with the switch unset.

## 5. Credentials and connection

- [ ] 5.1 `test(migration)`: write failing tests: the canary password appears in no log line (captured appender), exception,
  response, table row, audit row or report, in the success and the failure case; a reference is stored as text; a URL with user
  information answers `400`; the host allow list; the client trusts a CA that is in the JVM default trust store (the test installs one through an injected `SSLContext`, no file or variable of the plugin); no switch disables verification; the connection test for `401`,
  `403` on settings and a healthy Nexus; a caller who is not an instance admin gets `403` and the fake sees no request. Verify: they
  fail before 5.2.
- [ ] 5.2 `feat(migration)`: add the connection and discover endpoints, the credential handling, the TLS trust and the settings
  parsing for the `INGOT_NEXUS_IMPORT_*` variables with their fallbacks. Verify: the tests of 5.1 pass.
- [ ] 5.3 `docs(migration)`: add the variables to `docs/gitbook/reference/environment-variables.md` and the routes to
  `docs/gitbook/reference/http-api.md`.

## 6. Dry run and loss report

- [ ] 6.1 `test(migration)`: write failing tests: a dry run changes no setting, file or index row; one report row per object with
  the five statuses; non-Maven formats `UNSUPPORTED` with counts; settings `403` gives `MANUAL` rows and one area row; the mapping
  table of the design for hosted, proxy and group; `PRIVATE` visibility; a name collision proposes `<name>-nexus` and changes
  nothing; JSON and CSV output; the format histogram. Verify: they fail before 6.2.
- [ ] 6.2 `feat(migration)`: add the mapping, the report store, the dry-run job kind and the report routes. Verify: the tests of 6.1 pass.
- [ ] 6.3 `test(migration)`: write failing tests for creating definitions: one `updateDomain` call with the entity tag, one retry on
  `412`, `MANUAL` rows with the JSON on `409`, no existing repository changed, `preserveSnapshots` true for a SNAPSHOT policy. Verify: they fail before 6.4.
- [ ] 6.4 `feat(migration)`: add the definition writer. Verify: the tests of 6.3 pass.

## 7. Jobs, lease and resume

- [ ] 7.1 `test(migration)`: write failing tests with two workers, one database and a fake clock: only one of two takers wins an
  expired lease; a stale token commits nothing; a worker that lost the lease starts no further file; takeover after 400 of 1000
  files ends with 1000 files and one final row each; an invalid cursor restarts the listing and skips final rows; a literal
  password pauses with `CREDENTIALS_NEEDED` and the credentials route resumes it; a reference lets another worker continue; a
  pause on read-only and a resume after it; the throttle and the backoff on the fake clock. Verify: they fail before 7.2.
- [ ] 7.2 `feat(migration)`: add the four tables, the job store with the fenced lease, the worker, the takeover tick, the throttle
  and the job routes. Verify: the tests of 7.1 pass.

## 8. Content copy, conflicts, verify and audit

- [ ] 8.1 `test(migration)`: write failing tests: a plain copy of jar, pom and sidecars with equal bytes; metadata after the other
  files; a corrupted and a truncated body leave no file; the idempotent second job (`SKIPPED_SAME`); the three conflict policies
  with the default `refuse` leaving the file unchanged; `GONE` on a `404`; verify with `VERIFIED`, `MISMATCH` and a conflicted
  repository never `VERIFIED`; the fake saw no request other than `GET` and `HEAD`. Verify: they fail before 8.2.
- [ ] 8.2 `feat(migration)`: add the copy through the Maven facade, the digest stream, the conflict handling and the verify job.
  Verify: the tests of 8.1 pass.
- [ ] 8.3 `test(migration)`: write failing tests in the backend: `per-file` records deploy events with actor `migration` and one
  summary event; `summary` records only the summary; a token named `migration-bulk` is still audited. Verify: they fail before 8.4.
- [ ] 8.4 `feat(migration)`: add `NEXUS_IMPORT_RUN` to `AuditAction`, the bulk session registration to `AuditFacade` and the skip
  in `AuditPlugin`. Verify: the tests of 8.3 pass and the existing audit tests pass unedited.

## 9. Operator documentation

- [ ] 9.1 `docs(migration)`: add `docs/gitbook/how-to/import-from-nexus.md` and list it in `docs/gitbook/SUMMARY.md`. It covers the
  switch, the flow (connection test, discover, dry run, create definitions, copy, verify), the three decisions (conflict policy,
  audit mode, credential form), the TLS path through `INGOT_EXTRA_CA_CERTS` (a link to the image how-to), what a cluster does when a replica drops out,
  and the advice to freeze Nexus maintenance tasks and to start with a dry run and a small repository, since the importer was tested against a fake.
- [ ] 9.2 `docs(migration)`: add the `NEXUS_IMPORT_RUN` event to `docs/gitbook/reference/audit-events.md`, a note on the new tables and
  the bundled jar to `docs/gitbook/reference/upgrade-changes.md`, and a pointer to the new page from
  `docs/gitbook/how-to/import-from-other-repository-managers.md`.

## 10. Pull request

- [ ] 10.1 Open the pull request with the title `feat(migration): add nexus import for maven repositories`. The description follows
  `.github/PULL_REQUEST_TEMPLATE.md`, carries no session link, and states that the plugin is off by default with no breaking change.
