# Spec Delta

## Purpose

A record of authorization decisions for refused requests, writes and refused admin operations, so that an incident review can name the credential and the rule behind each one. Allowed reads are not recorded.

## ADDED Requirements

### Requirement: Denials, writes and refused admin operations are recorded

Ingot SHALL record one decision record for each denied check, each write decision (allowed or refused) and each refused admin operation. Ingot SHALL NOT record an allowed read.

#### Scenario: A denied read is recorded

- **WHEN** a token without rights reads an artifact
- **THEN** one record with the outcome Deny is written

#### Scenario: An allowed read is not recorded

- **WHEN** a token with rights reads an artifact
- **THEN** no record is written

#### Scenario: An allowed write is recorded

- **WHEN** a token with deploy rights uploads an artifact
- **THEN** one record with the outcome Allow is written

### Requirement: A record holds the decision context

A record SHALL hold a decision identifier, the time, the principal, the credential identifier, the action, the canonical resource, the outcome and the stage that decided. It SHALL hold the matched rule identifiers and the policy revision when they are known, and the raw resource only when it differs from the canonical one. A record SHALL NOT hold a token secret.

#### Scenario: A record names the rule that decided

- **WHEN** a denial is decided by a node `ingot.repository.releases.deploy` with a deny
- **THEN** the record names that node text among its matched rule identifiers

#### Scenario: A record never holds a secret

- **WHEN** a record is written for a request that carried a token
- **THEN** the record holds the token name and no part of the secret

### Requirement: Asynchronous recording never delays or changes a decision

In `async` mode, a record SHALL be queued and written by a background writer. A full queue SHALL drop the record and increment a counter, and the request SHALL be answered without waiting for the writer.

#### Scenario: A full queue drops and counts

- **WHEN** the queue is full and a denial is recorded
- **THEN** the record is dropped, the counter increases by one, and the answer is the same denial as before

### Requirement: Synchronous recording fails closed

In `sync` mode, a record for a denial or a write SHALL be stored before the answer is sent. If the record cannot be stored, Ingot SHALL refuse the request with 503 and SHALL NOT apply the decision.

#### Scenario: A failed store refuses the write

- **WHEN** the mode is `sync` and the record of an allowed write cannot be stored
- **THEN** the answer is 503 and the file is not written

### Requirement: The mode follows the drop-in rule

The mode SHALL be read from `INGOT_DECISION_LOG` with the values `off`, `async` and `sync`, falling back to `REPOSILITE_DECISION_LOG`. The default SHALL be `async`.

#### Scenario: The default records without configuration

- **WHEN** neither variable is set
- **THEN** the mode is `async`, and denials and writes are recorded
