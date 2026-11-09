# Proposal

## Why

Ingot keeps its shared settings (S3, LDAP, OIDC, mirrors, frontend and the other domains) in the database by default. An
operator who keeps them in Git and mounts the file into the container with `--shared-configuration` gets a source that is
read once at start and never again. A commit to that file reaches the running instance only after a restart. The file
source also writes back: a save on the dashboard rewrites the file, which fails on a ConfigMap mounted read-only.

Checked on `origin/main`: `LocalSharedConfigurationProvider.kt:44` returns `false` from `isUpdateRequired` and `:47` returns
`false` from `isMutable`, so `SharedConfigurationPlugin.kt:62` never starts the ten second poll for this source. The write-back
is `LocalSharedConfigurationProvider.kt:34-36`.

## What Changes

- **Opt-in switch.** `INGOT_SHARED_CONFIGURATION_RELOAD` turns the file source into a watched, read-only source. The legacy
  spelling `REPOSILITE_SHARED_CONFIGURATION_RELOAD` is read as a fallback, and `INGOT_` wins when both are set. The default is
  off. Accepted values are `true` and `false`, ignoring case. Any other value stops the start and the message names the variable.
- **Change detection by content.** With the switch on, the provider keeps the SHA-256 of the content it last handed to the
  loader. The poll re-reads the file and compares hashes. A hash survives the kubelet's `..data` symlink swap and a rewrite
  that keeps the modification time, where a modification time check would not. The existing ten second poll runs for this
  source as well.
- **Broken documents keep the current settings.** A document that does not parse keeps the settings in force and logs one
  ERROR that names the source. That document is not parsed again on every poll: the next attempt happens when its content
  changes. Domains that fail validation keep their previous values, as they do at start.
- **File-managed settings are read-only.** With the switch on, a write to a known settings domain through
  `PUT /api/settings/domain/{name}`, or through `SharedConfigurationFacade.updateSharedSettings` used by plugins, answers
  `409` with a message that says the domain is managed by the settings file. Nothing is written and no `SETTINGS_CHANGED` audit
  event is recorded. Unknown domains keep answering `404`.
- **Database source unaffected.** With the switch on and no `--shared-configuration` path, the switch has no effect. The
  instance logs one WARN at start that says so.
- **Switch off.** Nothing changes: no poll for the file, the write-back on save stays as it is today.

### Out of scope

- The Helm chart wiring (`sharedConfiguration` values, the ConfigMap mounted as a directory, the checksum annotation). The N1
  chart part belongs to `add-chart-observability` in wave D.
- The dashboard showing those domains read-only. This change defines the backend contract (the `409`). Until the dashboard
  change lands, a save shows the `409` message.
- Reloading local configuration (port, database, pools, TLS) and `_FILE` secrets (`add-file-secrets`). Secret references
  inside shared settings are `add-settings-secret-references`.
- Changing the default write-back, or changing the database source.
- Propagating file changes over the cluster bus. Each instance reads its own mounted copy.

## Capabilities

### New Capabilities

- `shared-configuration-reload`: the opt-in switch, the change detection of the file source, the handling of broken
  documents, and the read-only rule for file-managed settings.

## Impact

- **Type and scope.** The change ships under `feat(configuration)`. The commits use `test`, `feat` and `docs`, all with the scope
  `configuration`. The pull request title, squash-merged onto main, is:

  `feat(configuration): reload shared settings from a mounted file`

- **Breaking marker.** No breaking change. Nothing has to be configured to keep today's behaviour, and the switch is off by
  default. The title carries no `!` and no commit carries a `BREAKING CHANGE:` footer. Operators who want the reload set
  `INGOT_SHARED_CONFIGURATION_RELOAD=true` on the instance, a new variable that is never required.
- **Behaviour that changes by default.** None. A broken document in the file source now logs one ERROR at reload, where a reload
  used to log a success line; the settings are unchanged in both cases. This is the same reload path that serves the database
  source.
- **Code.** `reposilite-backend/src/main/kotlin/com/reposilite/configuration/shared/` (provider interface, local provider,
  plugin, facade, the new switch reader) and the tests under `reposilite-backend/src/test/kotlin/com/reposilite/configuration/shared/`
  plus one integration test. `LocalConfiguration` is not touched.
- **Documentation.** `docs/gitbook/reference/configuration-and-startup.md` (the sentence that the file is not watched),
  `docs/gitbook/reference/environment-variables.md` (the new variable with its fallback), `docs/gitbook/reference/http-api.md`
  (the `409` on the settings domain PUT).
- **Dependencies.** None on another change. `add-chart-observability` (N1 chart part, wave D) and `add-settings-secret-references`
  (N3, wave D) depend on this change. No file overlaps with `add-file-secrets` (wave C), because the switch reads its own
  variable and leaves `LocalConfiguration` alone.

## Verification

- Targeted tests per commit, run through `HEAVY_SLOTS=1 heavy` with Gradle `--no-daemon --max-workers=2`.
- The integration test runs as `:reposilite-backend:integration --tests 'com.reposilite.configuration.SharedConfigurationReloadIntegrationTest'`.
- `openspec validate add-shared-configuration-reload --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-shared-configuration-reload` prints nothing.
