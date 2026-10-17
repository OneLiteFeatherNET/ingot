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
| `authorization` | none | Credentials for the remote, see below. |
| `allowedGroups` | empty, all groups allowed | Group paths that may be fetched, for example `org/apache`. |
| `allowedExtensions` | `.jar`, `.war`, `.aar`, `.pom`, `.xml`, `.module`, the checksum files and `.asc` | File extensions that may be fetched. Use `<none>` to allow files without an extension. |
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
