# Spec Delta

## Purpose

A job that must run once across replicas holds a lease that expires. A replica that dies releases the job to the others, and a
job that has finished is never granted again for its day.

## ADDED Requirements

### Requirement: Lease grant

The claim store SHALL grant a lease for a key only when the key is not done and holds no live lease. Exactly one caller among
the replicas that share the database SHALL receive the grant for a key at a time.

#### Scenario: Two replicas ask for the same free lease

- **WHEN** two replicas call `lease` for the key `cleanup:nightly:active` at the same instant and no lease exists
- **THEN** exactly one call returns true and the other returns false

#### Scenario: A live lease refuses a second caller

- **WHEN** a lease of `cleanup:nightly:active` is held by one owner and its expiry is in the future
- **THEN** a call of `lease` by another owner returns false and the lease keeps its first owner

### Requirement: Takeover after expiry

A lease whose expiry is in the past SHALL be granted to the next caller. The store SHALL decide expiry from the injected clock
only.

#### Scenario: Holder stops renewing

- **WHEN** the holder of a lease does not extend it, and the clock passes its expiry
- **THEN** a call of `lease` by another owner returns true, and the previous owner's `extend` returns false

### Requirement: Renewal by the owner only

`extend` SHALL move the expiry of a live lease forward only when the caller is the current owner. `complete` SHALL release a
lease only when the caller is the current owner.

#### Scenario: Stale owner cannot release

- **WHEN** an owner whose lease was taken over calls `complete`
- **THEN** the lease of the new owner is unchanged

### Requirement: Done keys are never granted again

`complete` with the done flag SHALL mark the key as done. A done key SHALL NOT be granted again, even after its lease expires.
Keys that are not marked done SHALL be grantable again after their release or expiry.

#### Scenario: Finished day is not repeated

- **WHEN** an owner completes `cleanup:nightly:2026-11-09` with the done flag, and the clock moves past its former expiry
- **THEN** `lease` for that key returns false on every replica

#### Scenario: Released active lease is grantable again

- **WHEN** an owner completes `cleanup:nightly:active` without the done flag
- **THEN** a later `lease` call for that key by any owner returns true

### Requirement: Unshared stores grant everything

Without a shared database the lease calls SHALL always succeed, so that a single replica behaves as it does today.

#### Scenario: No shared database

- **WHEN** the unshared implementation receives `lease`, `extend` and `complete`
- **THEN** `lease` and `extend` return true and `complete` does nothing

### Requirement: Retention of lease rows

Claim rows older than seven days SHALL be deleted, except rows whose lease is live. A live lease SHALL NOT be deleted however old its
claim time is.

#### Scenario: Old released row is removed

- **WHEN** a released row was claimed eight days before the next claim is made
- **THEN** that row is deleted by the next claim

#### Scenario: Old row with a live lease is kept

- **WHEN** a row claimed eight days earlier still holds a live lease
- **THEN** the row is kept

### Requirement: Retry on database conflicts

A lease operation that fails with a deadlock (MariaDB error 1213, SQLSTATE 40001, including the certification failure of
Galera) SHALL be retried a bounded number of times. The delay between attempts SHALL come from an injected function. If the
Galera spike shows that no such error occurs, the retry SHALL NOT be added.

#### Scenario: Deadlock then success

- **WHEN** the first two attempts of `lease` fail with error 1213 and the third succeeds
- **THEN** `lease` returns the result of the third attempt and the test does not wait in real time

#### Scenario: Persistent conflict

- **WHEN** every attempt fails with error 1213
- **THEN** the operation throws the last error to the caller, which treats it as a lease that was not granted
