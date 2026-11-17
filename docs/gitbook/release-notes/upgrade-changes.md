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

Settings encryption adds one configuration row, `settings-key`, which holds the key identifier and a check value and no key. It is written only when a settings key is configured. Without a key, the default `INGOT_SETTINGS_ENCRYPTION=optional` keeps every stored value, the start and the settings API as they were. With a key, the first start writes the stored secrets sealed once. See [Settings encryption](../references/settings/settings-encryption.md).

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

A search backend can be supplied by a plugin, see [Write a search backend plugin](../manage/plugins/write-a-search-backend-plugin.md). The interfaces are experimental: they live in `net.onelitefeather.ingot.search.backend` and carry `@ExperimentalIngotSearchApi`, and their members may change before the API is declared stable. Nothing changes for an installation that keeps `searchEngine` at its default `database`.

One answer changes on purpose. A `searchEngine` value that no backend has registered used to switch search off, with `404` on every search route. It now answers `503` with `Retry-After` and a problem body that names the value, and the `backend` block of the status shows the reason. An installation that runs such a value sets `searchEngine` to `database`, or to the id of the backend it runs.

The status of `GET /api/maven/search/index` gains a `backend` block. The existing fields keep their meaning. See [Search filters and API](../references/http-api/search-filters-and-api.md#the-backend-block).

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

The `INGOT_` names are read first. Where a `REPOSILITE_` name exists, it is read as a fallback. When both are set, the `INGOT_` name wins. The full list of variables is in [Environment variables](../references/configuration/environment-variables.md).

* `INGOT_OPTS`, `INGOT_FORCE_CHOWN` and `INGOT_LOCAL_<KEY>` fall back to `REPOSILITE_OPTS`, `REPOSILITE_FORCE_CHOWN` and `REPOSILITE_LOCAL_<KEY>`.
* The `ingot.local.*` system properties fall back to `reposilite.local.*`.
* `JAVA_OPTS`, `PUID` and `PGID` have no prefix and are unchanged.

### Linking a team to a provider group needs the idp area

Writes that set, change or clear the external reference of a team now need the admin area `ingot.admin.idp`, or `ingot.admin.*`. A token that holds only `ingot.admin.teams` gets 403 for such writes. Grant `ingot.admin.idp` to that token before the upgrade. As a temporary fallback, set `INGOT_IDENTITY_IDP_GUARD=warn`, then remove it once the grant is in place. Existing references, memberships and read-only calls are not changed by the upgrade. The same rule covers the identity fields of the `login` domain: a settings write that changes a provider's issuer, client credentials, claims or admin groups, or the LDAP connection or admin groups, needs `ingot.admin.idp` too. A settings token with only `ingot.admin.settings` can still change every other login setting, such as the session lifetimes.

Mixed-version rolling upgrades are not guarded until every replica runs this release. An old replica accepts the writes that a new one refuses, because it has no guard, and the guard applies to the replica that serves the request. Upgrade all replicas before you grant or remove rights that depend on the guard, or stop the writes of automation until the upgrade is complete. See [Map provider groups to teams](../configure/authentication/map-provider-groups-to-teams.md) and [Environment variables](../references/configuration/environment-variables.md#identity-variables).

### Second factors for dashboard sign-in are optional (unreleased)

Dashboard sign-in can ask for a second factor: an authenticator app, a passkey or recovery codes. No operator action is needed. Nothing changes until an administrator sets a policy or a user enrols a factor. Existing configuration, volumes, manifests and plugins stay as they are, and access tokens, Maven, Gradle and CI keep working without a prompt.

* **No new required setting.** The `login` domain gains the object `mfa`, with every policy switch off, and each OpenID Connect provider gains the lists `mfaAcrValues` and `mfaAmrValues`, which are empty. Both keep their defaults until you change them.
* **The settings key is needed for authenticator codes only.** An authenticator app stores its seed encrypted, so it needs `INGOT_SETTINGS_KEY` or `INGOT_SETTINGS_KEY_FILE`. Without the key, the enrolment is refused with the reason. Passkeys and recovery codes need no key. Keep the key for as long as users hold authenticator codes, and see the rotation note in [Encrypt the settings secrets](../manage/operate/encrypt-settings-secrets.md).
* **New tables and a column, added at the first start.** The tables `mfa_factor`, `mfa_recovery_code` and `auth_challenge`, and the column `required_since` of `principal`, are created. Existing rows are not changed. A rollback to the previous release leaves them in place and ignores them, so the second factor is not checked after a rollback.
* **External sign-ins follow the policy only.** A user that the policy does not cover signs in as before, also when the user holds a factor. A covered user with a factor is asked for the factor, unless the provider has verified one through the trusted values of `mfaAcrValues` or `mfaAmrValues`. A covered GitHub user with a factor is challenged, because GitHub issues no ID token and no trusted value applies.
* **Restricted sessions after a grace period.** The policy is per user, and its grace period starts at the first sign-in after the policy covers the user. After it ends, a covered user without a factor gets a session that can only enrol a factor. Nothing is revoked by the server. Live sessions get the new scope at their next refresh.
* **Access token rule.** Creating, rotating or deleting an own token of a user with a factor needs a browser session with a fresh second factor. A token caller gets `403`. Tokens that already exist are not changed.

The dashboard has no second factor screen yet. Until that change lands, enrolment and the second factor are done through the API, as described in [Configure second factors](../configure/authentication/configure-second-factors.md).

See [Configure second factors](../configure/authentication/configure-second-factors.md) and [Recover admin access](../manage/access/recover-admin-access.md) before you set a policy.

### Cookie requests from other sites

A request to the API that carries the session or refresh cookie is now refused with `403` when it comes from another site or from a sibling subdomain. The body is the same error response as the CSRF refusal. Requests with an `Authorization` header, top-level navigations and same-origin requests are not affected. Maven, Gradle, sbt and token clients send no cookie and are not affected either.

The documented deployments serve the dashboard and its API from one origin, so no change is needed. A dashboard served from another site than its API, or from a sibling subdomain of it, needs `INGOT_FETCH_METADATA=off`. See [Environment variables](../references/configuration/environment-variables.md#server-variables) and [Cross-site requests](../references/architecture/sessions-and-login.md#cross-site-requests).

### Cache-Control of access-controlled files

Files of `PRIVATE` and `HIDDEN` repositories, and files that a group serves from a private member, are now answered with `Cache-Control: private, max-age=<seconds>` instead of `public`. A shared cache in front of Ingot, such as a CDN or a company proxy, no longer stores those answers, so a file downloaded with credentials is not handed to other clients. Maven, Gradle and the dashboard ignore the header, and status codes, bodies and `ETag` values stay the same. Refused `401` answers for files carry `no-cache, no-store, max-age=0`.

The same applies to a proxy or group whose remote mirror has `authorization`: a shared cache no longer stores its files, because the upstream would not hand them to an anonymous client. Maven, Gradle and the dashboard are not affected.

No configuration change is needed. To restore the old `public` value for these answers, set `INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC=true`. Do that only when the cache in front of Ingot may keep access-controlled files for other clients. See [Environment variables](../references/configuration/environment-variables.md#maven-cache-control).

Directory listings are not cacheable either, in HTML and in the JSON listings under `/api/maven/details`. They answer `Cache-Control: no-cache, no-store, max-age=0` and `Vary: Authorization, Cookie`, because a listing shows the caller only what the caller may read. Artifacts are unchanged. No client depends on a shared cache storing listings.

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
| Scheduled run that was not started at its minute | Not run | Still not run. Ingot does not catch up later, unless `INGOT_CLEANUP_CATCHUP=true` is set. With that variable it is made up within six hours, once per UTC day |

No configuration, environment variable, volume or plugin changes. The schedule itself does not move. The lease table `scheduled_job_claim` gets one column, `lease_epoch`, which is added at start and is `0` for every existing row. Each grant of a lease raises it, so a replica that lost its lease cannot renew or release the grant of the replica that took over.

**Mixed versions.** A replica on the previous release ignores the leases, so during a rolling upgrade it can still run a cleanup next to a replica on this release. Upgrade one replica first, and enable no scheduled cleanup policy until every replica runs this release. The rule for the pair of releases and the tests behind it are in [High availability support](../references/architecture/high-availability-support.md#rolling-upgrade-rule).

### Uploads are written beside their target

An upload is written to a temporary file in the directory of its target, named `.ingot-<random>.part`, and moved into place with one atomic move. Before, the temporary file lived in the system temporary directory. Three points are visible to an operator:

* An upload needs free space on the repository volume while it is written. It no longer needs space in the temporary directory of the container.
* A request that stores a file under a name of the form `.ingot-*.part` is refused with `400`. No Maven artifact has such a name.
* A partial file left by a crash is removed when the storage starts, once it has not been modified for 24 hours. Newer partial files stay, because another replica on the same volume may still be receiving them.

Partial files are never listed, counted in the storage usage or served. After a rollback to an earlier release on the same volume, that release lists a partial file until the upload completes or the file is removed. Status codes and bodies of uploads and downloads do not change, apart from the `400` above. No configuration, environment variable, manifest or plugin changes, and a data directory is adopted as before.

### S3 uploads no longer use the temporary directory

An S3 upload is sent to the bucket as it arrives. An upload above 8 MiB is a multipart upload, and no copy of it is written to the system temporary directory of the container. The stored objects are unchanged: the same keys, content types and bytes, and a repository reads them as before. No configuration, environment variable, manifest or plugin changes. Operators can drop any sizing that was made for the temporary directory.

* A failed upload is aborted, so the bucket keeps no incomplete upload from it. An upload that is interrupted by a killed process leaves parts in the bucket. Add the `AbortIncompleteMultipartUpload` lifecycle rule from [Large uploads](../set-up/cluster/use-s3-storage.md#large-uploads) to remove them.
* `INGOT_S3_BUFFER_TO_DISK=true` restores the temporary file path for every upload. Use it only when the storage service rejects multipart uploads.

### Nexus import is bundled and off by default

The image ships `nexus-import-plugin.jar` in `/app/bundled-plugins/`. It is loaded only when `INGOT_NEXUS_IMPORT_ENABLED=true` (or `REPOSILITE_NEXUS_IMPORT_ENABLED=true` when the `INGOT_` name is unset). With the switch off, no route exists and no table is created, so an existing installation behaves as before. No configuration, environment variable, manifest or plugin changes for existing deployments.

* When the switch is on, the importer creates two tables, `nexus_import_job` and `nexus_import_asset`, in the database that Ingot already uses. Their names do not collide with existing tables.
* The loader now switches each bundled jar on its own variable. A jar that the loader does not know still follows the metrics switch, as before.
* The audit action `NEXUS_IMPORT_RUN` is new. Its name is stored, so it is never renamed.

See [Sonatype Nexus importer](../set-up/migrate/import-from-other-repository-managers.md#sonatype-nexus-importer) for the flow.

### OpenSearch search backend is bundled and off by default (unreleased)

The image ships `opensearch-plugin.jar` in `/app/bundled-plugins/`. It is loaded only when `INGOT_OPENSEARCH_ENABLED=true` (or `REPOSILITE_OPENSEARCH_ENABLED=true` when the `INGOT_` name is absent). With the switch off nothing is loaded and no connection is made, so an existing installation behaves as before. No configuration, environment variable, volume, manifest or plugin changes for existing deployments.

* The search backend stays `database` until `searchEngine` is set to `opensearch` on every replica. Set the switch first and the search backend second, as described in [Use OpenSearch for search](../configure/search/use-opensearch-for-search.md).

See [OpenSearch search backend](../references/settings/opensearch-search-backend.md) for the variables and the security obligations of the cluster.

### Storage behaviour follows interfaces, and the support bundle gains details

Core now decides storage behaviour through the interfaces of `net.onelitefeather.ingot.storage.capability` instead of the classes of the built-in providers. A configured `fs` or `s3` repository behaves as before, with the same usage figures, quota, conflict messages and readiness. No configuration, environment variable, volume, manifest or plugin changes for existing deployments.

* The support bundle entry of each repository storage gains a `details` map. `region` keeps its meaning.
* A usage figure of a repository without settings on this replica reports the storage type `unknown` instead of `fs`. A configured repository reports its real type.
* The cluster usage event carries the storage type of each figure. A replica of the previous version reads a figure without it as `unknown`, and such a replica still decodes the new event.
* The package `net.onelitefeather.ingot.storage.capability` is experimental. `com.reposilite.storage.s3.RelatedKeyAware` stays, deprecated, so plugins compiled against it still link.

### Meilisearch search backend is an installable plugin (unreleased)

The Meilisearch backend is not in the image. Install `meilisearch-plugin.jar` into the plugins directory, which is `/app/data/plugins` in the container image, and nothing is loaded before that. A plugin in that directory is loaded at every start, so the jar is the switch and no `INGOT_` enable variable exists for it.

* The search backend stays `database` until `searchEngine` is set to `meilisearch` on every replica. Install the jar first and set the search backend second, as described in [Use Meilisearch for search](../configure/search/use-meilisearch-for-search.md).
* An installation without the jar sees no change, and no configuration, volume or manifest changes for existing deployments.

See [Meilisearch search backend](../references/settings/meilisearch-search-backend.md) for the variables, the named exceptions and the operator obligations.

### Elasticsearch search backend is optional and not bundled (unreleased)

The Elasticsearch plugin is not part of the image. It is installed by hand, or from the plugin store, by copying `elasticsearch-plugin.jar` into the plugins directory. Without the jar nothing is loaded and no connection is made, so an existing installation behaves as before. No configuration, environment variable, volume, manifest or existing plugin changes for existing deployments.

* The search backend stays `database` until `searchEngine` is set to `elasticsearch` on every replica. Install the jar first and set the search backend second, as described in [Use Elasticsearch for search](../configure/search/use-elasticsearch-for-search.md).

See [Elasticsearch search backend](../references/settings/elasticsearch-search-backend.md) for the variables, the privileges of the API key and the security obligations of the cluster.

### Plugin tiers are shown on start (unreleased)

Every loaded plugin now has a tier: Core, Official, Community or Experimental. The existing startup line that lists the loaded plugins is unchanged, byte for byte. One additional INFO line follows it and lists each plugin with its tier, for example `checksum (official)`. Each Experimental plugin also logs one WARN line at start, which names the plugin and says that its API may change. No configuration, environment variable, volume, manifest or plugin changes for existing deployments. No plugin is refused, skipped or started in another order because of its tier.

* A parser that matches the existing line sees no difference. A parser that matches every INFO line sees one new line directly after it.
* An installation with `INGOT_OPENSEARCH_ENABLED=true`, or with an Experimental jar in the plugins directory, sees one WARN line for each such plugin.
* The system information page shows the tier next to each plugin, and the support bundle file `plugins.json` gains a `tier` field per plugin. No existing field changes.

See [Plugin tiers](../references/plugins/plugin-tiers.md) for the four tiers and the rules that give a plugin its tier.

### The default colour is steel blue

An installation without a `seedColor` looks steel blue instead of Reposilite magenta. To keep the previous look, set `frontend.appearance.seedColor` to `#ac1c67` (see [Settings domains](../references/settings/settings-domains.md)). No configuration file, environment variable, volume or plugin changes.

### Lucene search backend is optional and not bundled (unreleased)

The Lucene plugin is not part of the image. It is installed by hand, or from the plugin store, by copying `lucene-plugin.jar` into the plugins directory. Without the jar nothing is loaded and no index is written, so an existing installation behaves as before. No configuration, environment variable, volume, manifest or existing plugin changes for existing deployments.

* The search backend stays `database` until `searchEngine` is set to `lucene` on the one replica. The backend serves exactly one replica; more than one replica must not use it. Read [Use Lucene for search](../configure/search/use-lucene-for-search.md) before you switch.

See [Lucene search backend](../references/settings/lucene-search-backend.md) for the variables and the single-writer rule.

### Storage cache type is new and optional (unreleased)

The storage type `cache` is new. It keeps copies of immutable files on the disk of the node in front of another storage, see [Cache provider](../references/repositories/storage-providers.md#cache-provider). It is provided by the storage cache plugin, which is not part of the image and is installed as a jar in the `plugins` folder. No existing configuration changes: a repository uses the cache only when its storage block has `"type": "cache"`, and the file system and S3 providers keep their settings and defaults.

### Azure Blob storage is new and optional (unreleased)

The `azure-blob` provider arrives as an optional plugin. No existing storage setting, default, environment variable, configuration file,
volume or database table changes. A repository that uses `fs` or `s3` behaves as before, whether or not the Azure plugin is installed.
