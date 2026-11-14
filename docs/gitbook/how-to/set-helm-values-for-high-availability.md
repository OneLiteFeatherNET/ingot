---
description: Set the Helm chart values for a server with more than one replica, with a shared database, shared storage, a shutdown budget and a disruption budget, render and check them before you install, and know which install notes are false warnings.
---

# Set Helm values for high availability

This page collects the chart values that a release with more than one server replica needs. It does not replace [Running more than one replica](run-on-kubernetes.md#running-more-than-one-replica), which explains each requirement, and it does not decide whether your setup is supported. For that, read [High availability support](../explanation/high-availability-support.md) and [Multi-replica operation](../explanation/multi-replica-operation.md) first.

## Which setups are supported

The page on high availability support lists the topology that the project tests on every pull request. In short: two or more replicas of one release, behind one Service, sharing a MariaDB database and S3 storage, with the optional NATS cluster bus. PostgreSQL is tested with one instance only.

Some setups have no test. Do not rely on them for more than one replica:

* SQLite, which is one file inside one pod.
* PostgreSQL with several replicas. The project tests PostgreSQL with one instance.
* A filesystem volume that several replicas mount. No test mounts one volume on two replicas.

## The values

Write the values of your release into `ingot-values.yaml`. This example uses two replicas, a database Secret, an S3 Secret, a cluster bus, a shared volume and a disruption budget:

```yaml
deployment:
  replicas: 2
  # The pod stays in the endpoints for 15 seconds, then the requests in flight get 30 seconds. 15 + 30 + 10 = 55 seconds
  # of grace period.
  shutdown:
    preStopSeconds: 15
    drainSeconds: 30
  topologySpreadConstraints:
    - maxSkew: 1
      topologyKey: topology.kubernetes.io/zone
      whenUnsatisfiable: DoNotSchedule
      labelSelector:
        matchLabels:
          app.kubernetes.io/name: ingot

database:
  # The Secret holds the key `database` with the connection string of the server.
  existingSecret: ingot-database
  secretKey: database

objectStorage:
  # The Secret holds AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY, and optionally AWS_REGION.
  existingSecret: ingot-s3-keys

clusterBus:
  url: tls://nats.nats.svc.cluster.local:4222
  passwordSecret: ingot-bus-password

persistence:
  # With S3 for the repositories, the volume holds the configuration and runtime files, and every replica mounts it, so it
  # needs ReadWriteMany. The project does not test a shared volume, see the list above.
  accessMode: ReadWriteMany

podDisruptionBudget:
  enabled: true

probes:
  # Readiness runs a database query and a storage listing. Liveness keeps the root path.
  readinessPath: /api/status/ready

env:
  - name: JAVA_OPTS
    value: "-Xmx512M"

resources:
  requests:
    cpu: 250m
    memory: 768Mi
  limits:
    cpu: 2.0
    memory: 1Gi
```

Each block points at a Secret or a value that you create first. The chart never renders the value of a Secret. Create the database Secret as in [Run on Kubernetes](run-on-kubernetes.md#create-the-database-secret), and the S3 and bus Secrets in the same way.

The memory limit must cover the heap and the memory that the JVM uses around it. The chart defaults, `-Xmx64M` and a 256Mi limit, are the upstream Reposilite values, and the values above are a starting point that was not measured. Size the heap and the limit for your load with [Size the deployment](size-the-deployment.md).

## What each block does

* `deployment.replicas` sets the count. With `autoscaling.enabled`, the autoscaler sets it instead, see [Autoscale the server](autoscale-the-server.md).
* `deployment.shutdown` keeps a stopping pod in the endpoints while the proxy removes it, and lets the requests in flight finish. The grace period becomes the sum of both values plus 10 seconds.
* `topologySpreadConstraints` spreads the pods over zones. The chart sets no default, because `DoNotSchedule` keeps a second replica pending on a single-node cluster.
* `database.existingSecret` renders `INGOT_LOCAL_DATABASE`, and the SQLite default is local to one pod.
* `objectStorage.existingSecret` gives every replica the same S3 credentials. The repositories must still use S3 in their settings, see [Use S3 storage](use-s3-storage.md).
* `clusterBus` renders the bus variables. Without the bus, the replicas converge through the database on the next poll.
* `persistence.accessMode` must allow several nodes, or `persistence.existingClaim` must name a shared volume. The default `ReadWriteOnce` allows one node only.
* `podDisruptionBudget` keeps one server pod down at a time during a node drain. See [Set a disruption budget](set-a-disruption-budget.md).
* `probes.readinessPath` takes a replica out of the load balancer while its database or storage check fails. Without it, the all-in-one readiness probe only checks that `/` answers.

For every key, see [Helm chart values](../reference/helm-values.md).

## Render and check before you install

Render the chart with your values. The command prints the objects and fails on a value that the chart rejects:

```bash
helm template ingot oci://ghcr.io/onelitefeathernet/charts/ingot \
  --version 1.1.1 \
  --namespace ingot \
  -f ingot-values.yaml > rendered.yaml
```

Check three things in `rendered.yaml`: the Deployment has `replicas: 2`, the `ReadWriteMany` access mode is on the claim, and the `PodDisruptionBudget` shows `maxUnavailable: 1`.

{% hint style="warning" %}
The install notes read only the `env` list. With the typed values `database.existingSecret` and `clusterBus.url` set, the notes still warn that `INGOT_LOCAL_DATABASE` and `INGOT_CLUSTER_URL` are missing from `env`. The variables are rendered, and you can see them in the Deployment. The warnings do not stop the install and can be ignored for this shape. The other warnings, about the access mode and the persistence, describe the values that you set, and they stay correct.
{% endhint %}

After the install, check the rollout and the disruption budget:

```bash
kubectl -n ingot rollout status deployment/ingot
kubectl -n ingot get pods -l app.kubernetes.io/name=ingot -o wide
kubectl -n ingot get pdb ingot
```

Both pods must be `Running`. With the spread constraint, they land in different zones when the cluster has more than one zone. The budget must show one allowed disruption.

## Upgrade a multi-replica release

Upgrade one release at a time with the same chart version and values file as before. During a rolling update, a replica of the previous release may keep running while the new release changes the schema. The rule that makes this safe, and its test evidence, is in [Rolling upgrade rule](../explanation/high-availability-support.md#rolling-upgrade-rule). Read it before you upgrade across a release that changes the schema. Start with [Back up before upgrading](back-up-before-upgrading.md).

## Related

* [Run on Kubernetes](run-on-kubernetes.md) installs the chart step by step.
* [Multi-replica operation](../explanation/multi-replica-operation.md) explains what each replica shares and what it keeps for itself.
* [High availability support](../explanation/high-availability-support.md) lists the tested and the untested topologies.
* [Autoscale the server](autoscale-the-server.md) and [Set a disruption budget](set-a-disruption-budget.md) cover the two availability features of the chart.
