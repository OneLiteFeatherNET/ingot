---
description: Reference for the artifact search query parameters, response fields, index administration endpoints, configuration keys and the search settings domain of Ingot.
---

# Search filters and API

Artifact search has a public query API, an administration API for managers, two configuration keys and one shared settings domain. Everything here is additive: no existing endpoint changes.

## Search

`GET /api/maven/search` returns one page of artifacts, one row per group, artifact and version. Anonymous callers see the public repositories. Signed-in callers see every repository they may browse, and a hidden or private repository never appears to a caller who may not browse it.

### Query parameters

| Parameter | Type | Meaning |
|-----------|------|---------|
| `q` | text, at most 200 characters | Free text. Split on whitespace, at most 5 words, and every word must match. A word matches when the group or artifact starts with it, or the version equals it. `group:artifact:version` is a coordinates query. |
| `groupId` | text | Prefix of the group. |
| `artifactId` | text | Prefix of the artifact. |
| `version` | text | Exact version, case insensitive. |
| `versionPrefix` | text | Prefix of the version. Cannot be combined with `version`. |
| `repository` | repeatable | Exact repository name. An unknown name matches nothing. |
| `extension` | repeatable | Exact extension, lower case. |
| `classifier` | repeatable | Exact classifier, case sensitive. `-` selects files without a classifier. |
| `sha1` | 40 hexadecimal characters | Exact checksum. Matches only files whose checksum is known. |
| `snapshot` | `true`, `false`, `all` | Default `all`. |
| `origin` | `hosted`, `mirror`, `all` | Default `all`. `mirror` selects cached copies. |
| `modifiedAfter`, `modifiedBefore` | ISO-8601 instant | On the last modified time. `modifiedAfter` is inclusive, `modifiedBefore` exclusive. |
| `sizeMin`, `sizeMax` | bytes | Inclusive bounds on the size of the files. |
| `sort` | `groupId`, `artifactId`, `version`, `modified`, `size` | Default `groupId`. |
| `order` | `asc`, `desc` | Default `asc`. |
| `page` | integer from 0 | Default 0. |
| `size` | integer from 1 to 100 | Default 20. |

All filters combine with AND. Repeated values of one parameter combine with OR. A filter on a file property, such as the extension or the size, selects files, and an artifact appears when at least one of its files matches. Unknown parameters are ignored.

Paging stops at `page * size + size <= 10000`. A request beyond that window answers `400`. The total is counted up to 10 001 artifacts; when there are more, `totalCapped` is `true`.

### Response

```json
{
  "items": [
    {
      "groupId": "org.apache.commons",
      "artifactId": "commons-lang3",
      "version": "3.14.0",
      "latestVersion": "3.17.0",
      "snapshot": false,
      "repositories": [
        { "name": "releases", "origin": "HOSTED" },
        { "name": "central-proxy", "origin": "MIRROR" }
      ],
      "fileCount": 8,
      "lastModified": "2026-09-30T08:14:02Z",
      "browseLink": "/browse/releases/org/apache/commons/commons-lang3/3.14.0"
    }
  ],
  "page": 0,
  "size": 20,
  "total": 143,
  "totalCapped": false,
  "facets": {
    "repository": [ { "value": "releases", "count": 612 } ],
    "origin": [ { "value": "HOSTED", "count": 700 }, { "value": "MIRROR", "count": 90 } ]
  },
  "indexStatus": { "state": "READY", "lagSeconds": 0, "lastFullRebuild": "2026-10-20T03:30:12Z" }
}
```

- `repositories` lists every repository that holds the artifact, with its origin. `MIRROR` marks a cached copy of a proxy repository.
- `latestVersion` is the highest release in the repositories the caller may see, or the highest snapshot when only snapshots exist.
- `browseLink` opens the first repository in the list in the browser.
- `facets` count files, not artifacts, and are limited to 20 buckets each. Send `facets=false` to skip them.

The `indexStatus` field reports the state of the index with each answer:

| State | Meaning |
|-------|---------|
| `READY` | A full rebuild has completed, and the lag and the drift are within limits. |
| `BUILDING` | No full rebuild has completed yet, so results may be partial. |
| `MISSING` | The index is empty and no rebuild is queued. |
| `DEGRADED` | The index is usable, but it lags by more than 5 minutes, has dead letters or dropped changes, has drift above 5 percent, or its last rebuild failed. |

`lagSeconds` is the age of the oldest change that has not been applied yet. `lastFullRebuild` is the time of the last completed rebuild.

### Files of a version

`GET /api/maven/search/files` lists the files of one version. The parameters `groupId`, `artifactId` and `version` are required and exact. The parameters `origin` and `repository` (repeatable) narrow the files as they narrow the search. `page` defaults to 0, and `size` defaults to 50 and is at most 200.

Each file has `repository`, `path`, `name`, `classifier`, `extension`, `size`, `lastModified`, `origin`, `sourceHost`, `sha1` and `browseLink`. `sha1` is null when the checksum is not known. `sourceHost` is null for hosted files, for copies cached before 1.3.0, and for anonymous callers.

### Errors

| Status | When |
|--------|------|
| `400` | A parameter has an invalid value. The message names the parameter. A result window beyond 10 000 entries is also refused here. |
| `404` | Search is turned off on this instance. |
| `503` | The database did not answer within 10 seconds, or the caller's grants are too many to filter. The response carries `Retry-After`. |

## Index administration

The endpoints below need an instance admin with the manager right, as the Maintenance endpoints do. The Maintenance page reads the same status.

| Method and path | Result |
|-----------------|--------|
| `GET /api/maven/search/index` | The status, described below. |
| `POST /api/maven/search/index/rebuild` | Starts a rebuild. The body is `{}` for all repositories, or `{"repositories": ["name"]}`. Answers `202` with `{"runId": n}`. |
| `POST /api/maven/search/index/verify` | Starts a verify run. Answers `202` with `{"runId": n}`. |
| `POST /api/maven/search/index/runs/{id}/cancel` | Cancels a live run. Answers `204`. |

The status answers with these fields:

| Field | Meaning |
|-------|---------|
| `state`, `lagSeconds`, `lastFullRebuild` | As in the search answer. |
| `rows` | The number of indexed files. |
| `outbox` | `pending`: changes waiting to be applied. `deadLetters`: changes that failed too often, which a rebuild or a verify repairs. `droppedEnqueues`: changes that could not be queued since the last rebuild. |
| `repositories` | The configured repository names, in the order a rebuild walks them. |
| `activeRun` | The live run, or `null`. |
| `runs` | The last 20 runs, newest first. |
| `lastVerify` | The report of the last completed verify run, or `null`. It holds `sampled`, `missing`, `changed`, `reorigined`, `removedRepositoryRows` and `drift`. |
| `engine` | The search engine in use, for example `database`. |

A run has `id`, `kind` (`REBUILD` or `VERIFY`), `trigger` (`MANUAL`, `SCHEDULED`, `STARTUP` or `GAP`), `status` (`RUNNING`, `COMPLETED`, `FAILED` or `CANCELLED`), `startedAt`, `finishedAt`, `leaseOwner`, `leaseUntil`, `repositories` (the names of a partial rebuild, or `null` for all), `cursorRepository`, `cursorPath`, `counters` (`processed`, `upserted`, `deleted`, `unparsed`, `skippedTooLong`, `errors`) and `lastError`.

Times in the status are epoch seconds with a fraction. Cancelling a run that has already ended answers `409`.

| Status | When |
|--------|------|
| `400` | The body names a repository that is not configured. |
| `403` | The caller is not a manager. |
| `404` | Search is turned off on this instance. |
| `409` | A rebuild or a verify run is already live. |

## Configuration keys

Both keys are local settings. They are read at start, and they are not changed at runtime.

| Key | Environment variable | System property | Default | Meaning |
|-----|----------------------|-----------------|---------|---------|
| `searchEnabled` | `INGOT_LOCAL_SEARCHENABLED` | `ingot.local.searchEnabled` | `true` | `false` answers `404` on every search endpoint and stops the workers. The tables stay. |
| `searchEngine` | `INGOT_LOCAL_SEARCHENGINE` | `ingot.local.searchEngine` | `database` | The engine that holds the index. `database` ships with Ingot. Other engines are plugins. |

The names are spelled `INGOT_LOCAL_SEARCHENABLED` and `INGOT_LOCAL_SEARCHENGINE`, without an underscore inside the key. A spelling such as `INGOT_LOCAL_SEARCH_ENABLED` does not match and is logged as an unknown property. The older `REPOSILITE_LOCAL_` prefix is read as a fallback, and when both prefixes set the same key, the `INGOT_` one wins.

## Settings domain `search`

The shared settings domain `search` holds the behaviour of the index. It is read at runtime, and a change reaches every replica within about 10 seconds. Read it at `GET /api/settings/domain/search` and write it at `PUT /api/settings/domain/search`, like the other shared settings.

| Key | Default | Meaning |
|-----|---------|---------|
| `verifyEnabled` | `true` | Runs the nightly verify. |
| `verifyAt` | `"02:30"` | Time of the nightly verify, UTC, `HH:mm`. |
| `verifySampleSize` | `2000` | Rows that one verify run compares with storage. At least 1. |
| `rebuildSchedule` | `{ "enabled": false, "at": "03:30", "everyDays": 7 }` | A scheduled full rebuild. `at` is UTC, `HH:mm`. `everyDays` is at least 1. |
| `gapRebuild` | `true` | Starts a rebuild after a start that finds a gap in the heartbeat, for example after a downgrade. Set it to `false` to start rebuilds by hand only. |

For how the verify and the rebuilds fit together, read [How the search index stays current](../explanation/how-the-search-index-stays-current.md).
