# Spec Delta

## Purpose

Second factors for the browser sign-in of local and LDAP accounts: authenticator codes, passkeys and recovery codes, the
login challenge, step-up before sensitive actions, an optional policy with a grace period, the rule for access tokens,
and an administrative reset. Every requirement below is off unless a user enrols a factor or an operator sets the policy.

## ADDED Requirements

### Requirement: Factor kinds and enrolment

A signed-in user SHALL be able to enrol any number of authenticator codes (TOTP) and passkeys. Authenticator codes SHALL
follow RFC 6238 with six digits, a thirty second step and one step of tolerance either side. Recovery codes SHALL be issued
in sets of ten, shown once, stored only as hashes, and each one SHALL be usable once. An authenticator code SHALL NOT be
enrolled when no settings encryption key is configured, and the answer SHALL carry the reason.

#### Scenario: A TOTP factor is enrolled after a confirming code

- **WHEN** a signed-in user starts TOTP enrolment and confirms it with the current code
- **THEN** the factor is stored with its seed encrypted, ten recovery codes are returned once, and an audit event
  `MFA_FACTOR_ENROLLED` is written with the user name and the address

#### Scenario: TOTP enrolment without a settings key is refused

- **WHEN** a user starts TOTP enrolment and neither `INGOT_SETTINGS_KEY` nor `INGOT_SETTINGS_KEY_FILE` is set
- **THEN** the request is answered with `409` and a message that names the missing key, and nothing is stored

#### Scenario: A recovery code is spent once

- **WHEN** a recovery code is presented twice, from one replica or from two replicas at the same time
- **THEN** exactly one presentation succeeds and the other is refused

### Requirement: Login challenge

A password or LDAP login that succeeds for a user with an enrolled factor, or for a user a policy covers, SHALL NOT start a
session. It SHALL start a single-use challenge that expires after five minutes, stored in the database so that every
replica accepts it, and SHALL answer with `mfaRequired` set. A session SHALL start only after a verified second factor.
A user without a factor and without a covering policy SHALL log in as before.

#### Scenario: A user with a TOTP factor is challenged after the password

- **WHEN** a user with an enrolled authenticator code signs in with the correct password
- **THEN** the answer sets `mfaRequired` to true, lists the methods, sets the challenge cookie, and no session cookie is set

#### Scenario: The challenge is refused after its expiry

- **WHEN** a valid code is presented more than five minutes after the password was accepted
- **THEN** the request is refused, and the user must sign in again

#### Scenario: A user without factors signs in as before

- **WHEN** a user without a factor and without a covering policy signs in with the correct password
- **THEN** the session cookies are set in the same way as before this change and `mfaRequired` is absent or false

### Requirement: Rate limit of second factor verification

Failed verifications of authenticator codes and recovery codes SHALL count through the failure limit of password logins,
per user name with address and per address. A blocked verification SHALL answer `429` with `Retry-After` and SHALL be
audited as `MFA_CHALLENGE_FAILED`. A correct code SHALL NOT reset the counter of the address.

#### Scenario: Wrong codes lock the attempt

- **WHEN** the same address presents wrong authenticator codes up to the configured maximum
- **THEN** the next attempt is refused with `429` and `Retry-After` until the lock ends

### Requirement: Replay protection of authenticator codes

An authenticator code SHALL be accepted only for a time step later than the last step accepted for that factor. The
acceptance SHALL be a conditional database update, so that two replicas receiving the same code accept it once.

#### Scenario: A code cannot be used twice

- **WHEN** a valid code is used to sign in and then the same code is presented again within its window
- **THEN** the second presentation is refused

### Requirement: Passkey registration

A user SHALL be able to register passkeys with the WebAuthn ceremonies. Each registration SHALL be bound to the relying
party identifier and origin derived from the public address.

#### Scenario: A passkey is registered for the own account

- **WHEN** a signed-in user completes a registration ceremony for their own account
- **THEN** the passkey is stored with its public key and its signature counter

### Requirement: Passkey as a second factor

A passkey SHALL be usable as a second factor. Challenges SHALL be single use and expire after two minutes.

#### Scenario: A passkey is a second factor

- **WHEN** a user with a registered passkey signs in with the correct password and completes an assertion
- **THEN** the session starts with the second factor recorded

### Requirement: Passkey as a first factor is opt-in

A passkey SHALL be usable as a first factor only when `login.mfa.passkeyFirstFactor` is true.

#### Scenario: A passkey cannot be a first factor while the switch is off

- **WHEN** `login.mfa.passkeyFirstFactor` is false and a passkey login is started
- **THEN** the request is refused and no challenge is stored

### Requirement: Signature counter

An assertion whose signature counter does not exceed the stored counter SHALL be refused, and the stored counter SHALL
then stay unchanged.

#### Scenario: A lower signature counter is refused

- **WHEN** an assertion arrives with a counter that is not greater than the stored counter
- **THEN** the assertion is refused and the stored counter is unchanged

#### Scenario: A passkey is a second factor

- **WHEN** a user with a registered passkey signs in with the correct password and completes an assertion
- **THEN** the session starts with the second factor recorded

#### Scenario: A passkey cannot be a first factor while the switch is off

- **WHEN** `login.mfa.passkeyFirstFactor` is false and a passkey login is started
- **THEN** the request is refused and no challenge is stored

#### Scenario: A lower signature counter is refused

- **WHEN** an assertion arrives with a counter that is not greater than the stored counter
- **THEN** the assertion is refused and the stored counter is unchanged

### Requirement: Step-up before sensitive actions

For a user with at least one factor, the sensitive actions SHALL require a second factor completed within
`login.mfa.stepUpMinutes`. The sensitive actions are enrolling or removing a factor, regenerating recovery codes, creating,
rotating or deleting the user's own access tokens, changing the password, and instance admin operations.

#### Scenario: A sensitive action without a recent second factor is refused

- **WHEN** a user with a factor requests a sensitive action outside the step-up window
- **THEN** the request is refused with `403` and the problem `step_up_required`

### Requirement: Step-up for a user without factors

A user without a factor SHALL need a session only for sensitive actions. Enrolling a first factor SHALL additionally
require a recent password entry within the same window.

#### Scenario: A first factor needs a recent password entry

- **WHEN** a user without a factor starts enrolment more than the step-up window after the password entry
- **THEN** the request is refused with `step_up_required`

#### Scenario: Token creation with an old second factor is refused

- **WHEN** a user with a factor creates an access token more than the step-up window after the last second factor
- **THEN** the request is refused with `step_up_required` and no token is created

#### Scenario: Step-up renews the window without a new login

- **WHEN** a signed-in user completes a second factor through the step-up route
- **THEN** the session of the same family is reissued with a new authentication time and the sensitive action succeeds

### Requirement: Access tokens stay single-factor

Basic and Bearer requests that use an existing access token SHALL be authenticated as before, with no second factor,
except for the token creation and rotation rule in the step-up requirement. Access tokens of administrators SHALL NOT be
covered by the administrator policy.

#### Scenario: A CI token keeps working

- **WHEN** a pipeline deploys with an access token whose owner has an enrolled factor
- **THEN** the deployment succeeds without any second factor

#### Scenario: A stolen token cannot create another token

- **WHEN** a Basic request with an access token of a user with a factor tries to create a token
- **THEN** the request is refused with `step_up_required`

### Requirement: Policy for administrators and named teams

The settings `login.mfa.requiredForAdmins` (default false) and `login.mfa.requiredForTeams` (default empty) SHALL cover
browser sessions of instance administrators and of members of the named teams. The server SHALL record `requiredSince`
for a user when the policy first covers that user, and the grace period `login.mfa.graceDays` (default 14) SHALL count
from that instant.

#### Scenario: Turning the policy on changes nothing for an existing installation

- **WHEN** an installation without a policy upgrades to this version
- **THEN** every user keeps their session behaviour, and no `requiredSince` is recorded

#### Scenario: A covered user is shown the grace period

- **WHEN** a covered user without a factor signs in during the grace period
- **THEN** the session is complete and the security status reports the end of the grace period

### Requirement: Restricted session after the grace period

After the grace period, a covered user without a factor SHALL receive a session with scope `enrol` at each refresh. Such a
session SHALL reach only the factor and security routes of `/api/identity/me/` and the logout route. Every other route
SHALL answer `403` with `mfa_enrolment_required`.

#### Scenario: A restricted session can enrol a factor

- **WHEN** a restricted session lists its factors and enrols one
- **THEN** the enrolment succeeds, and the next refresh issues a full session

#### Scenario: A restricted session cannot read the repositories through the dashboard API

- **WHEN** a restricted session calls a route outside the allowed set
- **THEN** the request is refused with `mfa_enrolment_required`

### Requirement: External logins

LDAP users SHALL be able to enrol Ingot factors and SHALL pass the same login challenge as local users. OpenID Connect
and GitHub users SHALL NOT be challenged for an Ingot factor unless a policy covers them. A covered OpenID Connect user
SHALL pass without a challenge only when the ID token carries an `acr` value in the provider's `mfaAcrValues` or an `amr`
value in its `mfaAmrValues`. A GitHub user under a policy SHALL always be challenged for an Ingot factor.

#### Scenario: An OpenID Connect user with a trusted acr is not challenged

- **WHEN** a covered user signs in through a provider whose `mfaAcrValues` contains the `acr` of the ID token
- **THEN** the session starts without an Ingot challenge

#### Scenario: A GitHub user under the policy is challenged

- **WHEN** a covered GitHub user signs in
- **THEN** the login challenge of an Ingot factor is started

### Requirement: Administrative reset

An instance administrator SHALL be able to remove one or all factors and the recovery codes of a user. The reset SHALL end
the sessions of that user, SHALL be audited as `MFA_FACTORS_RESET` with the administrator and the target name, and SHALL
NOT return or log any seed, code, key or session identifier. The administrator's list of factors SHALL show names, kinds
and times only.

#### Scenario: An administrator resets a locked-out user

- **WHEN** an instance administrator removes all factors of a user
- **THEN** the user's sessions end, the audit event is written, and the answer contains only factor ids and kinds

### Requirement: Break-glass access is unchanged

The `--token` startup credential SHALL keep working without any second factor and SHALL NOT be covered by the policy, as
the recovery guide describes.

#### Scenario: The recovery token still resets a factor

- **WHEN** an operator starts the instance with `--token` and removes the factors of a locked-out administrator
- **THEN** the removal succeeds without a second factor

### Requirement: Storage and high availability

The factors, recovery codes and challenges SHALL be stored in the database in the tables `mfa_factor`, `mfa_recovery_code`
and `auth_challenge`, created on start with the identity tables on SQLite, H2, MariaDB, MySQL and PostgreSQL. No state SHALL
be held only in one replica's memory.

#### Scenario: A challenge started on one replica is verified on another

- **WHEN** a login challenge is started through one replica and verified through another sharing the database
- **THEN** the verification succeeds

### Requirement: Relying party and origin

The relying party identifier SHALL be the host of `login.publicUrl`, or the request host when that setting is blank. A
passkey ceremony whose origin does not match SHALL be refused.

#### Scenario: A ceremony from another origin is refused

- **WHEN** a passkey registration arrives with an origin that is not the configured origin
- **THEN** the request is refused and no passkey is stored

### Requirement: Secrets and logs

Authenticator seeds SHALL be stored encrypted with the settings encryption, bound to the row. Recovery codes and challenge
identifiers SHALL be stored as SHA-256 hashes. No code, seed, recovery code, key or session identifier SHALL appear in an
audit event or a log line.

#### Scenario: A seed cannot be read back

- **WHEN** an administrator lists the factors of a user
- **THEN** no seed, code or key is part of the answer
