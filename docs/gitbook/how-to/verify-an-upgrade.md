---
description: Check an upgraded Ingot instance by reading the migration log, listing principals, and testing tokens and routes.
---

# Verify an upgrade

Run these checks after every upgrade, before you rely on the new instance.

## Prerequisites

* The upgraded server is running.
* A manager token. The principal list requires the Manager right.
* A token with routes, and one action it is allowed to take and one it is not.
* The answers from before the upgrade, for comparison.
* The name of one artifact you know exists. Read it anonymously if it is in a public repository.

## Steps

1. Read the log of the first start and look for the migration line:

   ```
   Identity | migrated 7 access token(s) to machine principals
   ```

   The line appears on the first start when the instance has persistent tokens. A later start prints it only when it copies tokens that an older version created after a rollback.

2. List the principals with a manager token. Each migrated token appears as a machine principal.

   ```bash
   curl -u <manager>:<secret> https://<your-host>/api/identity/principals
   ```

3. Authenticate with the token that has routes. Try the action it is allowed to take and the one it is not. Each answers as it did before the upgrade.

4. Read the artifact you know. It answers as it did before the upgrade.

## Result

All checks answer as they did before the upgrade.

If a token that worked before no longer authenticates, do not delete anything. Leave the data as it is and follow [Roll back an upgrade](roll-back-an-upgrade.md).
