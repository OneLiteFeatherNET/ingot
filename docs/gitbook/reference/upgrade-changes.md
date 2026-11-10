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

**Mixed versions.** A replica on the previous release ignores the leases, so during a rolling upgrade it can still run a cleanup next to a replica on this release. Upgrade one replica first, and enable no scheduled cleanup policy until every replica runs this release.
