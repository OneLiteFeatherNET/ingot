# Design

All paths are relative to `reposilite-backend/src/main/kotlin/com/reposilite/` unless they start with another top-level name.

## What exists on main

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
| `/api/console/sock` | `console/infrastructure/ConsoleWebSocketHandler.kt:67-117` | first message `Authorization:name:secret` or `Authorization:Session <jwt>` | the shared log stream, not a per-command answer |
| `GET /api/console/log` (SSE) | `console/infrastructure/ConsoleSseHandler.kt:62` | header or session cookie | the shared log stream |
| dashboard tab | `reposilite-frontend/src/components/console/ConsoleView.vue`, `store/console/connection.js:49,73-78,127` | the dashboard session | SSE for output, HTTP execute for input |

Findings that matter for a CLI:

1. The websocket is not a request and response channel. A command sent on it runs and writes its lines to the logger
   (`ConsoleWebSocketHandler.kt:104-106`), and every subscriber of the log stream sees them, mixed with unrelated log lines. A
   client cannot tell which lines belong to its command. The dashboard moved to HTTP execute for input for this reason.
   The CLI must use `POST /api/console/execute`.
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

Reuse: `POST /api/console/execute` as the one transport for both modes; the token and permission model; `redactedCommand`; the
picocli command tree; the OpenAPI document `docs/openapi/ingot-openapi.json` for the client contract.

Missing, and added by this change:

| Gap | Addition |
| --- | --- |
| Command and argument listing | `GET /api/console/commands` |
| Structured result, exit status | `?structured=true` on execute |
| Dangerous commands | `dangerous` flag, `409` until `confirm=true` in structured mode |
| Audit of console commands | `CONSOLE_COMMAND_EXECUTED` |
| Distinguishing the CLI in the audit | a `channel` detail: `http`, `websocket`, `stdin` is not audited |

Not added (open): quoting of arguments with spaces (question 8), per-command permissions (question 7), rate limit (question 9).

## Decision 1: the CLI is a separate client, not a refactor of the server console

The working interpretation of "REPL container" is a client and an image that runs it. The server's stdin console stays unchanged,
because the drop-in contract names `docker attach` and the default behaviour. A JLine REPL inside the server would change what
`docker attach` shows and what a piped stdin does. Alternative in open question 1.

## Decision 2: HTTP execute is the transport, one request per command

- Exec mode sends one request per command line. A script that reads commands from stdin sends them in order and stops at the first
  failure unless `--keep-going` is given.
- REPL mode sends one request per entered line. There is no session, so a dropped connection loses nothing, and a load balancer in
  front of several replicas is fine. Commands act on the database, not on one replica, except `level`, `status` and `failures`,
  which are per replica; structured mode names the replica in the `node` field (Decision 4).
- The websocket is not used, for finding 1. SSE is not used in this change.

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

The listing carries a `version` so the CLI can refuse a format it does not know. Completion of arguments with values that come from
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

- Prompt `ingot (<context>)> `, history in `$XDG_STATE_HOME/ingot/history` (mode `0600`, never records a line the server marks
  `sensitive`, and a `sensitive` command is not saved to history on the client either).
- Completion: command names and aliases from `/api/console/commands`, cached for the session, refreshed with the builtin `:reload`.
  Options and fixed values of the command at the cursor. The completer never sends a request per keystroke.
- Builtins start with `:`, so they cannot collide with a server command: `:reload`, `:context <name>`, `:output text|json`, `:quit`.
  `exit` and `quit` are not builtins and are sent to the server as typed (the server answers `Unknown command`).
- Multi-line output prints as received. A long output is not paged.
- `Ctrl-C` discards the current input line, and cancels an in-flight request on the client only: the server may still complete the
  command, and the CLI says so. `Ctrl-D` on an empty line leaves with exit code `0`.
- Colour only when stdout is a terminal and `NO_COLOR` is unset. Server ANSI sequences are kept when colour is on and stripped otherwise.
- Against a server without the new endpoints: the CLI prints a one-line notice, runs in plain mode, completes only `help` output
  parsed from the text, and has no confirmations (it warns that dangerous commands will run at once).

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
- GraalVM native image: evaluated in open question 4. The default is the jar and the JRE-based image, because it is the path that
  works on both architectures with no QEMU build, and a native binary needs a build per target architecture, which the repository
  avoids on purpose (`CLAUDE.md`, "How the images are built").

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
- **Use the websocket for the REPL.** Rejected: no per-command result (finding 1).
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

## Open questions

1. **Server stdin console as a REPL.** The maintainer's wording can also mean making the server's own console a JLine REPL. Default: no,
   the CLI is additive and `docker attach` keeps working. Alternative: a REPL for the stdin console behind `INGOT_CONSOLE_REPL=true`.
2. **Where the CLI lives.** Default: a Gradle module `ingot-cli` in this repository (shared release, one version, one CI,
   the API and its client change in one pull request). Alternative: its own repository, which gives it an independent release rhythm and
   a smaller clone, but splits API and client across two repositories and needs a compatibility matrix. Directory name: `ingot-cli`
   (new code, new namespace) versus `reposilite-cli` (module naming rule).
3. **Language.** Default: Kotlin on the JVM, to reuse picocli and the toolchain. Alternative: Go, a small static binary and fast start, at
   the price of a second toolchain, a second supply-chain pipeline and no code sharing.
4. **Native binary.** Default: ship the jar and the JRE image; add a GraalVM native binary only if the build sustains it
   (reflection config for picocli and JLine, one build per architecture without QEMU needs native runners). Document the outcome. A
   native binary is not a requirement of this change.
5. **Plain http.** Default: warn on stderr when sending credentials over `http` to a non-loopback host. Alternative: refuse unless
   `--insecure-http`.
6. **Argument completion from data.** Default: complete names, options and fixed values only. Alternative: a server hook that completes
   token names, repository ids and cache targets, which needs a new `ReposiliteCommand` member and a request per completion.
7. **Per-command permission.** Default: keep the single node `ingot.admin.console`, and note that a CLI token can run every command. Alternative:
   nodes such as `ingot.admin.console.read` (status, stats, tokens, failures, help) and `ingot.admin.console.write`, designed with
   `add-permission-nodes`.
8. **Quoting.** The server splits on single spaces. Default: the CLI refuses arguments with spaces. Alternative: add quote handling to
   `CommandExecutor.parseCommand`, which changes parsing for every channel and needs its own change.
9. **Rate limit for valid tokens.** Default: none beyond `add-overload-protection`. Alternative: a per-token command rate (for example 60 per minute)
   with a `429` and a `Retry-After`, off by default.
10. **Shipping the CLI inside the server image.** Default: no, the CLI image is separate. Alternative: add the jar to the `ingot` image for
    `kubectl exec`. It grows the server image for a use the CLI image already serves.
11. **Image name.** Default: `ghcr.io/onelitefeathernet/ingot-cli`, as requested.
12. **Config file format.** Default: YAML (contexts read like a kubeconfig). Alternative: the project's `.cdn` format, or JSON.
13. **Confirmation of `stop` in the REPL.** Default: dangerous, asks. Alternative: `stop` is excluded because the REPL already makes it explicit.
