# Spec Delta

## Purpose

What an operator and a plugin can observe about sign-in attempts: the audit action of a throttled request, the counters,
the plugin events for throttling and for a sign-in from a new address. The login challenge extension point is deferred to
`add-login-challenge-extension` and is not specified here.

## ADDED Requirements

### Requirement: Audit action for throttled requests

While the account throttle is on, the audit trail SHALL record a request answered with a delay as `LOGIN_THROTTLED`, with the attempted name, the address and
the path that answered it (`dashboard`, `basic`, `bearer`, `console`, `mfa`). The action SHALL be added to `AuditAction`
without changing any existing value. The secret, the password and the second-factor code SHALL NOT be recorded.

#### Scenario: The new action is written with the path

- **WHEN** a Basic request for `deploy` is answered with a delay
- **THEN** the audit event has the action `LOGIN_THROTTLED`, the name `deploy`, the address, and the path `basic`

#### Scenario: Existing actions keep their values

- **WHEN** the audit enum is compared with its state before this change
- **THEN** every earlier constant has the same name and the same serialised value

### Requirement: Counters per path and outcome

While the account throttle is on, the status of an instance SHALL expose counters of sign-in attempts per path (`dashboard`,
`basic`, `bearer`, `console`, `mfa`) and outcome (`succeeded`, `failed`, `throttled`). The counters SHALL be kept in memory of
the instance and SHALL reset when the instance restarts. The counters SHALL NOT carry names or addresses. While the switch is
off, no counter SHALL be kept and the status response SHALL be unchanged.

#### Scenario: A throttled request increments only its counter

- **WHEN** a dashboard request is answered with a delay
- **THEN** the counter `dashboard/throttled` increases by one, and `dashboard/failed` does not change

#### Scenario: The status response is unchanged while the switch is off

- **WHEN** the account throttle is off and a user fails a sign-in
- **THEN** the status response has no sign-in counter field

### Requirement: Plugin event for a throttled request

While the account throttle is on, a `LoginThrottledEvent` SHALL be published once for each request answered with a delay. It
SHALL carry the name, the address, the path and the remaining delay in seconds. It SHALL NOT carry the secret. Its publication SHALL happen after the state of the delay
is stored, and a listener that fails SHALL NOT change the answer to the request.

#### Scenario: A failing listener does not change the answer

- **WHEN** a registered listener throws while handling `LoginThrottledEvent`
- **THEN** the request is still answered `429` with `Retry-After`, and the failure is logged

### Requirement: Plugin event for a sign-in from a new address

While the account throttle is on, a `NewSignInEvent` SHALL be published when a successful login or token authentication comes
from an address outside the known-address list. It SHALL carry the principal or token identifier, the address, the path and
the hash of the user agent, never the raw user agent. While the switch is off, no `NewSignInEvent` SHALL be published.

#### Scenario: The first sign-in is announced as a new address

- **WHEN** the account throttle is on and a user signs in for the first time
- **THEN** a `NewSignInEvent` is published with the address of that sign-in

#### Scenario: A known address publishes nothing

- **WHEN** the account throttle is on and a user signs in from an address seen within the known-address window
- **THEN** no `NewSignInEvent` is published

#### Scenario: The switch off publishes no sign-in event

- **WHEN** the account throttle is off and a user signs in from any address
- **THEN** no `NewSignInEvent` and no `LoginThrottledEvent` is published
