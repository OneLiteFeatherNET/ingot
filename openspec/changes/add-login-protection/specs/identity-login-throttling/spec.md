# Spec Delta

## Purpose

Limits on guessing credentials on every path that accepts a password or a token: the dashboard login, Basic and Bearer
authentication, LDAP over Basic, the console, the second factor. The limits delay a guess and never lock an owner out.

## ADDED Requirements

### Requirement: Per-account counting across addresses

The system SHALL count failed credentials per account name, across all addresses, under the key `account|<name>` (name
lower case). A failure SHALL be counted only when the credential was verified and was wrong.

#### Scenario: Failures from different addresses add up

- **WHEN** a name receives failures from three different addresses, nine in total within the quiet period
- **THEN** the account counter for that name is nine

### Requirement: Progressive delay of a delayed account

From the failure that reaches the threshold on, each further failure within the quiet period SHALL set a delay that doubles,
starting at one second and capped at the configured maximum. A delayed request SHALL be answered with `429` and a
`Retry-After` header, and SHALL NOT be verified against the credential. The delay SHALL end by itself when its time has passed.

#### Scenario: Failures below the threshold are not delayed

- **WHEN** a name receives nine failed passwords from one address within an hour, and the threshold is ten
- **THEN** the tenth request is verified and answered `401` with the usual message, and no `429` is sent

#### Scenario: The delay doubles and is capped

- **WHEN** the threshold is ten, the cap is 300 seconds, and the name receives failures number 10, 11, 12 and 30 in turn,
  each within its delay
- **THEN** the delays set are 1, 2, 4 and 300 seconds, and each refused request carries `Retry-After` with that value

#### Scenario: The delay ends without any action

- **WHEN** a delay of 4 seconds was set and the injected clock moves 4 seconds further
- **THEN** the next request is verified against its credential

#### Scenario: A quiet period resets the count

- **WHEN** a name has failures and no failure for the length of the quiet period (60 minutes by default)
- **THEN** the next failure counts as the first one

### Requirement: Known addresses are exempt from the account delay

A request from an address that the account has signed in from within the last `knownAddressDays` (30 by default) SHALL NOT be
delayed by the `account|<name>` key. It SHALL still be subject to the per-address and per-name keys. A successful
authentication SHALL record its address as known for its principal, or for its token identifier when the name is not a
principal.

#### Scenario: The owner at a known address is not delayed by the account key

- **WHEN** an attacker has driven `account|alice` into a delay from an unknown address, and `alice` signs in from an
  address she used yesterday
- **THEN** the sign-in is verified and succeeds with the correct password

#### Scenario: A new address is delayed by the account key

- **WHEN** the account `alice` is in a delay and a request with the correct password comes from an address that never
  signed in as `alice`
- **THEN** the request is answered `429` with `Retry-After`, and the password is not verified

#### Scenario: Known addresses expire

- **WHEN** an address last signed in as `alice` 31 days before, and the default `knownAddressDays` is 30
- **THEN** a request from that address is treated as an unknown address

### Requirement: Credential paths share the limiter

Every path that verifies a password or a token SHALL check the limit before the verification, and SHALL count a failure
after it, under the same key namespace: the dashboard password login, Basic authentication, Bearer authentication, LDAP over
Basic, the console login and the setup code.

#### Scenario: A Bearer token is delayed like a Basic credential

- **WHEN** a Bearer secret for a token named `deploy` fails repeatedly past the threshold, from a new address
- **THEN** the next request with that secret is answered `429` with `Retry-After`, and the same happens for a Basic request
  with the name `deploy`

#### Scenario: The loopback mirror identity is not counted

- **WHEN** the loopback mirror credentials fail
- **THEN** no key is written for them

### Requirement: Second factor keys are separate

The second factor SHALL count only under `mfa|user|<name>|<address>`, `mfa|address|<address>` and `mfa|account|<name>`, and a
second factor failure SHALL NOT increase the password keys.

#### Scenario: Second factor failures do not delay the password

- **WHEN** a user fails the authenticator code ten times, and then signs in with the correct password from the same address
- **THEN** the password is verified and the sign-in succeeds, because the `account|` key is not increased by the code failures

### Requirement: Shared counters across replicas

The counters and the delays SHALL be stored in the `login_attempt` table, so that failures on one replica count on every
other replica. The per-address counter of Basic authentication, when `authentication.bruteForceProtection.enabled` is true,
SHALL be stored there too, under `address|<ip>`, and SHALL NOT be held in the memory of one instance. When the conditional
write of a counter fails the maximum number of times, the failure SHALL still be added with an atomic increment.

#### Scenario: A failure on one replica delays the next request on another

- **WHEN** two limiter instances share one database, and the first records the failures that reach the threshold
- **THEN** the second answers the next request for that name with `429`

#### Scenario: A failure is not lost under write contention

- **WHEN** the conditional write of a counter conflicts five times in a row
- **THEN** the failure is added with an atomic increment and the counter reflects it

### Requirement: Uniform answers and timing for unknown and existing names

A request for a name that does not exist, a wrong secret for an existing name, and a disabled or expired token SHALL receive the
same status and the same message. The verification SHALL run its hash for every request that reaches it, including a name
that does not exist or has no secret, so that the time of the answer does not depend on existence. The lazily created dummy
hash SHALL be created before the first request.

#### Scenario: An unknown dashboard user takes as long as a wrong password

- **WHEN** the local provider verifies a password for a user name that does not exist
- **THEN** one password hash is computed, and the answer is `Invalid username or password`

#### Scenario: A delayed name that does not exist gets the same delay

- **WHEN** a name that does not exist receives failures past the threshold
- **THEN** its requests are answered `429` with `Retry-After`, as for an existing name

### Requirement: Retry-After and client behaviour

Every `429` of this capability SHALL carry `Retry-After` in whole seconds, rounded up, and at least one. The response body
SHALL use the problem format of the endpoint family and SHALL NOT contain the name, the existence of the name or the
remaining count.

#### Scenario: A remaining delay of 0.4 seconds is answered as one second

- **WHEN** the remaining delay is 0.4 seconds
- **THEN** the response carries `Retry-After: 1`

### Requirement: Audit of throttled requests

A request answered with a delay SHALL be audited as `LOGIN_THROTTLED` with the attempted name and the address, and SHALL NOT be
audited as `LOGIN_FAILED`. The secret and the password SHALL NOT appear in the audit event.

#### Scenario: A throttled request is audited once

- **WHEN** a request is answered `429` by the account delay
- **THEN** exactly one `LOGIN_THROTTLED` event is written, and no `LOGIN_FAILED` event for that request

### Requirement: Default values and the existing switches

The account throttle SHALL be enabled by default with a threshold of 10 failures, a quiet period of 60 minutes, a maximum delay
of 300 seconds and a known-address window of 30 days. The existing dashboard limits `login.maxFailedAttempts` (5) and
`login.lockoutMinutes` (15) SHALL keep their names and defaults. The per-address switch `authentication.bruteForceProtection`
SHALL keep its default of disabled.

#### Scenario: An existing installation starts with the same defaults

- **WHEN** an instance starts with a configuration file written before this change
- **THEN** the settings read back are the old keys with their old values, and the new keys have the defaults above
