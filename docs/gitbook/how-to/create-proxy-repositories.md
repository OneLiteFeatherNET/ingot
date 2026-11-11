---
description: Create a proxy repository in Ingot that fetches from Maven Central or another remote repository, with credentials, cache storage, mirror cache behaviour and the typed PROXY rules.
---

# Create a proxy repository

A proxy repository serves artifacts from one or more remote repositories, such as Maven Central. Build tools then use one Ingot URL for everything. Ingot asks the remote hosts in the order you list them, and it can keep a copy of each file it fetches.

## Before you begin

* A manager account, so that you can change the maven settings.
* The URL of each remote repository, for example `https://repo.maven.apache.org/maven2` for Maven Central. The URL is the root of the repository, without a group path.
* Credentials for each remote, only if the remote requires them.
* Storage for the cached copies. The default is the local disk of the instance. For several replicas or large caches, use an S3 bucket, see [Use S3 storage](use-s3-storage.md).
* Read the [repository types reference](../reference/repository-types.md) if you want to declare the type. The type is optional.

## Declare the proxy

Add a repository to the maven settings and list each remote under `proxied`. Each entry has a `reference`, which is the URL of the remote. This example proxies Maven Central and keeps copies:

```json
{
  "id": "central",
  "type": "PROXY",
  "visibility": "PUBLIC",
  "proxied": [
    {
      "reference": "https://repo.maven.apache.org/maven2",
      "store": true
    }
  ]
}
```

Save the settings in the dashboard, or send the whole maven domain to `PUT /api/settings/domain/maven`.

{% hint style="info" %}
The `type` field is optional. Without it, the repository keeps the behaviour of earlier versions. A typed `PROXY` also refuses uploads and rejects a local repository in `proxied`. See [typed and untyped repositories](#typed-and-untyped-repositories) below.
{% endhint %}

## Add the remote options you need

Each `proxied` entry accepts the options below. Leave an option out to use its default.

| Option | Default | Use |
| --- | --- | --- |
| `reference` | none | The URL of the remote repository, or the id of another local repository. |
| `store` | `false` | Keep a copy of each fetched file on the local storage of the proxy. |
| `checksumPolicy` | `ignore` | Check the stored copies against the checksum files of the remote: `ignore`, `warn` or `fail`. Needs `store` set to `true`. See [Check checksums](#check-checksums). |
| `authorization` | none | Credentials for the remote, see below. |
| `allowedGroups` | empty, all groups allowed | Group paths that may be fetched, for example `org/apache`. |
| `allowedExtensions` | `.jar`, `.war`, `.aar`, `.pom`, `.xml`, `.module`, the checksum files, `.asc` and `.sigstore.json` | File extensions that may be fetched. Use `<none>` to allow files without an extension. |
| `connectTimeout` | `3` | Seconds to wait for a connection to the remote. |
| `readTimeout` | `15` | Seconds to wait for data from the remote. |
| `httpProxy` | empty | An outgoing HTTP or SOCKS proxy, see below. |
| `authenticatedFetchingOnly` | `false` | Ask only callers that are logged in to reach this remote. |

An empty `allowedGroups` or `allowedExtensions` list allows everything that the other list permits. `allowedGroups` matches the start of the group path, so `org/apache` also matches `org/apache/maven`.

### Add credentials

Set `authorization` when the remote needs a login or a token. Two methods exist:

```json
"authorization": { "method": "BASIC", "login": "deployer", "password": "secret" }
```

```json
"authorization": { "method": "CUSTOM_HEADER", "login": "X-Api-Key", "password": "secret" }
```

With `BASIC`, the login and password become a basic authentication header. With `CUSTOM_HEADER`, `login` is the header name and `password` is the header value. The password is secret. The dashboard and the settings API hide it when `hideSettingsSecrets` is on.

### Use an outgoing proxy

Set `httpProxy` when the instance can only reach the remote through another proxy. The value has one of these forms:

```text
HTTP 127.0.0.1:1081
SOCKS 127.0.0.1:1080 login password
```

The first word is the protocol, `HTTP` or `SOCKS`. Credentials for the outgoing proxy are set as the default authenticator of the Java process, so they apply to the whole instance, not only to this repository.

## Check how the proxy answers

When a request arrives, Ingot does the following for the proxy:

1. It looks in the local storage of the repository first. A file that is already stored is served from there.
2. If the file is not stored, it asks the remote hosts in the order of `proxied`. The first host that answers with the file wins.
3. If every host answers `404`, the client gets `404`. With the default local-first resolution, an upstream error from a host is replaced by the local result, so the client gets `404` as well. Only remote-first resolution passes an upstream error on.

{% hint style="info" %}
Maven metadata is the exception. With the default storage policy `PRIORITIZE_UPSTREAM_METADATA`, Ingot asks the remote first for `maven-metadata.xml` when the local copy is older than `metadataMaxAge` seconds. The default `metadataMaxAge` is `0`, so the remote is asked on every request for metadata. `STRICT` does not give a fully offline copy: a file that is missing locally is still requested from the mirror hosts, so do not rely on it to run without network access.
{% endhint %}

## Choose the status for upstream failures

By default (`INGOT_PROXY_UPSTREAM_STATUS=legacy`) the proxy keeps the statuses Reposilite always answered. A file that is not stored locally gives `404` for any upstream failure, because the local result replaces the error. A metadata request, which asks the remote first, gives `406` when the upstream answers an error status or an HTML page, and `400` when the upstream is unreachable or times out. The message of the `400` is `Upstream request failed`.

With `INGOT_PROXY_UPSTREAM_STATUS=strict` the proxy reports what went wrong, for artifacts and metadata alike:

| Upstream | Client gets |
| --- | --- |
| Answers `404` or `410` | `404` |
| Times out (connect or read) | `504` |
| Unreachable, answers another error status, or answers an HTML page | `502` |
| Stored locally | The stored file, in both modes |

A group repository goes on with its next member after a `404` or a failing member, and answers with the first member that has the file. When every member fails, the group answers with the worst status, so a down member gives `502` rather than a false `404`. In `strict` an upstream `404` for a metadata file is also remembered when `resolutionCacheMaxEntries` is above `0`, within that limit.

{% hint style="warning" %}
Gradle goes to the next repository only on `404`. A `502` or `504` from a repository fails the build, so choose `strict` only when you want a failing mirror to be visible and not silently skipped. The setting and its fallbacks are listed in [Environment variables](../reference/environment-variables.md#proxy-upstream-status).
{% endhint %}

## Keep a copy with store

With `store` set to `true`, a fetched file is written to the storage of the repository, at the same path, before the client receives it. The next request for that file is served from storage.

* Ingot records the directory as a mirror copy, with the remote it came from and the time of its last access. A directory that already holds deployed artifacts is not recorded, so deployed content is never treated as cache.
* Cleanup policies can remove mirror copies. See [Set cleanup policies](set-cleanup-policies.md).
* A file served from storage refreshes the last access of its directory.

With `store` set to `false`, the file is streamed to the client and not kept. When `resolutionCacheMaxEntries` is above `0`, Ingot also remembers which remote answered a metadata lookup, so that repeated lookups go to that remote first. A proxy with `store` set to `false` on every host has no cache copies to clean up.

The copy and the request share one fetch when every host of the repository has `store` set to `true`. Two clients that ask for the same missing file at the same time then cause one remote request.

{% hint style="warning" %}
With several replicas, a cache on local disk is per pod. Each pod fetches and keeps its own copy. Use [S3 storage](use-s3-storage.md) for the repository when the replicas should share the cache. See [Run on Kubernetes](run-on-kubernetes.md).
{% endhint %}

## Check checksums

With `checksumPolicy` the proxy compares the artifact files it stores with the checksum files that the same remote publishes next to them, `<file>.sha256` first and `<file>.sha1` when there is no `.sha256` file. Metadata files and checksum files are not checked.

| Value | Behavior |
| --- | --- |
| `ignore` | Stores the copy without a check, as earlier versions did. This is the default. |
| `warn` | Stores and serves the copy. A mismatch, or a remote without a checksum file, is written to the log with the file name and the remote. |
| `fail` | Stores and serves a copy only when it matches. A copy that does not match, or a remote without any checksum file, is refused, and the next remote in `proxied` is tried. When every remote is refused the client gets `502 Bad Gateway`, and nothing is stored. |

Each mismatch and each missing checksum file under `warn` or `fail` is also written to the audit log as `MIRROR_CHECKSUM_MISMATCH` or `MIRROR_CHECKSUM_MISSING`, and counted in `ingot_mirror_checksum_results_total`. See [audit events](../reference/audit-events.md#mirrors-and-protected-groups) and [monitor status and failures](monitor-status-and-failures.md).

The example enables the check for one remote:

```json
{
  "reference": "https://repo.example.com/releases",
  "store": true,
  "checksumPolicy": "fail"
}
```

Points to know before you choose `fail`:

* The check runs on the copies fetched after the policy is set. Copies that are already stored are not checked again.
* `warn` and `fail` need `store` set to `true`. A remote with `store` set to `false` is refused when the configuration is validated, and the message names `checksumPolicy`.
* Signature files such as `.asc` and `.sigstore.json` are artifact files, so `fail` checks them too. A remote that publishes no checksum for them is refused.
* A checksum file that the remote serves wrong makes every copy from that remote fail. Compare with another remote before you rely on one.

## Typed and untyped repositories

You can leave `type` out or set it to `PROXY`.

| | Untyped (no `type`) | Typed `PROXY` |
| --- | --- | --- |
| Uploads | Accepted | Refused with `405` |
| `proxied` | Any mix of URLs and local ids | At least one URL, no local ids |
| Rule check | None beyond the general settings rules | Checked when the settings are saved |

A typed proxy is the clearest choice for a pure mirror. The typed rules and their error messages are listed in the [repository types reference](../reference/repository-types.md).

## Verify the proxy

1. Ask the proxy for a file that the remote holds, through the Ingot URL:

   ```bash
   curl -fI https://repo.example.org/central/org/slf4j/slf4j-api/2.0.13/slf4j-api-2.0.13.pom
   ```

   The answer is `200 OK`.

2. With `store` set to `true`, check that the file now exists in the storage of the repository. The default location is `repositories/central/` in the working directory of the instance. Ingot returns the file from storage on the next request, even if the remote is down.

3. Check the repository list at `GET /api/maven/repositories`. A typed proxy shows `"type": "proxy"` and `"typeExplicit": true`.

## Related

* [Create a group repository](create-a-group-repository.md) puts several local repositories behind one URL.
* [Create hosted repositories](create-hosted-repositories.md) covers repositories that store your own uploads.
* [Use S3 storage](use-s3-storage.md) for shared or large caches.
* [Set cleanup policies](set-cleanup-policies.md) to remove old cache copies.
