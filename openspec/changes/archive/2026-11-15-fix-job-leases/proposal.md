# Proposal

## Why

Ingot runs as several replicas that share one database, and every replica runs the same schedules. Two jobs must run once
across the cluster: the cleanup policies and the audit retention purge. Their guard is a claim row that is inserted once per
UTC day and never expires. That guard has three gaps:

- If the replica that claimed a day dies in the middle of the run, that day is lost. No other replica runs it, and the
  cleanup stays undone until the next day.
- A manual run is refused only inside one JVM. A manual run on one replica and the scheduled run on another can delete the
  same versions at the same time.
- A replica that is down at the scheduled minute skips that day's run. The cleanup reference documents this as the rule.

The search index already uses leases with expiry for its rebuild, verify and outbox work (`RunStore`, `OutboxWorker`). The
cleanup and the audit purge do not. Whether the overlap really happens on two replicas is an open verification, so the first
tasks write the failing tests before any production code.

## What Changes

- **L1 Leases on the claim table.** `ScheduledJobClaims` gains `lease`, `extend` and `complete`, stored in the existing
  `scheduled_job_claim` table with three nullable columns: `lease_owner`, `lease_until` and `done_at`. A lease is granted
  when the key is not done and has no live lease. `complete` releases the lease, and it can mark the key as done.
- **L2 Takeover.** A holder that stops extending its lease loses it after 60 seconds. The next replica that ticks takes it
  over. A daily key is marked done only when its run has been attempted to the end, so a run that died in the middle is
  taken over, and a finished day is never run twice.
- **L3 Cleanup exclusivity.** Every run of a policy holds the lease `cleanup:<policy>:active` for its whole duration, manual
  and scheduled alike. A second run of the same policy on any replica is refused with the existing `409` problem response.
  The lease is released in every case, including an error.
- **L4 Catch-up.** A scheduled cleanup starts at the first tick at or after its scheduled time, for at most six hours, and
  at most once per UTC day. Today it skips the day. This reverses a rule the documentation states, see `design.md`.
- **L5 Audit purge.** The retention purge takes its daily key as a lease with takeover instead of an insert-once claim.
- **Lease renewal.** A run extends the leases it holds from its deletion loop, at most every 20 seconds, with no extra
  thread. When an extension fails, the run stops before its next deletion and records the run as failed or partial with
  the reason `lease lost`.

### Out of scope

- Moving the search leases (`RunStore`, `OutboxStore`) onto the new API. They keep their own code. A shared lease primitive
  for all of them is a follow-up.
- The rebuild and verify claims in `net.onelitefeather.ingot.search`, the metadata lock (roadmap F4) and the schema lock
  (roadmap F3).
- Any new setting, environment variable or manifest field. The lease and window durations are constants.
- The cleanup policy API, the run history and the seven-day retention of claim rows.
- Making the lease fence a delete that is already in flight. A deletion that started before the lease was lost completes.

## Capabilities

### New Capabilities

- `job-leases`: expiring leases in the shared claim table, their takeover, their renewal and their retention.
- `cleanup-scheduling`: single-run cleanup across replicas, catch-up of scheduled runs, and the audit purge on the same lease.

## Impact

- **Type and scope.** The change ships under `fix(scheduling)`. The branches use `test(scheduling)`, `fix(scheduling)`,
  `fix(cleanup)`, `fix(audit)` and `docs(cleanup)` commits, one type per commit. The pull request title, squash-merged onto
  main, is:

  `fix(scheduling): expire and take over cleanup and audit job leases`

- **Breaking marker.** No breaking change. No configuration key, environment variable, volume, manifest or plugin has to
  change. The title carries no `!` and no `BREAKING CHANGE:` footer.
- **Behaviour that changes by default.** A scheduled cleanup that a replica missed now runs on a later tick of the same UTC
  day, at most six hours after its scheduled time. A restart inside that window runs the missed run. Operators with enabled
  policies see this, so the upgrade notes and the cleanup reference say so.
- **Mixed versions.** The three new columns are nullable, so the previous release still reads and writes the table. A
  previous release ignores the leases, so during a rolling upgrade a replica on the old release can still run a cleanup
  next to a new one. The upgrade notes say this, and the rollout is documented as one replica first.
- **Dependencies.**
  - `fix-schema-init-lock` (roadmap F3, wave A): the new columns are added at boot, and that boot path is serialised by the
    schema lock. The change can be merged without it, but it is safer after it.
  - `add-cluster-bus` (merged on main): defines the claim table and the single-runner rule that this change extends.
  - `add-problem-details` (merged on main): the `409` response shape that a refused run uses.
- **Code.** `reposilite-backend` (scheduling, cleanup, audit and their tests) and `docs/gitbook/reference/cleanup-policies.md`,
  `docs/gitbook/how-to/set-cleanup-policies.md`, `docs/gitbook/reference/upgrade-changes.md`.

## Verification

- Each task runs its targeted tests through `HEAVY_SLOTS=1 heavy` with Gradle `--no-daemon --max-workers=2`, with
  `:reposilite-backend:test --tests '<pattern>'`. The MariaDB Galera spike runs alone, through the same runner.
- `openspec validate fix-job-leases --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/fix-job-leases` prints nothing.
