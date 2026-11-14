# Spec Delta

## Purpose

What an operator and a plugin can observe about sign-in attempts: the audit action of a throttled request, the counters,
the plugin events for throttling and for a sign-in from a new address, and the extension point that offers a challenge to a
person whose name is delayed.

## ADDED Requirements

### Requirement: Audit action for throttled requests

The audit trail SHALL record a request answered with a delay as `LOGIN_THROTTLED`, with the attempted name, the address and
the path that answered it (`dashboard`, `basic`, `bearer`, `console`, `mfa`). The action SHALL be added to `AuditAction`
without changing any existing value. The secret, the password and the second-factor code SHALL NOT be recorded.

#### Scenario: The new action is written with the path

- **WHEN** a Basic request for `deploy` is answered with a delay
- **THEN** the audit event has the action `LOGIN_THROTTLED`, the name `deploy`, the address, and the path `basic`

#### Scenario: Existing actions keep their values

- **WHEN** the audit enum is compared with its state before this change
- **THEN** every earlier constant has the same name and the same serialised value

### Requirement: Counters per path and outcome

The status of an instance SHALL expose counters of sign-in attempts per path (`dashboard`, `basic`, `bearer`, `console`, `mfa`)
and outcome (`succeeded`, `failed`, `throttled`). The counters SHALL be kept in memory of the instance and SHALL reset when the
instance restarts. The counters SHALL NOT carry names or addresses.

#### Scenario: A throttled request increments only its counter

- **WHEN** a dashboard request is answered with a delay
- **THEN** the counter `dashboard/throttled` increases by one, and `dashboard/failed` does not change

### Requirement: Plugin event for a throttled request

A `LoginThrottledEvent` SHALL be published once for each request answered with a delay. It SHALL carry the name, the address, the
path and the remaining delay in seconds. It SHALL NOT carry the secret. Its publication SHALL happen after the state of the delay
is stored, and a listener that fails SHALL NOT change the answer to the request.

#### Scenario: A failing listener does not change the answer

- **WHEN** a registered listener throws while handling `LoginThrottledEvent`
- **THEN** the request is still answered `429` with `Retry-After`, and the failure is logged

### Requirement: Plugin event for a sign-in from a new address

A `NewSignInEvent` SHALL be published when a successful login or token authentication comes from an address that is not in the
known-address list of the principal or token. It SHALL carry the principal or token identifier, the address, the path, and the
hash of the user agent. The raw user agent SHALL NOT be carried. The first sign-in of a principal SHALL publish the event as well.

#### Scenario: The first sign-in is announced as a new address

- **WHEN** a user signs in for the first time
- **THEN** a `NewSignInEvent` is published with the address of that sign-in

#### Scenario: A known address publishes nothing

- **WHEN** a user signs in from an address seen within the known-address window
- **THEN** no `NewSignInEvent` is published

### Requirement: Login challenge extension point

A plugin MAY register one `LoginChallengeProvider`. When no provider is registered, no challenge SHALL ever be asked and the
behaviour is the one of the throttle alone.

#### Scenario: No provider means no challenge

- **WHEN** no plugin registers a provider and a dashboard name is delayed
- **THEN** the answer is `429` with `Retry-After`, with no challenge in the body

### Requirement: Challenge only for delayed dashboard logins

When a provider is registered, it SHALL be asked only for a dashboard login whose name is delayed by the account key, and never
for a Basic, Bearer or console client. A verified challenge SHALL let the password be verified once; it SHALL NOT clear the delay.

#### Scenario: A provider is asked only for a delayed dashboard login

- **WHEN** a provider is registered and a Basic request for a delayed name arrives
- **THEN** the provider is not asked, and the answer is `429` with `Retry-After`

#### Scenario: A verified challenge lets one password check through

- **WHEN** a provider verifies the challenge of a delayed dashboard login and the password is wrong
- **THEN** the password is verified once, the failure counts, and the delay continues

### Requirement: Additive plugin interface and no default provider

The interface SHALL be additive: its methods that a plugin does not implement SHALL have default implementations, so that plugins
compiled against the previous version continue to load. Ingot SHALL NOT ship a provider, and SHALL NOT call a third-party service
to produce or verify a challenge by default.

#### Scenario: Existing plugins still load

- **WHEN** a plugin compiled against the previous plugin API is loaded
- **THEN** it loads and its behaviour is unchanged
