---
description: Configure the optional cluster bus that lets several Ingot replicas share cache invalidations, revocations and log lines, with the bus URL sources and their precedence, the password and credentials settings, the instance identifier, the switches, and the default that changes nothing when no URL is set.
---

# Configure the cluster bus

The cluster bus is an optional message channel between the replicas of one Ingot database. A replica publishes an event when it changes state that other replicas keep in memory, such as a disabled token or a revoked session. The other replicas drop their copy of that state at once instead of waiting for it to expire.

The bus is a speed-up, not a source of truth. The database stays the only place where sessions, tokens, configuration and counters are held. A replica that misses an event still converges on the next poll or when its cache entry expires. See [Multi-replica operation](../explanation/multi-replica-operation.md).

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

Valkey is named first among the Redis family. Redis and Valkey are used only to publish and to receive events. They never hold locks, counters or sessions, and no server module is needed.

Any other scheme stops the start with a configuration error. The error names the accepted schemes and does not print the URL, because the URL may carry a password.

Example for a Kubernetes deployment, where the URL can live in a ConfigMap and the password in a Secret:

```
INGOT_CLUSTER_URL=nats://nats.example:4222
INGOT_CLUSTER_PASSWORD=<from a Secret>
```

## Password

A URL that carries a user name and password uses them. When the URL has no password, `INGOT_CLUSTER_PASSWORD` supplies it. The legacy name `REPOSILITE_CLUSTER_PASSWORD` is read when the first one is not set. A password in the URL always wins over both variables.

Ingot never writes the credentials to its log or to the status output. It shows the URL with the credentials replaced by `REDACTED`.

When credentials are sent over an unencrypted scheme (`nats`, `redis` or `valkey`), the log contains a warning that names the scheme. Use `tls`, `rediss` or `valkeys` to encrypt the connection.

## NATS credentials file

Brokers in operator mode accept no user name and password. They expect a `.creds` file that holds a user JWT and an NKEY seed. Point Ingot at that file with the system property `ingot.cluster.credentials` or the variable `INGOT_CLUSTER_CREDENTIALS`. The system property wins when both are set.

The file is used only with the `nats` and `tls` schemes. With a Redis or Valkey scheme it is ignored, and the log says so. Mount the file from a Secret. Ingot does not print its contents.

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
| `INGOT_CLUSTER_CONSOLE` | `true` when a bus is configured | Switches the forwarding of live console log lines between replicas, in both directions. Set `false` to keep the console of each replica to itself while the rest of the bus keeps working. See [Console fan-out](#console-fan-out) for the privacy note. |
| `INGOT_CLUSTER_FAILURES` | `false` | Switches the aggregation of recorded failures across replicas. Off, each replica lists only the failures it recorded. |

Accepted values are `true`, `false`, `1`, `0`, `on`, `off`, `yes` and `no`, in any case. Any other value stops the start and names the variable.

## Failure aggregation

With `INGOT_CLUSTER_FAILURES` set to `true` and a bus URL, each replica publishes the failures it records. The failure list of every replica then shows the failures of the other replicas as well, merged in memory when the list is read. Without the switch, nothing is published and each replica lists only its own failures.

* The merged failures live in memory. A restart clears them, as it clears the local list.
* A stack trace stays with the replica that recorded it. The other replicas show the source, the exception type and the message.
* The message of a failure crosses the bus, so anyone who can read the bus can read it. Limit the broker access rules to the replicas.
* Clearing the failures on one replica does not clear the failures of the others.
* The failure count in the status of a replica counts only the failures that replica recorded itself. The console command `failures` lists only the failures of the replica it runs on.

## Status of this release

The settings above are read and checked when the instance starts. The bus connects only through a transport that is part of the build for its scheme. When a URL names a scheme without a transport in the build, the instance logs a warning, runs without the bus, and keeps serving requests.

## Related pages

* [Multi-replica operation](../explanation/multi-replica-operation.md) lists the state that each replica keeps for itself.
* [Run on Kubernetes](run-on-kubernetes.md) describes the replica setup and its limits.
* [Environment variables](../reference/environment-variables.md#cluster-bus-variables) lists every variable with its fallback and default.
