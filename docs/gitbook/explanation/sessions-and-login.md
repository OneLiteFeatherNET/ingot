---
description: Explains how a person signs in to the Ingot dashboard. Covers the two ways a request is authenticated, short session tokens, refresh tokens with an absolute lifetime, cookies, CSRF protection, failure lockouts, the setup code for the first administrator, and what logout and password changes end.
---

# Sessions, login and CSRF

A request to Ingot is authenticated in one of two ways. A script or build tool sends a token in an `Authorization` header. A person in the dashboard sends cookies that belong to a browser session. This page explains the second way: how a login becomes a session, how long it lasts, and how Ingot protects it. For the login providers themselves, see [Login providers](../reference/login-providers.md).

If an `Authorization` header is present, Ingot uses it and ignores the session cookies. Only a request without that header is treated as a browser session.

## Session and refresh token

A successful login gives the browser two values.

* The **session token** is a signed JSON Web Token (JWT). It names the user and the login it belongs to. It is valid for **15 minutes**. Ingot checks its signature and expiry on each request, and it also checks that the user is still enabled.
* The **refresh token** is a random value. Ingot stores only a hash of it. It is used to get a new session token when the old one runs out.

The session token is signed with a key that is stored in the instance database. Every replica reads the same key, so a session that one replica issued is accepted by all of them.

### Refresh rotation

A refresh token can be used once. Using it returns a new session token and a new refresh token in the same **family**. The old refresh token is then spent.

The dashboard renews its session when a request fails with `401`. It calls the refresh endpoint once and repeats the request. Concurrent failures share one renewal.

If a spent refresh token is presented again, Ingot treats it as theft. It revokes the whole family, so the family cannot be renewed again, and it records a `SESSION_REFRESH_REUSE_DETECTED` audit event. A session token already issued in that family stays valid until it expires, at most 15 minutes.

### Absolute lifetime

Renewal does not keep a login alive forever. The refresh token has two limits:

* An **idle** limit of 168 hours (7 days) by default. Each renewal issues a new refresh token with a new idle limit.
* An **absolute** limit of 30 days by default, counted from the moment of the login. No renewal can go past it. After it, the browser must log in again.

The idle limit never reaches past the absolute limit, so the absolute limit is the one that decides in the end.

## Cookies

The browser holds three cookies.

| Cookie | Holds | HttpOnly | Sent to |
| --- | --- | --- | --- |
| `ingot_session` | session token | yes | the instance path |
| `ingot_refresh` | refresh token | yes | the authentication endpoints only |
| `ingot_csrf` | CSRF value | no | the instance path |

Scripts on the page cannot read the two HttpOnly cookies. The CSRF cookie has to be readable, because the page sends its value back in a header (see below).

All three cookies use `SameSite=Lax`. They get the `Secure` attribute when the request came over HTTPS, or when the proxy in front of Ingot says so with `X-Forwarded-Proto: https`. If Ingot runs behind a proxy with TLS, make sure the proxy sets that header. Otherwise the cookies are not marked secure.

## CSRF protection

A cross-site page can make a browser send a cookie, but it cannot read the cookie and copy it into a header. Ingot uses this to protect changes made with cookies.

A request that changes data, such as `POST`, `PUT` or `DELETE`, and that carries a session or refresh cookie, must send the header `X-CSRF-Token`. The value must match the `ingot_csrf` cookie. A request without the header, or with a different value, is refused with `403`.

Some requests are exempt:

* Safe methods (`GET`, `HEAD`, `OPTIONS`, `TRACE`).
* Requests with an `Authorization` header.
* The login and setup requests, which create the cookies in the first place.

## Failed logins and lockouts

Ingot counts failed password logins. It keeps the counts in the database, so every replica sees the same counts.

* A failure counts against two keys: the pair of user name and address, and the address alone.
* After **5** failures, the key is locked. The default lock lasts **15 minutes**.
* Each lock that follows another lock without a quiet period is twice as long as the one before, up to **24 hours**.
* A login that is refused because of a lock gets the HTTP status `429` with a `Retry-After` header. The lock is not extended by further attempts while it lasts.
* A successful login clears the failures counted for its pair of user name and address. The failures counted for the address alone are kept, so a successful login cannot reset them.

The number of failures and the first lock time can be changed in the `login` settings. The defaults and the other login settings are listed in [Login providers](../reference/login-providers.md).

These lockouts apply to password logins. Basic and token authentication have their own brute force protection, which is separate. It is **off by default**. When it is enabled, it blocks an address after 10 failed attempts by default, for 300 seconds. Turn it on in the authentication settings if your instance accepts Basic or token authentication from networks you do not control.

## The setup code and the first administrator

An instance with no user needs a first administrator. Ingot prepares for it at start.

* When the instance starts and no user exists, Ingot writes a **setup code** to the log. The code has 12 characters. It uses upper-case letters and digits, without the characters that are easy to confuse (`0`, `O`, `1`, `I` and `L`).
* The dashboard asks for the code, a user name and a password. The code is compared without regard to case or surrounding spaces.
* Once the first administrator exists, the code is cleared. It also ends when any user appears, for example through an external provider.
* The code does not expire by time. A restart writes a new code, and the old code stops working.

Each replica keeps its own code in memory. A replica only accepts the code it wrote to its own log. While no administrator exists, send the setup request to the replica whose log shows the code. The claim of the first administrator is shared across replicas, so only one setup can complete, even when two replicas receive the request.

Wrong codes count against the address, the same way failed passwords do.

## What logout and password changes end

* **Logout** revokes the refresh token family of the browser. The browser's refresh cookie stops working at once. The session token already in use stays valid until it expires, at most 15 minutes later. Logout is therefore not instant for requests that carry a session token that was issued before it.
* **A disabled user** loses access on the next request, because every session request checks that the user is still enabled.
* **A password change** ends every login of the user, on every replica: its refresh tokens are revoked. A session token already in use stays valid until it expires, at most 15 minutes later.
* **A password reset by an administrator** does the same. In addition, the user must choose a new password at the next login.

## Related pages

* [Login providers](../reference/login-providers.md) lists the providers and their settings.
* [Identity model](identity-model.md) explains the user and the accounts a session belongs to.
* [Multi-replica operation](multi-replica-operation.md) explains what is shared between replicas.
* [Access tokens and routes](../reference/access-tokens-and-routes.md) describes the tokens that scripts use instead of a session.
