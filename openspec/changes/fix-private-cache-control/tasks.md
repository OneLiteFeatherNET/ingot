# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `maven`. Tests are written first
and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system
clock (the scope decision reads no time), no fixed ports, no shared mutable state, a fresh repository fixture per test, and
assertions with messages. The environment is read through a map argument, so no test depends on the real environment. Gradle
runs go through `heavy`, with `--max-workers=2`, targeted and never the full suite. Integration tests run as
`:reposilite-backend:integration --tests '<pattern>'`.

## 1. Proposal

- [ ] 1.1 `docs(openspec)`: propose fix-private-cache-control: this change and nothing else. Verify:
  `openspec validate fix-private-cache-control --strict` passes and the dash grep from the proposal prints nothing.

## 2. C1 and C2 scope and escape hatch

- [ ] 2.1 `test(maven)`: write failing unit tests for the scope decision, as a pure function of the visibility of a repository,
  its loopback members and the escape hatch value, with repositories built in memory: a PUBLIC repository alone gives `public`;
  a PRIVATE and a HIDDEN repository give `private`; a PUBLIC group with PUBLIC members gives `public`; a PUBLIC group with a
  PRIVATE member gives `private`; a PRIVATE member two groups down is found; a cycle of groups ends with an answer; a remote
  reference is ignored; the escape hatch `true` gives `public` for a PRIVATE repository. For the reader: `INGOT_` wins over
  `REPOSILITE_`, the legacy spelling alone is read, unset and `false` mean off, and `yes` fails with a message naming
  `INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC`. Verify: they fail on the missing class and function.
- [ ] 2.2 `test(maven)`: write failing integration tests next to `MavenIntegrationTest`, one fresh instance per test: an authorized
  GET of a PRIVATE file answers `private, max-age=<maxAge>`; an anonymous GET of it answers `401` with
  `no-cache, no-store, max-age=0`; a HIDDEN file fetched by its exact path answers `private`; a PUBLIC file still answers
  `public`; a PUBLIC group with a PRIVATE member answers `private` to an anonymous caller; the `206` and `304` answers match the
  `200` answer of the same file; `maven-metadata.xml` keeps `no-cache`. Verify: the `private` tests fail with `public` on the
  current code, and the rest pass.
- [ ] 2.3 `fix(maven)`: add the scope function beside `Repository`, use it to choose the `Cache-Control` value in
  `attachmentHeaders` for the file answers of `MavenEndpoints` and `MavenLatestApiEndpoints`, read the escape hatch once at start-up
  (a non-boolean value stops the start-up) and pass it to `MavenFacade`. Leave `Repository.acceptsCachingOf` and the ETag
  selection as they are. Verify: 2.1 and 2.2 pass and the existing maven unit and integration tests pass with no assertion
  changed.

## 3. Documentation

- [ ] 3.1 `docs(maven)`: in `docs/gitbook/reference/web-routes.md`, replace the `Cache-Control` row with the three values
  (`public`, `private`, `no-cache, no-store, max-age=0`) and the group rule; in `docs/gitbook/reference/repository-settings.md`,
  add the Cache-Control rule to the Visibility section; in `docs/gitbook/reference/environment-variables.md`, add
  `INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC` with its `REPOSILITE_` fallback, the default `false` and the accepted values; in
  `docs/gitbook/reference/upgrade-changes.md`, add the note that a shared cache no longer stores answers of PRIVATE and HIDDEN
  repositories and of groups with a private member, and how to restore the old value. Verify: the values on the pages match the
  scenarios in `specs/repository-cache-control`.

## 4. Pull request

- [ ] 4.1 `fix(maven)`: open the code pull request with the title `fix(maven): send private cache-control for access-controlled repository content`,
  from a branch with the groups 2 and 3, based on the remote main branch that already contains the merged proposal, with no file
  under `openspec/`. Read `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells the story of the owner of
  a private repository whose artifacts a shared cache kept, without class names. It carries no session link and no attribution line.
  Verify: `gh pr view --json title` shows the exact title, the commit-style hook accepts the title and every commit, the body has no
  em or en dash, and the CI is green.
