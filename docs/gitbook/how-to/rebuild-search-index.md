---
description: Rebuild or verify the artifact search index from the Maintenance page or the API, read the index state, cancel a run and schedule rebuilds.
---

# Rebuild the search index

A rebuild reads the files of storage again and makes the search index agree with them. Start one when the index is empty, after a repository has been restored from a backup, when the last verify reports drift above 5 percent, or after an upgrade in which an older replica wrote artifacts. Ingot also starts a rebuild on its own in some cases, see [Rebuilds on start](#rebuilds-on-start).

{% hint style="info" %}
A rebuild only reads storage. It never deletes or changes a file. Search keeps answering while a rebuild runs, and results can be partial until it ends.
{% endhint %}

## Before you begin

* A manager account, which is the only account that sees the Maintenance page and the index endpoints.
* Search turned on. If the Maintenance page says that search is turned off, set `INGOT_LOCAL_SEARCHENABLED` to `true` and restart Ingot. The [configuration keys](../reference/search-filters-and-api.md#configuration-keys) list the names.

## Rebuild from the Maintenance page

1. Open **Maintenance** and find the **Search index** panel.
2. Read the state, the lag and the date of the last full rebuild. See [Index states](#index-states).
3. Select **Rebuild index**. Choose all repositories or one repository, then select **Rebuild**.
4. The panel lists each repository of the run as **Done**, **In progress** or **Waiting**. It refreshes every two seconds while the run is live.

Only one rebuild or verify run can be live at a time. While one is live, the buttons are disabled, and a request for a second run is refused with a conflict.

## Index states

The panel shows one of these states:

* **Empty**: the index has no rows and no rebuild has completed.
* **Building**: no full rebuild has completed yet, and rows exist or a rebuild is running.
* **Ready**: a full rebuild has completed, and none of the conditions for **Degraded** holds.
* **Degraded**: at least one of these holds. The last rebuild failed. The lag behind storage is over five minutes. Some outbox entries are dead letters. Some changes could not be queued. The drift of the last verify is above 5 percent.

A rebuild does not clear the last verify report. A **Degraded** state that comes from drift stays until a later verify reports drift of 5 percent or less.

## Verify a sample

Select **Verify sample**. A verify checks a sample of index rows against storage. The sample holds 2,000 rows by default. The `verifySampleSize` key in the shared `search` settings domain changes it. For each sampled row, the verify does one of these:

* removes the row, when the file is gone from storage,
* queues the row for repair, when its size or modification time differ from storage,
* corrects the origin of the rows of its directory, when the origin differs from the mirror cache ledger.

A verify does not find files that are missing from the index. Only a rebuild does that.

The **Last verify** part of the panel shows:

* the drift, which is the share of sampled rows that are missing from storage or changed, out of the sample,
* the counts of missing rows, changed rows and rows with a corrected origin,
* the number of rows of repositories that are no longer configured, if any were removed.

When the drift is above 5 percent, the panel adds the line `Drift is above 5 percent. Start a rebuild to repair the index.` The state then reads **Degraded**.

A verify does not start while a rebuild is live.

## Cancel a run

Select **Cancel run** while a run is live. The run stops after the batch in flight and ends as cancelled.

## Rebuild with the API

The index administration endpoints need a manager. Leave out the body, or send `{}`, to rebuild all repositories:

```bash
curl -u <manager>:<secret> -X POST https://<your-host>/api/maven/search/index/rebuild \
  -H 'Content-Type: application/json' \
  -d '{"repositories": ["releases"]}'
```

The answer is `202 Accepted` with the id of the run:

```json
{ "runId": 12 }
```

Start a verify with `POST /api/maven/search/index/verify`. Read the progress with `GET /api/maven/search/index`, and cancel the run with `POST /api/maven/search/index/runs/12/cancel`. The full list of endpoints and answers is in the [reference](../reference/search-filters-and-api.md#index-administration).

## Rebuilds on start

When search is turned on, Ingot checks the index at start and starts a rebuild in two cases:

* The index is empty and has never completed a rebuild. This is the case on the first start after the search tables are created. The rebuild starts whatever the gap setting says.
* The index has rows, but no instance has written its heartbeat for more than 10 minutes, or it has never been written. An older version may have written artifacts that are not indexed. Set `gapRebuild` to `false` in the shared `search` settings domain to turn this case off.

## Schedule rebuilds

Ingot does not rebuild on a timer by default, because a full walk of a large bucket costs many requests. To rebuild on a schedule, enable it in the shared settings domain `search`:

```json
{ "rebuildSchedule": { "enabled": true, "at": "03:30", "everyDays": 7 } }
```

`at` is a time of day in UTC. The scheduled run is claimed once per day, so a restart does not start it twice.

## After an upgrade

On a single instance, the first start of [Ingot 1.2.0](upgrade-ingot.md) builds the index without a manual step. Check the state on the Maintenance page until the run completes.

If an older replica runs next to 1.2.0 replicas during a rolling upgrade, the artifacts it writes are not indexed. That mixed setup is not tested, so see [Upgrade Ingot](upgrade-ingot.md) for the recommended window. Start a rebuild of all repositories after the last older replica has stopped.

## Related

* [How the search index stays current](../explanation/how-the-search-index-stays-current.md) explains when a rebuild is needed and what a verify checks.
* [Search filters and API](../reference/search-filters-and-api.md) lists the settings and the endpoints.
