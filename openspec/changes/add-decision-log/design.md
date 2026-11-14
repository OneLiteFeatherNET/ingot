# Design

## D1. Record

A record is an `AuditEvent` whose action is `AUTHORIZATION_DENIED` or `AUTHORIZATION_WRITE`, with the decision fields in `details`. No table schema changes, so the audit query and CSV endpoints work unchanged. Fields:

| Field | Source |
|---|---|
| `decisionId` | UUID from an injected supplier |
| `time` | the audit clock (injected `Clock`) |
| `principal`, `credentialId` | the subject (token name, never the secret) |
| `action`, `resource` | the chain request; `rawResource` only when it differs from the canonical one |
| `outcome` | Allow or Deny |
| `stage` | the stage that decided: `disabled`, `built-in`, `default` |
| `ruleIds` | the node texts and their source of the matched candidates |
| `revision` | the policy revision, null until `add-policy-revision` |
| `obligations` | empty until phase C |

## D2. Which decisions are recorded

- Every Deny of a check.
- Every write decision, allowed or refused.
- Every refused admin operation.
- Allowed reads are never recorded.

## D3. Modes

- `off`: nothing is recorded, and the decision path does not allocate a record.
- `async` (default): a bounded queue in front of a single writer. Offering to the queue never blocks. A full queue drops the record and increments a counter. The counter is logged once per minute while it changes, so the drop is visible without a log flood.
- `sync`: the record is written before the answer. A write failure refuses the request with 503, and the decision is not applied.

## D4. Privacy

The record holds the client address when the audit already holds one. Decision records are read through the audit endpoints, which already require the audit area. Redaction by namespace belongs to `add-access-queries` and `add-protected-namespaces`.

## D5. Tests

The writer is a fake sink in tests. Queue overflow is tested with a sink that blocks on a latch that the test controls, with a bounded wait, never a sleep. The drop warning is asserted through a captured appender, as the test rules require. Decision identifiers and times come from injected suppliers.
