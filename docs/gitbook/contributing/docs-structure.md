---
description: Plan for restructuring the Ingot docs after the Grafana Mimir documentation. Lists the new sections, the mapping from every old page path to its new path, the redirects, the diagrams, the image rules and the pull request sequence.
---

# Docs structure plan

This page is the plan for restructuring the Ingot documentation. It is a working document for contributors and is not part of the published table of contents. When the last step is merged, the page is deleted or reduced to the rules in [Contributing to the docs](../explanation/contributing-to-the-docs.md).

## Why

Ingot adopted the deploy shapes of Grafana Mimir and Loki. A server runs with the deploy target `all` or `server`, the Helm chart has the modes `allInOne` and `split`, and a cluster shares a database, an object storage and the cluster bus. Readers who know Mimir expect its map of the docs. The current layout follows the four Diataxis folders (`tutorials`, `how-to`, `reference`, `explanation`). That layout is right for authors and wrong for readers, who come with a goal ("I want to deploy", "I want to monitor") and not with a page type.

The restructure changes where pages live and adds overview pages and diagrams. It does not rewrite page content. The Diataxis rule for a single page stays: each page is one of tutorial, how-to, reference or explanation, and the type is visible in the title and the first paragraph.

## What makes the Mimir docs work

The Mimir documentation at `https://grafana.com/docs/mimir/latest/` has these properties, and the plan copies them.

* **Goal first sections.** The top level is Introduction, Get started, Set up and configure, Send metric data, Manage, Query metric labels and Visualize data. Each section is a stage of a reader's job, and each holds pages of several Diataxis types.
* **An overview page per section.** Every section opens with a short page that says what the section is for, and lists its pages with one sentence each. A reader never lands in a bare list of titles.
* **Deployment modes as an explanation page.** One page, under References, compares the modes (monolithic with `-target=all`, microservices) in prose, says how the mode is chosen and links to the how-to for each. The Set up section and Get started point to it instead of repeating it.
* **Architecture under References.** The architecture pages (components, hash ring, the read and write paths) are reference material with diagrams. They are linked from the how-to pages that depend on them.
* **A diagram per concept.** A diagram sits next to the text it explains, is introduced by one sentence, and is not the only carrier of a fact.
* **Get started is one linear page.** It names the prerequisites, runs one single process, connects a client and ends with "verify success". It states which mode it deploys and links to the modes page.
* **Short, stable References.** Architecture, HTTP API, configuration parameters, glossary and learning resources are the only children of References. Release notes are a top-level section.

## The Ingot sections

| Section | Folder | Question it answers | Content |
|:--|:--|:--|:--|
| Introduction | `introduction/` | What is Ingot and how does it relate to Reposilite? | What Ingot is, how it compares, drop-in compatibility. |
| Get started | `get-started/` | How do I get a working instance in half an hour? | The two tutorials and the first administrator. |
| Set up | `set-up/` | How do I deploy it, and how do I move to it? | `deploy/` (Docker, JAR, Kubernetes, Helm modes), `network/` (proxy, TLS), `cluster/` (database, object storage, cluster bus), `migrate/` (from Reposilite, Nexus and other managers). |
| Configure | `configure/` | How do I tune it after it runs? | Settings secrets, branding, frontend, limits, and `authentication/`, `repositories/` and `search/`. |
| Publish and consume | `publish-and-consume/` | How do my build tools and CI use it? | The Send and Query equivalent of Mimir: build tool recipes, tokens, checksums and search. |
| Manage | `manage/` | How do I run it in production? | `operate/`, `upgrade/`, `monitor/`, `access/` and `plugins/`. |
| References | `references/` | What exactly does it do? | `architecture/` (including the new deployment modes page), `configuration/`, `http-api/`, `identity/`, `dashboard/`, `plugins/`, the console commands, the glossary and the FAQ. |
| Release notes | `release-notes/` | What changed? | Release notes and the upgrade changes. |

`contributing/` holds the pages for authors and is outside the reader sections.

Each section folder has a `README.md` that is the overview page. GitBook serves `folder/README.md` at the URL `folder`, so a section has a stable address.

### New pages

These pages do not exist yet. Each is written from pages that exist and from code on the main branch.

| Page | Type | Built from |
|:--|:--|:--|
| `introduction/README.md` | Overview | `README.md`, `what-is-ingot.md` |
| `get-started/README.md` | Overview | The two tutorials |
| `set-up/README.md` and `set-up/migrate/README.md` | Overview | The pages of the section |
| `configure/README.md` | Overview | The pages of the section |
| `publish-and-consume/README.md` | Overview | The pages of the section |
| `manage/README.md` | Overview | The pages of the section |
| `references/README.md` | Overview | The pages of the section |
| `references/architecture/deployment-modes.md` | Explanation | `deploy-targets.md`, `deployment-options-compared.md`, the `mode` key in `helm-values.md` |

The deployment modes page covers what exists on the main branch: the targets `all` and `server`, and the Helm modes `allInOne` and `split`. The `worker` target and a `scalable` Helm mode are not on the main branch and are not documented until they are.

## Navigation problem to fix first

26 pages exist in `docs/gitbook/` but are not linked from `SUMMARY.md`, so GitBook does not show them. They include the FAQ, the glossary, the release notes, the Helm values reference, `troubleshoot.md`, `harden-a-deployment.md`, `what-is-ingot.md`, `architecture-overview.md` and all five build tool recipes. The skeleton step links all of them in their final section.

## Mapping from old paths to new paths

Every page keeps its file name. Only the folder changes. The table is the single source for the moves, the redirects and the link rewrites in the later pull requests, so it is machine readable: one row per page, both paths relative to `docs/gitbook/`.

| Old path | New path |
|:--|:--|
| `explanation/what-is-ingot.md` | `introduction/what-is-ingot.md` |
| `explanation/how-ingot-compares.md` | `introduction/how-ingot-compares.md` |
| `explanation/drop-in-compatibility.md` | `introduction/drop-in-compatibility.md` |
| `tutorials/first-steps-with-docker.md` | `get-started/first-steps-with-docker.md` |
| `tutorials/set-up-users-teams-and-sso.md` | `get-started/set-up-users-teams-and-sso.md` |
| `how-to/set-up-first-admin.md` | `get-started/set-up-first-admin.md` |
| `how-to/install-with-docker.md` | `set-up/deploy/install-with-docker.md` |
| `how-to/install-jar.md` | `set-up/deploy/install-jar.md` |
| `how-to/run-as-service.md` | `set-up/deploy/run-as-service.md` |
| `how-to/run-on-kubernetes.md` | `set-up/deploy/run-on-kubernetes.md` |
| `how-to/run-split-with-helm.md` | `set-up/deploy/run-split-with-helm.md` |
| `how-to/run-split-dashboard-container.md` | `set-up/deploy/run-split-dashboard-container.md` |
| `how-to/set-helm-values-for-high-availability.md` | `set-up/deploy/set-helm-values-for-high-availability.md` |
| `how-to/autoscale-the-server.md` | `set-up/deploy/autoscale-the-server.md` |
| `how-to/set-a-disruption-budget.md` | `set-up/deploy/set-a-disruption-budget.md` |
| `how-to/route-with-gateway-api.md` | `set-up/deploy/route-with-gateway-api.md` |
| `how-to/manage-secrets-with-gitops.md` | `set-up/deploy/manage-secrets-with-gitops.md` |
| `how-to/size-the-deployment.md` | `set-up/deploy/size-the-deployment.md` |
| `explanation/deployment-options-compared.md` | `set-up/deploy/deployment-options-compared.md` |
| `how-to/run-behind-reverse-proxy.md` | `set-up/network/run-behind-reverse-proxy.md` |
| `how-to/configure-the-edge-for-large-uploads.md` | `set-up/network/configure-the-edge-for-large-uploads.md` |
| `how-to/enable-tls.md` | `set-up/network/enable-tls.md` |
| `how-to/terminate-tls-at-the-gateway.md` | `set-up/network/terminate-tls-at-the-gateway.md` |
| `how-to/restrict-network-traffic.md` | `set-up/network/restrict-network-traffic.md` |
| `how-to/configure-database.md` | `set-up/cluster/configure-database.md` |
| `how-to/configure-cluster-bus.md` | `set-up/cluster/configure-cluster-bus.md` |
| `how-to/use-s3-storage.md` | `set-up/cluster/use-s3-storage.md` |
| `how-to/move-from-reposilite.md` | `set-up/migrate/move-from-reposilite.md` |
| `how-to/upgrade-from-reposilite.md` | `set-up/migrate/upgrade-from-reposilite.md` |
| `how-to/upgrade-from-reposilite-2.md` | `set-up/migrate/upgrade-from-reposilite-2.md` |
| `how-to/migrate-from-upstream-chart.md` | `set-up/migrate/migrate-from-upstream-chart.md` |
| `how-to/import-from-other-repository-managers.md` | `set-up/migrate/import-from-other-repository-managers.md` |
| `how-to/import-from-nexus.md` | `set-up/migrate/import-from-nexus.md` |
| `how-to/use-the-nexus-import-wizard.md` | `set-up/migrate/use-the-nexus-import-wizard.md` |
| `explanation/token-migration.md` | `set-up/migrate/token-migration.md` |
| `how-to/encrypt-settings-secrets.md` | `configure/encrypt-settings-secrets.md` |
| `how-to/customize-branding.md` | `configure/customize-branding.md` |
| `how-to/serve-custom-frontend.md` | `configure/serve-custom-frontend.md` |
| `how-to/limit-concurrent-requests.md` | `configure/limit-concurrent-requests.md` |
| `how-to/configure-oidc-login.md` | `configure/authentication/configure-oidc-login.md` |
| `how-to/configure-keycloak-login.md` | `configure/authentication/configure-keycloak-login.md` |
| `how-to/configure-authentik-login.md` | `configure/authentication/configure-authentik-login.md` |
| `how-to/configure-okta-login.md` | `configure/authentication/configure-okta-login.md` |
| `how-to/configure-google-workspace-login.md` | `configure/authentication/configure-google-workspace-login.md` |
| `how-to/configure-entra-id-login.md` | `configure/authentication/configure-entra-id-login.md` |
| `how-to/configure-github-login.md` | `configure/authentication/configure-github-login.md` |
| `how-to/configure-ldap-login.md` | `configure/authentication/configure-ldap-login.md` |
| `how-to/map-provider-groups-to-teams.md` | `configure/authentication/map-provider-groups-to-teams.md` |
| `how-to/troubleshoot-external-login.md` | `configure/authentication/troubleshoot-external-login.md` |
| `how-to/create-hosted-repositories.md` | `configure/repositories/create-hosted-repositories.md` |
| `how-to/create-proxy-repositories.md` | `configure/repositories/create-proxy-repositories.md` |
| `how-to/create-a-group-repository.md` | `configure/repositories/create-a-group-repository.md` |
| `how-to/separate-releases-and-snapshots.md` | `configure/repositories/separate-releases-and-snapshots.md` |
| `how-to/set-cleanup-policies.md` | `configure/repositories/set-cleanup-policies.md` |
| `how-to/choose-a-search-backend.md` | `configure/search/choose-a-search-backend.md` |
| `how-to/use-opensearch-for-search.md` | `configure/search/use-opensearch-for-search.md` |
| `how-to/publish-and-consume-artifacts.md` | `publish-and-consume/publish-and-consume-artifacts.md` |
| `how-to/publish-and-consume-with-maven.md` | `publish-and-consume/publish-and-consume-with-maven.md` |
| `how-to/publish-and-consume-with-gradle-groovy.md` | `publish-and-consume/publish-and-consume-with-gradle-groovy.md` |
| `how-to/publish-and-consume-with-gradle-kotlin.md` | `publish-and-consume/publish-and-consume-with-gradle-kotlin.md` |
| `how-to/publish-and-consume-with-sbt.md` | `publish-and-consume/publish-and-consume-with-sbt.md` |
| `how-to/publish-and-consume-with-leiningen.md` | `publish-and-consume/publish-and-consume-with-leiningen.md` |
| `how-to/use-tokens-in-ci.md` | `publish-and-consume/use-tokens-in-ci.md` |
| `how-to/restrict-token-to-artifacts.md` | `publish-and-consume/restrict-token-to-artifacts.md` |
| `how-to/verify-uploaded-checksums.md` | `publish-and-consume/verify-uploaded-checksums.md` |
| `how-to/search-for-artifacts.md` | `publish-and-consume/search-for-artifacts.md` |
| `how-to/back-up-and-restore.md` | `manage/operate/back-up-and-restore.md` |
| `how-to/freeze-writes-for-maintenance.md` | `manage/operate/freeze-writes-for-maintenance.md` |
| `how-to/shut-down-without-dropping-requests.md` | `manage/operate/shut-down-without-dropping-requests.md` |
| `how-to/harden-a-deployment.md` | `manage/operate/harden-a-deployment.md` |
| `how-to/verify-release-sboms.md` | `manage/operate/verify-release-sboms.md` |
| `how-to/troubleshoot.md` | `manage/operate/troubleshoot.md` |
| `how-to/collect-a-support-bundle.md` | `manage/operate/collect-a-support-bundle.md` |
| `how-to/rebuild-search-index.md` | `manage/operate/rebuild-search-index.md` |
| `how-to/back-up-before-upgrading.md` | `manage/upgrade/back-up-before-upgrading.md` |
| `how-to/upgrade-ingot.md` | `manage/upgrade/upgrade-ingot.md` |
| `how-to/verify-an-upgrade.md` | `manage/upgrade/verify-an-upgrade.md` |
| `how-to/roll-back-an-upgrade.md` | `manage/upgrade/roll-back-an-upgrade.md` |
| `how-to/monitor-status-and-failures.md` | `manage/monitor/monitor-status-and-failures.md` |
| `how-to/monitor-with-the-prometheus-operator.md` | `manage/monitor/monitor-with-the-prometheus-operator.md` |
| `how-to/review-audit-log.md` | `manage/monitor/review-audit-log.md` |
| `how-to/forward-audit-events-to-a-siem.md` | `manage/monitor/forward-audit-events-to-a-siem.md` |
| `how-to/manage-users-teams-and-machine-accounts.md` | `manage/access/manage-users-teams-and-machine-accounts.md` |
| `how-to/grant-permissions-with-nodes.md` | `manage/access/grant-permissions-with-nodes.md` |
| `how-to/check-access.md` | `manage/access/check-access.md` |
| `how-to/recover-admin-access.md` | `manage/access/recover-admin-access.md` |
| `how-to/install-a-plugin.md` | `manage/plugins/install-a-plugin.md` |
| `how-to/write-a-plugin.md` | `manage/plugins/write-a-plugin.md` |
| `how-to/write-a-search-backend-plugin.md` | `manage/plugins/write-a-search-backend-plugin.md` |
| `how-to/extend-the-dashboard-from-a-plugin.md` | `manage/plugins/extend-the-dashboard-from-a-plugin.md` |
| `explanation/architecture-overview.md` | `references/architecture/architecture-overview.md` |
| `explanation/backend-architecture.md` | `references/architecture/backend-architecture.md` |
| `explanation/high-availability-support.md` | `references/architecture/high-availability-support.md` |
| `explanation/multi-replica-operation.md` | `references/architecture/multi-replica-operation.md` |
| `explanation/storage-and-mirror-caching.md` | `references/architecture/storage-and-mirror-caching.md` |
| `explanation/identity-model.md` | `references/architecture/identity-model.md` |
| `explanation/permission-evaluation.md` | `references/architecture/permission-evaluation.md` |
| `explanation/sessions-and-login.md` | `references/architecture/sessions-and-login.md` |
| `explanation/security-headers.md` | `references/architecture/security-headers.md` |
| `explanation/how-the-search-index-stays-current.md` | `references/architecture/how-the-search-index-stays-current.md` |
| `reference/configuration-and-startup.md` | `references/configuration/configuration-and-startup.md` |
| `reference/environment-variables.md` | `references/configuration/environment-variables.md` |
| `reference/deploy-targets.md` | `references/configuration/deploy-targets.md` |
| `reference/helm-values.md` | `references/configuration/helm-values.md` |
| `reference/settings-domains.md` | `references/configuration/settings-domains.md` |
| `reference/settings-encryption.md` | `references/configuration/settings-encryption.md` |
| `reference/storage-providers.md` | `references/configuration/storage-providers.md` |
| `reference/outbound-connections.md` | `references/configuration/outbound-connections.md` |
| `reference/repository-settings.md` | `references/configuration/repository-settings.md` |
| `reference/repository-types.md` | `references/configuration/repository-types.md` |
| `reference/cleanup-policies.md` | `references/configuration/cleanup-policies.md` |
| `reference/opensearch-search-backend.md` | `references/configuration/opensearch-search-backend.md` |
| `reference/login-providers.md` | `references/configuration/login-providers.md` |
| `reference/http-api.md` | `references/http-api/http-api.md` |
| `reference/api-errors.md` | `references/http-api/api-errors.md` |
| `reference/search-filters-and-api.md` | `references/http-api/search-filters-and-api.md` |
| `reference/web-routes.md` | `references/http-api/web-routes.md` |
| `reference/access-tokens-and-routes.md` | `references/http-api/access-tokens-and-routes.md` |
| `reference/permission-nodes.md` | `references/identity/permission-nodes.md` |
| `reference/artifact-permission-nodes.md` | `references/identity/artifact-permission-nodes.md` |
| `reference/audit-events.md` | `references/identity/audit-events.md` |
| `reference/dashboard-pages.md` | `references/dashboard/dashboard-pages.md` |
| `reference/dashboard-anchors.md` | `references/dashboard/dashboard-anchors.md` |
| `reference/plugin-api.md` | `references/plugins/plugin-api.md` |
| `reference/plugin-store.md` | `references/plugins/plugin-store.md` |
| `reference/console-commands.md` | `references/console-commands.md` |
| `reference/glossary.md` | `references/glossary.md` |
| `reference/faq.md` | `references/faq.md` |
| `reference/release-notes.md` | `release-notes/release-notes.md` |
| `reference/upgrade-changes.md` | `release-notes/upgrade-changes.md` |
| `explanation/contributing-to-the-docs.md` | `contributing/contributing-to-the-docs.md` |

Pages that stay: `README.md` and `SUMMARY.md`. A page that is mentioned from outside `docs/gitbook/` needs its reference updated in the same pull request that moves it. These are the references today: `Dockerfile`, `docker-compose.split.yml`, `.github/README.md`, `.github/RELEASE.md`, `.github/workflows/openapi-diff.yml`, `reposilite-backend/build.gradle.kts` and `reposilite-backend/src/main/kotlin/com/reposilite/frontend/api/PluginStylesheetValidator.kt` (comments only).

## Redirects

`.gitbook.yaml` supports a `redirects:` map from an old path to a new path within the space. The GitBook documentation shows the form `previous/page: new-folder/page.md`, so the old path has no extension and the new path ends in `.md`. Paths are relative to `root`, which is `./docs/gitbook/`.

```yaml
redirects:
  how-to/install-jar: set-up/deploy/install-jar.md
```

Rules:

* Every moved page gets one redirect in the same pull request that moves it, including the 26 pages that were not in the table of contents.
* The redirect map is generated from the mapping table and not written by hand.
* The documentation does not state whether a redirect covers anchors. Links with `#anchor` are updated inside the docs, and a redirect is not relied on for them.
* A page that is deleted instead of moved is not part of this plan.

## Diagrams

### Format decision

GitBook renders a code block with the language `mermaid` as a native diagram block, with no integration. Mermaid is therefore the default format:

* The source is the diagram. There is no generated file to commit or to keep in step, and a reviewer reads the change in the diff.
* GitHub renders the same block, so the source also reads well in a pull request.
* The documentation of GitBook does not say how the native block behaves in dark mode. The diagrams therefore use no colors of their own (no `classDef`, no `style`, no `fill`) and rely on the theme of the renderer. The first diagram pull request checks the result in the GitBook preview in both themes before more diagrams follow.

A diagram that Mermaid cannot draw legibly gets a committed SVG pair instead: a light and a dark file, with the Mermaid or D2 source beside them, shown with a `<picture>` element whose `<source media="(prefers-color-scheme: dark)">` points to the dark file. GitBook documents this form for images. The SVG sources live in `docs/gitbook/.gitbook/assets/src/`, the rendered files next to them in `docs/gitbook/.gitbook/assets/`. No such diagram is planned today.

### Image references (open question)

Images that are not Mermaid, such as a later screenshot, need a location. The maintainer chose `img.onelitefeather.net` earlier. The plan records both options and a default:

| Option | For | Against |
|:--|:--|:--|
| Repo-relative `.gitbook/assets/` (default) | Reviewed and versioned with the page, works offline and in the GitHub view, no dependency on a second host. | Binary files grow the repository. |
| Absolute `https://img.onelitefeather.net/...` URLs | Keeps binaries out of the repository. | GitBook shows a broken image when the URL moves, the files are not reviewed with the page, and the GitHub view depends on that host. |

Default: repo-relative for every diagram and for small images, and `img.onelitefeather.net` only for large screenshots, if the maintainer wants it. The "Screenshots" rule in [Contributing to the docs](../explanation/contributing-to-the-docs.md) says there are no screenshots, and is rewritten in the first diagram pull request.

Rules for every diagram, independent of the format: one sentence before it that says what it shows, a text alternative (the surrounding prose carries every fact the diagram shows), no more than about 12 nodes, labels that use the exact names of the docs (`INGOT_TARGET`, `allInOne`, `split`), and no secrets or real host names.

### Diagram list

Each diagram goes to the page where its concept is explained. Every node and arrow is checked against the code on the main branch before it is drawn.

| Id | Diagram | Mermaid type | Page |
|:--|:--|:--|:--|
| D1 | Architecture overview: client, server, dashboard, database, storage, search backends, cluster bus, plugins | flowchart | `references/architecture/architecture-overview.md` |
| D2 | Deployment modes: `allInOne` and `split` side by side, with the `all` and `server` targets | flowchart with subgraphs | `references/architecture/deployment-modes.md` |
| D3 | Cluster mode: replicas on a shared database, object storage and the cluster bus | flowchart | `references/architecture/multi-replica-operation.md` |
| D4 | Request flow: proxy, group and hosted resolution, resolution cache, mirror cache, storage | sequence diagram | `references/architecture/storage-and-mirror-caching.md` |
| D5 | Auth flow: browser login, session and refresh token, OIDC redirect | sequence diagram | `references/architecture/sessions-and-login.md` |
| D6 | Helm chart topology, one per mode (`allInOne`, `split`) | flowchart | `set-up/deploy/run-on-kubernetes.md` and `set-up/deploy/run-split-with-helm.md` |
| D7 | Reposilite to Ingot migration flow: back up, swap the image, verify, roll back | flowchart | `set-up/migrate/move-from-reposilite.md` |

D2 and D6 show only modes that exist on the main branch. A third mode is added to the same diagram by the pull request that adds the mode.

## Pull request sequence

Each pull request stays under about 400 changed lines, pure moves excluded. Docs only, type `docs(gitbook)`. The steps are merged in the order given.

1. **Plan.** This page. `docs(gitbook): plan the mimir style structure and diagrams`.
2. **Skeleton.** The new `SUMMARY.md` with the section groups of this plan, still pointing at the current page paths, the 26 orphan pages linked, and the overview pages at their final paths. `docs(gitbook): add the section skeleton and overview pages`.
3. **Moves, one section per pull request.** Each one moves the pages of one section, rewrites the relative links in every page, updates `SUMMARY.md`, adds the redirects and updates the outside references:
   1. Introduction and Get started
   2. Set up
   3. Configure
   4. Publish and consume
   5. Manage
   6. References, Release notes and Contributing
4. **Deployment modes page.** `references/architecture/deployment-modes.md`, with D2.
5. **Diagrams, two or three per pull request.** D1 with the image rules rewrite, then D3 to D5, then D6 and D7.
6. **Cleanup.** Update the structure rules in [Contributing to the docs](../explanation/contributing-to-the-docs.md) and delete this page.

Checks for every pull request: every relative link resolves, no dash characters (`grep -nP '[\x{2013}\x{2014}]'`), every page has a `description:` line, every page of the folder is in `SUMMARY.md`, and for a move, every old path is in `redirects:`.

## Open questions

1. **Image host.** Repo-relative `.gitbook/assets/` (default) or `img.onelitefeather.net`. See above.
2. **Mermaid in dark mode.** Not documented by GitBook. Verified in the preview in the first diagram pull request. The fallback is the SVG pair.
3. **Redirect anchors.** Not documented. The docs update their own anchor links and the plan does not rely on it.
4. **Outside references.** Moving a page needs a one line change in files outside `docs/gitbook/` (see the list above). The default is to include those edits in the move pull request, because a stale path in a comment or a compose file is a broken link.
5. **Orphan pages.** Linking the 26 orphans exposes pages that have not been in the published navigation. The default is to publish them all, since they went through review.
6. **Section naming.** "Publish and consume" stands in for Mimir's Send and Query. The default is to keep it.

## Related pages

* [Contributing to the docs](../explanation/contributing-to-the-docs.md) holds the rules for a single page.
* [Architecture overview](../explanation/architecture-overview.md) is the page that gets the first diagram.
* [Deploy targets](../reference/deploy-targets.md) and [Deployment options compared](../set-up/deploy/deployment-options-compared.md) are the sources of the modes page.
