# Proposal

## Why

The maintainer asked to turn the console into a REPL container and to provide a CLI. Today an operator has three ways to run a
console command, and none of them is a good remote shell:

- The stdin console of the server process. It only exists where the process has a terminal, so in a container it means
  `docker attach` or `kubectl attach`, which attaches to the one server process and can disturb it
  (`console/ConsoleThread.kt:33-59`).
- The dashboard console tab. It needs a browser and a login. It has an input line, but every command is a separate HTTP request,
  there is no completion, no confirmation for dangerous commands, and no per-command result
  (`reposilite-frontend/src/components/console/ConsoleView.vue`, `reposilite-frontend/src/store/console/connection.ts`).
- `POST /api/console/execute` with a token. Scripts get exit codes and a list of commands from the structured form, merged as
  #412 and #427. A remote shell still has no session: each line is a separate request, the live log is a second stream, and a
  dropped connection loses the context of the shell.

So an operator on Kubernetes who wants to rotate a token or run `settings-reencrypt` (see `encrypt-settings-at-rest`) wants one
interactive session that shows the command results and the live log together, and that the dashboard and the terminal share.
Nothing in the audit log says who ran a console command before #434, and the audit now covers every remote channel
(see `design.md`, "Audit").

## What Changes

Everything is additive. The server console, the existing endpoints and the stdin console keep their behaviour byte for byte.

- **Already on `main`** (`feat(console)`, `feat(audit)`, merged): the command listing `GET /api/console/commands` (#412), structured
  execution `POST /api/console/execute?structured=true` with exit codes, output streams and `data` (#427, #412), the confirmation
  of dangerous commands in structured mode (`409` until `confirm=true`), and the audit action `CONSOLE_COMMAND_EXECUTED` on the
  HTTP and WebSocket channels (#434). The `ingot` CLI documentation pages are on `main` too (#454). The CLI code is not.
- **A shared WebSocket protocol on `/api/console/sock`** (`feat(console)`). A connection negotiates a protocol version in its first
  frame. A version 1 connection exchanges versioned JSON envelopes: `execute`, `result`, `log`, `complete`, `confirm` and `error`.
  A connection whose first frame is the legacy `Authorization:` line keeps the text protocol exactly as it is today. Design,
  Decision 2 describes authentication (token or session cookie), reconnect, load balancers, backpressure and timeouts.
- **The dashboard console becomes a web REPL** (`feat(ui)`), on the shared protocol: a log pane with level filters, an input line
  with completion from the command listing, history kept per browser tab, structured results (exit code, stdout and stderr, data),
  and a confirmation dialog for dangerous commands. The dashboard keeps its login and does not gain a second authentication.
- **A standalone `ingot` CLI** (`feat(cli)`) that talks to a running Ingot with an access token. Two modes:
  - REPL: interactive, on the same shared WebSocket protocol as the dashboard. Completion, history, colour only on a terminal,
    `Ctrl-C` cancels the current input line, `Ctrl-D` leaves. It needs a server that speaks protocol version 1.
  - Exec: non-interactive over HTTP, as designed. `ingot exec <command> [args]`, or commands read from standard input. Exit codes,
    `--output json`, and no prompt unless the process runs on a terminal.
- **A new container image** `ghcr.io/onelitefeathernet/ingot-cli`, for `kubectl run -it --rm` and `docker compose run`, for
  `linux/amd64` and `linux/arm64`, with SBOM and provenance on releases.
- **A jar on every release**, and a GraalVM native binary only if the build sustains it (open question 4).
- **Configuration**: `INGOT_URL`, `INGOT_TOKEN`, `INGOT_TOKEN_FILE`, a credentials helper, contexts in a config file, and TLS
  options including a custom CA. The token is never a command-line argument.
- **Documentation**: the CLI reference and how-to pages (on `main` since #454, updated for the REPL transport), and the console
  reference for the WebSocket protocol.

### Does not change (drop-in)

- The stdin console stays, including `docker attach`. `--disable-interactive-console` keeps its meaning
  (`ReposiliteParameters.kt:135-136`, `console/application/ConsolePlugin.kt:81`).
- `/api/console/execute` and `/api/console/log` keep their request and response shapes, their authentication and the
  `ingot.admin.console` permission.
- `/api/console/sock` keeps its path, its worker exclusion and its text protocol for every client that sends `Authorization:` as
  its first frame. Such a client sees no change, no new frame and no new close code. Only a connection that opens with a
  version 1 `hello` gets the envelope protocol.
- The dashboard UI changes (it is the web REPL), but no endpoint it uses is removed or changed in its answer. Its login and session
  cookie are unchanged.
- The `ingot` server image is not touched. The server reads no new variable for the CLI and no existing one changes. Any new
  variable of the WebSocket protocol, if one is needed, gets the `INGOT_` prefix and a default that keeps today's behaviour.
- Package names stay `com.reposilite.*` where code is added to the server. The CLI is new code.

### Out of scope

- Replacing the server's stdin console with a JLine REPL (open question 1).
- Per-command permission nodes. One node, `ingot.admin.console`, guards every command today (open question 7).
- Commands that cannot run over HTTP or the WebSocket, such as the startup parameters of `ReposiliteParameters`.
- Streaming the live server log to the CLI exec mode. The REPL streams it over the shared protocol; exec mode does not.
- Resuming a command after a dropped connection. A command in flight at a drop is reported as unknown, never run again.
- A Windows installer or package-manager formulas (Homebrew, Scoop). The jar and the container are the delivery.

## Conventional Commits and pull requests

The change mixes types, so it ships as separate pull requests. This revision is the proposal pull request
`docs(openspec): run the console repl over a shared websocket`. The code pull requests are listed in `tasks.md`. The table below
shows the server, UI and CLI order. Rows marked merged are already on `main`.

| Order | Title | Content | State |
| --- | --- | --- | --- |
| 1 | `feat(console): add a command listing and structured results to the console api` | endpoint, structured mode, confirmation, `dangerous` and `sensitive` metadata | merged (#412, #427) |
| 2 | `feat(audit): audit remote console commands` | `CONSOLE_COMMAND_EXECUTED`, wired in the HTTP and websocket channels | merged (#434) |
| 3 | `feat(console): add the shared websocket protocol to the console` | version negotiation, envelope, authentication, completion, confirmation, log frames, limits | to do |
| 4 | `feat(ui): run the dashboard console as a web repl` | the dashboard console on the shared protocol | to do |
| 5 | `feat(cli): add the ingot command line client` | new Gradle module, exec mode over HTTP, configuration | to do |
| 6 | `feat(cli): run the repl over the shared websocket protocol` | REPL transport, reconnect, history, confirmation | to do |
| 7 | `build(cli): package the cli as a jar and a container image` | Dockerfile, release artefacts | to do |
| 8 | `ci(cli): build and publish the cli image` | CI job, release job, nightly filter, SBOM and provenance | to do |
| 9 | `docs(console): document the websocket protocol and the cli repl` | gitbook pages, environment variable reference | to do |

Rows 3 and 4 do not depend on the CLI. Row 4 needs row 3. Row 6 needs rows 3 and 5. Rows 7 and 8 need 5. Row 3 is the first
protocol change and merges on its own, because legacy clients must keep working before any client uses the new protocol.

## Impact

- Capabilities: `console-api` and `cli-client`, `cli-image` (added), `console-websocket` and `dashboard-console` (added here).
- Code: `console/` in `reposilite-backend`, a new Gradle module, `settings.gradle.kts`, `reposilite-frontend/src/components/console`,
  `reposilite-frontend/src/store/console`, `reposilite-frontend/server/lib/console-socket.js`, `.github/workflows/ci.yml`,
  `.github/workflows/release-please.yml`, `renovate.json`, `release-please-config.json`, `docs/gitbook`,
  `docs/openapi/ingot-openapi.json`.
- Compatibility: nothing breaks. Legacy WebSocket clients, the SSE log, `POST /api/console/execute` and the dashboard login keep
  working. A server without the protocol still answers the old frames, and the CLI REPL says so and exits with code `6`.
- Related changes: `encrypt-settings-at-rest` (`settings-reencrypt` and `settings-decrypt` are dangerous commands and the first
  users of the confirmation step), `add-audit-json-sink` (the new audit action reaches the sink without further work),
  `add-permission-nodes` and `introduce-authorizer-chain` (per-command permissions, open question 7), `add-overload-protection`
  (request limits, see "Security" in the design), `cluster-coordination` (console log fan-out decides which replicas' lines a
  session shows, see Decision 2).
