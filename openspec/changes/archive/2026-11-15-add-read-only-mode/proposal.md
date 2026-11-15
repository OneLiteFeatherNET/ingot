# Proposal

## Why

Ingot has no way to stop writes for a while. An operator who wants a consistent copy of the data has to stop the container, as
`docs/gitbook/how-to/back-up-and-restore.md` tells them to. A database failover, a storage migration or a blue/green cutover
has the same problem: during the switch, a deploy or a delete can land on one replica and be lost or split from the rest.

The cluster work already gives the building blocks. The database is authoritative for shared state, the cluster bus fans out
hints between replicas, and refusals already use RFC 9457 problem details. What is missing is one switch that every replica
honours, that defaults to off, and that a backup or a cutover can turn on without stopping anything.

The fix is an opt-in read-only mode. When it is on, every replica refuses writes with `503` and `Retry-After`, reads keep
working, and scheduled runs that write data do not start. The state is stored in the database, so a replica that restarts or
joins later reads the same state.

## What Changes

- **Switch.** New setting `INGOT_READ_ONLY` (with `REPOSILITE_READ_ONLY` as fallback and `-Dingot.readOnly` as system property).
  `true` forces read-only on that instance, whatever the stored state says. Unset or `false` leaves the stored state in charge,
  and the stored state is off by default.
- **Stored state.** The state is one entry in the existing configuration store, changed with a compare-and-save so that two
  toggles at once cannot both win. Each instance re-reads it from the database at most every five seconds. With the cluster bus
  configured, a change is also announced as a `read-only.changed` event, so the other replicas re-read at once. The database
  stays authoritative, so a lost event only delays the change by up to five seconds.
- **Refused requests.** While read-only is on, `PUT`, `POST`, `PATCH` and `DELETE` on the API and on the Maven protocol
  (deploy and delete) answer `503` with `Retry-After` and an RFC 9457 problem body. `GET` and `HEAD` keep working, and so do
  login, session refresh and the toggle itself. A caller without permission still gets `401` or `403`, as today.
- **Toggle.** `GET`, `POST` and `DELETE /api/admin/read-only` report, enable and disable the state. Changing it needs the
  maintenance area (`ingot.admin.maintenance`), which the cleanup endpoints already check. A change is written to the audit log.
  While the environment variable forces the state, a `POST` or `DELETE` answers `409` with a problem body that names the variable.
- **Scheduled runs.** Cleanup policy runs, the nightly search verify and the search index rebuild do not claim their run and do
  not start while read-only is on. They resume when it is turned off. Reads of the status and health endpoints are not affected.
- **Observability and dashboard.** A gauge `ingot_read_only` (1 or 0) per instance. The instance status gains a `readOnly` field
  and a source (`environment` or `stored`). The dashboard shows a banner while the state is on, and an administrator with the
  maintenance area gets a toggle.
- **Documentation.** `docs/gitbook/reference/environment-variables.md` for the setting, `docs/gitbook/reference/http-api.md` and
  `docs/gitbook/reference/api-errors.md` for the endpoint and the `503`, `docs/gitbook/reference/permission-nodes.md` (the
  maintenance area now gates the toggle, so its "no endpoint checks it" note changes), `docs/gitbook/reference/audit-events.md`
  for the new audit event, and a new how-to `docs/gitbook/how-to/freeze-writes-for-maintenance.md`. The backup how-to gets a
  read-only variant next to the stop-the-container steps, and `docs/gitbook/SUMMARY.md` lists the new page.

### Out of scope

- Read-only standby against a read-only database replica. That also needs the startup DDL skipped and is a separate change
  after `fix-schema-init-lock`.
- An online SQLite snapshot (`VACUUM INTO`), the backup drill and the Velero hooks. Those are `add-online-database-backup`, which
  uses this switch.
- Chart values for read-only mode. The chart does not change in this change, and the switch is reached through the existing
  environment passthrough.
- Blocking proxy cache fills. A `GET` on a proxy repository still stores the fetched file. A switch for that is not part of this change.
- A readiness change. The health endpoint `/api/status/health` stays `UP` in read-only mode. The separate readiness endpoint
  is in `add-readiness-endpoint`, which is not on main yet, and this change neither needs it nor changes it.
- Any change to the identity model, sessions or tokens.

## Capabilities

### New Capabilities

- `read-only-mode`: the switch, its precedence, the refused requests, the toggle, the scheduled runs, the audit event and the
  gauge.

## Impact

- **Type and scope.** The change ships under `feat(admin)`. The branches use `test`, `feat` and `docs` commits with the scope
  `admin`. The pull request title, squash-merged onto main, is:

  `feat(admin): add cluster-wide read-only mode`

- **Breaking marker.** No breaking change. Nothing has to change in a configuration file, volume, manifest or plugin. The switch
  is off by default, so a Reposilite installation pointed at Ingot behaves as before. The new endpoint and the new fields are
  additive. The title carries no `!` and no commit carries a `BREAKING CHANGE:` footer.
- **Behaviour that changes by default.** None. Every write path is unchanged until an operator turns the state on.
- **Opt-in.** `INGOT_READ_ONLY` and the stored state. The stored state is one new configuration entry, created the first time the
  toggle is used. The database schema does not change.
- **Dependencies on other changes.**
  - `add-cluster-bus` (merged): the `read-only.changed` event is one more `ClusterEventType` entry. Without a bus, a toggle is
    seen by the other replicas within five seconds through the database alone.
  - `add-problem-details` (merged): the `503` and `409` bodies use `application/problem+json`.
  - `fix-job-leases` (F5, not merged): the scheduled-run guard is placed before the claim. The guard does not need leases, but it
    touches the same scheduler files, so this change is ordered after `fix-job-leases`.
  - `add-cluster-metrics` (N8, not merged): the gauge uses the `ingot_` prefix that change introduces. It can land first, or this
    change adds the gauge on its own.
- **Code.** `reposilite-backend` (web dispatch, configuration, cluster, audit, scheduling, status, admin API),
  `reposilite-frontend` (status page banner and toggle), `docs/gitbook/`.

## Verification

- Targeted tests per commit, run through `heavy` with `--max-workers=2`.
- `openspec validate add-read-only-mode --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-read-only-mode` prints nothing.
