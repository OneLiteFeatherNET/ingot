---
description: Set up the embedded Lucene search backend of a single Ingot instance step by step, from a persistent volume and the Recreate strategy to installing the jar, selecting the backend, the initial rebuild, and the way back to the database.
---

# Use Lucene for search

This guide moves the artifact search of one Ingot instance from the database to an index on its own disk. There is no other service to run. The plugin is not part of the server image: you install its jar yourself, as in step 2. The settings are in the [Lucene reference](../../references/settings/lucene-search-backend.md), and the way the backends are chosen is in [Choose a search backend](choose-a-search-backend.md).

{% hint style="danger" %}
Use this backend only for one replica. A second replica behind the same Service would not answer search, and it would not write the index either. If you run more than one replica, keep the database backend or use a search cluster. The reference explains the single-writer rule.
{% endhint %}

## 1. Run one replica with a persistent volume

Make the data directory a volume that survives a restart. The index lives in `search/lucene` below the data directory, which is `/app/data` in the image, so no extra mount is needed. Without a persistent volume the index is lost on every restart and rebuilt from the database.

Set the deployment to one replica with the `Recreate` strategy, so that the old instance stops before the new one starts. In Kubernetes:

```yaml
spec:
  replicas: 1
  strategy:
    type: Recreate
```

## 2. Install the jar

Copy `lucene-plugin.jar` into the plugins directory of the server, or install it from the plugin store. Without the jar nothing of this backend is loaded, so an installation that does not install it behaves as before. Start the server once; the log names the registered backends.

## 3. Choose the backend

Set `searchEngine` to `lucene`, with the environment variable `INGOT_LOCAL_SEARCHENGINE=lucene`. The key is read at start, so restart the instance after the change. The database stays the default until you set it.

## 4. Check the start

The first start creates the index and takes the writer lease. The log shows that this replica holds the Lucene writer lease. The search index state is `BUILDING` while the initial rebuild runs from the database, and `READY` when it completes. The Maintenance page shows the rebuild.

If the log shows `another replica holds the Lucene writer lease`, a second instance is running against the same lease store. Stop it. The reference explains the other states.

## 5. Optional settings

Set `INGOT_LUCENE_DIRECTORY` only if the index must live on another volume. Set `INGOT_LUCENE_MIN_FREE_BYTES` only if the volume needs a different floor than the default of 1 GiB.

## Going back to the database

Set `searchEngine` back to `database` and restart. The database answers search again, and the index directory can stay or be deleted. Deleting it is safe, because the index is a copy of the database.
