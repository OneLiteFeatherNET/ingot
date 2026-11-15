# Spec Delta

## Purpose

Step-by-step guides for signing in to Ingot with a given identity provider. The guides describe the behaviour of the login providers
on `main`, name the limits, and are kept true by a test.

## ADDED Requirements

### Requirement: A recipe exists for each supported identity provider

The documentation MUST contain one how-to page each for Keycloak, Authentik, Okta, Google Workspace, Microsoft Entra ID and GitHub, listed once in
`docs/gitbook/SUMMARY.md`. Each page MUST follow the Diataxis how-to form: one goal, numbered steps, a result to check.

#### Scenario: An operator looks for the provider by name

- **WHEN** an operator opens the summary under "Identity and access"
- **THEN** a page for each of the six providers is listed, and each opens without a redirect

### Requirement: A recipe states the values the operator must copy

Each recipe MUST state the exact redirect URI `<publicUrl>/api/auth/oidc/<id>/callback`, the scopes, the claims Ingot reads and what the provider
sends for each, and the Ingot settings as a table of JSON key and value. Every key in the table MUST be a property of the provider settings.

#### Scenario: A key is renamed in the code

- **WHEN** a key that a recipe table names is no longer a property of the provider settings
- **THEN** the guard test fails and names the page and the key

#### Scenario: The operator pastes the redirect URI

- **WHEN** an operator follows the Keycloak recipe with the id `keycloak` and the public address `https://repo.example.org`
- **THEN** the page shows `https://repo.example.org/api/auth/oidc/keycloak/callback` as the value to register

### Requirement: Recipes state what the provider cannot give

A recipe MUST state when the provider sends no groups in the ID token and what follows from it. The Google Workspace recipe MUST state that the
ID token carries no groups, that team synchronisation and admin groups therefore do not work for it, and that access is limited with the allowed
email domains and with manually managed teams.

#### Scenario: Google Workspace and groups

- **WHEN** an operator wants Google Workspace groups to become Ingot teams
- **THEN** the page says this is not possible with the current provider, and shows the allowed email domains and a manual team as the alternative

### Requirement: Group to team mapping and administrator groups have one guide

One how-to MUST describe the external reference forms `oidc:<id>:<group>` and `ldap:<group>`, what a login adds and removes, that manual memberships are
untouched, how `adminGroups` creates a synchronised team that holds the instance admin right, the last-admin warning and the audit event
`LAST_ADMIN_REVOKED_BY_PROVIDER`, and how to regain access. The recipes MUST link to it.

#### Scenario: Making a group the administrators

- **WHEN** an operator lists a group under `adminGroups` and a member signs in
- **THEN** the guide states that the team `oidc:<id>:<group>` exists with the instance admin right and that the member is an instance administrator

#### Scenario: The last administrator leaves the group

- **WHEN** the operator reads what happens after the last admin group member is removed at the provider
- **THEN** the guide names the warning, the audit event and the recovery guide

### Requirement: Troubleshooting uses the messages of the code

The troubleshooting guide MUST list each `loginError` code, each message of the connection tests, each HTTP answer of the login routes and each log line
of the settings check and the group sync that an operator can meet. Each message MUST be quoted as the code writes it and MUST say whether it is
shown on screen or only written to the server log.

#### Scenario: A message is reworded

- **WHEN** a message quoted in the troubleshooting guide no longer occurs in the login sources
- **THEN** the guard test fails and names the message

#### Scenario: The username claim is missing

- **WHEN** a login ends in `?loginError=invalid_id_token` because the username claim is empty
- **THEN** the guide names the claim setting to change, with `email` as the value for a provider that sends no `preferred_username`

### Requirement: The guides change no behaviour

This change MUST NOT alter a setting, a default, an environment variable, a route or a message. A behaviour that the guides name as a limit MUST be
fixed in its own change, which then updates the guide.

#### Scenario: Defaults after the change

- **WHEN** an existing installation is upgraded to a version that contains this change
- **THEN** no provider is enabled, no setting differs, and no operator action is needed
