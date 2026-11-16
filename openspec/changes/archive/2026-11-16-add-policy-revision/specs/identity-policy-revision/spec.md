# Spec Delta

## Purpose

A revision counter for identity policy, advanced by every identity write and read by every replica, so that a revocation reaches the other replicas within a bounded interval and a write never decides on a stale view.

## ADDED Requirements

### Requirement: The revision advances with every identity write

Every identity write SHALL advance the policy revision by one in the same transaction as the write. A write that is rolled back SHALL NOT advance it.

#### Scenario: A write advances the revision once

- **WHEN** a team membership is changed
- **THEN** the revision increases by exactly one

#### Scenario: A rolled-back write leaves the revision alone

- **WHEN** a write fails inside its transaction
- **THEN** the revision is unchanged

### Requirement: Replicas refresh the revision and drop older credentials

Each replica SHALL read the revision on the configured interval and SHALL drop every cached authentication whose revision is older than the value read. The default interval SHALL be 2 seconds.

#### Scenario: A revocation reaches another replica within one interval

- **WHEN** replica A revokes a token and replica B refreshes once
- **THEN** replica B refuses the token on its next request

#### Scenario: The existing bound still applies

- **WHEN** a cached authentication is older than one minute
- **THEN** it is verified again, whatever its revision

### Requirement: Identity writes read the revision before deciding

An identity write SHALL read the revision before it decides, and SHALL reload its inputs when the value differs from the last value the replica saw.

#### Scenario: A stale replica does not decide on old rights

- **WHEN** replica B holds an old view of the admins and performs a write
- **THEN** the write reloads the admins before it applies the last-admin guard

### Requirement: The refresh interval follows the drop-in rule

The interval SHALL be read from `INGOT_AUTHORIZATION_REFRESH`, with `REPOSILITE_AUTHORIZATION_REFRESH` as the fallback. The default SHALL apply when neither is set.

#### Scenario: A single node needs no configuration

- **WHEN** neither variable is set on a single node
- **THEN** the node starts and behaves as before this change, with the default interval
