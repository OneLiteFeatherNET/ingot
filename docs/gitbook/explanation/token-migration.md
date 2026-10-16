---
description: Explains how legacy access tokens become machine accounts in Ingot, why an import ledger exists, why a rollback loses Ingot-only data, and why the old tables are kept.
---

# Token migration

This page explains the design of the token migration. For the steps, see [Upgrade from Reposilite 3.x](../how-to/upgrade-from-reposilite.md). For the exact mapping of each item, see [Upgrade changes and supported paths](../reference/upgrade-changes.md).

## Why tokens become machine accounts

Ingot records every actor as a principal, and rights belong to principals and teams. A persistent token is a credential that a script or a build uses, so it becomes a machine principal. The principal has the token's name and its own private team, and the private team holds the token's routes as grants. Every migrated token is visible in the principal list and takes part in the same rights model as the rest.

The secret is not reissued. The hash that Reposilite stored is copied unchanged, so a client keeps the secret it already has and does not need a change.

## Why routes keep their old matching

Reposilite 3.x matches a route by prefix, without a segment boundary. A route `/rel` therefore also covers `/releases`. A migrated grant keeps this behaviour, so the answers of the instance stay the same after the switch. Grants created in Ingot use a segment boundary and are case-sensitive. The two kinds of grant are kept apart by their role, so the old matching cannot spread to new grants.

## Why the migration is one transaction

The migration copies every legacy token inside one database transaction. Each check after the copy compares the written rows with what the legacy rows call for. If a check fails, the transaction rolls back, nothing is written, and the next start retries. An instance therefore never runs with half of its tokens migrated.

## Why the ledger exists

The table `identity_legacy_import` holds one row per legacy token, keyed by the legacy id. A row is written when the token is imported, and it is never deleted. This has two effects:

* A token revoked in Ingot stays revoked. Its legacy row still exists, but the row is already recorded, so the next start does not copy the token again.
* A token created on the old version after a rollback has no row. The next start imports it, so it keeps working after the next upgrade.

If a token id is already taken by a token of the new version, which can happen after a rollback, the migrated token gets the next free id and keeps its name.

Concurrent starts claim the rows first, so each legacy token is imported once even when several instances start together.

## Why a rollback loses Ingot-only data

The old version reads only its own tables. Everything created in Ingot lives in the identity tables, which the old version does not read. Users, machine accounts, tokens and rights created in Ingot are therefore missing in Reposilite. Tokens created in Ingot do not authenticate there, and revocations made in Ingot do not reach the old tables. This is a limit of the design and not a defect, and the rollback guide tells the operator what to recreate.

## Why the old tables are kept

The legacy tables hold the state the instance had before its first start under Ingot. Keeping them makes a rollback possible without a restore. Ingot only reads these tables, so running the migration again cannot change them. The cost is that they describe the state before the upgrade and nothing later, which is why the backup is still required before an upgrade.
