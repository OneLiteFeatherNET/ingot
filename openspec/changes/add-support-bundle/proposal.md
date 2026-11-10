# Proposal

## Why

When an Ingot instance misbehaves, the operator's first question to a maintainer or to the next shift is "what exactly is
running?". Today the answer is scattered. The version is in the header, memory and threads are on the dashboard, the database
is in `configuration.cdn`, the storage kinds are inside each repository's settings, the cluster members are on the status
page, the plugins are in a startup log line, and the recent log is in the console. Nothing collects them, and copying a
configuration into a ticket is dangerous because the local file carries the database password inline and the settings carry
S3 keys, LDAP passwords and mirror credentials.

Operators who come from Nexus expect a "Support" page with a system information view and a support ZIP they can attach to a
ticket. Ingot has no such place. The result is long back-and-forth ("please paste your settings") and, worse, secrets pasted
into public issues.

The fix is a System information page and a downloadable support bundle under Administration > Support. Both are for
administrators only, both are read-only, and both are built from a strict redaction contract: nothing is included unless a rule
says it is safe, and a test proves that a known secret never reaches the page or the zip.

## What Changes

- **System information.** A new admin endpoint `GET /api/admin/support/system-info` and a dashboard page at
  `/settings/system-info` show: Ingot version and build, JVM (vendor, version, heap, processors, uptime, input arguments with
  values redacted), operating system (name, version, architecture, container hint), database kind (host and port only, never
  user, password or URL query), storage kinds in use (filesystem or S3, per repository, with the bucket region and no keys),
  the cluster members and bus state as the status page already knows them, the enabled plugins with their versions, and the
  effective non-secret configuration (local and shared).
- **Support bundle.** `GET /api/admin/support/bundle` streams a zip with the same information as files plus the recent log
  lines of the answering instance, and a `manifest.json` that lists every file, the redaction rule set version and a count of
  the values that were redacted. The dashboard page `/settings/support` offers the download, shows what the bundle contains
  and says that secrets are removed. The bundle describes the instance that answers. A bundle of every replica is not part of
  this change.
- **Redaction contract.** Defined in the design and in the spec: an allowlist for local configuration, environment variables,
  system properties and JVM arguments; the existing settings masking for every shared settings domain, applied always and with
  secret references masked too; a structured redaction of the database setting and the cluster bus URL; value and pattern
  scrubbing of log lines; and a final guard that refuses to serve the bundle when a known secret value is still present.
- **Access.** Both endpoints need an authenticated principal with the console area (`ingot.admin.console`), the area that
  already guards the log stream. An anonymous caller gets `401`, a principal without the area gets `403`. The responses carry
  `Cache-Control: no-store`. The bundle download writes one audit event.
- **Opt-out.** New setting `INGOT_SUPPORT_BUNDLE_ENABLED` (fallback `REPOSILITE_SUPPORT_BUNDLE_ENABLED`, system property
  `-Dingot.supportBundle.enabled`). The default is `true`, because the endpoints are admin-only, read-only and new. `false`
  answers `404` on both endpoints and hides both pages, for operators who must not let any tool package configuration.
- **Documentation.** `docs/gitbook/reference/environment-variables.md` for the setting, `docs/gitbook/reference/http-api.md`
  for the endpoints, `docs/gitbook/reference/audit-events.md` for the audit event, `docs/gitbook/reference/dashboard-pages.md`
  for the pages, `docs/gitbook/reference/permission-nodes.md` for the console area now also gating the support endpoints, and a
  new how-to `docs/gitbook/how-to/collect-a-support-bundle.md` that states the redaction contract in plain words and tells the
  operator to still skim the zip before attaching it. `docs/gitbook/SUMMARY.md` lists the new page.

### Out of scope

- A bundle of all replicas in one download, a thread dump, a heap histogram and a metrics snapshot. They follow the same
  redaction contract and are a later change.
- Uploading the bundle anywhere, phone-home telemetry, and any outbound call. The bundle is only ever returned to the caller.
- Reading the log file on disk. Only the in-memory cached log (`cachedLogSize`, default 64 lines) is included, so the bundle
  never reads outside what the console already shows. Raising the default is not part of this change.
- A new permission area. See the design for why the console area is reused, and the open question in the design.
- Editing any setting from the Support pages, log level changes, and the repository admin pages.
- Navigation regrouping itself. It is `restructure-navigation` (on main) and `add-repositories-admin-page`, and this change only adds two pages to the registry.
- Legal and business questions about what support obligations a bundle implies.

## Capabilities

### New Capabilities

- `support-bundle`: the system information, the bundle, the redaction contract, the access rules, the opt-out and the audit event.

## Impact

- **Type and scope.** The change ships under `feat(admin)`, the scope the read-only mode change used. The branches use `test`,
  `feat` and `docs` commits with the scope `admin`. The pull request title, squash-merged onto main, is:

  `feat(admin): add system information page and support bundle`

- **Breaking marker.** No breaking change. Nothing has to change in a configuration file, volume, manifest or plugin. The
  endpoints and pages are new, and the opt-out is a new variable. The title carries no `!` and no commit carries a
  `BREAKING CHANGE:` footer.
- **Behaviour that changes by default.** Two new admin-only read endpoints and two new dashboard pages exist. No existing
  request, response, file or stored value changes. The audit log gains one new action.
- **Opt-out.** `INGOT_SUPPORT_BUNDLE_ENABLED=false`. No schema change.
- **Dependencies on other changes.**
  - `add-read-only-mode` (merged, `09e6ee52`): the downloads are `GET` and keep working while read-only.
  - `restructure-navigation` (on main, #76 and #82): the navigation registry the two pages are added to.
  - `add-repositories-admin-page`: defines the Administration groups Repository, Security, System and Support. The two pages are
    registered under Support at `/settings/system-info` and `/settings/support`, the group name and address style that change
    uses. This change builds on it if it lands first; otherwise the pages are registered in the existing registry as described
    in the design, and move into Support when that change lands.
  - `add-problem-details` (merged): refusals use `application/problem+json`.
  - `add-cluster-bus` (merged): the cluster members come from the existing status source and add no new bus message.
  - `add-structured-logging` (not merged): log lines may then be JSON. The scrubber works on the text of the line, so it does
    not depend on the format, and a test covers both.
- **Code.** `reposilite-backend` (new `net.onelitefeather.ingot.support` package, audit, status, settings secrets reuse),
  `reposilite-frontend` (two pages), `docs/gitbook/`.

## Verification

- Targeted tests per commit, run through `heavy` with `--max-workers=2`.
- `openspec validate add-support-bundle --strict` passes.
- `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-support-bundle` prints nothing.
