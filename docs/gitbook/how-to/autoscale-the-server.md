---
description: Scale the Ingot server with the autoscaler that the Helm chart renders, set its bounds and metrics, tune the scale-down, and check the result. States the requirements that more than one replica needs first.
---

# Autoscale the server

The chart can render a HorizontalPodAutoscaler for the server. With `autoscaling.enabled: true`, the autoscaler sets the replica count, and the chart leaves `deployment.replicas` out of the Deployment. This page sets the bounds and the metrics, tunes the scale-down, and checks that the scaling works.

Read [Running more than one replica](run-on-kubernetes.md#running-more-than-one-replica) before you turn the autoscaler on. Every replica that the autoscaler adds must reach the same database and the same repository storage.

## Before you begin

* A release of the Ingot chart that runs on Kubernetes, see [Run on Kubernetes](run-on-kubernetes.md).
* The Kubernetes metrics server. The autoscaler reads CPU and memory from it.
* A shared database, such as MariaDB or PostgreSQL, set with `database.existingSecret`. The install notes warn when more than one replica runs without one.
* Storage that every replica can mount, either S3 for the repositories or a volume with an access mode that allows several nodes. See [Use S3 storage](use-s3-storage.md).

The cluster bus is optional. Without it, the replicas converge through the database on the next poll, and the install notes warn about the missing `INGOT_CLUSTER_URL`. See [Configure the cluster bus](configure-cluster-bus.md).

## Set the bounds and the metrics

Write the autoscaler block into your values file. This example keeps two replicas as the floor, and scales on CPU only:

```yaml
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

* `minReplicas` is the lowest count. A value of `0` is not rendered, so the autoscaler then uses its own default of `1`.
* `maxReplicas` is the highest count.
* A utilization target is a share of the CPU or memory request of the container. The chart sets a CPU request of `1.0` by default, so a 60% target means about 0.6 CPU per pod on average.

The default `metrics` list scales on CPU and on memory, both at 60%. Ingot caps the heap with `JAVA_OPTS`, which defaults to `-Xmx64M`. The memory of a pod therefore stays near that cap, and a memory target may never reach its value. The install notes warn about this when the memory metric is in the list. Prefer the CPU metric, as the example does.

Helm merges a map of your values into the chart defaults, but a list replaces the default list as a whole. Setting `metrics` therefore removes the memory metric, and the warning goes away with it. A list you set for `behavior.scaleDown.policies` replaces the default policy in the same way.

## Tune the scale-down

The default scale-down waits 300 seconds before it acts on a lower recommendation, and removes at most one pod every 60 seconds. This keeps the replicas alive while a burst of requests ends. To change the pace, set the behaviour in your values file:

```yaml
autoscaling:
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 600
      policies:
        - type: Pods
          value: 1
          periodSeconds: 120
```

A shorter window removes idle replicas sooner. Every scale-down stops a pod, so set the shutdown budget of [Running more than one replica](run-on-kubernetes.md#shutdown-budget) to let the requests in flight finish.

## Apply and check the result

Apply the values with the same chart version as before:

```bash
helm upgrade --install ingot oci://ghcr.io/onelitefeathernet/charts/ingot \
  --version 1.1.1 \
  --namespace ingot \
  -f ingot-values.yaml
```

Then check the autoscaler and the Deployment:

```bash
kubectl -n ingot get hpa ingot
kubectl -n ingot describe hpa ingot
kubectl -n ingot get deployment ingot
```

The `TARGETS` column shows the current utilization of each metric. A value of `<unknown>` means that the metrics server does not report the pods, or that the pods have no resource request. The `REPLICAS` column of the Deployment shows the count that the autoscaler set.

To check that the replicas serve traffic, look at the readiness of the pods:

```bash
kubectl -n ingot get pods -l app.kubernetes.io/name=ingot
```

Every pod should show `1/1` in the `READY` column. A pod that stays at `0/1` has failed its readiness check, and the autoscaler counts it as not ready.

## Limits

* The autoscaler stays between `minReplicas` and `maxReplicas`. A pod that the cluster cannot schedule stays `Pending`, and the count stays below the target. Check for `Pending` pods when the count stays low. The topology spread constraints of [Running more than one replica](run-on-kubernetes.md#running-more-than-one-replica) can keep a pod pending on a small cluster.
* The chart renders the autoscaler only in the server workload. In `split` mode, the dashboard keeps `dashboard.replicas`.
* The repository's chart tests check the install notes that depend on the autoscaler. They do not render the HorizontalPodAutoscaler object, and they do not scale a cluster.

## Related

* [Set a disruption budget](set-a-disruption-budget.md) limits how many pods a node drain can evict at once.
* [Set Helm values for high availability](set-helm-values-for-high-availability.md) lists the other values that a multi-replica release needs.
* [Size the deployment](size-the-deployment.md) describes how to choose the heap and the memory limit.
* [Helm chart values](../reference/helm-values.md#scaling-and-availability) lists every key of the autoscaler.
