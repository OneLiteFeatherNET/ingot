## ADDED Requirements

### Requirement: Draining check

The readiness answer SHALL include a `draining` check, evaluated before all other checks. The `draining` check SHALL fail while
the draining flag of the graceful shutdown is set, and SHALL be listed first in `failing` when it fails. It SHALL NOT have a
time bound, because it reads a flag.

#### Scenario: Draining instance

- **WHEN** the draining flag is set and the other checks pass
- **THEN** `draining` is listed in `failing` as its first entry and the status is `503`

#### Scenario: Instance not draining

- **WHEN** the draining flag is not set and the other checks pass
- **THEN** `draining` is not listed in `failing` and the status is `200`
