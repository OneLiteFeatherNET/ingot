# Spec Delta

## Purpose

An operator can set the lifetime, the acquire timeout and the keepalive of the database connection pool, and can work out how
many connections a set of replicas needs. The pool size stays one key with the same default, and the new keys default to the
values the pool already uses.

## ADDED Requirements

### Requirement: Pool size keeps its single key

The number of pooled database connections SHALL be set by `databaseThreadPool`, read from `INGOT_LOCAL_DATABASETHREADPOOL`,
with `REPOSILITE_LOCAL_DATABASETHREADPOOL` as the fallback. No second key SHALL set the size. The default SHALL remain `1`.

#### Scenario: Environment sets the pool size

- **WHEN** `INGOT_LOCAL_DATABASETHREADPOOL` is `10` and `REPOSILITE_LOCAL_DATABASETHREADPOOL` is `4`
- **THEN** the maximum pool size of the MariaDB, MySQL or PostgreSQL connection is `10`

#### Scenario: Legacy spelling only

- **WHEN** only `REPOSILITE_LOCAL_DATABASETHREADPOOL` is set to `4`
- **THEN** the maximum pool size is `4`

### Requirement: Lifetime, timeout and keepalive keys

The server SHALL read `databaseMaxLifetime` (seconds, default `1800`), `databaseConnectionTimeout` (seconds, default `30`) and
`databaseKeepalive` (seconds, default `0`, which means off) from `INGOT_LOCAL_<KEY>` with `REPOSILITE_LOCAL_<KEY>` as the
fallback. They SHALL map to the Hikari `maxLifetime`, `connectionTimeout` and `keepaliveTime` of the pool, in milliseconds.
A value that is not a whole number SHALL stop the start, as every `Int` key does.

#### Scenario: Defaults are the pool's current values

- **WHEN** none of the three variables is set
- **THEN** the pool is created with a maximum lifetime of 1800000 milliseconds, a connection timeout of 30000 milliseconds and a keepalive time of 0, the values the pool uses before this change

#### Scenario: Keepalive set in seconds

- **WHEN** `INGOT_LOCAL_DATABASEKEEPALIVE` is `60`
- **THEN** the pool is created with a keepalive time of 60000 milliseconds

### Requirement: The keys apply to server databases only

The three keys SHALL apply to MariaDB, MySQL and PostgreSQL pools, and to a custom JDBC URL that starts with `jdbc:`. SQLite
and H2 SHALL keep their single connection and ignore the keys.

#### Scenario: SQLite ignores the pool keys

- **WHEN** the database is `sqlite reposilite.db` and `INGOT_LOCAL_DATABASEMAXLIFETIME` is `60`
- **THEN** the SQLite connection keeps its single connection and the value does not change its behaviour

### Requirement: Pool budget is documented

The database page SHALL state the connection budget of a set of replicas: each replica needs `databaseThreadPool`
connections, and one more connection while it starts on PostgreSQL for the schema lock. The server connection limit SHALL be
at least the number of replicas multiplied by that number. The page SHALL NOT recommend a pool size.

#### Scenario: Operator sizes the server

- **WHEN** an operator plans three replicas on PostgreSQL with `databaseThreadPool` of `5`
- **THEN** the page gives the figure of at least 18 server connections for the start, and it names the steady-state figure of 15 without the start connection

### Requirement: Environment and configuration reference lists the keys

`docs/gitbook/reference/environment-variables.md` and `docs/gitbook/reference/configuration-and-startup.md` SHALL list the three
keys with their `INGOT_` and `REPOSILITE_` names, their defaults and their units.

#### Scenario: Reference is complete

- **WHEN** the change is complete
- **THEN** each of the three keys appears in both reference pages with the default that the code uses
