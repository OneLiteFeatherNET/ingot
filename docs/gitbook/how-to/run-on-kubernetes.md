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

## Trace with the OpenTelemetry Java agent

The `opentelemetry` block adds the OpenTelemetry Java agent to the server. An init container copies the agent jar into an emptyDir, the server mounts it read-only at `/otel`, and the agent flag is added to `JDK_JAVA_OPTIONS`. The dashboard gets no agent.

```yaml
opentelemetry:
  enabled: true
  agentImage: ghcr.io/open-telemetry/opentelemetry-operator/autoinstrumentation-java:2.30.0
  serviceName: ingot
  endpoint: http://otel-collector.observability.svc.cluster.local:4317
  protocol: grpc
  tracesExporter: otlp
  metricsExporter: otlp
  logsExporter: none   # the console output is usually shipped already
```

The chart sets `OTEL_SERVICE_NAME`, `OTEL_EXPORTER_OTLP_PROTOCOL`, the trace, metric and log exporters, and `OTEL_EXPORTER_OTLP_ENDPOINT` when `endpoint` is set. Set these in the block, not in `env`: a variable that both `env` and the block set fails the render. The agent image must carry the agent jar at `/javaagent.jar` and a shell with `cp`, and it is pinned by tag, which Renovate updates as it does for the other images. With the restricted Pod Security profile the init container runs under the same security context as the server.

If your cluster runs the OpenTelemetry Operator, you can leave `opentelemetry.enabled` at `false` and inject the agent with the operator's `instrumentation.opentelemetry.io/inject-java` annotation on the pod instead. Use one of the two, not both.

## Keep the shared settings in Git

The shared settings can live in a file instead of the database. The `sharedConfiguration` block mounts the file read-only at `/app/shared-config` and turns on the reload, so a change to the file reaches the running server. Set one source:

- `content` renders a ConfigMap that the release owns. Put the JSON document in the values file, and the pods restart when the content changes, because the chart writes the checksum into the pod template.
- `existingConfigMap` names a ConfigMap that you manage, for example one that your GitOps tool or an external secret operator writes. The chart does not restart the pods. Kubernetes updates the mounted file after the kubelet sync, which can take a few minutes, and the server applies the change without a restart.

```yaml
sharedConfiguration:
  enabled: true
  existingConfigMap: ingot-shared-settings   # key: configuration.shared.json
```

The server answers `409` to a save from the dashboard or the API for a domain that the file holds, and it names the file. Change the file in Git instead. The chart adds `--shared-configuration` to `INGOT_OPTS` for you, and keeps the arguments of an `INGOT_OPTS` or `REPOSILITE_OPTS` value that `env` sets. Arguments that `envFrom` sets cannot be seen by the chart, so move them into `env`.

## Trust a private certificate authority

Set `extraCaCerts.enabled` to `true` and name one ConfigMap (`configMapName`) or one Secret (`secretName`) that holds the PEM files of your authority. The chart mounts it read-only at `/etc/ingot/extra-ca` and sets `INGOT_EXTRA_CA_CERTS` to that path. The image imports the certificates when the pod starts and keeps the public authorities trusted. How the image reads the directory, and which starts it refuses, is described in [Trust a private certificate authority](install-with-docker.md#trust-a-private-certificate-authority).

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

To use the workload identity of the pod instead of keys, see [Credentials from the workload identity](use-s3-storage.md#credentials-from-the-workload-identity). For how to deliver these Secrets through Git, see [Manage secrets with GitOps](manage-secrets-with-gitops.md).

## Route mirror traffic through an egress proxy

When the cluster reaches external repositories only through an egress proxy, set the proxy variables on the pod and turn on the switch. Mirror clients then use the proxy, and a mirror with its own `httpProxy` keeps that value:

```yaml
env:
  - name: INGOT_HTTP_USE_ENV_PROXY
    value: "true"
  - name: HTTPS_PROXY
    value: "http://egress-proxy.platform.svc.cluster.local:3128"
  - name: NO_PROXY
    value: "localhost,127.0.0.1,.svc,.cluster.local"
```

The switch is off by default, so an existing installation keeps its routes until you set it. Put no user name or password into `HTTPS_PROXY` or `HTTP_PROXY`, because Ingot refuses such a value. Keep the credentials of the proxy on the mirror with `httpProxy` instead. The variables are described in [Environment variables](../reference/environment-variables.md#mirror-environment-proxy).

A NetworkPolicy that limits egress has to allow the same route. Without it the pod cannot reach the proxy, and the mirror requests fail with the upstream error. Allow the pods of this release to reach the proxy and DNS, and nothing else that the mirrors need:

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: ingot-egress
spec:
  podSelector:
    matchLabels:
      app.kubernetes.io/name: ingot
  policyTypes:
    - Egress
  egress:
    - to:
        - namespaceSelector:
            matchLabels:
              kubernetes.io/metadata.name: kube-system
          podSelector:
            matchLabels:
              k8s-app: kube-dns
      ports:
        - protocol: UDP
          port: 53
        - protocol: TCP
          port: 53
    - to:
        - namespaceSelector:
            matchLabels:
              kubernetes.io/metadata.name: platform
          podSelector:
            matchLabels:
              app: egress-proxy
      ports:
        - protocol: TCP
          port: 3128
```

Replace the labels with the labels of your release and of your proxy. `kubectl get pods --show-labels` lists them. A host that `NO_PROXY` lists is reached directly, so it needs its own egress rule as well.

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
Two points of the high availability design are covered by the code now. Schema creation takes a lock that is shared by all pods, with the timeout `schemaLockTimeout`. A metadata write is locked across the pods that share one database, which the tests cover for MariaDB. Statistics are flushed at shutdown.

These limits still apply:

* The all-in-one probes use `/` unless `probes.readinessPath` is set.
* Shutdown does not wait for in-flight requests unless `deployment.shutdown` or `INGOT_SHUTDOWN_TIMEOUT` is set.

The design is in `docs/superpowers/specs/2026-08-08-kubernetes-ha-design.md` in the repository.
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

## Restrict network traffic

The chart renders no NetworkPolicy by default, so any pod in the cluster can reach the Ingot port, which serves the artifacts, the API and the metrics. To admit only the peers that need it, turn the policy on and list them:

```yaml
networkPolicy:
  enabled: true
  ingress:
    fromNamespaces:
      - kubernetes.io/metadata.name: gateway-system
      - kubernetes.io/metadata.name: monitoring
    fromPods: []
```

Each entry of `fromNamespaces` is a set of labels that selects a namespace. Each entry of `fromPods` is a set of pod labels that selects pods in the release namespace. An empty entry is refused, because it would select everything. List the namespace of the gateway that routes to Ingot, and the namespace of Prometheus if it scrapes `/metrics`.

The server accepts the Ingot port only from the listed peers. In split mode the chart renders a second policy for the dashboard, which accepts its port from the same list, and the server accepts the dashboard pods on its own port. Without any listed peer, the server admits only the dashboard pods in split mode, and nothing in allInOne mode.

Enforcement needs a CNI that enforces NetworkPolicy, such as Calico or Cilium. On a CNI without enforcement the policy is accepted and nothing is restricted. The kubelet sends the readiness and liveness probes from the node, and most enforcing CNIs admit that host traffic. If the readiness probe fails after you turn the policy on, check how your CNI treats traffic from the node before you change the allow-list.

Egress restriction is a separate switch and stays off:

```yaml
networkPolicy:
  egress:
    enabled: true
    allowInternet: true
    extra:
      - to:
          - podSelector:
              matchLabels:
                app.kubernetes.io/name: nats
        ports:
          - port: 4222
            protocol: TCP
```

With egress restricted, the server reaches DNS, the rules of `extra` and, when `allowInternet` is `true`, public addresses. The private ranges `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16` and `fc00::/7` stay excluded. Mirror proxying of public repositories needs `allowInternet`. Every service inside the cluster, such as the database, the cluster bus or an in-cluster S3 endpoint, needs its own rule in `extra`. A `podSelector` rule is usually the right form for those, because the policy sees the pod address behind the Service. The dashboard keeps its path to the server under the restriction.

Check the policy after the rollout:

```bash
kubectl -n ingot get networkpolicy
kubectl -n default run ingot-probe --rm -i --restart=Never --image=busybox:1.36 -- \
  wget -qO- -T 3 http://ingot.ingot.svc.cluster.local:8080/api/status/health
```

Replace `ingot` with your release name and namespace. The request from an unlisted namespace must time out. A request from a listed namespace must answer.

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

With a Gateway API implementation instead of an ingress, see [Terminate TLS at the gateway](terminate-tls-at-the-gateway.md). For the body size and the timeouts that the edge needs for large uploads, see [Configure the edge for large uploads](configure-the-edge-for-large-uploads.md).

For the sizing of the heap and the memory limit, see [Size the deployment](size-the-deployment.md). For a comparison of the JAR, the container image and the two Helm shapes, see [Deployment options compared](../explanation/deployment-options-compared.md).

## Related

* [Run with Docker](install-with-docker.md) describes the same image without an orchestrator.
* [Configure a database](configure-database.md) lists the database forms and their rules.
* [Configure the cluster bus](configure-cluster-bus.md) sets the optional message bus between replicas.
* [Use S3 storage](use-s3-storage.md) explains the S3 settings per repository.
* [Manage secrets with GitOps](manage-secrets-with-gitops.md) delivers the Secrets of this page from Git.
* [Terminate TLS at the gateway](terminate-tls-at-the-gateway.md) and [Configure the edge for large uploads](configure-the-edge-for-large-uploads.md) cover the cluster edge.
* [Size the deployment](size-the-deployment.md) describes how to choose the heap and the limits.
* [Create proxy repositories](create-proxy-repositories.md) for a proxy with a shared or large cache.
