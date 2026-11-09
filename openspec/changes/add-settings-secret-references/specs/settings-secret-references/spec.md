## ADDED Requirements

### Requirement: Opt-in switch

The server SHALL resolve references only when the environment variable `INGOT_SETTINGS_SECRET_REFERENCES` is `true`, or
`REPOSILITE_SETTINGS_SECRET_REFERENCES` is `true` when the `INGOT_` variable is not set. The default SHALL be `false`. The switch
SHALL NOT be read from the shared settings document.

#### Scenario: Switch off

- **WHEN** the switch is not set and a `@Sensitive` value is `${env:S3_SECRET}`
- **THEN** the server uses the text `${env:S3_SECRET}` as the value and reads no variable

#### Scenario: Switch set through the legacy spelling

- **WHEN** only `REPOSILITE_SETTINGS_SECRET_REFERENCES=true` is set and a `@Sensitive` value is `${env:S3_SECRET}`
- **THEN** the server resolves the reference

#### Scenario: Switch in the shared document

- **WHEN** the shared settings document contains a value that would turn the switch on
- **THEN** the switch stays off, because it is read from the environment only

### Requirement: Reference syntax

With the switch on, the server SHALL treat `${env:NAME}` and `${file:PATH}` inside a `@Sensitive` string value as references,
SHALL replace each with the value of the variable or the content of the file, and SHALL accept several references in one value.
The server SHALL treat `$${` as the literal text `${`, and SHALL leave every other `${` text unchanged.

#### Scenario: Reference inside a value

- **WHEN** the endpoint is `https://${env:S3_USER}:${env:S3_PASS}@s3.example.test` and both variables are allowed and set to `u` and `p`
- **THEN** the resolved endpoint is `https://u:p@s3.example.test`

#### Scenario: Escaped reference

- **WHEN** a `@Sensitive` value is `$${env:NAME}`
- **THEN** the resolved value is `${env:NAME}` and no variable is read

#### Scenario: Other dollar text

- **WHEN** a `@Sensitive` value is `${HOME}`
- **THEN** the value stays `${HOME}`

#### Scenario: Unclosed reference

- **WHEN** a `@Sensitive` value is `${env:S3_SECRET`
- **THEN** the value is refused as a malformed reference, and the message does not contain the value

### Requirement: Scope of references

The server SHALL resolve references only in `@Sensitive` string properties of shared settings. Other properties, the local
configuration file and the dashboard SHALL keep their values as written.

#### Scenario: Reference in a plain setting

- **WHEN** the bucket name is `${env:BUCKET}` and the switch is on
- **THEN** the bucket name stays `${env:BUCKET}`

### Requirement: Environment variable allowlist

The server SHALL resolve `${env:NAME}` only when `NAME` is listed in `INGOT_SETTINGS_SECRET_ENV`, a comma separated list of
variable names, or `REPOSILITE_SETTINGS_SECRET_ENV` when the `INGOT_` variable is not set.

#### Scenario: Variable not listed

- **WHEN** the switch is on, `INGOT_SETTINGS_SECRET_ENV` lists `S3_SECRET`, and a value is `${env:DB_PASSWORD}`
- **THEN** the reference is refused and the message names `DB_PASSWORD` and not its value

### Requirement: File directory allowlist

The server SHALL resolve `${file:PATH}` only when the canonical path of the file lies inside a directory listed in
`INGOT_SETTINGS_SECRET_FILES`, a comma separated list of absolute directories, or `REPOSILITE_SETTINGS_SECRET_FILES` when the
`INGOT_` variable is not set.

#### Scenario: File outside the allowed directories

- **WHEN** `INGOT_SETTINGS_SECRET_FILES` lists `/run/secrets` and a value is `${file:/app/data/reposilite.db}`
- **THEN** the reference is refused

#### Scenario: Path leaves the allowed directory

- **WHEN** `INGOT_SETTINGS_SECRET_FILES` lists `/run/secrets` and a value is `${file:/run/secrets/../../etc/passwd}`
- **THEN** the reference is refused

### Requirement: Empty allowlists refuse everything

Both allowlists SHALL default to empty, and an empty list SHALL refuse every reference of its kind.

#### Scenario: Empty lists

- **WHEN** the switch is on and both lists are empty
- **THEN** every reference is refused and the server resolves nothing

### Requirement: File content

When the server reads a file for a reference, it SHALL remove trailing line break characters, SHALL keep every other character,
SHALL require valid UTF-8, and SHALL treat an empty result as the empty string.

#### Scenario: Trailing line break

- **WHEN** the file `/run/secrets/s3` contains `s3cret` followed by `\n`
- **THEN** the resolved value is `s3cret`

#### Scenario: Invalid UTF-8

- **WHEN** the file contains bytes that are not valid UTF-8
- **THEN** the reference fails, and the message names the path and does not contain the bytes

### Requirement: Stored and shown as written

The server SHALL store a document with its references unchanged. The settings API SHALL return a reference as written, even when
`hideSettingsSecrets` is on. A literal secret in a `@Sensitive` value SHALL still be replaced with `********` when
`hideSettingsSecrets` is on. A resolved value SHALL NOT be returned by the settings API, written to a stored document, placed in
the audit event, or written to a log line.

#### Scenario: Reference is stored literally

- **WHEN** a client writes the secret key `${file:/run/secrets/s3}` through the settings API and the switch is on
- **THEN** the stored document holds `${file:/run/secrets/s3}` and the next read of the setting returns `${file:/run/secrets/s3}`

#### Scenario: Literal secret stays hidden

- **WHEN** `hideSettingsSecrets` is on and the secret key is the literal `s3cret`
- **THEN** the settings API returns `********`

#### Scenario: Resolved value never logged

- **WHEN** the switch is on, a reference resolves to `s3cret`, and the document is loaded and rendered
- **THEN** no captured log line and no audit event contains `s3cret`

### Requirement: Consumers use the resolved value

The server SHALL give every consumer of a `@Sensitive` setting the resolved value when the switch is on. These consumers are the
S3 storage provider, the LDAP login and connection check, the OIDC login, the mirror credentials and the mirror HTTP proxy.

#### Scenario: S3 keys resolved

- **WHEN** the switch is on, `INGOT_SETTINGS_SECRET_FILES` lists `/run/secrets`, and the S3 access key is `${file:/run/secrets/ak}` with the file containing `AKIA-test`
- **THEN** the S3 client is built with the access key `AKIA-test`

### Requirement: When references are resolved

The server SHALL resolve references when a settings document is loaded at start, when the shared configuration is reloaded, and
when a newer document is read from the database. The server SHALL NOT read a referenced file or variable for each request.

#### Scenario: Rotated file

- **WHEN** the file behind `${file:/run/secrets/s3}` changes and the shared configuration is reloaded
- **THEN** the consumers use the new value from the next reload on, and the earlier value is not used after that reload

### Requirement: Start failure

The server SHALL stop the start of an instance when a reference of its shared configuration cannot be resolved, unless the
ignore-errors startup option is set, as it is for any other broken shared configuration.

#### Scenario: Missing file at start

- **WHEN** the switch is on and the file behind `${file:/run/secrets/s3}` does not exist at start
- **THEN** the start stops, and the message names the domain, the property and the path `/run/secrets/s3`, and does not contain a value

### Requirement: Reload failure keeps previous settings

On a reload or a refresh, the server SHALL keep the previous resolved settings when a reference cannot be resolved, and SHALL
log one error.

#### Scenario: Missing file on reload

- **WHEN** the file behind a reference is missing during a reload
- **THEN** the previous resolved settings stay in place and one error is logged

### Requirement: Write refusal

On a write through the settings API, the server SHALL answer `400` and SHALL store nothing when a reference is refused or cannot
be resolved.

#### Scenario: Write with an unresolvable reference

- **WHEN** a client writes `${env:UNSET_VARIABLE}` as a secret through the settings API and the variable is allowed but not set
- **THEN** the answer is `400`, the message names `UNSET_VARIABLE`, and the stored document is unchanged

### Requirement: Failure messages name the reference, never its value

Every message about a failing reference SHALL name the domain, the property and the reference, and SHALL NOT contain a resolved
value.

#### Scenario: Message names the property, never the value

- **WHEN** a reference to a missing file fails at start, on a reload or on a write
- **THEN** the message names the domain, the property and the reference, and contains no resolved value

### Requirement: Behaviour without the switch

When the switch is off, the server SHALL store, return, log and use every setting as it did before this change.

#### Scenario: Unchanged behaviour

- **WHEN** the switch is off and a shared configuration holds only literal values
- **THEN** the settings API, the stored document and the consumers behave exactly as on main
