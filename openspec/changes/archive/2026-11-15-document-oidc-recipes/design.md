# Design

## What already exists on main

The generic guide (`docs/gitbook/how-to/configure-oidc-login.md`), the Entra ID guide (`configure-entra-id-login.md`), the GitHub guide
(`configure-github-login.md`), the LDAP guide (`configure-ldap-login.md`) and the reference (`docs/gitbook/reference/login-providers.md`)
are on `main`. They already hold the redirect URI rule, the claims table, the `loginError` code table and the admin group warning. This
change does not rewrite them. It adds the four recipes that are missing, pulls the shared group and administrator part into one page, and
adds the troubleshooting page that the code tables alone do not give: the messages behind the codes.

## Facts every recipe is built on (checked in the code)

- Redirect URI: `<publicUrl>/api/auth/oidc/<id>/callback`, with the scheme from `X-Forwarded-Proto` when `login.publicUrl` is empty
  (`reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/identity/login/oidc/OidcEndpoints.kt`, the callback route, and `PublicUrl.kt`).
- Authorization code flow with PKCE (S256), state and nonce (`OidcLoginService.kt`, `begin` and `authorizeUrl`). The client secret goes in an
  HTTP Basic header, and an empty secret makes it a public client that sends the client id in the form (`OidcLoginService.kt`, token request).
  A recipe must therefore register a confidential client, or a public client with PKCE, and must not use client authentication by signed JWT.
- All claims are read from the **ID token** only (`OidcLoginService.kt`, `oidcIdentity`). The userinfo endpoint is discovered but not used for
  claims. A provider that puts groups only in the access token or in userinfo gives no group sync. This drives the Okta and Keycloak steps.
- Groups claim: a list of strings or one string, anything else gives no groups (`OidcLoginService.kt`, `groupsOf`). Group matching is exact
  and case sensitive on the text of the claim.
- Without a value in the username claim and with `autoCreateUsers` on, the login fails with `invalid_id_token` (`OidcLoginService.kt`, after
  `validateIdToken`). Google sends no `preferred_username`, so its recipe sets `usernameClaim` to `email`. A user name may contain `@` but not a
  colon, a space or a control character (`LoginNames.kt`).
- Signature algorithms accepted: RS, PS and ES families. HMAC and `none` are refused (`IdTokenValidator.kt`, `ASYMMETRIC_ALGORITHMS`). A client
  at the provider configured for HS256 ID tokens fails, and the recipes say to use RS256.
- `email_verified` is never read, so the allowed email domains rule trusts the `email` claim as the provider sends it (grep of the login package
  finds no use). The Google and Keycloak recipes state this and say where the provider controls it.
- Discovery issuer must equal `issuerUrl` ignoring a trailing slash (`OidcMetadata.kt`, `fetchDiscovery`). This matters for Authentik, whose issuer
  ends in a slash, and for Okta, whose org and custom authorization servers have different issuers.
- The test endpoint reports the messages quoted in the troubleshooting page (`OidcConnectionCheck.kt`, `LdapConnectionCheck.kt`). The ID token
  messages (`IdTokenValidator.kt`) and the settings check messages (`OidcSettings.kt`) are written to the server log, and the redirect carries only the code.
  The troubleshooting page therefore says which message is on screen and which one needs the log.

## The shape of a recipe

Every provider page has the same sections in the same order, so an operator who read one can find a value in another:

1. Goal and what you need (accounts, role at the provider, the public address).
2. At the provider: create the client, the exact redirect URI, the scopes, the groups claim.
3. In Ingot: the settings as a table (JSON key, value for this provider), and the dialog fields that write them.
4. Claims Ingot reads, and what the provider sends for each.
5. Groups: what the provider sends and the exact `external reference` form, then a link to the group guide.
6. Check the result.
7. Troubleshooting specific to this provider, then a link to the general troubleshooting page.

A recipe is a how-to: it gives the steps for one goal and links to the reference and the explanation instead of teaching. The explanation of why
Ingot never links by name stays in `sessions-and-login.md`.

## Verification of vendor facts

Menu names and defaults at the provider change without notice and Ingot cannot test them in CI. Three levels, stated on each page:

- Keycloak and Authentik run as containers, so the recipes are followed once against a local instance (task 1.1 and 1.2) and the exact
  screens and claim output recorded in the pull request description.
- Entra ID, Okta and Google Workspace need a tenant or a developer account. If none exists when the pull request is opened, the page carries the
  same hint that the Entra page has today ("checked against the vendor documentation, not against a live account") and names the date and the
  documentation page it was checked against. A tenant is an open question for the maintainer, and the change does not wait for it.

## Guard test

`charts/ingot/tests/test_login_guides.py` (Python `unittest`, like `test_guides.py`, repository files only, no network, no clock) checks:

- every new page is listed once in `SUMMARY.md`;
- every JSON key in a settings table of a recipe exists as a property of `OidcProviderSettings` or `LdapLoginSettings`;
- every message in backticks in the "Message" column of the troubleshooting page occurs as a string in the Kotlin sources of the login package.

The third check is the reason the page can be trusted after a refactor. A message that is reworded in the code fails the test, and whoever changes
the message changes the page in the same pull request.

## Alternatives considered

- **One long page with a tab per provider.** Rejected: GitBook tabs hide the other providers from search and from links, and the Entra and GitHub
  pages already exist as separate pages.
- **Generate the pages from the settings annotations.** Rejected for the steps, which are provider prose. The reference page that lists the keys is
  the place for generation, and it is out of scope here.
- **Recipes for every provider in the icon list (GitLab, Gitea, Auth0).** Deferred: the generic guide covers them, and each recipe needs a way to check it.
