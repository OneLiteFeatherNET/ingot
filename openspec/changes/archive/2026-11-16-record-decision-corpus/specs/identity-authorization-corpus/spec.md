# Spec Delta

## Purpose

A golden matrix of authorization decisions, recorded from the current code, that every later authorization change must keep equal unless it names the rows it changes.

## ADDED Requirements

### Requirement: The corpus covers every decision entry point

The corpus SHALL record the outcome of each decision function the authorization layer exposes: `allows`, `decide`, `decidePath`, `isInstanceAdmin`, `allowsAdminArea`, `canSee`, `canListHidden`, `hasArtifactReadRule` and `readableArtifactSubtrees`. Each row SHALL name its subject fixture, its request or node, its action and its expected outcome.

#### Scenario: Every entry point has rows

- **WHEN** the corpus is evaluated
- **THEN** at least one row exists for each decision function listed above

#### Scenario: A removed row fails the suite

- **WHEN** a row of the corpus is deleted
- **THEN** the harness test fails and its message names the missing row identifier

### Requirement: Rows state the current behaviour

The corpus SHALL record what the code decides today, including surprising results. A row SHALL NOT change in a change that does not declare a behaviour change. A row whose outcome a later change alters SHALL carry a `changesIn` note that names that change.

#### Scenario: A known surprise is recorded as it is

- **WHEN** a personal deny of `ingot.repository.*.read` coexists with a team allow of `ingot.repository.releases.read`
- **THEN** the row expects the personal deny to decide, and the row carries a note that the precedence is a known surprise

#### Scenario: A marked row is the only one that may change

- **WHEN** a later change alters a row that names it in `changesIn`
- **THEN** the change's proposal lists that row, and no other row changes

### Requirement: Path variants are recorded raw and normalized

For encoded characters, dot segments, doubled slashes and mixed case, the corpus SHALL record both the raw string an upload passes to the authorization check and the normalized string a read passes to it.

#### Scenario: An encoded slash has two rows

- **WHEN** a path contains `%2F`
- **THEN** the corpus holds one row for the raw form and one row for the normalized form, each with its expected outcome

### Requirement: The corpus runs without a server, database or clock

The corpus SHALL be unit tests. Each test SHALL build its own fixture. The tests SHALL NOT use a database, the network, sleeps, system time or shared mutable state, and SHALL pass in any order.

#### Scenario: Order does not change the result

- **WHEN** the corpus rows are evaluated in reverse order
- **THEN** every row has the same outcome as in forward order

### Requirement: The change alters no production behaviour

This change SHALL NOT modify any file under `reposilite-backend/src/main`. The corpus SHALL pass on the commit that precedes it.

#### Scenario: Production sources are untouched

- **WHEN** the diff of the change is inspected
- **THEN** it contains test sources and fixtures only
