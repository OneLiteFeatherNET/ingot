---
description: Turn on the PodDisruptionBudget of the Ingot Helm chart so that a node drain evicts one server pod at a time, check the budget, and learn what it does and does not protect during a rollout.
---

# Set a disruption budget

A PodDisruptionBudget limits how many pods of a workload a voluntary disruption can remove at the same time. A node drain for maintenance is such a disruption. The Ingot chart renders the budget when you ask for it, and it is off by default.

## When to use it

Use the budget when the server runs on more than one replica, and the cluster is drained for upgrades. A drain evicts every pod on the node it empties. With a budget, the cluster refuses an eviction that would take more than one server pod down at once, so the drain waits until the remaining replicas are ready again before it goes on.

With one replica the budget still renders, but it allows the one pod to be evicted. A drain then stops the only server pod. Run two or more replicas if the instance must stay up during a drain. See [Running more than one replica](run-on-kubernetes.md#running-more-than-one-replica).

## Turn it on

Add the switch to your values file:

```yaml
podDisruptionBudget:
  enabled: true
```

The chart renders a PodDisruptionBudget named after the release, with `maxUnavailable: 1` and a selector for the server pods. In `split` mode it renders a second budget for the dashboard pods.

The budget uses `maxUnavailable: 1` and not `minAvailable: 1`. With one replica, `minAvailable: 1` blocks every node drain, because no pod may leave. `maxUnavailable: 1` allows the drain to go on. The chart does not expose the other form.

Apply the change with the same chart version as before:

```bash
helm upgrade --install ingot oci://ghcr.io/onelitefeathernet/charts/ingot \
  --version 1.1.1 \
  --namespace ingot \
  -f ingot-values.yaml
```

## Check the budget

```bash
kubectl -n ingot get pdb
kubectl -n ingot describe pdb ingot
```

The `ALLOWED DISRUPTIONS` column shows how many pods the cluster may evict now. With two healthy replicas it shows `1`. If it shows `0`, a pod is not ready, and the next eviction waits for it.

Run `kubectl drain` on a node that runs a replica, and watch the eviction of the server pod. The replacement pod must become ready before the next pod of the same budget goes.

## What the budget does not cover

* A rolling update is not a voluntary disruption that the budget governs. The rollout follows `rollingUpdate` in the chart values. The default `maxUnavailable: 0` and `maxSurge: 1` keep every replica serving while the new pods start.
* A crash, an out-of-memory kill or a failed node is not a voluntary disruption. The budget does not prevent them.
* The budget does not spread the pods over nodes or zones. Use `deployment.topologySpreadConstraints` for that, see [Running more than one replica](run-on-kubernetes.md#running-more-than-one-replica).

## Related

* [Autoscale the server](autoscale-the-server.md) changes the replica count while the budget stays in place.
* [Set Helm values for high availability](set-helm-values-for-high-availability.md) lists the values that go with the budget.
* [Helm chart values](../reference/helm-values.md#scaling-and-availability) lists the keys of the budget.
