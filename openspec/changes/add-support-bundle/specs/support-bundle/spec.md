# Spec Delta

## Purpose

An administrator can see what an Ingot instance is running and download a support bundle for a ticket, and the bundle never
contains a secret. Nothing existing changes, and the feature can be turned off.

## ADDED Requirements

### Requirement: System information

The instance SHALL provide an administrator with its Ingot version and build, JVM, operating system, database kind, storage
kinds, cluster members, enabled plugins and effective non-secret configuration, as JSON and as a dashboard page.

#### Scenario: Administrator reads the page

- **WHEN** a principal with the console area requests `GET /api/admin/support/system-info`
- **THEN** the response is `200` and lists every section above, with a build of `unknown` when the jar carries none

#### Scenario: Cluster members are listed

- **WHEN** a cluster bus is configured
- **THEN** the cluster section lists the local instance and the other members with the redacted bus URL

### Requirement: Support bundle

The instance SHALL return a zip on `GET /api/admin/support/bundle` that holds the system information as files, the recent log
lines of the answering instance and a manifest naming each file, the redaction rule version and the number of redacted values.

#### Scenario: Bundle contents

- **WHEN** an authorized administrator downloads the bundle
- **THEN** the zip holds `manifest.json`, `system-info.json`, `configuration-local.json`, `configuration-shared.json`,
  `cluster.json`, `plugins.json` and `logs/<instance>.log`, with `Cache-Control: no-store`

#### Scenario: Served while read-only

- **WHEN** the instance is read-only and an administrator downloads the bundle
- **THEN** the download succeeds

### Requirement: Administrator access only

Both endpoints SHALL require an authenticated principal with the console area and SHALL NOT be reachable anonymously.

#### Scenario: Anonymous caller

- **WHEN** a request has no credentials
- **THEN** the answer is `401` and no information is read

#### Scenario: Principal without the area

- **WHEN** a principal without the console area calls either endpoint
- **THEN** the answer is `403`

### Requirement: Allowlist redaction of local values

Local configuration, environment variables, system properties and JVM arguments SHALL be written only when an allowlist
names the key. Every other key SHALL appear by name with the value `[withheld]`.

#### Scenario: Unknown key

- **WHEN** the local configuration holds a key that is not allowlisted
- **THEN** the bundle lists its name with `[withheld]` and never its value

#### Scenario: Database setting

- **WHEN** the database setting is `mariadb db.example:3306 shop user hunter22`
- **THEN** the bundle shows the kind and the host and port only, and neither the user nor the password

### Requirement: Shared settings are masked with the existing masking

Every shared settings domain SHALL pass through the existing settings secret masking, whether or not `hideSettingsSecrets` is
enabled, and secret references SHALL be masked as well. A domain of unknown type SHALL be withheld.

#### Scenario: Masking independent of the flag

- **WHEN** `hideSettingsSecrets` is off and a domain holds an S3 secret key
- **THEN** the bundle shows `********` in its place

#### Scenario: Secret reference

- **WHEN** a secret value is the reference `${env:S3_SECRET}`
- **THEN** the bundle masks it

### Requirement: Log lines are scrubbed

Log lines SHALL have every known secret value of four or more characters replaced by `[redacted]`, and SHALL have
authorization headers, bearer and basic credentials, credentials in URLs and `password`, `secret`, `token` and key
assignments replaced, on plain and structured lines alike.

#### Scenario: Known value in a log line

- **WHEN** a cached log line contains the S3 secret key of a shared domain
- **THEN** the bundle's log holds `[redacted]` in its place

#### Scenario: Pattern in a log line

- **WHEN** a line contains `Authorization: Bearer abc.def`
- **THEN** the credential is replaced

### Requirement: Fail closed

When any known secret value is still present in the assembled output, the instance SHALL NOT serve the bundle, SHALL answer
`500` with a problem body, and SHALL write an unsuccessful audit event without the value.

#### Scenario: Guard trips

- **WHEN** a planted secret survives redaction in a test double
- **THEN** no zip bytes are returned and the audit event has `success` false

### Requirement: Known secrets never appear

For a fixed set of planted secrets in the settings, local configuration, bus URL, environment and logs, no entry of the bundle
and no field of the system information response SHALL contain any of them.

#### Scenario: Secret search

- **WHEN** the bundle and the JSON response are searched for each planted secret
- **THEN** none is found

### Requirement: Opt-out and audit

`INGOT_SUPPORT_BUNDLE_ENABLED` (fallback `REPOSILITE_SUPPORT_BUNDLE_ENABLED`) SHALL default to `true`. When `false`, both
endpoints SHALL answer `404` and the pages SHALL be absent. A bundle download SHALL write one audit event with the actor.

#### Scenario: Opt-out

- **WHEN** the setting is `false`
- **THEN** both endpoints answer `404`

#### Scenario: Download is audited

- **WHEN** an administrator downloads the bundle
- **THEN** one `SUPPORT_BUNDLE_DOWNLOADED` event records the actor and the file count
