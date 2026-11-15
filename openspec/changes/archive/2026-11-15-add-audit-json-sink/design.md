# Design

## What exists on main

- `AuditFacade.record` is the single place every event passes (`audit/AuditFacade.kt:50-73`). Producers emit an `AuditEvent`
  on the plugin event bus, and `AuditPlugin` forwards it (`AuditPlugin.kt:39-41`) inside `recordSafely`, which already swallows
  and logs any exception (`AuditPlugin.kt:78-84`). Deploys and deletes come from `DeployEvent` and `DeleteEvent`
  (`AuditPlugin.kt:43-61`).
- There is no file or stream output. `reference/audit-events.md` says the audit log is the table `audit_events` only.
- `LOGIN_FAILED` rows are folded per actor, address and minute in the database (`AuditFacade.kt:80-110`).
- `recordActorAddress` already drops the address before the insert (`AuditFacade.kt:52`).
- No audit row carries a node id. The cluster instance id exists: system property `ingot.cluster.instance`, then
  `INGOT_CLUSTER_INSTANCE_ID`, then the host name (`cluster/ClusterSettings.kt:48-56`, `systemHostName` at line 41).
- A repository is an entry of `MavenSettings.repositories`, keyed by `RepositorySettings.id` (`maven/application/MavenSettings.kt:44,125-129`).
  The only audit of a change is `SettingsChanges.kt`, which compares objects field by field and arrays as a whole, so a
  removed repository shows as the path `repositories` with no id.
- Every `INGOT_` read in the code takes an environment map as a parameter with `System.getenv()` as the default (for example
  `ShutdownSettings.read`, `RequestLimitSettings`), which keeps tests off the process environment.

## Decision 1: tap `AuditFacade.record`, before the insert

The sink is a second consumer inside `record`, called first and wrapped in its own try block. Reasons:

- One place sees every event, including the ones that come from `AuditEvent` and the ones that `AuditPlugin` builds from
  repository events. No producer changes.
- Called before the insert, the line is still written when the database is down. That is the case an auditor cares about.
- The row id and the folded `occurrences` do not exist yet at that point, so the line has no `id`. That is intended: a line is
  an event, not a row. `occurrences` is always `1`.

Alternatives considered:

- Read the table and tail it. Rejected: it adds polling, needs a cursor per replica, and still stops when the database stops.
- A tinylog or SLF4J logger named `audit`. Rejected: the audit text would travel through the log level and format settings,
  the file writer opt-out of `add-structured-logging`, and the cached console logger that the dashboard shows. One setting
  change in logging could silently drop evidence.

## Decision 2: line schema

One JSON object, UTF-8, `\n` terminated, no pretty printing, no embedded raw newline (the encoder escapes them). Field names
are stable, in this order, and every field is always present. A value that does not apply is `null`.

| Field | Type | Notes |
| --- | --- | --- |
| `schema` | string | Always `ingot.audit`. Lets a collector pick audit lines out of a shared stream. |
| `version` | integer | `1`. See the compatibility rule below. |
| `time` | string | ISO-8601 instant in UTC, from the injected `Clock` at the moment of recording. |
| `node` | string | The cluster instance id (system property, `INGOT_CLUSTER_INSTANCE_ID`, host name). Names the replica that wrote the line. |
| `action` | string | An `AuditAction` name. |
| `actor` | string | As in the table. |
| `actorAddress` | string or null | `null` when `recordActorAddress` is `false` or the address is unknown. |
| `repository` | string or null | |
| `path` | string or null | |
| `details` | object or null | The same map the table stores, as a JSON object, not as an escaped string. |
| `success` | boolean | |
| `occurrences` | integer | Always `1`. The field exists so one consumer can read both the sink and the CSV. |

Compatibility rule, written into the reference page: adding a field or an action name does not change `version`. Renaming,
removing or retyping a field, or changing the meaning of one, raises `version`, and the old version is not written alongside.
Consumers must ignore unknown fields. This is the stability promise, so the first red test pins the exact field list.

## Decision 3: switches

| Variable | Fallback | Meaning |
| --- | --- | --- |
| `INGOT_AUDIT_SINK_STDOUT` | `REPOSILITE_AUDIT_SINK_STDOUT` | `true` writes the lines to standard output. Any other value, or unset, does not. |
| `INGOT_AUDIT_SINK_FILE` | `REPOSILITE_AUDIT_SINK_FILE` | Path of the file to append to. Blank or unset: no file. |

Both, one or none can be set. Both unset is the default and starts no thread and opens nothing. `INGOT_` wins when both
spellings are set, as for every other variable. The names are new, so the legacy fallback exists for symmetry with the rule,
not because an old installation could carry it.

A file that cannot be opened at start does not stop the server. It is reported once as a warning and retried on the next
write attempt (see decision 5). A missing parent directory is created. This matches the rule that auditing must never fail
the action it describes (`AuditPlugin.kt:74-77`) and the contract that a start must not need new configuration.

Standard output shares the stream with the logger. The audit lines are recognisable by their `"schema":"ingot.audit"` prefix,
and the how-to shows the collector filters for Loki and Vector. An operator who wants a clean stream sets only the file.
With `INGOT_LOG_FORMAT=json` from `add-structured-logging` the two kinds are both JSON and still told apart by `schema`.

## Decision 4: never block the request path

- `record` hands the line to `AuditSink.offer`, which is a non-blocking `offer` on a bounded `ArrayBlockingQueue` (capacity
  10 000 lines). It never waits and never throws.
- One daemon writer thread drains the queue, writes and flushes per batch.
- A full queue drops the new line and increments a dropped counter. A write failure drops the batch and increments a failed
  counter. At most one WARN per minute is logged with both counters and the first error message, using the injected `Clock`.
  The line itself is never copied into the log, so a failure cannot leak an event into the wrong place.
- The database insert happens whether or not the sink succeeded, and the reverse.
- On shutdown the writer drains what it has within a bounded wait, then closes the file.

The counters are held in the sink and logged. Exposing them as metrics belongs to `add-cluster-metrics` and is left out here;
the design keeps them as plain atomic longs so that change can read them.

Alternatives considered: a synchronous write on the caller thread (rejected: a slow or full disk would stall a deploy, which
is the property this change must not have), and an unbounded queue (rejected: a stuck disk would grow the heap until the
process dies).

## Decision 5: rotation, reopening and the file

The sink opens the file with `CREATE` and `APPEND`. It does not rotate, compress or delete. Before a batch it checks, at most
once per second by the injected clock, whether the path still resolves to the file it holds (file key and existence). If not,
it reopens. That makes `logrotate` with `create`, and an agent that renames the file, work with no signal. `copytruncate` also
works because the descriptor is in append mode. The file mode is `0640` where the filesystem supports POSIX permissions, since
the content is personal data.

Under a read-only root filesystem the path must be on a writable volume, as for the log file. The how-to says so. Standard
output needs no volume, which is why it is the recommended shape in Kubernetes.

## Decision 6: high availability

Every replica writes the events that replica raised. An admin action lands on one replica, so there are no duplicates and no
coordination. The table stays the shared record; the sink is a per-replica stream and the collector merges by `time`. Lines
are not ordered across nodes, and the how-to says so. The `node` field is the instance id the cluster bus already uses, so
the same name appears in both places. A scheduled event (`CLEANUP_RUN` with actor `system`) is written by the replica that
ran it, because the job is claimed by one replica (`AuditFacade.kt:155`).

## Decision 7: repository create and delete

`SharedConfigurationFacade.recordSettingsChange` gets one more step for the domain named `maven`: it reads the ids in
`repositories` from the before and after trees, and emits `REPOSITORY_DELETED` for each id only in before and
`REPOSITORY_CREATED` for each id only in after, in addition to `SETTINGS_CHANGED`. Both new events set `repository` to the id
and carry `details` with `type` (the declared type, or `null`) and `visibility`. A renamed id is a delete plus a create,
because the id is the key (`@SettingsKey`).

Why in the facade and not in the maven plugin: the facade already has the before and after documents and the actor, and every
save path (single domain, patch, import) goes through `recordSettingsChange` (`SharedConfigurationFacade.kt:209,274`). The
maven plugin sees only the resulting settings reference.

`SETTINGS_CHANGED` is kept unchanged. Consumers that read it keep working, and the new events add precision. The action
column allows 64 characters and `REPOSITORY_DELETED` has 18, so no schema change is needed. The enum gets the two names
appended at the end, because the name is stored (`AuditAction.kt` comment).

Open item, not a blocker: a repository removed by editing `configuration.shared.json` and restarting, or by the shared
configuration reload, is not audited. That is the same limit as every other settings change today and is out of scope.

## GDPR and personal data

The audit event is personal data wherever it names a person. The change does not add data, it adds a copy, so the notes below
are part of the deliverable and go into the reference page and the how-to.

| Field | Personal data when |
| --- | --- |
| `actor` | It is a user name, an e-mail style login or an OIDC subject. A machine account or token name usually is not. |
| `actorAddress` | Always for a person. An IP address is personal data. |
| `details` | It names principals (`principal`, `team`, member lists). |
| `path`, `repository` | Rarely, when a coordinate embeds a name. |

What the operator gets, and what remains theirs:

- The sink writes `actorAddress` only when `recordActorAddress` is `true`. The setting is read per event, so turning it off
  stops the address in both the table and the lines at once. It does not erase what was already written.
- The sink output sits outside the database. The retention setting `retentionDays` does not reach it. The collector's
  retention is then the real retention, and the how-to tells the operator to set it to match their policy.
- Erasure: deleting a principal does not rewrite old audit rows or old lines, and the sink never rewrites a file. Where law or
  policy requires erasure, it happens in the collector or by deleting rotated files. The documentation states this limit
  and does not claim compliance.
- The file is created with mode `0640`, and standard output is the log stream of the platform, so its access is the platform's.
- Secrets are never in `details` (`AuditEvent.kt` contract), so the sink adds no secret.

## Risks

- Standard output mixing with logs. Mitigated by the `schema` marker and by the file option.
- A burst such as a brute-force login flood writes one line per failure, because the folding is a table concern. The bounded
  queue caps the memory, and drops are counted. The login brute-force protection already limits the rate.
- Duplicate lines if a collector reads both the file and standard output. The how-to says to pick one.

## Open questions

None blocks implementation. Maintainer confirmations that would change a decision: whether `occurrences` should be dropped from
the line (kept here for parity with the CSV), and whether the queue capacity needs a variable (not added; a constant is
enough until a user asks).
