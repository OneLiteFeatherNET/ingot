---
description: Create a cleanup policy in the Ingot dashboard, check it with a preview, run it once and put it on a daily schedule, or do the same with the API.
---

# Set cleanup policies

A cleanup policy deletes old versions of artifacts from the repositories you choose. This guide creates a policy, shows what it would delete, runs it once and then puts it on a daily schedule. A deleted version cannot be restored, so check the preview before you run.

## Before you begin

* Sign in as an instance admin. The Maintenance page and the cleanup endpoints need one.
* Know which repositories the policy should clean, and which versions it may remove. The [cleanup policy reference](../reference/cleanup-policies.md) lists the scopes, limits and rules.
* A copy of the repository data, if the policy touches releases.

## Create the policy in the dashboard

1. Open **Maintenance** in the navigation, or go to `/maintenance` on your instance. Find the **Cleanup policies** card.
2. Select **New policy**.
3. Enter an **Id**, for example `old-snapshots`. You cannot change the id later.
4. Leave **Enabled** off for now. A disabled policy can still be previewed and run by hand.
5. Under **Repositories**, select the repositories the policy acts on. A policy with no repository does nothing.
6. Choose a **Scope**: **Snapshots**, **Releases** or **Mirror cache**.
7. Set at least one limit:
    * **Keep the newest versions**: the newest N versions of each artifact stay.
    * **Older than (days)**: only versions last modified longer ago than this are deleted.
    * **Not downloaded for (days)**: only versions that nobody downloaded for this long are deleted.
8. Optional: enter a **Group filter**, a regular expression that has to match the whole artifact path, for example `net/onelitefeather/.*`.
9. Leave **When to run** on **Manual only**.
10. Select **Save policy**.

The policy appears in the list with its scope, repositories, limits and the text **Never run**.

## Preview what the policy deletes

1. On the policy, select **Preview**. A preview deletes nothing.
2. Read the result. It shows the number of artifacts and their size, and a table with the repository, path, version, size and reason of each version.
3. Read the warnings above the table. A warning tells you, for example, that a repository does not exist.
4. If the list is not what you expect, select the pencil icon for **Edit policy**, change the limits or the group filter, save, and preview again.

A preview that says **Nothing would be deleted** means that no version matches the limits right now.

## Run the policy once

1. On the policy, select **Run now**.
2. Type the id of the policy to confirm, then select **Run now** in the dialog.
3. Read the result. It shows the status (**Succeeded**, **Partly failed** or **Failed**), the number of artifacts deleted, their size, and a list of failures.

## Put the policy on a schedule

1. On the policy, select **Edit policy**.
2. Set **When to run** to **Daily at a time** and enter the time as **Time (HH:mm)**. The time is in the time zone of the server.
3. Select **Save policy**.
4. Switch **Enabled** on in the policy list.

An enabled policy runs once a day at that minute. If the instance is not running at that minute, the run for that day is skipped.

## Verify the result

1. In **Run history** on the same page, find your run. A manual run shows **Manual**, a scheduled run shows **Scheduled**, and each line lists the number of artifacts and the size deleted.
2. Browse the repository and confirm that the deleted versions are gone and the kept ones remain.
3. For a schedule, check **Run history** on the day after the scheduled time. A new run with the trigger **Scheduled** shows that the schedule works.

Each policy keeps its last 20 runs. An instance admin can also read the run as an audit event of the type `CLEANUP_RUN`.

## Do the same with the API

Policies are part of the `cleanup` settings domain, and the run endpoints are separate. The commands need an instance admin account, or a token of one. Replace `<admin>`, `<secret>` and `<your-host>`.

1. Read the current policies:

    ```bash
    curl -u <admin>:<secret> https://<your-host>/api/settings/domain/cleanup
    ```

2. Send the list back with your policy added. A `PUT` replaces the whole list, so keep the policies that already exist:

    ```bash
    curl -u <admin>:<secret> -X PUT https://<your-host>/api/settings/domain/cleanup \
      -H 'Content-Type: application/json' \
      -d '{"policies": [{
            "id": "old-snapshots",
            "enabled": false,
            "repositories": ["snapshots"],
            "scope": "SNAPSHOTS",
            "keepLatest": 3,
            "olderThanDays": 30,
            "schedule": "manual"
          }]}'
    ```

3. Preview the policy:

    ```bash
    curl -u <admin>:<secret> -X POST \
      https://<your-host>/api/cleanup/policies/old-snapshots/dry-run
    ```

    The answer lists `count`, `totalBytes`, `deletions` and `warnings`.

4. Run it:

    ```bash
    curl -u <admin>:<secret> -X POST \
      https://<your-host>/api/cleanup/policies/old-snapshots/run
    ```

5. List the runs of the policy:

    ```bash
    curl -u <admin>:<secret> "https://<your-host>/api/cleanup/runs?policy=old-snapshots"
    ```

    The newest run is first. Its `status` is `SUCCEEDED` when every deletion worked.

To schedule the policy, set `"enabled": true` and `"schedule": "04:30"` in the same `PUT`.

## If the result is not what you expected

| Symptom | Check |
| --- | --- |
| The save is refused | The message names the broken rule. Set at least one limit, use a valid id and a valid regular expression. |
| The preview is empty | Read the warnings. Check the repositories, the scope and the group filter. A version is only deleted when it matches every age limit. |
| An artifact keeps a version you wanted gone | Ingot always keeps the newest version of an artifact, and keeps snapshots newer than the newest release. See [Rules](../reference/cleanup-policies.md#rules). |
| A `MIRROR_CACHE` policy finds nothing | Only copies that Ingot recorded are considered. The repository needs a proxied entry with `store` turned on. |
| The scheduled run did not happen | The policy has to be enabled and have a time, and the instance has to run at that minute. |
| `Cleanup policy <id> is already running` | Wait until the run on that instance ends. |

## Related

* [Cleanup policies](../reference/cleanup-policies.md) lists every field, rule and endpoint.
* [Repository settings](../reference/repository-settings.md) describes `preserveSnapshots`, which controls the builds inside one snapshot version.
