# Proposal

## Why

The maintainer asked to turn the console into a REPL container and to provide a CLI. Today an operator has three ways to run a
console command, and none of them suits a script or a remote shell:

- The stdin console of the server process. It only exists where the process has a terminal, so in a container it means
  `docker attach` or `kubectl attach`, which attaches to the one server process and can disturb it
  (`console/ConsoleThread.kt:33-59`).
- The dashboard console tab. It needs a browser and a login (`reposilite-frontend/src/components/console/ConsoleView.vue`).
- `POST /api/console/execute` with a token. It works from a script, but a script gets raw text lines, no exit code per command, no
  list of commands to complete from, and no way to know that `token-revoke` or `stop` should be confirmed first
  (`console/infrastructure/ConsoleEndpoint.kt:69-83`, `console/api/ExecutionResponse.kt`).

So an operator on Kubernetes who wants to rotate a token or run `settings-reencrypt` (see `encrypt-settings-at-rest`) has to
exec into the server pod or hand-write `curl` calls and parse text. A CI job that must fail when a command fails has to guess
from the text. Nothing in the audit log says who ran a console command (see `design.md`, "Audit").

## What Changes

Everything is additive. The server console, the dashboard tab and the existing endpoints keep their behaviour byte for byte.

- **A standalone `ingot` CLI** that talks to a running Ingot over HTTP with an access token. Two modes:
  - REPL: interactive, with history, tab completion of command names and arguments fetched from the server, multi-line
    output, colour only on a terminal, `Ctrl-C` cancels the current line, `Ctrl-D` leaves.
  - Exec: non-interactive. `ingot exec <command> [args]`, or commands read from standard input. Exit codes, `--output json`,
    and no prompt unless the process runs on a terminal.
- **Server API additions, opt-in per request** (`feat(console)`):
  - `GET /api/console/commands`: the command tree (names, aliases, descriptions, options, parameters, a `dangerous` flag and a
    `sensitive` flag), for completion and help.
  - `POST /api/console/execute?structured=true`: the same command, answered as `{status, exitCode, output, data}`. Without the
    parameter the response is unchanged.
  - A confirmation step for dangerous commands in structured mode: `409` with a `confirmationRequired` problem unless
    `confirm=true`.
  - A new audit action `CONSOLE_COMMAND_EXECUTED` for every command run through a remote channel, with the token name.
- **A new container image** `ghcr.io/onelitefeathernet/ingot-cli`, for `kubectl run -it --rm` and `docker compose run`, for
  `linux/amd64` and `linux/arm64`, with SBOM and provenance on releases.
- **A jar on every release**, and a GraalVM native binary only if the build sustains it (open question 4).
- **Configuration**: `INGOT_URL`, `INGOT_TOKEN`, `INGOT_TOKEN_FILE`, a credentials helper, contexts in a config file, and TLS
  options including a custom CA. The token is never a command-line argument.
- **Documentation**: a reference page for the CLI, a how-to for running it on Kubernetes and in CI, and an update to
  `docs/gitbook/reference/console-commands.md`.

### Does not change (drop-in)

- The stdin console stays, including `docker attach`. `--disable-interactive-console` keeps its meaning
  (`ReposiliteParameters.kt:135-136`, `console/application/ConsolePlugin.kt:81`).
- `/api/console/execute`, `/api/console/sock` and `/api/console/log` keep their request and response shapes, their authentication
  and the `ingot.admin.console` permission.
- The dashboard console tab is untouched.
- The `ingot` server image is not touched. The server reads no new variable for the CLI and no existing one changes.
- Package names stay `com.reposilite.*` where code is added to the server. The CLI is new code.

### Out of scope

- Replacing the server's stdin console with a JLine REPL (open question 1).
- Per-command permission nodes. One node, `ingot.admin.console`, guards every command today (open question 7).
- Commands that cannot run over HTTP, such as the startup parameters of `ReposiliteParameters`.
- Streaming the live server log through the CLI. The REPL may later use `/api/console/log`; this change does not.
- A Windows installer or package-manager formulas (Homebrew, Scoop). The jar and the container are the delivery.

## Conventional Commits and pull requests

The change mixes types, so it ships as separate pull requests. The proposal pull request is `docs(openspec): propose
add-console-cli`. Code pull requests, in this order:

| Order | Title | Content |
| --- | --- | --- |
| 1 | `feat(console): add a command listing and structured results to the console api` | endpoint, structured mode, confirmation, `dangerous` and `sensitive` metadata |
| 2 | `feat(audit): audit remote console commands` | `CONSOLE_COMMAND_EXECUTED`, wired in the HTTP and websocket channels |
| 3 | `feat(cli): add the ingot command line client` | new Gradle module, REPL and exec modes, configuration |
| 4 | `build(cli): package the cli as a jar and a container image` | Dockerfile, release artefacts |
| 5 | `ci(cli): build and publish the cli image` | CI job, release job, nightly filter, SBOM and provenance |
| 6 | `docs(console): document the cli and the console api additions` | gitbook pages, environment variable reference |

Pull requests 1 and 2 are server changes and merge on their own. Pull request 3 uses 1 for completion and structured output and
falls back to plain text against a server without them. Pull requests 4 and 5 need 3.

## Impact

- Capabilities: `console-api`, `cli-client` and `cli-image`, all added.
- Code: `console/` in `reposilite-backend`, `audit/api/AuditAction.kt`, a new Gradle module, `settings.gradle.kts`,
  `.github/workflows/ci.yml`, `.github/workflows/release-please.yml`, `renovate.json`, `release-please-config.json`,
  `docs/gitbook`, `docs/openapi/ingot-openapi.json`.
- Compatibility: nothing breaks. A server without these additions still answers the old endpoints, and the CLI says so.
- Related changes: `encrypt-settings-at-rest` (`settings-reencrypt` and `settings-decrypt` are dangerous commands and the first
  users of the confirmation step), `add-audit-json-sink` (the new audit action reaches the sink without further work),
  `add-permission-nodes` and `introduce-authorizer-chain` (per-command permissions, open question 7), `add-overload-protection`
  (request limits, see "Security" in the design).
