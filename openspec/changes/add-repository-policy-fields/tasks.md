# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `maven`. Tests are written first
and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system
clock (any time source is injected as a `java.time.Clock`), no fixed ports, no shared mutable state, a temporary directory per
test, and assertions with messages. Gradle runs go through `heavy`, with `--max-workers=2`, targeted and never the full
suite. Integration tests run as `:reposilite-backend:integration --tests '<pattern>'`, and unit tests as
`:reposilite-backend:test --tests '<pattern>'`. Frontend tests run as `npx vitest run <file>` in `reposilite-frontend`.

Depends on: nothing unmerged (`add-read-only-mode` and `restructure-navigation` are on main). Coordinate the `RepositorySettings` constructor with `add-repository-format-spi`.

## 1. Proposal

- [ ] 1.1 `docs(openspec)`: propose add-repository-policy-fields: this change and nothing else. Verify:
  `openspec validate add-repository-policy-fields --strict` passes and the dash grep from the proposal prints nothing.

## 2. Settings fields and validation (`test(maven)` then `feat(maven)`)

- [ ] 2.1 `test(maven)`: write failing tests with named arguments: absent keys resolve to `MIXED` and `false`; a save after a
  description edit writes no new key; an explicit value is kept; a document of the previous release loads unchanged;
  `versionPolicy` on a GROUP and either key on a PROXY are violations naming repository and key; untyped and HOSTED accept
  both. Verify: they fail on the missing fields.
- [ ] 2.2 `feat(maven)`: add `VersionPolicy`, the two nullable trailing keys with `@Doc` text, the validation rules, and the
  effective values on `Repository` through `RepositoryFactory`. Verify: the tests of 2.1 pass and the existing settings tests
  pass unedited.
- [ ] 2.3 `docs(maven)`: document both keys in `docs/gitbook/reference/repository-settings.md` and the rules in
  `docs/gitbook/reference/repository-types.md`.

## 3. Enforcement (`test(maven)` then `feat(maven)`)

- [ ] 3.1 `test(maven)`: write failing tests over a table of paths: `RELEASE` refuses a `-SNAPSHOT` parent with `400` and stores
  nothing; `SNAPSHOT` refuses a release parent and accepts `maven-metadata.xml` and its checksums; `MIXED` and absent accept
  both; reads, deletes and cleanup are untouched by the policy; `readOnly` refuses deploy, delete and generate-POM with `405`
  and serves reads; the global mode answers `503` first; the redeploy `409` is unchanged. Verify: they fail before 3.2.
- [ ] 3.2 `feat(maven)`: add the policy and flag checks next to `checkAcceptsUploads`, with messages naming the repository.
  Verify: the tests of 3.1 pass and the existing deploy and route tests pass unedited.
- [ ] 3.3 `test(maven)`: write failing tests with a fake cleanup run: a read-only repository is skipped and reported, others
  run. Verify: they fail before 3.4.
- [ ] 3.4 `feat(maven)`: skip read-only repositories in the cleanup plugin. Verify: the tests of 3.3 pass.
- [ ] 3.5 `docs(maven)`: add the `400` and `405` cases to `docs/gitbook/reference/api-errors.md` and the OpenAPI responses to
  the deploy and delete routes.

## 4. List API (`test(maven)` then `feat(maven)`)

- [ ] 4.1 `test(maven)`: write failing tests: `versionPolicy`, `readOnly` and `writePolicy` in the list response follow D5, with
  `writePolicy` null for PROXY and GROUP; a response from before the change still deserialises. Verify: they fail before 4.2.
- [ ] 4.2 `feat(maven)`: add the three fields to `RepositorySummary` and fill them. Verify: the tests of 4.1 pass.
- [ ] 4.3 `docs(maven)`: list the fields in `docs/gitbook/reference/http-api.md`.

## 5. Dashboard (`test(maven)` then `feat(maven)`)

- [ ] 5.1 `test(maven)`: write failing frontend tests: the write policy mapping to and from `redeployment` and `readOnly`
  (ALLOW_ONCE keeps no `readOnly` key, READ_ONLY keeps `redeployment`); the declare action writes only `type`; it is blocked
  with the rule list when `violationsOf` reports one; `mixed` shows the warning and no action; the list shows the write
  policy badge. Verify: they fail before 5.2.
- [ ] 5.2 `feat(maven)`: add the write policy helpers and select, the badge, the declare-type action and the mixed warning to
  the repository list and the settings view. Verify: the tests of 5.1 pass and the existing frontend tests pass unedited.

## 6. Operator documentation

- [ ] 6.1 `docs(maven)`: add `docs/gitbook/how-to/separate-releases-and-snapshots.md`, list it in `docs/gitbook/SUMMARY.md`,
  and state the rollback behaviour and the status codes. Use plain words from the operator's point of view.

## 7. Pull request

- [ ] 7.1 Open the pull request with the title `feat(maven): add repository version policy, read-only flag and write policy view`.
  The description follows `.github/pull_request_template.md`, carries no session link, and states that the change is opt-in
  with no breaking change.
