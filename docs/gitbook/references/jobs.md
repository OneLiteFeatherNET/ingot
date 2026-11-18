---
description: Reference for the Jobs page of Ingot. Covers the address and filters, the fields and states of a run, the sources that list runs with their nodes and retention, the jobs API and the detail of one run.
---

# Jobs

The Jobs page lists the background runs of the instance in one place: index rebuilds and verifies, cleanup runs and nexus imports, and the runs of plugins that register a source. It is read only. A run is cancelled on the page of its source, not here. To set up the work itself, see [Rebuild the search index](../configure/search/rebuild-search-index.md), [Set cleanup policies](../configure/repositories/set-cleanup-policies.md) and [Use the Nexus import wizard](../set-up/migrate/use-the-nexus-import-wizard.md).

## The page

The page is at `/settings/jobs`. A run opens at `/settings/jobs/:id`, with the id URL-encoded.

| Item | Behaviour |
| --- | --- |
| Filters | `kind`, `state` and `trigger` in the address, for example `/settings/jobs?kind=index&state=FAILED`. Each filter may repeat. |
| Paging | The page shows 50 runs. **Load more** appends the next 50 and does not reload the first page. There is no total count. |
| Refresh | **Refresh** reloads the first page. The list does not update on its own. |
| Detail | Shows the fields of a run, its counts and its failures. It has no Cancel action. |
| Menu | The entry **Jobs** is in the **Support** group, after **Maintenance**. |

The page loads the newest run first. A run that starts while you page down the list appears when you press **Refresh**, and the rows you already loaded keep their order.

### Links from the Maintenance page

The **Search index** and **Cleanup policies** tabs of Maintenance no longer list every run. Each shows the latest run of its kind or of each policy, and a link **All runs**:

| Tab | Link | Shows |
| --- | --- | --- |
| Search index | `/settings/jobs?kind=index` | The index runs only. |
| Cleanup policies | `/settings/jobs?kind=cleanup` | The cleanup runs of every policy. |

The address `/maintenance?tab=index` still opens the Search index tab.

## Sources

A source is a place that keeps a record of its runs. The page lists the sources that are registered on the instance, and a visitor sees a source only when the visitor holds its node. A source the visitor may not read is absent from the list, from the kind filter and from the detail of its runs.

| Source | Kinds | Node that lists its runs | Runs kept |
| --- | --- | --- | --- |
| `index` | `index.rebuild`, `index.verify` | `ingot.admin.maintenance` | Every run is kept. Nothing deletes an index run. The page shows the runs that exist. |
| `cleanup` | `cleanup.policy` | `ingot.admin.maintenance` | The newest 20 runs of each policy. The cleanup service removes older runs after each run. |
| `migration` | `migration.nexus` | `ingot.admin.*` (instance admin) | Every job is kept until an operator deletes a finished job. |
| A plugin source | `<source>.<name>` | The node the plugin declares, `ingot.admin.*` by default | What the plugin keeps. |

Notes:

* The `cleanup` kind lists the runs of all policies. The page has no filter by policy.
* A plugin source that is disabled leaves the page with its rows. Its history stays in the tables of the plugin. See [Job sources](plugins/plugin-api.md#job-sources).
* A source whose store fails on one request is left out of that page, and the answer names it in `sourceErrors`. The other runs are still listed.

## Fields of a run

Every source returns runs in the same shape.

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | string | Unique and opaque: `<source>:<local id>`, for example `index:412`. Encode it in the path of the detail. |
| `source` | string | The source id: `index`, `cleanup`, `migration` or a plugin source. |
| `kind` | string | The kind of the run, such as `index.verify` or `cleanup.policy`. |
| `label` | string | The text of the row, for example "Verify" or "Cleanup: nightly". |
| `trigger` | `MANUAL`, `SCHEDULED` or `SYSTEM` | How the run started. See [Triggers](#triggers). |
| `triggerDetail` | string, optional | The raw trigger of the source. Set for the index, where `SYSTEM` groups the startup, gap and backend triggers. |
| `state` | see [States](#states) | The normalised state of the run. |
| `startedAt` | instant | When the run started. |
| `finishedAt` | instant, optional | When the run finished. Empty while the run is running or waiting. |
| `durationMs` | number, optional | The duration of a finished run, in milliseconds. |
| `counts` | list of `key`, `label`, `value` | The counters of the run, in the order of the source. The labels are the source's. |
| `failures` | list of `message` | The failures of the run. A failure names the path or the URL it concerns. It never holds a credential. |
| `failuresTruncated` | boolean | `true` when the run has more failures than the detail shows. The detail shows at most 50. |

The shape only grows. A source can add counts and failures, and no field is removed.

### States

Each source keeps its own status words. The page shows the normalised state.

| State | Index status | Cleanup status | Migration status |
| --- | --- | --- | --- |
| `QUEUED` | none | none | `QUEUED` |
| `RUNNING` | `RUNNING` | none | `RUNNING` |
| `PAUSED` | none | none | `PAUSED` |
| `SUCCEEDED` | `COMPLETED` | `SUCCEEDED` | `COMPLETED` |
| `PARTIAL` | none | `PARTIAL` | none |
| `FAILED` | `FAILED` | `FAILED` | `FAILED` |
| `CANCELLED` | `CANCELLED` | none | `CANCELLED` |

An index run that completes with a verify that fixed rows is `SUCCEEDED`. The counts show the fixes. A nexus job that completes with mismatched repositories is `SUCCEEDED`, and its report is in the detail.

### Triggers

| Trigger | Meaning |
| --- | --- |
| `MANUAL` | Started by an operator, or a nexus import job. |
| `SCHEDULED` | Started by a schedule, such as a cleanup policy with a time or a verify schedule. |
| `SYSTEM` | Started by the server itself. For the index these are the startup, gap and backend triggers, and the raw value is in `triggerDetail`. |

## Jobs API

The page reads two endpoints. Both need a token or a session with the node of the source. Each endpoint answers `401` without a credential.

### List runs

`GET /api/jobs`

| Parameter | Meaning |
| --- | --- |
| `kind` | A source id, such as `index`, or a kind, such as `index.verify`. May repeat. An unknown kind answers an empty page, not an error. |
| `state` | One of the states above. May repeat. An unknown state answers `400`. |
| `trigger` | One of `MANUAL`, `SCHEDULED` or `SYSTEM`. May repeat. An unknown trigger answers `400`. |
| `size` | The number of runs in the page, from 1 to 100. The default is 50. A larger value answers 100 runs and a smaller one answers 1. |
| `cursor` | The `nextCursor` of the previous page. The cursor is opaque. A malformed cursor answers `400`. |

The answer is an object:

| Field | Meaning |
| --- | --- |
| `items` | The runs of the page, newest first. |
| `nextCursor` | The cursor of the next page, or `null` when no source has more runs. |
| `sourceErrors` | The sources that failed on this request. |
| `kinds` | The kinds the caller may read. The kind filter offers only these. |

Runs are sorted by start time, newest first. Two runs with the same start time are sorted by their local id. The paging keeps the position of the last run in the cursor, so a run that starts while you page does not shift the rows you have not read yet, and no run appears twice.

A caller who may read none of the sources gets `403`.

### Get one run

`GET /api/jobs/{id}`

The answer is one run with its counts and its failures. A run that does not exist, and a run of a source the caller may not read, both answer `404`. The caller learns nothing about the second kind of run.

## Limits

The page size and the maximum of 100 bound one request. They do not limit the history of a source. The history is the runs each source keeps, as the table above lists.

## Drop-in compatibility

The Jobs page adds two endpoints and a page. It changes no existing path, method or response:

* `GET /api/maven/search/index` still returns its 20 runs, and `GET /api/cleanup/runs` still returns its list.
* No configuration key, environment variable, volume or manifest changes.
* The page reads the tables the sources already keep. It writes nothing.
