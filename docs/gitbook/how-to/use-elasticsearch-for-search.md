---
description: Set up the Elasticsearch search backend of an Ingot cluster step by step, from an API key with the privileges of the plugin and TLS to installing the jar, the switch on every replica, the initial rebuild and the way back to the database.
---

# Use Elasticsearch for search

This guide moves the artifact search of an Ingot cluster from the database to an Elasticsearch 8.x or 9.x cluster that you run. The plugin is not part of the server image. You install its jar yourself, as described in step 2. The settings of the plugin are in the [Elasticsearch reference](../reference/elasticsearch-search-backend.md). The way the backends are chosen and switched is in [Choose a search backend](choose-a-search-backend.md).

{% hint style="warning" %}
The search of the cluster answers `503` while the switch is under way, because no replica of the database backend and no replica of the Elasticsearch backend is serving. Plan a short window. The steps below name where it begins and ends.
{% endhint %}

## Before you begin

* An Elasticsearch 8.x or 9.x cluster with security on and TLS on. This guide uses `elasticsearch.example.org` as the host, and port `9200` as the port. Replace them with your own.
* A certificate of the cluster that the JVM trusts. A private certificate authority is trusted through `INGOT_EXTRA_CA_CERTS`, see step 1.
* Administrator access to the cluster, to create an API key.
* The same Ingot version on every replica, and a manager account in Ingot to read the Maintenance page.
* The jar of the plugin, `elasticsearch-plugin.jar`, built for the same Ingot version that you run, or taken from the plugin store.

## 1. Prepare the cluster and an API key

Create an API key that can use one index family and nothing else. The index family is named by the prefix of `INGOT_ELASTICSEARCH_INDEX`, which defaults to `ingot-artifacts`. Replace the names below if you set another prefix. The key below holds exactly the privileges that the plugin uses, and no more.

```json
POST /_security/api_key
{
  "name": "ingot-search",
  "role_descriptors": {
    "ingot_search": {
      "cluster": ["monitor"],
      "indices": [
        {
          "names": ["ingot-artifacts", "ingot-artifacts-*"],
          "privileges": ["create_index", "read", "write", "delete", "manage"]
        }
      ]
    }
  }
}
```

The response holds an `encoded` value. That value is the API key of the plugin. Write it to a file that the container can read, for example `/run/secrets/elasticsearch-api-key`, mounted read-only. The file must hold the encoded key and nothing else. Ingot removes the line break at its end, and no other character is changed. The plugin never writes a script into the cluster, so the key needs no permission for scripts.

Then check the certificate. The server image trusts the public authorities. Elasticsearch signs its HTTP certificate with the authority of the cluster, and a private authority is added with a directory of PEM files that you mount. That directory is named in `INGOT_EXTRA_CA_CERTS`. The steps are in [Install with Docker](install-with-docker.md#trust-a-private-certificate-authority). Hostname verification is always on, so the name in the URL must appear in the certificate of the cluster.

Last, allow the network path. Only the Ingot replicas should reach port `9200` of the cluster. Turn on the audit log of the cluster for the key. The reasons are in [Security obligations](../reference/elasticsearch-search-backend.md#security-obligations).

## 2. Install the plugin and set the variables on every replica

The plugin is not in the server image. Put its jar into the plugins directory of each replica, as described in [Install a plugin](install-a-plugin.md). The jar in that directory is the switch: without it the backend is not registered, and with it the backend is registered but not in use, because `searchEngine` still selects the database.

Set the same values on all replicas:

```
INGOT_ELASTICSEARCH_URLS=https://elasticsearch.example.org:9200
INGOT_ELASTICSEARCH_API_KEY_FILE=/run/secrets/elasticsearch-api-key
INGOT_LOCAL_SEARCHENGINE=elasticsearch
```

The URLs are the one value that has no default, and a credential is needed for a secured cluster. The full list is in the [reference](../reference/elasticsearch-search-backend.md#variables). A URL with user information in it is refused, so keep the key and any password out of `INGOT_ELASTICSEARCH_URLS`. A user name and a password can replace the key with `INGOT_ELASTICSEARCH_USERNAME` and `INGOT_ELASTICSEARCH_PASSWORD_FILE`, but not together with the key.

The two steps have to be applied in the order of the cluster rule, which is why this step is a jar and not a single restart:

1. Install the jar and set the Elasticsearch variables on every replica, and restart them. Search stays on the database, because `searchEngine` is still `database`. The plugin is loaded but not in use.
2. Follow [The three-minute switch](choose-a-search-backend.md#the-three-minute-switch) to set `INGOT_LOCAL_SEARCHENGINE=elasticsearch`. Stop every replica that still uses the database backend, wait three minutes, then start the replicas with the new value. Search answers `503` in this window.

The first replica that becomes available with the Elasticsearch backend stores the backend id for the cluster. Check the state on the Maintenance page or with `GET /api/maven/search/index`. The field `backend.id` reads `elasticsearch` and `backend.state` reads `AVAILABLE`.

If the state reads `OPEN_FAILED`, the field `backend.detail` names the cause, for example an unreachable cluster, a cluster of an unsupported major version, a refused URL or a key file that cannot be read. Fix that cause. The backend retries the open every 30 seconds, so the state changes once the cause is gone. The other states are listed in [Backend states](choose-a-search-backend.md#backend-states).

## 3. Run the initial rebuild

The switch to a new backend starts a full rebuild of all repositories by itself, with the trigger `BACKEND`. The Elasticsearch index is empty at that point, so search results are partial until the rebuild completes.

1. Open **Maintenance** and find the **Search index** panel.
2. Check that a run is live. The panel lists each repository as **Done**, **In progress** or **Waiting**.
3. If no run is live and the index state reads **Empty**, select **Rebuild index**, choose all repositories and select **Rebuild**. The rebuild only reads storage, and it never changes a file.
4. Wait until the index state reads **Ready**. Then compare a few known artifacts with a search.

If the rebuild fails, the panel shows the state **Degraded**. Read the reason on the panel and in the log, fix it, and start the rebuild again. The steps are in [Rebuild the search index](rebuild-search-index.md).

## 4. The way back to the database

A switch back follows the same steps as the switch to Elasticsearch, with the values reversed:

1. Stop every replica that uses the Elasticsearch backend.
2. Wait three minutes after the last of them has stopped.
3. Set `INGOT_LOCAL_SEARCHENGINE=database` on every replica, or remove the variable, since `database` is the default. Start the replicas.

The first replica of the database backend that becomes available stores its id, clears the date of the last full rebuild and starts a full rebuild with the trigger `BACKEND`. Follow it in the **Search index** panel, as in step 3, and expect partial results until it completes.

When no replica needs the plugin any longer, remove the jar from the plugins directory of each replica and restart them. Without the jar the backend is not registered, and the server behaves as it did before the plugin was installed.

## Related

* [Elasticsearch search backend](../reference/elasticsearch-search-backend.md) lists every variable, the privileges of the key, the index layout, the access rule and the states.
* [Choose a search backend](choose-a-search-backend.md) explains the cluster rule and the backend states.
* [Rebuild the search index](rebuild-search-index.md) covers the rebuild, the verify and their states.
* [Install a plugin](install-a-plugin.md) describes the plugins directory.
