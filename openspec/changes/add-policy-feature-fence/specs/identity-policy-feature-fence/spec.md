# Spec Delta

## Purpose

A table of policy features that a build records and checks at startup, so that an older build refuses a database written by a newer build instead of silently ignoring its policy rows.

## ADDED Requirements

### Requirement: Policy features are recorded in a table

The identity database SHALL contain a table `policy_feature` with one row per enabled policy feature, holding the feature name and the time it was enabled. The table SHALL be created on every supported database. A fresh installation SHALL have no rows.

#### Scenario: A fresh installation starts with an empty table

- **WHEN** Ingot starts on an empty identity database
- **THEN** the table `policy_feature` exists and holds no rows

#### Scenario: The table exists on every supported database

- **WHEN** Ingot starts on SQLite, H2, MariaDB, MySQL or PostgreSQL
- **THEN** the table `policy_feature` is created

### Requirement: Unknown features refuse startup

On startup, Ingot SHALL compare every feature name in `policy_feature` with the names this build knows. If a name is unknown, Ingot SHALL NOT complete startup, and SHALL log an error that names each unknown feature and the running version.

#### Scenario: A newer database is refused

- **WHEN** the database holds a feature `restrictions` that this build does not know
- **THEN** startup stops, and the log names `restrictions` and the version of the running build

#### Scenario: Known features start normally

- **WHEN** every feature in the table is known to this build
- **THEN** startup completes as it does without the table

### Requirement: The check runs before identity migrations

The feature check SHALL run before the legacy access token migration and before the permission node migration, so that a refused database is never written by this build.

#### Scenario: A refused database is not migrated

- **WHEN** the feature check refuses startup
- **THEN** neither identity migration runs, and their markers are unchanged

### Requirement: The change adds no feature

This change SHALL NOT register any policy feature. The known set SHALL be empty in this change.

#### Scenario: Existing installations are unaffected

- **WHEN** an installation created by an earlier release starts with this release
- **THEN** the feature check passes and no configuration is needed
