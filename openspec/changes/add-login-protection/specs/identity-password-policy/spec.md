# Spec Delta

## Purpose

The rules a new password must pass: length, the absence of composition rules, and the check against breached passwords.
None of the rules applies at login, so an existing user is never locked out by a new rule.

## ADDED Requirements

### Requirement: Length limits

A new password SHALL have at least `login.passwordPolicy.minLength` characters, which defaults to 12 and SHALL NOT be set
below 8. A new password SHALL have at most 256 characters. A value of `minLength` outside 8 to 256 SHALL be refused by the
settings validation with a message that names the key.

#### Scenario: A password of 11 characters is refused under the default

- **WHEN** a user sets a password of 11 characters with the default settings
- **THEN** the answer is a validation error that states the minimum of 12 characters

#### Scenario: The minimum cannot be lowered below 8

- **WHEN** an operator saves `minLength: 6`
- **THEN** the settings are refused with a violation on `login.passwordPolicy.minLength`

### Requirement: No composition rules and no expiry

The system SHALL NOT require a password to contain particular character classes, SHALL NOT reject a password for containing a
space or a Unicode character, and SHALL NOT force a periodic change. The length of a password SHALL be measured in Unicode
code points, and a password that is refused for length SHALL be refused with the same message regardless of its content.

#### Scenario: A passphrase of 12 lower case words with spaces is accepted

- **WHEN** a user sets a 30 character password with lower case letters and spaces
- **THEN** the password is accepted

### Requirement: Existing passwords are not affected

A change of the minimum length or of the breached-password mode SHALL NOT change any stored password, and SHALL NOT require a
user to change a password at login. A user whose password is shorter than the new minimum SHALL be asked for a new one only when
the user changes it.

#### Scenario: Raising the minimum does not lock a user out

- **WHEN** the minimum is raised from 12 to 15, and a user with a 12 character password signs in
- **THEN** the sign-in succeeds and no password change is required

### Requirement: Breached-password check modes

`login.passwordPolicy.breachCheck.mode` SHALL be one of `OFF`, `RANGE` and `OFFLINE`, and SHALL default to `OFF`.

#### Scenario: The default makes no external call

- **WHEN** the mode is not set
- **THEN** no breach check is made and no request leaves the instance

### Requirement: Range mode privacy

In mode `RANGE` the system SHALL compute the SHA-1 of the new password, send only its first five hexadecimal characters to the
configured range URL with `Add-Padding: true`, and compare the returned suffixes with the rest of the hash, locally.

#### Scenario: Only the prefix leaves the instance

- **WHEN** the mode is `RANGE` and a user sets the password `correct horse battery staple`
- **THEN** the outgoing request contains only the first five hexadecimal characters of its SHA-1, and the password is not sent

### Requirement: Offline mode loading

In mode `OFFLINE` the system SHALL read the file at `login.passwordPolicy.breachCheck.file` once at startup. A missing or
unreadable file SHALL fail the startup with a message that names the key, and the mode SHALL NOT fall back to `OFF`.

#### Scenario: A missing offline file stops the startup

- **WHEN** the mode is `OFFLINE` and the configured file does not exist
- **THEN** the startup fails with a message that names `login.passwordPolicy.breachCheck.file`

### Requirement: When the check runs

The check SHALL run when a password is set by the user, changed by the user, reset by an administrator, or created during setup.
It SHALL NOT run at login.

#### Scenario: A breached password is refused

- **WHEN** the mode is `OFFLINE`, the file contains the SHA-1 of the new password, and the user sets it
- **THEN** the answer is a validation error stating that the password appears in breached lists, and the password is not stored

#### Scenario: The check does not run at login

- **WHEN** a user whose password is in the breached list signs in
- **THEN** the sign-in succeeds and no breach check is made

### Requirement: Failure of the external check

When the mode is `RANGE` and the service does not answer, the password change SHALL be allowed by default and an audit event
`PASSWORD_BREACH_CHECK_UNAVAILABLE` SHALL be written. When `breachCheck.failClosed` is `true`, the change SHALL be refused with
`503` and `Retry-After`. An unavailable check SHALL NOT be cached as a pass.

#### Scenario: An outage does not block a reset by default

- **WHEN** the range service times out during an administrator's reset and `failClosed` is false
- **THEN** the reset succeeds and the audit trail contains `PASSWORD_BREACH_CHECK_UNAVAILABLE`

#### Scenario: Fail-closed refuses the change

- **WHEN** the range service times out and `failClosed` is true
- **THEN** the change is refused with `503` and a `Retry-After` header, and the password is not stored

### Requirement: Timeouts and privacy of the external call

The range request SHALL time out after 3 seconds to connect and 3 seconds to read. The request SHALL carry no user name, no
address and no cookie, and its response SHALL NOT be stored.

#### Scenario: The request carries no identity

- **WHEN** the range request is sent
- **THEN** it has no `Authorization` and no `Cookie` header, and its path is the five character prefix only
