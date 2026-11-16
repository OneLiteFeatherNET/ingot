# Proposal

**Status: DEFERRED.** This change is deferred until a plugin needs a login challenge. No code is planned before that
point. The wire format of the challenge is an open decision, and the choice is the maintainer's.

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

## Open decision

The wire format of the challenge on `/api/auth/login` is not decided. Three options are open, and none is chosen here:

- **(a)** An optional field on the login request body. This changes the OpenAPI snapshot and the frontend types.
- **(b)** A separate challenge endpoint, called before the login.
- **(c)** Keep the challenge out of core, and let the plugin serve its own page. The login route stays unchanged.

The `ChallengeRequest` and `ChallengeResponse` shapes are not decided. They follow from the option chosen. The choice is
the maintainer's, and the trade-offs of each option are in `design.md`.

## Capabilities

### New Capabilities

- `identity-login-challenge`: the extension point, the scope of a challenge (dashboard logins for delayed names only), and
  the rule that a verified challenge lets one password check through.

## Impact

- **Type and scope.** `feat(identity)` for the code, when it is picked up.
- **Status.** No code, configuration, schema or API change is proposed by this change until the decision is taken and a
  plugin needs the challenge. Nothing ships before then.
- **Dependency.** Pull request 1 of `add-login-protection` (the per-account throttle) must be merged first. The challenge
  is asked only for a name that this throttle delays.
- **Drop-in analysis.** Not applicable while the change is deferred. When it is picked up, the three checks of
  `add-login-protection` apply again.
