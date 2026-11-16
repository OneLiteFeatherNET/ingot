# Design

**Status: DEFERRED.** No code is planned until a plugin needs a login challenge.

## Defaults that stand

The defaults of `add-login-protection` design D6 stand and are not decided again here. No provider is registered by
default. At most one provider is used. When several are registered, the first by plugin load order is used and a warning
is logged.

## Open decision: the wire format of the challenge

The maintainer chooses one of the three options below. None is chosen in this document.

### Option a: an optional field on the login request body

- The login request gets an optional field that carries the challenge response.
- Trade-off: one route and one round trip. Existing clients send no field and keep working.
- Cost: the OpenAPI snapshot and the frontend types of the login request change.

### Option b: a separate challenge endpoint, called before the login

- The browser asks a challenge endpoint first, then calls the login route with the result.
- Trade-off: the login route is unchanged.
- Cost: two round trips for a delayed login, a new endpoint to design and document, and state that must be held between
  the two calls.

### Option c: keep the challenge out of core

- The plugin serves its own page for the challenge, and the login route is unchanged.
- Trade-off: no change to the core API, and the plugin owns the page and its verification.
- Cost: core still has to tell the plugin that a login is delayed, so an interface for that signal remains needed.

## Not decided

The `ChallengeRequest` and `ChallengeResponse` shapes are not decided. They follow from the option chosen.
