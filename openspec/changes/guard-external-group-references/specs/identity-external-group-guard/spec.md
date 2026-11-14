# Spec Delta

## Purpose

Linking a team to an identity provider group is not guarded by a dedicated right; a team administrator can widen the rights of IdP group members. Fixed through a security advisory.

## ADDED Requirements

### Requirement: Linking a team to a provider group needs the IdP right

Creating a team with an external reference, changing a team's external reference and clearing it SHALL require the caller to hold the node `ingot.admin.idp`, or `ingot.admin.*`, in `enforce` mode. `enforce` is the default.

#### Scenario: A teams administrator cannot link a team in enforce mode

- **WHEN** the mode is `enforce` and a caller holding only `ingot.admin.teams` sets `externalRef` on a team
- **THEN** the answer is 403 and the team is unchanged

#### Scenario: The IdP right allows the link in enforce mode

- **WHEN** the mode is `enforce` and a caller holding `ingot.admin.idp` sets `externalRef`
- **THEN** the team is linked and the answer is the one of today

### Requirement: Warn mode is a temporary fallback and changes no response

When the mode is `warn`, the write SHALL proceed exactly as before. A write by a caller without the IdP right SHALL record one `EXTERNAL_REF_UNGUARDED` audit event naming the caller, the team and the reference, and SHALL log a WARN line.

#### Scenario: An unguarded link is recorded and allowed

- **WHEN** the mode is `warn` and a caller holding only `ingot.admin.teams` sets `externalRef`
- **THEN** the write succeeds with the usual answer and one `EXTERNAL_REF_UNGUARDED` event is recorded

### Requirement: Existing references are kept

This change SHALL NOT remove or rewrite an external reference that already exists. The guard SHALL apply to later writes only.

#### Scenario: A link made before the change keeps working

- **WHEN** a team linked before this change is synchronized at login
- **THEN** its memberships and admin right follow the provider as before

### Requirement: The IdP right is an admin area node

The node `ingot.admin.idp` SHALL be accepted by the node grammar as an admin area. `ingot.admin.*` SHALL cover it. `ingot.admin.teams` SHALL NOT cover it.

#### Scenario: The area is covered only by the wildcard

- **WHEN** a principal holds `ingot.admin.teams` and a second holds `ingot.admin.*`
- **THEN** only the second holds `ingot.admin.idp`

### Requirement: The switch names follow the drop-in rule

The mode SHALL be read from `INGOT_IDENTITY_IDP_GUARD` with the values `off`, `warn` and `enforce`, falling back to `REPOSILITE_IDENTITY_IDP_GUARD`. The default SHALL be `enforce`. An operator MAY set `warn` as a temporary fallback while granting `ingot.admin.idp`.

#### Scenario: The default needs no configuration

- **WHEN** neither variable is set
- **THEN** the mode is `enforce`

#### Scenario: The fallback restores today's responses

- **WHEN** `INGOT_IDENTITY_IDP_GUARD=warn` is set and a caller holding only `ingot.admin.teams` sets `externalRef`
- **THEN** the write succeeds with the usual answer and one `EXTERNAL_REF_UNGUARDED` event is recorded
