# Design

## What exists on main, and what is missing

Verified against this worktree:

- The settings secret masking exists: `SettingsSecrets.mask(type, tree, keepReferences)` in
  `reposilite-backend/src/main/kotlin/com/reposilite/configuration/shared/SettingsSecrets.kt:90`, driven by the `@Sensitive`
  annotation (`configuration/shared/api/SecretAnnotations.kt:27`). It masks every non-empty `@Sensitive` string with
  `********` (`HIDDEN_SECRET`, line 38). `SettingsSecrets.redactDocument` (line 110) masks a whole shared document for logging
  and withholds domains without a known type. `SharedConfigurationFacade.kt:168` already uses it for a debug log. Today only the
  settings endpoint uses `mask`, and only when `hideSettingsSecrets` is on (`SettingsEndpoints.kt:122`, default `false`,
  `LocalConfiguration.kt:163`). The bundle must mask regardless of that flag.
- `@Sensitive` is declared on S3 keys and endpoint (`storage/s3/S3StorageProviderSettings.kt:43-49`), mirror passwords and
  proxies (`maven/application/MavenSettings.kt:294,323`), the LDAP search password (`LdapLoginSettings.kt:40`), the OIDC client
  secret (`OidcSettings.kt:48`) and one authentication setting (`AuthenticationSettings.kt:58`).
- The local configuration is NOT covered. `LocalConfiguration.database` (line 55 onward) is one string that carries the user
  and password positionally (`mariadb host:3306 db user password`, or a JDBC URL), and `keyPassword` (the TLS key password) is a
  plain reference. Neither is `@Sensitive`. Reusing the shared masking alone would leak both. This is why local configuration
  uses an allowlist, not masking.
- `com.reposilite.shared.maskSecret` (`shared/Secrets.kt`) keeps the first and last character of a secret. It is not reused:
  a support bundle must not reveal any character.
- `ClusterBusUrl.redacted()` (`cluster/ClusterBusUrl.kt:39`) already removes bus credentials, and `ClusterStatusView` uses it.
- Log lines: `ReposiliteJournalist.cachedLogger` (`ReposiliteJournalist.kt:66`) holds the last `cachedLogSize` lines in memory
  (default 64) and the console reads it (`ConsoleSseHandler.kt:85`). Lines are free text, so secrets can appear in them.
- Plugins: `Extensions.getPlugins()` (`plugin/Extensions.kt:82`) returns each plugin's metadata (name, version, dependencies).
- Status: `InstanceStatusResponse` (`status/api/StatusApi.kt:22`) already carries version, uptime, memory, threads, read-only
  state and the cluster section.
- Missing: any aggregate view, any export, any log redaction, and any route for them. The only admin routes that touch
  similar data are `/api/status/instance` (dashboard area) and `/api/console/log` (console area).

## Redaction contract (version 1)

The contract is the center of this change. Every file written to the zip, and every field of the JSON page, passes through one
`Redactor` and is described by exactly one rule. A field no rule describes is not written. The rules:

1. **R1 Allowlist first.** Local configuration, environment variables, system properties and JVM arguments are written only
   when an allowlist names the key. Everything else appears as its name with the value `[withheld]`, so a reader can see that
   it exists. The allowlist is a constant in the code and a table in the reference page.
   - Local configuration allowlist: `hostname`, `port`, `sslEnabled`, `sslPort`, `enforceSsl`, `webThreadPool`, `ioThreadPool`,
     `databaseThreadPool`, `compressionStrategy`, `idleTimeout`, `bypassExternalCache`, `cachedLogSize`, `defaultFrontend`,
     `basePath`, `hideSettingsSecrets`, `searchEnabled`, `searchEngine`, `schemaLockTimeout`. `keyPassword` and `keyPath` are
     withheld. A new local key is withheld until someone adds it, which is the safe default.
   - Environment: values only for `PUID`, `PGID`, `REPOSILITE_FORCE_CHOWN`, `INGOT_READ_ONLY`, `INGOT_METRICS_ENABLED`,
     `INGOT_SUPPORT_BUNDLE_ENABLED` and their `REPOSILITE_` spellings. `JAVA_OPTS` and `REPOSILITE_OPTS` are listed through the
     JVM argument rule. Every other variable is name only with `[withheld]`.
   - JVM arguments: `-Xmx`, `-Xms`, `-Xss`, `-XX:` flags and `--add-opens` style flags keep their value. Any `-D` property
     keeps its value only when its key starts with `ingot.` or `reposilite.` and does not match R6, and is withheld otherwise.
2. **R2 Shared settings use the existing masking, always.** Each shared domain is serialized and passed through
   `SettingsSecrets.mask(type, tree, keepReferences = false)`. The flag `hideSettingsSecrets` is ignored. `keepReferences`
   is `false` on purpose: a `${env:NAME}` or `${file:PATH}` reference is masked too, because the name of a variable or file is
   itself information the bundle does not need. A domain without a known type is withheld completely, as
   `redactDocument` does (line 110).
3. **R3 Database.** The database setting is reduced to its kind (`sqlite`, `mariadb`, `mysql`, `postgresql`, `h2`, or `jdbc:<kind>`),
   and for networked kinds the host and port. The user, the password, the file path of an embedded database beyond its file name,
   and every JDBC URL query parameter are dropped. If the string cannot be parsed, only `unparsed` is written.
4. **R4 Cluster bus.** The URL is written as `ClusterBusUrl.redacted()` and nothing else from the bus settings.
5. **R5 Never included.** Access tokens, session and refresh material, password hashes, the contents of any table, artifact
   files, request bodies and the audit log are never read by the support code. The collector has no dependency on those facades,
   which a test asserts structurally by checking the constructor parameters.
6. **R6 Log lines are scrubbed twice.** First by value: the set of every secret value known to the instance (all `@Sensitive`
   values of all shared domains, resolved through `SettingsSecrets.transformSensitive`, the local database password, the TLS key
   password and the bus credentials) is replaced by `[redacted]`, longest value first, for values of at least four characters.
   Then by pattern, case-insensitively: `Authorization: <anything>` and `Bearer <token>` and `Basic <b64>`, `user:pass@host`
   in a URL, and `password`, `passwd`, `secret`, `token`, `apikey`, `access_key`, `secret_key` followed by `=` or `:` and a
   value. Both passes work on the plain text of a line, so a structured JSON line is handled too.
7. **R7 Final guard, fail closed.** After assembly the redactor searches the complete output for every known secret value of
   R6 (including values under four characters when they are not a common word substring of the manifest itself). If one is
   found, no zip is served: the response is `500` with a problem body that says "support bundle refused: redaction check
   failed", the event is logged at `error` without the value, and an audit event with `success = false` is written. A bundle is
   never served partially.
8. **R8 Honest statement.** The how-to says the contract removes known secrets and obvious patterns and that an operator must
   still look through the zip before attaching it to a public issue, because free-text log lines can contain data no rule
   can recognize, such as a customer name in a path.

Why a value scrub and not only masking: a log line such as a failed S3 call may contain the endpoint or key that settings
masking would hide on the settings page. Masking the settings tree but leaving the same value in a log line would defeat the
purpose.

Alternatives considered:

- Denylist of secret-looking keys. Rejected: a new key with a new name leaks by default. The allowlist leaks nothing by default.
- Reusing `maskSecret`. Rejected, see above.
- Adding `@Sensitive` to `LocalConfiguration.database` and `keyPassword` and masking the local file the same way. Rejected for
  this change: the annotation targets property getters of shared settings types, and the local configuration is a different
  mechanism (reference properties read by CDN). Changing it is a larger change than the support page needs, and the allowlist
  covers it.

## Where the information comes from

One `SupportInformationCollector` builds a plain data object from facades that already exist, each read through a narrow
function so tests pass fakes: version (`VERSION`), JVM and OS (`System` and `ManagementFactory`), uptime and threads
(`StatusFacade`), the local configuration (`LocalConfiguration`), the shared domains
(`SharedConfigurationFacade.getDomainNames` and `getDomainSnapshot`), storage kinds (derived from the repository settings, kind
and bucket region only), plugins (`Extensions.getPlugins`), the cluster section (the existing `ClusterStatusSource`), and the
cached log (`ReposiliteJournalist.cachedLogger`). Build information is the version plus the build timestamp and commit when the
jar manifest carries them. If it does not, the fields read `unknown`. No new build step is part of this change.

The collector takes a `java.time.Clock` for the generation time, so the manifest is reproducible in tests.

## Endpoints and access

- `GET /api/admin/support/system-info` returns the JSON of the page.
- `GET /api/admin/support/bundle` returns `application/zip` with `Content-Disposition: attachment; filename="ingot-support-<instance>-<yyyyMMdd-HHmmss>.zip"`
  and `Cache-Control: no-store`.
- Both use `managerOnly("console")` (`shared/ContextDsl.kt:97`). The console area is the one that already lets a principal read
  the log stream, so no new capability is granted by the log part. The bundle adds the redacted configuration, which an
  `ingot.admin.settings` holder could already read through the settings endpoint. Anonymous callers get `401` before any
  information is read, and the routes are not under the repository path space.
- A bundle is built in memory up to a cap of 8 MiB, then streamed. A second request while one is being built waits on a
  single permit with a short bound and then answers `429`, so repeated downloads cannot be used to load the instance.
- Both are `GET`, so read-only mode does not refuse them (`ContextDsl.kt:117`).

Why the console area and not a new `support` area: a new area text is rejected by an older replica during a rolling upgrade or a
rollback (`NodePattern.ADMIN_AREAS`, `identity/model/NodePattern.kt:173`), so a grant stored by the new version could stop an
older one from reading it. Reusing `console` adds nothing to that list. The admin-area enforcement work (item N7 of the roadmap)
may later split the area. See the open question.

## The pages

The two pages are frontend views at `/settings/system-info` and `/settings/support`, registered in the navigation registry under
Administration, Support, the group that `add-repositories-admin-page` defines (Support holds Console, Maintenance and Analytics
there). This change builds on that change if it lands first. If it has not landed, the pages are registered in the registry that
`restructure-navigation` left on main, in its Administration area, and move into Support when `add-repositories-admin-page` lands.
The segment `settings` is already served by the dashboard routes
(`reposilite-backend/src/main/kotlin/com/reposilite/frontend/DashboardPaths.kt`), so no new top-level segment appears and no
repository name can be shadowed. The names `system-info` and `support` must not collide with a settings domain id, and a test
over the domain list asserts that. When the opt-out is on, the pages are absent from the registry and a direct visit shows the
not found view.

## Audit

One new `AuditAction` entry, `SUPPORT_BUNDLE_DOWNLOADED`, with details `{files, redactions}` and the actor of the request. The
JSON page view is not audited, because it is as readable as the status page. The reference table in `audit-events.md` gains a row.

## Consequences

- A bundle describes one replica. Cluster members appear in it, but their own logs do not.
- The log part is as long as `cachedLogSize` (64 lines by default). An operator who needs more raises that setting. This is
  documented, not changed.
- The allowlist means a future local setting is invisible in the bundle until it is added. The test that lists
  `LocalConfiguration` properties and requires each to be allowlisted or withheld by name makes that an explicit decision in
  the pull request that adds a setting.
- Reading every shared domain at once is slightly more work than one status call. It happens only on request, behind the
  single permit.
