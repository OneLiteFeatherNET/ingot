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

### Configuration file

| Setting | Change |
| --- | --- |
| `configuration.cdn`, `hideSettingsSecrets` | Added with the value `false`. |
| Settings domains `audit`, `cleanup`, `login` | Added with their default values. |
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

| Variable | Behaviour |
| --- | --- |
| `INGOT_OPTS` | Startup arguments. `REPOSILITE_OPTS` is still read as a fallback. |
| `INGOT_LOCAL_*` | Local configuration. `REPOSILITE_LOCAL_*` is still read. If both are set, `INGOT_LOCAL_*` takes precedence. |
| `ingot.local.*` system properties | Local configuration. `reposilite.local.*` is still read. |
| `INGOT_FORCE_CHOWN` | Ownership fix at start. `REPOSILITE_FORCE_CHOWN` is still read. |
| `JAVA_OPTS`, `PUID`, `PGID` | Unchanged. |
