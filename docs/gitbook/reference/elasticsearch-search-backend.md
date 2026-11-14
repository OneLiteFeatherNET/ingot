---
description: Reference for the Elasticsearch search backend of Ingot, the variables that configure it with their defaults and ranges, the API key privileges, the index layout and its mapping version, the access rule, the result window limit, the security obligations of the cluster, TLS, the licence, and the errors and states the backend reports.
---

# Elasticsearch search backend

The Elasticsearch backend is a plugin that answers artifact search from an Elasticsearch 8.x or 9.x cluster instead of the database. It is not part of the server image. You install `elasticsearch-plugin.jar` into the plugins directory yourself, or from the plugin store, and the jar in that directory is the switch: without it the backend is not registered. Choosing it for the cluster is a separate step, see [Choose a search backend](../how-to/choose-a-search-backend.md). For the steps of a first setup, see [Use Elasticsearch for search](../how-to/use-elasticsearch-for-search.md).

{% hint style="warning" %}
The Elasticsearch index holds the path of every file of every repository, including repositories that are `HIDDEN` or `PRIVATE`. Ingot filters the results at query time, but the cluster itself is a sensitive store. Read [Security obligations](#security-obligations) before you connect a cluster.
{% endhint %}

## Variables

The plugin reads its settings once, when the backend is created at start. Every variable has the `INGOT_ELASTICSEARCH_` prefix. Each one also has a `REPOSILITE_ELASTICSEARCH_` spelling, which is read when the `INGOT_` name is not set to a non-blank value. When both are set, the `INGOT_` name wins. A variable that is set to an empty value or to spaces counts as unset.

There is no variable that enables the plugin. Its jar is its switch, and the backend is chosen with `searchEngine`, see [Choose a search backend](../how-to/choose-a-search-backend.md).

| Variable | Fallback | Default | Range or accepted values | Meaning |
| --- | --- | --- | --- | --- |
| `INGOT_ELASTICSEARCH_URLS` | `REPOSILITE_ELASTICSEARCH_URLS` | none | Comma-separated base URLs. Each must use `https`, unless `INGOT_ELASTICSEARCH_ALLOW_HTTP` is `true`. At least one URL. | The cluster nodes. Required when the backend is selected. Spaces around each URL are ignored. |
| `INGOT_ELASTICSEARCH_ALLOW_HTTP` | `REPOSILITE_ELASTICSEARCH_ALLOW_HTTP` | `false` | `true` or `false`, in any letter case. Any other value stops the backend with a message that names the variable. | Accepts `http://` URLs in `INGOT_ELASTICSEARCH_URLS`. Plain HTTP sends the credentials and the index unencrypted, and a cluster without security needs it. Every start of the backend logs a warning while it is `true`. The warning names the switch and never the URL, the key, the user name or the password. |
| `INGOT_ELASTICSEARCH_API_KEY` | `REPOSILITE_ELASTICSEARCH_API_KEY` | none | The encoded API key, as Elasticsearch returns it (`base64(id:key)`). | The API key, sent as `Authorization: ApiKey`. Preferred over basic authentication. Cannot be combined with a user name. When set, `INGOT_ELASTICSEARCH_API_KEY_FILE` is not read. |
| `INGOT_ELASTICSEARCH_API_KEY_FILE` | `REPOSILITE_ELASTICSEARCH_API_KEY_FILE` | none | A path to a file that holds the encoded key. A relative path is resolved against the working directory. | Reads the key from the file. The file is read once at start, and line breaks at its end are removed. An empty file, or a file that cannot be read, stops the backend with a message that names the variable and not the file content. |
| `INGOT_ELASTICSEARCH_INDEX` | `REPOSILITE_ELASTICSEARCH_INDEX` | `ingot-artifacts` | 1 to 64 characters. The first character is a lower-case letter or a digit. The others are lower-case letters, digits, `-` or `_`. | The name prefix. It names the alias and the physical index, see [Index layout](#index-layout-and-the-alias). |
| `INGOT_ELASTICSEARCH_USERNAME` | `REPOSILITE_ELASTICSEARCH_USERNAME` | none | Any non-blank text. | The user of HTTP basic authentication. Set together with a password, and refused together with an API key. Without a user name no credentials are sent, so use that only for a cluster that runs without security. |
| `INGOT_ELASTICSEARCH_PASSWORD` | `REPOSILITE_ELASTICSEARCH_PASSWORD` | none | Any non-blank text. | The password of the user. Requires a username. When set, `INGOT_ELASTICSEARCH_PASSWORD_FILE` is not read. |
| `INGOT_ELASTICSEARCH_PASSWORD_FILE` | `REPOSILITE_ELASTICSEARCH_PASSWORD_FILE` | none | A path to a file that holds the password. A relative path is resolved against the working directory. | Reads the password from the file. The file is read once at start, and line breaks at its end are removed. An empty file, or a file that cannot be read, stops the backend with a message that names the variable and not the path content. Requires a username. |
| `INGOT_ELASTICSEARCH_BULK_SIZE` | `REPOSILITE_ELASTICSEARCH_BULK_SIZE` | `500` | Whole number from `1` to `5000`. | Actions per bulk request. A bulk request also stays below 5 MB. |
| `INGOT_ELASTICSEARCH_TIMEOUT_SECONDS` | `REPOSILITE_ELASTICSEARCH_TIMEOUT_SECONDS` | `30` | Whole number of seconds from `1` to `300`. | Timeout of one request to the cluster. |
| `INGOT_ELASTICSEARCH_REPLICAS` | `REPOSILITE_ELASTICSEARCH_REPLICAS` | `1` | Whole number from `0` to `10`. | Replica shards of the index. It is set when the index is created. A change later does not reach an existing index. |

A value that is not a whole number, or that is outside its range, stops the backend with a message that names the variable and not its value. The backend then reports `OPEN_FAILED` and the server keeps running. The rules are the same for every replica, so set the same values on all of them.

The `INGOT_ELASTICSEARCH_API_KEY_FILE` and `INGOT_ELASTICSEARCH_PASSWORD_FILE` variables keep the secret out of a manifest: the variable names the file, and the file holds the secret. The file is read when the backend opens, so a rotated key takes effect when each replica opens the backend again, which happens at its next start.

### Refused combinations

| Setting | Result |
| --- | --- |
| `INGOT_ELASTICSEARCH_URLS` is unset or blank, with the backend selected | The backend stops with a message that names `INGOT_ELASTICSEARCH_URLS`. |
| A URL contains user information, such as `https://user:password@host` | The backend stops. The message names the variable and never the URL or its user information. Put the credentials into the key variable, or into `INGOT_ELASTICSEARCH_USERNAME` and `INGOT_ELASTICSEARCH_PASSWORD`. |
| An `http://` URL while `INGOT_ELASTICSEARCH_ALLOW_HTTP` is `false` | The backend stops, and the message names the switch. |
| A URL with any other scheme, such as `ftp` | The backend stops, and the message names the variable. |
| An API key together with a user name | The backend stops and names both variables. Choose one of them. |
| A username without a password, or a password without a username | The backend stops, and the message names the variable that is missing. |
| Neither an API key nor a user name, and `INGOT_ELASTICSEARCH_ALLOW_HTTP` is `false` | The backend stops, and the message names the variables that are needed. |

Without any credential the backend starts only when `INGOT_ELASTICSEARCH_ALLOW_HTTP` is `true`, for a cluster that runs without security. Every start then logs a warning that names the variables and no value.

## Versions

The plugin supports Elasticsearch 8.x and 9.x. At open it reads the version of the cluster, and a major version other than 8 or 9 is refused: the backend reports `OPEN_FAILED` and names the version the cluster reported and the supported majors. Nothing is written to a refused cluster.

The container tests run against a secured Elasticsearch 8 and a secured Elasticsearch 9 cluster, each at a pinned minor version.

## Index layout and the alias

The plugin writes one physical index, `<prefix>-v1`, where `<prefix>` is the value of `INGOT_ELASTICSEARCH_INDEX`. The alias `<prefix>` points at it. Every read and write of the plugin uses the alias, so a later physical index can take the place of this one with one atomic change of the alias.

On the first start the plugin creates the index and the alias. On later starts it finds the alias and uses the index it points at. The alias must point at exactly one index.

The index has one primary shard, `INGOT_ELASTICSEARCH_REPLICAS` replicas and a refresh interval of one second. The shard count is not configurable in this version. The mapping is strict: a document with a field that the mapping does not know is refused.

The document id is the path hash of the database, the SHA-256 of the repository, a NUL byte and the path. Indexing the same file twice replaces one document, so a retried write does no harm.

### Index identity

The plugin reads the uuid of the physical index when it opens. The core keeps the rebuild cursor against that uuid. An operator who deletes and recreates the index gets a new uuid, so the next rebuild starts again from the first page and does not skip the rows of the new index. Nothing has to be done for this.

### Mapping version

The index records its mapping version in its metadata, under `ingot_mapping_version`. This release writes version `1`. When the plugin opens an index whose recorded version is different, it reports `OPEN_FAILED` and names both versions. It does not write to that index. Ingot does not migrate an index by itself.

A change of the mapping is a new mapping version. The release that makes the change carries its procedure. The steps are:

1. Create `<prefix>-v2` with the new mapping and the same shard and replica settings.
2. Run a full rebuild into the new index while the outbox writes to both indexes, so no change is lost during the rebuild.
3. Verify a sample of rows against storage with the verify action of the Maintenance page, and check the result.
4. Switch the alias in one `_aliases` request that removes `<prefix>-v1` and adds `<prefix>-v2`.
5. Keep `<prefix>-v1` until the switch has been stable for a while, then delete it.

Version 1 contains neither the second index nor the write to both indexes, so the procedure is not run by hand. Until a release carries it, an index of another version is refused as described above.

## Access rule

Every query carries the caller's access value as a filter of the query itself. The cluster applies the filter before it counts, sorts and pages the hits. The plugin never removes a row after the cluster has returned it, so a result that is outside the access value cannot reach the caller, and facets and totals do not count such rows.

The access value is translated from the same rules as the database engine:

* A repository the caller may not browse adds no hit and no facet.
* A precedence list of rules is applied in order: a path is visible when the first rule that matches it allows it.

The contract suite compares the results with the database engine for the same data.

## Limits

* **Result window.** Elasticsearch refuses a search whose last hit lies beyond its result window of 10 000 entries. A file page beyond that window is refused as an invalid query, with a message that asks to narrow the filters. The database engine has no such limit, so this is a named difference from it.
* **Bulk requests.** At most `INGOT_ELASTICSEARCH_BULK_SIZE` actions and 5 MB per request. A prefix removal and a reorigin are sent on their own, so the order of the writes is kept.
* **Buckets.** The total of a search is counted with buckets up to the total cap of the search plus one, which stays below the default bucket limit of the cluster, 65 536.

## Security obligations

Ingot's responsibility ends at its own filters. The cluster is the responsibility of its operator, and the following obligations apply.

* **A dedicated API key.** Create one API key for Ingot, with a name that says what it is for. The key holds only these privileges, and nothing else:
  * the cluster privilege `monitor`, for the health check;
  * on the index pattern `<prefix>-*` and on the alias `<prefix>`, the index privileges `create_index`, `read`, `write`, `delete` and `manage`. The `manage` privilege is needed for the alias switch and for reading the index settings at open.

  The key needs no `manage_security`, no `all` and no access to other indices. No dashboards or users of the cluster use it. Check the names against the security settings of your cluster version. The plugin never writes a script into the cluster, and it needs no permission for scripts.
* **Network policy.** Only the Ingot replicas can reach the cluster. Allow the port of the cluster for the replicas and for nothing else.
* **TLS.** Use `https` URLs. Keep the verification of the certificate on. The plugin has no option to skip it, see [TLS](#tls).
* **Audit logging.** Enable the audit log of the cluster for the key or the user, so that a write or a search by Ingot can be traced.
* **Rotation.** Rotate the key or the password, and write the new value to the file that `INGOT_ELASTICSEARCH_API_KEY_FILE` or `INGOT_ELASTICSEARCH_PASSWORD_FILE` names. A restart of each replica applies it. The cluster does not need a restart.

Ingot's own administration never shows the URL, the key, the user name or the password of the cluster, and none of them appears in a log, a status response or a support bundle. A cluster may echo the credentials it was sent in an error, and every text that leaves the plugin is cleaned of them first.

## TLS

The plugin uses the default `SSLContext` of the JVM, with hostname verification on. A certificate is accepted when the JVM trusts its authority, and a name that does not match the certificate is refused.

Elasticsearch signs its HTTP certificate with the authority of the cluster, which is written when the cluster is installed. To trust that authority, put its certificate in a directory of PEM files and name the directory in `INGOT_EXTRA_CA_CERTS`. The server image imports the certificates into a copy of the JVM trust store at start. The steps are in [Install with Docker](../how-to/install-with-docker.md#trust-a-private-certificate-authority). A JAR installation trusts what the JVM trusts, so add the authority to that trust store.

## Licence

The plugin's code is Apache 2.0 and contains no Elastic code. It does not depend on an Elastic client library: it speaks the REST API of the cluster with the JVM HTTP client. Elasticsearch itself is not distributed by Ingot. The server is under the Elastic License 2.0 or the Server Side Public License, and since version 8.16 also under the GNU Affero General Public License 3.0 as an option. The operator chooses which terms apply to the cluster.

## Errors and states

The backend reports one of the states of [Backend states](../how-to/choose-a-search-backend.md#backend-states). The states that the Elasticsearch backend reaches are these.

| State | When | Detail and behavior |
| --- | --- | --- |
| `OPEN_FAILED` | A setting is refused, the key or the password file cannot be read, the cluster is red or cannot be reached while the backend opens, the cluster is of an unsupported major version, the alias points at more than one index, or the index records another mapping version. | `backend.detail` names the cause. The plugin does not write while the state is `OPEN_FAILED`, and it retries the open every 30 seconds. |
| `DOWN` | The cluster is red or unreachable, or the circuit is open after five transport failures in a row. | `backend.healthDetail` names the cause. While the circuit is open, which lasts 30 seconds, no request is sent to the cluster. Then one request is let through to test the cluster, and a success closes the circuit. Outbox entries are kept and retried. |
| Busy | The cluster answers `429` or `503` to a bulk request, or it rejects an item of a bulk request as too many requests. | This is back pressure and not an error. The batch is released, the outbox waits for the `Retry-After` delay of the cluster, or 5 seconds when the header is missing, and the attempt is not counted. Entries wait in the outbox, so the index lags behind storage until the cluster accepts the batch. |
| Rejected document | The cluster refuses a document, for example because its field does not match the mapping. | The batch fails with the error text, which is redacted. Each failed attempt is counted, and an entry becomes a dead letter after ten failures, as for the database backend. |

In every state except `AVAILABLE`, search answers `503` with `Retry-After: 30`, as described in [Backend states](../how-to/choose-a-search-backend.md#backend-states). Writes are visible to search after the refresh interval of one second, and the `backend` block of the status reports the interval.

Results of a write can lag behind a deploy by about a second. A file that was deleted can still appear for that long. The verify action of the Maintenance page repairs real drift, see [Rebuild the search index](../how-to/rebuild-search-index.md).

## Related

* [Use Elasticsearch for search](../how-to/use-elasticsearch-for-search.md) is the setup, step by step.
* [Choose a search backend](../how-to/choose-a-search-backend.md) explains the states, the cluster rule and the switch between backends.
* [Environment variables](environment-variables.md#elasticsearch-search-backend) lists the variables with the other settings of the server.
* [OpenSearch search backend](opensearch-search-backend.md) describes the other external backend, which uses the same rules where the cluster is the same kind of store.
