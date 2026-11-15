# Design

## Where the state lives

The roadmap asked for the state to propagate over the cluster bus. The bus is optional and best-effort, and the HA design
keeps shared state in the database. A read-only switch that lived only in bus messages would be lost on a restart, and a
replica that joins later would not know about it. So the database is the authority: one entry in the existing configuration
store, changed with the compare-and-save that store already offers. Each instance caches the value for at most five seconds.
The bus event only shortens that delay.

Alternatives considered:

- A new table. Rejected: one key-value entry needs no schema change, and the drop-in rule prefers no schema change.
- Bus only. Rejected: a restarted or late replica would start writable, and a missed event would never be repaired.
- Environment only. Rejected: it needs a restart to change, which is the problem the switch solves.

## Environment forces, stored state follows

`INGOT_READ_ONLY=true` wins over the stored state. Its purpose is a deployment that is read-only from the start, for example a
standby. Unset or `false` lets the stored state decide, because an unset variable must not undo a toggle made on the dashboard.
The toggle answers `409` while the environment forces the state, so an operator sees why nothing changed.

## Where the refusal sits

The refusal runs after the caller is identified and after the permission check, so `401` and `403` keep their meaning and a
caller without access learns nothing new. The write handler types already mark `PUT` as a write action in the request
dispatch (`shared/ContextDsl.kt`), which is the natural place to add the check, so both the API and the Maven protocol pass
through one point. The implementation confirms this placement in its first red test.

Login, refresh and the toggle are exempt by an exact list of routes, not by a prefix, so that a look-alike path is not exempt.

## Scheduled runs

The guard sits before the claim. A claim taken while read-only would record a run that never happened, so the guard must come
first. A run that was due while read-only is skipped, not deferred, because a late cleanup during a cutover is the case the
switch is meant to avoid.

## Consequences

- A proxy repository still stores files it fetches on a `GET`. The switch stops client writes, not the cache. That keeps the
  mirror working during a backup and is listed as out of scope.
- A replica forced by the environment is read-only while the rest of the cluster is not. Operators who run a mixed cluster must
  know this, and the how-to says so.
- A toggle is audited, so an operator can see who stopped writes and when.
