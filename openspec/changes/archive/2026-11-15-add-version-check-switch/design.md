# Design

## What exists today

The check is lazy and hourly. `StatusFacade` holds a memoized supplier with a one hour expiry
(`reposilite-backend/src/main/kotlin/com/reposilite/status/StatusFacade.kt:100-117`). The first call happens when something asks
for the latest version: the instance status response (`StatusFacade.kt:151`) or the `status` console command
(`StatusFacade.kt:189-190`, `StatusCommand.kt:38`). It is not made at startup. In the test environment it never calls out
(`testEnv`, `StatusFacade.kt:103`).

The call is a plain `GET` of the address from `StatusPlugin.kt:64-66`, by default
`https://api.github.com/repos/OneLiteFeatherNET/ingot/releases/latest`, through `panda.utilities.IOUtils.fetchContent`. Ingot
adds no body, no header, no instance identifier and no version number. What the JDK connection adds on its own (its default
`User-Agent`, the source address of the host) cannot be removed by Ingot. A task checks this with a header capturing server
instead of trusting this paragraph.

It can be turned off only through a system property (`StatusFacade.kt:119-124`):
`ingot.status.remote-version-check`, then `reposilite.status.remote-version-check`, default `true`. Both are read on every
refresh, and the property reader treats any value except `true` as off. The variable `INGOT_STATUS_REMOTE_VERSION_CHECK` does
not exist. `grep -rn REMOTE_VERSION` over the backend finds nothing. So the report's roadmap item N2 needs the `feat` part.

The existing documentation names the property at `docs/gitbook/reference/environment-variables.md:288` and at
`docs/gitbook/how-to/monitor-status-and-failures.md:65`.

## The switch

A new `RemoteVersionCheckSetting` object in `com.reposilite.status` follows `UpstreamStatusMode`
(`reposilite-backend/src/main/kotlin/com/reposilite/shared/http/UpstreamStatusMode.kt`): a pure `read(properties, environment)`
with both maps injected, so tests never touch the process environment.

Order, first present and non-empty wins:

1. system property `ingot.status.remote-version-check`
2. environment variable `INGOT_STATUS_REMOTE_VERSION_CHECK`
3. system property `reposilite.status.remote-version-check`
4. environment variable `REPOSILITE_STATUS_REMOTE_VERSION_CHECK`
5. default `true`

This matches the order of `UpstreamStatusMode` and the rule in `CLAUDE.md`: the `INGOT_` spelling is preferred and the
`REPOSILITE_` spelling is a valid fallback. A system property outranks a variable, as everywhere else in Ingot.

Value rules, chosen so that nothing existing changes:

- Properties keep their meaning: `true` is on, everything else is off (`getBooleanProperty`, `StatusFacade.kt:123-124`).
  Changing that would turn a deployment that has `-Dingot.status.remote-version-check=no` back on.
- Variables accept `true` and `false` without regard to case. An empty value counts as not set. Any other value is ignored
  and one warning names the variable, so a typo falls back to the default instead of stopping the server, because the value
  is read after the server is up.

`StatusFacade` takes the setting as a new defaulted constructor parameter, `remoteVersionCheck: () -> Boolean`, whose default
reads the process. The existing tests construct the facade with named arguments and keep compiling. The supplier is called on
each refresh exactly as the property is today, so the behaviour of reading late is unchanged.

Alternatives considered:

- Make the default off for new installs. Rejected by the maintainer and by `CLAUDE.md`: the default behaviour is part of the
  drop-in contract.
- Treat a variable like a property (anything but `true` is off). Rejected: a new variable has no compatibility burden, and
  `yes` or `1` silently switching the check off is a surprise in the other direction. The warning makes the typo visible.
- Add the variable to the local configuration (`INGOT_LOCAL_*`). Rejected: the local configuration file would then own a
  key that the status plugin does not otherwise read, and `INGOT_LOCAL_` keys are written into `configuration.cdn`.
- Chart value. Not needed: the chart passes arbitrary environment variables, and a chart value is a separate change.

## The outbound statement

Every connection found in the code, with the place that opens it. The page documents exactly these, and a task re-greps before
the page is written, because the list is only true on the commit it was made from.

| Connection | Trigger | Default | Turn off | Code |
| --- | --- | --- | --- | --- |
| Version check, `GET` to GitHub releases API | An instance status request or the `status` command, at most once per hour | On | `INGOT_STATUS_REMOTE_VERSION_CHECK=false` or the property | `StatusFacade.kt:100-117`, `StatusPlugin.kt:64-66` |
| Mirror and proxy repositories, `HEAD` and `GET` to the configured hosts | A client request for a file the proxy repository does not hold | Off: the default repositories `releases`, `snapshots`, `private` have no proxied entry | Remove the proxied entry | `HttpRemoteClient.kt:89,127`, `MavenSettings.kt:46-48,175` |
| OIDC and GitHub login: discovery document, key set, token exchange, user lookup | A login attempt or the settings connection test for a configured provider | Off: no provider configured | Remove the provider | `OidcMetadata.kt:143`, `OidcLoginService.kt:247`, `GitHubLogin.kt:70,126` |
| LDAP bind and search | A login with LDAP enabled | Off | Disable LDAP | `LdapAuthenticator.kt:118-127` |
| S3 requests to the configured endpoint | Any read or write on an S3 backed repository | Off: filesystem storage is the default | Use filesystem storage | `S3StorageProviderFactory.kt:52-86` |
| Database connection (MariaDB, MySQL, PostgreSQL) | Startup and every query | Off: the default is a local SQLite file | Use SQLite or H2 | database plugin, HikariCP |
| NATS cluster bus | Startup, when `INGOT_CLUSTER_URL` is set | Off | Unset the URL | `NatsClusterTransport.kt:198` |

Facts the statement also carries, each confirmed by a grep or a test at apply time:

- No telemetry, usage reporting, analytics, licence or entitlement call exists, and no code path reads a licence file.
- The migration plugin reads local data only. The Prometheus plugin only answers scrapes. Neither opens a connection.
- The dashboard loads no script, style or font from another host. One placeholder default points at an image on
  `avatars.githubusercontent.com` (`reposilite-frontend/src/store/placeholders.js:51`), which the visitor's browser fetches,
  not the server, and which `INGOT_` branding settings replace.
- OIDC requests never follow redirects and time out after five seconds (`OidcHttp.kt:40-62`).
- Mirror requests can use an environment proxy only with `INGOT_HTTP_USE_ENV_PROXY=true`
  (`MirrorEnvironment.kt:34-38`), and a per mirror `httpProxy` wins.
- The base image and the container healthcheck call only `localhost`. Image pulls and package installs happen at build time.

The page is a reference page in the Diataxis sense: a table, then one short section per row. It gives the hosts an operator
would put on an egress allow list (`api.github.com` for the version check, and the hosts the operator configured for the rest).

## Consequences

- Operators on environment-only deployments can switch the call off without touching a start command. Nobody else notices.
- The statement becomes a promise. A later change that adds an outbound call has to update the page. A reviewer checklist
  line for that is out of scope here, and the risk is named in the pull request description.
- The environment variable and the system property can disagree. The property wins, and the page says so.
