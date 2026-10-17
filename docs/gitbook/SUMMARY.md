# Summary

* [Ingot documentation](README.md)

## Tutorials

* [First steps with Docker](tutorials/first-steps-with-docker.md)
* [Set up users, teams and single sign on](tutorials/set-up-users-teams-and-sso.md)

## How-to guides

### Install and run

* [Install with Docker](how-to/install-with-docker.md)
* [Run the dashboard in a separate container](how-to/run-split-dashboard-container.md)
* [Run on Kubernetes](how-to/run-on-kubernetes.md)
* [Run the JAR](how-to/install-jar.md)
* [Run as a service](how-to/run-as-service.md)
* [Run behind a reverse proxy](how-to/run-behind-reverse-proxy.md)
* [Enable TLS](how-to/enable-tls.md)
* [Configure a database](how-to/configure-database.md)
* [Use S3 storage](how-to/use-s3-storage.md)
* [Serve a custom frontend](how-to/serve-custom-frontend.md)
* [Customize branding](how-to/customize-branding.md)

### Identity and access

* [Set up the first administrator](how-to/set-up-first-admin.md)
* [Manage users, teams and machine accounts](how-to/manage-users-teams-and-machine-accounts.md)
* [Configure generic OIDC login](how-to/configure-oidc-login.md)
* [Configure Microsoft Entra ID login](how-to/configure-entra-id-login.md)
* [Configure GitHub login](how-to/configure-github-login.md)
* [Configure LDAP login](how-to/configure-ldap-login.md)
* [Use tokens in CI](how-to/use-tokens-in-ci.md)
* [Restrict a token to artifacts](how-to/restrict-token-to-artifacts.md)
* [Grant permissions with nodes](how-to/grant-permissions-with-nodes.md)
* [Check what a principal can do](how-to/check-access.md)
* [Recover admin access](how-to/recover-admin-access.md)

### Repositories and artifacts

* [Publish and consume artifacts](how-to/publish-and-consume-artifacts.md)
* [Create hosted repositories](how-to/create-hosted-repositories.md)
* [Create a proxy repository](how-to/create-proxy-repositories.md)
* [Search for artifacts](how-to/search-for-artifacts.md)
* [Rebuild the search index](how-to/rebuild-search-index.md)
* [Set cleanup policies](how-to/set-cleanup-policies.md)
* [Import from other repository managers](how-to/import-from-other-repository-managers.md)
* [Install a plugin](how-to/install-a-plugin.md)
* [Write a plugin](how-to/write-a-plugin.md)

### Operate and upgrade

* [Back up and restore](how-to/back-up-and-restore.md)
* [Back up before upgrading](how-to/back-up-before-upgrading.md)
* [Upgrade from Ingot 1.1.x](how-to/upgrade-ingot.md)
* [Upgrade from Reposilite 3.x](how-to/upgrade-from-reposilite.md)
* [Upgrade from Reposilite 2.x](how-to/upgrade-from-reposilite-2.md)
* [Verify an upgrade](how-to/verify-an-upgrade.md)
* [Roll back an upgrade](how-to/roll-back-an-upgrade.md)
* [Review the audit log](how-to/review-audit-log.md)
* [Monitor status and failures](how-to/monitor-status-and-failures.md)

## Reference

### Configuration

* [Configuration file and startup parameters](reference/configuration-and-startup.md)
* [Environment variables](reference/environment-variables.md)
* [Settings domains](reference/settings-domains.md)
* [Storage providers](reference/storage-providers.md)
* [Repository settings](reference/repository-settings.md)
* [Repository types](reference/repository-types.md)
* [Cleanup policies](reference/cleanup-policies.md)
* [Audit events](reference/audit-events.md)
* [Console commands](reference/console-commands.md)

### Identity and access

* [Permission nodes](reference/permission-nodes.md)
* [Artifact permission nodes](reference/artifact-permission-nodes.md)
* [Login providers](reference/login-providers.md)
* [Access tokens and routes](reference/access-tokens-and-routes.md)

### HTTP and web

* [HTTP API overview](reference/http-api.md)
* [Search filters and API](reference/search-filters-and-api.md)
* [Web routes](reference/web-routes.md)
* [Dashboard pages](reference/dashboard-pages.md)
* [Plugin API](reference/plugin-api.md)

### Upgrades

* [Upgrade changes and supported paths](reference/upgrade-changes.md)

## Explanation

### Architecture and compatibility

* [Drop-in compatibility with Reposilite](explanation/drop-in-compatibility.md)
* [Multi-replica operation](explanation/multi-replica-operation.md)
* [Storage and mirror caching](explanation/storage-and-mirror-caching.md)

### Identity and access

* [Identity model](explanation/identity-model.md)
* [How permissions are evaluated](explanation/permission-evaluation.md)
* [Sessions, login and CSRF](explanation/sessions-and-login.md)
* [Token migration](explanation/token-migration.md)

### Search

* [How the search index stays current](explanation/how-the-search-index-stays-current.md)
