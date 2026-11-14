# Spec Delta

## Purpose

Browser session lifetimes and their rules. The idle timeout is new and off by default. The other rules already exist and are
pinned by this change, so that they cannot be weakened without a failing test.

## ADDED Requirements

### Requirement: Optional idle timeout

`login.idleTimeoutMinutes` SHALL default to `0`, which keeps the refresh lifetime as the only idle limit. A value greater than
zero SHALL be at least 5 and SHALL NOT exceed the refresh lifetime in minutes; otherwise the settings SHALL be refused with a
violation on that key. When it is set, a refresh token SHALL expire `idleTimeoutMinutes` after its last use, or at the
refresh lifetime or the absolute end of the family, whichever comes first.

#### Scenario: The default keeps the existing idle time

- **WHEN** `idleTimeoutMinutes` is not set
- **THEN** a refresh token issued now expires after the refresh lifetime of 168 hours, as before

#### Scenario: An idle session ends after the timeout

- **WHEN** `idleTimeoutMinutes` is 30, a browser refreshes at minute 0, and the next refresh comes at minute 31
- **THEN** the refresh is refused, the family is revoked, and the browser must sign in again

#### Scenario: A value below five is refused

- **WHEN** an operator saves `idleTimeoutMinutes: 2`
- **THEN** the settings are refused with a violation on `login.idleTimeoutMinutes`

### Requirement: Session cookies

Session and refresh cookies SHALL be `HttpOnly`, SHALL carry `SameSite=Lax`, and SHALL carry `Secure` when the request is secure.
The refresh cookie SHALL be limited to the path of the authentication endpoints. The CSRF cookie SHALL NOT be `HttpOnly`,
because the page reads it to send the CSRF header. These rules SHALL be checked by tests against the current implementation
before any other requirement of this capability changes.

#### Scenario: A secure request sets Secure on every session cookie

- **WHEN** a login is answered over HTTPS, or through a trusted proxy that forwards `X-Forwarded-Proto: https`
- **THEN** each `Set-Cookie` of the session, refresh and CSRF cookies carries `Secure`

#### Scenario: The refresh cookie is not sent to other paths

- **WHEN** the refresh cookie is set
- **THEN** its `Path` is the authentication path and not the root of the application

### Requirement: Rotation and reuse detection

A refresh SHALL issue a new refresh token in the same family. Presenting a refresh token that was already spent SHALL revoke the
whole family and write `SESSION_REFRESH_REUSE_DETECTED`. Logout SHALL revoke the family.

#### Scenario: A reused refresh token revokes the family

- **WHEN** a refresh token is presented a second time after it was spent
- **THEN** the family is revoked, the new session is refused, and the audit trail contains `SESSION_REFRESH_REUSE_DETECTED`

### Requirement: Password change revokes sessions

A password change or an administrator's reset SHALL revoke every session of the user, including the session that made the change.
The user SHALL sign in again after the change. This behaviour SHALL be kept.

#### Scenario: A password change ends the sessions of the user

- **WHEN** a user with two browser sessions changes the password
- **THEN** both families are revoked, and the next refresh in either browser is refused

### Requirement: No session before login

The system SHALL NOT issue a session identifier or a session cookie before a successful login. A login SHALL create a new
family, so a session identifier that an attacker planted before the login is never honoured.

#### Scenario: An anonymous request gets no session cookie

- **WHEN** an anonymous request is made to the dashboard
- **THEN** no session or refresh cookie is set
