## ADDED Requirements

### Requirement: Draining flag set before the stop

The server SHALL set its draining flag before it stops the scheduler, the web server or any other component during shutdown.
While the flag is set, the readiness answer SHALL list `draining` in `failing` and answer `503`. The flag SHALL NOT be cleared
again in the same process.

#### Scenario: Shutdown begins

- **WHEN** a shutdown starts and a client requests `GET /api/status/ready`
- **THEN** the response status is `503` and `draining` is the first entry of `failing`

#### Scenario: Flag is set before the web server stops

- **WHEN** the shutdown sequence runs
- **THEN** the draining flag is set before the web server stop is called

### Requirement: Liveness stays up while draining

The server SHALL keep `GET /api/status/health` unchanged while it is draining. A draining instance SHALL answer `200` on that
path so that an orchestrator does not restart it while it finishes its work.

#### Scenario: Health while draining

- **WHEN** the instance is draining and a client requests `GET /api/status/health`
- **THEN** the response is `200` with the same body as before the shutdown began

### Requirement: Drain wait for requests in flight

The server SHALL read `INGOT_SHUTDOWN_TIMEOUT` as a whole number of seconds, with `REPOSILITE_SHUTDOWN_TIMEOUT` as the fallback
name. The default SHALL be `0`. When the value is above `0`, the server SHALL wrap its handler chain in a graceful handler and
SHALL wait up to that many seconds for requests in flight before it stops the web server.

#### Scenario: Request in flight during the drain

- **WHEN** the timeout is above `0`, a request is in flight, and the shutdown starts
- **THEN** the in-flight request completes with its normal response before the server stops

#### Scenario: Request still in flight after the timeout

- **WHEN** the timeout is above `0` and a request is still in flight when the timeout ends
- **THEN** the server stops, and the request is not waited for any longer

### Requirement: Refusal of new requests during the drain

While the drain wait of a shutdown lasts, the server SHALL answer each new request with `503`.

#### Scenario: New request during the drain

- **WHEN** the timeout is above `0`, the shutdown has started, and a new request arrives
- **THEN** the new request receives `503`

### Requirement: Unwrapped stop without a drain timeout

When the timeout is `0`, the server SHALL NOT wrap its handler chain and SHALL stop the web server without a wait.

#### Scenario: Default timeout keeps today's stop

- **WHEN** `INGOT_SHUTDOWN_TIMEOUT` and `REPOSILITE_SHUTDOWN_TIMEOUT` are both unset
- **THEN** the handler chain is not wrapped and the web server stop returns without a drain wait

### Requirement: Opt-in pre-stop delay

The server SHALL read `INGOT_SHUTDOWN_DELAY` as a whole number of seconds, with `REPOSILITE_SHUTDOWN_DELAY` as the fallback name.
The default SHALL be `0`. When the value is above `0`, the server SHALL keep serving for that many seconds after the draining flag
is set, and SHALL start the drain timeout only after the delay has passed.

#### Scenario: Delay before the drain

- **WHEN** the delay is `5` seconds and a shutdown starts
- **THEN** the server waits `5` seconds after the draining flag is set before it starts the graceful stop

#### Scenario: Default delay is none

- **WHEN** `INGOT_SHUTDOWN_DELAY` and `REPOSILITE_SHUTDOWN_DELAY` are both unset
- **THEN** the graceful stop starts immediately after the draining flag is set

### Requirement: Settings validation

The server SHALL refuse to start when `INGOT_SHUTDOWN_TIMEOUT` or `INGOT_SHUTDOWN_DELAY` is set to a value that is not a
non-negative whole number. The refusal SHALL name the variable and SHALL NOT include the value of any other setting. When the
`INGOT_` variable is set, the `REPOSILITE_` variable of the same key SHALL be ignored.

#### Scenario: Invalid timeout

- **WHEN** `INGOT_SHUTDOWN_TIMEOUT` is set to `soon`
- **THEN** the start is refused and the message names `INGOT_SHUTDOWN_TIMEOUT`

#### Scenario: Fallback name is read

- **WHEN** only `REPOSILITE_SHUTDOWN_TIMEOUT` is set to `10`
- **THEN** the drain timeout is `10` seconds

#### Scenario: Ingot name wins

- **WHEN** `INGOT_SHUTDOWN_TIMEOUT` is set to `10` and `REPOSILITE_SHUTDOWN_TIMEOUT` is set to `30`
- **THEN** the drain timeout is `10` seconds
