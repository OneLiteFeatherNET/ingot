# Design

## D1. The revision row

A single row in a table `policy_revision` (`id` fixed to 1, `revision` as a 64-bit integer). Every identity write runs `UPDATE policy_revision SET revision = revision + 1 WHERE id = 1` inside the same transaction as the write. The update is a plain statement, so it works on SQLite, H2, MariaDB, MySQL and PostgreSQL alike, which is the portability rule of the design.

## D2. Refresh

A refresh loop reads the row every `INGOT_AUTHORIZATION_REFRESH` (default 2 seconds) and stores the last value. The loop is a function that a test can call directly (`refreshOnce()`), so the tests never sleep. The loop starts with the server and stops with it.

## D3. Revision-aware cache

Each entry of the authentication cache stores the revision that was current when it was written. On a hit, the entry is used only when its revision equals the last value seen by the refresh. Otherwise it is dropped and the credential is verified again. The existing `expireAfterWrite` bound of one minute remains as the upper limit.

## D4. Writes

An identity write reads the revision row inside its transaction before it decides, and reloads its inputs when the value differs from the last value seen. This protects the last-admin guard and the escalation guard from acting on a stale view of another replica.

## D5. What is not changed

- Authorization still reads the identity tables on each check. The cache is only for authentication, so the rights themselves are never stale.
- The compiled snapshot of the design is not part of this change.

## D6. Tests

Two stores on one temporary SQLite file stand in for two replicas. A test revokes a token through one store, calls `refreshOnce()` on the other, and asserts the cached credential is refused. A Guava `Ticker` fake stands in for the one-minute bound, which the test advances without sleeping.
