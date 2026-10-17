---
description: Reference for every field of a repository in the maven settings of Ingot, including visibility, redeployment, preserved snapshots, storage providers, storage policy and proxied entries.
---

# Repository settings

Repositories are configured in the `maven` settings domain, in its `repositories` list. Each entry is one repository. This page lists every field of an entry, its type and default, and what the field does.

Ingot loads the domain from the shared settings, so the dashboard (**Settings**, tab **Maven**) and the settings API edit the same data. Saved changes apply without a restart: Ingot recreates all repositories when the domain changes.

```text
GET /api/settings/domain/maven
PUT /api/settings/domain/maven
```

Both endpoints need an instance admin. A `PUT` replaces the whole domain, so read it first and send it back with your change. A rejected update is not stored and answers `400` with a message that starts with `Invalid maven settings: `, followed by every rule that is broken, separated by `; `.

## Default repositories

A new instance starts with three repositories. Their other fields have the defaults listed below.

| Id | Visibility |
| --- | --- |
| `releases` | `PUBLIC` |
| `snapshots` | `PUBLIC` |
| `private` | `PRIVATE` |

## Repository fields

| Field | Dashboard label | Type | Default | Meaning |
| --- | --- | --- | --- | --- |
| `id` | Id | string | empty | Name and address of the repository. At least 1 character. A name of 64 or more characters fails to load. |
| `visibility` | Visibility | `PUBLIC`, `HIDDEN`, `PRIVATE` | `PUBLIC` | Who can read and list the repository. See [Visibility](#visibility). |
| `type` | Type | `HOSTED`, `PROXY`, `GROUP`, or absent | absent | Declared repository type. Absent means untyped. See [Repository types](repository-types.md). |
| `description` | Description | string | empty | Plain text shown in the repository list. At most 280 characters. |
| `tags` | Tags | list of strings | empty | Labels to group and find repositories. At most 10 tags. Each tag has 1 to 32 characters: lowercase letters, digits or hyphens, starting with a letter or digit. A tag may appear once. |
| `redeployment` | Redeployment | boolean | `false` | Whether a file that already exists can be uploaded again. See [Redeployment](#redeployment). |
| `preserveSnapshots` | Preserved snapshots | boolean | `false` | Whether older builds of a snapshot stay in storage. See [Preserved snapshots](#preserved-snapshots). |
| `storageProvider` | Storage provider | object | `{"type": "fs"}` | Where the files are kept. See [Storage provider](#storage-provider). |
| `storagePolicy` | Storage policy | `PRIORITIZE_UPSTREAM_METADATA`, `STRICT` | `PRIORITIZE_UPSTREAM_METADATA` | How metadata files are resolved when the repository has proxied entries. See [Storage policy](#storage-policy). |
| `metadataMaxAge` | Max age of metadata file | integer (seconds) | `0` | How long a stored metadata file counts as current. See [Storage policy](#storage-policy). |
| `resolutionCacheMaxEntries` | Resolution cache size | integer, at least 0 | `0` | Size of the cache for routing decisions. `0` turns the cache off. See [Resolution cache](#resolution-cache). |
| `proxied` | Mirrored repositories | list of [proxied entries](#proxied-entries) | empty | Repositories this repository can fetch files from. |

The id of a repository should not equal a reserved path segment of the dashboard or the API, such as `api`, `search` or `settings`. Ingot does not reject such an id, it logs a warning at startup and on every settings change. The dashboard creates ids from letters, digits, dots, underscores and hyphens, and compares them without regard to case.

## Visibility

Visibility decides who can read a repository. Publishing and deleting always need an account or token with the matching permission, whatever the visibility is.

| Operation | `PUBLIC` | `HIDDEN` | `PRIVATE` |
| --- | --- | --- | --- |
| Download a file | Anyone | Anyone who knows the exact path | Needs read access. An anonymous request answers `401`. |
| Open a directory listing | Anyone | Needs read access, or the directory leads to a part the caller can read | Same as `HIDDEN` |
| Appears in the root listing at `/` | Always | For callers who can see the repository | Same as `HIDDEN` |
| Appears in `GET /api/maven/repositories` | Always | For instance admins and for accounts whose role or node grant includes read on the repository. A legacy path grant does not list it. | For callers who can see the repository |

"Read access" means the read permission of the repository, or a read node on an artifact inside it. The [artifact permission nodes](artifact-permission-nodes.md) page describes the nodes. A caller "can see" a repository when they can read all of it or a part of it.

## Redeployment

`redeployment` controls what happens when an upload targets a path that already holds a file.

| Value | Upload to an existing path | Cache header on downloads |
| --- | --- | --- |
| `false` | Refused with `409` and the message `Redeployment is not allowed`. | `Cache-Control: public, max-age=3600` |
| `true` | Overwrites the file. | `Cache-Control: no-cache, no-store, max-age=0` |

Files whose name contains `maven-metadata.xml`, which includes their checksums, can always be uploaded again. They always answer with the `no-cache` header. The `max-age` value comes from the system property `reposilite.maven.maxAge`, in seconds, and defaults to 3600.

## Preserved snapshots

`preserveSnapshots` controls the builds inside a `-SNAPSHOT` directory.

| Value | Behaviour |
| --- | --- |
| `false` | When a deployment stores the `maven-metadata.xml` of a snapshot version, Ingot deletes the files of the older builds in that directory. Only files of the latest timestamp in the metadata stay. Ingot then reconciles the version metadata with the files that are left. |
| `true` | Ingot keeps all builds. |

The setting applies to files that start with the artifact id, in directories whose name ends with `-SNAPSHOT`.

## Storage provider

`storageProvider` is an object whose `type` field selects the provider. The value is `fs` or `s3`.

### File system provider, `type: fs`

| Field | Dashboard label | Type | Default | Meaning |
| --- | --- | --- | --- | --- |
| `quota` | Quota | string | `100%` | Maximum amount of data in this repository. A percentage of the usable disk space (`90%`, 1 to 100), a size with the unit `KB`, `MB` or `GB` (`500MB`, `10GB`), or a plain number of bytes. |
| `mount` | Mount | string | empty | Directory of the repository data. Empty means `repositories/<id>` in the working directory. A relative value is resolved against the `repositories` directory of the working directory. An absolute path is used as it is. |

An upload that would exceed the quota is refused with `507`. A quota of 0 or less fails to load. The check compares the content length of the upload with the space that is left.

### S3 provider, `type: s3`

| Field | Dashboard label | Type | Default | Meaning |
| --- | --- | --- | --- | --- |
| `bucketName` | Bucket | string | empty | Name of the bucket. |
| `endpoint` | Endpoint | string | empty | URL of an S3 compatible service. Empty uses the AWS endpoint. Sensitive. |
| `accessKey` | Access Key | string | empty | Access key. Used only when `secretKey` is set as well. Sensitive. |
| `secretKey` | Secret Key | string | empty | Secret key. Sensitive. |
| `region` | Region | string | empty | Region. Empty uses the `AWS_REGION` environment variable, and then the value `reposilite`. |
| `prefix` | Key Prefix | string | empty | Prefix for all object keys, for a bucket that other services share. |
| `sharedBucket` | Shared Bucket | boolean | `false` | Places the objects under the repository id, so several repositories can share one bucket. |
| `metadataCacheSettings` | Local Metadata Cache | object or absent | absent | Local cache for object metadata. The default is no caching. Each instance has its own cache. |

Without both keys, Ingot uses the default AWS credential chain. Two JVM system properties change the S3 client: `reposilite.s3.pathStyleAccessEnabled=true` turns on path style access, and `reposilite.s3.use-s3-v4-signer=true` selects the V4 signer.

The S3 provider enforces no quota, so `quota` and `quotaBytes` are null, but its usage is reported like that of any other provider. Ingot does not check uploads against a size limit.

With `sharedBucket` off, the key prefix of a repository is its `prefix`. With it on, the key prefix is the `prefix` followed by the repository id. Repositories that use the same endpoint and bucket must not have equal key prefixes, and one key prefix must not start with another. Ingot does not load a repository that breaks this rule, and logs the failure. Give each repository its own `prefix`, or turn on `sharedBucket` for all of them.

Fields of `metadataCacheSettings`:

| Field | Dashboard label | Type | Default | Meaning |
| --- | --- | --- | --- | --- |
| `enabled` | Enabled | boolean | `false` | Turns the cache on. |
| `maximumSize` | Max Cache Entries | integer | `10000` | Maximum number of cached entries. |
| `expireAfterAccessSeconds` | Expire After Access | integer (seconds) | `86400` | Drops an entry this long after its last read. |
| `expireAfterCreationSeconds` | Expire After Creation | integer (seconds) | `86400` | Drops an entry this long after it was cached. |

## Storage policy

The storage policy applies to metadata files, which are files whose name contains `maven-metadata.xml`. It matters only for a repository with proxied entries. All other files are read from the repository first and fetched from the proxied entries when they are missing.

| Value | Metadata file request |
| --- | --- |
| `PRIORITIZE_UPSTREAM_METADATA` | Asks the proxied entries first. With `metadataMaxAge` of 0 or less this happens on every request. With a larger value it happens only when the stored copy is older than `metadataMaxAge` seconds. The stored copy answers when no proxied entry has the file. |
| `STRICT` | Reads the stored copy first and asks the proxied entries only when it is missing. A stored file is served without any upstream request. A missing file still goes to the proxied entries, so `STRICT` is not an offline mode. |

## Resolution cache

`resolutionCacheMaxEntries` sets the maximum number of cached routing decisions. A decision is stored per directory and per kind of caller (signed in or anonymous), for requests to metadata files (`maven-metadata.xml`, `*.pom`, `*.module`, `ivy.xml` and `ivy-*.xml`). It records whether the answer came from storage, from a certain proxied entry, or was not found. Uploading or deleting such a file invalidates the entry.

| Value | Use |
| --- | --- |
| `0` | No cache. |
| `128` | A small fixed set of artifacts. |
| `512` | Focused ecosystems with a few curated proxied entries. |
| `2048` or more | Broad ecosystems such as Maven Central. |

The size grows with the number of distinct artifact versions that requests reach through the proxied entries, not with traffic.

## Proxied entries

An entry of `proxied` describes one repository that this repository can fetch files from. When a requested file is not in the repository storage, Ingot asks the entries in the order of the list. The first entry that has the file answers. A request for a metadata file can ask the entries first, see [Storage policy](#storage-policy).

| Field | Dashboard label | Type | Default | Meaning |
| --- | --- | --- | --- | --- |
| `reference` | Link | string | empty | The id of a local repository or the URL of a remote repository. At least 1 character. A trailing slash is ignored. An empty reference is skipped with a warning. |
| `store` | Store | boolean | `false` | Saves fetched files in this repository. Without it, files are streamed and not kept. |
| `allowedGroups` | Allowed Groups | list of strings | empty | Group ids that may be fetched. An empty list allows all. A group matches when the request path starts with the group id with dots replaced by slashes. |
| `allowedExtensions` | Allowed Extensions | list of strings | `.jar`, `.war`, `.aar`, `.pom`, `.xml`, `.module`, `.md5`, `.sha1`, `.sha256`, `.sha512`, `.asc` | Accepted file name endings. An empty list allows all files. The value `<none>` allows files without an extension. |
| `connectTimeout` | Connect Timeout | integer (seconds), at least 0 | `3` | Time to wait for the connection to a remote host. |
| `readTimeout` | Read Timeout | integer (seconds), at least 0 | `15` | Time to wait for data from a remote host. |
| `authorization` | Mirror Credentials | object or absent | absent | Credentials for the referenced repository. See below. |
| `httpProxy` | HTTP Proxy | string | empty | HTTP or SOCKS proxy for the connection to a remote host. Sensitive. |
| `authenticatedFetchingOnly` | Authenticated Fetching Only | boolean | `false` | Fetches an uncached file only for authenticated callers. |

`authenticatedFetchingOnly` does not hide the repository from anonymous callers, use the [visibility](#visibility) for that. Turn it on together with `store`, or with a second entry that has it turned off. Files that are already stored stay readable for everyone who may read the repository.

`httpProxy` has the form `HTTP 127.0.0.1:1081` or `SOCKS 127.0.0.1:1080 <login> <password>`.

Fields of `authorization`:

| Field | Dashboard label | Type | Default | Meaning |
| --- | --- | --- | --- | --- |
| `method` | Method | `BASIC`, `CUSTOM_HEADER`, `LOOPBACK_LINK` | `BASIC` | `BASIC` sends the login and password as HTTP Basic authentication. `CUSTOM_HEADER` sends the password as the value of a header named by `login`. `LOOPBACK_LINK` authenticates a local reference with a token. |
| `login` | Login | string | empty | User name, header name, or the token name for `LOOPBACK_LINK`. |
| `password` | Password | string | empty | Password, header value, or token secret. Sensitive. |

### Loopback references

A `reference` that equals the id of a repository on the same instance is a loopback reference. Ingot reads the files straight from that repository, without an HTTP request, and applies the visibility and permissions of the referenced repository. The filters `allowedGroups` and `allowedExtensions`, and the options `store` and `authenticatedFetchingOnly`, apply as for a remote reference. `connectTimeout`, `readTimeout` and `httpProxy` have no effect.

A loopback reference reads as an anonymous caller unless `authorization` has the method `LOOPBACK_LINK`. With that method, Ingot authenticates with the token named by `login` and its secret in `password`. A `PRIVATE` repository needs this to be readable through a reference.

Loopback references are how a repository serves other repositories of the instance under one address. The [repository types](repository-types.md) page describes the `GROUP` type and the rules that apply when a type is declared.

## Secrets

The fields marked sensitive on this page are the S3 `endpoint`, `accessKey` and `secretKey`, and the proxied `httpProxy` and `authorization.password`. The settings API returns them as stored. With the local setting `hideSettingsSecrets` turned on, it returns a placeholder instead and keeps the stored value when the placeholder is sent back. The setting is off by default. A write that leaves a secret out of a mirrored repository keeps the stored one. To remove a mirror credential, send `null` for its `authorization`.

## Related

* [Repository types](repository-types.md) lists the declared types and the rules they enforce.
* [Create hosted repositories](../how-to/create-hosted-repositories.md) shows how to add a repository.
* [Cleanup policies](cleanup-policies.md) lists the rules for deleting old versions.
