---
description: Gives an overview of the parts of an Ingot installation, which are the server, the dashboard, the database, the storage, the search backends, the cluster bus and the plugins. It follows one request through them and describes the three deployment shapes.
---

# Architecture overview

This page shows the parts of an Ingot installation and how they fit together. It is a map. Each part has a page that goes into detail, and this page links to them.

## Components

| Component | What it does | Read more |
|:--|:--|:--|
| Server | One JVM process. It serves the Maven protocol, the REST API, the login endpoints and, by default, the dashboard. | [Backend architecture](backend-architecture.md) |
| Dashboard | The web interface for browsing, settings, accounts and the audit log. It is served by the server, or by its own container. | [Run the dashboard in a separate container](../how-to/run-split-dashboard-container.md) |
| Database | Holds users, tokens, sessions, shared settings, the audit log, job claims and the search index. SQLite is the default. MariaDB and PostgreSQL are the supported choices for a server database. | [Configure a database](../how-to/configure-database.md) |
| Storage | Holds the artifact files of each repository, on a directory of the file system or in an S3-compatible bucket. | [Storage providers](../reference/storage-providers.md) |
| Search backends | Answer artifact search. The built-in backend keeps the index in the database. A plugin can register another backend, such as OpenSearch. | [Choose a search backend](../how-to/choose-a-search-backend.md) |
| Cluster bus | An optional message channel between replicas, on NATS or Redis. It speeds up the spread of revocations and cache invalidations. The database stays the source of truth. | [Configure the cluster bus](../how-to/configure-cluster-bus.md) |
| Plugins | Jars loaded from a directory at start. They register routes, facades, events, search backends and dashboard extensions. | [Plugin API](../reference/plugin-api.md) |

The server runs as a set of plugins on one runtime, and the bundled features, such as checksums, Prometheus metrics and the Nexus importer, are plugins as well.

## How a request moves through the server

1. A client sends a request: a build tool fetching or deploying an artifact, a browser loading the dashboard, or a script calling the API.
2. The server authenticates the caller. A build tool uses a token with Basic or Bearer authentication, and a browser uses a session from the login. See [Sessions, login and CSRF](sessions-and-login.md).
3. The server checks what the caller may do. The permission checks run on the identity data in the database. See [How permissions are evaluated](permission-evaluation.md).
4. The repository decides where the file comes from. A hosted repository reads its own storage, a proxy repository fetches and may keep a copy, and a group repository asks the repositories it lists. See [Storage and mirror caching](storage-and-mirror-caching.md).
5. For a deployment, the server writes the file to storage and updates the Maven metadata. It then queues a change for the search index, which a backend applies. See [How the search index stays current](how-the-search-index-stays-current.md).
6. The server records administrative and artifact events in the audit log, and answers the caller.

## Deployment shapes

### Single instance

One server process, with SQLite or a database server, and file system or S3 storage. This is the default, and it is what the [Docker tutorial](../tutorials/first-steps-with-docker.md) starts. It is the simplest shape and the one to start with.

### Split dashboard

The dashboard runs in its own container and passes every other request to the server, which runs without its dashboard. Use it when the dashboard should restart, scale or be hardened apart from the artifact store. The data and the API do not change. See [Run the dashboard in a separate container](../how-to/run-split-dashboard-container.md) and [Deployment options compared](deployment-options-compared.md).

### Multi-replica

Several server replicas share one database server and one S3 storage, behind one address. The database carries the shared state, and replicas coordinate scheduled work through claims and leases. The optional cluster bus shortens the delay for revocations. See [Multi-replica operation](multi-replica-operation.md) for the mechanics and [High availability support](high-availability-support.md) for what is supported and tested. A shared SQLite file does not suit several replicas.

## Related pages

* [What is Ingot](what-is-ingot.md)
* [How Ingot compares](how-ingot-compares.md)
* [Run on Kubernetes](../how-to/run-on-kubernetes.md)
