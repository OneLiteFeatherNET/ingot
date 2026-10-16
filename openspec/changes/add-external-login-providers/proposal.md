# Proposal

## Why

Phase 2a of the identity model (`docs/superpowers/specs/2026-10-15-identity-model.md`, section 13) gives browser sessions
one login provider: a local username and password. Organisations that run Ingot already keep their people in an identity
provider or a directory. Without external providers each of them needs a second password, and leaving the organisation
does not remove the Ingot account. Section 11 lists this as open point O-1.

This change adds external login providers behind the existing provider interface and session layer: OpenID Connect (any
standard provider, with discovery), GitHub (OAuth 2, built in, because GitHub is not OpenID Connect), and LDAP as a
browser login. Provider groups are synchronised into teams, and a group can make its members instance administrators.
Every provider is disabled by default, so an existing installation keeps its behaviour.

## What Changes

- Add settings to the existing shared settings domain `login`: `oidcProviders` (a list of providers, each with an id, a
  label, an icon, the issuer and the client credentials) and `ldap` (one directory). Defaults keep every provider disabled.
  Client secrets and the LDAP bind password are sensitive and masked by `hideSettingsSecrets`.
- `GET /api/auth/providers` lists the enabled providers with `type` (`LOCAL`, `OIDC`, `LDAP`), `label`, `icon` and, for OIDC,
  `loginUrl`. A disabled provider is never listed. The local provider stays listed while a local user exists or the setup
  was completed.
- OpenID Connect: `GET /api/auth/oidc/{id}/login` starts a flow with PKCE (S256), a state and a nonce. The state, nonce and
  verifier are kept in a short-lived signed cookie, so no server session is needed. `GET /api/auth/oidc/{id}/callback`
  exchanges the code, validates the ID token (issuer, audience, nonce, signature through the JWKS, expiry with clock skew)
  and starts the existing browser session. Discovery and JWKS documents are cached with a time-to-live on an injected clock.
- GitHub: an OIDC provider with the icon `GITHUB` and an empty or `https://github.com` issuer uses GitHub's OAuth endpoints
  directly. Its groups are organisations and `organisation/team-slug` pairs.
- LDAP login: `POST /api/auth/login` with `provider: "ldap"` performs a service bind, a search with an escaped filter
  (RFC 4515), and a bind as the user. It shares the failure limiter, the audit trail and the session cookies with the local
  password login.
- Just-in-time users: a first successful external login creates a user principal and links it to the external subject in
  the new table `user_identity`. A name that is already taken by another principal is refused, never linked.
- Group synchronisation: groups are matched to teams whose `external_ref` is `oidc:<id>:<group>` (or `ldap:<group>`).
  Membership rows of source `SYNC` are added and removed at each login. Manual memberships are never touched.
- Admin groups: a group listed in `adminGroups` makes its members instance administrators through a synchronised team that
  holds the instance role. No direct role is written for a person.
- Admin test endpoints: `POST /api/auth/providers/oidc/{id}/test` fetches discovery and JWKS, and
  `POST /api/auth/providers/ldap/test` binds and optionally looks a user up. Both answer `200` with `ok` and a reason, and
  never echo a password or a secret.
- The legacy LDAP Basic authentication (`com.reposilite.auth.LdapAuthenticator`, settings `authentication.ldap`) is not
  changed. The new LDAP login provider has its own settings and does not read them. The relation is documented in the guide.
- Documentation: `reposilite-site/data/guides/authentication/external-login.md`, and a link from `authentication/ldap.md`.
- No new library. Discovery and token exchange use `java.net.http`, LDAP uses JNDI, and ID token signatures use the
  `nimbus-jose-jwt` version already on the classpath.

### Pull request

Ships under `feat(identity)`. Pull request title, squash-merged onto main:

    feat(identity): sign users in through external login providers

### Out of scope

- Sessions, refresh tokens, password logins and CSRF rules of phase 2a. External providers only add ways to start a session.
- Dashboard buttons and the provider editor. The frontend is built against the contract in section 14 of the identity spec.
- SAML, SCIM provisioning, de-provisioning by the directory, and user info endpoints beyond what is needed for the claims.
- Group membership read from a provider's userinfo endpoint. Groups come from the ID token (OpenID Connect) or the GitHub API.
- Account linking for an existing local user with the same name. A conflict is refused, so an external login never takes over
  a local account.
- Changing the legacy LDAP Basic authentication, its settings or its tests.

## Breaking changes

**BREAKING**: none. Every new key is in the `login` domain with a default that keeps the provider disabled. No environment
variable, configuration file, volume, port, manifest or image reference changes meaning. A new database table
(`user_identity`) is created on start with the other identity tables, and no existing table is altered.

## Capabilities

### New Capabilities

- `external-login-providers`: OpenID Connect, GitHub and LDAP as browser login providers, their configuration, the login
  flows, just-in-time users, group synchronisation into teams, admin groups and the connection test endpoints.

### Modified Capabilities

- None in the openspec sense. The local login, the sessions and the team model keep their requirements. The contract additions
  are recorded in section 14 of the identity model specification.
