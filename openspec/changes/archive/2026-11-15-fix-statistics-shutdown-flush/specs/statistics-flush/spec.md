# Spec Delta

## Purpose

Resolved-request counts survive a restart and a rolling update, and no count is written twice or dropped while the buffer is drained.

## ADDED Requirements

### Requirement: Buffered counts are written on shutdown

The server SHALL write every buffered resolved-request count to the statistics repository when it emits the dispose event,
before it closes the database. The server SHALL write them whether or not statistics is enabled at that moment.

#### Scenario: Shutdown with buffered counts

- **WHEN** Ingot shuts down while three requests for an artifact are buffered and not yet written
- **THEN** the statistics repository holds those three requests after the shutdown, and the counts are shown after the next start

#### Scenario: Statistics switched off after counting

- **WHEN** statistics is disabled after requests were buffered, and Ingot then shuts down
- **THEN** the buffered requests are still written

#### Scenario: Database unavailable at shutdown

- **WHEN** the statistics write fails during the dispose event
- **THEN** the failure is logged, the counts stay in the buffer, and the other extensions still receive the dispose event

### Requirement: No count is dropped or written twice during a flush

The server MUST keep a request that is counted while a flush is writing, MUST write each counted request exactly once, and MUST
keep the counts of a failed write for the next flush.

#### Scenario: Count arrives during a flush

- **WHEN** a request is counted for an artifact while a flush is writing the previous counts for that artifact
- **THEN** the request is written by a later flush and is not removed from the buffer by the running flush

#### Scenario: Write fails

- **WHEN** a flush fails with an error that is not a deadlock or certification error
- **THEN** the counts of that flush are added back to the buffer, and the next flush writes them once

#### Scenario: Concurrent flushes

- **WHEN** the periodic flush and the shutdown flush run at the same time
- **THEN** each buffered count is written by exactly one of them

#### Scenario: Deadlock on a clustered database

- **WHEN** a write fails with SQLState `40001` on the first attempt
- **THEN** the flush retries the write, and each count is written once when the retry succeeds

### Requirement: Identifier rows are created once

The SQL statistics repository MUST treat an identifier row that another writer created first as a successful insert, and MUST
NOT roll back the batch of counts because of it.

#### Scenario: Identifier created by another writer

- **WHEN** two writers store counts for the same new artifact at the same time, and one of them finds no identifier row before the other inserts it
- **THEN** both batches are written, the identifier row exists once, and both counts are stored
