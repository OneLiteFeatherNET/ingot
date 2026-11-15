# Proposal

## Why

An operator who wants people to sign in to Ingot with the company identity provider finds one generic OpenID Connect guide,
one for Microsoft Entra ID, one for GitHub and one for LDAP. If the provider is Keycloak, Authentik, Okta or Google Workspace,
the operator must translate the generic guide into the vocabulary of that provider alone: which redirect URI to paste, which
scope makes the groups appear, which claim carries the user name, why the login ends in `?loginError=invalid_id_token`. The
answers exist in the code, but they are spread over the reference page, the generic guide and the source. Two questions come up
every time and have no page of their own: how a provider group becomes an Ingot team, and how a group becomes the group of
administrators. A roadmap review of competing repository managers (item N12, first half) rates this gap as the cheapest fix with
a high weight: nothing in the product changes, only the documentation does.

The login providers themselves are already on `main` (change `add-external-login-providers`, implemented). This change writes
the step-by-step pages and nothing else.

## What Changes

- **Four new recipes**, each a how-to in the Diataxis form (a goal, numbered steps, a result to check): Keycloak, Authentik, Okta
  and Google Workspace. Each states the exact redirect URI, the scopes, the claims Ingot reads, the Ingot settings as a table with
  the JSON key names of `login.oidcProviders`, and a short troubleshooting section for that provider.
- **Two existing recipes brought to the same shape**: Microsoft Entra ID and GitHub get the same fixed section order and a link to the
  new troubleshooting page. Their content stays, and the notice that the steps were not checked against a live tenant stays until
  task 1.2 records a check.
- **One how-to for groups**: `map-provider-groups-to-teams.md` covers the group to team mapping (`oidc:<id>:<group>`, `ldap:<group>`),
  what the sync adds and removes, how a group becomes the group of administrators through `adminGroups`, the last-admin warning and the
  recovery path. The generic, Entra, GitHub and LDAP guides link to it instead of each repeating the part.
- **One troubleshooting how-to**: `troubleshoot-external-login.md`, built from the real strings of the code: the `loginError` codes, the
  messages of the connection test, the HTTP answers of the login routes, the log lines of the settings check and of the group sync. A
  guard test fails when a quoted message no longer exists in the source.
- **Navigation**: the new pages are listed in `docs/gitbook/SUMMARY.md` under "Identity and access", and the tutorial and the generic
  guide link to them.

Conventional Commits type and scope: `docs(gitbook)`. The guard test ships as `test(gitbook)` in the same pull request, one type per
commit. Pull request title: `docs(gitbook): add recipes for keycloak, authentik, okta and google workspace login`.

### Out of scope

- Any change to the login code, the settings, their defaults or the dashboard dialog. A behaviour that an operator would want and the
  product lacks (a Google groups claim, userinfo groups, an `email_verified` check) is named in the guide as a limit and left to its own
  change.
- RP-initiated and IdP-initiated logout, and the complete OpenAPI document with its diff gate. They are the second half of roadmap item N12.
- SAML 2.0, SCIM, and recipes for Auth0 and PingFederate. The generic guide covers any provider that follows discovery, and a recipe for
  another provider is a follow-up that reuses the section order fixed here.
- Legal or business questions about which edition of a competitor offers single sign on.
- The legacy `authentication.ldap` Basic authentication.

## Impact

- **Operators:** nothing changes. No default, setting, environment variable, manifest, volume or data changes, and nothing needs to be
  adopted. **BREAKING: no.**
- **Code:** only Markdown under `docs/gitbook/` and one Python test under `charts/ingot/tests`.
- **Dependencies on other changes:** none open. `add-external-login-providers` is implemented and merged, and the pages describe it as it
  is on `main`. `restructure-navigation` is on main (#76, #82). `add-repositories-admin-page` may move the dashboard labels (**Access**, **SSO**); the pages then change in that change.
- **Facts about the vendors:** the provider side of every recipe (menu names, defaults) comes from the vendor's documentation and from
  one local run for Keycloak and Authentik. Entra ID, Okta and Google Workspace need an account that the project does not have yet, see
  `design.md`.

## Capabilities

### New Capabilities

- `login-provider-guides`: the step-by-step guides for the supported login providers, the fixed content of a recipe, the group and
  administrator mapping guide, the troubleshooting guide and the check that keeps them true.

### Modified Capabilities

- None. `external-login-providers` keeps its requirements, and this change documents it.
