---
description: Compare the four ways to run Ingot, the JAR, the container image, the Helm all-in-one chart and the Helm split chart, by how each one is installed, upgraded and rolled back, with a link to the how-to for each.
---

# Deployment options compared

Ingot runs in four shapes. They serve the same API, the same repositories and the same data. They differ in who starts the process, how a new version reaches it and how a bad version is taken back. This page compares those three steps. It does not compare speed or capacity, which depend on the workload and are not measured here.

| Shape | Install | Upgrade | Roll back |
|:------|:--------|:--------|:----------|
| JAR | Copy the JAR into a directory and start it with `java -jar` | Stop the process, replace the JAR, start it again on the same directory | Put the previous JAR back and start it on the same directory |
| Container image | Start the image with `docker run` or Compose | Change the image tag, then start the container again | Change the image back to the previous tag and start again |
| Helm all-in-one chart | `helm upgrade --install` with one `--version` and one values file | The same command with a newer `--version` | `helm rollback` to the revision before the upgrade |
| Helm split chart | The same command, with the dashboard workload in the values file | The same command with a newer `--version` | `helm rollback` to a revision before the split |

In every shape the data stays where the instance keeps it, the working directory or the data volume. The rollback steps therefore depend on the data as much as on the binary, and they are the same for all four shapes. Read [Back up before upgrading](../how-to/back-up-before-upgrading.md) and [Roll back an upgrade](../how-to/roll-back-an-upgrade.md) before any upgrade.

## JAR

The JAR is the most direct shape. Ingot writes its configuration, database and repositories into the directory it starts in, unless you pass `--working-directory`. Upgrading means replacing one file, and rolling back means putting the old file back on the same directory.

* Install: [Run the JAR](../how-to/install-jar.md).
* Upgrade: [Upgrade from Ingot 1.1.x](../how-to/upgrade-ingot.md), or [Upgrade from Reposilite 3.x](../how-to/upgrade-from-reposilite.md) for an instance that is still on Reposilite.
* Roll back: [Roll back an upgrade](../how-to/roll-back-an-upgrade.md).

## Container image

The container image is the same server in a container. The data lives in the volume that you mount at `/app/data`. An upgrade is a change of the image tag, and a rollback is the same change in reverse. The data is kept across both, as long as the volume stays. The entrypoint may change the owner of the volume at start, as described in the [environment variables](../reference/environment-variables.md) reference.

* Install: [Install with Docker](../how-to/install-with-docker.md).
* Upgrade: [Upgrade from Ingot 1.1.x](../how-to/upgrade-ingot.md) or [Upgrade from Reposilite 3.x](../how-to/upgrade-from-reposilite.md), depending on the version you run.
* Roll back: [Roll back an upgrade](../how-to/roll-back-an-upgrade.md). It lists what the older version cannot read after an upgrade.

## Helm all-in-one chart

The chart runs one workload of the all-in-one image. It serves the dashboard, the API and the repositories, and it creates the Deployment, the Service, the probes, the persistent volume claim and an optional ingress or route. The values file keeps the desired state, so an upgrade is a Helm release with a new chart version, and Helm keeps the earlier revisions for a rollback.

* Install: [Run on Kubernetes](../how-to/run-on-kubernetes.md).
* Upgrade: the same `helm upgrade --install` command with the newer `--version`. The chart version is the release version.
* Roll back: `helm rollback ingot <revision>`. The data volume is kept, so the rollback has the same data limits as the image rollback.

## Helm split chart

The split chart runs the server and the dashboard as two workloads, with the same chart. The server runs with `INGOT_TARGET=server`, and the dashboard image serves the dashboard in front of it. The split does not change the data: it changes which process serves which path.

* Install: [Run the dashboard split from the server with Helm](../how-to/run-split-with-helm.md).
* Upgrade: the same command as the all-in-one chart. Moving an all-in-one release to split is an upgrade in place, and the server pods restart once. The how-to describes this step.
* Roll back: `helm rollback` to a revision from before the split. Helm returns the release to that revision, so the dashboard workload is removed, and the server pods restart once more.

## Choosing a shape

* Run the JAR or the container image on a single host, when no cluster is in use.
* Run the all-in-one chart when Ingot is one workload on a cluster, and the dashboard and the API can share one process.
* Run the split chart when the dashboard should scale or be replaced apart from the server. Keep the server at one replica unless the requirements of [Run on Kubernetes](../how-to/run-on-kubernetes.md#running-more-than-one-replica) hold.

## Related

* [Deploy targets](../reference/deploy-targets.md) describes the `INGOT_TARGET` setting that the split chart uses for the server.
* [Multi-replica operation](multi-replica-operation.md) describes the limits of running several replicas.
