---
description: Reference for the storage providers of Ingot, the file system (fs) and S3 (s3). Lists every setting with its default, the S3 system properties, the quota rules and how storage usage is measured.
---

# Storage providers

A storage provider holds the files of one repository. Each repository chooses its provider in the `storageProvider` field of its settings, see [Settings domains](settings-domains.md#maven). Ingot ships two providers.

| Provider | `type` | Holds the files in |
| --- | --- | --- |
| File system | `fs` | A directory on the disk of the server. |
| S3 | `s3` | A bucket of an S3-compatible object store. |

When a stored `storageProvider` has no `type`, Ingot uses `s3` if the document contains `bucketName` and `fs` otherwise. A repository without a `storageProvider` uses the file system provider with its defaults.

Provider settings are part of the repository settings. By default these are shared settings in the database, not files in the storage provider. For the databases Ingot supports, see [Database connection](configuration-and-startup.md#database-connection): SQLite, MariaDB and PostgreSQL are supported, MySQL and H2 are experimental.

## File system provider

### Settings

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `type` | String | `fs` | Fixed value. |
| `quota` | String | `100%` | Upper limit for the data in this repository. See [Quota](#quota). |
| `mount` | String | empty | Directory of the repository data. Empty uses `<working directory>/repositories/<repository id>`. A relative value is resolved against `<working directory>/repositories`. An absolute value is used as it is. |

The directory is created when the repository is loaded.

### Quota

The quota is enforced by the file system provider only.

| Format | Example | Meaning |
| --- | --- | --- |
| Percentage | `90%` | A whole number from 1 to 100. That share of the usable space that the file store of the repository directory reports at the moment of the upload. |
| Size | `500MB`, `10GB`, `100KB` | Fixed number of bytes. Units are `KB`, `MB` and `GB` in any case, with the factor 1024. |
| Byte count | `10485760` | Fixed number of bytes. |

| Rule | Behavior |
| --- | --- |
| Valid values | A size must be greater than 0. A percentage must be greater than 0 and at most 100. An invalid value stops that repository from loading, and the failure is recorded. |
| When it is checked | Before an upload is written and again after the upload has been written to a temporary file. |
| When it is exceeded | The upload is refused with `507 Insufficient Storage`. Nothing is stored. |
| Formula | The upload is accepted when its size is at most the limit minus the usage that the provider reports for the repository. |

{% hint style="info" %}
A percentage quota uses the free space at the time of the upload as its reference. The usage report of the dashboard uses the total capacity of the disk instead, so the two figures can differ for the same quota.
{% endhint %}

{% hint style="warning" %}
Unverified at run time. In the code of this release, the usage that the file system provider reports is the result of `getFileSize` for the repository root, and `getFileSize` returns `-1` for a directory. Read this way, the quota limits the size of one upload and does not add up the files that are already stored. This follows from reading the code. It was not confirmed by a test run. Do not rely on the quota to cap the total size of a repository.
{% endhint %}

## S3 provider

### Settings

| Field | Type | Default | Secret | Meaning |
| --- | --- | --- | --- | --- |
| `type` | String | `s3` | no | Fixed value. |
| `bucketName` | String | empty | no | Bucket that holds the data. |
| `endpoint` | String | empty | yes | Address of the object store. Empty uses the AWS endpoint of the region. |
| `accessKey` | String | empty | yes | Access key. |
| `secretKey` | String | empty | yes | Secret key. |
| `region` | String | empty | no | Region. See [Region and credentials](#region-and-credentials). |
| `prefix` | String | empty | no | Prefix for all object keys, for example to share a bucket with other services. Leading and trailing slashes are removed. |
| `sharedBucket` | Boolean | `false` | no | Puts the objects of each repository under `<prefix>/<repository id>/`, so several repositories can share one bucket. |
| `metadataCacheSettings` | Object or null | `null` | no | Local cache for object metadata such as the file size. `null` caches nothing. |

Fields of `metadataCacheSettings`:

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `enabled` | Boolean | `false` | Turns the cache on. |
| `maximumSize` | Long | `10000` | Maximum number of cached entries. |
| `expireAfterAccessSeconds` | Long | `86400` | Seconds after the last read until an entry expires. |
| `expireAfterCreationSeconds` | Long | `86400` | Seconds after an entry was created until it expires. |

The cache is local to one instance. Instances do not share it.

The setting names are camelCase (`bucketName`, `accessKey`, `secretKey`). Secret fields are masked in the settings API when `hideSettingsSecrets` is `true`, see [Settings domains](settings-domains.md#settings-api).

### Region and credentials

| Item | Behavior |
| --- | --- |
| Region | The `region` setting. When empty, the environment variable `AWS_REGION`. When that is not set either, the placeholder region `reposilite`, which suits S3-compatible servers that ignore the region. |
| Credentials | When both `accessKey` and `secretKey` are set, they are used. Otherwise no credentials are passed to the client, and the AWS SDK applies its own default behavior. |
| Retries | A failed request is retried up to 5 times with exponential backoff between 1 second and 1 minute. |

### System properties

These are JVM system properties and have no environment variable form. Pass them in `JAVA_OPTS`. See [Environment variables](environment-variables.md#s3-storage-system-properties).

| System property | Default | Meaning |
| --- | --- | --- |
| `reposilite.s3.pathStyleAccessEnabled` | `false` | Uses path-style addresses (`https://host/bucket/key`). |
| `reposilite.s3.use-s3-v4-signer` | `false` | Signs requests with the AWS S3 V4 signer. |
| `reposilite.s3.skip-bucket-creation` | `false` | Does not create the bucket at start when it does not exist. By default Ingot creates a missing bucket. |

They apply to every S3 repository of the instance. There is no per-repository setting for them.

### Object keys

| Settings | Object key prefix |
| --- | --- |
| `prefix` empty, `sharedBucket` `false` | none: objects are stored at the root of the bucket |
| `prefix` set, `sharedBucket` `false` | `<prefix>/` |
| `sharedBucket` `true` | `<prefix>/<repository id>/`, or `<repository id>/` when `prefix` is empty |

Two S3 repositories with the same endpoint and bucket whose key prefixes are equal, or where one is inside the other, would mix their files. Ingot does not load these repositories and records a failure for each. The failure message asks for a distinct `prefix`, or for `sharedBucket` on every repository that shares the bucket.

### Quota and usage

| Aspect | Behavior |
| --- | --- |
| Quota enforcement | None. The S3 provider accepts every upload, and it has no `quota` field. |
| Usage report | The storage usage API lists every object of the repository to add up the size and the file count. For S3 repositories `quota` and `quotaBytes` are `null`. |
| Cost of the report | Listing takes time on a large bucket. The report is computed in the background. |

## Storage usage API

The storage usage API reports the measured usage of all repositories. It needs the manager permission.

| Route | Purpose |
| --- | --- |
| `GET /api/maven/storage` | Returns the last computed usage. The first request starts a computation, and `computing` is `true` until it finishes. |
| `POST /api/maven/storage/refresh` | Starts a new computation and answers `202` at once with the previous figures. A computation that is already running is not started twice. |

Response fields:

| Field | Meaning |
| --- | --- |
| `computedAt` | ISO-8601 instant of the last computation, or `null` before the first one. |
| `computing` | `true` while a computation runs. The figures are then the previous ones. |
| `totalBytes` | Sum of `usedBytes` of all repositories. |
| `repositories[].id` | Repository id. |
| `repositories[].usedBytes` | Total size of the files. |
| `repositories[].fileCount` | Number of files. |
| `repositories[].quota` | The configured quota, or `null` when there is none. S3 repositories and groups have none. |
| `repositories[].quotaBytes` | The quota in bytes, or `null` when unknown. A percentage is taken of the total capacity of the disk. |
| `repositories[].storageType` | `fs` or `s3`. |
| `repositories[].group` | `true` for a repository that only proxies other local repositories. |

## Related pages

* [Settings domains](settings-domains.md)
* [Environment variables](environment-variables.md)
* [Configuration file and startup parameters](configuration-and-startup.md)
