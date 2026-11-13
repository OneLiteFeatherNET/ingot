---
description: Overview of the REST endpoints of Ingot grouped by area, with the method, path, authentication requirement and purpose of each. Covers login, identity, legacy tokens, Maven and search APIs, status, audit, settings, console and the repository protocol.
---

# HTTP API overview

This page lists the HTTP endpoints of Ingot by area. For the request and response schemas, use the OpenAPI document that the server publishes at `/openapi`. Every REST endpoint of this list is in that document. The routes left out on purpose, such as the javadoc viewer pages, the dashboard and the request filters, are named with their reason in the exclusion list of the completeness test, `reposilite-backend/src/test/kotlin/com/reposilite/web/api/OpenApiCompletenessTest.kt`.

The document declares two security schemes. `tokenBasic` is HTTP Basic with an access token name and secret. `sessionBearer` is the bearer session that a login returns. A route that needs a login refers to either one, so a generated client knows which credentials to send.

Paths in braces are path parameters. `<gav>` stands for the rest of a Maven path, such as `net/onelitefeather/mycelium/1.0.0/`.

## OpenAPI document

The document describes the REST API above. It covers the endpoints, their parameters, request and response schemas and the security schemes. It does not cover the Maven repository protocol, the dashboard routes or the plugin API.

Get the document in one of two ways:

- From a running instance, at `GET /openapi`. It is the document of that instance and its version field names the running release.
- From a release page, as `ingot-<version>-openapi.json` next to the jar. Its version field holds the release version. Use it to generate a client for that release without starting an instance.

The compatibility rule is the one the pull request check enforces. Removing an endpoint or method, renaming a required parameter, requiring a request field that was optional, or narrowing a response is a breaking change. Such a change is declared with `!` after the type or scope of its pull request title and with a `BREAKING CHANGE:` footer in the commit, and release-please turns it into a major version. Additions do not break the rule.

## Authentication requirements

| Label | Meaning |
| --- | --- |
| None | Anyone may call the endpoint. |
| Optional token | Anonymous calls are allowed. A token or session changes what is visible. |
| Token or session | A valid token or a signed-in session is required. |
| Refresh cookie | The refresh cookie of a browser session is required. |
| Path permission | A token or session that may perform the action on the path. Instance admins pass. The action is read for GET and HEAD, deploy for PUT and POST, and delete for DELETE. |
| Instance admin | The caller holds `ingot.admin.*` (or `*`). Token narrowing applies. |
| Admin area `<area>` | The caller holds `ingot.admin.<area>` (or `ingot.admin.*`). Token narrowing applies. Only the areas `users`, `tokens`, `teams`, `console` and `audit` are checked this way, and the support endpoints use `console`. |

Wrong or missing credentials answer with `401`. A valid caller without the right answers with `403`. Read [Permission nodes](permission-nodes.md) and [Access tokens and routes](access-tokens-and-routes.md) for what the rights are.

## Support

Read-only routes for the system information and the support bundle. Both need the console area (`ingot.admin.console`), so an anonymous caller gets `401` and a principal without the area gets `403`. Every answer carries `Cache-Control: no-store`. Read-only mode does not refuse them. With the opt-out `INGOT_SUPPORT_BUNDLE_ENABLED=false` the two data routes answer `404` before anything is read. See [Collect a support bundle](../how-to/collect-a-support-bundle.md).

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| GET | `/api/admin/support/availability` | Admin area `console` | Returns `{"enabled": true}` or `{"enabled": false}`. The dashboard uses it to show or hide the support links. |
| GET | `/api/admin/support/system-info` | Admin area `console` | Returns the system information as JSON: the Ingot version and build (`unknown` when the jar carries none), the JVM, the operating system, the database kind and endpoint, the storage kinds and regions per repository, the cluster members, the plugins, the environment, the system properties and the redacted configuration. |
| GET | `/api/admin/support/bundle` | Admin area `console` | Returns `application/zip` with `Content-Disposition: attachment; filename="ingot-support-<instance>-<yyyyMMdd-HHmmss>.zip"`. The files are `manifest.json`, `system-info.json`, `configuration-local.json`, `configuration-shared.json`, `cluster.json`, `plugins.json` and `logs/<instance>.log`. The bundle describes the instance that answers only, and is capped at 8 MiB. |

A bundle is built under one permit. A second download while one is being built waits briefly and then answers `429`. A bundle or a system information answer that the final redaction check refuses is never served: the answer is `500` with a problem body that says the check failed. A download writes one [audit event](audit-events.md).

## Login and sessions

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| POST | `/api/auth/login` | None | Signs in with a provider. The body carries `provider`, `username` and `password`. |
| POST | `/api/auth/logout` | None | Ends the browser session. |
| POST | `/api/auth/refresh` | Refresh cookie | Renews the browser session. |
| GET | `/api/auth/me` | Token or session | Details of the current session or token. |
| GET | `/api/auth/providers` | None | Lists the announced login providers. |
| GET | `/api/auth/oidc/{id}/login` | None | Starts an OpenID Connect or GitHub login. |
| GET | `/api/auth/oidc/{id}/callback` | None | Finishes an OpenID Connect or GitHub login. |
| POST | `/api/auth/providers/oidc/{id}/test` | Instance admin | Tests an OpenID Connect provider. |
| POST | `/api/auth/providers/ldap/test` | Instance admin | Tests the LDAP directory. |
| GET | `/api/auth/setup` | None | Tells whether the first administrator must still be created. |
| POST | `/api/auth/setup` | None, only while no user exists | Creates the first administrator with the setup code from the log. |

See [Login providers](login-providers.md) for the settings behind these endpoints.

## Identity: own account and tokens

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| POST | `/api/identity/me/password` | Token or session | Changes the own password. |
| GET | `/api/identity/me/tokens` | Token or session | Lists the own tokens, without secrets. |
| POST | `/api/identity/me/tokens` | Token or session | Creates an own token. The secret is returned once. |
| POST | `/api/identity/me/tokens/{name}/rotate` | Token or session | Replaces the secret of an own token. |
| DELETE | `/api/identity/me/tokens/{name}` | Token or session | Deletes an own token. |
| GET | `/api/identity/check` | Token or session | Decides one node, or one path, for a principal. Instance admins may check any principal, others only themselves. |

## Identity: principals

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| GET | `/api/identity/principals` | Admin area `users` | Lists the principals, users and machine accounts. |
| POST | `/api/identity/principals` | Admin area `users` | Creates a principal of kind `USER` or `MACHINE`. |
| GET | `/api/identity/principals/{name}` | Admin area `users` | Returns a principal with its teams and tokens, without secrets. |
| PATCH | `/api/identity/principals/{name}` | Admin area `users` | Changes the display name or the disabled flag. |
| DELETE | `/api/identity/principals/{name}` | Admin area `users` | Deletes a principal with its tokens, memberships and instance role. |
| PUT | `/api/identity/principals/{name}/instance-admin` | Admin area `users` | Grants the instance admin role. |
| DELETE | `/api/identity/principals/{name}/instance-admin` | Admin area `users` | Removes the instance admin role. The last usable admin cannot be removed (`409`). |
| POST | `/api/identity/principals/{name}/password` | Instance admin | Resets the password of a user. |
| GET | `/api/identity/principals/{name}/nodes` | Instance admin | Lists the permission nodes of a principal, with an `ETag`. |
| PUT | `/api/identity/principals/{name}/nodes` | Instance admin | Replaces the whole node list of a principal. Accepts `If-Match`. |
| GET | `/api/identity/principals/{name}/tokens` | Admin area `tokens` | Lists the tokens of a principal. |
| POST | `/api/identity/principals/{name}/tokens` | Admin area `tokens` | Creates a token for a principal. The secret is returned once. |
| DELETE | `/api/identity/principals/{name}/tokens/{tokenName}` | Admin area `tokens` | Deletes a token of a principal. |

## Identity: teams

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| GET | `/api/identity/teams` | Admin area `teams` | Lists the teams with their member and grant counts. |
| POST | `/api/identity/teams` | Admin area `teams` | Creates a team. |
| GET | `/api/identity/teams/{name}` | Admin area `teams` | Returns a team with its members and grants. |
| PATCH | `/api/identity/teams/{name}` | Admin area `teams` | Changes the description or the external reference. |
| DELETE | `/api/identity/teams/{name}` | Admin area `teams` | Deletes a team with its memberships and grants. |
| PUT | `/api/identity/teams/{name}/members` | Admin area `teams` | Replaces the manual members. Synchronised members stay. |
| PUT | `/api/identity/teams/{name}/grants` | Admin area `teams` | Replaces all grants of a team. The role `ADMIN` is refused, grant it with nodes. |
| PUT | `/api/identity/teams/{name}/instance-admin` | Admin area `teams` | Grants the instance admin role to a team. |
| DELETE | `/api/identity/teams/{name}/instance-admin` | Admin area `teams` | Removes the instance admin role of a team. |
| GET | `/api/identity/teams/{name}/parents` | Admin area `teams` | Lists the parent teams, with an `ETag`. |
| PUT | `/api/identity/teams/{name}/parents` | Admin area `teams` | Replaces the parent teams. A cycle or a depth above 8 levels is refused (`409`). |
| GET | `/api/identity/teams/{name}/nodes` | Instance admin | Lists the permission nodes of a team, with an `ETag`. |
| PUT | `/api/identity/teams/{name}/nodes` | Instance admin | Replaces the whole node list of a team. |

## Identity: access explanation and node helpers

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| GET | `/api/identity/repositories/{repository}/access` | Instance admin | Explains who has which access on a repository, and why. |
| GET | `/api/identity/nodes/catalog` | Instance admin | Lists the nodes an operator can pick. The optional `prefix` filters the list. |
| GET | `/api/identity/nodes/templates` | Instance admin | Lists the node bundles that the dashboard offers. |

## Legacy tokens

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| GET | `/api/tokens` | Instance admin | Lists the legacy tokens with their permissions. |
| GET | `/api/tokens/{name}` | Token or session | Reads one token. Instance admins and the owner may read it. |
| PUT | `/api/tokens/{name}` | Instance admin | Creates or replaces a token. |
| PATCH | `/api/tokens/{name}` | Instance admin | Changes the description, permissions, routes or expiry. The secret stays. |
| POST | `/api/tokens/{name}/secret` | Instance admin | Regenerates the secret. The old one stops working. |
| DELETE | `/api/tokens/{name}` | Instance admin | Deletes a token. |

## Maven: repository information

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| GET | `/api/maven/repositories` | Optional token | Lists the repositories that the caller may see, sorted by id, with visibility, description and tags. Each entry also carries `versionPolicy` (`RELEASE`, `SNAPSHOT` or `MIXED`), `readOnly` (boolean) and `writePolicy` (`ALLOW`, `ALLOW_ONCE` or `READ_ONLY`, absent for a declared proxy or group). Older clients ignore the new fields. See [Repository settings](repository-settings.md#version-policy-and-read-only). |
| GET | `/api/maven/repositories/{repository}/members` | Optional token | Lists the repositories that a group references. |
| GET | `/api/maven/versions/{repository}/<gav>` | Optional token | Lists the versions of an artifact from its metadata. `filter` is a version prefix. `sorted` (default `true`) set to `false` keeps the raw order. |
| GET | `/api/maven/latest/version/{repository}/<gav>` | Optional token | Returns the latest version of an artifact. `filter` is a version prefix, and `sorted` works as for the versions. |
| GET | `/api/maven/latest/file/{repository}/<gav>` | Optional token | Returns the file of the latest version of an artifact. `extension` (default `jar`), `classifier` and `filter` select the file. |
| GET | `/api/badge/latest/{repository}/<gav>` | Optional token | Returns an SVG badge with the latest version. The parameters `name`, `color`, `prefix` and `filter` shape the badge. |
| POST | `/api/maven/generate/pom/{repository}/<gav>` | Path permission, deploy | Creates a stub POM for the GAV. Answers `201`. |
| GET | `/api/maven/storage` | Instance admin | Returns the storage usage of the repositories. |
| POST | `/api/maven/storage/refresh` | Instance admin | Recomputes the storage usage. |

## Search

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| GET | `/api/maven/search` | Optional token | Searches artifacts in the repositories the caller may browse. See [Search filters and API](search-filters-and-api.md). |
| GET | `/api/maven/search/files` | Optional token | Lists the files of one artifact version that a search found. |
| GET | `/api/maven/search/index` | Instance admin | Returns the state of the search index. |
| POST | `/api/maven/search/index/rebuild` | Instance admin | Starts a rebuild of the index from storage. |
| POST | `/api/maven/search/index/verify` | Instance admin | Starts a verify run. |
| POST | `/api/maven/search/index/runs/{id}/cancel` | Instance admin | Cancels a live index run. |

## Cleanup

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| GET | `/api/cleanup/runs` | Instance admin | Lists the cleanup runs. |
| POST | `/api/cleanup/policies/{id}/dry-run` | Instance admin | Previews what a cleanup policy would remove. |
| POST | `/api/cleanup/policies/{id}/run` | Instance admin | Runs a cleanup policy. |

## Maintenance: read-only

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| GET | `/api/admin/read-only` | Maintenance area | Reports whether the instance is read-only, and the source of the state: `environment` when `INGOT_READ_ONLY` forces it, `stored` otherwise. |
| POST | `/api/admin/read-only` | Maintenance area | Turns read-only on. Writes are refused with `503` on every replica that reads the state. Answers `409` while `INGOT_READ_ONLY` forces the state. |
| DELETE | `/api/admin/read-only` | Maintenance area | Turns read-only off again. Answers `409` while `INGOT_READ_ONLY` forces the state. |

A change is written to the [audit log](audit-events.md) and announced on the cluster bus when one is configured. See [Freeze writes for maintenance](../how-to/freeze-writes-for-maintenance.md).

## Migration: Nexus import

Available only when `INGOT_NEXUS_IMPORT_ENABLED` is `true`. Without it these paths answer `404`. Every route needs instance admin.

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| POST | `/api/migration/nexus/connection/test` | Instance admin | Probes a Nexus with a URL, a user and a password in the body. Answers the reachability, the version from the `Server` header, whether the credential is accepted and which areas it can read. A URL with user information answers `400`. The password is never returned. |

| POST | `/api/migration/nexus/discover` | Instance admin | Lists the Nexus repositories with their format and type, the component count and bytes of each hosted one, and a total per format. Reads only. |
| POST | `/api/migration/nexus/jobs` | Instance admin | Creates a job of kind `DRY_RUN`, `IMPORT` or `VERIFY` and answers `202` with its id. The body holds the URL, the user, the password (a literal or a `${env:NAME}` reference), optionally the repositories, the Ingot names per repository, the `conflictPolicy` (`refuse`, `abort` or `overwrite`), the `audit` mode, and for a verify job the `verifyImportJob`. The job runs at the next tick of an instance that may take it. |
| GET | `/api/migration/nexus/jobs` | Instance admin | Lists the jobs, newest first, with their status, owner and repository states. |
| GET | `/api/migration/nexus/jobs/{id}` | Instance admin | One job: status, pause reason, last error, the repositories with their state, the counts per file result and the summary of its plan. |
| POST | `/api/migration/nexus/jobs/{id}/pause` | Instance admin | Pauses a queued job at once, or a running job before its next file. Answers `204`. |
| POST | `/api/migration/nexus/jobs/{id}/resume` | Instance admin | Resumes a job the operator paused. Answers `204`. |
| POST | `/api/migration/nexus/jobs/{id}/cancel` | Instance admin | Cancels a job. A running one stops before its next file and keeps the files it copied. Answers `204`. |
| PUT | `/api/migration/nexus/jobs/{id}/credentials` | Instance admin | Supplies the password again for a job paused with `CREDENTIALS_NEEDED`, body `{"password": "..."}`. Answers `204`. |
| GET | `/api/migration/nexus/jobs/{id}/report` | Instance admin | The loss report of the job, as JSON or with `?format=csv`. |
| GET | `/api/migration/nexus/jobs/{id}/conflicts` | Instance admin | The files that differ from the target, with both checksums. |
| DELETE | `/api/migration/nexus/jobs/{id}` | Instance admin | Deletes a finished job with its rows. A job that is queued, paused or running answers `409`. Answers `204`. |

Every write answers `503` while read-only mode is on. No route takes a credential in the query string, and no route returns one.

## Database

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| POST | `/api/database/snapshots` | Maintenance area (`ingot.admin.maintenance`) | Writes a consistent snapshot of the SQLite database into `backups/` in the working directory while the instance runs. Answers with `fileName`, `sizeBytes` and `createdAt`, never with a path. Answers `409` for a database that is not SQLite or a name that is already taken. |

## Audit

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| GET | `/api/audit` | Instance admin | Lists audited events, newest first. The filters are actor, action, repository, text and time, and the results are paged with a cursor. |
| GET | `/api/audit/export` | Instance admin | Exports the events that match the same filters as CSV. |

## Settings

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| GET | `/api/settings/domains` | Instance admin | Lists the settings domains. |
| GET | `/api/settings/schema/{name}` | Instance admin | Returns the schema of a settings domain. |
| GET | `/api/settings/domain/{name}` | Instance admin | Reads a settings domain. |
| PUT | `/api/settings/domain/{name}` | Instance admin | Updates a settings domain. |
| GET | `/api/settings/domain/{name}?secrets=hidden` | Instance admin | Reads a settings domain with every secret replaced by `********`, whatever `hideSettingsSecrets` says. The dashboard uses this form. |
| GET | `/api/settings/encryption` | Instance admin | Reports the settings encryption mode, whether a key is active and how many stored secret values are still plain text. No value or key is returned. The System page warns from it. |
| POST | `/api/settings/export` | Instance admin | Returns the saved settings encrypted with a password of at least 12 characters (Argon2id and AES-256-GCM). Nothing is stored. The response carries `Cache-Control: no-store`. |
| POST | `/api/settings/import/open` | Instance admin | Decrypts an export with its password and returns the plain settings for review. Nothing is stored. A wrong password, a changed file and an unknown version all answer `400` with the same text. |

While `INGOT_SHARED_CONFIGURATION_RELOAD=true` and the shared settings come from a file, a `PUT` to a domain that the file holds answers `409`. The message names the file, nothing is written and no audit event is recorded. Change the file instead and let the instance pick it up. See [Shared settings reload](environment-variables.md#shared-configuration-reload).

## Overload answers

When the operator sets [`INGOT_HTTP_MAXCONCURRENT`](environment-variables.md#request-limit-variables), a request that arrives while that many requests are already in progress is answered at once with `503 Service Unavailable`. The answer carries `Retry-After: 1`, so a client can wait one second and send the request again. The body is the usual error object with `status` and `message`. A client that names `application/problem+json` with a higher quality than `application/json` gets the same body as problem details.

The refusal does not wait for a free slot, does not read the request body and does not touch the database. The liveness and readiness answers are never refused and do not count against the limit:

| Method | Path | Refused at the limit |
| --- | --- | --- |
| GET | `/api/status/health` | No |
| GET | `/api/status/ready` | No |

Downloads and uploads hold their slot until the last byte is transferred. Without the setting, no request is refused for concurrency.

## Status and statistics

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| GET | `/api/status/health` | None | Answers `200` with `UP`, or `503` with `DOWN`. |
| GET | `/api/status/instance` | Instance admin | Returns the status of the instance. |
| GET | `/api/status/snapshots` | Instance admin | Returns the latest status snapshots. |
| GET | `/api/status/failures` | Instance admin | Lists the recorded failures, newest first. |
| DELETE | `/api/status/failures` | Instance admin | Clears the recorded failures. |
| GET | `/api/statistics/resolved/all` | Instance admin | Returns the resolved-request statistics of all paths. |
| GET | `/api/statistics/resolved/unique` | Instance admin | Returns the number of unique resolved records. |
| GET | `/api/statistics/resolved/phrase/{limit}/{repository}/<gav>` | Path permission, read | Returns up to `{limit}` resolved-request entries whose path contains the phrase, highest count first, with their sum. `{limit}` must be a whole number from 1 to 100, otherwise the answer is `400`. A token sees only the entries under paths it may read, and the limit applies after that filter. A manager sees every entry. |

## Console

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| POST | `/api/console/execute` | Instance admin | Runs one console command and returns its output. |
| WebSocket | `/api/console/sock` | Instance admin | Interactive console over a WebSocket. |
| GET (SSE) | `/api/console/log` | Instance admin | Live log as server-sent events. |

The console, the WebSocket and the event stream accept a token or a browser session of an instance admin. 
## Frontend, javadoc and redirects

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| GET | `/api/frontend/settings` | None | Returns the presentation settings of the dashboard. |
| GET | `/api/frontend/plugin-ui` | Signed in | Returns the dashboard pages and settings sections that plugins contributed, filtered for the caller, with `version` 1 and the names of the enabled plugins. Answers `401` to an anonymous caller. The `stylesheets` list holds one entry per plugin with an accepted stylesheet, with its `plugin`, its `path` and its `sha256`, and is empty while `INGOT_LOCAL_PLUGINSTYLES=false`. See [Plugin API](../reference/plugin-api.md#dashboard-pages). |
| GET | `/api/frontend/plugin-styles/<plugin>/<hash>.css` | None | Returns the stylesheet of a plugin as `text/css; charset=utf-8`, wrapped in the layer `ingot-plugins`. `<hash>` is the first 16 hexadecimal characters of the SHA-256 of the bytes, and the answer is cached for a year with `immutable`. Answers `404` for an unknown plugin, an unknown hash, a path with more segments, or while the plugin styles are switched off. See [Plugin API](../reference/plugin-api.md#stylesheet). |
| GET | `/`, `/index.html` | None | Serves the dashboard page with `Cache-Control: no-cache` and `Cross-Origin-Opener-Policy: same-origin`. |
| GET | `/assets/<path>` | None | Serves the dashboard assets with `Cache-Control: public, max-age=31536000, immutable`. A missing file answers `404`. |
| GET | `/javadoc/{repository}/<gav>` | Optional token | Serves the javadoc of an artifact. Answers `404` while javadoc is disabled. |
| GET | `/javadoc/{repository}/<gav>/raw/<resource>` | Optional token | Serves one raw javadoc resource. Answers `404` while javadoc is disabled. |

When the redirect plugin is configured with a target repository, it adds GET and HEAD routes, one for each top-level entry of that target. They answer like the repository protocol below, for the target repository.

## Repository protocol

These paths serve the Maven repository layout. Clients use them directly.

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| GET, HEAD | `/{repository}/<gav>` | Optional token, with the visibility rules of the repository | Browses a directory or downloads a file. |
| PUT, POST | `/{repository}/<gav>` | Path permission, deploy | Deploys a file. |
| DELETE | `/{repository}/<gav>` | Path permission, delete | Deletes a file. |

A file can be deployed with any HTTP client that supports Basic authentication, with a token name and secret. Use the token that holds the deploy right on that path, see [Artifact permission nodes](artifact-permission-nodes.md).

The OpenAPI document at `/openapi` does not need a token. Checked against a running server, it answers `200` without credentials and with an access token. Ingot does not restrict it. To keep the document away from callers outside your network, block the path in the reverse proxy or gateway in front of Ingot.

For the shape of error answers, see [API errors](api-errors.md).

### Read-only refusals

While the instance is read-only, `PUT`, `POST`, `PATCH` and `DELETE` on the API and on the repository protocol answer `503` with a `Retry-After` header, and nothing is written. Authentication and authorization are checked first, so a caller without access still gets `401` or `403`. The exceptions are login, session refresh and the read-only toggle. `GET` and `HEAD` are served as before. See [Freeze writes for maintenance](../how-to/freeze-writes-for-maintenance.md).

## Contributing: keeping the snapshot current

The committed snapshot is `docs/openapi/ingot-openapi.json`. It is the document the server serves at `/openapi`, with sorted keys and the version field set to the placeholder `snapshot`, so a release does not change the file. The test `OpenApiSnapshotIntegrationTest` fails when the served document differs from the snapshot, and the pull request check compares the snapshot of the pull request with the one on `main`.

When a change alters the API on purpose, refresh the snapshot in the same pull request:

```
./gradlew :reposilite-backend:integration --tests 'com.reposilite.web.OpenApiSnapshotIntegrationTest' -PrefreshOpenApiSnapshot
```

Commit the rewritten file with the change. Its diff is the API change that reviewers read.

The pull request check fails when the change breaks the API. To declare an intended break, put `!` after the type or scope of the pull request title, for example `fix(api)!: drop the route`, and add a `BREAKING CHANGE:` footer to the commit. With the `!` the check only reports the changes and passes. Editing the title reruns the check without a new push. Adding an endpoint or an optional field does not need the marker.

Some changes the tool flags are accepted as non-breaking, for example a new value of an enum declared as `x-extensible-enum`. For those, add the exact message to `docs/openapi/breaking-changes-ignore.txt`: one line with the method, the path and the full message as oasdiff prints it, preceded by a comment line with the reason. The line must hold the full message, because a shorter one does not match and the check keeps failing. An entry only matches that exact change. The `!` marker stays the way to declare a real break.
