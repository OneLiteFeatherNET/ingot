# Spec Delta

## Purpose

A local setting can be read from a file, so a secret does not have to be written into the manifest, and no existing variable,
property or default changes.

## ADDED Requirements

### Requirement: File spelling of a local setting

The server SHALL read the value of a known local setting from the file named by `INGOT_LOCAL_<KEY>_FILE`. When that variable is
not set, it SHALL read the file named by `REPOSILITE_LOCAL_<KEY>_FILE`. When both are set, the `INGOT_` variable SHALL win.

#### Scenario: Secret read from a file

- **WHEN** `INGOT_LOCAL_KEYPASSWORD_FILE` names a file whose content is `s3cret` followed by a line break
- **THEN** the key `keyPassword` has the value `s3cret`

#### Scenario: Legacy spelling only

- **WHEN** only `REPOSILITE_LOCAL_PORT_FILE` is set and names a file whose content is `8181`
- **THEN** the key `port` has the value `8181`

#### Scenario: Both spellings

- **WHEN** `INGOT_LOCAL_PORT_FILE` names a file with `8181` and `REPOSILITE_LOCAL_PORT_FILE` names a file with `8080`
- **THEN** the key `port` has the value `8181`

### Requirement: Line breaks at the end of a file

The server SHALL remove the trailing `\n` and `\r` characters of a file's content, and SHALL keep every other character of the
value, including spaces.

#### Scenario: Windows line break

- **WHEN** the file content is `s3cret` followed by `\r\n`
- **THEN** the value is `s3cret`

#### Scenario: Inner and leading spaces

- **WHEN** the file content is ` a b ` followed by `\n`
- **THEN** the value is ` a b `

### Requirement: Plain variable wins over file variable

When one source (the environment or the system properties) sets both the plain variable and the `_FILE` variable of a key, the
server SHALL use the plain variable, SHALL NOT read the file, and SHALL log one warning that names both variables.

#### Scenario: Both set in the environment

- **WHEN** `INGOT_LOCAL_PORT` is `8181` and `INGOT_LOCAL_PORT_FILE` is set
- **THEN** the key `port` has the value `8181`, the file is not opened, and one warning names `INGOT_LOCAL_PORT` and `INGOT_LOCAL_PORT_FILE`

#### Scenario: Missing file ignored when the plain variable is set

- **WHEN** `INGOT_LOCAL_PORT` is `8181` and `INGOT_LOCAL_PORT_FILE` names a file that does not exist
- **THEN** the server starts and the key `port` has the value `8181`

#### Scenario: Environment file under a system property

- **WHEN** `INGOT_LOCAL_PORT_FILE` names a file with `8181` and the system property `ingot.local.port` is `8282`
- **THEN** the key `port` has the value `8282`

### Requirement: Empty file

The server SHALL accept a file whose content is empty after the removal of line breaks, and SHALL give the key the empty string.

#### Scenario: Empty secret

- **WHEN** `INGOT_LOCAL_KEYPASSWORD_FILE` names an empty file
- **THEN** the key `keyPassword` has the value `""`

### Requirement: Unreadable file stops the start

The server SHALL stop the start before any port is bound when a file named by a `_FILE` variable of a known key does not exist,
is not a readable regular file, or does not contain valid UTF-8. The stop SHALL print one line that names the variable as the
operator set it and the path, SHALL NOT contain the content of the file, and SHALL exit with status 1 without a stack trace.

#### Scenario: Missing file

- **WHEN** `INGOT_LOCAL_KEYPASSWORD_FILE` names `/run/secrets/missing`, which does not exist
- **THEN** the start stops with status 1, and the single line names `INGOT_LOCAL_KEYPASSWORD_FILE` and `/run/secrets/missing`

#### Scenario: Directory instead of file

- **WHEN** `REPOSILITE_LOCAL_KEYPASSWORD_FILE` names a directory
- **THEN** the start stops with status 1, and the line names `REPOSILITE_LOCAL_KEYPASSWORD_FILE`

#### Scenario: Content is never printed

- **WHEN** the file cannot be decoded as UTF-8
- **THEN** the start stops with status 1 and the line contains neither the bytes of the file nor a decoded fragment of them

### Requirement: Unknown key with a file variable

A `_FILE` variable whose key is not a local setting SHALL be reported as an unknown setting, as an unknown plain variable is, and
the server SHALL NOT read its file.

#### Scenario: Unknown key

- **WHEN** `INGOT_LOCAL_NOSUCHKEY_FILE` names a file that does not exist
- **THEN** the server logs a warning that names the variable, does not read the file and starts

### Requirement: System property form

The server SHALL read `ingot.local.<key>.file` and `reposilite.local.<key>.file` with the same rules as the environment form. The
system property layer SHALL keep taking precedence over the environment layer.

#### Scenario: Property file

- **WHEN** the system property `ingot.local.keyPassword.file` names a file whose content is `s3cret`
- **THEN** the key `keyPassword` has the value `s3cret`

#### Scenario: Property file under a plain environment variable

- **WHEN** `INGOT_LOCAL_KEYPASSWORD` is `envsecret` and the system property `ingot.local.keyPassword.file` names a file whose content is `s3cret`
- **THEN** the key `keyPassword` has the value `s3cret`

### Requirement: Values are read once, at start

The server SHALL read each file named by a `_FILE` variable once, during the start. A change to a file after the start SHALL NOT
change the running configuration.

#### Scenario: File changed while running

- **WHEN** the content of a file named by `INGOT_LOCAL_KEYPASSWORD_FILE` changes after the start
- **THEN** the key `keyPassword` keeps the value read at the start

### Requirement: Values stay out of the log

The server SHALL NOT log the content of a file. Its log lines about a file-read value SHALL name the variable and the key and follow
the existing rule for values.

#### Scenario: Start log

- **WHEN** `INGOT_LOCAL_KEYPASSWORD_FILE` names a file whose content is `s3cret`
- **THEN** no log line contains the full string `s3cret`
