# Proposal

## Open maintainer questions

1. **Where do the records go?** Default: the existing audit store, through `AuditFacade`, as rows with the new actions `AUTHORIZATION_DENIED` and `AUTHORIZATION_WRITE`. The existing audit query and CSV endpoints then read them. Alternative: a dedicated `decision_log` table, or only the JSON lines sink.
2. **Does `sync` ship in phase A?** Default: yes, off unless selected. `sync` writes denials and writes before the answer and refuses the request when the record cannot be stored. Fail-closed needs a durable store, and the audit table is one. Alternative: defer `sync` to change `add-protected-namespaces`.
3. **Are client addresses recorded?** Default: yes, as the audit already records `actorAddress` (`AuditFacade.kt`, `offerLine`), with the same retention. Alternative: omit the address from decision records.
4. **Are allowed reads ever recorded?** Default: no. The design's switch for reads is not added, because it would only ever be turned on.

## Why

Incident reviews ask which credential sent a request, and which rule decided it. Ingot can say that a token was created or a team changed (the audit trail), but not that a request was refused or which rule let a write through. The audit actions (`AuditAction.kt`) contain no decision entry, and the review found no record of refused requests (verify across the audit call sites before the change starts). The decision log closes that gap.

## What Changes

- Each denial, each write (allowed or refused) and each refused admin operation produces one decision record. Allowed reads produce none.
- A record holds: a decision identifier, the time, the principal, the credential identifier (the token name, never the secret), the action, the canonical resource and, where it differs, the raw resource, the outcome, the deciding stage of the chain, the matched rule identifiers, the policy revision (null until `add-policy-revision` is merged) and the obligations (empty until phase C).
- In `async` mode (default), records are queued and written by a background writer. A full queue drops the record and increments a counter. The request is never delayed by the writer.
- In `sync` mode, the write happens before the answer. A failed write refuses the request with 503 and the reason, and the decision is not applied.
- The switch is `INGOT_DECISION_LOG`, with values `off`, `async` and `sync`, and the `REPOSILITE_DECISION_LOG` fallback.

### Out of scope

- Override records and the `OVERRIDE` source (`add-protected-namespaces`).
- `who-can` and the redaction of query results (`add-access-queries`).
- Hash chaining, and any export format beyond the existing audit CSV.

## Capabilities

### New Capabilities

- `identity-decision-log`: the content of a decision record, the modes, the drop counter and the fail-closed rule of `sync`.

## Impact

- **Operators, default (`async`):** the audit table gains one row per denial and per write. A scanner that probes an instance produces many rows, so the table grows faster. Operators who need the old volume set `INGOT_DECISION_LOG=off`. No configuration is needed to upgrade.
- **Operators, `sync` (opt-in):** a database failure during a denial or write refuses that request with 503. This is intended for regulated installations, and the operator chooses it.
- **Operators, retention:** the existing audit cleanup (`CLEANUP_RUN`) must be verified to cover the new rows. Until then, the rows are kept as audit rows are kept today.
- **Configuration:** one new optional key, `INGOT_DECISION_LOG`, with the `REPOSILITE_` fallback. Docs: `docs/gitbook/explanation/permission-evaluation.md` (the audit section) and `docs/gitbook/explanation/drop-in-compatibility.md`.
- **Code:** `reposilite-backend`, in the audit package and in the authorization chain of `introduce-authorizer-chain`.
- **Commits:** `test(identity)` for the failing tests, `feat(identity)` for the log, `docs(identity)` for the pages. Pull request title, squash-merged onto main:

  `feat(identity): log authorization decisions`
