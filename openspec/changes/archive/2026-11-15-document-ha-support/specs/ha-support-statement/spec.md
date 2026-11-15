# Spec Delta

## Purpose

An operator can read, on one page, which high availability setup Ingot supports, what it does not support, how an upgrade may
proceed, how failures look, and what has been measured.

## ADDED Requirements

### Requirement: One page states the supported topology

The documentation SHALL contain one page that names the supported topology: PostgreSQL or MariaDB as the shared database, S3 or
a shared read-write volume as storage, several replicas of one release line behind one Service, one region.

#### Scenario: Operator looks for the supported setup

- **WHEN** an operator opens the page from the documentation summary
- **THEN** the page lists the database, the storage, the replica count, the region and the optional cluster bus that are supported

### Requirement: The page lists what is not supported

The page SHALL list the setups that are not supported, each with the reason: SQLite or H2 with several replicas, MySQL, several
regions, an unshared filesystem volume, non-adjacent release lines.

#### Scenario: Operator checks an unsupported setup

- **WHEN** an operator looks for SQLite with two replicas
- **THEN** the page says it is not supported and says why a file database cannot be shared

### Requirement: Every claim names its evidence

Every statement in the support table SHALL name the test or CI job that backs it and its state, and a statement without a test
SHALL be marked "not tested".

#### Scenario: A row without a test

- **WHEN** a row has no test that runs it
- **THEN** its state reads "not tested"

### Requirement: The rolling upgrade rule is stated for operators

The page SHALL state the rule for running a release and the next release together, with the order of the upgrade and the known
limits, and SHALL say "tested" only for the parts that the mixed-version test covers.

#### Scenario: Operator plans a rolling upgrade

- **WHEN** an operator reads the rule
- **THEN** it names the order of the upgrade, the forbidden window actions and the evidence for each

### Requirement: Scale rows carry measurements or say none exist

The scale table SHALL give a value only with its date, release, hardware and workload, and SHALL say "not measured" otherwise.

#### Scenario: No benchmark exists

- **WHEN** a dimension has no recorded run
- **THEN** its cell reads "not measured" and no target is shown

### Requirement: Older pages no longer contradict the statement

The Kubernetes guide, the multi-replica page, the upgrade page and the upgrade changes reference SHALL link to the statement and
SHALL NOT claim that replicas are unsupported or that a rolling upgrade is untested once the statement says otherwise.

#### Scenario: Reader of the upgrade page

- **WHEN** a reader opens the upgrade page
- **THEN** the hint about rolling upgrades links to the statement instead of saying the upgrade is untested
