# Settings encryption

The secret values of the shared settings can be stored encrypted. This page describes what is sealed, how the key is found, what the server checks at start, and what is not protected. The steps to turn it on, rotate the key or go back are in [Encrypt the settings secrets](../how-to/encrypt-settings-secrets.md).

## What is sealed

Every literal value of a setting marked as secret is sealed when a key is active. The secret settings are the S3 keys, the mirror credentials and proxy URLs, the LDAP bind password, the OIDC client secret and the search user password. A plugin marks its own secrets with `@Sensitive` and gets the same treatment.

Values that are not sealed stay as written: empty values, settings secret references such as `${env:NAME}` when [secret references](settings-domains.md) are on, and every setting that is not a secret.

The access tokens, the login passwords and the database password in `configuration.cdn` are not part of this. See [what is not protected](#what-is-not-protected).

## The envelope

A sealed value is one string:

```
enc:v1:<kid>:<base64(nonce || ciphertext || tag)>
```

- `enc:v1` is the format and its version. A reader refuses an unknown version and names the path of the value. It never treats the text as a plain value.
- `kid` is eight lower case hex characters that name the key that sealed the value. It is not secret.
- The body is a random 12 byte nonce, the AES-256-GCM ciphertext and the 16 byte tag, in base64.
- The additional authenticated data is the domain and the property path. A list entry is named by its key, not by its position. An envelope moved to another field, or to another list entry, does not open.

Because the nonce is random, a sealed value changes its bytes when it is written again. An unchanged value keeps its envelope, so the stored document changes only where a value changed.

## The key

The key is 32 random bytes, given as base64. Generate one with `openssl rand -base64 32`.

| Variable | Meaning |
| --- | --- |
| `INGOT_SETTINGS_KEY` | The key itself. |
| `INGOT_SETTINGS_KEY_FILE` | A file that holds the key. One trailing line break is ignored. |

Set one of the two, not both: the startup stops if both are set. The `REPOSILITE_` spellings are read when the `INGOT_` names are not set. The key is read from the environment or from the file it names. It is never read from the shared settings or from `configuration.cdn`, so a settings document cannot replace it.

A key that is not base64 of exactly 32 bytes stops the startup. The message names the variable and the length found, never a part of the key.

## Start checks

At start, before the shared settings are loaded, the server compares the configured key with the key record of the database. The record holds the key identifier and a check value, which is an HMAC of the key under a fixed label. It holds no key bytes.

| Situation | Result |
| --- | --- |
| No key, no sealed value | Starts as before. Nothing is checked or written. |
| Key, no record, no sealed value | Starts and records the key. This is the first start with a key. |
| Key, record matches | Starts. |
| Key, record of the previous key (`INGOT_SETTINGS_KEY_PREVIOUS`) | Starts with a warning that a rotation is open. |
| Key, record of another key | Stops. The message names the stored key identifier, never a key. |
| No key, sealed values stored | Stops. The message names the first affected domain and property. |
| Key missing, `INGOT_SETTINGS_ENCRYPTION=required` | Stops, and names the variables. |

The record is stored in the configuration table under the name `settings-key`. Several instances that share a database must use the same key. The check makes a wrong key visible at start instead of as a decryption error later.

## Modes

`INGOT_SETTINGS_ENCRYPTION` is `optional` by default.

- **`optional`** starts as before without a key. With a key, a database that still holds plain secrets is stored again with envelopes once, at start, in one compare-and-set write. A shared settings file managed by the operator is not rewritten at start: its values are sealed by the next save through the API.
- **`required`** stops the start without a key and refuses to store a secret while no key is active. Use it once every instance has a key.

Any other value stops the startup.

## Failure rules

- A value that cannot be opened fails the load of its domain. At start the instance stops. On a reload the previous settings stay in place and an error is logged that names the domain and the property.
- Log lines, audit events and the support bundle never contain an envelope, a key, a password or a plain secret value. The key identifier may appear in logs and in audit events.
- The settings API never returns an envelope. A read with `?secrets=hidden` returns `********` for every non-empty secret.

## Rotation

Rotation uses `INGOT_SETTINGS_KEY_PREVIOUS` (or its `_FILE` spelling) for decryption only. Three steps, each a rolling restart, so no replica meets a value it cannot read:

1. Add the new key as the previous key on every replica, with the old key still active.
2. Make the new key active and the old key the previous key on every replica. Writes now seal with the new key.
3. Run `settings-reencrypt` once on one replica. It seals every value with the active key, stores the check of the active key, and records one `SETTINGS_SECRETS_REENCRYPTED` event with the two key identifiers. Then remove the previous key and restart.

A replica that holds only the previous key stops at start once the record names the new key.

## Downgrade

`settings-decrypt` writes the plain values back and clears the key record. It needs the active key. Take an export first, then stop the instance, remove the key variables and start the older build. A start with the key set again seals the values again.

## Export and import

The settings can be exported with a password, independently of the key. The file holds the saved state with the secrets in clear inside the ciphertext, so it can restore an instance that has another key or a fresh one. The file is protected by the password, which must be at least 12 characters, through Argon2id (64 MiB, three passes, one lane, with declared costs bounded at 256 MiB, ten passes and four lanes) and AES-256-GCM. See the [HTTP API](http-api.md) for the routes.

## What is not protected

- Someone who controls the process environment or the mounted key file of an instance can decrypt. In a typical deployment the key sits in the same container spec as the database password, so the encryption protects the stored data against theft of the data alone, not of the whole deployment.
- The database password in `configuration.cdn`, the cluster bus password and the TLS key password. They keep their own `_FILE` and environment mechanisms.
- Access tokens and login passwords. They are already stored as hashes.
- Encryption of the database as a whole, of the search index and of artifacts.
- External key managers. The key is read from the environment or a file. An external provider is a follow-up.

A backup of the database holds the sealed values and restores only with the key that wrote it. See [Back up and restore](../how-to/back-up-and-restore.md).
