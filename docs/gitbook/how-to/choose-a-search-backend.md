---
description: Choose the search backend of an Ingot cluster, read each backend state on the Maintenance page and in the API, follow the cluster rule and switch backends with the three-minute switch.
---

# Choose a search backend

Artifact search in Ingot is answered by one search backend for the whole cluster. The backend holds the index that search reads. The built-in backend is `database`, which keeps the index in the database Ingot already uses. A plugin can register more backends, for example a dedicated search service. To write one, see [Write a search backend plugin](write-a-search-backend-plugin.md).

{% hint style="info" %}
A default installation needs none of this page. `searchEngine` defaults to `database`, and the cluster rule and the switch only matter when you run more than one backend.
{% endhint %}

## Set the backend

Set the local key `searchEngine` on every replica, to the same id. The id is `database`, or the id of a backend that a plugin registers. The environment variable is `INGOT_LOCAL_SEARCHENGINE`. The older `REPOSILITE_LOCAL_SEARCHENGINE` is read as a fallback. The key is read at start, so a change takes effect after a restart. The full list of keys is in [Configuration file and startup parameters](../reference/configuration-and-startup.md#shared-settings-and-search).

The key is local to each replica. The cluster rule below keeps the replicas in agreement.

## Backend states

Each replica reports one state for its backend. The state is shown in the **Search index** panel of the Maintenance page, and in the `backend.state` field of `GET /api/maven/search/index`. The API fields are described in [Search filters and API](../reference/search-filters-and-api.md#index-administration).

| State | What it means | What to do |
| --- | --- | --- |
| `AVAILABLE` | The backend is open and reports healthy. Search is answered and the outbox is drained. | Nothing. |
| `NOT_INSTALLED` | No backend is registered under the id of `searchEngine`. The plugin is missing, or its plugin is switched off. An unknown value in `searchEngine` lands here too. | Check the spelling of the id. Check that the plugin jar is in the plugins directory, or in the bundled directory with its switch on. The log at start lists the ids that were registered. |
| `REFUSED` | The factory was refused. It was built for another search backend API version, or another factory already has the id. The warning in the log names both classes. | Update the plugin to the API version of this server, or remove the duplicate plugin. A plugin cannot replace `database`. |
| `OPEN_FAILED` | The backend could not be opened. The reason is in `backend.detail`. | Fix the cause the detail names, usually a connection or a secret. Ingot retries the open every 30 seconds, so the state changes once the cause is gone. |
| `DOWN` | The backend opened, but its health check reports it down. The reason is in `backend.healthDetail`. | Check the backend service. Ingot checks the health every 10 seconds. Outbox changes are kept and retried with a growing delay. |
| `CLUSTER_MISMATCH` | The database stores another backend id for the cluster, and this replica is configured for a different one. | See [The cluster rule](#the-cluster-rule) and [The three-minute switch](#the-three-minute-switch). |

In every state except `AVAILABLE`, search answers `503 Service Unavailable` with the header `Retry-After: 30`. The body is a problem response that names the backend and the reason. The reason never contains a secret. Downloads and deploys keep working in every state, and the server starts even when the backend is missing or failing. Search does not fall back to the database index when another backend is down.

The rebuild and verify endpoints of the search index answer the same `503` while the backend is not available.

{% hint style="warning" %}
A replica that is in `CLUSTER_MISMATCH` drains nothing, so its outbox entries wait until the cluster agrees on one backend. Find out which id is stored before you change anything else.
{% endhint %}

## The cluster rule

Every replica shares one database, and the outbox that feeds the index lives there. If two replicas wrote to different indexes, the index would split and nothing would report it. The cluster rule prevents that:

* The database stores the id of the backend that owns the index. The first replica that becomes `AVAILABLE` and finds no stored id writes its own id.
* Each replica writes a heartbeat about once a minute, with its backend id and the time.
* A replica drains the outbox only while the stored id equals its own id. A replica with any other id is in `CLUSTER_MISMATCH` and drains nothing.
* The stored id changes only when no replica of the stored backend has written a heartbeat for three minutes.

As a result, starting a replica with a different `searchEngine` next to replicas that still run the old backend does not switch anything. The old replicas keep the index. The new replica waits in `CLUSTER_MISMATCH` until the switch is allowed. A replica that is in `CLUSTER_MISMATCH` answers search with `503`, and the replicas that still hold the stored id keep answering.

## The three-minute switch

To move the cluster to another backend, change the backend on all replicas in this order:

1. Install the plugin of the new backend on the replicas that will run it, and check that it is loaded. The state of such a replica becomes `AVAILABLE` only after the switch.
2. Stop every replica that uses the old backend. A replica that keeps writing heartbeats for the old id blocks the switch.
3. Wait three minutes after the last old replica has stopped. The rule needs no heartbeat of the old id younger than three minutes.
4. Set `searchEngine` to the new id on the replicas and start them. The first replica of the new backend that becomes `AVAILABLE` stores its id, clears the date of the last full rebuild, and starts a full rebuild of all repositories with the trigger `BACKEND`.
5. Follow the rebuild in the **Search index** panel. The index state reads **Building** until it completes, and search results can be partial until then. See [Rebuild the search index](rebuild-search-index.md#after-a-backend-switch).

Search is unavailable for the cluster between step 2 and step 4, because no replica of the old backend is running and the new one has not been opened yet. Plan the window for that.

A switch back to the old backend follows the same steps. The rebuild starts again on the way back.

## Related

* [Write a search backend plugin](write-a-search-backend-plugin.md) describes the interfaces a plugin implements.
* [Rebuild the search index](rebuild-search-index.md) covers the rebuild, its states and its triggers.
* [Search filters and API](../reference/search-filters-and-api.md) lists the status fields and the `503` answers.
* [High availability support](../explanation/high-availability-support.md) explains how replicas share one database.
