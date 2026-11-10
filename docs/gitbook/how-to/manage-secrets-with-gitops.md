---
description: Keep Ingot secrets out of Git when you deploy with GitOps, using SOPS, External Secrets Operator or Sealed Secrets to create the Secret that the chart reads through env and envFrom, and know which settings live in the repository settings instead.
---

# Manage secrets with GitOps

A GitOps repository holds the Helm values of Ingot, and the values must not hold a password. The chart reads secret values from a Kubernetes Secret that you create outside the values file. Three tools deliver such a Secret through Git without the plain value in it: SOPS, External Secrets Operator and Sealed Secrets. Pick one, and the chart reads the result the same way.

## Which values come from the environment

Ingot reads these values from its environment, so they can come from a Secret. The chart renders most of them from a Secret reference.

| Value | Where it is set | Chart value that reads a Secret |
|:------|:----------------|:--------------------------------|
| Database connection string, `INGOT_LOCAL_DATABASE` | Environment, or the local key `database` | `database.existingSecret` with `database.secretKey` |
| Key password of the TLS connector, `INGOT_LOCAL_KEYPASSWORD` | Environment | `envFrom` or `env` |
| S3 credentials for the AWS SDK, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` and the optional `AWS_REGION` | Environment of the pod | `objectStorage.existingSecret` |
| Password of the cluster bus, `INGOT_CLUSTER_PASSWORD` | Environment | `clusterBus.passwordSecret` |
| NATS credentials file, `INGOT_CLUSTER_CREDENTIALS` | Path to a mounted file | `clusterBus.credentialsSecret` |
| Metrics basic auth, `INGOT_PROMETHEUS_USER` and `INGOT_PROMETHEUS_PASSWORD` | Environment, with the same values as the Secret of the ServiceMonitor | `serviceMonitor.basicAuth.secretName`, plus `env` or `envFrom` on the server |

The metrics plugin is not part of the image, so the two metrics variables only matter once you have installed the plugin. Their older `REPOSILITE_PROMETHEUS_` spellings still work.

Any other value that Ingot reads from the environment can come from `envFrom` as well. The [environment variables](../reference/environment-variables.md) reference lists them.

{% hint style="warning" %}
Do not put these values in `env` as literal `value` entries, and do not put them in the values file. A value in the values file ends up in the Git history and in the rendered manifests.
{% endhint %}

## Repository settings are not environment variables

The S3 keys of a repository, `accessKey` and `secretKey`, are repository settings. You set them through the settings API or in the dashboard, and Ingot stores them in its database. They are not read from the environment, so no Secret in the values file can supply them.

Leave them empty in the settings if the pod gets its credentials from the environment or from the workload identity instead. See [Use S3 storage](use-s3-storage.md#credentials-from-the-workload-identity).

## Option 1: SOPS

SOPS encrypts the values of a Secret manifest, so the file can be committed. Encrypt the Secret with the key of your team, and let the GitOps tool decrypt it at apply time. Your tool needs its own SOPS setup for that.

```bash
kubectl -n ingot create secret generic ingot-database \
  --from-literal=database='mariadb ingot-db.databases.svc.cluster.local:3306 ingot ingot-user the-password' \
  --dry-run=client -o yaml > ingot-database.yaml

sops --encrypt --in-place ingot-database.yaml
```

Commit `ingot-database.yaml` in its encrypted form. The values file then points at the Secret:

```yaml
database:
  existingSecret: ingot-database
  secretKey: database
```

## Option 2: External Secrets Operator

External Secrets Operator creates the Secret from a store, such as a cloud secret manager or Vault. Git holds only a reference to the secret in the store. The `ExternalSecret` below creates the Secret `ingot-s3-keys` with the keys that the chart reads:

```yaml
apiVersion: external-secrets.io/v1
kind: ExternalSecret
metadata:
  name: ingot-s3-keys
  namespace: ingot
spec:
  secretStoreRef:
    name: <your-secret-store>
    kind: ClusterSecretStore
  target:
    name: ingot-s3-keys
  data:
    - secretKey: AWS_ACCESS_KEY_ID
      remoteRef:
        key: ingot/s3
        property: access-key-id
    - secretKey: AWS_SECRET_ACCESS_KEY
      remoteRef:
        key: ingot/s3
        property: secret-access-key
```

The chart reads the Secret through `objectStorage.existingSecret`:

```yaml
objectStorage:
  existingSecret: ingot-s3-keys
```

For other values that Ingot reads from the environment, let the operator create one Secret with `spec.dataFrom`, which copies every key of a store entry into the Secret. The values file then reads all of its keys with `envFrom`:

```yaml
apiVersion: external-secrets.io/v1
kind: ExternalSecret
metadata:
  name: ingot-env
  namespace: ingot
spec:
  secretStoreRef:
    name: <your-secret-store>
    kind: ClusterSecretStore
  target:
    name: ingot-env
  dataFrom:
    - extract:
        key: ingot/env
```

```yaml
envFrom:
  - secretRef:
      name: ingot-env
```

## Option 3: Sealed Secrets

Sealed Secrets encrypts a Secret with the public key of the controller in the cluster. Only that controller can decrypt the result, so the sealed file can be committed.

```bash
kubectl -n ingot create secret generic ingot-cluster-bus \
  --from-literal=password='<bus-password>' \
  --dry-run=client -o yaml \
  | kubeseal --format yaml > ingot-cluster-bus-sealed.yaml
```

The controller creates the Secret `ingot-cluster-bus` from the sealed file. The values file names it:

```yaml
clusterBus:
  url: tls://nats.nats.svc.cluster.local:4222
  passwordSecret: ingot-cluster-bus
```

## Check the result

After the sync, check that the Secret exists and that the Deployment reads it, without printing the values:

```bash
kubectl -n ingot get secret ingot-database ingot-s3-keys
kubectl -n ingot get deployment ingot -o jsonpath='{.spec.template.spec.containers[0].env[*].name}'
```

The first command lists the Secrets. The second lists the names of the variables, which must include `INGOT_LOCAL_DATABASE` with a `valueFrom` reference and no `value`.

## Related

* [Run on Kubernetes](run-on-kubernetes.md) sets the database Secret and the S3 Secret.
* [Use S3 storage](use-s3-storage.md) explains the repository keys.
* [Environment variables](../reference/environment-variables.md) lists every variable that Ingot reads.
