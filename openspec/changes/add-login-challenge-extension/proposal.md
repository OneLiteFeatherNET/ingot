# Proposal

**Status: UNBLOCKED (decided 2026-11-18).** The wire format of the challenge is option (a): an optional field on the login
request body, named `challenge`, with the shapes settled below. The change is no longer deferred. Its code still waits for the account throttle (pull request 1 of
`add-login-protection`).

## Why

`add-login-protection` delays sign-in attempts per account. A plugin author may want a person whose name is delayed to
answer a challenge first, for example a CAPTCHA, a proof of work, or a verification by another service. That is the only
case this extension point covers. No plugin needs it yet, so the extension point waits.

Ingot ships no challenge provider and calls no third-party service by default. A challenge is never part of the default
behaviour.

## What Changes

When this change is picked up, it adds a login challenge extension point with these properties:

- A plugin MAY register one `LoginChallengeProvider`. When several are registered, the first by plugin load order is used
  and a warning is logged.
- The challenge is asked only for a dashboard login whose name is delayed by the account throttle. It is never asked for a
  Basic, Bearer or console client, because those clients cannot answer one.
- A verified challenge lets one password check through. It does not clear the delay.
- Maven and Gradle keep the `429` with `Retry-After`, as they do without a provider.
- The interface is additive: its methods have default implementations, so plugins compiled against the previous plugin API
  still load.

The commit type and scope for the code is `feat(identity)`. It ships under that type when the change is picked up.

## Decision: wire format (2026-11-18)

The maintainer chose option (a): an optional field on the login request body carries the challenge response on
`/api/auth/login`. Options (b), a separate challenge endpoint, and (c), a page served by the plugin, are not taken. Their
trade-offs are recorded in `design.md`.

## Decision: field name and shapes (2026-11-18)

The field is named `challenge`. The shapes are settled in `design.md` and the spec delta:

- `ChallengeRequest`: the `429` answer, with `Retry-After`, for a delayed dashboard login when a provider demands a
  challenge. It is an `ErrorResponse` whose `message` is the code `challenge_required`, plus `provider` (the challenge
  provider id) and `parameters` (a JSON object of public, provider specific values).
- `ChallengeResponse`: the optional `challenge` member of the login request, with `provider` (the challenge provider id)
  and `answer` (a JSON object, provider specific).
- A login without `challenge` is answered exactly as today. `challenge` is ignored unless a provider is registered and the
  name is delayed by the account throttle. A missing, malformed or foreign challenge gets a fresh `ChallengeRequest`.
- A `ChallengeRequest` is audited as `LOGIN_THROTTLED` like any delayed login. A verified challenge does not replace the
  second factor, and step-up is not affected.

## Capabilities

### New Capabilities

- `identity-login-challenge`: the extension point, the scope of a challenge (dashboard logins for delayed names only), and
  the rule that a verified challenge lets one password check through.

## Impact

- **Type and scope.** `feat(identity)` for the code, when it is picked up.
- **Status.** The API change is the optional `challenge` field of option (a) and the `ChallengeRequest` alternative of the
  login `429`. It changes the OpenAPI snapshot and the frontend login types. Default behaviour does not change: no provider
  is registered by default, and a client that sends no field is answered as before.
- **Dependency.** Pull request 1 of `add-login-protection` (the per-account throttle) must be merged first. The challenge
  is asked only for a name that this throttle delays.
- **Drop-in analysis.** The change adds no configuration key, environment variable, volume, image change or plugin change.
  An upstream compose file still starts, an existing data directory is adopted, and `PUID`, `PGID` and `REPOSILITE_OPTS`
  are untouched. The OpenAPI change is additive.
