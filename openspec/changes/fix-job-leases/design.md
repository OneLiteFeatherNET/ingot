# Design

## Decisions

### 1. Leases in the existing claim table

The claim table `scheduled_job_claim` (`SqlScheduledJobClaims.kt`) gains `lease_owner`, `lease_until` and `done_at`, all nullable.
The alternative is a new lease table. One table keeps one retention rule, one place for the claim code and one shared
implementation for cleanup and audit, as the cluster design asks. The new columns are added with `createMissingTablesAndColumns`,
so an existing table is extended and not recreated.

### 2. A done flag, not only a lease

Insert-once claims stop a second replica, but they also stop a takeover: once a crashed replica has inserted the day's key, no
other replica may run that day. Takeover needs the key to stay grantable until the run has been attempted to its end. Hence the
done flag: a day is marked done when its run finishes, whether it succeeded or failed. A replica that dies before that point
leaves the key grantable after the lease expires. A failed run is not retried within the day, because retrying a storage failure
every 30 seconds would be worse than one failed run.

### 3. Two leases for a scheduled run

A scheduled run holds the day key (`cleanup:<policy>:<UTC date>`, done flag) and the active key (`cleanup:<policy>:active`, no
done flag). The day key stops two replicas from running the same day. The active key stops a manual run from overlapping the
scheduled one. A manual run holds only the active key. Both are renewed by the run, so a long run keeps them.

### 4. Catch-up reverses a documented rule

`docs/gitbook/reference/cleanup-policies.md` states that an instance that is not running at the scheduled minute "does not start
the run for that day. Ingot does not catch up later." This change reverses it, as the cluster reliability principle asks: a
missed run is made up. The six-hour window is a limit chosen here, not a figure from the roadmap. Consequence: a replica restarted
inside the window runs the missed cleanup at once. The alternative is to keep the documented skip and to keep only the takeover of
a run that was already started. The maintainer should confirm this choice before the code pull request is merged. If the answer is
to keep the skip, tasks 4.3 and 4.4 drop their catch-up cases and the spec requirement "Catch-up within a window" is removed.

### 5. Search leases stay where they are

`RunStore` and `OutboxWorker` already implement expiring leases on the search tables, and they are tied to `SearchTables`. Moving
them onto the claim table would widen this change to the search index, which the roadmap keeps separate. A shared primitive is a
follow-up. The roadmap's note "reuse the search lease code" is therefore read as "follow the same pattern", not as shared code.

### 6. Mixed versions

A previous release does not read the lease columns. During a rolling upgrade its replicas can run a cleanup alongside a new
replica. This is accepted for one release and written into the upgrade notes. A cluster that needs strict exclusivity during the
upgrade should upgrade all replicas before enabling a policy.
