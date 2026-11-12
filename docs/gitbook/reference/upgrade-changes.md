---
description: Lookup tables for the upgrade to Ingot. Lists the supported upgrade paths, the changes of the first start in the database, the configuration and the tokens, and the environment variables.
---

# Upgrade changes and supported paths

## Supported upgrade paths

| From | To | Storage | Container test |
| --- | --- | --- | --- |
| Reposilite 3.5.28 | First Ingot release with the identity model | SQLite (default) | `UpgradeScenario`, with switch and rollback |
| Reposilite 3.5.28 | First Ingot release with the identity model | MariaDB 11.4 | `MariaDbUpgradeTest`, with switch and rollback |
| Ingot 1.1.0 | First Ingot release with the identity model | SQLite (default) | `PreviousIngotMigrationTest`, with switch and rollback |
| Reposilite 2.x | Ingot, tokens only, through `migration-plugin` and `token-import` | 2.x data files | None |

## What the first start changes

### Database tables

The identity schema is created on the first start. The legacy token tables are kept and only read.

| Table | Content |
| --- | --- |
| `principal` | Users and machine accounts. Each migrated token gets one machine principal. |
| `principal_token` | Token name, secret hash and expiry date. |
| `team`, `team_member`, `team_grant` | Teams, their members and their grants. Each machine principal has a private team that holds its migrated routes. |
| `instance_role` | Instance roles. A migrated manager token gets the instance admin role. |
| `permission_node`, `team_parent` | Permission nodes and team hierarchy. |
| `local_credential`, `refresh_token`, `login_attempt`, `login_setup_marker`, `user_identity` | Login tables. |
| `identity_migration` | One row per migration step, so each step runs once. |
| `identity_legacy_import` | One row per legacy token that has been imported, keyed by the legacy id. Rows are never deleted. |
| `access_token`, `permission_access_token`, `permission_route` | Legacy token tables. Kept unchanged. Ingot reads them and never writes them. |
| `scheduled_job_claim` | Claims of the scheduled cleanup and audit purge. The nullable columns `lease_owner`, `lease_until` and `done_at` are added on the first start. Existing rows keep their values and stay valid. |

### Search finds words of the coordinates

The search index keeps the words of each group, artifact and version in a table named `search_token`, and a column `gav_key` is added to `search_artifact`. The first start only adds them. Nothing is renamed, dropped or rewritten, and the marker `schema-2` in `search_state` records the step.

A plain word of a search now also matches a part of a name that starts after a separator. `core` finds `spring-core`, so **searches can return groups they did not return before**. Every group that matched before still matches. Rows indexed before the upgrade have no word tokens until a background sweep fills them in; the sweep runs on the first tick after the start and then every six hours, and the search answer reports `tokensComplete: false` and the state `BUILDING` until it has finished. A rollback to the previous version works without a step; the previous version ignores the new column and the table.

### Search backends (experimental)

A search backend can be supplied by a plugin, see [Write a search backend plugin](../how-to/write-a-search-backend-plugin.md). The interfaces are experimental: they live in `net.onelitefeather.ingot.search.backend` and carry `@ExperimentalIngotSearchApi`, and their members may change before the API is declared stable. Nothing changes for an installation that keeps `searchEngine` at its default `database`.

One answer changes on purpose. A `searchEngine` value that no backend has registered used to switch search off, with `404` on every search route. It now answers `503` with `Retry-After` and a problem body that names the value, and the `backend` block of the status shows the reason. An installation that runs such a value sets `searchEngine` to `database`, or to the id of the backend it runs.

The status of `GET /api/maven/search/index` gains a `backend` block. The existing fields keep their meaning. See [Search filters and API](search-filters-and-api.md#the-backend-block).

No configuration key, environment variable, volume, manifest, image or plugin has to change.

### Configuration file

| Setting | Change |
| --- | --- |
| `configuration.cdn`, `hideSettingsSecrets` | Added with the value `false`. |
| `configuration.cdn`, `searchEnabled` and `searchEngine` | Added with the values `true` and `database`. Older versions ignore them after a rollback. |
| Settings domain `search` | Added with its default values. |
| Settings domains `audit`, `cleanup`, `login` | Added with their default values. |
| `authentication.ldap` | Keeps working for the dashboard login with no change while `login.ldap` is off, and `login.ldap` is the new place for it and takes precedence once enabled. |
| Other settings | Values unchanged. Header comments may differ, because both products rewrite the file. |

### Tokens

| Legacy item | Result in Ingot |
| --- | --- |
| Persistent token | Machine principal with the same name. If another principal differs only in case, the name gets the token id appended, and the token keeps its exact name. |
| Secret | The stored hash is copied unchanged. Clients keep the secret they use. |
| Expiry date | Kept. |
| Manager permission | Instance admin role. |
| Read route | Grant `LEGACY_READ` on the private team, with the same path. |
| Write route | Grant `LEGACY_WRITE` on the private team, with the same path. It also allows delete. |
| Other route permission | Skipped. The log has a warning. |
| Temporary token from `--token` | Not stored and not migrated. Pass it again as before. |
| Statistics from Reposilite 2.x | Not imported. |

Tokens created in Ingot have the form `ingot_<tokenId>_<random>`. The secret is stored as a bcrypt hash.

### Environment variables

The `INGOT_` names are read first. Where a `REPOSILITE_` name exists, it is read as a fallback. When both are set, the `INGOT_` name wins. The full list of variables is in [Environment variables](environment-variables.md).

* `INGOT_OPTS`, `INGOT_FORCE_CHOWN` and `INGOT_LOCAL_<KEY>` fall back to `REPOSILITE_OPTS`, `REPOSILITE_FORCE_CHOWN` and `REPOSILITE_LOCAL_<KEY>`.
* The `ingot.local.*` system properties fall back to `reposilite.local.*`.
* `JAVA_OPTS`, `PUID` and `PGID` have no prefix and are unchanged.

### Cookie requests from other sites

A request to the API that carries the session or refresh cookie is now refused with `403` when it comes from another site or from a sibling subdomain. The body is the same error response as the CSRF refusal. Requests with an `Authorization` header, top-level navigations and same-origin requests are not affected. Maven, Gradle, sbt and token clients send no cookie and are not affected either.

The documented deployments serve the dashboard and its API from one origin, so no change is needed. A dashboard served from another site than its API, or from a sibling subdomain of it, needs `INGOT_FETCH_METADATA=off`. See [Environment variables](environment-variables.md#server-variables) and [Cross-site requests](../explanation/sessions-and-login.md#cross-site-requests).

### Cache-Control of access-controlled files

Files of `PRIVATE` and `HIDDEN` repositories, and files that a group serves from a private member, are now answered with `Cache-Control: private, max-age=<seconds>` instead of `public`. A shared cache in front of Ingot, such as a CDN or a company proxy, no longer stores those answers, so a file downloaded with credentials is not handed to other clients. Maven, Gradle and the dashboard ignore the header, and status codes, bodies and `ETag` values stay the same. Refused `401` answers for files carry `no-cache, no-store, max-age=0`.

No configuration change is needed. To restore the old `public` value for these answers, set `INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC=true`. Do that only when the cache in front of Ingot may keep access-controlled files for other clients. See [Environment variables](environment-variables.md#maven-cache-control).

### Mirror extensions

The default `allowedExtensions` of a mirrored repository now includes `.sigstore.json`. A stored list is kept on upgrade and is not rewritten. To let a mirror with a stored list serve Sigstore bundles, add `.sigstore.json` to its `allowedExtensions` in the settings.

### Phrase statistics limit

The phrase endpoint `GET /api/statistics/resolved/phrase/{limit}/{repository}/<gav>` answered with one entry whatever `{limit}` was. It now returns up to `{limit}` entries, and its `sum` covers those entries.

| Request | Before | Now |
| --- | --- | --- |
| `{limit}` from 1 to 100 | One entry | Up to `{limit}` entries |
| `{limit}` of 0, above 100, or not a number | Data, one entry, the limit was ignored | `400` with an error response, no lookup |
| Token that cannot read every matching path | One entry, possibly unreadable | Only the readable entries, then the limit |

Clients that always send `1` see no difference. A client that sends a limit outside 1 to 100 has to send a valid one. No configuration, environment variable or plugin changes.

### Cleanup and audit job leases

The scheduled cleanup and the audit retention purge hold their daily run as a lease in the database, and a cleanup run holds a lease for its whole duration.

| Behaviour | Before | Now |
| --- | --- | --- |
| Manual run while a run of the same policy is live on another instance | Started, and could delete the same versions at the same time | Answers `409` and deletes nothing |
| Scheduled run whose instance stopped before the run ended | The day was lost, and no instance ran it | A later check on the same UTC day takes the day over after 60 seconds, and runs it once |
| Scheduled run that was not started at its minute | Not run | Still not run. Ingot does not catch up later |

No configuration, environment variable, volume or plugin changes. The schedule itself does not move.

**Mixed versions.** A replica on the previous release ignores the leases, so during a rolling upgrade it can still run a cleanup next to a replica on this release. Upgrade one replica first, and enable no scheduled cleanup policy until every replica runs this release. The rule for the pair of releases and the tests behind it are in [High availability support](../explanation/high-availability-support.md#rolling-upgrade-rule).

### Uploads are written beside their target

An upload is written to a temporary file in the directory of its target, named `.ingot-<random>.part`, and moved into place with one atomic move. Before, the temporary file lived in the system temporary directory. Three points are visible to an operator:

* An upload needs free space on the repository volume while it is written. It no longer needs space in the temporary directory of the container.
* A request that stores a file under a name of the form `.ingot-*.part` is refused with `400`. No Maven artifact has such a name.
* A partial file left by a crash is removed when the storage starts, once it has not been modified for 24 hours. Newer partial files stay, because another replica on the same volume may still be receiving them.

Partial files are never listed, counted in the storage usage or served. After a rollback to an earlier release on the same volume, that release lists a partial file until the upload completes or the file is removed. Status codes and bodies of uploads and downloads do not change, apart from the `400` above. No configuration, environment variable, manifest or plugin changes, and a data directory is adopted as before.

### S3 uploads no longer use the temporary directory

An S3 upload is sent to the bucket as it arrives. An upload above 8 MiB is a multipart upload, and no copy of it is written to the system temporary directory of the container. The stored objects are unchanged: the same keys, content types and bytes, and a repository reads them as before. No configuration, environment variable, manifest or plugin changes. Operators can drop any sizing that was made for the temporary directory.

* A failed upload is aborted, so the bucket keeps no incomplete upload from it. An upload that is interrupted by a killed process leaves parts in the bucket. Add the `AbortIncompleteMultipartUpload` lifecycle rule from [Large uploads](../how-to/use-s3-storage.md#large-uploads) to remove them.
* `INGOT_S3_BUFFER_TO_DISK=true` restores the temporary file path for every upload. Use it only when the storage service rejects multipart uploads.

### Nexus import is bundled and off by default

The image ships `nexus-import-plugin.jar` in `/app/bundled-plugins/`. It is loaded only when `INGOT_NEXUS_IMPORT_ENABLED=true` (or `REPOSILITE_NEXUS_IMPORT_ENABLED=true` when the `INGOT_` name is unset). With the switch off, no route exists and no table is created, so an existing installation behaves as before. No configuration, environment variable, manifest or plugin changes for existing deployments.

* When the switch is on, the importer creates two tables, `nexus_import_job` and `nexus_import_asset`, in the database that Ingot already uses. Their names do not collide with existing tables.
* The loader now switches each bundled jar on its own variable. A jar that the loader does not know still follows the metrics switch, as before.
* The audit action `NEXUS_IMPORT_RUN` is new. Its name is stored, so it is never renamed.

See [Import from Nexus](../how-to/import-from-nexus.md) for the flow.

### OpenSearch search backend is bundled and off by default (unreleased)

The image ships `opensearch-plugin.jar` in `/app/bundled-plugins/`. It is loaded only when `INGOT_OPENSEARCH_ENABLED=true` (or `REPOSILITE_OPENSEARCH_ENABLED=true` when the `INGOT_` name is absent). With the switch off nothing is loaded and no connection is made, so an existing installation behaves as before. No configuration, environment variable, volume, manifest or plugin changes for existing deployments.

* The search backend stays `database` until `searchEngine` is set to `opensearch` on every replica. Set the switch first and the search backend second, as described in [Use OpenSearch for search](../how-to/use-opensearch-for-search.md).

See [OpenSearch search backend](opensearch-search-backend.md) for the variables and the security obligations of the cluster.

### The default colour is steel blue

An installation without a `seedColor` looks steel blue instead of Reposilite magenta. To keep the previous look, set `frontend.appearance.seedColor` to `#ac1c67` (see [Settings domains](settings-domains.md)). No configuration file, environment variable, volume or plugin changes.
