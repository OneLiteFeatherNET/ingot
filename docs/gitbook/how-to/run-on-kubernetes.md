---
description: Run Ingot on Kubernetes with the upstream Reposilite Helm chart and the Ingot image, with a database Secret, S3 storage, health checks, persistence, autoscaling and ingress, and the limits of running more than one replica.
---

# Run on Kubernetes

This page installs Ingot with the upstream Reposilite Helm chart and the Ingot image. Ingot does not ship its own chart yet. The chart sets the Deployment, Service, probes, persistence, autoscaling and ingress. You set the image to Ingot and pass the Ingot settings through environment variables.

{% hint style="warning" %}
Several replicas are not fully supported yet. Read [Running more than one replica](#running-more-than-one-replica) before you set `replicas` above `1` or enable autoscaling.
{% endhint %}

## Before you begin

* A Kubernetes cluster and `kubectl` with access to a namespace for Ingot.
* Helm 3.
* A database for anything beyond one replica. MariaDB or PostgreSQL. The default SQLite file works for one replica only.
* An S3 bucket for repositories, if you run more than one replica. Filesystem storage needs a volume that several pods can mount.
* An ingress controller, if you want a host name. Autoscaling needs the Kubernetes metrics server.

The steps below were checked against chart `reposilite` version `1.4.2`. The chart's `appVersion` is the upstream Reposilite version. Ingot does not use it, because the image tag is set explicitly in the values.

## Add the chart repository

```bash
helm repo add reposilite https://helm.reposilite.com/
helm repo update
helm show values reposilite/reposilite --version 1.4.2 > ingot-chart-values.yaml
```

The file lists every value of the chart with its default. Use it as a reference. Your own values file only needs the keys that change.

## Create the database Secret

Ingot reads its database from the local configuration key `database`. The environment variable `INGOT_LOCAL_DATABASE` overrides it. Store the value in a Secret so that the password stays out of the chart values.

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

Create `ingot-values.yaml`. The tag pins the release and the digest together. Replace the digest with the digest of the release you deploy.

```yaml
image:
  name: ghcr.io/onelitefeathernet/ingot
  # The chart renders name:tag, so the digest goes into the tag field.
  tag: "1.2.0@sha256:<digest>"

deployment:
  replicas: 1

env:
  - name: JAVA_OPTS
    value: "-Xmx768M"
  - name: INGOT_LOCAL_DATABASE
    valueFrom:
      secretKeyRef:
        name: ingot-database
        key: database

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
helm upgrade --install ingot reposilite/reposilite \
  --version 1.4.2 \
  --namespace ingot \
  -f ingot-values.yaml
```

The Service is named `ingot-reposilite`, because the release name `ingot` does not contain the chart name.

## Check the rollout and the health

Wait for the Deployment, then ask the instance for its health through a port forward:

```bash
kubectl -n ingot rollout status deployment/ingot-reposilite
kubectl -n ingot port-forward svc/ingot-reposilite 8080:8080
```

In a second shell:

```bash
curl -fsS http://localhost:8080/api/status/health
```

The answer is `{"status":"UP"}` with status `200`. A failing instance answers `503` with `{"status":"DOWN"}`.

## Health checks

The chart runs a readiness probe and a liveness probe. Both send `GET /` to port `8080`. The chart fixes the path, so you cannot point the probes at `/api/status/health`.

{% hint style="warning" %}
The probes check whether `/` answers. Ingot has no separate readiness endpoint. If you turn off the dashboard with `INGOT_LOCAL_DEFAULTFRONTEND=false`, check that `/` still answers with a success status before you rely on the probes. This behaviour was not verified for this page.
{% endhint %}

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

## Running more than one replica

Several replicas need all of the following:

* A database shared by all pods. SQLite is a file inside one pod and cannot be shared. The default `sqlite reposilite.db` is for one replica only.
* S3 storage for every repository, or a `ReadWriteMany` volume for the filesystem repositories.
* A `ReadWriteMany` volume, or `persistence.enabled: false`, so that pods on other nodes can start.

{% hint style="warning" %}
As of this writing, the code does not yet do the following, which the design for Kubernetes high availability calls for:

* Schema creation runs in every pod at startup, with no lock across pods.
* A metadata write, such as a deployment that updates `maven-metadata.xml`, is not locked across pods. Two pods that deploy the same artifact at the same time can lose one version from the metadata.
* There is no readiness endpoint, and shutdown does not wait for in-flight requests. Statistics are not flushed at shutdown.

Until these are fixed, run one replica. Several replicas are only safe if you accept these risks. The design is in `docs/superpowers/specs/2026-08-08-kubernetes-ha-design.md` in the repository.
{% endhint %}

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
* [Use S3 storage](use-s3-storage.md) explains the S3 settings per repository.
* [Create proxy repositories](create-proxy-repositories.md) for a proxy with a shared or large cache.
