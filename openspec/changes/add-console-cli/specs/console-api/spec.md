## Purpose

A remote client can list the console commands, run one and get a structured result with an exit status, and dangerous commands can be
confirmed. Every remote command is audited. The existing endpoints and the stdin console do not change. The WebSocket gains a
versioned protocol for the REPLs (see `console-websocket`), and the dashboard console becomes a web REPL (see `dashboard-console`).

## ADDED Requirements

### Requirement: The existing console surface is unchanged

The server SHALL keep the request and response shape, the status codes and the permission (`ingot.admin.console`) of
`POST /api/console/execute` without `structured=true`, of `/api/console/sock` and of `/api/console/log`. The stdin console SHALL
keep reading commands from standard input unless `--disable-interactive-console` is given.

#### Scenario: Plain execute

- **WHEN** a token with the console area posts `status` to `/api/console/execute`
- **THEN** the body is `{"status": ..., "response": [...]}` exactly as before and no confirmation is asked

#### Scenario: Stdin console

- **WHEN** the server starts with a terminal attached and a line `status` is typed
- **THEN** the command runs and its output goes to the log as before

### Requirement: Command listing

`GET /api/console/commands` SHALL return, to a principal with the console area, a versioned list of every registered command with its
name, aliases, description, options, positional parameters, subcommands, a `dangerous` flag, a `sensitive` flag and a `scope` of `node`
or `cluster`. A principal without the console area SHALL be refused like on execute.

#### Scenario: Listing

- **WHEN** a token with the console area requests `/api/console/commands`
- **THEN** the response has `version` `1` and an entry for `token-generate` with `sensitive` true

#### Scenario: Not permitted

- **WHEN** a token without the console area requests the listing
- **THEN** the response is an authorization error and no command name is disclosed

### Requirement: Structured execute

`POST /api/console/execute?structured=true` SHALL run the command and answer `status`, `exitCode`, `output` (lines with a `stream`),
`data` (or null) and `node`. `exitCode` SHALL be `0` for success, `1` for a failed command, `2` for an unknown command or a parse error and
`3` when confirmation is missing. ANSI sequences SHALL be removed from `output`.

#### Scenario: Failed command

- **WHEN** a structured request runs `level nonsense` and the command fails
- **THEN** `exitCode` is `1` and the failure lines have the stream `stderr`

#### Scenario: Unknown command

- **WHEN** a structured request runs `nosuchcommand`
- **THEN** `exitCode` is `2`

#### Scenario: Data

- **WHEN** a structured request runs `tokens`
- **THEN** `data` holds the tokens as a JSON list and `output` still holds the text lines

### Requirement: Confirmation of dangerous commands

In structured mode, a command that reports itself as dangerous for its arguments SHALL NOT run unless the request has `confirm=true`. The
server SHALL answer `409` with `confirmationRequired` and run nothing. `stop`, `token-revoke`, `token-regenerate`, `settings-reencrypt`,
`settings-decrypt` and `cache` with the action `purge` SHALL be dangerous. A command from a plugin SHALL not be dangerous unless it says so.

#### Scenario: Missing confirmation

- **WHEN** a structured request runs `token-revoke ci` without `confirm=true`
- **THEN** the answer is `409`, the token is not revoked and an audit event with `success` false is written

#### Scenario: Confirmed

- **WHEN** the same request has `confirm=true`
- **THEN** the token is revoked and the audit event records `confirmed` true

#### Scenario: Plain mode never asks

- **WHEN** the same command is sent without `structured=true`
- **THEN** it runs at once, as it does today

### Requirement: Remote console commands are audited

The server SHALL write the audit action `CONSOLE_COMMAND_EXECUTED` for every command that reaches execution through `/api/console/execute`
or the websocket, with the token name as actor, the channel, the confirmation flag, the exit code and the redacted command line. A command
with sensitive input SHALL store only its name and the redaction marker. A failure to write the event SHALL NOT fail the command. The stdin
console SHALL NOT write this event.

#### Scenario: Secret hidden

- **WHEN** a token runs `token-regenerate ci s3cr3t`
- **THEN** the event stores `token-regenerate <redacted>` and the secret appears nowhere in it

#### Scenario: Audit store unavailable

- **WHEN** the audit write fails while a command runs
- **THEN** the command still completes and returns its result
