# Design

All paths are relative to `reposilite-backend/src/main/kotlin/com/reposilite/` unless they start with another top-level name.

## What exists on main

> **Status at this revision.** The command listing (#412), structured execution and the confirmation of dangerous commands (#427),
> and the audit event `CONSOLE_COMMAND_EXECUTED` on the HTTP and websocket channels (#434) are on `main`. Findings 3, 5 and 6 below, and
> the "missing" rows of the table at "What the CLI can reuse", describe `main` before those pull requests. They are kept as history.
> The websocket protocol of Decision 2 is the new work of this revision.

### The command model

- Commands are picocli subcommands. `CommandExecutor` is the root `@Command(name = "")` (`console/CommandExecutor.kt:36`), holds one
  `CommandLine` (`:44`) and registers every `ReposiliteCommand` as a subcommand (`:126-127`). Plugins add commands through
  `CommandsSetupEvent` (`console/application/ConsolePlugin.kt:76-77`).
- `ReposiliteCommand` has one method, `execute(context)`, and one flag, `sensitiveInput` (`console/api/ReposiliteCommand.kt`).
  `CommandContext` carries a `status` of `SUCCEEDED` or `FAILED` and a list of output lines (`console/CommandContext.kt`).
- A line is split on single spaces and handed to picocli (`console/CommandExecutor.kt:116-121`). There is no quoting, so an
  argument cannot contain a space. The length limit is `MAX_COMMAND_LENGTH = 1024` (`console/ConsoleFacade.kt:31`).
- Registered commands: `help`, `stop`, `level` (`console/StandardCommands.kt:30,47,57`), `status`, `failures`, `stats`, `cache`,
  `tokens`, `token-generate`, `token-modify`, `token-rename`, `token-revoke`, `token-regenerate`, `token-export`, `token-import`,
  `route-add`, `route-remove`, `settings-reencrypt`, `settings-decrypt`
  (`token/infrastructure/AccessTokenCommands.kt:30-183`, `token/infrastructure/RouteCommands.kt:28,88`,
  `configuration/shared/infrastructure/SettingsEncryptionCommands.kt:31,63`, `maven/infrastructure/CacheCommand.kt:31`,
  `status/StatusCommand.kt:25`, `status/FailuresCommand.kt:22`, `statistics/StatsCommand.kt:28`).
- `ConsoleFacade.getCommands()` already returns the picocli command map (`console/ConsoleFacade.kt:59-60`), and `HelpCommand`
  renders usage text from it (`console/StandardCommands.kt:36-37`). So the tree exists in memory and is not exposed as data.

### The channels

| Channel | Where | Authentication | Output |
| --- | --- | --- | --- |
| stdin | `ConsoleThread` (`console/ConsoleThread.kt:33-59`), started in `ConsolePlugin.kt:81-83` unless `testEnv` or `--disable-interactive-console` | none, the process owner | the logger |
| `POST /api/console/execute` | `console/infrastructure/ConsoleEndpoint.kt:69-83` | token as basic auth or a session bearer, then `requireArea("console")` | `ExecutionResponse(status, response: List<String>)` |
| `/api/console/sock` | `console/infrastructure/ConsoleWebSocketHandler.kt:67-117` | first message `Authorization:name:secret` or `Authorization:Session <jwt>` (legacy text protocol); a first message `{"type":"hello"}` opens the version 1 protocol of Decision 2, which also accepts the session cookie of a browser | legacy: the shared log stream and no per-command answer; version 1: a `result` per `execute` |
| `GET /api/console/log` (SSE) | `console/infrastructure/ConsoleSseHandler.kt:62` | header or session cookie | the shared log stream |
| dashboard tab | `reposilite-frontend/src/components/console/ConsoleView.vue`, `store/console/connection.js:49,73-78,127` | the dashboard session | SSE for output, HTTP execute for input |

Findings that matter for a CLI:

1. The websocket is not a request and response channel. A command sent on it runs and writes its lines to the logger
   (`ConsoleWebSocketHandler.kt:104-106`), and every subscriber of the log stream sees them, mixed with unrelated log lines. A
   client cannot tell which lines belong to its command. The dashboard moved to HTTP execute for input for this reason.
   The legacy text protocol keeps this behaviour for its clients. The version 1 protocol answers each `execute` with its own
   `result` frame, so a REPL no longer has to find its output in the log stream (Decision 2).
2. `execute` also echoes every output line into the server log (`console/CommandExecutor.kt:46-47`, `logger.info(it)`), and so to every
   console viewer. That is existing behaviour, and it means the output of `tokens` is visible to all console users. Structured mode
   must not add to this: it returns the lines to the caller and logs the same lines as today (unchanged), see "Security".
3. The result is `status` plus text lines (`console/api/ExecutionResponse.kt`, `console/CommandContext.kt:12-15`). There is no exit code and no
   data. A parse error comes back as `FAILED` with a usage message (`console/CommandExecutor.kt:102-112`).
4. Colour: the output may carry ANSI sequences produced through `context.effect` (`console/CommandContext.kt:18-19`). The dashboard
   converts them (`reposilite-frontend/src/store/console/ansi.js`). The CLI needs to keep or strip them by terminal.
5. Nothing lists commands for a remote client. `help` returns usage text, not data.
6. Nothing marks a command as dangerous. `stop`, `token-revoke`, `token-regenerate`, `settings-reencrypt`, `settings-decrypt`
   and `cache ... purge` all run on the first request.

### Authentication, permission, rate limit

- `ConsoleAuthenticator` accepts a token (`authenticateByCredentials`) or a browser session, then requires the admin area
  `console`, node `ingot.admin.console` (`console/infrastructure/ConsoleAuthenticator.kt:40-59,62`). The HTTP route re-checks with
  `requireArea("console")` (`ConsoleEndpoint.kt:73`). The OpenAPI text says 403 for a missing area (`ConsoleEndpoint.kt:62-66`) while the
  authenticator returns 401 for the websocket and SSE (`ConsoleAuthenticator.kt:58`). The CLI must treat both as "not allowed".
- There is one permission for every command. A token that may run `status` may also run `stop`.
- Failed logins are limited by the brute-force ban (`auth/AuthenticationFacade.kt:190-205`). Nothing limits the number of
  commands a valid token runs.

### Audit

- `AuditAction` has token events (`TOKEN_CREATED`, `TOKEN_DELETED`, `TOKEN_SECRET_REGENERATED`, `TOKEN_ROUTES_CHANGED`,
  `audit/api/AuditAction.kt:27-30`) but no console event. Whether a token command run from the console emits
  its token event is decided by the token facade, and `stop`, `level` and `cache purge` are not audited by any producer found
  in this investigation.
- What exists is a log line: `REMOTE EXECUTION <uri> from <ip>` and `<name> (<ip>) requested command: <redacted>`
  (`ConsoleEndpoint.kt:70,78`), and for the websocket `CLI | <name>@<host>> <redacted>` (`ConsoleWebSocketHandler.kt:105`). Log
  lines are not the audit trail: they rotate, are not queryable and are not in the JSON lines sink (`add-audit-json-sink`).
- Redaction: `sensitiveInput` hides all arguments of `token-generate` with a secret and `token-regenerate` with a secret
  (`token/infrastructure/AccessTokenCommands.kt:71,195`). Any parse error redacts everything (`console/CommandExecutor.kt:73-75`).
  `PreparedCommand.redactedCommand` (`console/PreparedCommand.kt`) is the value that is safe to store.

### The stdin console in the container

- The image has no `USER` and runs `entrypoint.sh`, which drops to `reposilite` with `runuser` and `exec java` (`Dockerfile:109-118`,
  `entrypoint.sh:148`). The stdin console reads whatever stdin the container has. With no stdin it logs a warning and returns
  (`console/ConsoleThread.kt:36-40`).
- `docker attach` and `docker run -it` therefore work today and must keep working.

## What the CLI can reuse, and what is missing

Reuse: `POST /api/console/execute` as the transport of exec mode, and the version 1 socket of Decision 2 for the REPLs; the token and
permission model; `redactedCommand`; the
picocli command tree; the OpenAPI document `docs/openapi/ingot-openapi.json` for the client contract.

Gaps as first written. The first four rows are on `main` now (#412, #427, #434). The rest is open:

| Gap | Addition | State |
| --- | --- | --- |
| Command and argument listing | `GET /api/console/commands` | on main (#412) |
| Structured result, exit status | `?structured=true` on execute | on main (#427) |
| Dangerous commands | `dangerous` flag, `409` until `confirm=true` in structured mode | on main (#427) |
| Audit of console commands | `CONSOLE_COMMAND_EXECUTED` | on main (#434) |
| Per-command answer and session on the socket for the REPLs | the version 1 protocol, Decision 2 | to do |
| Node of a command in the listing | `scope` on each descriptor, Decision 2.7 | to do |
| Distinguishing the CLI in the audit | `client` from the hello | to do |

Not added (open): quoting of arguments with spaces (question 8), per-command permissions (question 7), rate limit (question 9).

## Decision 1: the CLI is a separate client, not a refactor of the server console

The working interpretation of "REPL container" is a client and an image that runs it. The server's stdin console stays unchanged,
because the drop-in contract names `docker attach` and the default behaviour. A JLine REPL inside the server would change what
`docker attach` shows and what a piped stdin does. Alternative in open question 1.

## Decision 2: one shared WebSocket protocol for the dashboard web REPL and the CLI REPL

The maintainer decided that the dashboard console and the `ingot` REPL use the same WebSocket session. Exec mode is not part of
this decision. It stays on HTTP (`POST /api/console/execute?structured=true`), one request per command line, because a script
needs a request that ends with an exit code, and a process that stops at the first failure does not need a session.

The first revision of this change chose one HTTP request per REPL line, for two reasons: a dropped connection would lose no session,
and a load balancer in front of several replicas would need no affinity. Both are handled by the protocol below (see 2.6 and 2.7),
so the REPL uses the socket. The decision is numbered 2 and its parts are 2.1 to 2.9.

### 2.1 Endpoint and negotiation

- The path is unchanged, `/api/console/sock` (`console/application/ConsolePlugin.kt:103-112`). It is not registered on the worker
  target (`ConsolePlugin.kt:101-102`, `DeployTarget.WORKER`), and it stays that way.
- Negotiation happens in band, in the first frame. A first frame that starts with `Authorization:` runs the existing code path of
  `ConsoleWebSocketHandler`, unchanged. A first frame that is a JSON object with `"type":"hello"` opens protocol version 1. Any other
  first frame is refused as today.
- Why in band and not a `Sec-WebSocket-Protocol` subprotocol: a subprotocol has to be selected during the upgrade, which changes the
  upgrade of every legacy client. An in-band hello leaves the upgrade untouched, and a legacy client never sends a hello.

### 2.2 Handshake

Client to server:

```json
{"type": "hello", "protocols": [1], "client": "ingot-cli/1.0.0", "logLevel": "INFO",
 "resume": {"boot": "8f2c1e", "seq": 412}, "auth": {"scheme": "token", "value": "ci-rotation:<secret>"}}
```

- `protocols`: the versions the client speaks. The server picks the highest it supports. No common version: `error` with code
  `unsupported_protocol` and `supported: [1]`, then close with 1002.
- `auth`: optional. `scheme` `token` takes `name:secret`, the string `INGOT_TOKEN` holds. `scheme` `session` takes a session JWT, the
  value of `Authorization:Session` in the legacy protocol. Without `auth` the session cookie of the upgrade request is used. A browser
  sends that cookie on its own, so the dashboard sends no secret.
- `client`: the product and version, at most 64 characters. The server stores it as the audit `client`.
- `logLevel`: optional. The default is the server's visible threshold. A client can raise it, never lower it below that threshold.
- `resume`: optional, see 2.6.

Server to client:

```json
{"type": "welcome", "protocol": 1, "node": "ingot-0", "version": "<server version>", "listing": 1,
 "capabilities": ["execute", "complete", "confirm", "log"], "identity": "ci-rotation", "boot": "8f2c1e", "seq": 418, "replay": "resumed"}
```

- `identity` is the token name or the session's user, never the secret. `node` is the instance id of `StructuredExecutionResponse.node`.
  `boot` is a random id made at process start. `seq` is the last log sequence number at the time of the welcome. `replay` is
  `resumed` or `full` (2.6).
- The server sets no hello deadline. A version 1 client is announced only by the hello frame itself (2.1), and a socket that sends no hello stays open as it does on upstream, because legacy sockets keep upstream behaviour (drop-in contract, maintainer decision 104). No `timeout` error is sent for a silent socket.

### 2.3 Envelope

- Every frame is one UTF-8 text frame holding one JSON object with a `type`. A request that expects an answer carries an `id` chosen
  by the client, and every answer to it carries the same `id`.
- The protocol version is fixed by the handshake. Frames carry no version of their own. A client ignores fields it does not know, so
  the server can add fields to version 1 without a new version.
- An unknown `type` gets an `error` with code `unknown_type` and the request `id`. The connection stays open.
- Liveness uses WebSocket ping and pong control frames, not JSON.
- An inbound frame over 4 KiB gets an `error` with code `too_long` and the connection stays open. A command line over
  `MAX_COMMAND_LENGTH` (1024 characters) gets the same error, as the server refuses it today.

Client to server:

| Type | Fields | Answer |
| --- | --- | --- |
| `execute` | `id`, `command`, `confirm` (optional, default `false`) | `result`, or `confirm` first for a dangerous command |
| `complete` | `id`, `line`, `cursor` (optional, default end of line) | `complete` |
| `confirm` | `id` (the `execute` it answers), `accept` | the `result` of that `execute` |

Server to client:

| Type | Fields | Meaning |
| --- | --- | --- |
| `result` | `id`, `status`, `exitCode`, `output` (lines with `stream`), `data`, `node`, `confirmed` | the answer to `execute`, the same fields as the structured HTTP answer plus `confirmed` |
| `confirm` | `id`, `command`, `reason` | a dangerous command waits for the user; the arguments of a `sensitive` command are never sent |
| `complete` | `id`, `replace` (`start`, `end`), `candidates` | completion from the command listing (Decision 3) |
| `log` | `seq`, `node`, `level`, `text`, `dropped` | one log line; `dropped` counts the lines lost before it (2.8) |
| `error` | `id` (optional), `code`, `message`, `retryable` | a refused or failed request, or a connection-level problem |

- At most one `execute` runs per connection. A second one gets `error` with code `busy` and `retryable: true`. `complete` is answered
  at once and is not queued behind an `execute`. Log frames interleave with everything.
- A declined confirmation (`accept: false`, or no answer within 120 seconds) runs nothing. It returns a `result` with `exitCode` 3,
  `status` `FAILED`, `confirmed` `false` and one `stderr` line that says the command did not run.
- `error` codes: `unauthorized`, `unsupported_protocol`, `timeout`, `not_hello`, `unknown_type`, `too_long`, `busy`, `rate_limited`,
  `slow_consumer`, `shutdown`, `internal`.
- `complete` is answered from the same listing as `GET /api/console/commands`: names, aliases, options and fixed values. Values from
  data (token names, repository ids) are not completed in version 1 (open question 6).

### 2.4 Authentication and authorization

- A token in the hello (`scheme` `token`) passes the same check as the legacy first message: `ConsoleAuthenticator.authenticate`, which
  includes the brute-force limit and the console area. Nothing new is decided here.
- A browser session has no secret in the hello. The handler reads the session cookie of the upgrade request, as the SSE handler does
  (`console/infrastructure/ConsoleSseHandler.kt:100-112`). A browser sends that cookie on any cross-site WebSocket too, so a
  connection that authenticates by cookie must send an `Origin` header that names the host the request reached, as a trusted proxy
  forwards it. Otherwise it is closed with 1008 (the guard against cross-site WebSocket hijacking). A token in the hello is not
  affected, because a page on another site cannot read it.
- The principal is checked again before each `execute` and before each `confirm` is accepted. A revoked token, a removed console area
  or an expired session closes the connection with 1008 and an `error` with code `unauthorized`. A session also closes at its expiry
  time, from the clock. A check at the handshake alone would keep a revoked REPL open for as long as it stays connected. The cluster
  rule "principal revocation takes effect on the next request" (`openspec/specs/cluster-coordination`) is kept this way.
- The permission is unchanged: console access is the permission area `console` (node `ingot.admin.console`) for every command (open question 7, settled 2026-11-17). A missing area
  answers `unauthorized`, as the websocket and SSE do today. The HTTP route answers 403 (open question 16).

### 2.5 Compatibility with the existing handler

- The legacy branch is the code that runs today, and the new branch is added beside it. Every legacy behaviour stays: raw log lines as
  text frames, the `keep-alive` echo, the `ErrorResponse` text and disconnect on failed authentication, a command that runs with no
  answer on the socket, and the audit event with `confirmed` false and no `client`. A golden test pins each of them.
- A legacy server that receives a version 1 hello answers its usual "missing credentials" error and disconnects. The CLI cannot tell
  that from a wrong token on the socket, so on that answer it asks `GET /api/console/commands` over HTTP. A `404` means an older server:
  exit code `6`. Any other answer means the token is refused: exit code `4`.
- The dashboard's proxy (`reposilite-frontend/server/lib/console-socket.js`) relays text frames both ways and forwards the `cookie`,
  `origin` and `authorization` headers. It needs no change for version 1. It handles text only, and version 1 uses text only.
- `GET /api/console/log` (SSE) and `POST /api/console/execute` keep their shapes. The dashboard stops using SSE for its console when the
  web REPL ships. Other SSE clients are unaffected.

### 2.6 Reconnect

- A connection is one session. A client that loses it reconnects with a backoff that starts at 1 second, doubles, stops at 30 seconds and
  has jitter. It does not reconnect after 1008 or `unsupported_protocol`. After 1001 (`shutdown`) it waits one backoff step.
- Log continuity: every `log` frame has a `seq`, unique within a boot. On reconnect the client sends `resume` with the `boot` and `seq`
  of the last frame it holds. If the boot matches, the server replays the cached lines with a greater `seq`, then continues live
  (`replay` `resumed`). If it does not match, because the process restarted or another replica answers, the server replays its whole
  line cache and says `full`. The client de-duplicates on `(node, seq)`. The line cache must keep the `seq` each line was stamped with.
  Today the cache replay (`ConsoleWebSocketHandler.kt:103-105`) stores no sequence, so this is a change to the cached logger.
- Commands: an `execute` without a `result` at the drop is reported as unknown. The client never sends it again. The audit event
  `CONSOLE_COMMAND_EXECUTED` is the record of what ran. Version 1 keeps no results for a resume (open question 18).
- A `confirm` pending at the drop is declined by the server when the connection closes, so nothing runs.
- Command history and the input line live in the client, so a reconnect keeps them. The history is per browser tab for the dashboard
  and per terminal session for the CLI.

### 2.7 Load balancers and several replicas

- A WebSocket is one TCP connection, which the balancer keeps on one replica for its life. No affinity is needed for correctness.
- A command runs on the replica that receives the `execute`. The token check, the database and most commands behave the same on every
  replica. The per-replica commands, `level`, `status`, `failures` and `stats` (and `cache`, to be verified per action), act on the
  replica they reach. Each `result` names its `node`. Each descriptor of the listing gets an optional `scope` (`node` or `cluster`).
  This is additive, so the listing stays at version 1. The REPL shows the `node` of the welcome in its prompt.
- Log lines: a session shows the lines of its own replica. With the bus and console fan-out (`openspec/specs/cluster-coordination`,
  "Console log fan-out"), it also shows the lines of other replicas, each with its `node`. On the database transport, fan-out needs
  `INGOT_CLUSTER_CONSOLE=true`. Every `log` frame carries `node`, so the client can label a line in either case.
- A reconnect may reach another replica. The `boot` check then fails and the new replica's cache is replayed as `full`. The client marks
  the change of `node`. Sticky sessions help log continuity and are not required (open question 14).
- The proxies in front need WebSocket upgrade support and an idle timeout longer than the ping interval of 2.9. The dashboard already
  reaches the server through the Nitro proxy, which upgrades `/api/console/sock`.

### 2.8 Backpressure and log rate limits

- Each connection has a bounded outbound queue of 1000 frames and one writer thread. The log subscriber does not write to the socket.
  It offers the line to the queue and returns. Today the subscriber sends inside the logging thread (`ConsoleWebSocketHandler.kt:91-99`),
  so one slow client can hold up whichever thread logs. The legacy path keeps that behaviour. Version 1 does not have it.
- When the queue is full, `log` frames are dropped and counted. The next delivered `log` frame carries the count in `dropped`.
  `result`, `confirm`, `complete` and `error` frames are never dropped. If the queue fills with them, the server closes the connection
  with 1013 and an `error` with code `slow_consumer`.
- Log rate: at most 200 lines per second per connection, a token bucket with a burst of 500. Lines over the rate are dropped and counted.
  A raised `logLevel` cuts the volume further. The tests inject the clock.
- Commands: one `execute` in flight per connection (`busy`). No command rate limit is added here (open question 9).

### 2.9 Timeouts

- Handshake: no deadline. A silent socket stays open as upstream does (2.2).
- Liveness: the server sends a WebSocket ping every 20 seconds and closes a connection that has not answered with a pong within 60 seconds.
- Confirmation: 120 seconds, then declined (2.3).
- Command: no server-side timeout, because a command such as `settings-reencrypt` may run for a while. The client shows the elapsed time.
  Ctrl-C in the CLI stops the wait for the result and leaves the command running on the server, and the CLI says so. Version 1 has no
  cancel frame (open question 18).
- Close codes: 1000 normal. 1001 `shutdown`, sent after an `error` with code `shutdown`. 1002 protocol error. 1008 refused (authentication,
  handshake, unsupported protocol, expired session). 1009 a frame the transport refuses. 1013 `slow_consumer`.

### 2.10 What stays

- The stdin console is not touched, so `docker attach` keeps working (Decision 1, "The stdin console in the container").
- `--disable-interactive-console` keeps its meaning. The socket is served by the server process, as it is today.
- Exec mode and the HTTP endpoints are unchanged. Nothing is added to the server's environment: the protocol needs no variable, and any
  limit that becomes configurable later gets an `INGOT_` name with the current value as its default.

## Decision 3: `GET /api/console/commands`

Response, authenticated like execute, requires the console area:

```json
{
  "version": 1,
  "commands": [
    {
      "name": "token-generate",
      "aliases": [],
      "description": "Generate a new access token",
      "dangerous": false,
      "sensitive": true,
      "parameters": [{"label": "<name>", "index": "0", "required": true, "description": "Access token name"}],
      "options": [{"names": ["--secret", "-s"], "description": "...", "arity": "1"}],
      "subcommands": []
    }
  ]
}
```

Built from `ConsoleFacade.getCommands()`: picocli `CommandSpec` already has names, aliases, options and positional parameters, so
the mapping is a read of existing data. `sensitive` mirrors `sensitiveInput`. `dangerous` is a new member on `ReposiliteCommand`
with a default of `false`, so existing plugin commands compile and behave as before (a binary default through a Kotlin interface
default, checked by the existing plugin ABI gate, see `gate-plugin-abi`). The server marks `stop`, `token-revoke`,
`token-regenerate`, `settings-reencrypt`, `settings-decrypt` and `cache` with the action `purge`. Because `cache` is one command with an action
argument, `dangerous` is a function of the parsed arguments, not a constant; the member is therefore
`fun isDangerous(arguments: List<String>): Boolean`, default `false`. The listing shows the command as dangerous when any form of it is.

The listing carries a `version` so the CLI can refuse a format it does not know. Each descriptor has an optional `scope`, `node` or
`cluster`, which tells a REPL whether the command acts on the replica it reaches (Decision 2.7). The field is additive, so the version
stays `1`. Completion of arguments with values that come from
data (token names, repository ids) is not part of this change. The CLI completes names, aliases, options and the fixed values
that picocli declares (`completionCandidates`). Open question 6.

## Decision 4: structured execute

`POST /api/console/execute?structured=true` with the same body (the command line, text/plain, as today) returns:

```json
{
  "status": "SUCCEEDED",
  "exitCode": 0,
  "output": [{"stream": "stdout", "text": "line"}],
  "data": null,
  "node": "ingot-0"
}
```

- `exitCode`: `0` for `SUCCEEDED`, `1` for `FAILED`, `2` for a parse error or an unknown command (picocli convention), `3` when
  the command needs confirmation and did not get it (the same case also answers `409`).
- `output` keeps the lines in order. `stream` is `stdout` for output and `stderr` for lines of a `FAILED` command, which is the
  only distinction the existing `CommandContext` supports without touching commands.
- `data` is `null` unless the command implements the optional interface `StructuredCommand`, which adds
  `fun data(context): Any?`. This change implements it for `tokens`, `status` and `stats`, where the lines are a table or a list. All other
  commands answer with `output` only. The CLI prints `data` for `--output json` when present and wraps `output` otherwise, so
  `--output json` always yields valid JSON.
- `node` is the instance id from `ClusterSettings` (system property `ingot.cluster.instance`, `INGOT_CLUSTER_INSTANCE_ID`, host
  name), as the audit sink does.
- ANSI sequences: structured mode strips them from `output`. The plain mode does not change.
- Without `structured=true`, the response, the status codes and the ANSI text stay as today.

Alternative rejected: change `ExecutionResponse` in place. A new field is additive for a JSON reader, but the OpenAPI diff gate
(`add-openapi-diff-gate`) and generated clients would still see a changed schema, and the contract of an existing endpoint is the thing
this fork promises not to move.

## Decision 5: confirmation of dangerous commands

In structured mode, if `isDangerous(arguments)` is true and `confirm=true` is absent, the server answers `409` with
`{"error": "confirmationRequired", "command": "<name>", "reason": "<description>"}` and runs nothing. With `confirm=true` it runs.
In plain mode nothing changes: the old endpoint never asks, as today.

On the version 1 WebSocket (Decision 2.3) the same rule holds. A dangerous `execute` gets a `confirm` frame, and runs only after an
accepted `confirm` answer or with `confirm` true in the `execute` frame. The legacy text protocol never asks, as today.

The CLI maps this to:

- REPL on a terminal: print the command and its reason, read `y/N` from the terminal (not from piped stdin), default no.
- Exec mode: fail with exit code `3` unless `--yes` is given. `--yes` sends `confirm=true`. On a terminal with no `--yes`, exec asks like
  the REPL. In a pipe it never asks.
- `--yes` is accepted for every command and means nothing for a command that is not dangerous.

The confirmation is client policy backed by a server check. It is not a security boundary: a token with the console area can send
`confirm=true` itself. Its job is to stop an accidental `stop` or `token-revoke` in a script or a REPL typo. The audit event records
that the call was confirmed.

## Decision 6: audit event

New action `CONSOLE_COMMAND_EXECUTED`, emitted by the HTTP execute route and the websocket handler for every command that reaches
`PreparedCommand.execute`, including a failed command, and for a rejected one (a `409`) with `success=false`. Fields:

- actor: the token name (`name@host` in the websocket handler today is a log label; the audit actor is the token name alone), address
  from the existing `recordActorAddress` rule.
- `path`: not used. `repository`: not used.
- `details`: `{"command": "<redactedCommand>", "channel": "http|websocket", "client": "<User-Agent product, at most 64 chars>", "confirmed": true|false, "exitCode": 0}`.
  The command line is the redacted one. A command with `sensitiveInput` stores the command name and `<redacted>`.
- Not emitted for stdin: the process owner is not a principal and the log already shows it.
- Already emitted today by the legacy websocket branch (#434), with `confirmed` `false` and no `client`. Version 1 sets `client` from the
  hello and `confirmed` from the answer to the `confirm` frame, and writes the event at the same point as the legacy branch.

The event goes through `AuditFacade.record` (`audit/AuditFacade.kt:87`), so the JSON lines sink gets it with no change. A failing audit
write never fails the command, as for every other event (the sink and `recordSafely` already swallow errors).

Why a new action and not a log line: an auditor must be able to answer "who ran `settings-reencrypt` or `stop`" from the table. See
`encrypt-settings-at-rest`, which emits `SETTINGS_SECRETS_REENCRYPTED` from inside the command, and records the rotation with key
ids. This event is the outer record, the settings event the inner one. They do not replace each other.

## Decision 7: the CLI

- Language and libraries: Kotlin on the JVM, picocli for the command line of the CLI itself (already a project dependency, no
  new licence), JLine 3 for the REPL (line editing, history, completion, a terminal check), the JDK `java.net.http.HttpClient` for
  HTTP (TLS, proxies, a custom `SSLContext`). Jackson or the project's existing JSON library for parsing.
  A new dependency must pass the allowed-licenses check (`allowed-licenses.json`).
- Module: a new Gradle module `ingot-cli`, package `net.onelitefeather.ingot.cli`, artifact `net.onelitefeather.ingot:ingot-cli`. The
  existing `reposilite-*` names are kept for what exists; this part is new code, so it takes the new namespace, in line with
  `CLAUDE.md`. Open question 2 asks whether it should be its own repository.
- It does not depend on `reposilite-backend`. It shares nothing but the JSON shape, which `docs/openapi/ingot-openapi.json` pins and the
  OpenAPI diff gate guards.

### Configuration

Resolution order, first hit wins: command-line flag, environment variable, the selected context in the config file, default.

| Setting | Flag | Environment | Config file |
| --- | --- | --- | --- |
| Server URL | `--url` | `INGOT_URL` | `url` |
| Token (name:secret) | none | `INGOT_TOKEN` | none |
| Token file | `--token-file <path>` | `INGOT_TOKEN_FILE` | `tokenFile` |
| Credentials helper | `--credential-helper <cmd>` | `INGOT_CREDENTIAL_HELPER` | `credentialHelper` |
| Context | `--context <name>` | `INGOT_CONTEXT` | `current` |
| CA bundle | `--ca-cert <path>` | `INGOT_CA_CERT` | `caCert` |
| Skip verification | `--insecure` | `INGOT_INSECURE` | none |
| Output | `--output text\|json` | `INGOT_OUTPUT` | `output` |
| Colour | `--color auto\|always\|never` | `NO_COLOR`, `INGOT_COLOR` | `color` |
| Timeout | `--timeout <seconds>` | `INGOT_TIMEOUT` | `timeout` |

- The config file is `$XDG_CONFIG_HOME/ingot/config.yaml`, else `~/.config/ingot/config.yaml`, else `INGOT_CONFIG`. It holds
  contexts: `name`, `url`, `tokenFile`, `credentialHelper`, `caCert`. It holds no secret, and the CLI refuses a file that stores a token value inline
  and says to use `tokenFile`.
- No `REPOSILITE_` fallback is added: these variables are new, so there is nothing to stay compatible with. `INGOT_` only.
- `INGOT_URL` must be a base URL. A context path is kept (`https://host/ingot`).
- The CLI never reads `configuration.cdn` or the server data directory.

### Token handling

- The token is `name:secret`, as the endpoint expects (basic auth, `ConsoleEndpoint.kt:45`). It is read from `INGOT_TOKEN`, the file named by
  `INGOT_TOKEN_FILE` (first line, trimmed), or the credentials helper (a program that prints `name:secret` on stdout, run with no
  arguments through `ProcessBuilder`, never through a shell). There is no `--token` flag, so the secret cannot land in shell history or
  `ps`.
- A token file with permissions wider than owner-only produces a warning on POSIX systems, not an error.
- The CLI never prints the token, not in `--verbose`, not in an error, not in a stack trace. It sends the secret in the
  `Authorization` header only, never in a URL.
- The CLI refuses to send credentials over plain `http` to a non-loopback host unless `--insecure-http` is given. Open question 5 on
  whether to refuse hard.
- `Ctrl-C` in a prompt for a confirmation answers no.

### REPL

- Transport: one WebSocket session, the version 1 protocol of Decision 2. The REPL sends no HTTP request per line. Exec mode is the
  only client of the HTTP execute endpoint. A server without version 1 gives exit code `6` (Decision 2.5), and the REPL points to
  `ingot exec`. There is no HTTP fallback for the REPL (open question 17).
- Prompt `ingot (<context>)> `, history in `$XDG_STATE_HOME/ingot/history` (mode `0600`, never records a line the server marks
  `sensitive`, and a `sensitive` command is not saved to history on the client either).
- Completion: a `complete` frame per Tab, answered from the listing (Decision 2.3). The first listing of the session is cached, and the
  builtin `:reload` asks again. The completer never sends a frame per keystroke.
- Builtins start with `:`, so they cannot collide with a server command: `:reload`, `:context <name>`, `:output text|json`, `:quit`.
  `exit` and `quit` are not builtins and are sent to the server as typed (the server answers `Unknown command`).
- Multi-line output prints as received. A long output is not paged.
- `Ctrl-C` discards the current input line, and stops the wait for an in-flight `result` on the client only: the server may still
  complete the command, and the CLI says so. The connection stays open. `Ctrl-D` on an empty line leaves with exit code `0`.
- Colour only when stdout is a terminal and `NO_COLOR` is unset. Server ANSI sequences are kept when colour is on and stripped otherwise.
- Against a server without version 1 (Decision 2.5): the CLI prints one line that names the missing protocol, exits with code `6`, and
  never runs a command. The older text-only REPL is not kept, because it would be a second REPL with no confirmations.
- Reconnects follow Decision 2.6. While the socket is down the prompt says so, and a line typed then is refused, not queued.

### Exec

- `ingot exec <command> [args...]`: the arguments are joined with single spaces into one command line (the server splits on spaces,
  `CommandExecutor.kt:118`). An argument that contains a space is refused by the CLI with exit code `2` and a clear message, instead of being split silently.
- `ingot exec -` or `ingot exec < file`: one command per line, blank lines and lines starting with `#` skipped.
- `--output json` prints one JSON object per command (JSON lines when several), the structured response plus `command` and `node`.
- Exit codes: `0` success, `1` the command failed, `2` usage, parse error or an unknown command, `3` confirmation required and not
  given, `4` authentication failed or not permitted, `5` server unreachable or timed out, `6` the server is too old for a needed feature.
  With several commands the CLI stops at the first non-zero code, unless `--keep-going`, and then exits with the highest.
- A prompt appears only when stdin and stdout are terminals. In a pipe the CLI never waits for input.
- Nothing is written to stdout except command output (and JSON), so `ingot exec tokens | ...` works. Notices and warnings go to stderr.

## Decision 8: the container image

- Image `ghcr.io/onelitefeathernet/ingot-cli`, entrypoint `ingot`, default command `[]` (so `docker run -it ghcr.io/.../ingot-cli`
  opens the REPL when `INGOT_URL` is set, and prints usage otherwise).
- This is a new image, so the "no `USER`" rule of the server image does not apply: it has no volume, no `PUID`, nothing to chown.
  It runs as a non-root user `ingot` (uid 10001) with a read-only-friendly layout (config under `/home/ingot`, history under
  `$XDG_STATE_HOME`), and works with `readOnlyRootFilesystem` when `HOME` is a writable `emptyDir`.
- Build rules from `CLAUDE.md`: build stage pinned to `--platform=$BUILDPLATFORM` (a jar has no machine code), run stage built per
  target for `linux/amd64` and `linux/arm64`, base images pinned by tag and digest (the same JRE base as `Dockerfile:40`, so Renovate
  raises one digest for both), SBOM and provenance (`sbom: true`, `provenance: mode=max`) on releases only, nothing on nightlies.
  The nightly push for this image is gated by the existing `changes` job (`.github/workflows/release-please.yml:77-100`): the deny list is
  extended so a change to `ingot-cli/**`, the CLI Dockerfile or the console API files counts as relevant, and anything unknown still counts.
- It is built beside `ingot-dashboard` (`release-please.yml:639-690`) with the same retry and registry back-off, and tested by
  the "Container images" job: start the CLI image against the server image from the same run, run `ingot exec status` and
  `ingot exec help`, and assert exit code and output. That job also checks the server image is untouched (the three drop-in questions of
  `CLAUDE.md`).
- A jar `ingot-cli-<version>.jar` (shaded, runnable) is attached to each release and signed like the other release artefacts
  (`sign-release-artifacts`), with its SBOM (`attach-release-sboms`).
- GraalVM native image: decided by open question 4 (settled 2026-11-17). The jar and the JRE-based image ship, because it is the path that
  works on both architectures with no QEMU build, and a native binary needs a build per target architecture, which the repository
  avoids on purpose (`CLAUDE.md`, "How the images are built"). Task 8.3 records the GraalVM evaluation, and a native binary is added
  only if the build sustains it.

## Security

- **Token handling**: see "Token handling". The secret travels in a header over TLS, is never logged by the CLI, and on the server
  `redactedCommand` keeps secrets out of logs and the audit event.
- **Permission**: the CLI needs a token with the console area (`ingot.admin.console`) and nothing more. Every command still checks
  whatever it checked before. The CLI does not widen anything, and the new listing endpoint needs the same area. Operators should
  create a dedicated token for the CLI so audit shows `ci-rotation` and not a person.
- **Which permission runs which command**: today one permission covers all of them. Open question 7 proposes keeping it, and tracks per-command
  nodes with `add-permission-nodes` and `introduce-authorizer-chain`.
- **Rate limits**: failed authentication is limited per address (`AuthenticationFacade.kt:190-205`). There is no limit for a valid token. The
  proposal keeps it that way and relies on `add-overload-protection` for request limits; open question 9.
- **Audit**: every remote command is an event with the token name (Decision 6).
- **Dangerous commands**: Decision 5.
- **WebSocket sessions**: the handshake authenticates once, and every `execute` and accepted `confirm` checks the principal again
  (Decision 2.4). A cookie-authenticated connection requires a matching `Origin` (cross-site WebSocket hijacking). Frames are
  limited to 4 KiB, a connection has one `execute` in flight, and the outbound queue is bounded (Decision 2.8). A token travels in the
  hello, over TLS, like the legacy first message, and is never echoed in a frame, an error or the audit event.
- **Output**: the console logs command output through the logger (finding 2). The CLI does not change what is logged. Secrets that a
  command prints once (a generated token secret) appear in the response to the caller and also in the server log today; the CLI
  does not make this worse, and a follow-up should stop it (not in this change, mentioned here).
- **Input**: the CLI rejects a line over `MAX_COMMAND_LENGTH` (1024) before sending, the server rejects it as before.
- **TLS**: the default trust store plus an optional extra CA. `--insecure` disables verification, prints a warning on stderr every run, and
  is not available through a config file.
- **No secrets echoed**: the confirmation prompt shows the command name and the redacted form, never the arguments of a `sensitive` command.

## Alternatives considered

- **Rebuild the server's stdin console as a JLine REPL.** Rejected as the main path: it changes `docker attach` and piped stdin, which
  the drop-in rule protects. Kept as open question 1 for the interactive case only, behind a switch.
- **One HTTP request per REPL line.** Superseded by Decision 2, which the maintainer chose: the dashboard console and the REPL share one
  WebSocket session. The reasons for HTTP per line were no session lost on a drop and no affinity behind a load balancer. Decision 2.6
  and 2.7 cover both reasons.
- **Use the websocket only for the dashboard, and HTTP for the REPL.** Rejected by the maintainer's decision: two protocols for one
  console would need two sets of tests for the same confirmation, completion and log behaviour.
- **Generate a client from OpenAPI.** Rejected for the first version: three endpoints do not need a generator, and the command tree
  is data, not a schema. The OpenAPI file is still the contract that the diff gate protects.
- **A shell wrapper around `curl`.** Rejected: no completion, no exit codes, no TLS handling, secrets in shell variables.
- **Spawn a second JVM in the server image** (`ingot` command inside the server container). Rejected for this change: it gives no
  isolation from the server and no `kubectl run` flow. A later option is to add the jar to the server image under another name;
  it would not touch the contract (open question 10).

## Conflicts

Open pull requests that touch the same files, at the time of writing:

- #275 `docs(gitbook): add a hardening guide and complete the console command reference` edits
  `docs/gitbook/reference/console-commands.md`, which the documentation pull request of this change also edits. Land #275 first, or rebase.
- #237 `fix(identity): declare the session cookie on routes that accept it` edits
  `console/infrastructure/ConsoleEndpoint.kt` and `docs/openapi/ingot-openapi.json`. The console API pull request edits both.
  Land #237 first.
- #247 and #231 touch `audit/api/AuditAction.kt` and, with #247, `docs/gitbook/reference/audit-events.md`. The audit pull request
  adds one enum constant and one row, so conflicts are small, but they will occur.
- #268, #266, #264, #261, #260, #257 edit `.github/workflows/ci.yml`; #256 edits `.github/workflows/release-please.yml`. The CI pull request
  edits both and should be written after they merge, and the open workflow changes (#268 builds the pull request images once and
  reuses them) decide how the CLI image is built in CI.
- #234 and #220 edit `settings.gradle.kts` (new search plugin modules). The CLI module adds one `include` line, trivial to rebase.
- `docs/gitbook/reference/environment-variables.md` is edited by #277, #263, #247, #239 and #234. The documentation pull request adds rows and should come last.
- No open pull request edits `console/CommandExecutor.kt`, `console/ConsoleFacade.kt` or `console/api/*`.
- Open proposals that interact: `encrypt-settings-at-rest` (the two commands it adds are the first `dangerous` ones and need the same
  `isDangerous` hook; whichever lands second adopts it), `add-audit-json-sink` (receives the new action), `add-permission-nodes` and
  `introduce-authorizer-chain` (per-command permissions), `add-overload-protection` (limits).

## Open questions (settled 2026-11-17)

Every question is settled with its recommended default on 2026-11-17. The tasks and the specs implement these answers. No question is
left open in this change.

1. **Server stdin console as a REPL.** Resolved: no. The CLI is additive and `docker attach` keeps working. A JLine REPL behind
   `INGOT_CONSOLE_REPL=true` is not planned.
2. **Where the CLI lives.** Resolved: a Gradle module `ingot-cli` in this repository, in the directory `ingot-cli`, one release, one CI.
   The CLI is not moved to its own repository, so task group 6 stays here.
3. **Language.** Resolved: Kotlin on the JVM, to reuse picocli and the toolchain.
4. **Native binary.** Resolved: ship the jar and the JRE image. A GraalVM native binary is added only if task 8.3 shows that the build
   sustains it, and the outcome is written down either way.
5. **Plain http.** Resolved: warn on stderr when credentials would travel over `http` to a non-loopback host.
6. **Argument completion from data.** Resolved: complete names, options and fixed values only. Completion of token names, repository
   ids and cache targets is not in version 1.
7. **Per-command permission.** Resolved: console access is the permission area `console` (node `ingot.admin.console`) for every command,
   as the maintainer decided for this change. A CLI token can run every command. Per-command nodes are not added here.
8. **Quoting.** Resolved: the CLI refuses arguments with spaces. `CommandExecutor.parseCommand` is not changed.
9. **Rate limit for valid tokens.** Resolved: none beyond `add-overload-protection`. A per-token command rate stays off.
10. **Shipping the CLI inside the server image.** Resolved: no. The CLI image is separate, and the `ingot` image is not touched.
11. **Image name.** Resolved: `ghcr.io/onelitefeathernet/ingot-cli`.
12. **Config file format.** Resolved: YAML, with contexts that read like a kubeconfig.
13. **Confirmation of `stop` in the REPL.** Resolved: `stop` is dangerous and asks for confirmation, as in every channel.
14. **Sticky sessions.** Resolved: none required. A reconnect may reach another replica, and the client marks the change of `node`.
    Cookie affinity is documented per ingress and is not the default.
15. **Which replicas a session shows.** Resolved: the own replica, plus the other replicas when console fan-out is on (Decision 2.7).
    The switch stays the only way to show other replicas' lines. Every frame carries `node`, so the dashboard labels the lines.
16. **Authorization wording.** Resolved: the code is the reference. Console access is the permission area `console`, the HTTP route
    answers 403 and the socket answers `unauthorized`. The docs are corrected in task 9.2.
17. **HTTP fallback for the REPL.** Resolved: none. A server without version 1 gives exit code `6`.
18. **Results on resume, and cancel.** Resolved: a command in flight at a drop is reported as unknown, and version 1 has no cancel frame
    and keeps no results for a resume.
19. **Per-replica commands.** Resolved: `level`, `status`, `failures` and `stats` are `scope` `node`, `cache` is checked per action, and
    a plugin command defaults to `cluster`. A plugin command that holds state per replica declares `node` itself.
