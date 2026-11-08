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

## NATS over TLS

The `nats` scheme connects in clear text. The `tls` scheme requires TLS on the broker. Ingot uses the trust store of the Java runtime to check the broker certificate, and it has no separate trust setting for the bus. A certificate that the runtime does not trust stops the connection, and the log then says that the bus is not reachable. Use `tls` whenever credentials or console lines cross the network.

Ingot publishes and receives core NATS messages on the subject `ingot.<cluster>.events`, where `<cluster>` is the cluster identifier stored in the database. Replicas of other Ingot databases on the same broker use their own subject and never see these events. The broker keeps no copy of a message. An event sent while a replica is disconnected is lost, and that replica converges through the database as described above. Broker access rules can limit who publishes to or subscribes on `ingot.*`.

## Licensing

The NATS client library, `jnats`, is licensed under the Apache License 2.0, and Ingot bundles it in the server image. The NATS server is also Apache 2.0 licensed, so running a broker you operate yourself adds no license obligation for Ingot.

## Instance identifier

Each replica announces itself with an identifier. It defaults to the host name, which is the pod name under Kubernetes, so the default is usually right. To set it explicitly, use `INGOT_CLUSTER_INSTANCE_ID`. Use a value that is unique among the replicas of the database.

## Switches

| Variable | Default | Effect |
| --- | --- | --- |
| `INGOT_CLUSTER_CONSOLE` | `true` when a bus is configured | Switches the forwarding of live console log lines between replicas. Set `false` to keep the console of each replica to itself while the rest of the bus keeps working. |
| `INGOT_CLUSTER_FAILURES` | `false` | Switches the aggregation of recorded failures across replicas. Off, each replica lists only the failures it recorded. |

Accepted values are `true`, `false`, `1`, `0`, `on`, `off`, `yes` and `no`, in any case. Any other value stops the start and names the variable.

## Status of this release

The settings above are read and checked when the instance starts. The bus connects only through a transport that is part of the build for its scheme. When a URL names a scheme without a transport in the build, the instance logs a warning, runs without the bus, and keeps serving requests.

## Related pages

* [Multi-replica operation](../explanation/multi-replica-operation.md) lists the state that each replica keeps for itself.
* [Run on Kubernetes](run-on-kubernetes.md) describes the replica setup and its limits.
* [Environment variables](../reference/environment-variables.md#cluster-bus-variables) lists every variable with its fallback and default.
