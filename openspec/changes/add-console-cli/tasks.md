# Tasks

Each group is one or more commits of one Conventional Commits type and scope, and each code group ships as its own pull request, branched
from the remote main branch that contains the merged proposal. Tests are written first and seen to fail before the production code exists.
Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock (inject a `java.time.Clock`), no fixed ports, no shared mutable
state, a temporary directory per test, bounded waits through latches, assertions with messages. The CLI is tested against a stub
HTTP server on an ephemeral port in process, never against a real server in unit tests. Gradle runs go through `heavy` with
`--max-workers=2`, targeted and never the full suite. Unit tests run as `:reposilite-backend:test --tests '<pattern>'`, integration tests as
`:reposilite-backend:integration --tests '<pattern>'`. Task boxes are ticked in the follow-up pull request, never in a code pull request.

## 1. Proposal

- [x] 1.1 `docs(openspec): propose add-console-cli`: this change and nothing else. Verify: `openspec validate add-console-cli --strict`
  passes and a grep for em and en dashes over the change prints nothing.

## 2. Server API (`feat(console)`)

- [x] 2.1 `test(console)`: failing tests for the command listing (names, aliases, options, parameters, `sensitive` from `sensitiveInput`, a
  version field, refusal without the console area). Verify: they fail on the missing endpoint.
- [x] 2.2 `test(console)`: failing tests for structured execute: exit codes `0`, `1`, `2`, `3`; stream names; ANSI stripped; `data` for `tokens`,
  `status`, `stats`; plain mode byte for byte unchanged (a golden test of the old body). Verify: they fail before 2.4.
- [x] 2.3 `test(console)`: failing tests for confirmation: each dangerous command answers `409` and runs nothing without `confirm=true`;
  `cache` is dangerous only for `purge`; a plugin command defaults to not dangerous; plain mode never asks.
- [x] 2.4 `feat(console)`: add `isDangerous(arguments)` to `ReposiliteCommand` with a default of `false`, the optional `StructuredCommand`, the
  listing endpoint, structured execute and the confirmation. Mark the six commands. Update `docs/openapi/ingot-openapi.json` and pass the
  OpenAPI diff gate as additive only. Verify: 2.1 to 2.3 are green and the plugin ABI gate reports no break.
- [x] 2.5 Code pull request, title `feat(console): add a command listing and structured results to the console api`. Read
  `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections, from the operator's point of view (a script that cannot tell a failed command from
  a successful one). No file under `openspec/`, no session link, no attribution, no em or en dash. Verify with `gh pr view --json title,body,files`.

## 3. Audit (`feat(audit)`)

- [x] 3.1 `test(audit)`: failing tests with a fake audit sink: one event per HTTP and websocket command, token name as actor, channel, exit code,
  confirmed flag; redacted line for sensitive commands; no event for stdin; a failing audit write does not fail the command.
- [x] 3.2 `feat(audit)`: add `CONSOLE_COMMAND_EXECUTED`, emit it from `ConsoleEndpoint` and `ConsoleWebSocketHandler`, add the row to
  `docs/gitbook/references/identity/audit-events.md`. Verify: 3.1 is green and the event reaches the JSON lines sink when it is on.
- [x] 3.3 Code pull request, title `feat(audit): audit remote console commands`. Same description rules as 2.5.

## 4. The CLI (`feat(cli)`)

- [x] 4.1 `feat(cli)`: add the Gradle module `ingot-cli` to `settings.gradle.kts` together with its first code in the commit of 4.7, because
  an empty module is not a change of its own. Verify: the module builds.
- [x] 4.2 `test(cli)`: failing tests for configuration resolution as a pure function of flags, an environment map, a config file in a
  temporary directory: order, contexts, refusal of an inline token, no `--token`, no `REPOSILITE_` fallback.
- [x] 4.3 `test(cli)`: failing tests for the token sources (env, file, credentials helper as a stub program), the file permission warning, and a
  check that no output stream ever contains the secret.
- [x] 4.4 `test(cli)`: failing tests for exec mode against a stub server: exit codes `0` to `6`, `--yes`, `--keep-going`, JSON lines, stdin script
  with comments, argument with a space, no prompt in a pipe, stdout holds only command output.
- [ ] 4.5 `test(cli)`: failing tests for the REPL through a scripted terminal (JLine dumb terminal): completion from a listing, no request per
  keystroke, `Ctrl-C`, `Ctrl-D`, confirmation `y/N`, sensitive lines kept out of history, colour only on a terminal, older server fallback.
- [x] 4.6 `test(cli)`: failing tests for TLS: a custom CA against a test server with a generated certificate in a temporary directory, the
  `--insecure` warning, no insecure switch in the config file.
- [x] 4.7 `feat(cli)`: implement the module with picocli, JLine 3 and `java.net.http.HttpClient`. Add the licences to `allowed-licenses.json` if
  needed. Verify: 4.2 to 4.6 are green and `./gradlew :ingot-cli:build` passes.
- [x] 4.8 Code pull request, title `feat(cli): add the ingot command line client`. Same description rules as 2.5. If open question 2 is decided for a
  separate repository, this group moves there and the title keeps its type and scope.

## 5. Packaging and images (`build(cli)`, `ci(cli)`)

- [ ] 5.1 `build(cli)`: a runnable shaded jar `ingot-cli-<version>.jar`, a CLI `Dockerfile` with the build stage pinned to
  `--platform=$BUILDPLATFORM`, the JRE base pinned by tag and digest, a non-root user, entrypoint `ingot`. Add the base image to `renovate.json`
  and the module version to `release-please-config.json`. Verify: the image builds for both architectures locally through `heavy`, and the
  REPL starts.
- [ ] 5.2 Evaluate a GraalVM native binary (open question 4): record build time, size and the reflection configuration cost in the pull request
  description, and only add it if the build sustains it. Verify: the result is written down either way.
- [x] 5.3 Code pull request, title `build(cli): package the cli as a jar and a container image`. Same description rules as 2.5.
- [x] 5.4 `ci(cli)`: extend the "Container images" job to start the CLI image against the server image of the same run and assert `status` and
  `help`; add the second architecture build on pull requests like the server image; extend the `changes` filter deny list for the CLI paths.
  Verify: the three drop-in questions of `CLAUDE.md` still pass for the server image.
- [x] 5.5 `ci(cli)`: release job for `ghcr.io/onelitefeathernet/ingot-cli` with `sbom: true` and `provenance: mode=max` on releases only, the registry
  back-off of the dashboard image, jar upload, signing and SBOM attachment. Verify: a dry run on a branch builds without pushing.
- [x] 5.6 Code pull request, title `ci(cli): build and publish the cli image`. Same description rules as 2.5.

## 6. Documentation (`docs(console)`)

- [x] 6.1 `docs(console)`: `docs/gitbook/references/cli.md` (flags, variables, exit codes, config file), `docs/gitbook/manage/operate/run-the-cli.md`
  (`kubectl run -it --rm`, `docker compose run`, a CI step with `INGOT_TOKEN_FILE`, creating a dedicated token), an update to
  `docs/gitbook/references/console-commands.md` for the listing and structured mode, and every new variable in
  `docs/gitbook/references/configuration/environment-variables.md`. Verify: every `INGOT_` name in the CLI source appears in the reference page.
- [x] 6.2 Code pull request, title `docs(console): document the cli and the console api additions`. Same description rules as 2.5.

## 7. Drop-in check

- [ ] 7.1 No commit. After 2.5 and 3.3, the Container images job stays green with the unmodified upstream compose file, the adoption of an
  existing `/app/data`, and `PUID`, `PGID` and `REPOSILITE_OPTS`. If it fails, the change is wrong, not the test.
