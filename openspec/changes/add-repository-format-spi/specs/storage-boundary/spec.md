# Spec Delta

## Purpose

The storage layer moves bytes by path and knows no repository format.

## ADDED Requirements

### Requirement: Storage imports no Maven behaviour

No class under `com.reposilite.storage` SHALL depend on a class under `com.reposilite.maven` other than
`RepositoryVisibility`, which stays where it is for binary compatibility. An architecture test SHALL enforce this.

#### Scenario: Dependency rule

- **WHEN** the architecture test inspects the compiled backend classes
- **THEN** the only dependency from `com.reposilite.storage` into `com.reposilite.maven` is `RepositoryVisibility`

### Requirement: Related cache keys come from the format

The S3 head cache SHALL drop, after a put or a delete, the keys that a related-key rule names for the written key. A
repository's S3 provider SHALL use the rule of the repository's format. An S3 client created without a rule SHALL use the
rule that applies today (the `maven-metadata.xml` entries two levels up).

#### Scenario: Maven repository on S3

- **WHEN** a client deploys `com/example/a/1.0/a-1.0.jar` to a `maven2` repository on S3 whose head cache holds
  `com/example/a/maven-metadata.xml`
- **THEN** that entry is dropped from the head cache, as before

#### Scenario: Format without related keys

- **WHEN** an S3 provider is given a rule that returns no keys and a file is written
- **THEN** only the written key is dropped from the head cache

### Requirement: Storage provider SPI unchanged

`StorageProvider`, `StorageProviderFactory` and their settings SHALL keep their public signatures, and `VersionComparator`
SHALL keep ordering storage listings as it does today.

#### Scenario: Directory listing order

- **WHEN** a directory holding `1.10`, `1.2` and `1.9` is listed from the filesystem provider
- **THEN** the order is `1.2`, `1.9`, `1.10`, as before
