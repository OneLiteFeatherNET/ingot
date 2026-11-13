---
description: Set up the OpenSearch search backend of an Ingot cluster step by step, from a dedicated cluster user and TLS to the two switches on every replica, the initial rebuild and the way back to the database.
---

# Use OpenSearch for search

This guide moves the artifact search of an Ingot cluster from the database to an OpenSearch cluster. It assumes the server image, which ships the plugin, and an OpenSearch 2.x cluster that you run. The settings of the plugin are in the [OpenSearch reference](../reference/opensearch-search-backend.md). The way the backends are chosen and switched is in [Choose a search backend](choose-a-search-backend.md).

{% hint style="warning" %}
The search of the cluster answers `503` while the switch is under way, because no replica of the database backend and no replica of the OpenSearch backend is serving. Plan a short window. The steps below name where it begins and ends.
{% endhint %}

## Before you begin

* An OpenSearch 2.x cluster with the security plugin on and TLS on. This guide uses `opensearch.example.org` as the host, and port `9200` as the port. Replace them with your own.
* A certificate of the cluster that the JVM trusts. A private certificate authority is trusted through `INGOT_EXTRA_CA_CERTS`, see step 1.
* Administrator access to the cluster, to create a role and a user.
* The same Ingot version on every replica, and a manager account in Ingot to read the Maintenance page.

## 1. Prepare the cluster and a dedicated user

Create a role that can use one index family and nothing else. The index family is named by the prefix of `INGOT_OPENSEARCH_INDEX`, which defaults to `ingot-artifacts`. Replace the names below if you set another prefix. The role below grants the groups of actions that the plugin uses, on the alias and the indices of that prefix. Check the names against the security plugin of your cluster version before you apply it.

```yaml
ingot_search:
  cluster_permissions:
    - "cluster_monitor"
  index_permissions:
    - index_patterns:
        - "ingot-artifacts"
        - "ingot-artifacts-*"
      allowed_actions:
        - "create_index"
        - "manage_aliases"
        - "indices_monitor"
        - "read"
        - "write"
```

The role needs no permission for scripts. The plugin sends its one script inline with each request, as a fixed text with parameters, so nothing is stored in the cluster.

Create a user, assign it the role, and generate a password. Keep the password out of the manifests. Write it to a file that the container can read, for example `/run/secrets/opensearch-password`, mounted read-only. The file must hold the password and nothing else. Ingot removes the line break at its end, and no other character is changed.

Then check the certificate. The server image trusts the public authorities. A private authority is added with a directory of PEM files that you mount, and that directory is named in `INGOT_EXTRA_CA_CERTS`. The steps are in [Install with Docker](install-with-docker.md#trust-a-private-certificate-authority). Hostname verification is always on, so the name in the URL must appear in the certificate of the cluster.

Last, allow the network path. Only the Ingot replicas should reach port `9200` of the cluster. Turn on the audit log of the cluster for the new user. The reasons are in [Security obligations](../reference/opensearch-search-backend.md#security-obligations).

## 2. Set the switches on every replica

Ingot needs two switches on every replica. The first loads the plugin. The second selects its backend. Set the same values on all replicas:

```
INGOT_OPENSEARCH_ENABLED=true
INGOT_OPENSEARCH_URLS=https://opensearch.example.org:9200
INGOT_OPENSEARCH_USERNAME=ingot
INGOT_OPENSEARCH_PASSWORD_FILE=/run/secrets/opensearch-password
INGOT_LOCAL_SEARCHENGINE=opensearch
```

The plugin loads only when `INGOT_OPENSEARCH_ENABLED` is `true`. The URLs are the one value that has no default, and the credentials are needed for a secured cluster. The full list is in the [reference](../reference/opensearch-search-backend.md#variables). A URL with user information in it is refused, so keep the user and the password out of `INGOT_OPENSEARCH_URLS`.

The two switches have to be applied in the order of the cluster rule, which is why this step is a switch and not a single restart:

1. Set `INGOT_OPENSEARCH_ENABLED=true` and the other OpenSearch variables on every replica, and restart them. Search stays on the database, because `searchEngine` is still `database`. The plugin is loaded but not in use.
2. Follow [The three-minute switch](choose-a-search-backend.md#the-three-minute-switch) to set `INGOT_LOCAL_SEARCHENGINE=opensearch`. Stop every replica that still uses the database backend, wait three minutes, then start the replicas with the new value. Search answers `503` in this window.

The first replica that becomes available with the OpenSearch backend stores the backend id for the cluster. Check the state on the Maintenance page or with `GET /api/maven/search/index`. The field `backend.id` reads `opensearch` and `backend.state` reads `AVAILABLE`.

If the state reads `OPEN_FAILED`, the field `backend.detail` names the cause, for example an unreachable cluster, a refused URL or a password file that cannot be read. Fix that cause. The backend retries the open every 30 seconds, so the state changes once the cause is gone. The other states are listed in [Backend states](choose-a-search-backend.md#backend-states).

## 3. Run the initial rebuild

The switch to a new backend starts a full rebuild of all repositories by itself, with the trigger `BACKEND`. The OpenSearch index is empty at that point, so search results are partial until the rebuild completes.

1. Open **Maintenance** and find the **Search index** panel.
2. Check that a run is live. The panel lists each repository as **Done**, **In progress** or **Waiting**.
3. If no run is live and the index state reads **Empty**, select **Rebuild index**, choose all repositories and select **Rebuild**. The rebuild only reads storage, and it never changes a file.
4. Wait until the index state reads **Ready**. Then compare a few known artifacts with a search.

If the rebuild fails, the panel shows the state **Degraded**. Read the reason on the panel and in the log, fix it, and start the rebuild again. The steps are in [Rebuild the search index](rebuild-search-index.md).

## 4. The way back to the database

A switch back follows the same steps as the switch to OpenSearch, with the values reversed:

1. Stop every replica that uses the OpenSearch backend.
2. Wait three minutes after the last of them has stopped.
3. Set `INGOT_LOCAL_SEARCHENGINE=database` on every replica, or remove the variable, since `database` is the default. Start the replicas.

The first replica of the database backend that becomes available stores its id, clears the date of the last full rebuild and starts a full rebuild with the trigger `BACKEND`. Follow it in the **Search index** panel, as in step 3, and expect partial results until it completes.

When no replica needs the plugin any longer, set `INGOT_OPENSEARCH_ENABLED=false` or remove it, and restart the replicas. With the switch off the plugin is not loaded, and the image behaves as it did before the plugin was bundled.

## Related

* [OpenSearch search backend](../reference/opensearch-search-backend.md) lists every variable, the index layout, the access rule and the states.
* [Choose a search backend](choose-a-search-backend.md) explains the cluster rule and the backend states.
* [Rebuild the search index](rebuild-search-index.md) covers the rebuild, the verify and their states.
* [Search filters and API](../reference/search-filters-and-api.md#the-backend-block) describes the `backend` block of the status.
