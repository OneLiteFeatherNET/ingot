---
description: Configure Ingot. Lists the how-to pages for login providers, repositories, search, branding and the frontend, and links to the reference for every setting.
---

# Configure Ingot

This section changes the behaviour of a running instance. Every setting has a lookup entry in [Configuration file and startup parameters](../references/configuration/configuration-and-startup.md) and [Environment variables](../references/configuration/environment-variables.md). A variable with an `INGOT_` name takes precedence, and the `REPOSILITE_` name stays valid as a fallback.

## Authentication

Login providers and group mapping.

* [Configure generic OIDC login](authentication/configure-oidc-login.md)
* [Configure Keycloak login](authentication/configure-keycloak-login.md)
* [Configure Authentik login](authentication/configure-authentik-login.md)
* [Configure Okta login](authentication/configure-okta-login.md)
* [Configure Google Workspace login](authentication/configure-google-workspace-login.md)
* [Configure Microsoft Entra ID login](authentication/configure-entra-id-login.md)
* [Configure GitHub login](authentication/configure-github-login.md)
* [Configure LDAP login](authentication/configure-ldap-login.md)
* [Map provider groups to teams](authentication/map-provider-groups-to-teams.md)
* [Troubleshoot external login](authentication/troubleshoot-external-login.md)

## Repositories

Hosted, proxy and group repositories, version policies, cleanup and checksums.

* [Create hosted repositories](repositories/create-hosted-repositories.md)
* [Create a proxy repository](repositories/create-proxy-repositories.md)
* [Create a group repository](repositories/create-a-group-repository.md)
* [Separate releases and snapshots](repositories/separate-releases-and-snapshots.md)
* [Set cleanup policies](repositories/set-cleanup-policies.md)
* [Verify uploaded checksums](repositories/verify-uploaded-checksums.md)

## Search

The search backend and its index.

* [Choose a search backend](search/choose-a-search-backend.md)
* [Use OpenSearch for search](search/use-opensearch-for-search.md)
* [Rebuild the search index](search/rebuild-search-index.md)

## Dashboard

Branding and a custom frontend.

* [Customize branding](dashboard/customize-branding.md)
* [Serve a custom frontend](dashboard/serve-custom-frontend.md)
