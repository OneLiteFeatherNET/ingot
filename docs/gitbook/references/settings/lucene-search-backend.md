---
description: Reference for the embedded Lucene search backend of Ingot, the variables that configure it with their defaults, the single-writer rule and what more than one replica does, the rebuild after a takeover, the index location and commits, the access rule, and the permissions the index directory needs.
---

# Lucene search backend

Tier: [Experimental](../plugins/plugin-tiers.md#experimental)

The Lucene backend is a plugin that answers artifact search from an index on the local disk of one Ingot instance, with no other service. It is not part of the server image. You install `lucene-plugin.jar` into the plugins directory yourself, or from the plugin store, and the jar in that directory is the switch: without it the backend is not registered. Choosing it is a separate step, see [Choose a search backend](../../configure/search/choose-a-search-backend.md). For the steps of a first setup, see [Use Lucene for search](../../configure/search/use-lucene-for-search.md).

{% hint style="danger" %}
The backend serves exactly one replica. Only the replica that holds the writer lease writes the index and answers search. Every other replica reports the backend as down, answers search with `503`, and writes nothing. Downloads, uploads and deploys keep working on every replica. If you run more than one replica behind one Service, do not choose this backend; use the database or a search cluster. Read [Single writer](#single-writer) before you choose it.
{% endhint %}

## Variables

The plugin reads its settings when the backend is created at start. Every variable has the `INGOT_LUCENE_` prefix and a `REPOSILITE_LUCENE_` spelling, which is read when the `INGOT_` name is not set to a non-blank value. The `INGOT_` name wins when both are set.

| Variable | Fallback | Default | Range or accepted values | Meaning |
| --- | --- | --- | --- | --- |
| `INGOT_LUCENE_DIRECTORY` | `REPOSILITE_LUCENE_DIRECTORY` | `search/lucene` below the data directory | An absolute path to a directory. A file at that path stops the backend with a message that names the variable. | Where the index lives. The directory holds the Lucene index and nothing else. In the image the default is `/app/data/search/lucene`. |
| `INGOT_LUCENE_MIN_FREE_BYTES` | `REPOSILITE_LUCENE_MIN_FREE_BYTES` | `1073741824` (1 GiB) | Whole number of bytes, zero or more. | The free space the index volume keeps for the rest of the server. Below it the replica stops adding to the index and reports `low disk space`. |

A value that is not a whole number, or is negative, stops the backend with a message that names the variable and not its value. The backend then reports `OPEN_FAILED` and the server keeps running.

There is no variable that enables the plugin. Its jar is its switch, and the backend is chosen with `searchEngine`.

## Single writer

Exactly one replica may write the index. The replica takes the lease `search.lucene.writer` from the instance database when the backend opens, for 60 seconds, and renews it on each health check, every 10 seconds. Before each batch of writes it renews the lease at its epoch. The lease store increments the epoch on every grant, so a replica that lost the lease cannot renew it.

What the operator sees with more than one replica:

- The replica that holds the lease searches and writes. The others log `another replica holds the Lucene writer lease` when they open and at each attempt, every 10 seconds, and report the backend as down with the same reason.
- Search on a replica without the lease answers `503`. Its index directory is not created and nothing is written there.
- A replica whose lease was taken over, for example after a pause or a partition, closes its index and writes nothing more. It logs `the Lucene writer lease was taken over`. Its batch is not counted as a failed attempt, so the outbox does not turn it into a dead letter.
- Downloads, uploads and deploys work on every replica, because they only add outbox rows.

The lease keeps a second writer out, but it does not make search work on two replicas. Run one replica, with the `Recreate` strategy, so that the old instance stops before the new one starts.

## Rebuild after a takeover

Each commit records the epoch of its holder. When a replica takes the lease at an epoch other than the one its index was written under, it clears the index and takes a new identity. The core then starts a full rebuild from the database, because the replica cannot know which writes the previous holder acknowledged. Until the rebuild completes the index is incomplete, and the Maintenance page shows the rebuild.

A restart of the same holder does not rebuild the index. The replica keeps its owner in its data directory, and a restart within the lease time re-acquires its own live lease with the same epoch, so its index is kept. The lease is not released at shutdown for that reason. A restart after the lease has expired is a new grant with a new epoch, and it rebuilds the index. Another replica takes the lease once it has expired, which takes at most one minute. The index is a copy of the database, so a rebuild costs time, not data.

A takeover cannot overlap the old holder's writes. The holder commits a batch only while its lease is valid for the whole commit, with a margin of five seconds for the clock skew between replicas. A batch that would finish later is not written.

## Index location and commits

The index is one Lucene directory. The plugin writes no file outside it. Each batch of writes is one commit, made before the call that applies the batch returns, and the outbox entries are removed only after that. A stop between the commit and the removal replays the batch, and the replay leaves the same documents, because each file has one document keyed by its path hash.

A commit that fails for lack of disk space is retried after 60 seconds and is not counted as an attempt. Any other failure reopens the writer from the last commit and is passed on.

A corrupt index, or one written with another mapping version, is `OPEN_FAILED` with the path and the exception class or the two versions. The plugin deletes nothing. Move or delete the directory yourself, and the next start rebuilds the index.

## Access rule

Every search, facet and file read carries the caller's access value as a clause of the Lucene query. A row outside that value is never returned, and it is not counted in the facets. The rules are the same as the database engine's, including a precedence list in which an earlier denial wins. The core still checks each returned row against the access value, and it counts any row that fails.

## Security

The index holds the path of every file of every repository, including `HIDDEN` and `PRIVATE` repositories, as the database does. Its protection is the file permissions of the directory. Make it readable and writable only by the user that runs Ingot, the `reposilite` service user in the image, for example mode `0700`. No credential is involved, so the plugin reads no secret and logs none.

## Limits

- Groups are collected in memory for each request. A search that matches a very large number of groups uses memory in proportion to that number.
- Facet counts read the stored fields of the matching files. A facet over millions of files is slower than on the database.
- Deleting the index directory is safe. The next start creates an empty index and rebuilds it from the database.
