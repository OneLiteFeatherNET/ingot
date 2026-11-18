# Design

**Status: DECIDED (2026-11-18).** The wire format is option (a). The code waits only for the account throttle, see `tasks.md`.

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

## Not decided

The field name and the `ChallengeRequest` and `ChallengeResponse` shapes are not decided. They follow from option (a) and
are settled in the spec before the code starts.
