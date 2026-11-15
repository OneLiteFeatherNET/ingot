# Proposal

## Why

An operator who runs Sonatype Nexus Repository 3 and wants to move to Ingot has no path that they can trust. Today
`docs/gitbook/how-to/import-from-other-repository-managers.md` tells them to export the files with some tool, copy them into a
directory or re-deploy them by hand, and then rebuild the search index. Nothing tells them which of their repositories, rules
and settings Ingot cannot carry over, nothing proves that every file arrived intact, and a copy that dies halfway has to be
started again and compared by eye. For a repository manager that holds the artifacts of a whole company, that is the point at
which people decide not to move.

The first thing an operator needs is not a button but an answer: "what will happen to my Nexus, and what will not". Then a copy
that can be stopped and started again, that checks every file against what Nexus says it holds, that refuses to overwrite
something that already differs, and that still finishes when one server of a cluster drops out in the middle of a long run.

This change is the backend and REST API of that path, for Maven only: connect, discover, dry run with a loss report, create the
repository definitions, copy the content of Maven hosted repositories, and verify. The wizard in the dashboard is a separate
change (`add-nexus-import-wizard`) that calls this API.

## What Changes

- **New plugin `nexus-import`.** A new module `reposilite-plugins/nexus-import-plugin`, code in the package
  `net.onelitefeather.ingot.nexusimport` (new code, built on the unchanged `com.reposilite.*` plugin API). The legacy 2.x
  `migration-plugin` is not touched. The plugin reads Nexus only through its REST API and never writes to Nexus.
- **Spike first.** Before any production code, the endpoints the MVP needs are checked against the Nexus 3 documentation and
  their response shapes are recorded as fixtures. The design below states the shapes we expect, and the spike confirms or
  corrects them.
- **Connect and discover.** URL and credentials in, version, edition hints, the permissions of the credential, the repositories
  with format, type and counts, and a format histogram out. Credentials are never stored in plain text and never logged.
- **Dry run.** A job that writes nothing and produces a loss report: one row per discovered object with a status
  (`MAPPED`, `PARTIAL`, `MANUAL`, `UNSUPPORTED`, `EXCLUDED`), the reason, the consequence and the action the operator takes. JSON
  and CSV download. One audited summary event per run.
- **Repository definitions.** Maven hosted, proxy and group definitions are created in the `maven` settings domain. A name that
  is taken is never touched: the plugin proposes another name. Access is never broadened.
- **Content copy and verification.** Maven hosted content is copied through the Nexus REST API and written through the existing
  Maven facade. Every file is checked against the checksums Nexus reports, and a sha256 is computed locally. A verify pass
  compares counts, path sets, sizes and checksums per repository.
- **Jobs that survive.** Job state is persisted in plugin-owned tables. A job is resumable and idempotent. In a cluster, one
  replica runs a job under a fenced lease, and when that replica loses the lease another replica continues from the committed
  progress without writing a file twice or counting one wrongly.
- **Conflict policy.** An existing path with a different checksum is a conflict. The default is `refuse`: nothing is
  overwritten, the conflict is recorded, and the repository does not become `VERIFIED`. `abort` and `overwrite` are explicit
  choices per job.
- **Switch.** The plugin jar is bundled in the image under `/app/bundled-plugins` and stays off until
  `INGOT_NEXUS_IMPORT_ENABLED=true` (fallback `REPOSILITE_NEXUS_IMPORT_ENABLED`). The loader of bundled plugins today knows only the
  metrics switch, so it gains a switch per jar.
- **Audit.** Each copied file produces a deploy audit event with the actor `migration` by default. `INGOT_NEXUS_IMPORT_AUDIT=summary`
  records the summary event only.
- **Documentation.** `docs/gitbook/reference/environment-variables.md`, `docs/gitbook/reference/http-api.md`,
  `docs/gitbook/reference/audit-events.md`, `docs/gitbook/reference/upgrade-changes.md`, a new how-to
  `docs/gitbook/how-to/import-from-nexus.md` listed in `docs/gitbook/SUMMARY.md`, and a pointer from
  `docs/gitbook/how-to/import-from-other-repository-managers.md`.

### Out of scope

- The dashboard wizard (`add-nexus-import-wizard`). This change has no frontend file.
- Users, roles, privileges, content selectors, LDAP and OIDC settings, tokens, cleanup policies, routing rules, tasks and the
  cut-over assistant. They are reported as `MANUAL` or `UNSUPPORTED` rows in the loss report, nothing more.
- Delta sync after cut-over, the `/repository/<name>` alias and per-job rollback.
- Proxy cache content. A proxy definition is created, its cached files are not copied.
- Formats other than Maven. Other formats are listed with component count and size, so that the format decision has data.
- Reading Nexus blob store files, the Nexus database, the Groovy script API or the Configuration API of 3.87+.
- Nexus 2.
- Importing `uploader`, `uploaderIp` and `lastDownloaded`. They are personal data or have no target; the loss report names them.
- A real Nexus in CI. The `sonatype/nexus3` image is under the Sonatype EULA, so tests run against a fake Nexus HTTP server
  that replays fixtures. See the design for what that does and does not prove.
- Legal and business model questions.
- A change to the default of any existing setting, and an SSRF policy for the rest of the server.

## Capabilities

### New Capabilities

- `nexus-import-connection`: connection, credential handling, TLS trust, discovery and the format histogram.
- `nexus-import-report`: the dry run, the loss report, the concept mapping and the creation of repository definitions.
- `nexus-import-content`: the copy of Maven hosted content, checksum verification, the conflict policy, the verify pass and the
  audit events.
- `nexus-import-jobs`: persisted, resumable and idempotent jobs, the fenced lease, the read-only interaction, throttling and the
  REST API.
- `nexus-import-packaging`: the plugin module, the bundled jar, the per-jar switch and the drop-in guarantees.

## Impact

- **Type and scope.** The change ships under `feat(migration)`. The branches use `test`, `feat`, `build` and `docs` commits with
  the scope `migration`. The pull request title, squash-merged onto main, is:

  `feat(migration): add nexus import for maven repositories`

- **Breaking marker.** No breaking change. Nothing has to change in a configuration file, volume, manifest or plugin. The plugin
  is off by default and creates no table until it is switched on. The title carries no `!` and no commit carries a
  `BREAKING CHANGE:` footer.
- **Behaviour that changes by default.** None. The image gains one more jar in `/app/bundled-plugins`, which is not read while the
  switch is off. The metrics switch keeps its meaning.
- **Opt-in.** `INGOT_NEXUS_IMPORT_ENABLED` and the other `INGOT_NEXUS_IMPORT_*` variables listed in the design.
- **Schema.** Four plugin-owned tables are created the first time the plugin starts, never otherwise. They are additive. Older
  Ingot and Reposilite ignore unknown tables, so a downgrade works, and the tables can be dropped by hand.
- **Core files touched.** `PluginLoader.kt` (switch per bundled jar), `AuditAction.kt` and `AuditPlugin.kt` (one new action and one
  bulk session registration), `settings.gradle.kts`, `Dockerfile` and `.dockerignore` (build and ship the jar). Each is
  additive, and the existing tests for them pass unedited.
- **Dependencies on other changes.**
  - `add-read-only-mode` (merged): jobs do not start and pause while the instance is read-only.
  - `fix-job-leases` (merged): the lease pattern. This change follows the run-store pattern of the search rebuild
    (`net/onelitefeather/ingot/search/runs/RunStore.kt`) and does not use `ScheduledJobClaims`, see the design.
  - `add-settings-secret-references` (merged): credentials can be given as `${env:NAME}` or `${file:PATH}` references.
  - `add-nexus-import-wizard` (separate author): calls this API. This change does not depend on it.
  - `add-image-extra-ca-bundle` (lands first): the image imports `INGOT_EXTRA_CA_CERTS` into the JVM trust store. The plugin has
    no CA setting of its own and relies on that trust store, so an operator with a private CA for Nexus uses the same variable
    as for every other outbound connection. Outside the image (a bare jar), the operator sets the JVM trust store as usual.
- **Code.** `reposilite-plugins/nexus-import-plugin` (new), `reposilite-backend` (loader and audit, four small files), build files,
  `docs/gitbook/`.

## Verification

- Targeted tests per commit, run through `heavy` with `--max-workers=2`.
- The three image checks from `CLAUDE.md` pass with the new jar in the image and the switch unset: an unmodified upstream compose
  file starts, an existing `/app/data` is adopted, and `PUID`, `PGID` and `REPOSILITE_OPTS` take effect.
- `openspec validate add-nexus-import --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-nexus-import` prints nothing.
