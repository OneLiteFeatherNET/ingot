---
description: Reference for the artifact search query parameters, response fields, index administration endpoints, configuration keys and the search settings domain of Ingot.
---

# Search filters and API

Artifact search is available from Ingot 1.2.0. It has a public query API, an administration API for instance admins, two local configuration keys and one shared settings domain. Everything here is additive: no existing endpoint changes.

## Search

`GET /api/maven/search` returns one page of artifacts, one row per group, artifact and version. Anonymous callers see the public repositories. Signed-in callers see every repository they may browse. A hidden or private repository never appears to a caller who may not browse it.

### Query parameters

| Parameter | Type | Meaning |
|-----------|------|---------|
| `q` | text, at most 200 characters | Free text. Split on whitespace, at most 5 words, and every word must match. A word matches when the group or artifact starts with it, or the version equals it. A word of the form `group:artifact:version` is a coordinates word. Each part is optional. |
| `groupId` | text | Prefix of the group. |
| `artifactId` | text | Prefix of the artifact. |
| `version` | text | Exact version, case insensitive. |
| `versionPrefix` | text | Prefix of the version. Cannot be combined with `version`. |
| `repository` | repeatable | Exact repository name. An unknown name matches nothing. |
| `extension` | repeatable | Exact file extension. The value is compared in lower case. |
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
      "lastModified": 1790756042.0,
      "browseLink": "/browse/releases/org/apache/commons/commons-lang3/3.14.0"
    }
  ],
  "page": 0,
  "size": 20,
  "total": 143,
  "totalCapped": false,
  "facets": {
    "repository": [ { "value": "releases", "count": 612 } ],
    "extension": [ { "value": "jar", "count": 540 }, { "value": "pom", "count": 72 } ],
    "classifier": [ { "value": "-", "count": 600 }, { "value": "sources", "count": 12 } ],
    "origin": [ { "value": "HOSTED", "count": 700 }, { "value": "MIRROR", "count": 90 } ],
    "snapshot": [ { "value": "false", "count": 790 } ]
  },
  "indexStatus": {
    "state": "READY",
    "lagSeconds": 0,
    "lastFullRebuild": 1792467012.0,
    "deadLetters": 0,
    "droppedEnqueues": 0
  }
}
```

- `repositories` lists every repository the caller may see that holds the version, with its origin. `MIRROR` marks a cached copy of a proxy repository. A repository that holds both a hosted file and a cached copy is listed as `HOSTED`.
- `latestVersion` is the highest release among the versions the caller may see, or the highest snapshot when only snapshots exist. It ignores the filters. It is left out when the read cap is reached: more than 5,000 versions of one artifact, or a page that would read more than 100,000 index rows.
- `fileCount` counts the files that match the filters.
- `browseLink` opens the first repository in the list in the browser.
- `facets` has five keys: `repository`, `extension`, `classifier`, `origin` and `snapshot`. Each is a list of `{ "value", "count" }` buckets, largest first, at most 20 per key. The counts are files, not artifacts. A file without a classifier is in the bucket `-`. Each facet leaves out its own filter, so it still lists the other values you could choose.

Timestamps are written as decimal epoch seconds, such as `1790756042.123456789`. A field without a value is left out of the JSON rather than sent as `null`. This applies to `latestVersion`, `lastFullRebuild`, `sha1`, `sourceHost`, `classifier`, `activeRun` and `lastVerify`.

The `indexStatus` field reports the state of the index with each answer:

| State | Meaning |
|-------|---------|
| `READY` | A full rebuild has completed, and the lag and the drift are within limits. |
| `BUILDING` | No full rebuild has completed yet, so results may be partial. |
| `MISSING` | The index is empty and no rebuild is queued. |
| `DEGRADED` | The index is usable, but it lags by more than 5 minutes, has dead letters or dropped changes, has drift above 5 percent, or its last rebuild failed. |

`lagSeconds` is the age of the oldest change that has not been applied yet. `lastFullRebuild` is the time of the last completed rebuild. `deadLetters` counts changes that failed too often, and `droppedEnqueues` counts changes that could not be queued since the last rebuild.

### Files of a version

`GET /api/maven/search/files` lists the files of one version. The parameters `groupId`, `artifactId` and `version` are required and exact. The parameters `origin` and `repository` (repeatable) narrow the files as they narrow the search. `page` defaults to 0, and `size` defaults to 50 and is at most 200.

Each file has `repository`, `path`, `name`, `classifier`, `extension`, `size`, `lastModified`, `origin`, `sourceHost`, `sha1` and `browseLink`.

- `sha1` is left out when the checksum is not known.
- `sourceHost` is set for a cached copy whose upstream host was recorded. Hosted files have none, and neither do cached copies made before the host was recorded. Anonymous callers receive it as well.

### Errors

| Status | When |
|--------|------|
| `400` | A parameter has an invalid value. The message names the parameter. A result window beyond 10 000 entries is also refused here. |
| `404` | Search is turned off on this instance. |
| `503` | The database did not answer within 10 seconds, or the caller's grants are too many to filter. The response carries `Retry-After`. |

## Index administration

The endpoints below need an instance admin. Any other caller is refused with `403`. The Maintenance page reads the same status.

| Method and path | Result |
|-----------------|--------|
| `GET /api/maven/search/index` | The status, described below. |
| `POST /api/maven/search/index/rebuild` | Starts a rebuild. Without a body, or with `{}`, every repository is rebuilt. With `{"repositories": ["name"]}`, only the named ones. An empty list is refused. Answers `202` with `{"runId": n}`. |
| `POST /api/maven/search/index/verify` | Starts a verify run. Answers `202` with `{"runId": n}`. |
| `POST /api/maven/search/index/runs/{id}/cancel` | Cancels a live run. Answers `204`. |

The status answers with these fields:

| Field | Meaning |
|-------|---------|
| `state`, `lagSeconds`, `lastFullRebuild` | As in the search answer. |
| `rows` | The number of indexed files. |
| `outbox` | `pending`: changes waiting to be applied. `deadLetters`: changes that failed too often, which a rebuild or a verify repairs. `droppedEnqueues`: changes that could not be queued since the last rebuild. |
| `repositories` | The configured repository names, in the order a rebuild walks them. |
| `activeRun` | The live run. Left out when no run is live. |
| `runs` | The last 20 runs, newest first. |
| `lastVerify` | The report of the last completed verify run. Left out when there is none. It holds `runId`, `at`, `sampled`, `missing`, `changed`, `reorigined`, `removedRepositoryRows` and `drift`. |
| `engine` | The search engine in use. Currently always `database`. |

A run has `id`, `kind` (`REBUILD` or `VERIFY`), `trigger` (`MANUAL`, `SCHEDULED`, `STARTUP` or `GAP`), `status` (`RUNNING`, `COMPLETED`, `FAILED` or `CANCELLED`), `startedAt`, `finishedAt`, `leaseOwner`, `leaseUntil`, `repositories` (the names of a partial rebuild, or `null` for all), `cursorRepository`, `cursorPath`, `counters` (`processed`, `upserted`, `deleted`, `unparsed`, `skippedTooLong`, `errors`) and `lastError`.

At most one run is live at a time, whether it is a rebuild or a verify. Cancelling a run that has already ended answers `409`.

| Status | When |
|--------|------|
| `400` | The body is not an object of the expected shape, an empty repository list was sent, or the body names a repository that is not configured. |
| `403` | The caller is not an instance admin. |
| `404` | Search is turned off on this instance, or, for cancel, the run does not exist. |
| `409` | A rebuild or a verify run is already live, or, for cancel, the run has already finished. |

## Configuration keys

Both keys are local settings. They are read at start, and they are not changed at runtime.

| Key | Environment variable | System property | Default | Meaning |
|-----|----------------------|-----------------|---------|---------|
| `searchEnabled` | `INGOT_LOCAL_SEARCHENABLED` | `ingot.local.searchEnabled` | `true` | `false` answers `404` on every search endpoint and stops the index workers. The tables stay. |
| `searchEngine` | `INGOT_LOCAL_SEARCHENGINE` | `ingot.local.searchEngine` | `database` | The engine that holds the index. Only `database` is recognised. Any other value leaves search off and logs a warning at start. |

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
