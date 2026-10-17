---
description: Rebuild or verify the artifact search index from the Maintenance page or the API, cancel a run and schedule rebuilds.
---

# Rebuild the search index

A rebuild reads the files of storage again and makes the search index agree with them. Start one when the index is empty, when a verify reports drift above 5 percent, after a repository has been restored from a backup, or once the last pod of an older version has stopped during a rolling upgrade.

{% hint style="info" %}
A rebuild only reads storage. It never deletes or changes a file. Search keeps answering while a rebuild runs, and results can be partial until it ends.
{% endhint %}

## Before you begin

* A manager account, which is the only account that sees the Maintenance page and the index endpoints.
* Search turned on. If the Maintenance page says that search is turned off, set `INGOT_LOCAL_SEARCHENABLED` to `true` and restart Ingot. The [configuration keys](../reference/search-filters-and-api.md#configuration-keys) list the names.

## Rebuild from the Maintenance page

1. Open **Maintenance** and find the **Search index** panel.
2. Read the state, the lag and the date of the last full rebuild. The state **Ready** means that a full rebuild has completed at least once.
3. Select **Rebuild index**. Choose all repositories or one repository, then select **Rebuild**.
4. The panel lists each repository of the run as done, in progress or waiting. It refreshes every two seconds while the run is live.

Only one rebuild or verify run can be live at a time. While one is live, the buttons are disabled, and a request for a second run is refused with a conflict.

## Verify a sample

Select **Verify sample**. A verify compares a sample of 2,000 index rows with storage by default. For each row it finds it either:

* removes the row, when the file is gone from storage, or
* corrects the row, when its size or modification time changed.

A verify does not find files that are missing from the index. Only a rebuild does that.

The panel shows the last verify report: the drift, which is the share of sampled rows that disagreed with storage, and the counts behind it. Above 5 percent the index is degraded and the panel recommends a rebuild. A verify does not start while a rebuild is live.

## Cancel a run

Select **Cancel run** while a run is live. The run stops at its next batch and ends as cancelled.

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

Read the progress with `GET /api/maven/search/index`, and cancel the run with `POST /api/maven/search/index/runs/12/cancel`. The full list of endpoints and answers is in the [reference](../reference/search-filters-and-api.md#index-administration).

## Schedule rebuilds

Ingot does not rebuild on a timer by default, because a full walk of a large bucket costs many requests. To rebuild on a schedule, enable it in the shared settings domain `search`:

```json
{ "rebuildSchedule": { "enabled": true, "at": "03:30", "everyDays": 7 } }
```

`at` is a time of day in UTC. The scheduled run is claimed once per day, so a restart does not start it twice.

## After a rolling upgrade from 1.2

While 1.2 pods run beside 1.3 pods, the artifacts that the 1.2 pods write are not indexed. The next rebuild or verify sample picks them up. After the last 1.2 pod has stopped, start a rebuild of all repositories, or enable the schedule so that a rebuild runs soon after.

## How long it takes

The release notes estimate about 100 seconds per million files on a local disk. Object storage is limited by the number of listing requests, so a bucket takes longer. The first start after an upgrade runs this rebuild in the background.

## Related

* [How the search index stays current](../explanation/how-the-search-index-stays-current.md) explains when a rebuild is needed and what a verify checks.
* [Search filters and API](../reference/search-filters-and-api.md) lists the settings and the endpoints.
