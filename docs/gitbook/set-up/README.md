---
description: Set up Ingot. Lists the pages that deploy Ingot with Docker, the JAR or Kubernetes, put it behind a proxy or TLS, run it on a shared database and storage, and move to it from Reposilite, Nexus or another manager.
---

# Set up Ingot

This section covers getting Ingot running outside a tutorial: the install paths, the network setup, a cluster of replicas, and the move from another repository manager. A server runs with the deploy target `all` or `server`, and the Helm chart has the modes `allInOne` and `split`. [Deploy targets](../references/configuration/deploy-targets.md) and [Deployment options compared](deployment-options-compared.md) explain the choice.

## Plan

Choose a shape and size it.

* [Deployment options compared](deployment-options-compared.md)
* [Size the deployment](size-the-deployment.md)

## Deploy

Install paths for a host.

* [Install with Docker](deploy/install-with-docker.md)
* [Run the JAR](deploy/install-jar.md)
* [Run as a service](deploy/run-as-service.md)
* [Run the dashboard in a separate container](deploy/run-split-dashboard-container.md)

## Kubernetes

The Helm chart and the Kubernetes pages.

* [Run on Kubernetes](kubernetes/run-on-kubernetes.md)
* [Run the dashboard split from the server with Helm](kubernetes/run-split-with-helm.md)
* [Set Helm values for high availability](kubernetes/set-helm-values-for-high-availability.md)
* [Autoscale the server](kubernetes/autoscale-the-server.md)
* [Set a disruption budget](kubernetes/set-a-disruption-budget.md)
* [Route with a Gateway API HTTPRoute](kubernetes/route-with-gateway-api.md)
* [Manage secrets with GitOps](kubernetes/manage-secrets-with-gitops.md)
* [Terminate TLS at the gateway](kubernetes/terminate-tls-at-the-gateway.md)

## Network

Reverse proxy, TLS and traffic restrictions.

* [Run behind a reverse proxy](network/run-behind-reverse-proxy.md)
* [Configure the edge for large uploads](network/configure-the-edge-for-large-uploads.md)
* [Enable TLS](network/enable-tls.md)
* [Restrict network traffic with a NetworkPolicy](network/restrict-network-traffic.md)

## Cluster

The shared database, the object storage and the cluster bus that several replicas need.

* [Configure a database](cluster/configure-database.md)
* [Configure the cluster bus](cluster/configure-cluster-bus.md)
* [Use S3 storage](cluster/use-s3-storage.md)

## Migrate

Moving from Reposilite, Nexus and other repository managers.

* [Move from Reposilite to Ingot](migrate/move-from-reposilite.md)
* [Upgrade from Reposilite 3.x](migrate/upgrade-from-reposilite.md)
* [Migrate from the upstream Helm chart](migrate/migrate-from-upstream-chart.md)
* [Import from other repository managers](migrate/import-from-other-repository-managers.md)
* [Import from Nexus](migrate/import-from-nexus.md)
* [Use the Nexus import wizard](migrate/use-the-nexus-import-wizard.md)
