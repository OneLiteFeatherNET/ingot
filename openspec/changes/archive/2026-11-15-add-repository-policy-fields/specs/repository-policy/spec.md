# Spec Delta

## Purpose

An operator can say what a repository is for, release builds or snapshot builds, and freeze one repository on its own, and
the dashboard shows each repository's type and write behaviour in plain words. Nothing changes for a repository that sets none
of the new fields.

## ADDED Requirements

### Requirement: Version policy field

A repository in the `maven` settings SHALL accept an optional `versionPolicy` of `RELEASE`, `SNAPSHOT` or `MIXED`. An absent
value SHALL behave as `MIXED`, SHALL NOT be written back when absent, and SHALL NOT change any deploy decision.

#### Scenario: Absent policy behaves as before

- **WHEN** a repository has no `versionPolicy` and a `-SNAPSHOT` file and a release file are deployed
- **THEN** both deploys are accepted as before

#### Scenario: Absent key is not written back

- **WHEN** a repository without `versionPolicy` is saved after its description is edited
- **THEN** the stored document contains no `versionPolicy` key for it

### Requirement: Release policy refuses snapshot deploys

A deploy into a hosted or untyped repository with `versionPolicy` `RELEASE` SHALL be refused with `400` when the parent
directory of the path ends with `-SNAPSHOT`, and nothing SHALL be stored.

#### Scenario: Snapshot into a release repository

- **WHEN** a deploy of `com/acme/app/1.0-SNAPSHOT/app-1.0-20260101.jar` targets a `RELEASE` repository
- **THEN** the answer is `400` naming the repository and the policy, and the file does not exist afterwards

#### Scenario: Release into a release repository

- **WHEN** a deploy of `com/acme/app/1.0/app-1.0.jar` targets a `RELEASE` repository
- **THEN** the deploy is accepted

### Requirement: Snapshot policy refuses release deploys

A deploy into a hosted or untyped repository with `versionPolicy` `SNAPSHOT` SHALL be refused with `400` when the parent
directory of the path does not end with `-SNAPSHOT`, except for `maven-metadata.xml` and its checksums.

#### Scenario: Release into a snapshot repository

- **WHEN** a deploy of `com/acme/app/1.0/app-1.0.jar` targets a `SNAPSHOT` repository
- **THEN** the answer is `400` naming the repository and the policy

#### Scenario: Artifact metadata is exempt

- **WHEN** a deploy of `com/acme/app/maven-metadata.xml` targets a `SNAPSHOT` repository
- **THEN** the deploy is accepted

### Requirement: Policy scope is deploys only

The version policy SHALL NOT affect reads, deletes, mirror cache fills, metadata regeneration or cleanup runs.

#### Scenario: Existing content stays reachable

- **WHEN** a repository holds a snapshot file and its policy is set to `RELEASE`
- **THEN** the file can still be read and deleted

### Requirement: Per-repository read-only flag

A repository with `readOnly` true SHALL refuse deploys and deletes with `405`, SHALL keep serving reads and mirror cache
fills, and SHALL be skipped by scheduled cleanup runs. The global read-only mode SHALL stay unchanged and SHALL answer `503`
before this check.

#### Scenario: Deploy to a read-only repository

- **WHEN** an authorised caller deploys to a repository with `readOnly` true
- **THEN** the answer is `405` naming the repository and nothing is stored

#### Scenario: Cleanup skips a read-only repository

- **WHEN** a due cleanup run covers a repository with `readOnly` true
- **THEN** no file is deleted there and the run reports the repository as skipped

#### Scenario: Global mode wins

- **WHEN** the cluster-wide read-only mode is on and a deploy targets a repository with `readOnly` true
- **THEN** the answer is `503`

### Requirement: Validation applies only on opt-in

A settings update SHALL be rejected with the existing `400` rule list when `versionPolicy` is set on a declared GROUP or
PROXY, or `readOnly` is set on a declared PROXY or GROUP. A repository that sets neither field SHALL be validated exactly as
before.

#### Scenario: Policy on a group

- **WHEN** a settings update sets `versionPolicy` on a repository of type GROUP
- **THEN** the update is rejected and the message names the repository and the key

#### Scenario: Existing document still loads

- **WHEN** a settings document written by an earlier version is loaded
- **THEN** it validates and no repository gains a key

### Requirement: Derived write policy

The repository list API and the dashboard SHALL show a write policy of `ALLOW`, `ALLOW_ONCE` or `READ_ONLY` for hosted and
untyped repositories, derived from `redeployment` and `readOnly` and stored nowhere.

#### Scenario: Derivation

- **WHEN** repositories have (`redeployment` true), (`redeployment` false) and (`readOnly` true)
- **THEN** their write policies are `ALLOW`, `ALLOW_ONCE` and `READ_ONLY`

#### Scenario: Form writes the real keys

- **WHEN** an operator chooses `ALLOW_ONCE` in the settings form
- **THEN** the saved repository has `redeployment` false and no `readOnly` key, and no key named `writePolicy` exists

### Requirement: Declare type

For a repository without a declared type whose derived type is hosted, proxy or group, the dashboard SHALL offer an action
that writes `type` with the derived value and changes no other field. For a derived `mixed` repository it SHALL show a warning
and a suggestion to split, and offer no one-click action.

#### Scenario: One click declares the derived type

- **WHEN** an operator uses the action on an untyped repository whose derived type is group
- **THEN** the saved repository has `type` GROUP and every other field is unchanged

#### Scenario: Server refusal is shown

- **WHEN** the declared type would break a type rule
- **THEN** the dialog lists the violated rules and nothing is saved
