# Proposal

**Status: UNBLOCKED (decided 2026-11-18).** The wire format of the challenge is option (a): an optional field on the login
request body. The change is no longer deferred. Its code still waits for the account throttle (pull request 1 of
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
trade-offs are recorded in `design.md`. The field name and the `ChallengeRequest` and `ChallengeResponse` shapes are not
decided yet. They follow from option (a) and are settled in the spec before the code starts.

## Capabilities

### New Capabilities

- `identity-login-challenge`: the extension point, the scope of a challenge (dashboard logins for delayed names only), and
  the rule that a verified challenge lets one password check through.

## Impact

- **Type and scope.** `feat(identity)` for the code, when it is picked up.
- **Status.** The API change is the optional field of option (a). It changes the OpenAPI snapshot and the frontend login
  types. Default behaviour does not change: no provider is registered by default, and a client that sends no field is
  answered as before.
- **Dependency.** Pull request 1 of `add-login-protection` (the per-account throttle) must be merged first. The challenge
  is asked only for a name that this throttle delays.
- **Drop-in analysis.** The change adds no configuration key, environment variable, volume, image change or plugin change.
  An upstream compose file still starts, an existing data directory is adopted, and `PUID`, `PGID` and `REPOSILITE_OPTS`
  are untouched. The OpenAPI change is additive.
