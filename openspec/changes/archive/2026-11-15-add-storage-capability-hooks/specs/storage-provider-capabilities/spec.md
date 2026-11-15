# Spec Delta

## Purpose

Core decides storage behaviour by what a provider or its settings declare, not by its concrete class. A provider from a plugin
gets the same cluster binding, namespace check, usage report, readiness and support entry as fs and s3, and the output for fs and
s3 does not change.

## ADDED Requirements

### Requirement: Behaviour is chosen by capability interfaces

Core SHALL decide storage behaviour by testing for a capability interface in `net.onelitefeather.ingot.storage.capability`, and
SHALL NOT test for a concrete provider or settings class. Every capability interface SHALL carry the opt-in marker
`ExperimentalIngotStorageApi`. A provider or settings type that does not implement a capability SHALL keep the default behaviour
of that capability.

#### Scenario: Plugin provider receives the cluster binding

- **WHEN** a repository's provider implements `ClusterCacheAware` and the cluster bus opens
- **THEN** core binds that provider with the maximum age, whatever its class

#### Scenario: Provider without the capability

- **WHEN** a provider does not implement `ClusterCacheAware`
- **THEN** core does not bind it and the repository keeps the local cache bound it had before

### Requirement: Cluster-bound head cache keeps its maximum age

A provider that implements `ClusterCacheAware` SHALL accept a maximum age of answers from the cluster bus. The default maximum
age SHALL be 300 seconds. The `invalidate` callback SHALL drop the keys it names and the related keys of the repository format.

#### Scenario: Default maximum age

- **WHEN** the S3 provider is bound to the cluster bus with the default maximum age
- **THEN** an answer stored before the bind is dropped, and an answer stored after it expires 300 seconds after it was stored, as
  judged by an injected ticker

#### Scenario: Invalidation drops related keys

- **WHEN** `invalidate` receives a key whose related keys are defined by the format
- **THEN** the key and each related key are removed from the cache

### Requirement: Shared namespaces must not overlap

Core SHALL refuse to load two repositories whose settings implement `SharedNamespaceAware` and whose namespace keys overlap.
Core SHALL report the refused repository with the message the settings return from `overlapMessage()`. A setting whose namespace key
is null SHALL NOT take part in the check.

#### Scenario: Equal namespaces

- **WHEN** two S3 repositories have the same endpoint, the same bucket and the same resolved key prefix
- **THEN** both are refused with the S3 message that names the shared bucket and the `prefix` and `sharedBucket` settings

#### Scenario: Nested namespaces

- **WHEN** one S3 repository's resolved prefix is a parent of another's in the same bucket
- **THEN** both are refused

#### Scenario: Distinct namespaces

- **WHEN** two S3 repositories share a bucket but resolve to sibling prefixes
- **THEN** both are loaded

#### Scenario: Blank bucket

- **WHEN** an S3 repository has a blank bucket name
- **THEN** its namespace key is null and it takes no part in the check

### Requirement: Usage snapshot reports the real storage type

The usage snapshot SHALL report the configured type of each repository. A repository whose settings are not known to this replica
SHALL report `unknown`, and SHALL NOT report `fs`. A figure received from another replica SHALL carry the type it was measured with.

#### Scenario: Plugin type

- **WHEN** a repository is configured with a plugin storage type `example`
- **THEN** its usage entry reports `example` as its storage type

#### Scenario: Unconfigured repository

- **WHEN** a received figure names a repository that this replica does not configure
- **THEN** its usage entry reports `unknown` as its storage type

#### Scenario: Figure from an older replica

- **WHEN** a usage event arrives without a `storageType` for a figure
- **THEN** the figure is read with the storage type `unknown`

### Requirement: Quota is reported for providers that support it

A quota SHALL be reported only for a provider whose `UsageCapability` declares `supportsQuota` and whose settings implement
`QuotaSettings`. A percentage quota SHALL be measured against the provider's `totalCapacityBytes()`, and it SHALL be unknown when that
value is null. This change SHALL NOT enforce a quota for any provider other than the filesystem provider.

#### Scenario: Filesystem percentage quota

- **WHEN** a filesystem repository is configured with the quota `50%`
- **THEN** the quota bytes are half of the total capacity of its root

#### Scenario: S3 repository has no quota

- **WHEN** an S3 repository is reported
- **THEN** its quota is null and its usage is not reported as a figure

#### Scenario: Plugin without quota support

- **WHEN** a plugin provider does not declare `supportsQuota`
- **THEN** its quota is null, even when its settings carry a quota value

### Requirement: Readiness distinguishes a failed backend from an empty one

A provider that implements `ReadinessProbe` SHALL answer `Ok` when its root answers a listing, including an empty listing, `NotFoundOk`
when the backend answers that the root does not exist, and `Failed` with a reason otherwise. A provider without the capability SHALL be probed with the
listing of its root, and its answer SHALL be read in the same way.

#### Scenario: Empty root

- **WHEN** the root of a repository answers with no entries
- **THEN** the probe answers `Ok`

#### Scenario: Missing root

- **WHEN** the backend answers that the root does not exist
- **THEN** the probe answers `NotFoundOk`, and the repository counts as answered

#### Scenario: Failed listing

- **WHEN** the listing of the root fails with an error other than not found
- **THEN** the probe answers `Failed` with the error message as its reason, and the repository counts as not answered

### Requirement: Support bundle entries describe the storage without secrets

A settings type that implements `SupportDetails` SHALL return only non-sensitive key and value pairs. The support bundle SHALL carry
those pairs as the `details` of each repository storage entry. The `region` field SHALL keep its meaning for the S3 provider and
SHALL be null for every other provider.

#### Scenario: S3 entry

- **WHEN** an S3 repository has the region `eu-central-1`
- **THEN** its entry has `region` `eu-central-1` and `details` containing the same region

#### Scenario: Filesystem entry

- **WHEN** a filesystem repository is listed
- **THEN** its entry has a null `region` and empty `details`

#### Scenario: Redaction still applies

- **WHEN** a plugin's `describe()` returns a value that matches a known secret
- **THEN** the support bundle redacts that value

### Requirement: Filesystem and S3 behaviour is unchanged

The behaviour of the filesystem and S3 providers SHALL be identical before and after this change for every output named in this
capability: the shared-bucket conflict messages, the usage snapshot, the quota, the cluster binding, the readiness outcome and the
support bundle entries. The only added fields are `details` and the storage type of a figure.

#### Scenario: Parity of conflict messages

- **WHEN** the existing S3 conflict cases are checked after this change
- **THEN** each message is byte-identical to the message before this change

#### Scenario: Parity of the usage snapshot

- **WHEN** a filesystem repository with a quota and an S3 repository are measured
- **THEN** each entry carries the same used bytes, file count, quota and quota bytes as before, and the storage type is `fs` and `s3`

### Requirement: Old name of the related key interface keeps working

The interface `com.reposilite.storage.s3.RelatedKeyAware` SHALL remain on the JVM as a deprecated interface with no member of its
own, and it SHALL extend `net.onelitefeather.ingot.storage.capability.RelatedKeyAware`. A provider that implements the old name SHALL
receive the related keys of the repository format.

#### Scenario: Compiled provider with the old name

- **WHEN** a provider implements `com.reposilite.storage.s3.RelatedKeyAware`
- **THEN** the repository format gives it its related keys

#### Scenario: Plugin with the new name

- **WHEN** a provider implements `net.onelitefeather.ingot.storage.capability.RelatedKeyAware`
- **THEN** the repository format gives it its related keys
