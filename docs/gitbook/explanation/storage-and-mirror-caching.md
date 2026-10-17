---
description: Explains how Ingot resolves files in proxy and group repositories, when it stores upstream copies, how loopback references work, and how the mirror cache ledger, the resolution cache and cleanup relate.
---

# Storage and mirror caching

A repository can serve files that it does not own. It can fetch them from a remote Maven repository, or from another repository on the same server. This page explains how Ingot decides where a file comes from, when it keeps a copy, and how those copies are tracked, invalidated and cleaned up. It contains no steps. For the settings themselves, see the repository settings reference.

## Repository kinds

A repository has no `type` setting. Its kind follows from its `proxied` list:

* **Hosted**: the `proxied` list is empty. Files come only from the repository storage. Deployments write there.
* **Proxy**: the `proxied` list contains remote URLs. Missing files are fetched from those URLs.
* **Group**: the `proxied` list contains only ids of other repositories on this server. Requests are passed to those repositories.

Nothing stops a repository from listing both URLs and local ids. Ingot then treats it as both.

Every repository keeps its files in its own storage provider, either the filesystem or S3. Mirror copies go to that same storage, next to the deployed files.

## How a request is resolved

For most files, Ingot tries the local storage first. Remote hosts are tried only when the local copy is missing. The order changes for one kind of file under the default storage policy:

* **Local first** (the default for all files except metadata, see below). A local copy is served if it exists. If not, the mirror hosts are tried in the order they are listed. The first host that has the file wins.
* **Remote first** for `maven-metadata.xml` under `PRIORITIZE_UPSTREAM_METADATA`, the default policy. Ingot asks the upstream hosts first, unless the local copy is younger than `metadataMaxAge`. A `metadataMaxAge` of 0 means the upstream is asked on every request.
* **Remote first** when the resolution cache has recorded that a host serves this directory (see [Resolution cache](#resolution-cache)).

A file must pass the rules of a host before Ingot asks that host. The host is skipped when:

* the file extension is not in `allowedExtensions`. The default list covers `.jar`, `.war`, `.aar`, `.pom`, `.xml`, `.module`, the checksum types and `.asc`. Extensionless files need the marker `<none>`.
* the artifact group is not in `allowedGroups`, when that list is set.
* the host has `authenticatedFetchingOnly` set and the caller is anonymous.

When no host is eligible and the local copy is missing, the request is answered as not found.

Errors from upstream hosts are handled differently by path:

* In local-first resolution, an upstream error is replaced by the local result. The client sees not found, not the upstream status.
* In remote-first resolution, the upstream error is passed on when the local copy is missing.

## Proxy references

A `proxied` entry with a URL points to a remote Maven repository. Ingot sends `HEAD` and `GET` requests to that URL with the same path as the request.

Settings that matter for a URL reference:

* `store` (default `false`). When `false`, the file is streamed to the client and not kept. When `true`, the file is written to the repository storage before it is served. The next request is then a local hit.
* `connectTimeout` (default 3 seconds) and `readTimeout` (default 15 seconds).
* `authorization`, with the method `BASIC` or a custom header. The password is a sensitive setting.
* `httpProxy`, for a proxy that the upstream must be reached through.

When several requests ask for the same file at the same time, and every host of the repository stores its copies, Ingot makes one upstream request. The other requests wait for its result. Anonymous and authenticated callers are kept apart, because a host that needs authentication can answer them differently.

## Group references

A `proxied` entry whose reference is the id of another repository on the same server is a **loopback reference**. Ingot does not make a network call for it. It asks the other repository for the file in the same process.

What this means in practice:

* The referenced repository resolves the file with its own rules, including its own mirrors. A group can therefore reach a file that sits several proxies away.
* Access to the member is checked for the credentials of the reference, not for the caller. Without credentials, that is anonymous access. A member that anonymous callers cannot see is therefore not served through the group.
* Credentials are optional. Without credentials, the request is anonymous. An entry that uses the method `LOOPBACK_LINK` sends a token instead. The login is the token name and the password is the token secret.
* `allowedExtensions` and `allowedGroups` still apply to the entry.
* The timeouts and `httpProxy` of the entry are not used. There is no network call.
* With `store` set to `true`, the group keeps a copy of what the member serves, in its own storage. The copy is recorded in the ledger with no source host, because a repository id is not a host.

`GET /api/maven/repositories/{repository}/members` lists the repositories a group refers to through loopback references. The list contains only the members that the calling token may see. External URLs are never listed.

## Copies in the mirror cache

When a copy is stored, Ingot writes a row to the mirror cache ledger. The ledger is kept in the database, so a database backup holds it too. The ledger is the only way Ingot knows that a directory holds mirror copies. Cleanup and the search index use it.

A row describes one version directory of one repository, for example `net/example/app/1.0.0`:

* The **source host** is the host name of the upstream that supplied the first copy in that directory. A port is kept when it is not the default for the scheme. Credentials, path and query are never kept. A host name longer than 255 characters is cut. A loopback copy has no source host.
* The **store time** is when the row was written.
* The **last access** is when a file from that directory was last served. It is written at most once per hour for each directory and each server process. This applies only to repositories that have at least one mirror host with `store` set to `true`.

Rules that decide when rows are written or removed:

* Only artifact files create a row. Metadata and checksum files do not.
* A directory that already holds deployed artifacts gets no row. A mirror copy written next to deployed files does not turn the directory into a copy.
* The first copy decides the source host. A later copy in the same directory does not change it.
* A deployment into the directory removes its row, because the directory now holds hosted content. When a row was removed, the search index re-labels the copies whose origin changed.
* A deletion removes the row of the deleted path and of every path below it.
* Repository names longer than 128 characters and directory paths longer than 512 characters are not recorded.

A copy without a row is never treated as a mirror copy. This includes copies from before the ledger existed. Those copies are never removed by a mirror-copy cleanup.

## Resolution cache

The resolution cache is a second cache. It does not store file content. It stores routing decisions, so that Ingot does not repeat a long search through the mirror hosts for every request.

It is off by default (`resolutionCacheMaxEntries` is 0). It covers only resolution metadata: `.pom`, `.module`, `maven-metadata.xml`, and Ivy XML files. For a directory it can record one of three results:

* **Local**: the local copy answered. The file is looked up locally first.
* **Remote** with a host: a host without local storage answered. Later requests try that host first and no other host is asked, unless that host is missing from the configuration.
* **Missing metadata**: no local copy and no host had the file. Later requests for the same file get not found at once. This record applies only to the exact directory.

Some rules keep the cache from going wrong:

* A failed upstream request is not recorded. The upstream may recover.
* Entries are kept per directory and per caller type (anonymous or authenticated).
* An entry for a directory applies to the files below it, except for the missing-metadata result.
* When the cache is full, the entry with the fewest hits is removed. The limit is soft, so the cache can briefly hold a few entries more than the limit under concurrent writes.

### Invalidation

Deploying, deleting or saving metadata drops the entries for the directory of the changed file. The change applies only when the file is a resolution metadata file. Entries for ancestor directories stay in place, because a group-level route does not change when one artifact changes.

The cache lives in memory, in each server process. A restart empties it. Each instance of a clustered deployment has its own cache.

The console command `cache resolution stats [repository] [--top N]` shows the entries with the most hits. `cache resolution purge [repository]` empties the cache for one repository or for all of them.

## Metadata caching

Two settings control how fresh `maven-metadata.xml` is:

* `metadataMaxAge` sets how old the local copy may be before Ingot asks the upstream again. The age is measured from the modification time of the local file. Under the default policy, a value of 0 means the upstream is asked on every request.
* `resolutionCacheMaxEntries` controls the routing cache described above. It does not affect the content of the file.

### The STRICT storage policy

The `STRICT` policy makes local files the first choice for every file, including metadata. The setting description calls it full offline mode. The code does not match that description. A local miss still goes to the mirror hosts, so `STRICT` does not stop upstream requests. Do not rely on `STRICT` to run without network access.

## Cleanup of mirror copies

The `MIRROR_CACHE` cleanup scope only considers directories that have a ledger row. Keep rules still apply first. The age of a copy is judged by its last access, through `notDownloadedForDays`, or by its modification time, through `olderThanDays`.

Two conditions limit what cleanup can find:

* The repository needs at least one mirror host with `store` set to `true`. Without one, cleanup reports that the scope finds nothing there.
* Only copies with a recorded row are candidates. A directory that a deployment has touched has no row, so it is never removed as a copy.

The cleanup reference covers the policy keys and the schedule.

## Summary

| Question | Short answer |
| --- | --- |
| Where does a file come from? | Local storage first, then the mirror hosts in order. Metadata may go upstream first. |
| Is the upstream copy kept? | Only when the host has `store` set to `true`. Otherwise it is streamed. |
| Can a group reach files of its members? | Yes, through loopback references, with the member's own access rules. |
| What marks a directory as a mirror copy? | A ledger row, written by a mirror store and removed by a deployment or a deletion. |
| Does a restart clear the caches? | It clears the resolution cache. The ledger and the stored copies stay. |
| Is `STRICT` an offline mode? | No. The code still asks the mirror hosts on a local miss. |
