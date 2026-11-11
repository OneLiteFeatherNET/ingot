# Proposal

## Why

The shared settings of Ingot hold the credentials an operator needs to run the instance: the S3 access and secret keys, the mirror
credentials and proxy URLs, the LDAP bind password, the OIDC client secret and the search user password. They are stored as plain
text. The database row (`settings.content`) and the shared configuration file hold them in clear, and so does every database
snapshot taken by the backup feature. Anyone who can read the database, a backup or the file reads the credentials of the upstream
storage and the identity provider.

The download of the settings makes this worse. The dashboard's "Download as JSON" writes what the settings API returns, and the
API returns the plain values by default, because `hideSettingsSecrets` is off. A file that an administrator copies to a laptop, a
ticket or a backup volume carries every credential in clear, without a password between the file and the reader.

Operators want two things. The stored secrets must be encrypted, and a settings file that leaves the instance must need a password
to open it. A key-based encryption is only useful if the key is not in the same file as the data, so the key comes from the
environment or from a file that a secret store mounts, as the rest of the deployment already does.

Existing Reposilite installations must keep working with no change. Encryption therefore starts only when an operator provides a key.

## What Changes

- **Encryption at rest of secret values.** When a settings key is configured, every non-empty literal value of a `@Sensitive`
  property is stored as an envelope `enc:v1:<kid>:<base64>` (AES-256-GCM, a random nonce per value, the domain and the property
  path bound as additional data). Other values, empty values and settings secret references stay as written. The envelope is
  decrypted when the document is loaded and is never returned by the settings API.
- **Key configuration.** `INGOT_SETTINGS_KEY` holds a base64 encoded 32 byte key, or `INGOT_SETTINGS_KEY_FILE` names a file that
  holds it (the Docker and Kubernetes secret pattern). The `REPOSILITE_` spellings are read as fallback. Setting both is refused.
  The key is never read from the shared settings or from `configuration.cdn`.
- **Key identity and startup check.** The server stores a key identifier and a check value for the key that encrypted the document.
  A start with a different key stops with a message that names the key identifier and never the key. Several instances that share a
  database must use the same key, and the check makes a mismatch visible at start instead of as a decryption error later.
- **Optional by default, required on request.** `INGOT_SETTINGS_ENCRYPTION` is `optional` by default. With `required`, Ingot refuses
  to start without a valid key and refuses to store a secret while no key is active.
- **Rotation.** `INGOT_SETTINGS_KEY_PREVIOUS` (with its `_FILE` spelling) is accepted for decryption only, so a new key can be
  rolled out replica by replica. A console command re-encrypts the stored document with the active key.
- **Settings API reads.** The dashboard reads with `?secrets=hidden`. Secret values are then replaced by `********` whatever
  `hideSettingsSecrets` says. The plain `GET` without the parameter is unchanged, so existing scripts keep working. A write that
  sends `********` keeps the stored value on every instance, not only when `hideSettingsSecrets` is on.
- **Password protected export and import.** The dashboard's download asks for a password and the server returns an encrypted file
  (Argon2id key derivation, AES-256-GCM). Import asks for the password, decrypts the file for review and then stores it through the
  existing review and save flow. The file holds the secrets in clear inside the ciphertext, so it can restore another instance that
  has a different key.
- **Warning.** The dashboard shows whether secrets are encrypted and how many stored secret values are still plain text, without
  showing the values.
- **Audit.** Four actions are appended to the audit enumeration: `SETTINGS_EXPORTED`, `SETTINGS_IMPORT_DECRYPTED`,
  `SETTINGS_IMPORT_REFUSED` and `SETTINGS_SECRETS_REENCRYPTED`. None of them carries a value, a password or a key.
- **Documentation.** `docs/gitbook/reference/environment-variables.md`, `docs/gitbook/reference/settings-domains.md`,
  `docs/gitbook/reference/http-api.md`, `docs/gitbook/reference/audit-events.md`, `docs/gitbook/reference/upgrade-changes.md`, a new
  reference page `docs/gitbook/reference/settings-encryption.md` for the envelope, the key files and the failure rules, a new how-to
  `docs/gitbook/how-to/encrypt-settings-secrets.md` for the key, the rotation and the downgrade, and
  `docs/gitbook/how-to/back-up-and-restore.md`, which states that a backup needs the key that wrote it.

### Out of scope

- External key management (AWS KMS, Vault, cloud secret managers, a sealed key service). The key is read from the environment or a
  file, and an external key provider is a follow-up.
- Encryption of the local configuration: the database password in `configuration.cdn`, the cluster bus password and the TLS key
  password. They stay with the `_FILE` and environment mechanisms of their own changes.
- Encryption of stored job credentials of the Nexus import, which are kept by that import. The envelope can be reused by a follow-up.
- Encryption of artifacts, of the database as a whole and of the search index. Access tokens and login passwords are not affected:
  they are already stored as bcrypt and Argon2id hashes.
- Changing the default of `hideSettingsSecrets` or the plain `GET` of the settings API. Scripts keep the behaviour they have.
- A new plugin API for reading secrets. Plugins read their settings through the existing domain reference, which already returns
  decrypted values.

## Capabilities

### New Capabilities

- `settings-secret-encryption`: the envelope, the key sources and the key identity, the start check, the optional and required modes,
  rotation, the reads and writes of secret values, and the warning.
- `settings-encrypted-export`: the password protected export and import, its file format, the key derivation, the dashboard flow and
  the audit actions.

## Impact

- **Type and scope.** The change ships under `feat(settings)`. Both capabilities are settings features, so one type and one scope
  cover the change. The pull request title, squash-merged onto main, is:

  `feat(settings): encrypt secret values of shared settings at rest`

- **Breaking marker.** No breaking change. Without `INGOT_SETTINGS_KEY` or `INGOT_SETTINGS_KEY_FILE` the stored bytes, the startup, the
  plain settings API and the dashboard behave as before, apart from the warning and the password on the download. A Reposilite data
  directory is adopted with no step. The title carries no `!` and no commit carries a `BREAKING CHANGE:` footer.
- **Behaviour that changes by default.** Two things, both in the dashboard and both needed for the change: the download asks for a
  password and writes the encrypted export, and a write of `********` keeps the stored value even when `hideSettingsSecrets` is off.
  The second is listed as an open question in the design.
- **Operator-visible with a key.** The first start with a key encrypts the stored secrets in the database. A downgrade to a build
  without this change needs the decryption step in the how-to. A backup restores only with the key that wrote it.
- **Code.** `reposilite-backend`: `configuration/shared` (the facade, the endpoints, the masking), the new packages
  `net.onelitefeather.ingot.settings.encryption` and `net.onelitefeather.ingot.settings.export`, `audit/api/AuditAction.kt`,
  `frontend/api/FrontendSettingsResponse.kt`, and `reposilite-frontend` (the settings download and import components and their
  tests). Plugin packages stay `com.reposilite.*`.

## Verification

- Unit tests with a fixed test key, a fixed nonce source and a small Argon2id cost, in `@TempDir` directories, run with
  `:reposilite-backend:test --tests '<pattern>'`.
- One integration test with `:reposilite-backend:integration --tests '<pattern>'` shows that a stored secret is not readable in the
  database row or the shared file, and that no settings response or export contains the value.
- The container tests cover an unmodified upstream Reposilite data directory, a first start with a key, a wrong key and a rotation.
- Vitest covers the password dialogs of the download and the import.
