# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `settings`. Tests are written first and
seen to fail before the production code exists (red, then green). Every test follows F.I.R.S.T.: no sleeps or waits by time, no
system clock (a `java.time.Clock` is injected), no shared mutable state, a `@TempDir` per test, a fixed test key and a fixed nonce
source, a small Argon2id cost injected for the tests, and assertions with messages. Gradle runs go through the slot runner
(`heavy`) with `--no-daemon --max-workers=2`, targeted and never the full suite. Unit tests run with
`:reposilite-backend:test --tests '<pattern>'`, the integration test with `:reposilite-backend:integration --tests '<pattern>'`.
New code lives in `net.onelitefeather.ingot.settings.encryption` and `net.onelitefeather.ingot.settings.export`; existing classes keep
their packages.

## 1. Proposal

- [ ] 1.1 `docs(openspec)`: propose encrypt-settings-at-rest: this change (proposal, design, capability specs, tasks) and nothing
  else. Verify: `openspec validate encrypt-settings-at-rest --strict` passes and the dash grep prints nothing.

## 2. Envelope and key

- [ ] 2.1 `test(settings)`: write failing unit tests for the envelope codec as a pure function with a fixed 32 byte key and a fixed
  nonce source: a seal and open round trip; the output starts with `enc:v1:` and the eight character key identifier; the same input
  with the same fixed nonce gives the same output; a different key fails to open with a message that holds no key bytes; one changed
  byte of the body fails; an envelope opened under another domain or property path fails; a list entry named by its key opens and
  the same entry at another index opens; `enc:v2:` is refused and named; the key identifier is the same for the same key and differs
  for another key. Verify: the tests fail on the missing classes.
- [ ] 2.2 `feat(settings)`: add the envelope codec (AES-256-GCM through `javax.crypto`, the key identifier and check value through
  HMAC-SHA256) with an injected `NonceSource`. Verify: the tests of 2.1 pass.

## 3. Key sources and the start check

- [ ] 3.1 `test(settings)`: write failing tests for the key source reader with an injected environment map and a `@TempDir` file:
  `INGOT_SETTINGS_KEY` is read; the `REPOSILITE_` spelling is read only when the `INGOT_` name is absent; `_FILE` content with one
  trailing line break is read; both sources stop with a message that names both variables; 16 bytes and invalid base64 stop with a
  message that holds no part of the value; the key is not read from a field of the shared document or from `LocalConfiguration`.
  Verify: they fail.
- [ ] 3.2 `feat(settings)`: add the key source reader and the `settings_key` table (created with `SchemaUtils` as the other tables are).
  Verify: the tests of 3.1 pass.
- [ ] 3.3 `test(settings)`: write failing tests for the start check with an in-memory fake of the `settings_key` table: no stored
  check and no envelope adopts the key; a matching check starts; a check of the previous key starts with a warning; another check stops
  and the message names the stored key identifier only; no key with envelopes stops and names the domain and property; `required`
  without a key stops and names the variables; the mode value other than `optional` or `required` stops. Verify: they fail.
- [ ] 3.4 `feat(settings)`: add the check and the mode reader (`INGOT_SETTINGS_ENCRYPTION`, default `optional`). Verify: the tests of
  3.3 pass.

## 4. Sealing in the settings store

- [ ] 4.1 `test(settings)`: write failing tests on `SharedConfigurationFacade` with the in-memory provider and fake settings classes
  that carry `@get:Sensitive`: the stored document holds an envelope for a literal secret and the plain value for a non-secret; an empty
  secret is stored empty; a reference is stored as written with the switch on; a document with envelopes loads into the plain consumer
  view; the entity tag of the same settings is the same before and after sealing (the tag is the hash of the plain JSON); a failing
  decryption on load keeps the previous settings on a reload and logs the domain and property only; the failure log holds no envelope.
  Verify: they fail.
- [ ] 4.2 `feat(settings)`: seal in `renderConfiguration` and open in the load and refresh paths, before reference resolution, using
  the codec of group 2 and the key of group 3. Verify: the tests of 4.1 pass.
- [ ] 4.3 `test(settings)`: write failing tests for the file provider with reload off and on: with reload off the file is written sealed
  on the next save and not at start; with reload on the file is read as plain and sealed values alike and is never rewritten.
  Verify: they fail.
- [ ] 4.4 `feat(settings)`: apply the file and database rules of 4.3 in the first start path (`optional` mode, database case: one
  compare-and-set write and one `SETTINGS_SECRETS_REENCRYPTED` event). Verify: the tests of 4.3 pass.

## 5. Reads, writes and the schema

- [ ] 5.1 `test(settings)`: write failing tests on `SettingsEndpoints` and `SettingsSecrets` with the flag off and on: `?secrets=hidden`
  returns `********` for every non-empty secret and no envelope; the plain read is unchanged with the flag off and with it on; a
  reference is returned as written; a `********` on write keeps the stored value with the flag off; a `********` without a stored counterpart
  gets `400` naming the property; the ETag is the same as before the change. Verify: they fail.
- [ ] 5.2 `feat(settings)`: add the `secrets` query parameter, the placeholder rule for every write, and the masking of envelopes in
  `SettingsSecrets.mask`. Verify: the tests of 5.1 pass.
- [ ] 5.3 `test(settings)`: write failing tests that the schema of a domain with `@Sensitive` properties carries `"x-ingot-secret": true`
  on those properties and on no others, and that a plugin settings class with `@get:Sensitive` gets the same. Verify: they fail.
- [ ] 5.4 `feat(settings)`: add the flag to the schema generation in `configuration/shared/SchemaGenerator.kt`. Verify: the tests of
  5.3 pass.

## 6. Rotation and downgrade

- [ ] 6.1 `test(settings)`: write failing tests for the rotation with `INGOT_SETTINGS_KEY_PREVIOUS`: a value sealed with the previous key
  reads; a write seals with the active key; `settings-reencrypt` rewrites every envelope with the active key, stores the active check
  and records one event with the two key identifiers and no value; a replica holding only the previous key stops at start after the
  check moved on. Verify: they fail.
- [ ] 6.2 `feat(settings)`: add `INGOT_SETTINGS_KEY_PREVIOUS` (and its `_FILE` spelling), the console command `settings-reencrypt` in the
  style of `AccessTokenCommands.kt`, and the event. Verify: the tests of 6.1 pass.
- [ ] 6.3 `test(settings)`: write failing tests for the downgrade command `settings-decrypt`: it refuses without the active key; with the
  key it writes the plain values back and removes the check row; the event holds no value. Verify: they fail.
- [ ] 6.4 `feat(settings)`: add the command `settings-decrypt`. Verify: the tests of 6.3 pass.

## 7. Warning, audit and logs

- [ ] 7.1 `test(settings)`: write failing tests for the state response with a key, without a key, and with two plain secrets (the count
  excludes references and empty values), and for the four new `AuditAction` values appended after `UPLOAD_CHECKSUM_MISMATCH` with their
  wire names; a test that the audit serialisation of a settings change holds no value. Verify: they fail.
- [ ] 7.2 `feat(settings)`: add `secretsEncrypted` and `plaintextSecrets` to `FrontendSettingsResponse`, and the four audit actions.
  Verify: the tests of 7.1 pass.
- [ ] 7.3 `test(settings)`: write failing tests with a captured appender that a failing load with envelopes, the start check and the
  rotation log lines hold no envelope, no key, no password and no plain value, and that the support bundle masking (when that change is
  on main) masks envelopes. Verify: they fail.
- [ ] 7.4 `feat(settings)`: route every log line of this change through the masked document or a name only. Verify: the tests of 7.3
  pass.

## 8. Export and import

- [ ] 8.1 `test(settings)`: write failing tests for the export codec with a fixed salt and nonce source and the test Argon2id cost: a
  round trip returns the plain document; a wrong password, a changed header field, a changed ciphertext byte, an unknown version and a
  memory cost above 262144 KiB each fail with the same message; the file holds no plain value; the password shorter than 12 characters
  is refused; the export holds the saved state and not a draft. Verify: they fail.
- [ ] 8.2 `feat(settings)`: add the codec in `net.onelitefeather.ingot.settings.export` (Argon2id through the Bouncy Castle generator
  that `PasswordHasher.kt` uses, AES-256-GCM, the header as additional data) and the routes `POST /api/settings/export` and
  `POST /api/settings/import/open` in a new `SettingsExportEndpoints`, registered next to `SettingsEndpoints` in
  `SharedConfigurationPlugin.kt`. Verify: the tests of 8.1 pass.
- [ ] 8.3 `test(settings)`: write failing route tests: the export needs the settings permission (`ingot.admin.settings`) and returns the
  attachment headers with `Cache-Control: no-store` and a file name from the injected clock; a decrypt stores nothing; a wrong password
  records `SETTINGS_IMPORT_REFUSED` with reason `password`; a successful decrypt records `SETTINGS_IMPORT_DECRYPTED`; the events hold no
  password. Verify: they fail.
- [ ] 8.4 `feat(settings)`: implement the route behaviour and the two audit events. Verify: the tests of 8.3 pass.

## 9. Dashboard

- [ ] 9.1 `test(settings)`: write failing vitest tests (`reposilite-frontend/src/components/settings/`) for a password dialog component:
  the confirmation must equal the password before the request is sent; the strength hint is shown below 12 characters; the password is
  cleared when the dialog closes; the download menu opens the dialog and no longer serialises the domains in the browser; the import of
  a file with a `format` header asks for the password before it posts, and a file without the header is applied as before. Verify: they fail.
- [ ] 9.2 `feat(settings)`: add the password dialog, use `?secrets=hidden` for every domain read in the store
  (`reposilite-frontend/src/store/client.js`), route the download and the import through the new endpoints, and show the warning from the
  state response on the System page. The plain download is removed. Verify: the tests of 9.1 pass.
- [ ] 9.3 `test(settings)`: write failing vitest tests for the warning: an alert appears only while `secretsEncrypted` is false and the
  count is above zero, and it names the count and the key setting without a value. Verify: they fail.
- [ ] 9.4 `feat(settings)`: add the alert. Verify: the tests of 9.3 pass.

## 10. Integration and container tests

- [ ] 10.1 `test(settings)`: write the failing integration test with `:reposilite-backend:integration --tests
  '*EncryptSettingsAtRest*'`: with a key, a stored S3 secret is not readable in the `settings` row or the shared file, the settings
  responses (plain and `?secrets=hidden`) and the export file hold no value, and a restart with the same key reads the value back through
  the consumer view. Verify: it fails.
- [ ] 10.2 `feat(settings)`: close the gaps the integration test finds, with no new production behaviour beyond groups 2 to 8. Verify:
  the test of 10.1 passes.
- [ ] 10.3 `test(settings)`: write failing container tests in `reposilite-backend/src/containerTest/kotlin/com/reposilite/image/` (in
  `UpgradeScenario.kt`, and in a new test beside it for the key): an unmodified upstream Reposilite data directory starts unchanged
  without a key; with a key the first start seals the stored secrets; a second instance with the same key runs; a second instance with
  another key stops with the key identifier in the log. Verify: they fail on the image of the branch.
- [ ] 10.4 `feat(settings)`: make the container tests of 10.3 pass with the groups above. The image and its entrypoint are not changed.

## 11. Documentation

- [ ] 11.1 `docs(settings)`: update `docs/gitbook/reference/environment-variables.md` with `INGOT_SETTINGS_KEY`, `INGOT_SETTINGS_KEY_FILE`,
  `INGOT_SETTINGS_KEY_PREVIOUS`, `INGOT_SETTINGS_KEY_PREVIOUS_FILE` and `INGOT_SETTINGS_ENCRYPTION`, with their `REPOSILITE_` spellings;
  `docs/gitbook/reference/configuration-and-startup.md` for the modes and the start checks; `docs/gitbook/reference/settings-domains.md`
  for `?secrets=hidden` and the placeholder rule; `docs/gitbook/reference/http-api.md` for the three new routes;
  `docs/gitbook/reference/audit-events.md` for the four actions; `docs/gitbook/reference/upgrade-changes.md` for the new table and the
  unchanged default; `docs/gitbook/how-to/use-s3-storage.md` where it states that keys are stored in plain text.
- [ ] 11.2 `docs(settings)`: add `docs/gitbook/reference/settings-encryption.md` (the envelope, the key sources, the start check, the
  modes, the failure rules, the rotation and what is not protected), register it in `docs/gitbook/SUMMARY.md`, and add
  `docs/gitbook/how-to/encrypt-settings-secrets.md` (generate the key, mount it, read the warning, rotate, and go back to a build without
  this change). Verify: the links in both pages resolve in `SUMMARY.md`.
- [ ] 11.3 `docs(settings)`: update `docs/gitbook/how-to/back-up-and-restore.md` so a backup holds sealed values and restores only with
  the key that wrote it, and `docs/gitbook/reference/plugin-api.md` for the `@Sensitive` marker and the `x-ingot-secret` flag.

## 12. Pull request

- [ ] 12.1 Open the pull request from this branch to main with the title
  `feat(settings): encrypt secret values of shared settings at rest`. The description follows the repository template and tells the
  operator's story: the credentials in clear in a download, the key from the environment, the export with a password, and that
  nothing changes without a key. It carries no session link and no attribution line.
