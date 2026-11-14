---
description: Set up the Meilisearch search backend of an Ingot cluster step by step, from a dedicated key and TLS to installing the plugin jar, the switch on every replica, the initial rebuild and the way back to the database.
---

# Use Meilisearch for search

This guide moves the artifact search of an Ingot cluster from the database to a Meilisearch server. The image does not contain the plugin. You install `meilisearch-plugin.jar` yourself, and the server then offers the id `meilisearch`. The settings and the named exceptions of the backend are in the [Meilisearch reference](../reference/meilisearch-search-backend.md). The way the backends are chosen and switched is in [Choose a search backend](choose-a-search-backend.md).

{% hint style="warning" %}
The search of the cluster answers `503` while the switch is under way, because no replica of the database backend and no replica of the Meilisearch backend is serving. Plan a short window. The steps below name where it begins and ends.
{% endhint %}

## Before you begin

* A Meilisearch server of minor version 1.15, with TLS in front of it. This guide uses `search.example.org` as the host. Replace it with your own. Ingot opens only that minor version.
* A certificate of the server that the JVM trusts. A private certificate authority is trusted through `INGOT_EXTRA_CA_CERTS`, see [Install with Docker](install-with-docker.md#trust-a-private-certificate-authority).
* The master key of the server, used once to create the key of Ingot. Keep it out of Ingot.
* The jar `meilisearch-plugin.jar` of the release you run.
* The same Ingot version on every replica, and a manager account in Ingot to read the Maintenance page.

## 1. Create a dedicated key

Ingot needs a key that can use one index family and nothing else. The index family is named by `INGOT_MEILISEARCH_INDEX`, which defaults to `ingot-artifacts`. Create the key with the master key, from a host that you trust, and name only the actions below:

```
indexes.create, indexes.get, settings.get, settings.update, documents.add, documents.get, documents.delete, search, stats.get, tasks.get, version
```

Restrict the key to the index uid, for example `ingot-artifacts`, and leave out its expiry or set one you will rotate. The plugin does not check the key, because it cannot detect a master key without a privileged call. A key with more actions works, but it gives the plugin more than it needs.

Write the key to a file that the container can read, for example `/run/secrets/meilisearch-key`, mounted read-only. The file holds the key and nothing else. Ingot removes the line break at its end, and no other character is changed.

Last, allow the network path. Only the Ingot replicas should reach the server. The reasons are in [Operator obligations](../reference/meilisearch-search-backend.md#operator-obligations).

## 2. Install the plugin and set the switch on every replica

Copy `meilisearch-plugin.jar` into the plugins directory. In the container image that is `/app/data/plugins`, and the directory is created if it does not exist. The jar is the opt-in: a plugin in that directory is always loaded, and no `INGOT_` switch exists for it. Set the variables on every replica:

```
INGOT_MEILISEARCH_URL=https://search.example.org
INGOT_MEILISEARCH_API_KEY_FILE=/run/secrets/meilisearch-key
INGOT_LOCAL_SEARCHENGINE=meilisearch
```

Restart the replicas once the jar is in place. Search stays on the database until `searchEngine` is `meilisearch`, so the plugin is loaded but not in use. The full list of variables is in the [reference](../reference/meilisearch-search-backend.md#variables). A URL with user information in it is refused, so keep the user and the password out of `INGOT_MEILISEARCH_URL`. Plain HTTP is refused unless you set `INGOT_MEILISEARCH_ALLOW_HTTP=true`, and that setting is logged at every start.

Changing `INGOT_LOCAL_SEARCHENGINE` is the switch itself. Follow [The three-minute switch](choose-a-search-backend.md#the-three-minute-switch): stop every replica that still uses the database backend, wait three minutes, then start the replicas with the new value. Search answers `503` in this window.

The first replica that becomes available with the Meilisearch backend stores the backend id for the cluster. Check the state on the Maintenance page or with `GET /api/maven/search/index`. The field `backend.id` reads `meilisearch` and `backend.state` reads `AVAILABLE`.

If the state reads `OPEN_FAILED`, the field `backend.detail` names the cause, for example an unreachable server, a server of another minor version, a refused URL or a key file that cannot be read. Fix that cause and the backend retries the open. The other states are listed in [Backend states](choose-a-search-backend.md#backend-states).

When the server reports a settings change, for example after an upgrade of the plugin, the index is reindexed. Search answers `503` with the reason "the index is being reindexed for a settings change" until the server finishes. The health of the status is `DOWN` with "settings task running" meanwhile.

## 3. Run the initial rebuild

The switch to a new backend starts a full rebuild of all repositories by itself, with the trigger `BACKEND`. The Meilisearch index is empty at that point, so search results are partial until the rebuild completes.

1. Open **Maintenance** and find the **Search index** panel.
2. Check that a run is live. The panel lists each repository as **Done**, **In progress** or **Waiting**.
3. If no run is live and the index state reads **Empty**, select **Rebuild index**, choose all repositories and select **Rebuild**. The rebuild only reads storage, and it never changes a file.
4. Wait until the index state reads **Ready**. Then compare a few known artifacts with a search.

If the rebuild fails, the panel shows the state **Degraded**. Read the reason on the panel and in the log, fix it, and start the rebuild again. The steps are in [Rebuild the search index](rebuild-search-index.md).

## 4. The way back to the database

A switch back follows the same steps as the switch to Meilisearch, with the values reversed:

1. Stop every replica that uses the Meilisearch backend.
2. Wait three minutes after the last of them has stopped.
3. Set `INGOT_LOCAL_SEARCHENGINE=database` on every replica, or remove the variable, since `database` is the default. Start the replicas.

The first replica of the database backend that becomes available stores its id, clears the date of the last full rebuild and starts a full rebuild with the trigger `BACKEND`. Follow it in the **Search index** panel, as in step 3, and expect partial results until it completes.

When no replica needs the plugin any longer, remove `meilisearch-plugin.jar` from the plugins directory and restart the replicas. Without the jar nothing of the backend runs.

## Related

* [Meilisearch search backend](../reference/meilisearch-search-backend.md) lists the named exceptions, every variable and the operator obligations.
* [Choose a search backend](choose-a-search-backend.md) explains the cluster rule and the backend states.
* [Use OpenSearch for search](use-opensearch-for-search.md) is the same procedure for the OpenSearch backend.
* [Rebuild the search index](rebuild-search-index.md) covers the rebuild, the verify and their states.
