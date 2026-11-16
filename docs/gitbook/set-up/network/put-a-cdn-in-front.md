# Put a CDN in front

A CDN or a company cache in front of Ingot can serve most downloads without asking the instance again. This page says which answers such a cache may store, how it treats credentials, and which optional switches help it. The reference values are in [Web routes](../../references/http-api/web-routes.md) and [Environment variables](../../references/configuration/environment-variables.md#maven-cache-control).

## Before you begin

* Ingot runs behind its proxy as described in [Run behind a reverse proxy](run-behind-reverse-proxy.md). The cache sits in front of that proxy.
* Decide which repositories are public. Only a `PUBLIC` repository can be stored by a shared cache.

## Which answers a cache may store

Ingot sets `Cache-Control` on every file answer. The cache must follow it:

| `Cache-Control` | When Ingot sends it | May a shared cache store it |
| --- | --- | --- |
| `public, max-age=<seconds>` | A release file of a `PUBLIC` repository, or of a group whose local members are all `PUBLIC`, that does not allow redeployment, and that is not generated metadata. A remote mirror with credentials does not qualify. | Yes |
| `private, max-age=<seconds>` | A file of a `HIDDEN` or `PRIVATE` repository, or of a group that reaches one. Also a file that a remote mirror with `authorization` serves, even when its repository is `PUBLIC`. | No |
| `no-cache, no-store, max-age=0` | Snapshots, `maven-metadata.xml` and its checksums, a refused `401`, a missing file, and every directory listing. | No |

The rule for the cache is short: store only the `public` answers, and never a `private` one. The `max-age` is 3600 seconds unless the system property `reposilite.maven.maxAge` changes it. Ingot sets no `s-maxage`, so the TTL of the cache is the one you configure on the cache.

## Credentials

A request that carries `Authorization` is answered for the caller who sent it. For a private repository the cache must not answer such a request from its store, because the store may hold the answer of another caller. Use one of these two setups:

* The cache forwards `Authorization` to Ingot and does not answer a request that carries it from its store.
* The cache bypasses its store for every request that carries `Authorization`.

Ingot sends `private` for those answers in any case, so a cache that follows the table above never keeps them.

## Configure the cache

A recipe that matches the table above:

1. Cache only responses whose `Cache-Control` starts with `public`. Do not cache responses with `private` or `no-store`.
2. Forward `Authorization` to Ingot, and never answer a request that carries it from the store for a private repository.
3. Keep the TTL in the cache. Ingot does not set a shared-cache lifetime for you.
4. Do not cache the `/api/` routes. They answer per caller.

## Purge changed files

A release that is deleted and deployed again keeps its old bytes in the cache until the TTL runs out. The optional purge plugin tells the cache which URLs changed on each deploy and each delete:

* Copy the plugin jar `cdn-purge-plugin.jar` into the plugins directory of the instance. It is not part of the image.
* Set `INGOT_CDN_PURGE_URL` to the endpoint of the cache, and `INGOT_CDN_PURGE_BASE_URL` to the public address of the instance. A purge URL without a base URL stops the start.
* Set `INGOT_CDN_PURGE_TOKEN` when the endpoint needs a bearer token. The token is a secret.

The plugin sends one request for each deployed or deleted file, and a cleanup run sends one request for each file it removes. There is no retry, and no batching. A failed request is logged, and the deployment or the deletion answers as it would without the plugin. Map the request body `{"urls": ["<public url>"]}` to your CDN with a small adapter when its own purge API differs.

## Presigned redirects

Without help, every download of an S3 repository passes through Ingot. Set `INGOT_S3_PRESIGNED_REDIRECTS=true` to let the bucket serve the bytes of cacheable public files instead. The `302` carries `Cache-Control: public` with a `max-age` no longer than half the lifetime of its URL, so a cache drops it before the URL expires. `INGOT_S3_PRESIGN_TTL_SECONDS` sets the lifetime, and `INGOT_S3_PRESIGN_ENDPOINT` sets the address in the URL when clients reach the bucket under another name. The switch is off by default. See [Presigned redirects](../cluster/use-s3-storage.md#presigned-redirects).

Enable the switch only when every client follows a `302` for a `GET`. Maven and Gradle are expected to, but check the clients you run before you turn it on.

## Maven and Gradle

Maven and Gradle ignore `Cache-Control` and read the same files, so no client setting changes. A client that reads through the cache gets the same bytes as one that reads from Ingot.

If your cache must store a file of a credentialed mirror, you have to restore the old behaviour with `INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC=true`. Do that only when the cache may keep access-controlled files for other clients. See [Maven cache control](../../references/configuration/environment-variables.md#maven-cache-control).

## Verify

* Download a release from a `PUBLIC` repository through the cache twice. The second answer should come from the cache.
* Request a file of a private repository with `Authorization` through the cache. The answer should come from Ingot and carry `private` or `no-cache, no-store`.
* Deploy a file again, and check that the purge endpoint received its URL.

## Related

* [Run behind a reverse proxy](run-behind-reverse-proxy.md), for the proxy in front of Ingot.
* [Use S3 storage](../cluster/use-s3-storage.md), for the bucket and its presigned redirects.
* [Environment variables](../../references/configuration/environment-variables.md), for every switch named here.
