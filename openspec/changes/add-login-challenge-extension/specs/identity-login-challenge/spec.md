# Spec Delta

**DEFERRED.** This change is deferred until a plugin needs a login challenge. The requirements below are not scheduled.

## Purpose

The login challenge extension point: a plugin may offer a challenge to a person whose dashboard name is delayed by the
account throttle, and the rules that bound it. The wire format of the challenge is undecided (see `design.md`).

## ADDED Requirements

### Requirement: Login challenge extension point

A plugin MAY register one `LoginChallengeProvider`. When no provider is registered, no challenge SHALL ever be asked and the
behaviour is the one of the throttle alone. While the account throttle is off, no provider SHALL be asked, whether one is
registered or not, because no login is delayed.

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
