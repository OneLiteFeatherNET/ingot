# Design

## Context

Three pieces of the current code decide what a shared cache sees.

- `attachmentHeaders` in `reposilite-backend/src/main/kotlin/com/reposilite/shared/extensions/JavalinExtensions.kt` writes
  `Cache-Control` from two inputs: the `cache` flag and the `CacheScope`. The `maxAge` value is read from the system property
  `reposilite.maven.maxAge`, default 3600. No `s-maxage`, no `immutable` and no `Vary` is written for file answers.
- `Repository.acceptsCachingOf` in `reposilite-backend/src/main/kotlin/com/reposilite/maven/Repository.kt` is `!redeployment`
  and not generated metadata. Release files of a repository without redeployment therefore get `public, max-age=3600`.
  Snapshots of a redeployable repository get `no-cache, no-store, max-age=0`.
- `resolveCacheScope` in `reposilite-backend/src/main/kotlin/com/reposilite/maven/CacheScopeRule.kt` walks the local
  repositories reached through loopback references. `cacheScopeNode()` keeps only those references. A remote mirror is not
  looked at, so its `authorization` never reaches the rule. This is the cause of F1.

Downloads of S3 repositories are read in `S3StorageProvider.getFile` through `s3.getObject`, and the response body is
streamed by Ingot. Nothing in the code creates a presigned URL.

`fix-private-cache-control` is on main with its code and its documentation. Its change directory is not yet archived, and
this change does not touch it.

## Decisions

### D1 A credentialed mirror makes the answer private

The rule of `fix-private-cache-control` (D2 there) leaves remote references out, because this instance cannot judge the
access rules of an upstream host. A mirror with `authorization` is different: the operator told Ingot that the upstream
refuses anonymous readers. The content that comes through such a mirror is therefore not meant for every caller, and the
answer is `private`. The rule stays local to the repository and its loopback references, so `cacheScopeNode()` gets one more
flag: a remote mirror whose `authorization` is set. A group that reaches such a mirror is private because the walk reaches
the group's node, and a node with the flag returns `PRIVATE` in `resolveCacheScope`.

Alternative considered: keying the answer on the request. Rejected for the reason given in `fix-private-cache-control` D1. An
anonymous request to a credentialed proxy can still be answered from the stored copy, and a shared cache cannot tell the
two callers apart.

The escape hatch `INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC` covers this group as well, because `resolveCacheScope` returns `PUBLIC`
for every answer when it is set. No second switch is added.

### D2 Vary is checked before it is changed

The file answers of `MavenEndpoints` and `MavenLatestApiEndpoints` set no `Vary`. Whether the answer may differ by
`Accept-Encoding` depends on the compression strategy (`compressionStrategy`, default `none`, in
`LocalConfiguration.kt`) and on what the Jetty layer under Javalin adds. The test comes first. Code is added only if the
test shows the header missing.

### D3 Presigned redirects are opt-in and narrow

- **Switch.** `INGOT_S3_PRESIGNED_REDIRECTS` is read once at start, in the same way as `INGOT_S3_BUFFER_TO_DISK`. Only `true`
  and `false` are accepted, in any case, and any other value stops the start. Empty means `false`. The legacy spelling
  `REPOSILITE_S3_PRESIGNED_REDIRECTS` is the fallback.
- **Which answers.** A redirect is only made for a `GET` of a file that is cacheable (`acceptsCachingOf` is true), stored in
  an S3 repository, and requested without a `Range`, `If-None-Match`, `If-Modified-Since` or `If-Range` header. Every other
  answer is served by the instance exactly as before. `HEAD` is not redirected. Metadata and snapshots are never redirected,
  so their freshness rules stay in the instance.
- **After the access check.** The redirect is produced only after the normal read check of the request, so the presigned URL
  is given only to a caller who could read the file. The URL is a bearer for its lifetime, and this is the reason for the
  next rule.
- **Cache-Control of the redirect.** The `302` carries the scope value of the file, `public` or `private`, with
  `max-age = min(maxAge, TTL / 2)`. A shared cache that stores the redirect must drop it before the presigned URL expires.
  The halved value keeps a margin for clock skew. A private scope is never stored by a shared cache, so the URL is not
  stored there either.
- **Lifetime.** `INGOT_S3_PRESIGN_TTL_SECONDS`, default 300, accepted range 60 to 3600. A value outside the range or not a
  number stops the start, naming the variable.
- **Endpoint.** The presigner uses the configured S3 endpoint. Many S3-compatible servers are reachable only inside the
  cluster, so `INGOT_S3_PRESIGN_ENDPOINT` can name the address that clients use. An empty value means the configured endpoint.
  Path style and the V4 signer follow the existing system properties.
- **Clock.** The SDK presigner reads the system clock for the signature date. The unit tests therefore assert the bucket,
  the key, the endpoint and `X-Amz-Expires`, and never the signature date.
- **Storage interface.** `StorageProvider` gets `presignedGetUrl(location, ttl): String?` with a default that returns `null`.
  The default keeps every other provider and every plugin compiling, and a `null` answer means "serve through the instance".
- **Status code.** `302 Found`, because Maven, Gradle and common HTTP clients follow it for `GET`. If a client does not follow
  it, the switch stays off, and the guide says so. Open question Q4.

### D4 Purge is a plugin on the existing events

`DeployEvent` and `DeleteEvent` already reach plugins (`com.reposilite.maven.api`, listed in `plugin-api.md`). The purge
webhook is therefore a plugin, which needs no change to the core and no change to the image.

- **Request.** One `POST` per event, with a JSON body `{"urls": ["<public url>"]}`. The public URL is
  `INGOT_CDN_PURGE_BASE_URL` joined with the repository id and the path, with each path segment percent-encoded. The request
  carries `Authorization: Bearer <INGOT_CDN_PURGE_TOKEN>` when the token is set. Its timeout is 5 seconds.
- **Off by default.** An empty `INGOT_CDN_PURGE_URL` means the plugin sends nothing. A set URL without a base URL stops the
  plugin's start with a message that names the variable.
- **Failure isolation.** The event bus has no exception isolation, so the listener catches every failure, logs it and returns.
  A deployment answers the same way whether the purge succeeded or not. There is no retry (Q5).
- **Cluster.** An event fires on the replica that handled the request or that runs the cleanup. Each replica with the plugin
  purges its own events, and no coordination is needed.
- **Bundling.** The plugin is not added to the Dockerfile. The image stays as it is, and the operator opts in by copying the
  jar into the plugins directory, as for any other plugin (Q6).

### D5 No immutable directive

`immutable` tells a cache that a URL never changes for its lifetime. Two facts rule it out here. First, `maxAge` is one hour
and has no environment variable, so the directive would add almost nothing in the cache's freshness model. Second, a release
of a repository without redeployment can be deleted and deployed again, and an `immutable` answer keeps the old bytes until
the directive expires. Purging by the webhook helps only when it is installed, so the default must be safe without it.

### D6 The CDN guide states the rules, and the reference pages keep the values

The guide repeats the table of `Cache-Control` values from `web-routes.md` and states the rules that a CDN must follow:

- a cache stores only the `public` answers and never a `private` one;
- for a private repository the CDN must forward `Authorization` to Ingot and must not answer a request that carries it from
  its store, or it must bypass the cache for such requests;
- the CDN sets its own TTL, because `s-maxage` is not set by Ingot;
- the purge webhook and the presigned redirect are optional, and they are described with their variables.

The guide does not duplicate the reference tables. It links to them, and a scenario in `specs/cdn-guide` requires the values
and variable names in both places to match.

## Open questions and their defaults

- **Q1 Is F1 a default change?** Default: yes, as a fix. It follows the precedent of `fix-private-cache-control`, whose
  header change for private repositories is documented in `upgrade-changes.md`. The drop-in rule says defaults do not change,
  so the maintainer confirms this reading before the fix pull request is opened. The escape hatch covers operators who
  disagree.
- **Q2 Presign TTL default.** 300 seconds, range 60 to 3600.
- **Q3 Presign endpoint.** Empty, which means the configured endpoint.
- **Q4 Redirect status.** `302`, see D3.
- **Q5 Purge retry and batching.** None. One request per event. A cleanup run sends one request per deleted file, and the
  guide says so.
- **Q6 Plugin bundling.** Not bundled. A later change may bundle it behind a switch, as the metrics plugin is.
- **Q7 Vary.** Added only if the F2 test shows it missing (D2).

## Non-goals

- No change to `Repository.acceptsCachingOf`, to the ETag rule, to `maxAge`, or to the listing headers.
- No change to who may read a repository, and no change to the stored copies of proxied files.
- No provider-specific purge API.
