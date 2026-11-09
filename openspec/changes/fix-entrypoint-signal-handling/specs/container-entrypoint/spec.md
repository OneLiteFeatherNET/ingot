# Spec Delta

## Purpose

The server image starts the server so that a stop reaches the server process itself, and the identity and environment the
server gets stay as they are for every deployment that works today.

## ADDED Requirements

### Requirement: Termination signal reaches the server process

The container entrypoint SHALL start the server as the process that replaces the entrypoint, so that `SIGTERM` is delivered to
the JVM and not to a wrapper process in front of it. The entrypoint MUST NOT leave any process in front of the JVM on the
root start path.

#### Scenario: Stop with the default root start

- **WHEN** the image is started with default settings, as root, and stopped with a 30 second timeout
- **THEN** the server process receives `SIGTERM`, the container exits with code 143 before the timeout, and the shutdown log
  line appears in the container output

#### Scenario: Stop with an explicit non-root user

- **WHEN** the image is started with `user: "977:977"` and stopped with a 30 second timeout
- **THEN** the container exits with code 143 and the shutdown log line appears, as before this change

### Requirement: Privilege drop keeps the service account contract

On the root start path the server SHALL run under the uid from `PUID` and the gid from `PGID`, with the supplementary groups of
that account initialised. Defaults stay 977 for both. The entrypoint MUST export `HOME`, `USER`, `LOGNAME` and `SHELL` for the
service account, with the values that `runuser` set before this change.

#### Scenario: Custom PUID and PGID

- **WHEN** the image is started as root with `PUID=1234` and `PGID=1234`
- **THEN** the server process runs as uid 1234 and gid 1234, and the data directory is owned by that account

#### Scenario: Environment of the server process

- **WHEN** the server process starts on the root start path
- **THEN** `USER` and `LOGNAME` name the service account, and `HOME` names its home directory

### Requirement: Server command line is unchanged

The entrypoint SHALL pass the same arguments to the JVM as before this change: the tinylog file settings, `JAVA_OPTS`, `-jar ingot.jar`
and the working directory taken from `INGOT_OPTS` or `REPOSILITE_OPTS`, with `/app/data` as the default working directory.

#### Scenario: Options from the legacy variable

- **WHEN** only `REPOSILITE_OPTS` is set to `--token-cache=5`
- **THEN** the JVM receives `--working-directory=/app/data --token-cache=5` as its arguments
