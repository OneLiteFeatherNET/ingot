---
description: Run Ingot on Kubernetes with the Ingot Helm chart, with a database Secret, S3 storage, health checks, persistence, autoscaling and ingress, and the limits of running more than one replica.
---

# Run on Kubernetes

This page installs Ingot with the Ingot Helm chart, which is published as an OCI chart with each release. The chart sets the Deployment, Service, probes, persistence, autoscaling and ingress. You pass the Ingot settings through environment variables. Its values keep the keys of the upstream Reposilite chart, so [migrating an existing release](migrate-from-upstream-chart.md) is a values diff. To run the dashboard as its own workload, see [Run the dashboard split from the server with Helm](run-split-with-helm.md).

{% hint style="warning" %}
Several replicas are not fully supported yet. Read [Running more than one replica](#running-more-than-one-replica) before you set `replicas` above `1` or enable autoscaling.
{% endhint %}

## Before you begin

* A Kubernetes cluster and `kubectl` with access to a namespace for Ingot.
* Helm 3.8 or later, which installs charts from an OCI registry.
* A database for anything beyond one replica. MariaDB or PostgreSQL. The default SQLite file works for one replica only.
* An S3 bucket for repositories, if you run more than one replica. Filesystem storage needs a volume that several pods can mount.
* An ingress controller, if you want a host name. Autoscaling needs the Kubernetes metrics server.

The chart version and its `appVersion` are the Ingot release, so the chart and the image of a release always match. The image digest in the values below pins the exact image as well.

## Read the chart values

```bash
helm show values oci://ghcr.io/onelitefeathernet/charts/ingot --version 1.1.1 > ingot-chart-values.yaml
```

Replace `1.1.1` with the release you deploy. The file lists every value of the chart with its default. Use it as a reference. Your own values file only needs the keys that change.

## Create the database Secret

Ingot reads its database from the local configuration key `database`. The environment variable `INGOT_LOCAL_DATABASE` overrides it. Store the value in a Secret so that the password stays out of the chart values. The chart reads the key through `database.existingSecret` and never renders the value. The key defaults to `database` and can be changed with `database.secretKey`.

The value has the form `<engine> <host>:<port> <database> <user> <password>`:

```bash
kubectl create namespace ingot

kubectl -n ingot create secret generic ingot-database \
  --from-literal=database='mariadb ingot-db.databases.svc.cluster.local:3306 ingot ingot-user the-password'
```

Use `postgresql` as the engine for PostgreSQL. The password is split on spaces, so it must not contain a space.

{% hint style="info" %}
SQLite, MariaDB and PostgreSQL are supported. MySQL and H2 are experimental. The code comment for the database key still labels PostgreSQL as experimental, so check the release notes of your version before you rely on it in production.
{% endhint %}

## Write the values

Create `ingot-values.yaml`. Replace the digest with the digest of the release you deploy. While `image.digest` is set, the chart renders the image as `name@digest` and ignores `image.tag`.

```yaml
image:
  name: ghcr.io/onelitefeathernet/ingot
  digest: "sha256:<digest>"

deployment:
  replicas: 1

database:
  # Renders INGOT_LOCAL_DATABASE from the key `database` of the Secret ingot-database.
  existingSecret: ingot-database
  secretKey: database

env:
  - name: JAVA_OPTS
    value: "-Xmx768M"

service:
  port: 8080

probes:
  port: 8080

persistence:
  enabled: true
  size: 4Gi
  accessMode: ReadWriteOnce
  path: /app/data

resources:
  requests:
    cpu: 500m
    memory: 512Mi
  limits:
    memory: 1Gi
```

{% hint style="warning" %}
The chart defaults are upstream Reposilite defaults: `-Xmx64M` and a memory limit of `256Mi`. The values above are starting points that were not measured. Size the heap and the limits for your own load.
{% endhint %}

## Install the release

```bash
helm upgrade --install ingot oci://ghcr.io/onelitefeathernet/charts/ingot \
  --version 1.1.1 \
  --namespace ingot \
  -f ingot-values.yaml
```

The release is named `ingot` and the chart is named `ingot`, so the Deployment and the Service are both named `ingot`.

## Verify the chart and the image (optional)

The Helm chart and the image of each release are signed by the release workflow of this repository. Checking the signature before the first install is optional, and the install above does not depend on it. You need [cosign](https://docs.sigstore.dev/cosign/system_config/installation/) installed.

```bash
cosign verify \
  --certificate-identity https://github.com/OneLiteFeatherNET/ingot/.github/workflows/release-please.yml@refs/heads/main \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com \
  ghcr.io/onelitefeathernet/charts/ingot:1.1.1

cosign verify \
  --certificate-identity https://github.com/OneLiteFeatherNET/ingot/.github/workflows/release-please.yml@refs/heads/main \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com \
  ghcr.io/onelitefeathernet/ingot:1.2.0
```

A signature from this repository exits with `0`. A failure means that the artifact was not signed by the release workflow on `main`. Nightly images carry no signature, so this check fails for them by design. The identity and the checks for the dashboard image and the standalone jar are in [Verify a release](https://github.com/OneLiteFeatherNET/ingot/blob/main/.github/RELEASE.md#verify-a-release).

If Flux reconciles the chart from an `OCIRepository`, Flux can verify the same signature before it installs anything. Set `verify.provider` to `cosign` and match the identity of the release workflow:

```yaml
spec:
  verify:
    provider: cosign
    matchOIDCIdentity:
      issuer: "^https://token\\.actions\\.githubusercontent\\.com$"
      subject: "^https://github\\.com/OneLiteFeatherNET/ingot/\\.github/workflows/release-please\\.yml@refs/heads/main$"
```

## Check the rollout and the health

Wait for the Deployment, then ask the instance for its health through a port forward:

```bash
kubectl -n ingot rollout status deployment/ingot
kubectl -n ingot port-forward svc/ingot 8080:8080
```

In a second shell:

```bash
curl -fsS http://localhost:8080/api/status/health
```

The answer is `{"status":"UP"}` with status `200`. A failing instance answers `503` with `{"status":"DOWN"}`.

## Health checks

The chart runs a readiness probe and a liveness probe. In the default all-in-one mode both send `GET /` to port `8080`. Set `probes.path` to point them at another path.

Set `probes.readinessPath` to `/api/status/ready` to send only the readiness probe to the readiness endpoint. It reports the database, the storage and, with a cluster bus, the bus. A replica that fails a check leaves the load balancer rotation, and the liveness probe does not restart it, so a database outage does not restart the pod.

```yaml
probes:
  readinessPath: /api/status/ready
```

{% hint style="warning" %}
With `probes.readinessPath` empty, the all-in-one readiness probe checks whether `/` answers, so a replica whose database is down can still pass it. If you turn off the dashboard with `INGOT_LOCAL_DEFAULTFRONTEND=false`, check that `/` still answers with a success status before you rely on the probes. This behaviour was not verified for this page.
{% endhint %}

In split mode the readiness probe uses `/api/status/ready` by default, with a 3 second timeout and two failures. See [Run the dashboard split from the server with Helm](run-split-with-helm.md#probes-and-shutdown).

Kubernetes ignores the `HEALTHCHECK` of the image. Only the probes of the chart apply.

## Persistence

The chart mounts one volume at `/app/data`, the working directory of Ingot. The working directory holds:

* the generated local configuration, `configuration.cdn`,
* the SQLite database file `reposilite.db`, when SQLite is in use,
* the filesystem repositories, under `repositories/`, unless a repository uses S3.

Keep the volume when you use SQLite or filesystem storage. With MariaDB or PostgreSQL and S3 storage, the volume only holds configuration and runtime files, and you can set `persistence.enabled: false` to use an `emptyDir` instead.

The chart's default access mode is `ReadWriteOnce`. A volume with that mode can be attached to one node only. Use a storage class with `ReadWriteMany` when several pods need the same filesystem.

{% hint style="info" %}
The image starts as root and lets its entrypoint switch to the service user. The `PUID`, `PGID` and `REPOSILITE_FORCE_CHOWN` variables apply to that path. Leave `deployment.containerSecurityContext.enabled` at `false` unless the volume is already writable for the UID you set.
{% endhint %}

## Run under the restricted Pod Security level

A namespace that enforces the Pod Security `restricted` level rejects the default pod, because the image starts as root. Set `deployment.securityProfile` to run the server without root:

```yaml
deployment:
  securityProfile: restricted
```

The `restricted` profile runs the pod and the container as uid and gid `977`, sets `fsGroup` to `977`, uses the `RuntimeDefault` seccomp profile, drops every capability and disallows privilege escalation. The root filesystem is read-only. The chart mounts two `emptyDir` volumes for the paths that the server writes, `ingot-tmp` at `/tmp` and `ingot-logs` at `/var/log/reposilite`. The data volume stays as it is.

The profile does not change who owns existing files. The entrypoint does not change ownership either, because it runs only as root. The data volume must therefore belong to uid `977`. A volume that a deployment wrote under another uid, for example uid `999` from `containerSecurityContext.runAsUser`, cannot be written by the server. Change its owner once, with the Deployment scaled to zero and in a namespace that does not enforce the restricted level:

```bash
kubectl -n ingot scale deployment/ingot --replicas=0

kubectl -n ingot run ingot-chown --rm -i --restart=Never --image=busybox:1.36 \
  --overrides='{"spec":{"containers":[{"name":"ingot-chown","image":"busybox:1.36","command":["chown","-R","977:977","/app/data"],"volumeMounts":[{"name":"data","mountPath":"/app/data"}]}],"volumes":[{"name":"data","persistentVolumeClaim":{"claimName":"ingot"}}]}}'
```

Replace `ingot` with your release name and claim name, and scale the Deployment back up afterwards. The chart does not run this step for you, because an init container that runs `chown` would run as root, which the restricted level forbids.

On clusters that assign the uid, such as OpenShift, set `deployment.securityProfile` to `restricted-openshift`. The chart then sets the same fields without `runAsUser`, `runAsGroup` and `fsGroup`, so the cluster's assigned values apply. The chart checks this variant with render tests only, not on an OpenShift cluster.

Do not set `podSecurityContext.enabled` or `containerSecurityContext.enabled` together with a profile. The render fails and names both keys.

## Size the heap from the memory limit

The chart does not change the heap unless you ask it to. The `jvm` block sets JVM flags in `JDK_JAVA_OPTIONS`, which the JVM reads before `JAVA_OPTS`. Each key renders its flag only when it is set:

```yaml
jvm:
  maxRAMPercentage: 70       # -XX:MaxRAMPercentage=70, the heap follows the memory limit
  initialRAMPercentage: 20   # -XX:InitialRAMPercentage=20
  gc: UseG1GC                # -XX:+UseG1GC
  activeProcessorCount: 2    # -XX:ActiveProcessorCount=2
  extraOptions:              # appended unchanged
    - -XX:+ExitOnOutOfMemoryError
```

The chart keeps the text of a `JDK_JAVA_OPTIONS` entry that you set in `env` and appends its flags after it. If that entry sets the same option as a `jvm` key, the render fails and names the option. Remove one of them.

An explicit `-Xmx` wins over a percentage. The chart default `JAVA_OPTS=-Xmx64M` sets one, so `maxRAMPercentage` has no effect until you remove the `-Xmx` from `JAVA_OPTS`. `helm install` prints a warning when both are set. Set `env` without `JAVA_OPTS`, or with a `JAVA_OPTS` that has no `-Xmx`:

```yaml
jvm:
  maxRAMPercentage: 70

env: []
```

The percentage applies to the container memory limit, so set `resources.limits.memory` as well. The chart does not provide measured sizes; choose the percentages for your own load.

## Use S3 for repositories

Storage is set per repository in the maven settings. To store a repository in S3, set its `storageProvider` to `s3`. The endpoint can be an in-cluster service, for example a MinIO service:

```json
{
  "id": "releases",
  "storageProvider": {
    "type": "s3",
    "bucketName": "ingot-releases",
    "endpoint": "http://minio.storage.svc.cluster.local:9000",
    "region": "us-east-1",
    "accessKey": "",
    "secretKey": ""
  }
}
```

Send the whole maven domain to `PUT /api/settings/domain/maven`, or save it in the dashboard.

A bucket with a custom endpoint, such as MinIO, usually needs path-style access. Set it in `JAVA_OPTS`:

```yaml
env:
  - name: JAVA_OPTS
    value: "-Xmx768M -Dreposilite.s3.pathStyleAccessEnabled=true"
```

Leave `accessKey` and `secretKey` empty to use the AWS default credential chain. That chain reads the environment variables of the pod, for example from a Secret through `envFrom`, and the IAM role of a service account. Ingot reads `AWS_REGION` from the environment too. Using a Secret keeps the credentials out of the database.

On Kubernetes, `objectStorage.existingSecret` gives the pods these variables without an `env` entry. Create a Secret with the keys `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`, and the optional key `AWS_REGION`:

```bash
kubectl -n ingot create secret generic ingot-s3-keys \
  --from-literal=AWS_ACCESS_KEY_ID='<access-key-id>' \
  --from-literal=AWS_SECRET_ACCESS_KEY='<secret-access-key>' \
  --from-literal=AWS_REGION='us-east-1'
```

```yaml
objectStorage:
  existingSecret: ingot-s3-keys
```

The chart renders each variable as a reference to a key of that Secret, so the values stay in the cluster. The first two keys must exist, or the pod does not start. `AWS_REGION` may be left out.

## Scale

Set `deployment.replicas` for a fixed count, or turn on autoscaling. With autoscaling enabled, the chart leaves `replicas` out of the Deployment and the autoscaler sets the count:

```yaml
deployment:
  replicas: 2

autoscaling:
  enabled: true
  minReplicas: 2
  maxReplicas: 4
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 60
```

Apply these only after the requirements in the next section hold.

With more than one replica, spread the pods over failure domains, so that one node or zone failure does not take every replica down. `deployment.topologySpreadConstraints` passes its list to the server pods unchanged. The chart sets no default, because a `DoNotSchedule` constraint keeps a second replica pending on a single-node cluster:

```yaml
deployment:
  replicas: 2
  topologySpreadConstraints:
    - maxSkew: 1
      topologyKey: topology.kubernetes.io/zone
      whenUnsatisfiable: DoNotSchedule
      labelSelector:
        matchLabels:
          app.kubernetes.io/name: ingot
```

In split mode, `dashboard.topologySpreadConstraints` takes the same kind of list for the dashboard pods.

## Running more than one replica

Several replicas need all of the following:

* A database shared by all pods. SQLite is a file inside one pod and cannot be shared. The default `sqlite reposilite.db` is for one replica only.
* S3 storage for every repository, or a `ReadWriteMany` volume for the filesystem repositories.
* A `ReadWriteMany` volume, or `persistence.enabled: false`, so that pods on other nodes can start.

{% hint style="warning" %}
As of this writing, the code does not yet do the following, which the design for Kubernetes high availability calls for:

* Schema creation runs in every pod at startup, with no lock across pods.
* A metadata write, such as a deployment that updates `maven-metadata.xml`, is not locked across pods. Two pods that deploy the same artifact at the same time can lose one version from the metadata.
* The all-in-one probes use `/` unless `probes.readinessPath` is set, and shutdown does not wait for in-flight requests unless `deployment.shutdown` is set. Statistics are not flushed at shutdown.

Until these are fixed, run one replica. Several replicas are only safe if you accept these risks. The design is in `docs/superpowers/specs/2026-08-08-kubernetes-ha-design.md` in the repository.
{% endhint %}

An optional cluster bus lets the replicas drop cached state faster when another replica changes it. Without a bus URL nothing changes. See [Configure the cluster bus](configure-cluster-bus.md). With more than one replica or the autoscaler on, the install notes warn about the settings that are missing, such as a `ReadWriteOnce` claim or no `INGOT_LOCAL_DATABASE`. The warnings do not stop the install. See [Multi-replica operation](../explanation/multi-replica-operation.md#what-the-helm-chart-warns-about).

### Shutdown budget

A rolling update stops one pod after another. Two optional values let a pod leave in an orderly way: a `preStop` sleep keeps the pod in the endpoints while the proxy removes it, and `drainSeconds` sets `INGOT_SHUTDOWN_TIMEOUT`, the time Ingot waits for requests in flight.

```yaml
deployment:
  shutdown:
    preStopSeconds: 15
    drainSeconds: 30
```

Both values default to `0`, which keeps the immediate stop and the 60 second grace period. When either is above `0`, the grace period becomes `preStopSeconds + drainSeconds + 10`, here 55 seconds, so the kubelet does not kill the pod before the budget ends. The values apply to every pod, so a rolling update takes about their sum longer for each replica. If you set `deployment.lifecycle`, your own `preStop` action is kept and `preStopSeconds` renders no sleep. An `INGOT_SHUTDOWN_TIMEOUT` set in `env` is kept as well.

The chart sets the bus with the `clusterBus` values. `clusterBus.url` renders `INGOT_CLUSTER_URL`. `clusterBus.passwordSecret` renders `INGOT_CLUSTER_PASSWORD` from the key `password` of a Secret. `clusterBus.credentialsSecret` mounts a Secret with the key `nats.creds` read-only, and points `INGOT_CLUSTER_CREDENTIALS` at the file. A broker in operator mode needs the credentials file, not a password.

```bash
kubectl -n ingot create secret generic ingot-bus-password --from-literal=password='<password>'
```

```yaml
clusterBus:
  url: tls://nats.nats.svc.cluster.local:4222
  passwordSecret: ingot-bus-password
```

Use `tls` when the credentials cross the network, because the `nats` scheme connects in clear text. The broker must serve TLS for that scheme.

## Expose the instance with ingress

Turn on the ingress of the chart and set your host name and ingress class:

```yaml
ingress:
  enabled: true
  className: <your-ingress-class>
  hosts:
    - host: ingot.example.org
      paths:
        - path: /
          pathType: Prefix
  tls:
    - secretName: ingot-tls
      hosts:
        - ingot.example.org
```

Apply the change with `helm upgrade --install` and the same `--version` and values file as before. If you run Ingot under a path prefix, see [Run behind a reverse proxy and base path](run-behind-reverse-proxy.md).

## Related

* [Run with Docker](install-with-docker.md) describes the same image without an orchestrator.
* [Configure a database](configure-database.md) lists the database forms and their rules.
* [Configure the cluster bus](configure-cluster-bus.md) sets the optional message bus between replicas.
* [Use S3 storage](use-s3-storage.md) explains the S3 settings per repository.
* [Create proxy repositories](create-proxy-repositories.md) for a proxy with a shared or large cache.
