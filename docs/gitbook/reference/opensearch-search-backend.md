---
description: Reference for the OpenSearch search backend of Ingot, the variables that configure it with their defaults and ranges, the index layout and its mapping version, the access rule, the security obligations of the cluster, TLS, and the errors and states the backend reports.
---

# OpenSearch search backend

The OpenSearch backend is an opt-in plugin that answers artifact search from an OpenSearch 2.x cluster instead of the database. It ships in the server image as `opensearch-plugin.jar` in `/app/bundled-plugins/`, and it does nothing until its switch is on. Choosing it for the cluster is a separate step, see [Choose a search backend](../how-to/choose-a-search-backend.md). For the steps of a first setup, see [Use OpenSearch for search](../how-to/use-opensearch-for-search.md).

{% hint style="warning" %}
The OpenSearch index holds the path of every file of every repository, including repositories that are `HIDDEN` or `PRIVATE`. Ingot filters the results at query time, but the cluster itself is a sensitive store. Read [Security obligations](#security-obligations) before you connect a cluster.
{% endhint %}

## Variables

The plugin reads its settings once, when the backend is created at start. Every variable has the `INGOT_OPENSEARCH_` prefix. Each one also has a `REPOSILITE_OPENSEARCH_` spelling, which is read when the `INGOT_` name is not set to a non-blank value. When both are set, the `INGOT_` name wins. A variable that is set to an empty value or to spaces counts as unset.

The switch is the exception. `INGOT_OPENSEARCH_ENABLED` is read by the loader, and the `REPOSILITE_OPENSEARCH_ENABLED` spelling is read only when the `INGOT_` name is absent. An empty `INGOT_OPENSEARCH_ENABLED` is set, and it keeps the plugin off.

| Variable | Fallback | Default | Range or accepted values | Meaning |
| --- | --- | --- | --- | --- |
| `INGOT_OPENSEARCH_ENABLED` | `REPOSILITE_OPENSEARCH_ENABLED` | `false` | `true` in any letter case turns the plugin on. Every other value leaves it off. | The bundled switch. Off, the plugin loads nothing and registers no backend. |
| `INGOT_OPENSEARCH_URLS` | `REPOSILITE_OPENSEARCH_URLS` | none | Comma-separated base URLs. Each must use `https`, unless `INGOT_OPENSEARCH_ALLOW_HTTP` is `true`. At least one URL. | The cluster nodes. Required when the plugin is on. Spaces around each URL are ignored. |
| `INGOT_OPENSEARCH_ALLOW_HTTP` | `REPOSILITE_OPENSEARCH_ALLOW_HTTP` | `false` | `true` or `false`, in any letter case. Any other value stops the backend with a message that names the variable. | Accepts `http://` URLs in `INGOT_OPENSEARCH_URLS`. Plain HTTP sends the credentials and the index unencrypted. Every start of the backend logs a warning while it is `true`. The warning names the switch and never the URL, the user name or the password. |
| `INGOT_OPENSEARCH_INDEX` | `REPOSILITE_OPENSEARCH_INDEX` | `ingot-artifacts` | 1 to 64 characters. The first character is a lower-case letter or a digit. The others are lower-case letters, digits, `-` or `_`. | The name prefix. It names the alias and the physical index, see [Index layout](#index-layout-and-the-alias). |
| `INGOT_OPENSEARCH_USERNAME` | `REPOSILITE_OPENSEARCH_USERNAME` | none | Any non-blank text. | The user of HTTP basic authentication. Set together with a password. Without a username no credentials are sent, so use that only for a cluster that runs without security. |
| `INGOT_OPENSEARCH_PASSWORD` | `REPOSILITE_OPENSEARCH_PASSWORD` | none | Any non-blank text. | The password of the user. Requires a username. When set, `INGOT_OPENSEARCH_PASSWORD_FILE` is not read. |
| `INGOT_OPENSEARCH_PASSWORD_FILE` | `REPOSILITE_OPENSEARCH_PASSWORD_FILE` | none | A path to a file that holds the password. A relative path is resolved against the working directory. | Reads the password from the file. The file is read once at start, and line breaks at its end are removed. An empty file, or a file that cannot be read, stops the backend with a message that names the variable and not the path content. Requires a username. |
| `INGOT_OPENSEARCH_BULK_SIZE` | `REPOSILITE_OPENSEARCH_BULK_SIZE` | `500` | Whole number from `1` to `5000`. | Actions per bulk request. A bulk request also stays below 5 MB. |
| `INGOT_OPENSEARCH_TIMEOUT_SECONDS` | `REPOSILITE_OPENSEARCH_TIMEOUT_SECONDS` | `30` | Whole number of seconds from `1` to `300`. | Timeout of one request to the cluster. |
| `INGOT_OPENSEARCH_REPLICAS` | `REPOSILITE_OPENSEARCH_REPLICAS` | `1` | Whole number from `0` to `10`. | Replica shards of the index. It is set when the index is created. A change later does not reach an existing index. |

A value that is not a whole number, or that is outside its range, stops the backend with a message that names the variable and not its value. The backend then reports `OPEN_FAILED` and the server keeps running. The rules are the same for every replica, so set the same values on all of them.

The `INGOT_OPENSEARCH_PASSWORD` and `INGOT_OPENSEARCH_PASSWORD_FILE` pair is the way to keep the secret out of a manifest: the variable names the file, and the file holds the secret. The file is read when the backend opens, so a rotated password takes effect when each replica opens the backend again, which happens at its next start.

### Refused combinations

| Setting | Result |
| --- | --- |
| `INGOT_OPENSEARCH_URLS` is unset or blank, with the plugin on | The backend stops with a message that names `INGOT_OPENSEARCH_URLS`. |
| A URL contains user information, such as `https://user:password@host` | The backend stops. The message names the variable and never the URL or its user information. Put the credentials into `INGOT_OPENSEARCH_USERNAME` and `INGOT_OPENSEARCH_PASSWORD`. |
| An `http://` URL while `INGOT_OPENSEARCH_ALLOW_HTTP` is `false` | The backend stops, and the message names the switch. |
| A URL with any other scheme, such as `ftp` | The backend stops, and the message names the variable. |
| A username without a password, or a password without a username | The backend stops, and the message names the variable that is missing. |

## Index layout and the alias

The plugin writes one physical index, `<prefix>-v1`, where `<prefix>` is the value of `INGOT_OPENSEARCH_INDEX`. The alias `<prefix>` points at it. Every read and write of the plugin uses the alias, so a later physical index can take the place of this one with one atomic change of the alias.

On the first start the plugin creates the index and the alias. On later starts it finds the alias and uses the index it points at. The alias must point at exactly one index.

The index has one primary shard, `INGOT_OPENSEARCH_REPLICAS` replicas and a refresh interval of one second. The shard count is not configurable in this version. The mapping is strict: a document with a field that the mapping does not know is refused.

The document id is the path hash of the database, the SHA-256 of the repository, a NUL byte and the path. Indexing the same file twice replaces one document, so a retried write does no harm.

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

## Security obligations

Ingot's responsibility ends at its own filters. The cluster is the responsibility of its operator, and the following obligations apply.

* **A dedicated user.** Create one user for Ingot. The user reads and writes only the alias `<prefix>` and the indices `<prefix>-*`. It has no access to other indices, and no dashboards or users of the cluster use it. The plugin needs these groups of actions: create an index, manage aliases, read and write documents, and read the mapping and the cluster health. Check the names against the security plugin of your cluster version.
* **No script permission.** The reorigin script is a fixed inline constant of the plugin, sent with each request. Its values are the parameters `origin` and `source_host`, and no value of a request enters its text. The user needs no cluster permission for stored scripts, and the plugin never writes a script into the cluster.
* **Network policy.** Only the Ingot replicas can reach the cluster. Allow the port of the cluster for the replicas and for nothing else.
* **TLS.** Use `https` URLs. Keep the verification of the certificate on. The plugin has no option to skip it, see [TLS](#tls).
* **Audit logging.** Enable the audit log of the cluster for the user, so that a write or a search by the Ingot user can be traced.
* **Rotation.** Rotate the password of the user, and write the new value to the file that `INGOT_OPENSEARCH_PASSWORD_FILE` names. A restart of each replica applies it. The cluster does not need a restart.

Ingot's own administration never shows the URL, the user name or the password of the cluster, and the password never appears in a log, a status response or a support bundle.

## TLS

The plugin uses the default `SSLContext` of the JVM, with hostname verification on. A certificate is accepted when the JVM trusts its authority, and a name that does not match the certificate is refused.

To trust an internal certificate authority, put its certificate in a directory of PEM files and name the directory in `INGOT_EXTRA_CA_CERTS`. The server image imports the certificates into a copy of the JVM trust store at start. The steps are in [Install with Docker](../how-to/install-with-docker.md#trust-a-private-certificate-authority). A JAR installation trusts what the JVM trusts, so add the authority to that trust store.

## Errors and states

The backend reports one of the states of [Backend states](../how-to/choose-a-search-backend.md#backend-states). The states that the OpenSearch backend reaches are these.

| State | When | Detail and behavior |
| --- | --- | --- |
| `OPEN_FAILED` | A setting is refused, the password file cannot be read, the cluster is red or cannot be reached while the backend opens, the alias points at more than one index, or the index records another mapping version. | `backend.detail` names the cause. The plugin does not write while the state is `OPEN_FAILED`, and it retries the open every 30 seconds. |
| `DOWN` | The cluster is red or unreachable, or the circuit is open after five transport failures in a row. | `backend.healthDetail` names the cause. While the circuit is open, which lasts 30 seconds, no request is sent to the cluster. Then one request is let through to test the cluster, and a success closes the circuit. Outbox entries are kept and retried. |
| Busy | The cluster answers `429` or `503` to a bulk request, or it rejects an item of a bulk request as too many requests. | This is back pressure and not an error. The batch is released, the outbox waits for the `Retry-After` delay of the cluster, or 5 seconds when the header is missing, and the attempt is not counted. Entries wait in the outbox, so the index lags behind storage until the cluster accepts the batch. |
| Rejected document | The cluster refuses a document, for example because its field does not match the mapping. | The batch fails with the error text, which is redacted. Each failed attempt is counted, and an entry becomes a dead letter after ten failures, as for the database backend. |

In every state except `AVAILABLE`, search answers `503` with `Retry-After: 30`, as described in [Backend states](../how-to/choose-a-search-backend.md#backend-states). Writes are visible to search after the refresh interval of one second, and the `backend` block of the status reports the interval.

Results of a write can lag behind a deploy by about a second. A file that was deleted can still appear for that long. The verify action of the Maintenance page repairs real drift, see [Rebuild the search index](../how-to/rebuild-search-index.md).

## Related

* [Use OpenSearch for search](../how-to/use-opensearch-for-search.md) is the setup, step by step.
* [Choose a search backend](../how-to/choose-a-search-backend.md) explains the states, the cluster rule and the switch between backends.
* [Environment variables](environment-variables.md#opensearch-search-backend) lists the variables with the other settings of the server.
* [Write a search backend plugin](../how-to/write-a-search-backend-plugin.md) describes the interfaces that this plugin implements.
