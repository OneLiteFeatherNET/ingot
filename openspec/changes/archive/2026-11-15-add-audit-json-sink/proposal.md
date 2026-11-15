# Proposal

## Why

An operator who must show an auditor, or feed a SIEM, who changed what on the artifact store has two options today, and
neither fits a collector. The audit log lives in the database table `audit_events`. Reading it means calling `GET /api/audit`
with an admin credential or exporting a CSV by hand (`AuditEndpoints.kt`, `AuditFacade.writeCsv`). A collector such as Loki,
Elasticsearch, Splunk or a cloud log service reads files and container output, not a paged admin API. So the evidence sits
in a place the security team does not watch, and it dies with the 90 day retention (`AuditSettings.kt:14`) unless somebody
exports it on a schedule.

There is a second gap in the trail. Creating or deleting a whole repository is the most consequential administrative act on
the store, yet it has no event of its own. A repository is one entry in the `repositories` list of the `maven` settings
domain, so removing one writes only `SETTINGS_CHANGED` with `domain=maven` and the field path `repositories`
(`SharedConfigurationFacade.kt:354-361`, `SettingsChanges.kt:25-43`). A reviewer cannot filter for "who deleted repository
X" and cannot tell from the event which repository it was.

## What Changes

Everything is opt-in. With nothing set, the server writes no sink output and the only visible difference is two new audit
action names that appear when a repository is created or deleted.

- **JSON-lines audit sink.** When enabled, every audit event is also written as one JSON object per line, to standard
  output, to a file, or to both. It is off by default. The line has a stable schema with a `schema` name and an integer
  `version`, so a collector rule does not break when the server is upgraded.
- **Switches.** `INGOT_AUDIT_SINK_STDOUT=true` writes to standard output. `INGOT_AUDIT_SINK_FILE=<path>` appends to that
  file. Each reads the `INGOT_` name first and falls back to the `REPOSILITE_` spelling.
- **Never on the request path.** Events go through a bounded in-memory queue to one writer thread. A full queue or a failing
  disk drops the line, counts it, and logs one rate-limited warning. The request, the deploy and the database row are never
  delayed or failed by the sink.
- **Rotation is the collector's job.** The file is append-only. The sink reopens it when it is renamed or removed, so
  `logrotate` with `create`, a Kubernetes log agent or a sidecar work without a signal.
- **High availability.** Each replica writes the events it raised, tagged with a `node` field. The collector merges them.
  The database table stays the shared, queryable record.
- **Distinct repository events.** `REPOSITORY_CREATED` and `REPOSITORY_DELETED` are emitted when a save of the `maven`
  settings domain adds or removes a repository id. They are written in addition to the existing `SETTINGS_CHANGED` event.
- **Personal data.** The sink honours `recordActorAddress`. The documentation says plainly which fields are personal data,
  that the sink moves them outside the database and its retention, and that erasure in the collector is the operator's duty.
- **Documentation.** `docs/gitbook/reference/audit-events.md`, `docs/gitbook/reference/environment-variables.md` and a new
  how-to `docs/gitbook/how-to/forward-audit-events-to-a-siem.md`.

### Out of scope

- Download auditing (roadmap D13). It needs per-repository opt-in and aggregation, and a synchronous write on the download
  path is exactly what this change avoids. A separate change.
- Built-in rotation, compression or retention of the sink file. The collector or `logrotate` owns it.
- Syslog, OTLP, HTTP or Kafka sinks, and tamper evidence such as hash chaining (roadmap L5). The line schema is the base for
  them.
- Audit events for tasks and storage operations, and for settings loaded from `configuration.shared.json` at start or by the
  shared configuration reload. Only a save through the settings API is audited today, and that does not change.
- Changing the table `audit_events`, its retention, the API, the CSV export or the dashboard page. The new action names show
  in them unchanged because the action column is text (`SqlAuditRepository.kt:54`, 64 characters).
- Shipping the sink output to the cluster bus, and a sink setting in the dashboard.
- Legal advice on GDPR. The change documents the data and the controls, nothing more.

## Capabilities

### New Capabilities

- `audit-json-sink`: the opt-in JSON-lines output, its schema, its switches, its failure behaviour, rotation and the
  per-replica rule.
- `audit-repository-events`: the `REPOSITORY_CREATED` and `REPOSITORY_DELETED` audit actions.

## Impact

- **Type and scope.** The change ships under `feat(audit)`. The pull request title, squash-merged onto main, is:

  `feat(audit): add a json lines audit sink and repository events`

- **Breaking marker.** No breaking change. No operator has to change a configuration key, variable, volume, manifest or
  plugin. The title has no `!` and no commit carries a `BREAKING CHANGE:` footer. A consumer that reads the audit API or the
  CSV and rejects unknown action names must tolerate two new values; the action list is documented as open.
- **Defaults.** Unchanged. No sink output without a switch. Retention, `recordActorAddress`, the table and the API are as today.
- **Dependencies.** None required. `add-structured-logging` also writes JSON to standard output; the audit lines carry
  `"schema":"ingot.audit"` so a collector can tell the two apart (see `design.md`). `add-chart-observability` may later expose
  the two variables as chart values; that belongs there.
- **Code.** `reposilite-backend` (`audit/`, `configuration/shared/SharedConfigurationFacade.kt`) and `docs/gitbook/`.

## Verification

- Red tests first, one behaviour each, run through `heavy` with `--max-workers=2`.
- `openspec validate add-audit-json-sink --strict` passes.
- The Container images job stays green with no `INGOT_AUDIT_SINK_*` variable set, which covers the upstream compose file and
  the upgrade rehearsal.
- `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-audit-json-sink` prints nothing.
