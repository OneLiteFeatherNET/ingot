## ADDED Requirements

### Requirement: Legacy text protocol is unchanged

The server SHALL keep the text protocol of `/api/console/sock` for a connection whose first frame starts with `Authorization:`. Such a
connection receives the log as raw text frames, is answered `keep-alive` to `keep-alive`, sends a command line as text and gets no answer
frame for it, and on failed authentication gets the error and a disconnect as before.

#### Scenario: Legacy client runs a command

- **WHEN** a client sends `Authorization:ci-rotation:<secret>` as its first frame and `status` as a later frame
- **THEN** the server sends log lines as raw text, no JSON envelope is sent, the command runs as before and its audit event has `confirmed` false

#### Scenario: Legacy keep-alive

- **WHEN** a legacy client sends `keep-alive`
- **THEN** the server answers `keep-alive`

### Requirement: Version negotiation by hello

A connection whose first frame is a JSON object with the type `hello` SHALL open protocol version 1. The server SHALL answer with a
`welcome` that states the highest version both sides support. A first frame of any other form SHALL be refused as before.

#### Scenario: Common version

- **WHEN** the hello lists the protocols `[1, 2]`
- **THEN** the `welcome` states protocol `1`

#### Scenario: No common version

- **WHEN** the hello lists only `[2]`
- **THEN** the server sends an `error` with the code `unsupported_protocol` and `supported` `[1]`, and closes the connection with 1002

#### Scenario: No hello in time

- **WHEN** a connection sends no valid hello within 10 seconds
- **THEN** it is closed with 1008 after an `error` with the code `timeout`

### Requirement: Envelope and unknown frames

Every version 1 frame SHALL be a text frame holding one JSON object with a `type`. An answer to a request SHALL carry the `id` of that
request. The server SHALL ignore fields it does not know, and SHALL answer an unknown `type` with the `error` code `unknown_type` while the
connection stays open.

#### Scenario: Unknown type

- **WHEN** a client sends `{"type": "reboot", "id": "r1"}`
- **THEN** the server answers with an `error` that has the code `unknown_type` and the `id` `r1`, and the connection stays open

#### Scenario: Frame over the size limit

- **WHEN** a client sends a frame larger than 4 KiB
- **THEN** the server answers with an `error` that has the code `too_long` and the connection stays open

### Requirement: Execute and result

An `execute` frame SHALL run its command and SHALL be answered by one `result` frame with the `id` of the request, the `status`, the
`exitCode`, the `output` lines with their `stream`, the `data` (or null) and the `node`. A second `execute` while one is running SHALL be
answered with the error code `busy` and `retryable` true, and it SHALL NOT run.

#### Scenario: Failed command

- **WHEN** a client sends `execute` with the `id` `e1` and the command `level nonsense`
- **THEN** the `result` with the `id` `e1` has exit code `1`, and its failure lines have the stream `stderr`

#### Scenario: Second execute while one runs

- **WHEN** a second `execute` arrives while the first one is running
- **THEN** it is answered with the error code `busy`, `retryable` true, and it does not run

### Requirement: Confirmation of dangerous commands on the socket

A dangerous `execute` SHALL NOT run until the client answers the server's `confirm` frame for the same `id` with `accept` true. An
`execute` that carries `confirm` true SHALL run without that answer. A declined confirmation, and one not answered within 120 seconds, SHALL
run nothing and SHALL be answered with a `result` that has exit code `3` and `confirmed` false. The `confirm` frame SHALL NOT contain the
arguments of a sensitive command.

#### Scenario: Accepted confirmation

- **WHEN** a client sends `execute` for `token-revoke ci`, receives `confirm`, and answers with `accept` true
- **THEN** the token is revoked and the `result` has `confirmed` true

#### Scenario: Confirmation not answered

- **WHEN** no answer to `confirm` arrives within 120 seconds
- **THEN** nothing runs and the `result` has exit code `3`

#### Scenario: Sensitive arguments stay out of the frame

- **WHEN** `token-regenerate ci s3cr3t` needs confirmation
- **THEN** the `confirm` frame names `token-regenerate` and does not contain `s3cr3t`

### Requirement: Completion from the command listing

A `complete` frame SHALL be answered by a `complete` frame with the `id` of the request, a `replace` range and the `candidates`. The
candidates SHALL come from the same listing as `GET /api/console/commands`. A `complete` SHALL NOT wait for a running `execute`.

#### Scenario: Command name

- **WHEN** a client sends `complete` with the line `tok`
- **THEN** the candidates include `tokens` and `token-generate`, and the `replace` range covers `tok`

#### Scenario: Completion during a command

- **WHEN** a client sends `complete` while an `execute` runs
- **THEN** the `complete` is answered before the `result`

### Requirement: Live log with sequence numbers and levels

Each `log` frame SHALL carry a `seq`, a `node`, a `level` and a `text`. The `seq` SHALL be unique within one boot of the process. A client MAY
raise its threshold with `logLevel` in the hello, and SHALL NOT receive lines below the visible threshold of the server.

#### Scenario: Raised threshold

- **WHEN** the hello sets `logLevel` to `WARN`
- **THEN** no `log` frame with the level `INFO` is sent on that connection

#### Scenario: Line from another replica

- **WHEN** a line produced on another replica reaches this one through console fan-out
- **THEN** its `log` frame carries the `node` of that replica

### Requirement: Resume after a drop

A hello with `resume` for the current boot SHALL replay the cached lines with a greater `seq` and the `welcome` SHALL state `replay`
`resumed`. A hello for another boot SHALL replay the whole cache and the `welcome` SHALL state `replay` `full`. The server SHALL NOT keep
results for a resume, and SHALL NOT run a command again on its own.

#### Scenario: Same boot

- **WHEN** a client resumes with the `seq` 412 of the boot it holds
- **THEN** the lines after 412 are replayed and the `welcome` states `resumed`

#### Scenario: Restarted process

- **WHEN** a client resumes with the boot of an earlier process
- **THEN** the `welcome` states `full` and the cache is replayed

### Requirement: Backpressure of the outbound queue

Each version 1 connection SHALL have a bounded outbound queue. When the queue is full, the server SHALL drop `log` frames and count them in
the `dropped` field of the next `log` frame it delivers. It SHALL NOT drop `result`, `confirm`, `complete` or `error` frames. If the queue
is full of those frames, the connection SHALL be closed with 1013 after an `error` with the code `slow_consumer`. A write to the socket
SHALL NOT block the thread that logs.

#### Scenario: Slow reader loses log lines only

- **WHEN** the queue is full of log lines and a `result` arrives
- **THEN** the lines are counted as dropped, the `result` is delivered, and the next `log` frame states the count

#### Scenario: Slow reader of results

- **WHEN** the queue is full of results that the client does not read
- **THEN** the connection is closed with 1013

### Requirement: Log rate limit per connection

A version 1 connection SHALL receive log lines at no more than 200 per second on average, with a burst of 500. Lines over the limit SHALL be
counted in `dropped`.

#### Scenario: Burst

- **WHEN** 1000 lines are logged in the same instant
- **THEN** at most 500 of them are delivered at once and the rest are counted as dropped

### Requirement: Authentication at the handshake and before each action

A token in the hello SHALL be checked as the legacy first message is checked. A connection without a token SHALL authenticate with the
session cookie of the upgrade request, and SHALL require an `Origin` header that names the host the request reached. The principal SHALL be
checked again before each `execute` and before each accepted `confirm`. A revoked token, a removed console area or an expired session SHALL
close the connection with 1008.

#### Scenario: Cookie connection from another site

- **WHEN** a connection authenticates with a session cookie and its `Origin` names another site
- **THEN** it is closed with 1008 and the error code `unauthorized`

#### Scenario: Token revoked while connected

- **WHEN** the token of a connected client is revoked
- **THEN** its next `execute` is refused and the connection is closed with 1008

#### Scenario: Session expires while connected

- **WHEN** the session of a connected browser reaches its expiry time
- **THEN** the connection is closed with 1008

### Requirement: Node identity and command scope

The `welcome` SHALL name the node and the boot of the process. Each descriptor of the command listing SHALL carry a `scope` of `node` or
`cluster`.

#### Scenario: Per-replica command

- **WHEN** the command listing is read
- **THEN** `status` has the scope `node`

### Requirement: Liveness and shutdown

The server SHALL send a WebSocket ping every 20 seconds, and SHALL close a connection that does not answer with a pong within 60 seconds.
When the server stops, each open version 1 connection SHALL receive an `error` with the code `shutdown` and SHALL be closed with 1001.

#### Scenario: Silent client

- **WHEN** a connected client does not answer pings for 60 seconds
- **THEN** the connection is closed

#### Scenario: Server shutdown

- **WHEN** the server stops
- **THEN** each open connection receives the error code `shutdown` and is closed with 1001

### Requirement: Socket commands are audited

Every `execute` that reaches execution on a version 1 connection SHALL write a `CONSOLE_COMMAND_EXECUTED` event with the token name as the
actor, the channel `websocket`, the `client` of the hello, and the `confirmed` answer. A declined command SHALL be written with `confirmed`
false and exit code `3`, as a rejected HTTP command is.

#### Scenario: Client named in the event

- **WHEN** a client with `client` `ingot-cli/1.0.0` runs `status`
- **THEN** the event has the channel `websocket` and the client `ingot-cli/1.0.0`

### Requirement: The worker target has no console socket

On the worker target, `/api/console/sock` SHALL NOT be registered, as before, so a connection attempt is not upgraded.

#### Scenario: Worker

- **WHEN** the server runs with the deploy target `worker`
- **THEN** a connection attempt to `/api/console/sock` is not upgraded
