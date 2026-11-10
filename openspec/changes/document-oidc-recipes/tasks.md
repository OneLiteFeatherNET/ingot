# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `gitbook`. The guard test is written
first and seen to fail before the pages exist. It follows F.I.R.S.T.: repository files only, no network, no clock, no shared state, one
behaviour per case, assertions with messages that name the file. Run it from the repository root with
`python3 -m unittest discover -s charts/ingot/tests -t charts/ingot/tests -v`. No Gradle run is needed, because no Kotlin changes.

## 1. Proposal and verification

- [ ] 1.1 `docs(openspec): propose document-oidc-recipes`: this change and nothing else. Verify:
  `openspec validate document-oidc-recipes --strict` passes and the dash grep prints nothing.
- [ ] 1.2 Spike, no commit: start Keycloak and Authentik as local containers, follow the draft recipe once against a local Ingot, and record the
  screens, the ID token claims (groups with and without the full group path in Keycloak, the `groups` scope mapping in Authentik) and the
  result of **Test connection** in the pull request description. Verify: a login completes, and a group arrives as the exact string the page states.
  If it does not, the page is corrected before it is merged.
- [ ] 1.3 Spike, no commit: for Entra ID, Okta and Google Workspace, check each step against the vendor documentation and record the page and the
  date. Where a developer account or tenant is available, follow the steps once. Verify: each of the three pages carries either a recorded live check
  or the hint "checked against the vendor documentation, not against a live account" with the date.

## 2. Guard test (test(gitbook))

- [ ] 2.1 `test(gitbook)`: write `charts/ingot/tests/test_login_guides.py` with `unittest`. Cases, one behaviour each: every new page is listed once
  in `docs/gitbook/SUMMARY.md`; every JSON key in a settings table of a recipe is a property of `OidcProviderSettings` or `LdapLoginSettings` (read as text
  from the Kotlin sources); every backticked message in the "Message" column of `troubleshoot-external-login.md` occurs as a string in the Kotlin
  sources under `reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/identity/login`; every recipe contains the redirect URI form
  `/api/auth/oidc/` and the section headings fixed in the design. Verify: the cases fail because the pages do not exist, and each failure names the file.

## 3. Recipes (docs(gitbook))

- [ ] 3.1 `docs(gitbook)`: add `docs/gitbook/how-to/configure-keycloak-login.md`: realm and OIDC client with client authentication on, the exact
  redirect URI, the `groups` client scope with the Group Membership mapper and the full group path setting, `usernameClaim`, the Ingot settings
  table, check and Keycloak specific troubleshooting. Verify: test 2.1 passes for this page.
- [ ] 3.2 `docs(gitbook)`: add `docs/gitbook/how-to/configure-authentik-login.md`: provider and application, the issuer with its trailing slash and the
  slug, the scope mappings that put `groups` in the ID token, the Ingot settings table, check and troubleshooting. Verify: test 2.1 passes for this page.
- [ ] 3.3 `docs(gitbook)`: add `docs/gitbook/how-to/configure-okta-login.md`: the web application, the redirect URI, the choice between the org and
  a custom authorization server and its effect on the issuer, the `groups` claim on the ID token with its filter and the `groups` scope, the Ingot
  settings table, check and troubleshooting. Verify: test 2.1 passes for this page.
- [ ] 3.4 `docs(gitbook)`: add `docs/gitbook/how-to/configure-google-workspace-login.md`: the OAuth client in Google Cloud, the consent screen type
  (internal for a Workspace), the redirect URI, `usernameClaim` set to `email`, `allowedEmailDomains`, the statement that there are no groups and what
  follows, the note that `email_verified` is not checked, the Ingot settings table, check and troubleshooting. Verify: test 2.1 passes for this page.
- [ ] 3.5 `docs(gitbook)`: bring `configure-entra-id-login.md` and `configure-github-login.md` to the fixed section order, add the settings table with
  JSON keys to each, and link both to the group guide and the troubleshooting page. Keep their content and their hints. Verify: test 2.1 passes for both.

## 4. Groups and troubleshooting (docs(gitbook))

- [ ] 4.1 `docs(gitbook)`: add `docs/gitbook/how-to/map-provider-groups-to-teams.md`: the reference forms for OIDC, GitHub and LDAP, creating the team
  with its external reference, what a login adds and removes, manual memberships, `adminGroups`, the synchronised admin team, the last-admin warning
  with `LAST_ADMIN_REVOKED_BY_PROVIDER`, the skipped admin group when a team of that name already exists, and the link to
  `recover-admin-access.md`. Replace the repeated passages in `configure-oidc-login.md` and `configure-ldap-login.md` by a short pointer. Verify: test
  2.1 passes and the removed passages are still reachable by link.
- [ ] 4.2 `docs(gitbook)`: add `docs/gitbook/how-to/troubleshoot-external-login.md` with a "Message" column of quoted strings. Sources: the `loginError`
  codes (`ExternalAccountService.kt`, `OidcLoginService.kt`), the test messages (`OidcConnectionCheck.kt`, `LdapConnectionCheck.kt`), the ID token reasons
  (`IdTokenValidator.kt`), the discovery reasons (`OidcMetadata.kt`), the route answers (`OidcEndpoints.kt`), the settings check lines (`OidcSettings.kt`)
  and the group sync lines (`ExternalGroupSync.kt`). Each row says whether the message is on screen or only in the log. Verify: test 2.1 passes.
- [ ] 4.3 `docs(gitbook)`: list the six new pages in `docs/gitbook/SUMMARY.md` under "Identity and access", and link the recipes from
  `docs/gitbook/how-to/configure-oidc-login.md`, `docs/gitbook/tutorials/set-up-users-teams-and-sso.md` and
  `docs/gitbook/reference/login-providers.md`. Verify: test 2.1 passes, and the summary shows each new page once.

## 5. Pull request

- [ ] 5.1 Open the pull request with the title `docs(gitbook): add recipes for keycloak, authentik, okta and google workspace login`. The description
  follows `.github/PULL_REQUEST_TEMPLATE.md`, tells the operator's story (a login that ends in `loginError=invalid_id_token` and the page that now
  explains it), carries the results of 1.2 and 1.3, states that nothing changes for existing installations, and carries no session link and no
  attribution line. Verify: `gh pr view --json title` shows the exact title and the description has no em or en dash.
