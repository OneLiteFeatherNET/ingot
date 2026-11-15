# Spec Delta

## Purpose

An operator can mount PEM files into the server image and point one variable at the directory, and the server then also trusts
those certificate authorities, with no change to the image and no loss of the public ones.

## ADDED Requirements

### Requirement: Unset variable leaves the start unchanged

When neither `INGOT_EXTRA_CA_CERTS` nor `REPOSILITE_EXTRA_CA_CERTS` is set or both are empty, the entrypoint SHALL NOT create a
trust store copy, SHALL NOT run `keytool` and SHALL NOT add a trust store option to the JVM command line.

#### Scenario: Default start

- **WHEN** the image starts without either variable
- **THEN** the JVM command line has no `javax.net.ssl.trustStore` option and the server starts as before

### Requirement: Variable names a directory of PEM files

The variable MUST name a directory. The entrypoint SHALL read every regular file in it, following symbolic links to files and
skipping names that start with a dot, and SHALL import each certificate of each file as a trust anchor. A file with several
certificates SHALL contribute all of them. `INGOT_EXTRA_CA_CERTS` wins over `REPOSILITE_EXTRA_CA_CERTS` when both are set.

#### Scenario: Chain in one file

- **WHEN** the directory holds one file with two certificates
- **THEN** both certificates are trusted by the server

#### Scenario: Kubernetes volume layout

- **WHEN** the directory holds `..data` and a symbolic link to a PEM file as a ConfigMap mount does
- **THEN** the linked certificate is imported once and the dot entries are ignored

#### Scenario: Fallback name

- **WHEN** only `REPOSILITE_EXTRA_CA_CERTS` is set
- **THEN** the certificates in that directory are trusted

### Requirement: Public authorities stay trusted

The imported certificates SHALL be added to a copy of the JDK trust store, and the JVM SHALL be pointed at the copy with
`-Djavax.net.ssl.trustStore`. The entrypoint MUST NOT use the mounted directory as the trust store itself.

#### Scenario: Public and private

- **WHEN** the bundle holds one private CA
- **THEN** a server certificate from that CA and a server certificate from a public CA are both accepted

### Requirement: Both start paths work with a writable copy

The copy SHALL be written under `${TMPDIR:-/tmp}` in a directory created for this start, readable by the service account, and
nothing else SHALL be written outside that directory. The step SHALL run for the root start and for the start as a non-root
user, and the entrypoint path, the user and the exec of the server SHALL stay as they are.

#### Scenario: Root start with PUID

- **WHEN** the image starts as root with `PUID=1234` and `PGID=1234` and a bundle
- **THEN** the server runs as uid 1234 and trusts the bundle

#### Scenario: Non-root start

- **WHEN** the image starts with `user: "977:977"` and a bundle
- **THEN** the server trusts the bundle

#### Scenario: Read-only root filesystem

- **WHEN** the image starts with a read-only root filesystem, a writable `/tmp` and a bundle
- **THEN** the server trusts the bundle

### Requirement: A bundle that cannot be honoured stops the start

When the variable is set and the directory is missing or unreadable, holds no certificate, holds a file that is not a
certificate, or `JAVA_OPTS` already sets `javax.net.ssl.trustStore`, the entrypoint MUST exit with code 1 and print one line
that names the variable used. It SHALL NOT start the server.

#### Scenario: Missing directory

- **WHEN** the variable names a path that does not exist
- **THEN** the container exits with code 1 and the output names `INGOT_EXTRA_CA_CERTS`

#### Scenario: Not a certificate

- **WHEN** the directory holds a file with no certificate block
- **THEN** the container exits with code 1 and the output names the variable and the file

#### Scenario: Conflicting trust store

- **WHEN** `JAVA_OPTS` contains `-Djavax.net.ssl.trustStore=/x` and the variable is set
- **THEN** the container exits with code 1 and the output names `INGOT_EXTRA_CA_CERTS`

### Requirement: Certificate contents are not logged

The entrypoint SHALL print the number of imported certificates and the file names to standard error once per start, and MUST
NOT print certificate contents.

#### Scenario: Start log

- **WHEN** a bundle of two files imports successfully
- **THEN** one line reports the count and the two file names
