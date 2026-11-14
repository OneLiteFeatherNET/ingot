---
description: Configure Ingot. Lists the how-to pages for settings secrets, branding, the frontend, request limits, login providers, repositories and search, and links to the reference for every setting.
---

# Configure Ingot

This section changes the behaviour of a running instance. Every setting has a lookup entry in [Configuration file and startup parameters](../reference/configuration-and-startup.md) and [Environment variables](../reference/environment-variables.md). A variable with an `INGOT_` name takes precedence, and the `REPOSILITE_` name stays valid as a fallback.

## Instance

Secrets, branding, frontend and limits.

* [Encrypt the settings secrets](../how-to/encrypt-settings-secrets.md)
* [Customize branding](../how-to/customize-branding.md)
* [Serve a custom frontend](../how-to/serve-custom-frontend.md)
* [Limit concurrent requests](../how-to/limit-concurrent-requests.md)

## Authentication

Login providers and group mapping.

* [Configure generic OIDC login](../how-to/configure-oidc-login.md)
* [Configure Keycloak login](../how-to/configure-keycloak-login.md)
* [Configure Authentik login](../how-to/configure-authentik-login.md)
* [Configure Okta login](../how-to/configure-okta-login.md)
* [Configure Google Workspace login](../how-to/configure-google-workspace-login.md)
* [Configure Microsoft Entra ID login](../how-to/configure-entra-id-login.md)
* [Configure GitHub login](../how-to/configure-github-login.md)
* [Configure LDAP login](../how-to/configure-ldap-login.md)
* [Map provider groups to teams](../how-to/map-provider-groups-to-teams.md)
* [Troubleshoot external login](../how-to/troubleshoot-external-login.md)

## Repositories

Hosted, proxy and group repositories, version policies and cleanup.

* [Create hosted repositories](../how-to/create-hosted-repositories.md)
* [Create a proxy repository](../how-to/create-proxy-repositories.md)
* [Create a group repository](../how-to/create-a-group-repository.md)
* [Separate releases and snapshots](../how-to/separate-releases-and-snapshots.md)
* [Set cleanup policies](../how-to/set-cleanup-policies.md)

## Search

The search backend.

* [Choose a search backend](../how-to/choose-a-search-backend.md)
* [Use OpenSearch for search](../how-to/use-opensearch-for-search.md)
