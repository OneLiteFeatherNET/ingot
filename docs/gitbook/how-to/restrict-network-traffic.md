---
description: Turn on the NetworkPolicy of the Ingot Helm chart so that only the gateway, the monitoring stack and the dashboard reach the Ingot port, restrict the egress when needed, and check the policy and its failure modes.
---

# Restrict network traffic with a NetworkPolicy

The chart renders no NetworkPolicy by default. Without one, every pod in the cluster can reach the Ingot port, which serves the artifacts, the API and the metrics. This page turns on the chart's policy, lists the peers that need the port, and checks that the policy works. The basic switch and the table of peers are also in [Run on Kubernetes](run-on-kubernetes.md#restrict-network-traffic). This page covers the steps in more detail.

## Before you begin

* A CNI that enforces NetworkPolicy, such as Calico or Cilium. On a CNI without enforcement the policy is accepted, and nothing is restricted.
* The labels of the namespaces and pods that call Ingot. Read them with `kubectl get namespace <name> --show-labels` and `kubectl -n <namespace> get pods --show-labels`.

## Decide the peers

Each entry of `networkPolicy.ingress.fromNamespaces` selects a namespace by its labels. Each entry of `fromPods` selects pods of the release namespace. The chart allows these peers on the Ingot port only:

| Caller | Entry | Example |
|---|---|---|
| The gateway or the ingress controller | `fromNamespaces` | `kubernetes.io/metadata.name: gateway-system` |
| Prometheus, when it scrapes `/metrics` | `fromNamespaces` or `fromPods` | `kubernetes.io/metadata.name: monitoring` |
| Other workloads in the release namespace | `fromPods` | `app.kubernetes.io/name: my-tool` |

An entry with no labels is refused, because it would select every namespace or every pod.

## Turn on the policy

Write the peers into your values file:

```yaml
networkPolicy:
  enabled: true
  ingress:
    fromNamespaces:
      - kubernetes.io/metadata.name: gateway-system
      - kubernetes.io/metadata.name: monitoring
    fromPods: []
```

Apply the change with the same chart version as before. The chart renders one NetworkPolicy named after the release. It selects the server pods, and it allows the listed peers on the port of `service.port`.

In `split` mode, the chart renders a second policy for the dashboard pods. It admits the same peers on `dashboard.service.port`. The server policy also admits the dashboard pods, because the dashboard calls the server.

Without any listed peer, the server policy has no ingress rule. In `allInOne` mode, nothing can reach the server. In `split` mode, only the dashboard pods can reach it.

## Restrict the egress when needed

Egress control is a separate switch, and it is off by default. When you turn it on, the server may reach DNS, the public internet and the rules that you add:

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

* `allowInternet: true` allows public addresses on every port. The private ranges `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16` and `fc00::/7` stay excluded. Mirror proxying of public repositories needs this.
* Every service inside the cluster, and every database or S3 endpoint on a private address, needs its own rule in `extra`. The example allows the NATS pods of the release namespace on port 4222.
* The `extra` rules apply to the server policy only. In `split` mode, the dashboard keeps DNS and its path to the server.

With `allowInternet: false`, the server reaches DNS and the `extra` rules only. A proxy repository that fetches from a public host then fails.

## Check the policy

List the policies, then check that a listed caller reaches Ingot and an unlisted one does not:

```bash
kubectl -n ingot get networkpolicy
```

From a namespace that is not listed, the request must time out. From a listed namespace, it must answer `{"status":"UP"}`. The probe command of [Run on Kubernetes](run-on-kubernetes.md#restrict-network-traffic) shows one way to send that request from a test pod.

Also check the readiness of the pods after you turn the policy on:

```bash
kubectl -n ingot get pods -l app.kubernetes.io/name=ingot
```

## Troubleshoot

* **Pods stay at `0/1` after the change.** The kubelet sends the probes from the node. Some CNIs drop that traffic when a policy selects the pod. Check how your CNI treats traffic from the node, and add the rule that it needs.
* **Prometheus shows the target as down.** Its namespace or its pods are not in `fromNamespaces` or `fromPods`. The ServiceMonitor scrape needs the same rule as any other caller.
* **Artifacts from a proxy repository fail.** The egress is restricted and `allowInternet` is `false`, or the upstream host is on a private address. Add a rule to `extra`, or allow the address range.
* **The database does not answer.** The database is on a private address or in another namespace, and no rule in `extra` allows it. A `namespaceSelector` together with a `podSelector` selects a pod in another namespace.
* **A policy has no effect.** The CNI does not enforce NetworkPolicy, or the namespace has another policy that allows the traffic.

## Related

* [Run on Kubernetes](run-on-kubernetes.md) installs the chart and lists the basic switch.
* [Route with a Gateway API HTTPRoute](route-with-gateway-api.md) and [Configure the edge for large uploads](configure-the-edge-for-large-uploads.md) describe the path from the gateway to Ingot.
* [Monitor with the Prometheus Operator](monitor-with-the-prometheus-operator.md) describes the scrape that the policy must admit.
* [Helm chart values](../reference/helm-values.md#routing-and-network) lists every key of the policy.
