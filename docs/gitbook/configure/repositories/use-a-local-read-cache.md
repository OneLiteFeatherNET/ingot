---
description: Put a local read cache in front of a remote storage provider, such as an S3 bucket, so that release files are served from the disk of the node after the first download.
---

# Use a local read cache

A repository whose files sit in a remote store, such as an S3 bucket, downloads the same release again for every build that needs it. The storage cache keeps a copy of each immutable file on the disk of the node that serves it. The first download fills the copy, and later downloads read it locally.

The cache is a storage type of its own. It wraps another storage provider, which stays the authority for every file. The cache is not part of the Ingot image. It is installed as a plugin jar.

## When to use it

* Use it when the files of the repository are in a remote store that costs latency or egress.
* Do not use it when the store is already local, such as a file system on the same disk. The cache would only add a second copy.
* Do not use it for a repository that replaces release files. A replaced file can be served from a copy until that copy is invalidated or evicted. Disable redeployment for such a repository, see [Create hosted repositories](create-hosted-repositories.md).

## Install the plugin

Put the plugin jar, `storage-cache-plugin.jar`, into the `plugins` folder of the server. It is published as `net.onelitefeather.ingot:storage-cache-plugin`. Without the jar, no repository can use the type `cache`, and nothing else changes.

## Configure a repository

Set the storage provider of the repository to the type `cache` and put the storage it wraps into the `delegate` field:

```json
{"type":"cache","delegate":{"type":"s3","bucketName":"maven-releases"},"maxSize":"20GB"}
```

Only `delegate` is required. Every other field has a default:

| Field | Default | Meaning |
| --- | --- | --- |
| `delegate` | none | The storage provider that holds the files. Any installed type except `cache`. |
| `directory` | `<working directory>/cache/<repository name>` | Where the copies are kept. It must be on the disk of this node, not a shared volume. |
| `maxSize` | `5GB` | The most the copies may take. Units are `B`, `KB`, `MB`, `GB` and `TB` with binary sizes. |
| `maxFileSize` | no limit | The largest file that is copied. Larger files are always read from the delegate. |
| `cacheOnUpload` | `false` | Copy an upload into the cache as it is stored. Otherwise a file is copied on its first download. |
| `cacheMutable` | `false` | Must stay `false`. Metadata and snapshot files are never copied. |

The cache copies a file only when it is immutable. Metadata such as `maven-metadata.xml`, its checksums, and every file inside a `-SNAPSHOT` directory always come from the delegate.

## Sizing the copies

The cache removes the least recently downloaded copy when a new one does not fit under `maxSize`. A copy that a client is still reading is never removed. When every copy is in use, a new download is served from the delegate and is not copied; the skipped download is counted. Pick a `maxSize` that holds the releases that are downloaded often. A file larger than `maxSize` is never copied.

## Several nodes

Each node needs its own cache directory. Do not point two nodes at one shared volume: the directory lock only guards one node, and the lock is weak on network file systems.

When a removal or an overwrite happens on one node of a cluster, the other nodes drop their copy of that file over the cluster bus. This needs the cluster to be configured, see [Cluster](../../set-up/cluster/README.md). A node that is down while the change is made keeps its copy until the copy is evicted or the cache directory is cleared. For that reason the cache suits releases that are not replaced.
