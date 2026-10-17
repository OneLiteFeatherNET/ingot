---
description: Reference for the audit log of Ingot. Lists every audit action with its trigger and detail keys, the retention settings, the query parameters of the audit API and the columns of the CSV export.
---

# Audit events

The audit log records administrative and artifact events. Each event is one row in the table `audit_events`. This page lists what is recorded, how long it is kept, and how the log is read and exported. For the steps to read and export the log, see [Review the audit log](../how-to/review-audit-log.md).

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
| `CLEANUP_RUN` | A cleanup policy run finishes, started manually or by the schedule (`CleanupService.run`). `success` is `true` only when the run status is `SUCCEEDED`. The actor is `system` for scheduled runs. `repository` is set when the policy names exactly one repository. | `runId`, `policy`, `scope`, `trigger`, `status`, `deletedCount`, `deletedBytes` |

### Settings

| Action | Written when | Details keys |
| --- | --- | --- |
| `SETTINGS_CHANGED` | A settings domain is saved and at least one field value differs from before (`SharedConfigurationFacade`). Values are never written, only paths. | `domain`, `fields` (dotted paths such as `ldap.host`) |

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
| `LAST_ADMIN_REVOKED_BY_PROVIDER` | A group synchronisation leaves no usable instance admin although there was one before (`ExternalGroupSync.reportLastAdminRevoked`). One event per affected admin. The actor is `system:<provider>`. | `provider`, `principal` |

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
