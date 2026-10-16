# Design

The normative contract for the frontend is section 14 of `docs/superpowers/specs/2026-10-15-identity-model.md`. This
document records the decisions behind it. Where the two differ, section 14 is what was implemented.

## 1. Where the code lives

- `net.onelitefeather.ingot.identity.login.oidc`: settings, discovery and JWKS cache, PKCE, state cookie, ID token
  validation, the OIDC and GitHub flows, the connection test.
- `net.onelitefeather.ingot.identity.login.ldap`: the LDAP login provider, filter escaping, the connection test.
- `net.onelitefeather.ingot.identity.login.external`: just-in-time users, `user_identity`, group synchronisation and
  admin groups. Shared by both protocols.
- Wiring stays small: `LoginService` takes the provider list from a function instead of a fixed list, `LoginPlugin` creates
  the new services, `LoginSettings` gains two fields, `AuthLoginEndpoints` gains routes and a provider DTO gains two fields,
  `IdentityStore.deletePrincipal` also removes `user_identity` rows, and `IDENTITY_TABLES` lists the new table.

## 2. Configuration

Both settings live in the `login` domain. The domain is shared, so the frontend reads and writes it through the existing
shared settings API. Secrets use `@Sensitive`, and list entries use `@SettingsKey` on `id`, so masking and restoring behave
as they do for repositories.

- Entries of `oidcProviders` with an empty or duplicate id, or an id that is not a slug (`[a-z0-9-]{1,64}`), are skipped
  and logged once per start. Skipping avoids a broken entry taking the whole list down.
- An enabled provider with missing required fields (issuer for OIDC, client id, url for LDAP) is listed as unavailable: it
  is not announced, and its routes answer `503` with a message that names the missing field, never the secret.

## 3. OpenID Connect

### Discovery and JWKS

- Discovery is `GET {issuerUrl}/.well-known/openid-configuration`. The `issuer` in the document must equal the configured
  issuer, after removing a trailing slash on both sides. Otherwise the provider is unavailable.
- Discovery and the JWKS are cached per provider with a time-to-live of one hour. The clock is injected. A key id that is not
  in the cached JWKS triggers one refetch, at most once per minute, so that key rotation converges without a restart.
- Only `https` issuers are accepted, except loopback addresses (`127.0.0.1`, `::1`, `localhost`), so that tests and local
  development work without TLS.
- Requests have a connect and a read timeout of five seconds and follow no redirects.

### Authorization request

- `GET /api/auth/oidc/{id}/login?redirect=<path>` creates a random state, a nonce and a PKCE verifier of 32 bytes each. The
  challenge is `BASE64URL(SHA256(verifier))`, method `S256`.
- The state, nonce, verifier, provider id and redirect path go into the cookie `ingot_oidc`. It is a JWT signed with HMAC-SHA256
  with a key derived from the session signing key (HMAC of the label `ingot-oidc-state`), so a separate key is not stored.
  Lifetime ten minutes, checked against the injected clock. The cookie is HttpOnly, SameSite=Lax (Strict would not be sent on
  the return from the identity provider), path `<base>/api/auth/oidc`, and `Secure` under the same rule as the session cookies.
- The response is `302` to the authorize endpoint with `response_type=code`, `client_id`, `redirect_uri`, `scope`, `state`,
  `nonce`, `code_challenge` and `code_challenge_method=S256`.

### Callback

1. A missing or invalid state cookie, a provider id that differs from the path, or a state that differs from the cookie is
   `invalid_state`. The cookie is cleared in every outcome.
2. An `error` parameter from the provider is `access_denied` when the provider says so, otherwise `provider_error`.
3. The code is exchanged at the token endpoint with `grant_type=authorization_code`, `code_verifier`, the redirect uri and
   client authentication by HTTP Basic (`client_secret_basic`). A failure is `token_exchange_failed`.
4. The ID token is validated. The header must name `RS256`, `RS384`, `RS512`, `PS256`, `ES256`, `ES384` or `ES512`. `none` and
   the HMAC algorithms are rejected, so a token signed with the client secret is never accepted. The key is looked up by `kid`
   in the JWKS (a key without `kid` is accepted only when the JWKS holds exactly one key). The signature is checked with
   nimbus. Then the claims: `iss` equals the issuer, `aud` contains the client id, `azp` equals the client id when there are
   several audiences, `exp` lies in the future with 60 seconds of skew, `iat` is not more than 60 seconds ahead, and `nonce` equals
   the nonce of the cookie. Any failure is `invalid_id_token`, and the reason is logged at debug level only.
5. The claims give the subject (`sub`), the user name (`usernameClaim`, default `preferred_username`), the display name
   (`displayNameClaim`, default `name`), the email (`emailClaim`) and the groups (`groupsClaim`, a JSON array or a single string).
   The subject is required, and so is the user name when a user is created.
6. Redirect uris are built from the request: the scheme is `https` when `SessionCookies.isSecure` says so, otherwise `http`,
   the host is the `Host` header, and the base path is the configured one. The identity provider compares the uri with the
   registered one, so a forged `Host` cannot redirect a code. This is a known limit: a deployment that sits behind a proxy
   which rewrites `Host` must set it correctly. An operator-set public URL (`login.publicUrl`) replaces the request-derived
   base, so the registered uri does not depend on `Host` at all.

## 4. GitHub

GitHub is OAuth 2, so the ID token step is replaced by the API.

- Endpoints are fixed: authorize `https://github.com/login/oauth/authorize`, token `https://github.com/login/oauth/access_token`,
  user `https://api.github.com/user`, teams `https://api.github.com/user/teams`. They apply when the icon is `GITHUB` and the
  issuer is empty or `https://github.com`.
- The scopes default `openid profile email` is not a GitHub scope. When the configured scopes are blank or equal that default,
  GitHub uses `read:user user:email read:org`. Any other value is sent as configured.
- The token request asks for JSON (`Accept: application/json`). The access token is sent as `Authorization: Bearer`.
- Subject: the numeric user `id`, as a string. User name: `login`. Display name: `name`, falling back to `login`. Email: `email`
  from the user document (it can be absent, which fails any configured domain rule).
- Groups: each organisation of `/user/teams` gives the group `<org-login>` and each team gives `<org-login>/<team-slug>`. The
  groups are matched to teams with external refs `oidc:<id>:<org>` or `oidc:<id>:<org>/<team-slug>`. A `GITHUB` provider uses
  the same group and team code as OIDC; only the authentication differs.
- PKCE is sent as well. It is harmless if the provider ignores it.

## 5. Just-in-time users and linking

- `user_identity` (`provider` varchar 64, `subject` varchar 512, `principal_id` foreign key cascading, unique on
  `(provider, subject)`). The provider is `oidc:<id>` or `ldap:<url-host>`, as the contract says.
- A known identity logs in as its principal. A disabled principal is refused with `user_disabled`. Its display name is kept
  up to date when the claim differs.
- An unknown identity with `autoCreateUsers=false` is refused with `user_not_provisioned`.
- An unknown identity with `autoCreateUsers=true` creates a user principal named after the user-name claim. The name must
  satisfy the setup rules (no colon, no whitespace, no control characters, at most 255 characters). If a principal of that
  name exists, the login is refused with `username_taken`. An external login never links to an existing local account.
- Creation is refused with `setup_required` while no user exists. The first administrator is created with the setup code
  (section 13.7), so an external provider cannot create an instance without an administrator. This is the only guard against
  an empty instance being claimed by whoever logs in first through a provider.
- Creation is audited as `PRINCIPAL_CREATED`, with the actor `oidc:<id>` or `ldap`.

## 6. Group synchronisation and admin groups

- A team takes part in synchronisation when its `external_ref` starts with `oidc:<id>:` for a provider, or with `ldap:` for
  the LDAP provider. Teams with other refs are never touched.
- At each successful external login, the desired set is the teams whose ref equals `<prefix><group>` for one of the groups
  of the login. Each desired team gets a membership with source `SYNC` if the principal has none yet. Each `SYNC` membership of
  the principal in a team of the same provider prefix that is not desired is removed. `MANUAL` memberships are never changed.
  `SYNC` memberships of other providers are never changed.
- Only existing teams are matched for ordinary groups. An operator creates the team with the external ref, and nothing is
  created on login.
- Admin groups: for each name in `adminGroups` the team with ref `<prefix><group>` is created on demand. The admin right of that
  team is set through `AdminRights`, one function of the external package. It sets the instance role of the team today and will
  set the permission node `ingot.admin.*` on the team once ADR-INGT-0003 lands. Nothing writes a principal's admin right directly. A team of the same name without that ref is not taken over; the admin
  group is then logged as skipped. For a team of the provider prefix, the instance role follows `adminGroups`: granted when
  the group is listed, revoked when it is not. A manually granted instance role is on a manual team and is never touched.
  The last-admin rule of section 7.4 applies to these teams as it does to any other.
- Consequence: group memberships are read at an external login and never at a refresh, because a refresh does not contact the
  identity provider. A group removed in the directory therefore takes effect at that user's next external login, and an open
  session keeps its memberships until its refresh family ends (at most `login.refreshAbsoluteDays` after the login). Ending the
  sessions of a user at once is possible through a password reset or a revocation, which is an open point (section 14.6).

## 7. LDAP

- Implemented with JNDI (`com.sun.jndi.ldap.LdapCtxFactory`), as the legacy authenticator does. No dependency is added.
- The service bind uses `bindDn` and `bindPassword`. A blank `bindDn` searches anonymously.
- The user filter is the template with `{username}` replaced by the escaped user name. Escaping follows RFC 4515: `\`, `*`,
  `(`, `)`, NUL, and every byte above 0x7f are written as `\hh`. Unit tests pin injection payloads such as `*)(uid=*` and
  `admin)(|(uid=*`.
- An empty user name or an empty password is refused before any directory call. An empty password is an unauthenticated bind
  in LDAP, and a successful bind would be a login for anyone.
- The search returns exactly one entry. Zero entries and several entries are both refused. The user bind uses the DN of the
  entry and the password, and only a credentials failure (`AuthenticationException`) is a rejection. Other failures are
  `Unavailable`, answered with `503`.
- `ldap://` and `ldaps://` URLs are accepted. `startTls=true` upgrades a plain connection with the StartTLS extended operation
  before the first bind. Connect and read timeouts are five seconds.
- Attributes: `usernameAttribute` gives the user name (falling back to the typed name), `displayNameAttribute` and `emailAttribute`
  give the profile.
- Groups: the group search runs in `groupBaseDn` with `groupFilter`, where `{dn}` and `{username}` are replaced by their escaped
  values, and reads `groupNameAttribute`. Blank `groupBaseDn` means no group sync. The group names become `ldap:<name>` refs.
- The LDAP provider is a `LoginProvider` (password based), so `POST /api/auth/login` with `provider: "ldap"` goes through
  `LoginService.login` like the local provider: the same key limiter, the same `429` with `Retry-After`, the same audit records.
- The new outcomes `Unavailable` (`503`) and `Refused(message)` (`403`) are added to `LoginOutcome`. `Refused` carries the
  reasons `setup_required`, `user_not_provisioned` and `username_taken` for the LDAP path.

## 8. Relation to the legacy LDAP Basic authentication

The legacy authenticator (`com.reposilite.auth.LdapAuthenticator`, settings `authentication.ldap`, Basic and token for Maven
clients) stays exactly as it is, including its settings, its routes and its tests. The new `login.ldap` settings are
independent: nothing is copied from the old settings, and nothing reads the new ones except the browser login. An operator
who wants both points them at the same directory and keeps two sets of settings. The guide says so.

## 9. Login state and errors

- A callback that fails redirects to `<base>/?loginError=<code>` with one of: `invalid_state`, `access_denied`,
  `provider_error`, `token_exchange_failed`, `invalid_id_token`, `email_domain_not_allowed`, `user_disabled`,
  `user_not_provisioned`, `username_taken`, `setup_required`, `provider_unavailable`, `no_database`. The code never carries a
  detail that helps an attacker, and the reason is logged at debug level.
- Failed external logins are audited as `LOGIN_FAILED` with the actor `oidc:<id>` or `ldap` and the address. Successful ones
  as `LOGIN_SUCCEEDED`, as for the local login.
- `allowedEmailDomains`, when not empty, requires an email whose domain (case-insensitive) is listed. It is checked on every
  login, not only at creation, so removing a domain from the list takes effect at the next login.

## 10. Redirect after login

- The `redirect` parameter is a path relative to the UI, with a leading `/`. It is rejected when it starts with `//` or `/\`,
  contains a backslash, a colon before the query string, or any control character, or is longer than 1024 characters.
  Rejected values fall back to `/`.
- The response location is the base path followed by the path, so it stays on the same origin.

## 11. Testing

- Unit tests (`src/test`): discovery caching on an injected clock, PKCE challenge, state cookie tampering and expiry, the
  ID token validator (signature, algorithm, issuer, audience, `azp`, expiry, skew, nonce, `kid`), the redirect policy, the
  email domain rule, the GitHub mapping, filter escaping, the LDAP provider against an in-memory UnboundID server,
  group synchronisation, admin groups, just-in-time users, and the legacy LDAP Basic path (existing tests unchanged).
- The OIDC identity provider is an in-process `com.sun.net.httpserver.HttpServer` on loopback that serves discovery, JWKS and
  the token endpoint. Keys are generated per test. There are no real network calls, no sleeps, and bounded waits use latches.
- Integration tests (`src/integration`, local variant): the provider list with a disabled provider hidden, the OIDC login and
  callback over HTTP against the stub, and the LDAP login through `POST /api/auth/login`, including `429` after repeated
  failures. The admin test endpoints are covered there too.

## 12. Dependencies

No new library. `java.net.http` (JDK) for discovery, JWKS and token requests. JNDI (JDK) for LDAP. `nimbus-jose-jwt` 10.9.1
(already declared) for JWS parsing and verification. `UnboundID` stays a test dependency, as it is today. Because
`reposilite-backend/build.gradle.kts` does not change, the license report is unchanged and `checkLicense` is not affected.

## 13. Operator changes

None are required. The new keys are optional and default to disabled. The new table is created on start. The guide
`reposilite-site/data/guides/authentication/external-login.md` documents every key, the redirect uri to register with the
provider, the group ref convention and the admin group behaviour.
