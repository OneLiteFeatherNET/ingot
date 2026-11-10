---
description: Configure the optional cluster bus that lets several Ingot replicas share cache invalidations, revocations and log lines, with the bus URL sources and their precedence, the password and credentials settings, the instance identifier, the switches, and the default that changes nothing when no URL is set.
---

# Configure the cluster bus

The cluster bus is an optional message channel between the replicas of one Ingot database. A replica publishes an event when it changes state that other replicas keep in memory, such as a disabled token or a revoked session. The other replicas drop their copy of that state at once instead of waiting for it to expire.

The bus is a speed-up, not a source of truth. The database stays the only place where sessions, tokens, configuration and counters are held. A replica that misses an event still converges on the next poll or when its cache entry expires, except for javadoc caches, see [Javadoc caches](#javadoc-caches). See [Multi-replica operation](../explanation/multi-replica-operation.md).

## Before you begin

* Every replica uses the same database. The bus does not replace a shared database. See [Configure a database](configure-database.md).
* You have a message broker that every replica can reach. The bus URL names it.
* You know which of the replicas should show each other's log lines. See the switches below.

## The default changes nothing

Without a bus URL in any of the sources below, an instance behaves as it did before the bus existed. It opens no connection to a broker, starts no bus thread and publishes no event. You do not need to set any variable on a single instance or on a deployment that does not use the bus.

## Set the bus URL

The URL is read from the first source that defines it:

1. The system property `ingot.cluster.url`, for example `-Dingot.cluster.url=nats://nats.example:4222` in `JAVA_OPTS`.
2. The environment variable `INGOT_CLUSTER_URL`.
3. The environment variable `REPOSILITE_CLUSTER_URL`, which an installation that still uses the Reposilite name can keep.

The accepted schemes are:

| Scheme | Broker | Encrypted |
| --- | --- | --- |
| `nats` | NATS | no |
| `tls` | NATS over TLS | yes |
| `redis` | Redis | no |
| `rediss` | Redis over TLS | yes |
| `valkey` | Valkey | no |
| `valkeys` | Valkey over TLS | yes |
| `database` | The instance database, with no broker | the database connection |

Valkey is named first among the Redis family. Redis and Valkey are used only to publish and to receive events. They never hold locks, counters or sessions, and no server module is needed.

Any other scheme stops the start with a configuration error. The error names the accepted schemes and does not print the URL, because the URL may carry a password. The `database` scheme is the exception: it does not stop the start, see [Database transport](#database-transport).

Example for a Kubernetes deployment, where the URL can live in a ConfigMap and the password in a Secret:

```
INGOT_CLUSTER_URL=nats://nats.example:4222
INGOT_CLUSTER_PASSWORD=<from a Secret>
```

## Password

A URL that carries a user name and password uses them. When the URL has no password, `INGOT_CLUSTER_PASSWORD` supplies it. The legacy name `REPOSILITE_CLUSTER_PASSWORD` is read when the first one is not set. A password in the URL always wins over both variables.

Ingot never writes the credentials to its log or to the status output. It shows the URL with the credentials replaced by `REDACTED`.

When credentials are sent over an unencrypted scheme (`nats`, `redis` or `valkey`), the log contains a warning that names the scheme. Use `tls`, `rediss` or `valkeys` to encrypt the connection.

## Valkey and Redis

Valkey is the first choice for the Redis family. It is released under the BSD-3-Clause licence. Redis 8 is distributed under RSALv2, SSPLv1 or AGPLv3, so check the terms of the server you run before you deploy it. Ingot uses only the plain publish and subscribe commands, so the same configuration works with either server. The Ingot transport is built on Jedis, which is released under the MIT licence.

Use `valkey` or `valkeys` for Valkey, and `redis` or `rediss` for Redis. Both names of a server reach the same transport, so `valkey://` and `redis://` to one host behave alike. The TLS variants `valkeys` and `rediss` encrypt the connection.

```
INGOT_CLUSTER_URL=valkeys://valkey.example:6379
INGOT_CLUSTER_PASSWORD=<from a Secret>
```

Each replica opens one connection that subscribes to the channel of its cluster, and publishes over a second connection. The channel name contains the cluster identifier of the database, so several Ingot installations can share one server without mixing their events. When the server is down, a replica keeps serving requests, logs the change of connection state once, and reconnects with a growing delay of up to one minute. An event published during the outage is lost, and the database fallbacks described at the top of this page apply.

## NATS credentials file

Brokers in operator mode accept no user name and password. They expect a `.creds` file that holds a user JWT and an NKEY seed. Point Ingot at that file with the system property `ingot.cluster.credentials` or the variable `INGOT_CLUSTER_CREDENTIALS`. The system property wins when both are set.

The file is used only with the `nats` and `tls` schemes. With a Redis or Valkey scheme it is ignored, and the log says so. Mount the file from a Secret. Ingot does not print its contents.

## NATS over TLS

The `nats` scheme connects in clear text. The `tls` scheme requires TLS on the broker. Ingot uses the trust store of the Java runtime to check the broker certificate, and it has no separate trust setting for the bus. A certificate that the runtime does not trust stops the connection, and the log then says that the bus is not reachable. Use `tls` whenever credentials or console lines cross the network.

Ingot publishes and receives core NATS messages on the subject `ingot.<cluster>.events`, where `<cluster>` is the cluster identifier stored in the database. Replicas of other Ingot databases on the same broker use their own subject and never see these events. The broker keeps no copy of a message. An event sent while a replica is disconnected is lost, and that replica converges through the database as described above. Broker access rules can limit who publishes to or subscribes on `ingot.*`.

## Licensing

The NATS client library, `jnats`, is licensed under the Apache License 2.0, and Ingot bundles it in the server image. The NATS server is also Apache 2.0 licensed, so running a broker you operate yourself adds no license obligation for Ingot.

## Database transport

The `database:` scheme needs no broker. Each replica publishes its events as rows of a table, `cluster_event`, in its own database, and reads the rows of the other replicas. It uses the connection pool that the instance already has. Use it when the replicas run on MariaDB, MySQL or PostgreSQL and you do not want to operate a message broker.

Set the URL to `database:` with nothing after the colon. `database://` is accepted as well. A host or credentials in the URL are refused, and the error does not print the URL.

The transport is refused on SQLite and H2. A file database cannot be shared between instances. The instance logs one error that names the databases the transport needs, keeps the bus off and keeps serving requests.

An event reaches the other replicas within one poll interval after it is stored. The interval is set with `INGOT_CLUSTER_POLL_INTERVAL_MS`, which defaults to 1000 and is raised to 200 when it is set lower. Every replica reads the table once per interval, so a short interval adds database load. A replica that starts does not apply the rows stored before it started. Rows older than five minutes are removed by one replica per five-minute window. An event larger than 16 KiB is not stored, and the log names its type.

Console forwarding is off on this transport by default, because every log line would become a row. Set `INGOT_CLUSTER_CONSOLE=true` to turn it on.

## Instance identifier

Each replica announces itself with an identifier. It defaults to the host name, which is the pod name under Kubernetes, so the default is usually right. To set it explicitly, use `INGOT_CLUSTER_INSTANCE_ID`. Use a value that is unique among the replicas of the database.

## Console fan-out

With a bus configured, each replica forwards the live log lines it shows in its console to the other replicas. An administrator connected to the console of one replica sees the lines of all replicas. Each forwarded line is labelled with the replica that logged it, for example `[ingot-1] ...`. A line is forwarded only at the visibility threshold of the replica that logged it, and it is shown once on each replica.

A forwarded line is not written to the local log of the receiving replica. It exists only in the live console sessions that are connected at that moment, and the console does not keep it for later sessions.

Set `INGOT_CLUSTER_CONSOLE=false` to turn fan-out off. Each replica then keeps its console to itself, and token, configuration and session events still propagate.

### Privacy

A forwarded line leaves the replica in clear text and travels through the bus to every other replica. Anyone who can subscribe to the bus can read it, including the broker operator and every account that has subscribe permission on the subject. Log lines can contain host names, client addresses, repository paths and user names, so give the bus the same trust as the database. Use a `tls`, `rediss` or `valkeys` scheme for a bus that crosses a network boundary. Turn fan-out off when the lines of one replica must not leave it.

## Switches

| Variable | Default | Effect |
| --- | --- | --- |
| `INGOT_CLUSTER_CONSOLE` | `true` when a bus is configured, `false` on the database transport | Switches the forwarding of live console log lines between replicas, in both directions. Set `false` to keep the console of each replica to itself while the rest of the bus keeps working. See [Console fan-out](#console-fan-out) for the privacy note. |
| `INGOT_CLUSTER_POLL_INTERVAL_MS` | `1000` | Poll interval of the database transport in milliseconds. Values below 200 are raised to 200. Other transports ignore it. |
| `INGOT_CLUSTER_FAILURES` | `false` | Switches the aggregation of recorded failures across replicas. Off, each replica lists only the failures it recorded. |

Accepted values are `true`, `false`, `1`, `0`, `on`, `off`, `yes` and `no`, in any case. Any other value stops the start and names the variable.

## Failure aggregation

With `INGOT_CLUSTER_FAILURES` set to `true` and a bus URL, each replica publishes the failures it records. The failure list of every replica then shows the failures of the other replicas as well, merged in memory when the list is read. Without the switch, nothing is published and each replica lists only its own failures.

* The merged failures live in memory. A restart clears them, as it clears the local list.
* A stack trace stays with the replica that recorded it. The other replicas show the source, the exception type and the message.
* The message of a failure crosses the bus, so anyone who can read the bus can read it. Limit the broker access rules to the replicas.
* Clearing the failures on one replica does not clear the failures of the others.
* The failure count in the status of a replica counts only the failures that replica recorded itself. The console command `failures` lists only the failures of the replica it runs on.

## Javadoc caches

When a javadoc jar is deployed, the replica that accepted the upload drops its unpacked copy of the artifact and publishes a `javadoc.invalidated` event. The other replicas drop their copy of the same artifact when the event arrives. Without a bus URL, only the accepting replica drops its copy.

Javadoc caches are the one kind of state on the bus without a poll or a time bound. A replica that misses the event keeps serving its unpacked copy of the old build until that copy is removed. The copy survives a restart as long as the working directory of the replica keeps it. It is removed when the same artifact is deployed again and that replica receives the event, or when the working directory is emptied. Plan a restart with an empty working directory when a replica may have missed a deploy, for example after a network outage to the broker.

## Cluster view in the status

With a bus URL, the instance status (`GET /api/status/instance`, which the dashboard shows) gets a `cluster` section. Without a bus URL the section is absent, and the response is the same as before the bus existed.

The section has two parts:

* `bus` reports the connection state (`connected`, `reconnecting` or `disabled`), the time of the last message received from the bus, and the number of messages this replica published, applied and ignored. On the database transport it also shows `dropped`, the number of events too large to be stored. The URL is shown with `REDACTED` in place of the credentials.
* `instances` lists this replica first, then every other replica the bus has reported on, sorted by identifier. Each entry shows its version, its last restart, its memory and threads, its failures, its health (`up`, `down` or `unknown`) and its recent samples.

The cluster summary above the list follows fixed rules. Memory and threads are summed over the instances, and so are the failure counts. The uptime is the shortest one, which belongs to the most recent restart. The version is shown once when all replicas agree, and as `mixed` with the list of versions otherwise. The health is that of the worst instance. Failure lists stay per replica unless `INGOT_CLUSTER_FAILURES` is on.

Each replica publishes a snapshot whenever it records one of its own status snapshots, which is every minute for the first twelve and then every hour. The stale rule applies to the entries of the other replicas. An entry is stale when its last snapshot is older than one interval. A stale entry stays in the list, marked as stale, so that a replica which stopped reporting remains visible. An entry is dropped when its last snapshot is older than three intervals.

The memory and thread graphs draw one line per replica. They never add the replicas together, because each replica samples at its own moment.

## Status of this release

The settings above are read and checked when the instance starts. The bus connects only through a transport that is part of the build for its scheme. When a URL names a scheme without a transport in the build, the instance logs a warning, runs without the bus, and keeps serving requests.

## Related pages

* [Multi-replica operation](../explanation/multi-replica-operation.md) lists the state that each replica keeps for itself.
* [Run on Kubernetes](run-on-kubernetes.md) describes the replica setup and its limits.
* [Environment variables](../reference/environment-variables.md#cluster-bus-variables) lists every variable with its fallback and default.
