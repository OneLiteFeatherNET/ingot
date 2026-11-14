---
description: Reference for the audit log of Ingot. Lists every audit action with its trigger and detail keys, the retention settings, the query parameters of the audit API and the columns of the CSV export.
---

# Audit events

The audit log records administrative and artifact events. Each event is one row in the table `audit_events`. This page lists what is recorded, how long it is kept, and how the log is read and exported. For the steps to read and export the log, see [Review the audit log](../how-to/review-audit-log.md). Optionally, every event is also written as a JSON line for a log collector, see [JSON lines sink](#json-lines-sink).

## Event fields

Every event has the same fields. The API returns them as JSON, and the CSV export writes them as columns.

| Field | Content |
| --- | --- |
| `id` | Numeric row id, assigned on insert. |
| `timestamp` | Time of the event. ISO-8601 instant in UTC in the API and the export. |
| `actor` | Who did it: a token name, a principal name, `anonymous`, `system`, `system:<provider>`, `oidc:<providerId>` or `setup`. Stored up to 255 characters. |
| `actorAddress` | Client address of the actor, or empty. Stored up to 64 characters. Empty when `recordActorAddress` is `false` or the address is unknown. |
| `action` | One value of the table below. |
| `repository` | Repository name, or empty. Only artifact events and cleanup runs of a single-repository policy set it. |
| `path` | Artifact coordinates (`gav`) for artifact events, or empty. Stored up to 1024 characters. |
| `details` | JSON object with the keys of the action, or empty. Holds names, counts and field paths. Secrets are not written. |
| `success` | `false` for rejected logins, reused refresh tokens and cleanup runs that did not fully succeed. `true` otherwise. |
| `occurrences` | Number of identical failed logins folded into this row. `1` for every other event. |

The actor of an event raised by an HTTP request is the name of the token that made the request, with the client address. A request that cannot be resolved to a token is recorded as `anonymous`. Events raised by a scheduled job use the actor `system`.

## Audit actions

The list is the enum `AuditAction` in `reposilite-backend/src/main/kotlin/com/reposilite/audit/api/AuditAction.kt`. The name is stored in the database. Details keys that hold lists are JSON arrays of strings.

### Artifacts

| Action | Written when | Details keys |
| --- | --- | --- |
| `ARTIFACT_DEPLOYED` | A file is deployed. `AuditPlugin` listens to `DeployEvent`. The actor comes from the session identifier of the request. Sets `repository` and `path`. | None. |
| `ARTIFACT_DELETED` | A file is deleted. `AuditPlugin` listens to `DeleteEvent`. Sets `repository` and `path`. Files removed by a cleanup run are not recorded one by one (see `CLEANUP_RUN`). | None. |
| `UPLOAD_CHECKSUM_MISMATCH` | A checksum file (`.md5`, `.sha1`, `.sha256` or `.sha512`) does not match the artifact stored before it, under the `warn` or the `reject` [upload checksum policy](../how-to/verify-uploaded-checksums.md). `AuditPlugin` listens to `UploadChecksumMismatchEvent`. The actor is the uploader. Sets `repository` and `path` (the checksum file). `success` is `true`. Under `reject` the upload was refused, under `warn` it was stored. The digests are never written. | `algorithm` (`md5`, `sha1`, `sha256` or `sha512`), `outcome` (`accepted` under `warn`, `rejected` under `reject`) |
| `CLEANUP_RUN` | A cleanup policy run finishes, started manually or by the schedule (`CleanupService.run`). `success` is `true` only when the run status is `SUCCEEDED`. The actor is `system` for scheduled runs. `repository` is set when the policy names exactly one repository. | `runId`, `policy`, `scope`, `trigger`, `status`, `deletedCount`, `deletedBytes` |
| `DATABASE_SNAPSHOT_CREATED` | A snapshot of the SQLite database is written, from `POST /api/database/snapshots` or from `--snapshot-database`. The actor is the principal of the request, or `command-line` for the flag. Only successful snapshots are recorded. | `file` (the file name in `backups/`) |

### Mirrors and protected groups

| Action | Written when | Details keys |
| --- | --- | --- |
| `NAMESPACE_PROXY_REFUSED` | A request under a protected group prefix was refused: a proxy member was skipped and no hosted member holds the coordinate. `AuditPlugin` listens to `ProxyRefusedEvent`. The actor is the token of the request, or `anonymous`. Sets `repository` and `path`, and `success` is `false`. The client address is not recorded. | `prefix` (the protected prefix that covers the coordinate), `skippedMembers` (names of the proxy members that were not asked) |
| `MIRROR_CHECKSUM_MISMATCH` | A copy stored by a mirror with `checksumPolicy` `warn` or `fail` does not match the checksum file of the same host. `AuditPlugin` listens to `MirrorIntegrityEvent`. The actor is `system`, and `success` is `false`. Sets `repository` and `path`. | `host`, `policy` (`warn` or `fail`), `algorithm` (`sha256` or `sha1`), `outcome` (`stored` under `warn`, `refused` under `fail`) |
| `MIRROR_CHECKSUM_MISSING` | A host of a mirror with `checksumPolicy` `warn` or `fail` publishes no checksum file for the artifact. The actor, `success` and the fields are those of `MIRROR_CHECKSUM_MISMATCH`, with `algorithm` set to `none`. | `host`, `policy`, `algorithm` (always `none`), `outcome` (`stored` under `warn`, `refused` under `fail`) |

Nothing is written for a mirror with `checksumPolicy` `ignore`, or for metadata and checksum files. See [Check checksums](../how-to/create-proxy-repositories.md#check-checksums).

### Settings

| Action | Written when | Details keys |
| --- | --- | --- |
| `SETTINGS_CHANGED` | A settings domain is saved and at least one field value differs from before (`SharedConfigurationFacade`). Values are never written, only paths. | `domain`, `fields` (dotted paths such as `ldap.host`) |
| `REPOSITORY_CREATED` | A save of the `maven` domain adds a repository id that was not stored before (`SharedConfigurationFacade`). Written in addition to `SETTINGS_CHANGED`. Sets `repository` to the new id. | `visibility`, `type` (the declared type, or `null`) |
| `REPOSITORY_DELETED` | A save of the `maven` domain removes a stored repository id. Written in addition to `SETTINGS_CHANGED`. Sets `repository` to the removed id. A rename is one deletion of the old id and one creation of the new id. A change of visibility or type alone writes no repository event. | `visibility`, `type` of the removed entry (or `null`) |

### Legacy tokens

These events come from `AccessTokenFacade`, which serves the legacy token API and the console commands.

| Action | Written when | Details keys |
| --- | --- | --- |
| `TOKEN_CREATED` | A legacy token is created or replaced (`createAccessToken`, `upsertAccessToken`). The startup bootstrap of `--token` is not recorded. | `token`, `type`, `replaced` |
| `TOKEN_DELETED` | A legacy token is deleted (`deleteToken`). | `token` |
| `TOKEN_SECRET_REGENERATED` | The secret of a legacy token is regenerated (`regenerateAccessToken`). The secret is not recorded. | `token` |
| `TOKEN_ROUTES_CHANGED` | The permissions or routes of a legacy token change: on create, on update, and when a permission or route is added or removed. Nothing is written for an empty change. | `token`, `permissionsAdded`, `permissionsRemoved`, `routesAdded`, `routesRemoved` (routes are written as `<path> (<permission>)`) |

### Users, machine accounts and tokens of the identity model

These events come from `IdentityFacade` unless another source is named. The actor is the token that made the request.

| Action | Written when | Details keys |
| --- | --- | --- |
| `PRINCIPAL_CREATED` | A principal is created (`createPrincipal`), or an external login creates a user automatically (`ExternalAccountService`, actor is the provider id). The first administrator created by the setup is recorded as `SETUP_COMPLETED` instead. | `principal`, plus `kind` (`USER` or `MACHINE`) for `createPrincipal` |
| `PRINCIPAL_UPDATED` | The display name changes, or a principal is enabled again (`updatePrincipal`). Nothing is written when no field changed. | `principal`, `changed` (`displayName`, `disabled`) |
| `PRINCIPAL_DISABLED` | A principal is disabled (`updatePrincipal`). | `principal` |
| `PRINCIPAL_DELETED` | A principal is deleted (`deletePrincipal`). Its tokens are removed with it and are listed here, not as `TOKEN_DELETED`. | `principal`, `kind`, `tokens` |
| `TOKEN_CREATED` | A token of a principal is created, through the principal API (`IdentityFacade.createToken`) or by the user for their own account (`OwnTokens.issue`). | `token`, `principal` |
| `TOKEN_DELETED` | A token of a principal is deleted (`IdentityFacade.deleteToken`, `OwnTokens.delete`). | `token`, plus `principal` for `IdentityFacade.deleteToken` |
| `TOKEN_SECRET_REGENERATED` | A user rotates the secret of their own token (`OwnTokens.rotate`). | `token` |
| `INSTANCE_ADMIN_GRANTED` | The instance role is granted to a principal or a team. Granting it again writes nothing. | `principal` or `team` |
| `INSTANCE_ADMIN_REVOKED` | The instance role is removed from a principal or a team. | `principal` or `team` |
| `NODES_CHANGED` | The permission nodes of a principal or a team are replaced and the list differs from before (`replacePrincipalNodes`, `replaceTeamNodes`). | `principal` or `team`, `added`, `removed` (entries like `<node>=allowed` or `<node>=denied`), plus `kind` for principals |

### Teams

| Action | Written when | Details keys |
| --- | --- | --- |
| `TEAM_CREATED` | A team is created (`createTeam`). | `team` |
| `TEAM_UPDATED` | The description or the external reference changes (`updateTeam`), or the parents change (`replaceTeamParents`). | `team`, `changed` (`description`, `externalRef`, `parents`). For parents also `added` and `removed`. |
| `TEAM_DELETED` | A team is deleted (`deleteTeam`). | `team` |
| `TEAM_MEMBERSHIP_CHANGED` | The manual members of a team change (`replaceMembers`), or a login synchronises the groups of an external provider (`ExternalGroupSync`). The synchronisation writes one event per team and login, with the actor `system:<provider>`. | `team`, `added`, `removed` (principal names) |
| `TEAM_GRANTS_CHANGED` | The grants of a team are replaced and differ from before (`replaceGrants`). | `team`, `added`, `removed` (entries like `repository=<name or *> path=<prefix or *> role=<ROLE>`) |
| `EXTERNAL_REF_UNGUARDED` | A write sets, changes or clears the external reference of a team without the admin area `ingot.admin.idp` (`createTeam` and `updateTeam`). In `enforce` the write is refused with 403 and the outcome is `refused`. In `warn` the write proceeds, the outcome is `allowed`, and a warning is logged. Not recorded in `off`. | `team`, `reference` (the requested reference, empty when the reference is cleared), `caller` (the principal name), `mode` (`enforce` or `warn`), `outcome` (`refused` or `allowed`) |
| `LAST_ADMIN_REVOKED_BY_PROVIDER` | A group synchronisation leaves no usable instance admin although there was one before (`ExternalGroupSync.reportLastAdminRevoked`). One event per affected admin. The actor is `system:<provider>`. | `provider`, `principal` |

### Maintenance

| Action | Written when | Details keys |
| --- | --- | --- |
| `READ_ONLY_CHANGED` | The stored read-only state is changed through `POST` or `DELETE /api/admin/read-only` (`ReadOnlyFacade.set`). A repeated enable or disable changes nothing and writes nothing. A forced state writes nothing either. | `enabled` (the stored value after the change) |
| `NEXUS_IMPORT_RUN` | A Nexus import job ends, or pauses for a reason that needs the operator. The actor is `migration`. In `summary` mode (`INGOT_NEXUS_IMPORT_AUDIT=summary`) this is the only event of the copy. In `per-file` mode each copied file also has its own `ARTIFACT_DEPLOYED` event with the actor `migration`. | `copied`, `skipped`, `conflicts`, `failed` (counts) and `status` |
| `SETTINGS_EXPORTED` | The saved shared settings are exported with a password through `POST /api/settings/export`. The actor is the principal of the request. Nothing is stored. The file never appears in the event. | `domains` (the number of domains) |
| `SETTINGS_IMPORT_DECRYPTED` | An encrypted settings file is opened through `POST /api/settings/import/open`. Nothing is stored by the request. | `domains` (the number of domains) |
| `SETTINGS_IMPORT_REFUSED` | An encrypted settings file could not be opened. `success` is `false`. | `reason`: `password` when the authentication of the file failed, which is also what a changed file looks like, or `file` for a file of another format, version or cost |
| `SETTINGS_SECRETS_REENCRYPTED` | The stored secret values are written with the active settings key: at the first start with a key, by `settings-reencrypt` after a rotation, or by `settings-decrypt` for a downgrade. No value, password or key appears in the event. | `reason` (`first-start`, `rotation` or `decrypt`), `values` (the number of secret values written), `activeKeyId` and `previousKeyId` (key identifiers, never keys) |
| `SUPPORT_BUNDLE_DOWNLOADED` | A support bundle is downloaded from `GET /api/admin/support/bundle`. The actor is the principal of the request. A refused bundle (the final redaction check found a secret, or the archive exceeds its size cap) is recorded with `success` false and a `reason`, and no file count. Opening the system information page writes nothing. | `files` (the number of files in the bundle), `redactions` (the number of values withheld, masked or scrubbed); on a refusal `reason` instead |

### Login and sessions

| Action | Written when | Details keys |
| --- | --- | --- |
| `LOGIN_SUCCEEDED` | A password login succeeds (`LoginService.login`), or an OIDC or GitHub login succeeds (`OidcLoginService`). The actor is the principal name with the client address. | None. |
| `LOGIN_FAILED` | A login is rejected. See the list below the table. `success` is `false`. | None. |
| `LOGOUT` | A user logs out with a known refresh token (`SessionService.logout`). Unknown or missing tokens write nothing. | None. |
| `SESSION_REFRESH_REUSE_DETECTED` | A refresh token that was already used is presented again. The whole token family is revoked (`SessionService.refresh`). `success` is `false`. | None. |
| `SETUP_COMPLETED` | The first administrator is created with the setup code (`LoginService.completeSetup`). The actor is the new user. | `principal` |
| `PASSWORD_CHANGED` | A user changes their own password (`LoginService.changePassword`). The password is not recorded. | `principal` |
| `PASSWORD_RESET` | An administrator sets a new password for a user (`LoginService.resetPassword`). | `principal` |

`LOGIN_FAILED` has three sources:

* `LoginService.login`: a rejected, disabled, refused or rate-limited password login. The actor is the submitted user name with the client address. A wrong setup code is recorded with the actor `setup`.
* `OidcLoginService`: a failed OIDC or GitHub callback. The actor is `oidc:<providerId>`.
* `AuthenticationFacade`: a rejected token on a request, including requests refused by the brute-force protection. The actor is the submitted token name with the client address.

Failed logins are folded. All failures of one actor name from one address inside the same minute share one row, and `occurrences` counts them. The next failure after that minute starts a new row. A folded row has no `repository`, `path` or `details`.

## Retention

Retention and address recording are fields of the settings domain `audit` (title "Audit"). They are shared settings, stored with the other shared settings, and edited in the dashboard settings or through `GET` and `PUT /api/settings/domain/audit`. The code defines no environment variable and no `configuration.cdn` key for them.

| Key | Type | Default | Effect |
| --- | --- | --- | --- |
| `retentionDays` | integer | `90` | Events older than this many days are deleted. `0` or less keeps every event. |
| `recordActorAddress` | boolean | `true` | Stores the client address in `actorAddress`. When `false`, the field stays empty for new events. Existing rows are not changed. |

Purge behaviour:

* The purge job is scheduled at startup. It runs first after one hour and then every 24 hours (`AuditPlugin`).
* It deletes every row whose `timestamp` is older than now minus `retentionDays` days, whatever the action. Deleted rows cannot be recovered. Export the log first if you need them.
* It reads `retentionDays` on each run, so a change applies to the next run.
* With several instances on one database, only one instance purges per UTC day (claim key `audit-purge`).
* A purge that removes rows writes the log line `AUDIT | Removed <n> event(s) older than <days> day(s)`.

## JSON lines sink

The sink writes every audit event as one JSON object per line, in addition to the table. It is off by default, and with it off the server writes nothing extra. It is switched on with the variables `INGOT_AUDIT_SINK_STDOUT` and `INGOT_AUDIT_SINK_FILE`, listed in [Environment variables](environment-variables.md#audit-sink-variables). For a collector setup, see [Forward audit events to a SIEM](../how-to/forward-audit-events-to-a-siem.md).

### Line fields

Every line is UTF-8, has exactly one JSON object and ends with a line feed. Every field is always present, and a value that does not apply is `null`.

| Field | Type | Content |
| --- | --- | --- |
| `schema` | string | Always `ingot.audit`. Lets a collector pick audit lines out of a stream that carries other logs. |
| `version` | integer | `1`. See the compatibility rule below. |
| `time` | string | ISO-8601 instant in UTC with milliseconds, taken when the event is recorded. |
| `node` | string | The instance identifier of the replica that recorded the event. It is the value of `INGOT_CLUSTER_INSTANCE_ID`, or the host name when that is unset. |
| `action` | string | One action name of the tables above. |
| `actor` | string | As in the table. |
| `actorAddress` | string or `null` | Client address. `null` when `recordActorAddress` is `false` or the address is unknown. |
| `repository` | string or `null` | Repository name, as in the table. |
| `path` | string or `null` | Artifact coordinates for artifact events, as in the table. |
| `details` | object or `null` | The details keys of the action as a JSON object, not as text. `null` when the action has none. |
| `success` | boolean | As in the table. |
| `occurrences` | integer | Always `1`. Each failed login is its own line. |

An example line, wrapped here for reading:

```json
{"schema":"ingot.audit","version":1,"time":"2026-11-10T08:15:30.123Z","node":"ingot-0","action":"TOKEN_CREATED","actor":"admin","actorAddress":"10.0.0.2","repository":null,"path":null,"details":{"token":"deploy-bot","replaced":false},"success":true,"occurrences":1}
```

A line has no `id`. It describes an event, not a table row, so the lines and the rows do not match one to one: failed logins are folded into one row in the table, and each of them is one line.

### Compatibility

Adding a field or an action name does not change `version`. Consumers must ignore fields they do not know, and must accept action names they do not know. Renaming, removing or retyping a field, or changing what it means, raises `version`. The server writes one version at a time, so a collector rule that checks `version` keeps working until the operator upgrades past it.

### Failure behaviour

* Recording an event never waits for the sink. The line is handed to an in-memory queue of 10 000 lines, and one background thread writes it.
* The table row is written whether or not the line was written, and the line is written before the row, so a line exists even when the database is down.
* A full queue drops the new line. A write error, such as a full disk or a missing permission, drops the batch that failed.
* Dropped and failed lines are counted. At most one warning per minute is logged, starting with `AUDIT | JSON sink lost`, with the counts and the last error.
* A file that cannot be opened does not stop the start. The sink tries again on the next write and creates missing parent directories.
* On shutdown the queue is written for up to 5 seconds. Lines still queued after that are lost.

### Files

The file is opened in append mode and created with mode `0640`. The sink never rotates, compresses or deletes the file. It checks the path at most once per second and opens the file again when the path no longer names the open file, so an external `logrotate` with `create`, or a rename by an operator, needs no signal.

## Personal data

The audit log holds personal data wherever a person is named. The JSON lines sink copies that data out of the database, so it has to be handled in both places. Ingot does not give legal advice on this, but the table below says which fields carry personal data and what controls exist.

| Field | Personal data when |
| --- | --- |
| `actor` | It is a user name, an e-mail style login or an OIDC subject. A machine account or token name usually is not. |
| `actorAddress` | Always when the actor is a person, because an IP address is personal data. |
| `details` | It names a principal, a team or the members of a team. |
| `path`, `repository` | Rarely, when a coordinate contains a name. |

* `recordActorAddress` controls the address in the table and in the lines. Turning it off stops new addresses in both. It does not remove the addresses already written.
* `retentionDays` applies to the table only. The lines are kept as long as the collector and the rotated files keep them, so set those to the retention the policy requires.
* Removing a principal does not rewrite old rows or old lines. The sink never edits its file. Where a person must be erased from the lines, the collector or the file owner does it.
* Secrets are never written to `details`, and the lines carry no secret.
* The file is `0640`. Standard output is the log stream of the platform, so its access is the access to that stream.

## Read access

| Endpoint | Method | Permission |
| --- | --- | --- |
| `/api/audit` | `GET` | Instance admin. |
| `/api/audit/export` | `GET` | Instance admin. |

Both routes use the `managerOnly` check without an admin area, which passes for an instance admin. A caller without it is refused with the message `Only manager can access this endpoint`.

The dashboard page `/audit` is gated on the permission node `ingot.admin.audit` in `reposilite-frontend/src/router/routes.js`. The two API routes do not check that node.

## Query parameters

`/api/audit` and `/api/audit/export` read the same filters. A blank parameter counts as absent. An invalid value is answered with `400` and a message that names the parameter.

| Parameter | Meaning |
| --- | --- |
| `actor` | Exact match on `actor`. |
| `action` | One or more action names. Repeat the parameter or separate names with commas. Unknown names are answered with `400` and listed together. |
| `repository` | Exact match on `repository`. |
| `q` | Case-insensitive substring match on `actor`, `repository`, `path` and `details`. The characters `%` and `_` match literally. |
| `from` | Inclusive lower bound, ISO-8601 instant such as `2026-10-15T00:00:00Z`. |
| `to` | Exclusive upper bound, same format. `from` must be before `to`. |
| `limit` | Page size from 1 to 200. Default 50. Paging only applies to `/api/audit`. |
| `cursor` | Opaque value from `nextCursor` of the previous page. Paging only applies to `/api/audit`. |

`GET /api/audit` returns `{ "events": [...], "nextCursor": "..." }`, newest first. `nextCursor` is `null` on the last page. The export ignores `limit` and `cursor` for paging, but still rejects invalid values.

## CSV export

`GET /api/audit/export` returns the events that match the filters as CSV, newest first.

| Property | Value |
| --- | --- |
| Content type | `text/csv` |
| Download name | `ingot-audit.csv` (`Content-Disposition: attachment`) |
| Encoding | UTF-8 |
| Line end | `\n`, RFC 4180 quoting |
| Row limit | 100 000 rows. A larger result is cut off and the server logs a warning. Narrow the filters to export the rest. |
| Header | Always the first line, also when no event matches. |

Columns, in this order:

| Column | Content |
| --- | --- |
| `id` | Row id. |
| `timestamp` | ISO-8601 instant in UTC. |
| `actor` | Actor name. |
| `actorAddress` | Client address, or empty. |
| `action` | Action name. |
| `repository` | Repository name, or empty. |
| `path` | Artifact coordinates, or empty. |
| `success` | `true` or `false`. |
| `occurrences` | Folded occurrence count. |
| `details` | The details JSON as one text cell, or empty. |

A cell that starts with `=`, `+`, `-`, `@`, a tab or a carriage return gets a leading apostrophe, so a spreadsheet does not read it as a formula.

## Storage

| Property | Value |
| --- | --- |
| Table | `audit_events`, created on every start if missing. No migration step. |
| Indexes | `timestamp`, `actor`, `action` |
| Settings domains added on first start | Listed in [Upgrade changes and supported paths](upgrade-changes.md). |
