# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `admin`. Tests are written
first and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system
clock (any time source is injected as a `java.time.Clock`), no fixed ports, no shared mutable state, a temporary directory per
test, and assertions with messages. Gradle runs go through `heavy`, with `--max-workers=2`, targeted and never the full
suite. Integration tests run as `:reposilite-backend:integration --tests '<pattern>'`, and unit tests as
`:reposilite-backend:test --tests '<pattern>'`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-support-bundle`: this change and nothing else. Verify:
  `openspec validate add-support-bundle --strict` passes and the dash grep from the proposal prints nothing.

## 2. Redaction contract

- [ ] 2.1 `test(admin)`: write failing unit tests for the redactor with a fixed set of known secrets (a database password, an S3
  secret key, an LDAP password, a mirror password, a bus password, a TLS key password): R1 allowlist (an unknown local key, an
  unknown environment variable and a `-Dfoo.password=x` argument come out as `[withheld]`); R2 a shared domain is masked with
  `hideSettingsSecrets` off and a `${env:NAME}` reference is masked; an untyped domain is withheld; R3 each database form
  (sqlite, mariadb positional, JDBC URL with query secrets, unparsable) keeps kind and host only; R4 a bus URL with
  credentials; R6 value scrub (longest first, four character minimum) and each pattern, on plain and JSON lines; R7 a planted
  secret in the assembled output makes the guard refuse. One test lists the properties of `LocalConfiguration` by reflection
  and requires each to be allowlisted or withheld by name. Verify: they fail on the missing classes.
- [ ] 2.2 `feat(admin)`: add the `Redactor`, the allowlists and the guard in `net.onelitefeather.ingot.support`, reusing
  `SettingsSecrets.mask` and `ClusterBusUrl.redacted()`. Verify: the tests of 2.1 pass.

## 3. Collector and bundle

- [ ] 3.1 `test(admin)`: write failing tests with fake sources and a fixed clock: the system information carries version,
  build or `unknown`, JVM, OS, database kind, storage kinds, cluster members, plugins and the redacted configuration; the zip
  holds `manifest.json`, `system-info.json`, `configuration-local.json`, `configuration-shared.json`, `cluster.json`,
  `plugins.json` and `logs/<instance>.log`; the manifest lists each file and the redaction count; the collector has no
  constructor parameter of a token, session, identity or audit type; and the central test of this change: with all known
  secrets planted in the settings, the local configuration, the bus URL, the environment and the cached log, the byte content
  of every zip entry contains none of them. Verify: they fail before 3.2.
- [ ] 3.2 `feat(admin)`: add the `SupportInformationCollector` and the zip writer with the 8 MiB cap. Verify: the tests of 3.1
  pass.

## 4. Endpoints, access and audit

- [ ] 4.1 `test(admin)`: write failing integration tests for `GET /api/admin/support/system-info` and
  `GET /api/admin/support/bundle`: `401` without credentials and nothing read; `403` without the console area; `200` with it;
  `Cache-Control: no-store` and the attachment name; served while read-only; `404` on both with the opt-out; `429` for a second
  concurrent build with a latch holding the first; `500` problem body and an unsuccessful audit event when the guard trips; one
  audit event with the actor on a download and none on the JSON view; and the secret test again over the real HTTP response
  with a real settings domain holding a known S3 key. Verify: they fail before 4.2.
- [ ] 4.2 `feat(admin)`: add the routes, the single permit, the `INGOT_SUPPORT_BUNDLE_ENABLED` switch with its `REPOSILITE_`
  fallback and system property, the `SUPPORT_BUNDLE_DOWNLOADED` audit action and the OpenAPI descriptions. Verify: the tests of
  4.1 pass.
- [ ] 4.3 `docs(admin)`: document the setting in `docs/gitbook/reference/environment-variables.md`, the endpoints in
  `docs/gitbook/reference/http-api.md`, the action in `docs/gitbook/reference/audit-events.md`, and the console area now also
  gating the support endpoints in `docs/gitbook/reference/permission-nodes.md`.

## 5. Pages

- [ ] 5.1 `test(admin)`: write failing frontend tests (`npx vitest run` in `reposilite-frontend`): the System information page
  renders every section from a fixture; the Support page offers the download and states what is removed; both appear under
  Support in the registry only for a principal with the console area and only when the feature is on; a test over the settings
  domain ids asserts no domain is called `system-info` or `support`. Verify: they fail before 5.2.
- [ ] 5.2 `feat(admin)`: add the two pages and their registry entries under `/settings/system-info` and `/settings/support`.
  Verify: the tests of 5.1 pass and the existing navigation tests pass unedited.
- [ ] 5.3 `docs(admin)`: add both pages to `docs/gitbook/reference/dashboard-pages.md`.

## 6. Operator documentation

- [ ] 6.1 `docs(admin)`: add `docs/gitbook/how-to/collect-a-support-bundle.md` with the redaction contract in plain words, the
  allowlist tables, the log length caveat, the one-replica caveat and the advice to skim the zip before attaching it. List it in
  `docs/gitbook/SUMMARY.md`.

## 7. Pull request

- [ ] 7.1 Open the pull request with the title `feat(admin): add system information page and support bundle`. The description
  follows `.github/pull_request_template.md`, carries no session link, and states that the change is additive with no breaking
  change and that the endpoints can be turned off.
