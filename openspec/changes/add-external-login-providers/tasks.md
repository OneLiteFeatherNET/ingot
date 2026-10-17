# Tasks

Each group is one Conventional Commits commit of one type. Tests of a group are written and seen to fail before the code they
test, and they land in the same commit as that code. The pull request is squash-merged, so its title is the commit on main.

## 1. Specification

- [ ] 1.1 `docs(openspec): propose sso and ldap login providers`: this change (proposal, design, spec delta, tasks) and section 14 of
  `docs/superpowers/specs/2026-10-15-identity-model.md` with the contract. Verify: `openspec validate add-external-login-providers --strict`
  passes, and no em or en dash appears in the changed files.

## 2. OpenID Connect sign-in

- [ ] 2.1 `feat(identity)`: write failing unit tests for discovery caching on an injected clock, PKCE S256, the state cookie
  (tampering, provider mismatch, expiry), the ID token validator (signature, `HS256` and `none` refused, issuer, audience, `azp`,
  expiry with skew, nonce, `kid` lookup and one refetch per minute), and the redirect policy. Verify: they fail to compile or fail
  before 2.2.
- [ ] 2.2 `feat(identity)`: add the `oidcProviders` settings with `@Sensitive` and `@SettingsKey`, the `login.oidc` package with
  discovery and JWKS caches, PKCE, the state cookie, the validator and the redirect policy. Verify: 2.1 passes.
- [ ] 2.3 `feat(identity)`: write failing tests for the just-in-time rules (create, link, `username_taken`, `user_disabled`,
  `user_not_provisioned`, `setup_required`) and for `user_identity` cascading with the principal. Then add the table
  `user_identity`, the store methods, the `oidc` login flow and the routes `GET /api/auth/oidc/{id}/login` and `.../callback`,
  with the provider list. Verify: the tests pass, and `IdentityStore.deletePrincipal` removes the link.
- [ ] 2.4 `feat(identity)`: write failing integration tests against an in-process `HttpServer` IdP (login redirect, callback with a
  valid token, wrong nonce, `error=access_denied`, disabled provider hidden) in `src/integration`, then document the provider in
  `docs/gitbook/reference/login-providers.md` with the redirect uri, the claims and the error codes. Verify: the
  targeted integration class passes with `--max-workers=2`.

## 3. GitHub sign-in

- [ ] 3.1 `feat(identity)`: write failing tests for the GitHub mapping (subject from the numeric id, login as the user name, email
  absent, scope default replaced, organisation and team groups) with a stub of the three GitHub endpoints, then add the GitHub branch
  of the OIDC flow. Verify: the tests pass, and the OIDC tests of 2.x still pass.

## 4. LDAP sign-in

- [ ] 4.1 `feat(identity)`: write failing tests for RFC 4515 escaping (including `*)(uid=*` and NUL), the empty user name and password
  refusals (no directory call), exactly-one search results, the `Unavailable` outcome, and the LDAP provider against the in-memory
  UnboundID server (login, wrong password, unknown user, disabled user, StartTLS flag accepted). Then add `login.ldap`, the LDAP
  provider and the new `LoginOutcome` variants `Unavailable` and `Refused`. Verify: the tests pass.
- [ ] 4.2 `feat(identity)`: write failing integration tests for `POST /api/auth/login` with `provider: "ldap"`, including `429` after
  five failures, and that the legacy LDAP Basic authentication test class is unchanged and passes. Then wire the provider into
  `LoginService.providers()` and document the relation to `authentication.ldap` in the guide. Verify: targeted tests pass.

## 5. Group synchronisation and admin groups

- [ ] 5.1 `feat(identity)`: write failing tests for group sync (add `SYNC`, remove on the next login, `MANUAL` untouched, other provider
  prefixes untouched, unknown refs ignored), admin groups (grant on listing, revoke on removal, a same-named team without the ref is not
  taken over), for OIDC and LDAP. Then add the synchronisation service and call it from both flows. Verify: the tests pass and the
  instance-role tests of phase 1b still pass.

## 6. Connection tests

- [ ] 6.1 `feat(identity)`: write failing tests for `POST /api/auth/providers/oidc/{id}/test` (reachable, unreachable, issuer mismatch)
  and `POST /api/auth/providers/ldap/test` (bind only, user lookup, wrong password, no secret echoed), and for both being instance-admin
  only. Then add the two endpoints. Verify: the tests pass.
- [ ] 6.2 `docs(site)`: finish the guide with the admin test endpoints and link `authentication/ldap.md` to the new page. Verify: every key
  of `login.oidcProviders` and `login.ldap` appears in the guide.

## 7. Integration follow-ups

Found when the frontend was wired to the contract. Each one is its own commit, named as written.

- [x] 7.1 `fix(frontend): use the ldap placeholders of the backend`: the user filter takes `{username}`, the group filter `{dn}`, in
  the defaults, the helper texts and the validation. Verify: `loginSettings.test.js` and `LdapPanel.test.js` pass.
- [x] 7.2 `fix(frontend): align the sso provider form with the backend contract`: scopes as one text, the id slug of the backend,
  providers that start disabled, the issuer and secret rules of GitHub and of other providers, and every login error code.
  Verify: `SsoProvidersPanel.test.js`, `loginSettings.test.js` and `loginErrors.test.js` pass.
- [x] 7.3 `build(frontend): mirror the login provider api in the fake backend`: the provider list, the placeholders, the test report
  shapes and the error texts of the backend. Verify: a throwaway smoke run of the routes of `login-providers.cjs` (not committed) matched the backend's answers.
- [x] 7.4 `fix(identity): log skipped sso provider entries`: a skipped or duplicate entry is logged once per start and per change,
  with its reason and without a secret. Verify: `OidcProviderCatalogTest` passes with a captured log.
- [x] 7.5 `feat(identity): build sso redirects from a configured public url`: `login.publicUrl`, validated, used for the redirect uri
  and for the callback preview of the SSO tab. Verify: `PublicUrlTest`, `OidcRedirectUriTest` and `loginSettings.test.js` pass.
- [x] 7.6 `feat(audit): record team changes from provider sync`: `TEAM_MEMBERSHIP_CHANGED` with the actor `system:<provider>` for
  every synchronised membership change. Verify: `ExternalGroupSyncTest` passes, and the audit labels are tested.
- [x] 7.7 `fix(settings): point the login domain to the access page`: the login domain is a card with links, not a generic form.
  Verify: `SettingsView.test.js` and `configuration.test.js` pass.

## 8. Pull request

- [ ] 8.1 Open the pull request titled `feat(identity): sign users in through external login providers`, once the branch is pushed.
  Verify: `gh pr view` shows the title, and the body lists the commits and the test commands with their results. The first
  implementation session does not push, so this task stays open until the branch is reviewed.
