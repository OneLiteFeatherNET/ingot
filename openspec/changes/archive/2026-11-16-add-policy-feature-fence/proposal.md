# Proposal

## Open maintainer questions

1. **Can an operator override the refusal?** Default: no override. A database that holds a feature the running build does not know is started by the newer build or restored from a backup. An escape hatch such as `INGOT_POLICY_FEATURES=ignore` would bring back the silent drop the fence exists to stop.
2. **Is the fence stored in the migration markers?** Default: no. It uses its own `policy_feature` table, so `identity_migration` keeps its meaning.
3. **Does the first release with the fence register any feature?** Default: no. The known set is empty, so the fence refuses only a database written by a newer build that has features. Changes 8 to 10 register their features.

## Why

Restrictions, namespaces and signing policies written by a future release would be silently ignored by an older build. A rollback during an incident would then drop every restriction without a warning. Today there are no such rows, so nothing is at risk yet. The fence is the cheap precondition: a build that meets an unknown feature refuses to start instead of ignoring it.

The fence protects builds from this release onward. Builds older than this release ignore unknown tables, so a rollback below this release stays unsupported, and the operator documentation says so.

## What Changes

- A new table `policy_feature` (name, enabled time) is created with the other identity tables on every supported database. A fresh installation has no rows.
- At startup, before the legacy access token migration and the permission node migration run (`AccessTokenComponents`), the server reads the feature names and compares them with the names this build knows. An unknown name stops startup and logs an error that names the feature and the running version.
- The known set is empty in this change.
- The operator documentation states the downgrade rule.

## Capabilities

### New Capabilities

- `identity-policy-feature-fence`: the table of policy features, the startup comparison and its refusal.

## Impact

- **Operators:** no configuration key, environment variable or manifest change. A new empty table appears in the identity database. An existing installation starts exactly as before. A database written by a newer build that has features is refused by this build, and the refusal names the features. Nothing here is BREAKING, because no build that exists today writes features.
- **Code:** `reposilite-backend`, in `net.onelitefeather.ingot.identity.storage` and at the startup of the access token components.
- **Docs:** one note in `docs/gitbook/explanation/identity-model.md` on the downgrade rule.
- **Commit:** `feat(identity)` for the table and the check. Pull request title, squash-merged onto main:

  `feat(identity): refuse to start on unknown policy features`

### Out of scope

- Registering any feature, and any table that uses features.
- Rollback to builds older than this release.
- An operator override of the refusal.
