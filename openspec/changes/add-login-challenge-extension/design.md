# Design

**Status: DECIDED (2026-11-18).** The wire format is option (a), the field is named `challenge`, and the `ChallengeRequest`
and `ChallengeResponse` shapes are settled below. The code waits only for the account throttle, see `tasks.md`.

## Defaults that stand

The defaults of `add-login-protection` design D6 stand and are not decided again here. No provider is registered by
default. At most one provider is used. When several are registered, the first by plugin load order is used and a warning
is logged.

## Decision: the wire format of the challenge (2026-11-18)

Option (a) is chosen: an optional field on the login request body carries the challenge response.

- One route and one round trip. Existing clients send no field and keep working.
- Cost: the OpenAPI snapshot and the frontend types of the login request change.
- Not taken: option (b), a separate challenge endpoint (two round trips for a delayed login, a new endpoint, and state held
  between the two calls), and option (c), a page served by the plugin (core would still need a signal interface for a
  delayed login).

## Decision: the field is named `challenge` (2026-11-18)

The optional member of `LoginRequest` is named `challenge`. `LoginRequest` already has a member `provider`, which names the
login provider (for example the local password store or a directory). The challenge provider therefore never appears as a
top level member of the request: it travels inside `challenge`, so the two provider ids cannot be confused.

## Decision: `ChallengeRequest`, what the server asks for (2026-11-18)

A login that a provider wants answered with a challenge gets status `429`, the `Retry-After` header, and a body that is an
`ErrorResponse` with two more members. Every member that clients read today keeps its meaning, so a client that does not
know the challenge still sees a `429` it can wait out.

```json
{
  "status": 429,
  "message": "challenge_required",
  "detail": "challenge_required",
  "type": "about:blank",
  "title": "Too Many Requests",
  "instance": "/api/auth/login",
  "provider": "example-pow",
  "parameters": { "difficulty": 20, "seed": "..." }
}
```

- `message`, and `detail` which mirrors it, carry the error code `challenge_required`. It follows the lower case code style
  of `step_up_required`, the existing code that tells a client to complete another interaction before it retries.
- `provider` is the id of the registered `LoginChallengeProvider`: a non-empty string chosen by the plugin and stable across
  restarts. Ingot does not interpret it.
- `parameters` is a JSON object produced by the provider. It holds only public values the client needs to present or solve
  the challenge (a site key, a seed, a difficulty). It never holds a secret, and Ingot passes it through unchanged. An empty
  object is valid.
- The `Retry-After` header is set exactly as on the plain throttle answer. Waiting it out without answering stays a valid
  way to get the next password check.
- OpenAPI: the `429` of `POST /api/auth/login` becomes `oneOf` `ErrorResponse` and the new schema `ChallengeRequest`, which
  carries the `ErrorResponse` members plus the required members `provider` and `parameters`.

## Decision: `ChallengeResponse`, what the client sends back (2026-11-18)

The client repeats the login request with the same `provider`, `username` and `password`, and adds `challenge`:

```json
{
  "provider": "local",
  "username": "alice",
  "password": "...",
  "challenge": {
    "provider": "example-pow",
    "answer": { "nonce": "..." }
  }
}
```

- `challenge.provider` is the challenge provider id that the `ChallengeRequest` named.
- `challenge.answer` is a JSON object, provider specific, handed to the provider unchanged. Ingot does not interpret it.
- The whole request stays within the body limit the login route has today (64 KiB). The limit is not raised for a provider.
- OpenAPI: `LoginRequest` gains the optional, nullable member `challenge` of the new schema `ChallengeResponse`. No other
  member of `LoginRequest` changes.

## Decision: when `challenge` is read, and absent or unknown providers (2026-11-18)

The field is read only when all three hold: a provider is registered, the account throttle is on, and the name of this
dashboard login is delayed by the account throttle at this moment. In every other case `challenge` is ignored, whatever it
contains, and the answer is the one a request without the field gets. This keeps the drop-in promise and does not tell a
caller whether a provider exists.

When the field is read:

| Request | Answer | Password checked | Counts as a failure |
| --- | --- | --- | --- |
| No `challenge` | `429` `ChallengeRequest` | no | no |
| `challenge` malformed (not an object, `provider` missing or not a string, `answer` missing or not an object) | `429` `ChallengeRequest` | no | no |
| `challenge.provider` is not the id of the registered provider | `429` `ChallengeRequest` | no | no |
| The provider does not verify `answer` | `429` `ChallengeRequest` | no | no |
| The provider verifies `answer` | the answer of one ordinary password check, see below | once | as today |

A malformed or foreign `challenge` is answered with a fresh `ChallengeRequest` rather than a `400`: the client recovers by
answering the new challenge, and a `400` would be a second error path for the same situation. A login request that misses
`provider`, `username` or `password` is still answered `400` before any of this, as today.

When the provider fails (it throws or does not answer), the failure is logged and the answer is the plain `429` with
`Retry-After` of the throttle alone, with no challenge. A failing plugin never lets a password check through and never
closes the wait-it-out path.

## Decision: interaction with the throttle and `LOGIN_THROTTLED` (2026-11-18)

- **Which delays.** Only a delay of the account throttle of `add-login-protection` (the per name key) can be answered with a
  challenge. The delay of the attempt limiter (per address, and per name and address), which `LoginService.login` checks
  first, stays a plain `429` with `Retry-After`. A challenge never lets a request past that limiter.
- **Audit and events.** Every request answered with a `ChallengeRequest` is a delayed login and is recorded exactly as one is
  today: audited once as `LOGIN_THROTTLED`, counted as throttled and announced, with the path and no secret. Neither
  `parameters` nor `answer` is written to the audit trail or the log.
- **A verified challenge.** It lets exactly one password check through. It does not clear the delay and does not reset the
  failure count. A wrong password counts as a failure for the account throttle and the limiter as today, and the next
  request for the name is delayed again.
- **Answers after a verified challenge.** They are the answers of an ordinary login today: `200` `LoginResponse` with a
  session, `200` `SecondFactorRequiredResponse` (see below), `401` for a wrong password or an unknown name (same text for
  both), `403` for a disabled user or a refused password, `503` when the login provider cannot be reached.

## Decision: interaction with the second factor and step-up (2026-11-18)

- A challenge replaces nothing of the second factor. A user with a factor who passes a verified challenge and the right
  password gets `200` `SecondFactorRequiredResponse` with `mfaRequired` and the challenge cookie, exactly as without a
  throttle, and completes the sign-in at `/api/auth/mfa/verify`.
- `/api/auth/mfa/verify`, the passkey routes, `/api/auth/setup` and `/api/identity/me/step-up` never ask for and never read
  a challenge. Their own `429` answers stay plain.
- The `403` `step_up_required` of sensitive actions is unrelated and unchanged. Both codes live in `message`, so a client
  tells them apart by status and code: `429` `challenge_required` before sign-in, `403` `step_up_required` after it.
- The "challenge cookie" of the second factor and the login challenge of this change are separate things. The cookie is not
  set, read or cleared by a `ChallengeRequest` or a `ChallengeResponse`.

## Decision: Basic, Bearer and console clients (2026-11-18)

Unchanged from the proposal: they are never asked, because they cannot answer. Maven and Gradle keep the `429` with
`Retry-After`.

## Not decided

Nothing on the wire is left open. The Kotlin signatures of `LoginChallengeProvider` (how it produces `parameters` and
verifies `answer`) are settled in the code pull request; they produce and consume exactly the shapes above.
