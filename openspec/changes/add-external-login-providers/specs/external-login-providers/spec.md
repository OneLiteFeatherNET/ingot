# External login providers

## ADDED Requirements

### Requirement: Providers are configured in the login domain and disabled by default

The system SHALL read `oidcProviders` and `ldap` from the shared settings domain `login`. Every provider SHALL be disabled
unless its `enabled` flag is set. Client secrets and the LDAP bind password SHALL be sensitive settings, masked by
`hideSettingsSecrets`.

#### Scenario: a fresh installation announces only the local provider

- **WHEN** no external provider has been configured
- **THEN** `GET /api/auth/providers` lists the local provider and no other provider

#### Scenario: a disabled provider is never announced

- **WHEN** an OIDC provider has `enabled` false
- **THEN** it is absent from `GET /api/auth/providers` and `GET /api/auth/oidc/{id}/login` answers `404`

#### Scenario: a sensitive value is masked

- **WHEN** `hideSettingsSecrets` is enabled and an OIDC client secret is set
- **THEN** the settings API returns a placeholder for the secret, and sending the placeholder back keeps the stored secret

### Requirement: Provider ids are unique slugs

Each OIDC provider id SHALL be unique and SHALL match `[a-z0-9-]{1,64}`. An entry that breaks this rule SHALL be skipped and
logged once per start, without disabling the other entries.

#### Scenario: a duplicate id is skipped

- **WHEN** two entries of `oidcProviders` share an id
- **THEN** the first one is used, the second one is skipped and logged, and no request is served for the second one

### Requirement: The provider list announces type, label and icon

`GET /api/auth/providers` SHALL list each enabled provider with `id`, `type` (`LOCAL`, `OIDC` or `LDAP`), `label` and `icon`.
An OIDC provider SHALL also carry `loginUrl`. The local provider SHALL be listed first while a local user exists or the
first administrator was set up.

#### Scenario: an OIDC provider is announced with its login url

- **WHEN** an OIDC provider with id `corp` is enabled
- **THEN** the list contains `{ id: "corp", type: "OIDC", label, icon, loginUrl: "/api/auth/oidc/corp/login" }`

### Requirement: OpenID Connect login uses PKCE, state and nonce

`GET /api/auth/oidc/{id}/login` SHALL redirect to the authorization endpoint with `code_challenge_method=S256`, a random
state and a random nonce. The state, nonce and PKCE verifier SHALL be carried in a signed cookie that expires after ten
minutes, and SHALL NOT be stored on the server.

#### Scenario: the redirect carries a PKCE challenge

- **WHEN** a client follows `GET /api/auth/oidc/corp/login?redirect=/dashboard`
- **THEN** the response is `302`, the location carries `code_challenge_method=S256`, `state` and `nonce`, and the response sets the `ingot_oidc` cookie

#### Scenario: a callback with a different state is refused

- **WHEN** the callback presents a state other than the one in the cookie
- **THEN** the client is redirected to `/?loginError=invalid_state` and no session cookie is set

#### Scenario: an expired state cookie is refused

- **WHEN** the callback arrives more than ten minutes after the login redirect
- **THEN** the client is redirected to `/?loginError=invalid_state`

### Requirement: ID tokens are validated completely

The callback SHALL accept an ID token only when its signature verifies against a key of the provider's JWKS, its algorithm is
asymmetric, its issuer and audience match the configuration, its `azp` matches when there are several audiences, its expiry
lies in the future and its nonce equals the nonce of the cookie. Clock skew SHALL be at most 60 seconds, and the clock SHALL be
injected.

#### Scenario: a token signed by another key is refused

- **WHEN** the ID token signature does not verify against the JWKS
- **THEN** the callback is redirected to `/?loginError=invalid_id_token` and no session is started

#### Scenario: a token signed with an HMAC algorithm is refused

- **WHEN** the ID token header names `HS256`, even when signed with the client secret
- **THEN** the callback is refused with `invalid_id_token`

#### Scenario: an expired token outside the skew is refused

- **WHEN** the token expired 61 seconds before the injected clock reads
- **THEN** the callback is refused with `invalid_id_token`, and a token that expired 59 seconds before is accepted

### Requirement: Discovery and JWKS are cached

Discovery and the JWKS SHALL be cached per provider with a time-to-live of one hour on the injected clock. An unknown key id
SHALL trigger at most one refetch per minute.

#### Scenario: a second login within the time-to-live does not fetch discovery again

- **WHEN** two logins happen within one hour of the injected clock
- **THEN** the discovery document is requested once

#### Scenario: a rotated key is picked up without a restart

- **WHEN** an ID token names a key id that the cached JWKS does not hold, and the minute has passed
- **THEN** the JWKS is fetched again once and the token is accepted when the new key verifies it

### Requirement: Redirects after login stay on the same origin

The `redirect` parameter SHALL be a path that starts with a single `/`, contains no backslash, no colon before its first
slash, no control character and at most 1024 characters. Any other value SHALL be replaced by `/`. The response location SHALL
be the base path followed by the accepted path.

#### Scenario: an absolute URL is replaced

- **WHEN** the redirect is `https://evil.example/steal`
- **THEN** the login redirects to the base path `/`

#### Scenario: a protocol-relative URL is replaced

- **WHEN** the redirect is `//evil.example`
- **THEN** the login redirects to the base path `/`

#### Scenario: a relative path is kept

- **WHEN** the redirect is `/repositories/releases?page=2`
- **THEN** the login redirects to `/repositories/releases?page=2` under the base path

### Requirement: GitHub uses OAuth 2 with its own endpoints

A provider with icon `GITHUB` and an empty or `https://github.com` issuer SHALL use GitHub's authorize, token, user and team
endpoints, and SHALL NOT perform OpenID Connect discovery. Its groups are the organisations and `organisation/team-slug`
pairs of the user. When the configured scopes are blank or equal `openid profile email`, the scopes sent are
`read:user user:email read:org`.

#### Scenario: GitHub login without an issuer

- **WHEN** a provider with icon `GITHUB` and no issuer is enabled and the user completes the flow
- **THEN** no request for `.well-known/openid-configuration` is made, and the user is identified by the numeric GitHub id

### Requirement: External logins create users just in time and never link to local accounts

A first successful external login with `autoCreateUsers` set SHALL create a user principal named after the user-name claim,
and SHALL link it to the external subject in the table `user_identity`. A name held by another principal SHALL be refused with
`username_taken`. A login SHALL be refused with `setup_required` while no user exists, and with `user_not_provisioned` when
`autoCreateUsers` is false and no link exists. A disabled principal SHALL be refused with `user_disabled`.

#### Scenario: a new subject creates a linked user

- **WHEN** an unknown subject logs in through OIDC provider `corp` with `preferred_username` `alice`
- **THEN** a user `alice` exists, and `user_identity` links provider `oidc:corp` and the subject to it

#### Scenario: a local account with the same name is not taken over

- **WHEN** a local user `alice` exists and an external subject with `preferred_username` `alice` logs in
- **THEN** the login is refused with `username_taken` and the local account is unchanged

#### Scenario: an empty instance cannot be claimed through a provider

- **WHEN** no user exists and an external subject logs in
- **THEN** the login is refused with `setup_required`

### Requirement: Groups synchronise into teams with external references

At each successful external login the system SHALL add a `SYNC` membership to every existing team whose `external_ref` equals
`oidc:<id>:<group>` (or `ldap:<group>` for LDAP) for a group of the login, and SHALL remove `SYNC` memberships of the principal
in teams of the same provider prefix that are no longer matched. `MANUAL` memberships and memberships of other providers SHALL
NOT change.

#### Scenario: a group is added and later removed

- **WHEN** a user in group `devs` logs in and the team with ref `oidc:corp:devs` exists, and at the next login the group is gone
- **THEN** the first login adds a `SYNC` membership and the second login removes it

#### Scenario: a manual membership survives

- **WHEN** a user has a `MANUAL` membership in a team that is also matched by a group
- **THEN** the membership stays `MANUAL` and is not removed when the group is gone

### Requirement: Admin groups grant the instance role through a synchronised team

For each name in `adminGroups` the system SHALL create the team with ref `<prefix><group>` when it does not exist, and grant it the
instance role. For a team of the provider prefix, the instance role SHALL be granted while its group is listed in `adminGroups`
and revoked when it is not. A team with the same name and no such ref SHALL NOT be taken over.

#### Scenario: a member of an admin group becomes an instance admin

- **WHEN** a user of group `ops` logs in through `corp` and `ops` is listed in `adminGroups`
- **THEN** the user is an instance administrator on the next request

#### Scenario: a member loses the role when the group leaves the list

- **WHEN** `ops` is removed from `adminGroups` and the user logs in again
- **THEN** the user is no longer an instance administrator

### Requirement: LDAP login escapes filters and refuses empty secrets

The LDAP login SHALL escape every substituted value according to RFC 4515. An empty user name or an empty password SHALL be refused
before any directory request. The search SHALL return exactly one entry. Only a credentials failure of the user bind SHALL be a
rejection; any other directory failure SHALL answer `503`.

#### Scenario: an injection payload stays a literal

- **WHEN** the user name is `*)(uid=*` and the filter is `(uid={username})`
- **THEN** the search matches nothing, and the request is answered as an unknown user

#### Scenario: an empty password is refused without a bind

- **WHEN** `POST /api/auth/login` has `provider: "ldap"` and an empty password
- **THEN** the response is `401` and the directory receives no user bind

#### Scenario: an unreachable directory is not a wrong password

- **WHEN** the directory cannot be reached
- **THEN** the response is `503` and the failure counter is not increased

### Requirement: LDAP logins share the failure limiter with the local login

LDAP password logins SHALL use the same failure limiter, audit records and session cookies as the local login. Repeated failures
of one user name from one address SHALL lock the attempt with `429` and `Retry-After`, and the directory SHALL NOT be asked while the
lock holds.

#### Scenario: failures of the LDAP provider count towards the local limit

- **WHEN** an LDAP user name fails five times from one address
- **THEN** the sixth attempt is answered `429` without a directory request

### Requirement: The legacy LDAP Basic authentication does not change

The authenticator of `authentication.ldap` used by Maven clients SHALL keep its settings, its behaviour and its tests. The
settings of `login.ldap` SHALL NOT be read by it, and it SHALL NOT be read from `login.ldap`.

#### Scenario: an existing Basic LDAP client works unchanged

- **WHEN** an existing Maven client authenticates with its LDAP user name and password over Basic
- **THEN** the answer is the same as before this change, and the existing LDAP authenticator tests pass unchanged

### Requirement: Connection tests are available to instance administrators

`POST /api/auth/providers/oidc/{id}/test` and `POST /api/auth/providers/ldap/test` SHALL be available to instance administrators only.
They SHALL answer `200` with `ok` and, on failure, an `error`. They SHALL NOT echo a password, a client secret or a bind password.

#### Scenario: an OIDC test reports the discovered endpoints

- **WHEN** an administrator tests a reachable OIDC provider
- **THEN** the answer is `{ ok: true, issuer, endpoints }` and the endpoints list the authorization, token and JWKS URLs

#### Scenario: an LDAP test without credentials only binds

- **WHEN** an administrator tests the LDAP provider without a user name
- **THEN** the answer reports whether the service bind succeeded, and no user is looked up

### Requirement: Failed external callbacks redirect with a stable code

A failed external login SHALL redirect to `/?loginError=<code>`, where the code is one of the codes of section 14.4 of the identity
model specification. The code SHALL NOT reveal which check failed beyond that code.

#### Scenario: a provider that denies access

- **WHEN** the provider returns `error=access_denied` on the callback
- **THEN** the client is redirected to `/?loginError=access_denied`
