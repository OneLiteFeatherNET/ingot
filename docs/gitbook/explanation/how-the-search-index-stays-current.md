---
description: Explains how the artifact search index is kept current through an outbox, verify and rebuild, what cached copies and checksums mean for results, and what changes during a rolling upgrade.
---

# How the search index stays current

Artifact search does not walk storage on every request. It reads an index table that Ingot keeps up to date as files change. This page explains how that table is kept current, what each part of the process can and cannot catch, and what to expect during an upgrade. For the parameters and endpoints, see [Search filters and API](../reference/search-filters-and-api.md).

## Four mechanisms

The index is kept current by four mechanisms, from the cheapest to the most expensive:

1. **Changes.** Uploads, deletions (including deletions made by cleanup policies), cached copies, checksum files and metadata that the server writes each record a small change for the index.
2. **Workers.** Background workers on every replica apply the recorded changes.
3. **Verify.** A nightly check compares a sample of rows with storage and repairs the rows it finds wrong.
4. **Rebuild.** A full walk of storage, one repository at a time, which can resume where it stopped. It runs on the first start, after a detected gap, on request and, if you enable it, on a schedule.

Most of the time the first two are enough. The other two repair what the first two cannot see.

## Changes go through a table

A write on the request path does not update the index. It records that a path needs a second look in a table called the outbox, in its own short transaction. A worker later reads storage again for that path and makes the index agree with what it finds.

The table is what makes this safe across restarts. An in-memory queue would lose every change that was waiting when the process stopped, and a rolling update stops every pod. With a table, a change survives a restart and is applied by whichever replica reaches it first.

Two properties follow from re-reading storage:

* Applying a change twice has the same result as applying it once. A crash between the update and the removal of the change does no harm.
* Several changes for the same file collapse into one read. A deployment that also writes its checksum files produces several changes, and the worker still reads the file once.

A change can be delayed. The state of the index reports the age of the oldest change that is still waiting as its lag.

## Verify catches drift in what the index holds

A nightly verify runs at 02:30 UTC by default. It takes a sample of consecutive rows, 2,000 by default, starting at a random row and wrapping around to the first row. It compares each row with storage. It removes rows for files that no longer exist, corrects rows whose size or modification time changed, and corrects rows whose origin changed, for example a file that is now served as a cached copy. The drift is the share of sampled rows that were missing or changed. Above 5 percent the index state becomes `DEGRADED`.

A verify only sees what is in the index. It cannot find a file that was never indexed, because it starts from the rows. That is what a rebuild is for.

## A rebuild reads everything again

A rebuild walks the repositories in name order. It reads storage 500 entries at a time and saves its position after each page. A run that stops, for example because its replica was restarted, is picked up by another replica from the saved position once the lease of the run has expired. A rebuild never writes a path that still has a change waiting, because that change is newer than what the walk read.

When a repository has been walked completely, the rows of files that the walk did not see are removed. A full rebuild also removes the rows of repositories that were deleted from the configuration. If one repository cannot be listed, its old rows stay, and the run ends as failed.

{% hint style="warning" %}
The race described in the next paragraph is not covered by a test in the repository.
{% endhint %}

There is one known race. A file deleted while the walk is past its position can be listed by the walk and written after its deletion was applied. The row then stays for a file that is gone, until a later verify or rebuild finds it.

## Startup and gaps

On the first start, the index is empty and no rebuild has ever completed. A rebuild then starts in the background. Search works meanwhile and says that the results are partial.

On a later start, Ingot looks at the heartbeat that every replica writes once a minute. If the heartbeat is missing or older than 10 minutes while rows exist, a rebuild starts, because changes may have been written without being indexed, for example by a version that did not index them.

The cost of this rule is one storage walk after a stop of more than 10 minutes, even when nothing was missed. Set `gapRebuild` to `false` to start rebuilds by hand instead.

## Cached copies carry their source

A proxy repository stores the files it fetches in the same storage as hosted files, and records a cached copy in its ledger. The index copies that marking for each version directory. A cached file is therefore marked `MIRROR` with the proxy repository it was cached in, and the page shows it as cached from that repository. Hosted files are marked `HOSTED`.

Three limits apply to this marking.

* A proxy shows only what it has fetched. An artifact that was never requested through the proxy is not in the index, even when the upstream repository holds it.
* The upstream host, which Ingot records as `sourceHost`, is the host of the first fetch that created the version directory. Later files in the same directory keep that host, even when they were fetched from another one. Anonymous callers see it as well.
* Cached copies made before Ingot recorded the upstream host have no `sourceHost`. The page shows only the repository for them.

## Checksums are known only when they were indexed

The index stores the SHA-1 of a file when it reads the checksum file next to it. That is the case for a file that was indexed after its checksum file was written. A rebuild lists storage and does not read checksum files. It keeps the checksum of a row it already has while the size and the modification time are unchanged, and it gives new rows none. A write of a checksum file later adds the value.

As a result, a search by `sha1` finds only files whose checksum is known. A file uploaded without a checksum file, or indexed before its checksum existed, does not match until a checksum is written for it or it is indexed again. Treat an empty `sha1` as "not known", not as "no checksum".

## Several replicas share one index

All replicas write their changes to the same table and apply them from there. A worker claims a batch of changes for a short time. If its replica dies, another replica takes the batch once the claim expires. A search reads the database directly, with no cache in between, so every replica gives the same answer.

At most one run is live at a time, whether it is a rebuild or a verify. A start is a single conditional write, so only one replica can win it. A verify does not start while a rebuild is live. A scheduled run is claimed for its UTC day. A claimant that dies does not lose the run, because the run row remains and the next replica continues it from the saved position.

## Rolling upgrade and rollback

During a rolling upgrade, pods of the previous version can still write artifacts. Those writes do not reach the index, because that version does not maintain it. A verify does not find them either, because it starts from the rows it has. Only a rebuild indexes them. The gap rule does not help while the old pods are up, because the new pods keep the heartbeat fresh.

Once the last pod of the previous version has stopped, start a rebuild of all repositories, or enable the schedule. The [rebuild how-to](../how-to/rebuild-search-index.md) describes the steps.

Rolling back to the previous version needs no step. That version does not read the search tables, so the index stays where it is. Files stored while the older version runs are not indexed. With `gapRebuild` on, which is the default, the next start of the newer version finds the heartbeat too old and starts a rebuild. Ingot never drops the search tables.

## What a first start costs

The first start after an upgrade builds the index in the background. The time depends on the number of files and on the storage. Object storage is slower, because every listing is a request to the storage service. Search answers during the build and says that the results are partial. The Maintenance page lists each repository of the run as done, in progress or waiting.

The feature is on by default and needs no configuration change. Set `INGOT_LOCAL_SEARCHENABLED` to `false` to turn it off. The endpoints then answer `404`, and the tables stay in place.

## Related

* [Search for artifacts](../how-to/search-for-artifacts.md) describes the search page.
* [Rebuild the search index](../how-to/rebuild-search-index.md) describes the steps for a rebuild and a verify.
* [Search filters and API](../reference/search-filters-and-api.md) lists the parameters, the endpoints and the settings.
