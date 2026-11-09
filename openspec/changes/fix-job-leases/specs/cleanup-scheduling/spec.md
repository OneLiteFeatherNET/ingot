# Spec Delta

## Purpose

A cleanup policy deletes once, whichever replica runs it, and a scheduled run that a replica missed is made up later the same day.

## ADDED Requirements

### Requirement: One run per policy across replicas

A run of a cleanup policy SHALL hold the lease `cleanup:<policy>:active` for its whole duration. A second run of the same policy,
manual or scheduled and on any replica that shares the database, SHALL be refused with `409` in the problem response shape while the
lease is live.

#### Scenario: Manual run while a scheduled run is live on another replica

- **WHEN** a scheduled run of `nightly` is running on replica A and a manual run of `nightly` is requested on replica B
- **THEN** the request on replica B answers `409` and deletes nothing

#### Scenario: Lease released after a failed run

- **WHEN** a run of `nightly` fails with an error during its deletions
- **THEN** the lease `cleanup:nightly:active` is released before the run returns, and a later run is accepted

### Requirement: Lease kept alive during long runs

A run SHALL extend the leases it holds from its deletion loop at most every 20 seconds. A run that finds it has lost a lease SHALL
NOT start its next deletion, and SHALL record the run as `PARTIAL` or `FAILED` with the reason `lease lost`.

#### Scenario: Lease lost between two deletions

- **WHEN** another replica takes over the lease of `nightly` while the run is deleting
- **THEN** the run deletes nothing after the next check, and its record names the lease as the reason

### Requirement: Once per day with takeover

A scheduled run SHALL take the lease `cleanup:<policy>:<UTC date>` with the done flag before it starts. A day whose run has been
attempted to the end SHALL NOT run again on any replica. A day whose run was not completed, because its replica stopped, SHALL be
run by another replica once the lease has expired.

#### Scenario: Replica stops during the scheduled run

- **WHEN** replica A stops in the middle of the scheduled run of `nightly` for 2026-11-09, and replica B ticks after the lease expired
- **THEN** replica B runs `nightly` for 2026-11-09 once, and no third run starts that day

#### Scenario: Completed day on another replica

- **WHEN** replica A completed the scheduled run of `nightly` for 2026-11-09 and replica B ticks later that day
- **THEN** replica B starts no run

### Requirement: Catch-up within a window

A scheduled run SHALL start at the first tick at or after the scheduled time of its policy, for at most six hours after that time.
A scheduled run whose window has passed SHALL NOT start that day.

#### Scenario: Replica down at the scheduled minute

- **WHEN** an enabled policy is due at 02:00 and the replica that ticks first is started at 02:40
- **THEN** the replica starts the run once at that tick

#### Scenario: Window passed

- **WHEN** an enabled policy is due at 02:00 and the first tick after the window is at 09:00
- **THEN** no run starts on that day

### Requirement: Scheduled run that finds a manual run

A scheduled run that finds the active lease held SHALL be recorded in the log as skipped, and the day SHALL count as attempted. The
run SHALL NOT be retried later that day.

#### Scenario: Manual run already running at the scheduled time

- **WHEN** a manual run of `nightly` holds the active lease when the scheduled run becomes due
- **THEN** the scheduled run logs a skip and starts no deletion

### Requirement: Audit retention purge on the same lease

The audit retention purge SHALL take its daily key as a lease with the done flag. A purge whose holder stopped SHALL be taken over by
another replica after the lease expires, and a completed purge day SHALL NOT repeat.

#### Scenario: Purge holder stops

- **WHEN** the replica that holds the purge lease for 2026-11-09 stops before it deletes, and another replica ticks after the expiry
- **THEN** the other replica runs the purge once for that day
