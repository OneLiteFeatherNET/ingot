---
description: Reference for the shared settings domains of Ingot (authentication, audit, cleanup, frontend, javadoc, login, maven, search, statistics and web) with every field, its default value and the rules that the settings API checks.
---

# Settings domains

Shared settings are the settings that all instances of one installation have in common. They are grouped in domains. Each domain has a name, a document and a JSON schema. The local settings of one instance are not part of them, see [Configuration file and startup parameters](configuration-and-startup.md).

## Where the settings live

| Aspect | Behavior |
| --- | --- |
| Storage | By default the `settings` table of the database, under the name `remote-shared-configuration`. With `--shared-configuration` a JSON file is used instead, see [Shared settings source](configuration-and-startup.md#shared-settings-source). |
| Document | One JSON document. The top-level keys are the domain names. A domain that is missing from the document has its default values. |
| Refresh | Instances that use the database check it every 10 seconds. A change reaches every instance within about 10 seconds. |
| Domain name | The title of the domain in lower case, for example `authentication`. |

## Settings API

All routes need the manager permission.

| Route | Purpose |
| --- | --- |
| `GET /api/settings/domains` | Lists the domain names. |
| `GET /api/settings/schema/{name}` | Returns the JSON schema of one domain. |
| `GET /api/settings/domain/{name}` | Returns the current settings of one domain. The answer carries an `ETag` header. |
| `PUT /api/settings/domain/{name}` | Replaces the settings of one domain. The answer carries the new `ETag`. |

| Behavior | Detail |
| --- | --- |
| Optimistic concurrency | A `PUT` with an `If-Match` header that no longer matches the stored document is refused with `412`. Without `If-Match` the write is unconditional. |
| Validation | A domain with rules (marked in the tables below) refuses an invalid document with `400` and keeps the current settings. |
| Unknown domain | The reads answer `200` with an empty body. A `PUT` answers `400` with `Body is empty`. |
| Secret fields | Marked as secret below. With the local key `hideSettingsSecrets` set to `true`, a read returns `********` for every non-empty secret, and a write that sends `********` keeps the stored value. A `********` that has no stored counterpart is refused with `400`. With the default `false`, the stored values are returned. |

Domains:

| Domain | Purpose |
| --- | --- |
| [`authentication`](#authentication) | Brute force protection and the legacy LDAP authenticator for Basic authentication. |
| [`audit`](#audit) | Retention of the audit log. |
| [`cleanup`](#cleanup) | Cleanup policies. |
| [`frontend`](#frontend) | Title, links and appearance of the dashboard. |
| [`javadoc`](#javadoc) | Javadoc viewer. |
| [`login`](#login) | Browser login: sessions, lockout, OpenID Connect and LDAP. |
| [`maven`](#maven) | Repositories. |
| [`search`](#search) | Behavior of the artifact search index. |
| [`statistics`](#statistics) | Request statistics. |
| [`web`](#web) | Proxy headers. |

## authentication

Settings of the Basic authentication of Maven clients and tools.

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `bruteForceProtection.enabled` | Boolean | `false` | Blocks an address after too many failed authentications. |
| `bruteForceProtection.maxAttempts` | Int | `10` | Failed attempts before the address is blocked. |
| `bruteForceProtection.banDurationSeconds` | Long | `300` | Seconds that the address stays blocked. |
| `ldap` | Object | see below | Legacy LDAP authenticator. |

`authentication.ldap` serves the Basic authentication of clients. The new dashboard login uses `login.ldap`. While `login.ldap` is off, `authentication.ldap` serves the dashboard login too. See [Login providers](login-providers.md).

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `ldap.enabled` | Boolean | `false` | Turns the authenticator on. |
| `ldap.ssl` | Boolean | `false` | Uses `ldaps` instead of `ldap`. |
| `ldap.hostname` | String | `ldap.domain.com` | Address of the directory server. |
| `ldap.port` | Int | `389` | Port of the directory server. |
| `ldap.baseDn` | String | `dc=company,dc=com` | Base DN with the users. |
| `ldap.searchUserDn` | String | `cn=reposilite,ou=admins,dc=domain,dc=com` | Account that searches the directory. It needs read access to all entries. |
| `ldap.searchUserPassword` | String, secret | `reposilite-admin-secret` | Password of that account. |
| `ldap.typeAttribute` | String | `person` | Value of the `objectClass` in the user search. |
| `ldap.userAttribute` | String | `cn` | Attribute that holds the unique user name. The access token is created with it. |
| `ldap.userFilter` | String | `(&(objectClass=person)(ou=Maven Users))` | LDAP filter for users. |
| `ldap.userType` | `PERSISTENT` or `TEMPORARY` | `PERSISTENT` | Type of the access token that is created for an LDAP user. |

## audit

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `retentionDays` | Int | `90` | Events older than this are deleted once a day. `0` keeps every event. |
| `recordActorAddress` | Boolean | `true` | Stores the client IP address next to the actor of each event. |

## cleanup

Validated: a domain with a violation is refused. Nothing is deleted unless a policy is run by hand or enabled with a schedule. The rules are in [Cleanup policies](cleanup-policies.md).

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `policies` | List of policy | empty | The cleanup policies. Every policy is evaluated on its own. |

Fields of a policy:

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `id` | String | empty | Unique id. 1 to 64 letters, digits, dots, dashes or underscores, starting with a letter or digit. |
| `enabled` | Boolean | `false` | Only enabled policies with a schedule run on their own. Manual runs work either way. |
| `repositories` | List of String | empty | Ids of the repositories that the policy acts on. An empty list acts on nothing. |
| `scope` | `SNAPSHOTS`, `RELEASES` or `MIRROR_CACHE` | `RELEASES` | Which versions the policy may touch. |
| `keepLatest` | Int or null | `null` | Keeps the newest N versions of each artifact. At least 1 when set. |
| `olderThanDays` | Int or null | `null` | Deletes only versions last modified more than this many days ago. At least 1 when set. |
| `notDownloadedForDays` | Int or null | `null` | Deletes only versions not downloaded for this many days. At least 1 when set. |
| `groupFilter` | String or null | `null` | Regular expression that must match the whole artifact path, for example `net/onelitefeather/.*`. Empty matches everything. |
| `schedule` | String | `manual` | `manual`, or a daily time `HH:mm` in the time zone of the server, for example `04:30`. |

Rules checked on save: policy ids are unique, at least one of `keepLatest`, `olderThanDays` and `notDownloadedForDays` is set, `groupFilter` is a valid regular expression, and `schedule` is `manual` or a valid time.

## frontend

Validated: the colour fields of `appearance` must be empty or a six digit hex colour.

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `id` | String | `ingot-repository` | Repository id used in the Maven repository configuration shown by the dashboard. |
| `title` | String | `Ingot Repository` | Title on the home page. |
| `description` | String | `Public Maven repository hosted through Ingot` | Description on the home page. HTML tags are not supported. |
| `organizationWebsite` | String | `https://github.com/OneLiteFeatherNET` | Link to the website of the organization. |
| `organizationLogo` | String | `https://avatars.githubusercontent.com/u/103827826` | Link to the logo of the organization. |
| `privacyPolicy` | String | empty | Link to a privacy policy, shown in the footer. |
| `icpLicense` | String | empty | ICP license number for web services in China. |
| `appearance` | Object | see below | Branding of the dashboard. |

Fields of `appearance`:

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `seedColor` | String | empty | Colour `#RRGGBB` from which the palette is derived. Empty keeps the built-in theme. |
| `schemeVariant` | `TONAL_SPOT`, `VIBRANT`, `EXPRESSIVE`, `NEUTRAL`, `FIDELITY`, `CONTENT` or `MONOCHROME` | `VIBRANT` | How the palette is derived from `seedColor`. Applies only when `seedColor` is set. |
| `primaryColor` | String | empty | Overrides the primary key colour, `#RRGGBB`. |
| `secondaryColor` | String | empty | Overrides the secondary key colour, `#RRGGBB`. |
| `tertiaryColor` | String | empty | Overrides the tertiary key colour, `#RRGGBB`. |
| `errorColor` | String | empty | Overrides the error key colour, `#RRGGBB`. |
| `logoDark` | String | empty | Link to a logo for the dark theme. Empty uses `organizationLogo`. |
| `favicon` | String | empty | Link to the favicon. Empty uses the bundled favicon. |
| `footerText` | String | empty | Plain text in the footer. HTML is not rendered. |

## javadoc

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `enabled` | Boolean | `true` | Turns the Javadoc viewer on. |
| `suffixes` | List of String | `-javadoc.jar`, `-groovydoc.jar` | File name suffixes that the viewer serves, for example `-docs.zip`. |

## login

Validated: `publicUrl` must be valid.

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `sessionLifetimeMinutes` | Long | `15` | Lifetime of a browser session token. |
| `refreshLifetimeHours` | Long | `168` | Lifetime of a refresh token. Every renewal issues a new one, so this limits the idle time of a browser session. |
| `refreshAbsoluteDays` | Long | `30` | Longest time a login stays renewable, counted from the login. |
| `maxFailedAttempts` | Int | `5` | Failed password logins that lock a user name from one address. The same number of failures locks an address. |
| `lockoutMinutes` | Long | `15` | Duration of the first lockout. Each further lockout without a quiet period doubles it, up to 24 hours. |
| `publicUrl` | String | empty | Public address of the dashboard, for example `https://repo.example.org/`. `http://` is accepted only for localhost. The redirect addresses of the OpenID Connect providers are built from it. Empty builds them from the `Host` header and `X-Forwarded-Proto` of the request. |
| `oidcProviders` | List of provider | empty | External OpenID Connect providers. |
| `ldap` | Object | see below | Directory for the browser login. |

The lockout limits apply independently of `authentication.bruteForceProtection`.

Fields of an entry of `oidcProviders`. Every entry is disabled by default.

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `id` | String | empty | Slug of the provider: 1 to 64 lower case letters, digits or dashes, unique in the list. It is part of the group references, so do not change it. |
| `enabled` | Boolean | `false` | Announces the provider on the login page and serves its routes. |
| `label` | String | empty | Text of the login button. |
| `icon` | `GENERIC`, `GITHUB`, `GITLAB`, `GOOGLE`, `MICROSOFT`, `KEYCLOAK`, `AUTHENTIK`, `OKTA`, `AUTH0` or `GITEA` | `GENERIC` | Logo of the button. `GITHUB` with an empty issuer or `https://github.com` uses the GitHub OAuth endpoints. |
| `issuerUrl` | String | empty | Base URL of the provider. Discovery is read from `/.well-known/openid-configuration`. |
| `clientId` | String | empty | Client id registered with the provider. |
| `clientSecret` | String, secret | empty | Client secret. Sent with HTTP Basic authentication. |
| `scopes` | String | `openid profile email` | Requested scopes. For GitHub the default is `read:user user:email read:org`. |
| `usernameClaim` | String | `preferred_username` | Claim that gives the user name of a new user. |
| `displayNameClaim` | String | `name` | Claim that gives the display name. |
| `emailClaim` | String | `email` | Claim that gives the email address. |
| `groupsClaim` | String | `groups` | Claim that lists the groups, as an array or a single string. |
| `allowedEmailDomains` | List of String | empty | When not empty, the email must be in one of these domains. Checked at every login. |
| `autoCreateUsers` | Boolean | `true` | Creates a user at the first login. When `false`, only linked users can log in. |
| `adminGroups` | List of String | empty | Groups whose members become instance administrators through a synchronized team. |

An enabled entry that lacks `issuerUrl` or `clientId` is not announced, and the log names the missing field. For GitHub the needed fields are `clientId` and `clientSecret`.

Fields of `ldap`:

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `enabled` | Boolean | `false` | Announces the LDAP login and accepts LDAP passwords on `POST /api/auth/login`. |
| `label` | String | `LDAP` | Text of the LDAP login on the login page. |
| `url` | String | empty | `ldap://` or `ldaps://` address, for example `ldaps://ldap.example.org:636`. |
| `startTls` | Boolean | `false` | Upgrades an `ldap://` connection with StartTLS before the first bind. |
| `bindDn` | String | empty | Account of the service bind. Empty searches anonymously. |
| `bindPassword` | String, secret | empty | Password of the service bind. |
| `userBaseDn` | String | empty | Where users are searched. |
| `userFilter` | String | `(uid={username})` | Filter that finds a user. `{username}` is replaced by the escaped user name. |
| `usernameAttribute` | String | `uid` | Attribute that gives the user name of a new user. |
| `displayNameAttribute` | String | `cn` | Attribute that gives the display name. |
| `emailAttribute` | String | `mail` | Attribute that gives the email address. |
| `groupBaseDn` | String | empty | Where groups are searched. Empty turns the group synchronization off. |
| `groupFilter` | String | `(member={dn})` | Filter that finds the groups of a user. `{dn}` and `{username}` are replaced by their escaped values. |
| `groupNameAttribute` | String | `cn` | Attribute that gives the name of a group. |
| `autoCreateUsers` | Boolean | `true` | Creates a user at the first login. When `false`, only linked users can log in. |
| `adminGroups` | List of String | empty | Groups whose members become instance administrators through a synchronized team. |

The directory login is usable when `url` and `userBaseDn` are set. For the provider settings in detail, see [Login providers](login-providers.md).

## maven

Validated: the description and tags of each repository are checked. The repository fields are described in [Repository settings](repository-settings.md).

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `repositories` | List of repository | `releases`, `snapshots`, `private` | The repositories. `private` has the visibility `PRIVATE`, the other two use the defaults below. |

Fields of a repository:

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `id` | String | empty | Id of the repository. Not empty. |
| `visibility` | `PUBLIC`, `HIDDEN` or `PRIVATE` | `PUBLIC` | Visibility of the repository. |
| `description` | String | empty | Plain text shown in the repository list. At most 280 characters. |
| `tags` | List of String | empty | Labels. At most 10 tags. Each tag has 1 to 32 lower case letters, digits or dashes, starts with a letter or digit and appears once. |
| `redeployment` | Boolean | `false` | Accepts redeployment of the same artifact version. |
| `preserveSnapshots` | Boolean | `false` | Keeps old snapshot build files. By default Ingot deletes them. |
| `storageProvider` | Object | file system | Storage of the repository. See [Storage providers](storage-providers.md). |
| `storagePolicy` | `PRIORITIZE_UPSTREAM_METADATA` or `STRICT` | `PRIORITIZE_UPSTREAM_METADATA` | How requests are handled when mirrors are configured. `STRICT` reads the stored copy first and asks the upstream only when it is missing. It is not an offline mode. |
| `metadataMaxAge` | Long | `0` | Seconds that fetched metadata files stay valid with `PRIORITIZE_UPSTREAM_METADATA`. `0` fetches every time. |
| `resolutionCacheMaxEntries` | Int | `0` | Size of the cache for routing decisions of metadata reads. `0` turns it off. At least 0. |
| `proxied` | List of mirror | empty | Mirrors associated with the repository. |

Fields of a mirror in `proxied`:

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `reference` | String | empty | Id of another local repository, or the URL of a remote repository. Not empty. |
| `store` | Boolean | `false` | Stores proxied artifacts locally. |
| `allowedGroups` | List of String | empty | Allowed artifact groups. Empty allows all. |
| `allowedExtensions` | List of String | `.jar`, `.war`, `.aar`, `.pom`, `.xml`, `.module`, `.md5`, `.sha1`, `.sha256`, `.sha512`, `.asc` | Accepted file extensions. Empty allows all. The value `<none>` permits files without an extension. |
| `connectTimeout` | Int | `3` | Seconds to wait for the connection to the remote host. At least 0. |
| `readTimeout` | Int | `15` | Seconds to read data from the remote host. At least 0. |
| `authorization` | Object or null | `null` | Credentials for the remote host. |
| `httpProxy` | String, secret | empty | HTTP or SOCKS proxy, for example `HTTP 127.0.0.1:1081` or `SOCKS 127.0.0.1:1080 login password`. |
| `authenticatedFetchingOnly` | Boolean | `false` | Requires an authenticated request before an uncached artifact is fetched from the remote repository. |

Fields of `authorization`:

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `method` | `BASIC`, `CUSTOM_HEADER` or `LOOPBACK_LINK` | `BASIC` | How the credentials are sent. |
| `login` | String | empty | Login, or the name of the custom header. |
| `password` | String, secret | empty | Password, or the value of the custom header. |

## search

Validated: `verifyAt` and `rebuildSchedule.at` are times `HH:mm`, `verifySampleSize` and `rebuildSchedule.everyDays` are at least 1. Whether search exists at all is the local key `searchEnabled`. See [Search filters and API](search-filters-and-api.md).

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `verifyEnabled` | Boolean | `true` | Runs the nightly check that compares a sample of index rows with storage and repairs differences. |
| `verifyAt` | String | `02:30` | Start of the nightly check, UTC, `HH:mm`. |
| `verifySampleSize` | Int | `2000` | Index rows that one check compares with storage. |
| `rebuildSchedule.enabled` | Boolean | `false` | Starts a scheduled full rebuild. |
| `rebuildSchedule.at` | String | `03:30` | Start of the scheduled rebuild, UTC, `HH:mm`. |
| `rebuildSchedule.everyDays` | Int | `7` | Days between two scheduled rebuilds. |
| `gapRebuild` | Boolean | `true` | Starts a rebuild after a start that finds a gap in the heartbeat, for example after a downgrade. |

## statistics

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `enabled` | Boolean | `true` | Records request statistics. They can use much space on public repositories with many artifacts. |
| `resolvedRequestsInterval` | `DAILY`, `WEEKLY`, `MONTHLY` or `YEARLY` | `MONTHLY` | Interval into which recorded requests are grouped. A shorter interval gives more detail and a larger database. |

## web

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `forwardedIp` | String | `X-Forwarded-For` | Name of the header that carries the client IP behind a proxy. Common values are `X-Forwarded-For`, `CF-Connecting-IP` and `X-Real-IP`. |
| `forwardedPrefix` | String | empty | Name of the header with which a reverse proxy announces the sub-path Ingot is mounted under, for example `X-Forwarded-Prefix`. Empty turns it off. |

## Secret fields

These fields are masked when `hideSettingsSecrets` is `true`.

| Domain | Field |
| --- | --- |
| `authentication` | `ldap.searchUserPassword` |
| `login` | `oidcProviders[].clientSecret`, `ldap.bindPassword` |
| `maven` | `repositories[].storageProvider.endpoint`, `.accessKey`, `.secretKey` (S3), `repositories[].proxied[].httpProxy`, `repositories[].proxied[].authorization.password` |

## Related pages

* [Configuration file and startup parameters](configuration-and-startup.md)
* [Storage providers](storage-providers.md)
* [Environment variables](environment-variables.md)
