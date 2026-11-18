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
- **THEN** the password is verified once, the answer is `401` as for any wrong password, the failure counts, and the delay
  continues

### Requirement: Additive plugin interface and no default provider

The interface SHALL be additive: its methods that a plugin does not implement SHALL have default implementations, so that plugins
compiled against the previous version continue to load. Ingot SHALL NOT ship a provider, and SHALL NOT call a third-party service
to produce or verify a challenge by default.

#### Scenario: Existing plugins still load

- **WHEN** a plugin compiled against the previous plugin API is loaded
- **THEN** it loads and its behaviour is unchanged

### Requirement: Challenge response travels on the login request

The challenge response SHALL travel as the optional, nullable member `challenge` of the login request body on
`POST /api/auth/login`, as a `ChallengeResponse` object with the members `provider` (the challenge provider id, a string) and
`answer` (a provider specific JSON object that Ingot hands to the provider unchanged). No other member of the login request
SHALL change. A login request that carries no `challenge` SHALL be answered exactly as before this change.

#### Scenario: A request without the field

- **WHEN** a client sends a login request without `challenge`
- **THEN** the answer is the same as before this change, and the OpenAPI document differs only by the optional `challenge`
  member and the `ChallengeRequest` alternative of the `429`

#### Scenario: A delayed dashboard login with a verified response

- **WHEN** a delayed dashboard login carries `challenge` with the id of the registered provider and an `answer` the provider
  verifies
- **THEN** the password is verified once, as the requirement on verified challenges states

### Requirement: Challenge request answer

When a registered provider demands a challenge for a delayed dashboard login, the answer SHALL be status `429` with the
`Retry-After` header of the throttle and a `ChallengeRequest` body. A `ChallengeRequest` SHALL carry every `ErrorResponse`
member, with `message` and `detail` set to the code `challenge_required`, plus `provider` (the id of the registered provider)
and `parameters` (a JSON object of public, provider specific values). `parameters` SHALL NOT carry a secret.

#### Scenario: A delayed dashboard login without a response

- **WHEN** a provider is registered and a dashboard login without `challenge` arrives for a name the account throttle delays
- **THEN** the answer is `429` with `Retry-After`, `message` `challenge_required`, the provider id in `provider` and the
  provider's values in `parameters`, and the password is not checked

#### Scenario: A client that does not know the challenge waits it out

- **WHEN** a client ignores a `ChallengeRequest` and repeats the login without `challenge` after `Retry-After` has passed and
  the name is no longer delayed
- **THEN** the password is checked as before this change

### Requirement: Challenge read only for delayed names

The `challenge` member SHALL be read only when a provider is registered, the account throttle is on, and the name of the
dashboard login is delayed by the account throttle. Otherwise it SHALL be ignored, whatever it contains.

#### Scenario: A challenge for a name that is not delayed is ignored

- **WHEN** a dashboard login for a name that is not delayed carries a `challenge`
- **THEN** the `challenge` is not read and the answer is the one of the same request without it

#### Scenario: A challenge without a registered provider is ignored

- **WHEN** no provider is registered and a dashboard login for a delayed name carries a `challenge`
- **THEN** the answer is `429` with `Retry-After` and no challenge in the body, as without the field

### Requirement: Unknown, malformed and rejected challenges

When `challenge` is read and it is malformed, names another provider id than the registered one, or carries an `answer` the
provider does not verify, the answer SHALL be a fresh `ChallengeRequest`, the password SHALL NOT be checked, and nothing SHALL
count as a failure. When the provider fails, the answer SHALL be the plain `429` with `Retry-After` of the throttle alone.

#### Scenario: A challenge for another provider

- **WHEN** a delayed dashboard login carries a `challenge` whose `provider` is not the id of the registered provider
- **THEN** the answer is a fresh `ChallengeRequest` and the password is not checked

#### Scenario: A malformed challenge

- **WHEN** a delayed dashboard login carries a `challenge` that is not an object, lacks a string `provider`, or lacks an
  object `answer`
- **THEN** the answer is a fresh `ChallengeRequest`, not `400`, and the password is not checked

#### Scenario: A rejected answer

- **WHEN** the registered provider does not verify the `answer` of a delayed dashboard login
- **THEN** the answer is a fresh `ChallengeRequest`, the password is not checked, and no failure is counted

#### Scenario: A failing provider

- **WHEN** the registered provider throws or does not answer while a delayed dashboard login is handled
- **THEN** the failure is logged and the answer is `429` with `Retry-After` and no challenge in the body

### Requirement: Challenge and the throttle

Only a delay of the account throttle SHALL be answered with a `ChallengeRequest`. A delay of the attempt limiter (per address,
or per name and address) SHALL stay a plain `429` with `Retry-After`. Every login answered with a `ChallengeRequest` SHALL be
audited once as `LOGIN_THROTTLED` and counted as throttled, as a delayed login is today, and neither `parameters` nor `answer`
SHALL be written to the audit trail or the log. A verified challenge SHALL NOT reset the failure count.

#### Scenario: The attempt limiter is not bypassed

- **WHEN** the attempt limiter delays a dashboard login that carries a `challenge` the provider would verify
- **THEN** the answer is `429` with `Retry-After` and no challenge in the body, and the provider is not asked

#### Scenario: A challenge request is audited as a delayed login

- **WHEN** a delayed dashboard login is answered with a `ChallengeRequest`
- **THEN** one `LOGIN_THROTTLED` event is audited with the path and no secret, and neither `parameters` nor `answer` appears
  in it

### Requirement: Challenge and the second factor

A verified challenge SHALL NOT replace the second factor. After a verified challenge and the right password, a user with a
second factor SHALL be answered `200` with `SecondFactorRequiredResponse`, as without a throttle. The routes
`/api/auth/mfa/verify`, the passkey sign-in routes, `/api/auth/setup` and `/api/identity/me/step-up` SHALL NOT ask for or
read a challenge, and the `403` `step_up_required` answer SHALL NOT change.

#### Scenario: A user with a second factor after a verified challenge

- **WHEN** a delayed dashboard login of a user with an authenticator carries a verified challenge and the right password
- **THEN** the answer is `200` with `mfaRequired` and the second factor challenge cookie, and no session is started

#### Scenario: The second factor verification is never challenged

- **WHEN** `/api/auth/mfa/verify` answers `429` because of too many failed codes
- **THEN** the body carries no `challenge_required` code and no provider is asked
