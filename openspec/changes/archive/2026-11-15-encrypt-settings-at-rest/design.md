# Design

## Context

### Where the shared settings live

The shared settings are one JSON document with one entry per settings domain. `SharedConfigurationFacade.renderConfiguration()`
writes all domains with one `writeValueAsString` call (`reposilite-backend/src/main/kotlin/com/reposilite/configuration/shared/SharedConfigurationFacade.kt:371-374`).
The document goes to one of two providers, chosen in `SharedConfigurationComponents.kt:63-75`:

- **Database (default).** `RemoteSharedConfigurationProvider` stores the document in the `settings` table, one row per name, with a
  `content` text column and a `revision` counter (`reposilite-backend/src/main/kotlin/com/reposilite/configuration/infrastructure/SqlConfigurationRepository.kt:40-43`,
  `:57-60`). The compare-and-set on the revision is how instances avoid overwriting each other.
- **File.** `LocalSharedConfigurationProvider` reads and writes `configuration.shared.json`
  (`reposilite-backend/src/main/kotlin/com/reposilite/configuration/shared/infrastructure/LocalSharedConfigurationProvider.kt:27`).
  With reload on (`--shared-configuration` plus the reload switch) the file is owned by the operator, its API writes are refused
  (`SharedConfigurationFacade.kt:197`, `:252`), and a change is detected by SHA-256 of its content.

Replication between instances uses the database revision (poll) and the cluster bus event `config.changed`, whose payload holds the
domain name only (`reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/cluster/ClusterEventType.kt:28`). Values never travel
on the bus. NATS is one optional transport of the bus, and the database transport needs no NATS.

Each domain is a `SharedSettings` class registered through its plugin metadata (`SharedConfigurationComponents.kt:55-58`). The consumer
reads the same object that the facade stores (`DefaultSharedSettingsReference`, `SharedConfigurationFacade.kt:102-124`).

### Which values are secrets

Eight properties carry `@Sensitive` on main (`reposilite-backend/src/main/kotlin/com/reposilite/configuration/shared/api/SecretAnnotations.kt:27`):

| Secret | Property | Domain (as registered) | Source |
|---|---|---|---|
| S3 endpoint, access key, secret key | `endpoint`, `accessKey`, `secretKey` | repository settings (`MavenSettings`) | `storage/s3/S3StorageProviderSettings.kt:43-51`, `docs/gitbook/reference/repository-settings.md:261` |
| Mirror proxy URL, which may hold credentials | `httpProxy` | repository settings (`MavenSettings`) | `maven/application/MavenSettings.kt:344` |
| Mirror credential password | `password` | repository settings (`MavenSettings`) | `maven/application/MavenSettings.kt:373` |
| LDAP bind password | `bindPassword` | login settings (`LoginSettings.ldap`) | `identity/login/ldap/LdapLoginSettings.kt:40`, `identity/login/application/LoginPlugin.kt:50` |
| OIDC client secret | `clientSecret` | login settings (`LoginSettings.oidcProviders`) | `identity/login/oidc/OidcSettings.kt:48` |
| Search user password | `searchUserPassword` | authentication settings | `auth/application/AuthenticationSettings.kt:58-60` |

Three observations that shape the design:

- The search user password defaults to the literal `reposilite-admin-secret` (`AuthenticationSettings.kt:60`). Every instance that
  was not configured has that value stored in clear.
- `httpProxy` is a secret by the annotation but not by the dashboard. The dashboard masks by key name, with the pattern
  `secret|password|access[-_]?key|endpoint` (`reposilite-frontend/src/components/renderers/sensitive.js`), so `httpProxy` is shown in
  clear when the server returns it. The mask in the settings API is the server's `@Sensitive`, not the dashboard's pattern.
- SMTP and OpenSearch credentials do not exist on main. A future setting only has to carry `@Sensitive`.

Not in the settings document, and therefore not part of this change:

- Access tokens. The legacy table stores a bcrypt hash of the secret (`reposilite-backend/src/main/kotlin/com/reposilite/token/AccessTokenSecurityProvider.kt:40-46`),
  and the identity table stores `secret_hash` (`identity/storage/IdentityTables.kt:102`, `identity/service/IdentityFacade.kt:249-259`).
- Login passwords. They are Argon2id hashes (`identity/login/PasswordHasher.kt`).
- Cluster bus credentials. They come from `INGOT_CLUSTER_PASSWORD` and the NATS credentials file, both read from the environment
  (`reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/cluster/ClusterSettings.kt:57-60`, `:103-116`).
- The database password, which sits in `configuration.cdn` (`configuration/local/LocalConfiguration.kt`). It is local configuration and
  out of scope.

### What is stored, exported and logged today

- **Stored.** Plain text in the `settings` row and in `configuration.shared.json`. The online database backup holds the same values
  (`openspec/changes/add-online-database-backup/design.md:38`).
- **Read by the API.** `GET /api/settings/domain/{name}` returns the stored settings. Masking happens only when `hideSettingsSecrets` is
  true. Its default is `false` (`configuration/local/LocalConfiguration.kt:167`), read in `SettingsEndpoints.kt:120-139`
  (the flag is read at line 127). A dashboard session therefore receives every secret in clear.
- **Downloaded.** The dashboard download is built in the browser from the same GET responses. `SettingsView.vue:247-254` serialises
  them with `settingsExport.js`, so the file holds the secrets in clear unless the operator turned the flag on. The download menu
  says "Secrets are not included" only when `secretsHidden` is set (`SettingsDownloadMenu.vue:106-111`).
- **Imported.** The dashboard overlays a file on the draft (`settingsImport.js`, `SettingsView.vue:384-405`) and saves through the
  ordinary `PUT` (`reposilite-frontend/src/store/client.js:604`). The server restores placeholders from the stored values
  (`SettingsEndpoints.kt:185-210`), but only while `hideSettingsSecrets` is on: the restore receives `placeholders = hideSecrets.get()`
  (line 198). With the flag off a `********` sent back is stored as a literal value.
- **Logged.** A failing shared document is logged through `SettingsSecrets.redactDocument`, which masks every `@Sensitive` value
  (`SharedConfigurationFacade.kt:166-168`, `SettingsSecrets.kt:110-120`). The audit event of a settings change names fields only
  (`SharedConfigurationFacade.kt:354-360`).
- **Support bundle.** `Redaction.kt` does not exist on main. The support bundle is still an open change, and its redactor is specified
  to reuse `SettingsSecrets.mask` (`openspec/changes/add-support-bundle/tasks.md:25-26`). This change keeps that contract: a masked
  value stays masked, and ciphertext is masked the same way.

### Environment and images

- The image reads only the process environment. `entrypoint.sh` takes `INGOT_OPTS` (with `REPOSILITE_OPTS` as fallback, line 8)
  and passes the jar its arguments. It never reads a file of variables.
- `docker-compose.yml:16-17` uses Compose `env_file: .env`. Compose resolves that file and puts the values in the environment, so the
  key can come from `.env` there, but the image does not know the file exists.
- The `_FILE` pattern already exists for local settings: `INGOT_LOCAL_KEYPASSWORD_FILE` reads the value from a file
  (`configuration/local/LocalConfigurationFactory.kt:114-130`, `configuration/local/LocalSettingFiles.kt:49`). The key file follows the same idea.
- The chart already mounts Kubernetes Secrets for the cluster credentials (`charts/ingot/values.yaml:33-36`,
  `charts/ingot/templates/deployment.yaml:225-228`). The key follows that shape in a later chart change. The chart is out of scope here.

### What already exists that this change builds on

- **Masking and restore.** `SettingsSecrets.mask`, `restore` and `transformSensitive` (`SettingsSecrets.kt:90-120`) already walk
  `@Sensitive` values, including lists matched by `@SettingsKey`. The encryption uses the same walk.
- **References.** Settings secret references are on main (commit `dfcfafa85`, `feat(configuration): resolve secret references in shared
  settings (#125)`): `${env:NAME}` and `${file:PATH}` are stored instead of a value (`configuration/shared/SettingsSecretReferences.kt`,
  `configuration/shared/SecretReferenceResolver.kt`). A reference is the stronger option where it can be used. This change encrypts
  the literal values that remain.
- **Consumer view.** `SharedConfigurationFacade` keeps the stored form and the effective form apart when references are on
  (`SharedConfigurationFacade.kt:102-124`, `:296-307`). Decryption uses the same split.
- **Nexus import.** Its design rejected encryption of stored passwords for the MVP because it "needs a key that every replica shares"
  (`openspec/changes/add-nexus-import/design.md:131`). This change provides exactly that shared key, with the start check that makes a
  wrong key visible. The Nexus job credentials are out of scope and can reuse the envelope later.

## Decisions

### 1. Encrypt the secret fields, not the whole document (default)

Field level encryption seals the `@Sensitive` literals and leaves everything else readable. The stored document stays a JSON document
that an operator can read, diff in Git and search, and the support bundle can show non-secret settings (repositories, cleanup,
branding) without a key. Only the values that must be secret are sealed. A diff of the stored document shows which field changed,
because the envelope of an unchanged value changes only when the value is written again (see decision 4).

The alternative is to encrypt the whole document into one envelope. That protects the non-secret settings too, and it has no way to
tell a secret from a repository name, so it is larger for every reader. It also breaks the things operators do today: the shared
file in Git cannot be reviewed, a support engineer cannot see the cleanup policy without the key, and the database row can no longer
be queried for a single domain. Whole document encryption is kept as an option for an operator who wants it, behind the same key
switch, as a follow-up; the envelope format reserves the `doc` marker for it (see decision 4).

### 2. Mark secrets with `@Sensitive`, and show it in the schema

`@Sensitive` (`SecretAnnotations.kt:27`) is the marker. It already exists, it is used by eight properties, and plugins can use it
without any change in the plugin API. Its target is the getter, so Kotlin properties are annotated with `@get:Sensitive` as today.

The JSON schema served to the dashboard (`/api/settings/schema/{name}`) does not carry the marker today, so the dashboard decides by
key name. This change adds `"x-ingot-secret": true` to the schema of every `@Sensitive` property, in the schema generation of
`configuration/shared/SchemaGenerator.kt`. The dashboard uses the flag and keeps the key-name pattern as a fallback
for older servers. A plugin marks its secrets with `@get:Sensitive` and gets encryption, masking and the flag at once. The annotation
stays in `com.reposilite.configuration.shared.api`, a package that plugins already import.

The annotation is checked at start: a `@Sensitive` property that is not a `String` already fails (`SettingsSecrets.kt:255`).

### 3. Reads: the dashboard never receives a value, the plain GET keeps its behaviour

Two reads exist after this change:

- `GET /api/settings/domain/{name}` without a parameter behaves as today. With `hideSettingsSecrets` false it returns the values, with
  it true it returns `********`. Scripts that read the API keep working, and the drop-in contract requires that.
- `GET /api/settings/domain/{name}?secrets=hidden` always returns `********` for a non-empty `@Sensitive` value, whatever the flag says.
  The dashboard always uses this form. Encrypted values are masked the same way; an envelope never leaves the server, and a
  reference stays as written, as it does today (`SettingsSecrets.kt:125`).

The `PUT` path changes in one place. The placeholder rule (`SettingsSecrets.restore` with `placeholders`) runs for every write, not only
while the flag is on. An unchanged secret in the dashboard therefore comes back as `********` and keeps its stored value. A
`********` without a stored counterpart is refused with `400` (`SettingsSecrets.kt:184-186`), as it already is when the flag is on.

Compatibility: a script that writes the literal text `********` as a password, with the flag off, used to store that text. After this
change it keeps the stored value or receives `400`. The open questions list this as a decision with the default stated.

Under the `ETag` (`SharedConfigurationFacade.kt:92-93`) the tag stays the hash of the plain canonical JSON. A sealed value changes its
bytes on every render (the nonce is random), so a tag over the stored bytes would change on every read.

### 4. Envelope

A sealed value is one string:

```
enc:v1:<kid>:<base64(nonce || ciphertext || tag)>
```

- `enc:v1` is the format and version. A reader refuses an unknown version with the path of the value, so a future format cannot be
  mistaken for text.
- `<kid>` is eight lower case hex characters, the first four bytes of `HMAC-SHA256(key, "ingot-settings-kid-v1")`. It names the key
  that sealed the value. It is not secret, and it is written to the log at start.
- The body is base64 (RFC 4648, standard alphabet) of a 12 byte nonce, the ciphertext and a 16 byte GCM tag, in that order.
- AES-256-GCM with the key as the cipher key. The additional authenticated data is `ingot-settings|v1|<domain>|<path>`, where `<path>`
  names the property, and a list entry is named by its `@SettingsKey` value, not by its index. Moving an envelope to another field,
  or to another list entry, fails to decrypt. A rename of a list entry through the API re-seals the value, because the restore
  takes the plain value and seals it under the new path.
- The nonce comes from `SecureRandom` through an injected source. The random nonce per value is the default. With a 96 bit random
  nonce the collision risk stays negligible for the number of settings writes an instance makes under one key; the document says
  so, and the rotation procedure (decision 7) limits the number of values under one key.

The marker `doc` in the place of the `kid` is reserved. A whole-document envelope (decision 1) would use `enc:v1:doc:...`, and it is not
part of this change.

### 5. Key sources, key identity and the start check

- **Sources.** `INGOT_SETTINGS_KEY` holds the key as base64 of 32 random bytes, for example `openssl rand -base64 32`.
  `INGOT_SETTINGS_KEY_FILE` names a file with the same content. One trailing line break is removed. Setting both is refused at start.
  The `REPOSILITE_SETTINGS_KEY` and `REPOSILITE_SETTINGS_KEY_FILE` spellings are read when the `INGOT_` names are not set, as the
  project convention requires for new variables (see open questions). The key is read from the environment only. It is not a field of
  `LocalConfiguration`, so a `configuration.cdn` line cannot set it.
- **Parsing.** A value that is not base64 of exactly 32 bytes stops the start with a message that names the variable and the length it
  found, never the value.
- **Key identity.** A second check value, `base64(HMAC-SHA256(key, "ingot-settings-check-v1"))`, is stored with the `kid`, in a new
  table `settings_key` (`kid`, `check`, `updated_at`), created with `SchemaUtils` as the other tables are. It is a new table, so no
  stored row changes shape.
- **Start check.** At start, with a key configured, the server compares the check value of the configured key with the stored one:
  - no stored check and no envelope in the document: the key is adopted and the check stored (first start with a key);
  - the stored check equals the configured key: start;
  - the stored check equals the previous key (decision 7): start, with a warning that a rotation is open;
  - anything else: stop with `The settings key does not match the key of this database (key id <kid>). Start the instance with the key the other instances use.`
- **No key, sealed values stored.** An instance without a key that finds envelopes in the document stops at start, naming the domain and
  the property. It does not start with unreadable secrets.

The check protects against a replica started with the wrong key, the common mistake when a key is copied between environments. It
protects against nothing else; the instance that holds the key can always read the secrets.

### 6. Drop-in behaviour and the optional and required modes

The drop-in contract (`CLAUDE.md`) outranks the wish to encrypt everything by default. An existing Reposilite data directory has plain
text secrets and no key. The default must not change that, and it does not:

- `INGOT_SETTINGS_ENCRYPTION` is `optional` by default. Without a key, the stored bytes, the start, the API responses and the
  download behave as today, except for the warning and the password on the download (decision 8). The upgrade rehearsal in
  `reposilite-backend/src/containerTest/kotlin/com/reposilite/image/UpgradeScenario.kt` verifies that an upstream Reposilite data
  directory starts unchanged with no key.
- With a key and `optional`, the database case encrypts on the first start: the document is stored again with the sealed values, in
  the same compare-and-set as any write, and one audit event `SETTINGS_SECRETS_REENCRYPTED` names the domains and fields, not the values.
  In the file case the file is not rewritten at start, because it is an operator's file; its values are sealed on the next save by the
  API, which is the only writer. Reading the file accepts plain and sealed values alike.
- `INGOT_SETTINGS_ENCRYPTION=required` stops the start without a valid key, and refuses every write of a `@Sensitive` value while no key
  is active, with `409` and the message that a key is required. It is the setting an operator uses to make encryption mandatory. It is
  not the default, because the default has to keep existing installations running (CLAUDE.md, drop-in).

The warning (decision 9) tells an operator who runs in `optional` mode without a key that secrets are stored in clear.

### 7. Rotation

Rotation changes the key in three rolling restarts, so no replica ever meets a value it cannot read:

1. Add the new key as `INGOT_SETTINGS_KEY_PREVIOUS` on every replica, with the old key still active. Each replica can now read the new
   key's values, and none writes them yet.
2. Set the new key as `INGOT_SETTINGS_KEY` and the old key as `INGOT_SETTINGS_KEY_PREVIOUS`, on every replica. Writes now use the new key,
   and the old values are still readable.
3. Run the console command `settings-reencrypt` once on one replica. It loads the stored document, seals every envelope with the active
   key and stores it with the check of the new key. Then remove `INGOT_SETTINGS_KEY_PREVIOUS` and restart.

The start check accepts the previous key while the check still names it, so a replica restarted during step 2 starts. The
`SETTINGS_SECRETS_REENCRYPTED` audit event records the rotation with the key identifiers. The console command follows the existing
command style (`token-generate`, `token-regenerate`, `reposilite-backend/src/main/kotlin/com/reposilite/token/infrastructure/AccessTokenCommands.kt`).
Manual re-encryption is the default; an automatic re-encryption after step 2 is an option listed as open question.

### 8. Password protected export and import

**Export.** `POST /api/settings/export` with the body `{"password": "..."}` and the permission of the settings area
(`ingot.admin.settings`, as the other settings routes use `managerOnly("settings")`). The server reads the saved state of every domain
under the state lock (`SharedConfigurationFacade.getDomainSnapshot`), not the dashboard's draft, and returns:

```json
{
  "format": "ingot-settings-export",
  "version": 1,
  "createdAt": "2026-11-11T10:00:00Z",
  "kdf": {"algorithm": "argon2id", "memoryKiB": 65536, "iterations": 3, "parallelism": 1, "salt": "<base64>"},
  "cipher": {"algorithm": "AES-256-GCM", "nonce": "<base64>"},
  "ciphertext": "<base64>"
}
```

The plain text inside the ciphertext is the canonical JSON of the saved document, with every secret in clear and every reference as
written. The key is `Argon2id(password, salt)` with the parameters in the file, computed by the Bouncy Castle `Argon2BytesGenerator`
that the login hashing already uses (`identity/login/PasswordHasher.kt`). The additional authenticated data is the canonical JSON of
the header (`format`, `version`, `createdAt`, `kdf`, `cipher`), so changing any header field fails the check. The response is
`application/json` with `Content-Disposition: attachment; filename="ingot-settings-<yyyy-mm-dd>.json"` and `Cache-Control: no-store`.
The date comes from an injected `Clock`.

The password must be at least 12 characters (open question). The export works without a settings key, because the key of the file is the
password and not the settings key. That makes the file the portable form: it restores an instance with another key, or a fresh instance.

**Import.** `POST /api/settings/import/open` with `{"password": "...", "export": {...}}` decrypts the file and returns the plain document.
It stores nothing. The dashboard then overlays the result on the draft, as it does for a plain file today, and the operator reviews
the changes and saves them through the normal `PUT` (`SettingsReviewSheet.vue`, `ConflictDialog.vue`). The `PUT` path then seals the
values with the local key. A wrong password, a changed header or ciphertext, an unknown version and a KDF cost above the bounds
(`memoryKiB` above 262144, `iterations` above 10, `parallelism` above 4) all return `400` with the same text, which does not say which
check failed.

The import accepts a file without the `format` header as a plain document, which is the file the dashboard writes today, so older files
keep working.

**Why review in the draft.** Import through the draft reuses the review of every change that exists, the conflict check against a newer
stored state, and the audit event of each saved domain (`SETTINGS_CHANGED`). A direct store on the server would need its own review and
conflict path.

### 9. Warning, audit and logs

- **Warning.** `FrontendSettingsResponse` (`reposilite-backend/src/main/kotlin/com/reposilite/frontend/api/FrontendSettingsResponse.kt:47`) gains
  `secretsEncrypted` (true when a key is active) and `plaintextSecrets`, the number of stored non-empty `@Sensitive` values that are not
  sealed and not references. The System group page of the dashboard shows an alert while `secretsEncrypted` is false and
  `plaintextSecrets` is greater than zero. The alert names the count and the key setting, never a value.
- **Audit.** Four actions are appended to `AuditAction` (`reposilite-backend/src/main/kotlin/com/reposilite/audit/api/AuditAction.kt`)
  after `UPLOAD_CHECKSUM_MISMATCH`: `SETTINGS_EXPORTED` (actor, domain count, export time), `SETTINGS_IMPORT_DECRYPTED` (actor, domain count),
  `SETTINGS_IMPORT_REFUSED` (actor, reason class `password` or `file`, never the message of the crypto library), and
  `SETTINGS_SECRETS_REENCRYPTED` (actor, reason `first-start` or `rotation`, key identifiers, count of values). None carries a value,
  a password or a key. The existing `SETTINGS_CHANGED` keeps its field list.
- **Logs.** The log lines of this change name a domain and a property and never a value. The envelope never reaches a log: the
  failure path (`SharedConfigurationFacade.kt:166-168`) already logs through `SettingsSecrets.redactDocument`, which masks envelopes
  as well as plain values, because it masks every non-empty `@Sensitive` string. A test asserts this with a captured appender.
- **Support bundle.** The redactor of `add-support-bundle` masks through `SettingsSecrets.mask`. This change extends the masking test
  of that change: an envelope and the key bytes must not appear in the bundle. If the support bundle lands after this change, its test
  adds the envelope case; if before, this change adds it. The key file content is never read by the bundle.

### 10. Plugins

Plugins mark a `@Sensitive` property and get encryption, masking and the schema flag with no further code.

A plugin reads its own settings through `SharedConfigurationFacade.getDomainSettings` (or the typed `getDomainSettings<T>()`), which
returns the consumer view: the plain values, decrypted at load and reference-resolved. A plugin cannot read another domain's secrets through
a settings API, because the API is the only route and it masks.

This is not a sandbox. Plugins run in the same JVM, and a plugin that wants another domain can call the facade. The guarantee is the
contract in `docs/gitbook/reference/plugin-api.md`: a plugin reads its own domain. No new reader API is added (open question). The
plugin ABI gate (`gate-plugin-abi`) is not changed.

### 11. Documentation and images

- `docs/gitbook/reference/environment-variables.md` gets the `INGOT_SETTINGS_KEY`, `INGOT_SETTINGS_KEY_FILE`, `INGOT_SETTINGS_KEY_PREVIOUS`,
  `INGOT_SETTINGS_KEY_PREVIOUS_FILE` and `INGOT_SETTINGS_ENCRYPTION` rows, with the `REPOSILITE_` spellings.
- `docs/gitbook/reference/settings-encryption.md` (new) is the reference: the envelope, the key sources, the start check, the modes, the
  failure rules, the rotation, and what is and is not protected.
- `docs/gitbook/how-to/encrypt-settings-secrets.md` (new) is the how-to: generate the key, mount it, check the warning, rotate, and go back
  to a build without this change (the console command `settings-decrypt` writes the plain values back, and the export is the backup
  to take first).
- `docs/gitbook/reference/settings-domains.md` documents `?secrets=hidden` and the `PUT` placeholder rule.
- `docs/gitbook/reference/http-api.md` documents the two new routes, `docs/gitbook/reference/audit-events.md` the four actions,
  `docs/gitbook/reference/upgrade-changes.md` the new table and the unchanged default.
- `docs/gitbook/how-to/back-up-and-restore.md` states that a backup of the database holds sealed values and needs the key that wrote it.
- The image and its entrypoint do not change. The key reaches the process through the environment, as the other variables do, and the
  chart change for the key is a follow-up.

## Threat model

Protected: someone who reads the database, a database backup, a copy of `configuration.shared.json` or a settings export without the
password. Protected: a support bundle and a log line, which never contain a value. Protected: a browser session, which never receives a
value. Protected: an export file taken to another place, once the password is strong.

Not protected: someone who controls the process environment or the mounted key file of an instance, because that instance can
decrypt. In a typical deployment the key sits in the same container spec as the database password, so the encryption protects the stored
data against theft of the data alone, not against theft of the whole deployment. The reference resolver (`${env:...}`, `${file:...}`)
and an external key manager are the stronger options, the first of which is on main and the second a follow-up.

## Open questions

Each question has the default this design uses. The maintainer decides before the code lands.

1. **Field or document encryption.** Default: field level (decision 1). Alternative: whole document, as an option behind the same key switch.
2. **Mandatory encryption.** Default: `optional`, so existing installations start unchanged. `required` is opt-in. Alternative: make
   `required` the default for new installations only, which needs an install-time signal the image does not have.
3. **Encryption on the first start.** Default: encrypt at start in database mode, on the next save in file mode (decision 6).
   Alternative: encrypt at start in file mode too, which rewrites an operator file.
4. **Dashboard read.** Default: the `?secrets=hidden` parameter, and the plain GET unchanged. Alternative: flip the default of
   `hideSettingsSecrets` to `true`, which breaks scripts that read the values, so it is not recommended for this change.
5. **Placeholder on write with the flag off.** Default: a `********` is a placeholder on every write, so an unchanged secret keeps its
   stored value, and a `********` without a stored counterpart gets `400`. This changes the behaviour for a script that sets a secret to
   that literal text. Alternative: keep the flag as the only switch and let the dashboard send the value it has.
6. **Environment names.** Default: `INGOT_` names with `REPOSILITE_` fallbacks, as the project requires for new variables and as
   `add-settings-secret-references` does. Alternative: `INGOT_` only, which the project rule does not permit for an existing prefix family.
7. **Automatic rotation.** Default: the console command `settings-reencrypt`, run by the operator. Alternative: re-encrypt on the first
   write by a replica that holds both keys, which finishes rotation without a command and leaves nothing to forget.
8. **Export password.** Default: at least 12 characters, Argon2id with 64 MiB, 3 iterations and 1 lane, with the cost bounds of decision 8.
   Alternative: a longer minimum, or a passphrase generator in the dashboard. The bounds are only a limit on what a decrypt accepts.
9. **Export of drafts.** Default: the export holds the saved state only, because the browser never holds plain secrets and a draft could not
   be sealed on the server. Alternative: the dashboard encrypts a draft locally, which needs an Argon2id implementation in the browser bundle.
10. **Plain download in the dashboard.** Default: the plain JSON download is removed from the dashboard, so the only file the download
    produces is the encrypted export. The dashboard's plain download would hold only `********` placeholders, so keeping it is safe
    too; it is removed to avoid two downloads with different meanings.
11. **Brute force on the import password.** Default: no limiter beyond the Argon2id cost, because the attacker needs the file first.
    Alternative: reuse the failed-attempt limiter of `AuthenticationSettings` for the decrypt route, keyed by principal.
12. **Plugin reader API.** Default: none. Plugins read their own domain through the existing reference (decision 10). Alternative: a scoped
    reader that checks the domain of the caller, which is a convention and not a sandbox.
13. **Downgrade.** Default: the console command `settings-decrypt` writes the plain values back and asks for the key first, with the
    export as backup. Alternative: documentation only, with an instruction to re-enter the secrets.
14. **Local secrets.** Default: out of scope. The database password in `configuration.cdn` stays as it is, and the `_FILE` mechanism is
    the advice. Alternative: a follow-up change that seals local values.
15. **Split.** Default: one change with two capabilities, one PR title. If review asks for a smaller PR, the export (decision 8, the
    routes, the dashboard password dialogs and the audit actions) can move to a second `feat(settings)` change that depends on the
    `?secrets=hidden` read of this change. Both would ship under the same type.
16. **Support bundle order.** Default: the masking test lands in whichever change comes second. Alternative: this change waits for the
    support bundle.
