---
description: Overview of the REST endpoints of Ingot grouped by area, with the method, path, authentication requirement and purpose of each. Covers login, identity, legacy tokens, Maven and search APIs, status, audit, settings, console and the repository protocol.
---

# HTTP API overview

This page lists the HTTP endpoints of Ingot by area. For the request and response schemas, use the OpenAPI document that the server publishes at `/openapi`. The document is generated from the same route definitions as this list. Some endpoints have no OpenAPI annotation, so this page is the complete list.

Paths in braces are path parameters. `<gav>` stands for the rest of a Maven path, such as `net/onelitefeather/mycelium/1.0.0/`.

## Authentication requirements

| Label | Meaning |
| --- | --- |
| None | Anyone may call the endpoint. |
| Optional token | Anonymous calls are allowed. A token or session changes what is visible. |
| Token or session | A valid token or a signed-in session is required. |
| Refresh cookie | The refresh cookie of a browser session is required. |
| Path permission | A token or session that may perform the action on the path. Instance admins pass. The action is read for GET and HEAD, deploy for PUT and POST, and delete for DELETE. |
| Instance admin | The caller holds `ingot.admin.*` (or `*`). Token narrowing applies. |
| Admin area `<area>` | The caller holds `ingot.admin.<area>` (or `ingot.admin.*`). Token narrowing applies. Only the areas `users`, `tokens` and `teams` are checked this way. |

Wrong or missing credentials answer with `401`. A valid caller without the right answers with `403`. Read [Permission nodes](permission-nodes.md) and [Access tokens and routes](access-tokens-and-routes.md) for what the rights are.

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
| GET | `/api/maven/repositories` | Optional token | Lists the repositories that the caller may see, sorted by id, with visibility, description and tags. |
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
| GET | `/api/statistics/resolved/phrase/{limit}/{repository}/<gav>` | Path permission, read | Returns the resolved-request statistics of one path. |

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
| GET | `/`, `/index.html` | None | Serves the dashboard page. |
| GET | `/assets/<path>` | None | Serves the dashboard assets. |
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

{% hint style="warning" %}
The OpenAPI document at `/openapi` is produced by the OpenAPI plugin that the server registers. Whether that document needs a token was not checked against a running instance. Check it there before you publish the path to a client.
{% endhint %}
