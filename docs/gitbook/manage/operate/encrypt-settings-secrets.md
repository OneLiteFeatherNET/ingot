# Encrypt the settings secrets

This guide turns on encryption of the secret values in the shared settings, rotates the key and goes back to a build without it. The rules behind each step are in [Settings encryption](../../references/settings/settings-encryption.md).

Nothing changes for an instance that does not set a key. Its secrets stay as they were, and the existing data directory starts unchanged.

## 1. Generate the key

```sh
openssl rand -base64 32
```

Keep the output. Anyone with the key and the database can read the secrets, so store it where the database password is stored, in your secret manager or in a file that only the service account reads.

## 2. Give the key to the instance

With Docker Compose, put the key in a secret file and name the file:

```yaml
services:
  ingot:
    image: ghcr.io/onelitefeathernet/ingot:latest
    environment:
      INGOT_SETTINGS_KEY_FILE: /run/secrets/settings_key
    secrets:
      - settings_key

secrets:
  settings_key:
    file: ./settings_key.txt
```

Use `INGOT_SETTINGS_KEY` instead when the environment is the only place you keep it. Set one of the two, not both.

Every instance that shares the database must get the same key. A different key stops the instance at start and names the stored key identifier.

## 3. Start and check the result

On the first start with the key, Ingot stores any plain secret again with envelopes, once. The log shows a line such as `Settings encryption | Sealed 3 stored secret values`, and the audit log gets one `SETTINGS_SECRETS_REENCRYPTED` event with `reason` `first-start`.

To confirm the result, look at the stored settings: a secret value now begins with `enc:v1:`. The database row and the shared file hold no plain value once the event is in the audit log.

To refuse plain secrets from now on, set `INGOT_SETTINGS_ENCRYPTION=required` once every instance has the key. An instance without a key then stops at start, and a save of a secret without a key is refused.

## 4. Rotate the key

Rotation takes three rolling restarts.

1. Generate the new key. Give it to every instance as `INGOT_SETTINGS_KEY_PREVIOUS_FILE` (or `INGOT_SETTINGS_KEY_PREVIOUS`), with the old key still active. Restart each instance.
2. Make the new key active and the old key the previous key on every instance. Restart each instance. New writes now use the new key.
3. On one instance, run the console command `settings-reencrypt`. It seals every stored value with the new key, including the seeds of authenticator codes, and moves the key record. A seed that neither key opens is left unchanged and named in the output: its owner has to enrol the authenticator again. Then remove `INGOT_SETTINGS_KEY_PREVIOUS` from every instance and restart them.

{% hint style="warning" %}
The command `settings-reencrypt` does not re-seal the authenticator seeds of [second factors](../../configure/authentication/configure-second-factors.md). Keep `INGOT_SETTINGS_KEY_PREVIOUS` set on every instance for as long as users hold authenticator codes that were enrolled before the rotation. Removing it earlier makes those codes fail, and the affected users need an administrator reset of their factor. Recovery codes and passkeys are not affected.
{% endhint %}

A restart that happens between steps is safe. An instance that still holds only the old key stops at start once the record names the new key, and that is the signal to finish step 2 for it.

## 5. Go back to a build without encryption

Take an export first, from the dashboard's download, and keep it with its password.

1. On one instance, run the console command `settings-decrypt`. It needs the active key, reads the values and writes them back in clear.
2. Stop every instance.
3. Remove `INGOT_SETTINGS_KEY`, `INGOT_SETTINGS_KEY_FILE` and `INGOT_SETTINGS_ENCRYPTION` from the environment.
4. Start the older build.

A start with the key still set would seal the values again, so the key comes out before the older build starts.

## Settings that hold a secret

The secrets are the S3 access and secret keys, the mirror credentials and proxy URLs, the LDAP bind password, the OIDC client secret and the search user password. A reference such as `${env:NAME}` is not sealed, because it names a secret without holding one. See [Settings domains](../../references/settings/settings-domains.md).

The seeds of authenticator apps for [second factors](../../configure/authentication/configure-second-factors.md) are not settings values, but they are sealed with the same key. An instance without a key refuses to enrol an authenticator app and says why. Passkeys and recovery codes need no key. Without the key, an enrolled authenticator app cannot check its codes, so the key must be kept for as long as those codes are in use.

## Troubleshooting

- **The instance stops with "does not match the key of this database".** The key differs from the one the other instances use. Give it the right key. The message names the stored key identifier, which you can compare with the first eight hex characters of the `enc:v1:` envelopes in the database.
- **The instance stops with "is encrypted, but no settings key is configured".** The database holds sealed values and this instance has no key. Give it the key, or restore a backup taken before encryption.
- **The instance stops with "is required, but no settings key is configured".** Set the key, or set `INGOT_SETTINGS_ENCRYPTION` back to `optional`.
