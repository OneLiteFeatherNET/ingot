---
description: Reference for every field of a repository in the maven settings of Ingot, including visibility, redeployment, upload checksum policy, preserved snapshots, storage providers, storage policy and proxied entries.
---

# Repository settings

Repositories are configured in the `maven` settings domain, in its `repositories` list. Each entry is one repository. This page lists every field of an entry, its type and default, and what the field does.

Ingot loads the domain from the shared settings, so the dashboard (**Administration**, **Repository**, **Repositories**) and the settings API edit the same data. Saved changes apply without a restart: Ingot recreates all repositories when the domain changes.

```text
GET /api/settings/domain/maven
PUT /api/settings/domain/maven
```

Both endpoints need an instance admin. A `PUT` replaces the whole domain, so read it first and send it back with your change. A rejected update is not stored and answers `400` with a message that starts with `Invalid maven settings: `, followed by every rule that is broken, separated by `; `.

## Repositories page

The **Repositories** page of the dashboard, at `/settings/repositories`, lists the repositories of the `maven` domain in one table. Select a name to open that repository at `/settings/repositories/<id>`. A visitor needs the same permission as for **Settings**. Without it the page shows a no access message and loads no repository data.

The page edits the same draft as the rest of **Settings**. A change made on the page waits in the save bar with every other unsaved change, and **Save** writes the `maven` domain. The page adds no endpoint of its own.

### Maven options

Below the table, the section **Maven options** edits the fields of the `maven` domain that are not repositories. Today that is one field, `resolutionCacheClusterMaxAgeSeconds`, the maximum age of a cached routing decision in cluster mode. See [Maven domain](settings-domains.md#maven). The section is saved with the repositories, and a change to it does not change any repository.

### Columns

The table lists every repository of the draft. A repository that is not saved yet has the text **Unsaved** in its row.

| Column | Shows |
| --- | --- |
| Name | The id. Links to the repository's page. |
| Type | The type in words: Hosted, Proxy, Group or Mixed. See [Derived type](#derived-type). |
| Visibility | Public, Hidden or Private. |
| Version policy | The version policy of the repository. Shown only when the settings schema of the server declares `versionPolicy`. A group shows a dash. |
| Write policy | Allow, Allow once or Read only, for a hosted repository. See [Write policy](#write-policy). A group or proxy shows a dash. Without `readOnly` in the settings schema, the column shows Allow or Allow once from `redeployment` alone. |
| Storage | File system or S3. A group shows a dash. |
| Address | The address of the repository, with a button that copies it. |

### Derived type

A repository without a `type` key is untyped. The page works out its type from its references in the draft, with the same rule the server uses:

| References | Type |
| --- | --- |
| None | Hosted |
| Only names of repositories on this instance | Group |
| Only `http://` or `https://` addresses | Proxy |
| Both | Mixed |

Blank references are ignored. The badge of an untyped repository says **derived** next to the type.

**Declare type** writes the derived type to the `type` key, and nothing else. The change waits in the draft until you select **Save**. Declaring a type also drops the empty mirrored entries of the repository. When that would drop some, a dialog states how many entries are removed. Nothing changes until you confirm it.

A repository with both local and remote references has the type **Mixed**. It has no **Declare type** action, because no single declared type gives the same result. The page shows this warning next to the badge and in the detail: "References local and remote repositories. Split it into a group and a proxy."

### Write policy

The write policy of a hosted repository is a choice of three values. It is stored in two existing fields. No key is added for the choice itself.

| Choice | Stored result |
| --- | --- |
| Allow | `redeployment: true`, and `readOnly` removed or `false` |
| Allow once | `redeployment: false`, and `readOnly` removed or `false`. An upload to a file that already exists is refused, as described under [Redeployment](#redeployment). |
| Read only | `readOnly: true`. `redeployment` keeps its value. |

Choosing Read only does not change `redeployment`, so choosing Allow or Allow once later returns the repository to the state it had before. A key is written only when the choice changes it. Saving a repository without touching the control leaves its stored JSON as it was.

The response to a deploy to a Read only repository is not described on this page yet.

### Delete

To delete a repository, open its page and select **Delete**. The dialog asks you to type the id of the repository. The confirm button stays unavailable until the typed name matches. The dialog also names each group of the draft that lists the repository as a member. Focus starts on **Cancel**.

Confirming removes the repository from the draft. The change takes effect when you select **Save**, like any other change of the `maven` domain.

### Create

**New repository** opens the repository wizard. The address `/settings/repositories?new=repository` opens it once and then removes the query from the address. The wizard steps are described in [Create hosted repositories](../how-to/create-hosted-repositories.md).

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
| `protectedGroupPrefixes` | Protected group prefixes | list of dotted group ids, such as `net.onelitefeather` | empty | Group ids that a group serves from hosted members only, so that a proxy member never answers for them. Accepted only on a `GROUP`. See [Protect your own namespace](../how-to/create-a-group-repository.md#protect-your-own-namespace). |
| `uploadChecksumPolicy` | Upload checksum policy | `ignore`, `warn`, `reject`, or absent | absent, which means the instance default | What happens to an uploaded checksum file that does not match its stored artifact. Absent uses `INGOT_UPLOAD_CHECKSUM_POLICY`, which is `ignore` when unset. See [Upload checksum policy](#upload-checksum-policy). |
| `versionPolicy` | Version policy | `RELEASE`, `SNAPSHOT`, `MIXED`, or absent | absent, which means `MIXED` | Which versions a deploy may carry. Hosted and untyped repositories only. See [Version policy and read-only](#version-policy-and-read-only). |
| `readOnly` | Read-only | boolean, or absent | absent, which means `false` | Refuses deploys and deletes with `405`. Hosted and untyped repositories only. See [Version policy and read-only](#version-policy-and-read-only). |

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

Visibility also sets the `Cache-Control` of downloaded files. A file of a `PUBLIC` repository, or of a group whose local members are all `PUBLIC`, may be stored by a shared cache such as a CDN. A file of a `HIDDEN` or `PRIVATE` repository, or of a group that reaches one, is marked `private`, so a shared cache keeps it out. Remote references do not change this. The escape hatch is described in [Maven cache control](environment-variables.md#maven-cache-control).

## Upload checksum policy

`uploadChecksumPolicy` controls a checksum file (`.md5`, `.sha1`, `.sha256` or `.sha512`) that is uploaded after its artifact. Ingot digests the stored artifact with the algorithm of the suffix and compares the digest in the upload with it. The upload may hold the bare digest or the `sha1sum` form, a digest followed by two spaces and a file name.

| Value | Matching file | Mismatching file |
| --- | --- | --- |
| `ignore` | Stored | Stored, no check |
| `warn` | Stored | Stored, the mismatch is audited with outcome `accepted` |
| `reject` | Stored | Refused with `400` and the algorithm in the message. Nothing is stored, the artifact stays as it is, and the refusal is audited with outcome `rejected` |

A checksum file that arrives before its artifact is stored without a check under every value. See [Verify uploaded checksums](../how-to/verify-uploaded-checksums.md).

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
| `metadataCacheSettings` | Local Metadata Cache | object or absent | absent | Local cache for object metadata. The default is no caching. Each instance has its own cache. Mutable metadata (`maven-metadata*` and `-SNAPSHOT` directories) is never cached, and with a cluster bus an entry is used for at most 300 seconds. |

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
| `allowedExtensions` | Allowed Extensions | list of strings | `.jar`, `.war`, `.aar`, `.pom`, `.xml`, `.module`, `.md5`, `.sha1`, `.sha256`, `.sha512`, `.asc`, `.sigstore.json` | Accepted file name endings. An empty list allows all files. The value `<none>` allows files without an extension. |
| `connectTimeout` | Connect Timeout | integer (seconds), at least 0 | `3` | Time to wait for the connection to a remote host. |
| `readTimeout` | Read Timeout | integer (seconds), at least 0 | `15` | Time to wait for data from a remote host. |
| `authorization` | Mirror Credentials | object or absent | absent | Credentials for the referenced repository. See below. |
| `httpProxy` | HTTP Proxy | string | empty | HTTP or SOCKS proxy for the connection to a remote host. Sensitive. |
| `checksumPolicy` | Checksum policy | `ignore`, `warn` or `fail` | `ignore` | Checks stored copies against the checksum files of the remote. `warn` and `fail` need `store` set to `true`. See [Check checksums](../how-to/create-proxy-repositories.md#check-checksums). |
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

## Version policy and read-only

`versionPolicy` and `readOnly` are optional. A repository that does not set them behaves exactly as it did before they existed, and Ingot does not write them back to the settings document. Set them when a repository has a single job: release builds only, snapshot builds only, or a frozen repository that must not change.

`versionPolicy` applies to deploys only. Reads, deletes, mirror cache fills and cleanup runs are not affected, so a repository that changes its policy keeps the files it already holds.

| Value | A deploy into a `-SNAPSHOT` version directory | A deploy into any other version directory |
| --- | --- | --- |
| absent or `MIXED` | Accepted | Accepted |
| `RELEASE` | Refused with `400` | Accepted |
| `SNAPSHOT` | Accepted | Refused with `400`, except `maven-metadata.xml` and its checksums |

The `400` message names the repository and the policy, for example `Repository 'releases' has version policy RELEASE and does not accept the snapshot version 1.0-SNAPSHOT`. Nothing is stored when a deploy is refused. [Separate releases and snapshots](../how-to/separate-releases-and-snapshots.md) walks through the setup.

`readOnly: true` refuses every deploy, delete and POM generation into the repository with `405`, and the message names the repository. Reads keep working, and mirror cache fills of a proxied entry are not affected. Scheduled cleanup skips the repository, and the cleanup preview lists it as skipped. The flag is distinct from the read-only mode of the whole instance, which answers `503` to every write, and that answer comes first.

Both keys are rejected on a declared `PROXY` or `GROUP`, because those types take no uploads, and `versionPolicy` is rejected on a `GROUP` as well. A group does not inherit a policy: each member enforces its own.

### Write policy

The repository list and the dashboard show one write policy for every hosted and untyped repository. It is computed from `redeployment` and `readOnly` and is not stored.

| Write policy | Condition | Effect |
| --- | --- | --- |
| `ALLOW` | `redeployment` is `true` and `readOnly` is not set | Uploads may overwrite existing files. |
| `ALLOW_ONCE` | `redeployment` is `false` and `readOnly` is not set | An existing file cannot be uploaded again, see [Redeployment](#redeployment). |
| `READ_ONLY` | `readOnly` is `true` | Every deploy and delete is refused with `405`. |

The settings form edits the write policy as one choice. `ALLOW` and `ALLOW_ONCE` set `redeployment` and remove `readOnly`. `READ_ONLY` sets `readOnly` and leaves `redeployment` as it is, so switching back restores the previous redeploy choice. A proxy or a group has no write policy.

## Secrets

The fields marked sensitive on this page are the S3 `endpoint`, `accessKey` and `secretKey`, and the proxied `httpProxy` and `authorization.password`. The settings API returns them as stored. With the local setting `hideSettingsSecrets` turned on, it returns a placeholder instead and keeps the stored value when the placeholder is sent back. The setting is off by default. A write that leaves a secret out of a mirrored repository keeps the stored one. To remove a mirror credential, send `null` for its `authorization`.

## Related

* [Repository types](repository-types.md) lists the declared types and the rules they enforce.
* [Create hosted repositories](../how-to/create-hosted-repositories.md) shows how to add a repository.
* [Cleanup policies](cleanup-policies.md) lists the rules for deleting old versions.
