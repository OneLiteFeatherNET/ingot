# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `configuration`. Tests are written
first and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system
clock (any time source is injected as a `java.time.Clock`), environment variables and file reads go through injected interfaces
with fakes, a temporary directory (`@TempDir`) per test, no shared mutable state, and assertions with messages. Gradle runs go
through `HEAVY_SLOTS=1 heavy` with `--no-daemon --max-workers=2`, targeted and never the full suite. Unit tests run with
`:reposilite-backend:test --tests '<pattern>'`, the integration test with `:reposilite-backend:integration --tests '<pattern>'`.

## 1. Proposal

- [ ] 1.1 `docs(openspec)`: propose add-settings-secret-references: this change (proposal, design, capability spec, tasks) and
  nothing else. Verify: `openspec validate add-settings-secret-references --strict` passes and the dash grep from the proposal
  prints nothing.

## 2. Reference grammar, allowlists and switch

- [ ] 2.1 `test(configuration)`: write failing unit tests for the reference parser and resolver as pure functions of the value,
  a fake environment map and a `@TempDir` directory: `${env:NAME}` and `${file:PATH}` are replaced; several references in one value;
  `$${` gives the literal `${`; `${HOME}` stays; an unclosed reference is refused and the message holds no value; an empty name or
  path is refused; a variable outside `INGOT_SETTINGS_SECRET_ENV` is refused and named; a file outside the allowed directories is
  refused; `/run/secrets/../` escaping the allowed directory is refused after canonicalisation; empty lists refuse everything; a
  trailing `\n` or `\r\n` is removed; invalid UTF-8 fails without the bytes; the switch reads `INGOT_` before `REPOSILITE_`. Verify:
  they fail on the missing classes.
- [ ] 2.2 `feat(configuration)`: add the parser, the resolver with the injected environment and file reader, the three settings and
  the switch read from the process environment only. Verify: the tests of 2.1 pass.

## 3. Storage, masking and validation

- [ ] 3.1 `test(configuration)`: write failing tests on `SettingsSecrets` and the settings endpoints: with the switch on a reference is
  returned as written under `hideSettingsSecrets=true`; a literal secret is still `********`; a placeholder sent back keeps the stored
  value; a reference sent back is stored literally; a write with a refused or unset reference answers `400`, names the field and the
  reference, and stores nothing; with the switch off every value is masked and stored as before. Verify: they fail before 3.2.
- [ ] 3.2 `feat(configuration)`: teach `SettingsSecrets` to keep references visible when the switch is on, and validate references
  in the write path of `SettingsEndpoints` and `SharedConfigurationFacade.updateDomain`. Verify: the tests of 3.1 pass and the existing
  `SettingsSecretsTest` passes unedited.

## 4. Resolved view and load path

- [ ] 4.1 `test(configuration)`: write failing tests for the facade: a loaded document gives an effective view with the resolved
  values and a raw view with the literal references; rendering writes the raw view; a missing file at start stops the load and the
  error names the domain, the property and the path; a failing reload keeps the previous effective view and logs one error; a changed
  file takes effect on the next reload; no fake file read happens between two reads of the settings; a captured appender holds no
  resolved value in any line. Verify: they fail before 4.2.
- [ ] 4.2 `feat(configuration)`: add the effective view per domain, build it in the load, reload and refresh paths of
  `SharedConfigurationFacade`, and keep the raw view for rendering, audit and the debug dump. Verify: the tests of 4.1 pass.

## 5. Consumers

- [ ] 5.1 `test(configuration)`: write failing unit tests that the S3 storage provider factory, the LDAP login provider and connection
  check, the OIDC login service and GitHub login, the mirror credentials and the mirror HTTP proxy take the resolved value from the
  effective view, using fakes and no network. Add one integration test, `SettingsSecretReferenceIntegrationTest`, that stores a mirror
  password as a reference, fetches through the mirror and checks the request header, run as `:reposilite-backend:integration --tests
  '*SettingsSecretReference*'`. Verify: they fail before 5.2.
- [ ] 5.2 `feat(configuration)`: switch the consumers from the raw settings to the effective view. Verify: the tests of 5.1 pass and
  `MirrorCredentialsRoundTripIntegrationTest` passes unedited.

## 6. Documentation

- [ ] 6.1 `docs(configuration)`: document the three environment variables in `docs/gitbook/reference/environment-variables.md`, the
  reference syntax, the allowlists, the failure rules and the replica note in a new section of
  `docs/gitbook/reference/configuration-and-startup.md`, and correct the plain text statement for references in
  `docs/gitbook/how-to/use-s3-storage.md`. Verify: the three pages name the same variable names and the same escape.

## 7. Pull request

- [ ] 7.1 Open the code pull request with the title `feat(configuration): resolve secret references in shared settings`. Branch
  from the main branch after `add-file-secrets` and `add-shared-configuration-reload` have merged. Read
  `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells the story of the operator who keeps a secret in a
  mounted file and wants the settings to name it, without class names. It carries no session link and no attribution line. Verify:
  `gh pr view --json title` shows the exact title, the commit-style hook accepts the title and every commit, and the body has no em
  or en dash.
