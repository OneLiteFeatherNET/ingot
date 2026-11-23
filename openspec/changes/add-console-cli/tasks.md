# Tasks

Each group is one or more commits of one Conventional Commits type and scope, and each code group ships as its own pull request, branched
from the remote main branch that contains the merged proposal. Tests are written first and seen to fail before the production code exists.
A test that pins behaviour which already exists (the legacy protocol) is written first and is green before any production change; its
job is to stay green. The merge order of the code pull requests is the table in `proposal.md`.

Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock (inject a `java.time.Clock` or a fake scheduler), no fixed ports,
no shared mutable state, a temporary directory per test, bounded waits through latches, assertions with messages. The CLI and the
WebSocket are tested against stubs on an ephemeral port in process, never against a real server in unit tests. The dashboard uses fake
timers. Gradle runs go through `heavy` with `--max-workers=2`, targeted and never the full suite. Unit tests run as
`:reposilite-backend:test --tests '<pattern>'`, integration tests as `:reposilite-backend:integration --tests '<pattern>'`. Task boxes are
ticked in the follow-up pull request, never in a code pull request.

Groups 2 and 3 are merged on `main` (#412, #427, #434). Their tasks stay as the record of what was built, and they are not work to do.

## 1. Proposal

- [x] 1.1 `docs(openspec): run the console repl over a shared websocket`: this revision and nothing else, under
  `openspec/changes/add-console-cli/`. Verify: `openspec validate add-console-cli --strict` passes, and a grep for em and en dashes over the
  change prints nothing.

## 2. Server API (merged: `feat(console)`, #412 and #427)

- [x] 2.1 `test(console)`: command listing tests. Merged in #412.
- [x] 2.2 `test(console)`: structured execute tests, exit codes, streams, `data`, plain mode unchanged. Merged in #427.
- [x] 2.3 `test(console)`: confirmation tests for dangerous commands. Merged in #427.
- [x] 2.4 `feat(console)`: listing, structured execute, `isDangerous`, the `StructuredCommand` interface and the OpenAPI additions. Merged in
  #412 and #427. The `scope` member is new and is task 4.8.
- [x] 2.5 Code pull request `feat(console): add a command listing and structured results to the console api`. Merged as #412 and #427.

## 3. Audit (merged: `feat(audit)`, #434)

- [x] 3.1 `test(audit)`: audit tests for the HTTP and websocket channels. Merged in #434.
- [x] 3.2 `feat(audit)`: `CONSOLE_COMMAND_EXECUTED` and its emission. Merged in #434. The `client` value from the hello is task 4.6.
- [x] 3.3 Code pull request `feat(audit): audit remote console commands`. Merged as #434.

## 4. Server protocol (`feat(console)`, `test(console)`)

- [x] 4.1 `test(console)`: golden tests of the legacy text protocol on `/api/console/sock`. A first frame `Authorization:...` gets raw log
  lines, a `keep-alive` is answered, a command gets no answer frame, failed authentication gets the error and a disconnect, the audit
  event has `confirmed` false and no `client`. Verify: green on `main` before any production change, and still green after 4.8.
- [ ] 4.2 `test(console)`: failing tests for the handshake: a hello negotiates protocol 1, a list without 1 closes with 1002 and
  `unsupported_protocol`, no hello deadline; a silent socket stays open as upstream, an unknown `type` gets
  `unknown_type` and keeps the connection, a frame over 4 KiB gets `too_long`, and the worker target does not upgrade the path. Verify:
  red on the missing branch.
- [x] 4.3 `test(console)`: failing tests for `execute`, `result` and `confirm`: the `result` has the fields of the structured HTTP answer
  plus `confirmed`; a second `execute` gets `busy`; `complete` is answered while an `execute` runs; a dangerous command waits for an accepted
  `confirm`; a declined or unanswered one (120 seconds, fake scheduler) runs nothing and answers exit code `3`; the `confirm` frame of
  `token-regenerate ci s3cr3t` does not contain the secret. Verify: red before 4.8.
- [x] 4.4 `test(console)`: failing tests for the log frames: `seq` is unique within a boot, the cached lines keep the `seq` they were
  stamped with, `logLevel` can be raised and not lowered below the visible threshold, a line from another replica carries its `node`,
  a `resume` for the same boot replays after its `seq` and says `resumed`, a `resume` for another boot replays the cache and says `full`.
  Verify: red before 4.8.
- [x] 4.5 `test(console)`: failing tests for backpressure: a writer that never reads does not block the thread that logs; a full queue of
  log frames drops and counts them, and the next delivered frame states `dropped`; a `result` is never dropped; a queue full of results
  closes with 1013 and `slow_consumer`; 1000 lines in the same instant deliver at most 500. Verify: red before 4.8, and each test waits on a
  latch, never on time.
- [ ] 4.6 `test(console)`: failing tests for authentication: a token in the hello goes through `ConsoleAuthenticator`; a cookie with a
  matching `Origin` is accepted and a cookie with another `Origin` closes with 1008; a token revoked after the handshake is refused on the
  next `execute` and closes with 1008; a session closes at its expiry time (fake clock); no frame, error or audit event contains the secret;
  the audit `client` is the hello `client`. Verify: red before 4.8.
- [ ] 4.7 `test(console)`: failing tests for the listing `scope` (`status` is `node`, a plugin command defaults to `cluster`), for the
  liveness pings (a socket whose ping write fails is dropped; there is no pong deadline because Javalin 7.2.2 exposes no pong events) and for `shutdown` (`error` then 1001). Verify: red before 4.8.
- [ ] 4.8 `feat(console)`: add the version 1 branch beside the legacy branch of `ConsoleWebSocketHandler`, with the bounded queue and one
  writer per connection, the handshake, the timers through an injected scheduler, the sequence numbers in the cached logger, the `scope`
  member of the listing, the `client` in the audit event, and the additive description of the socket frames in
  `docs/openapi/ingot-openapi.json`. The legacy class body does not change. Verify: 4.1 to 4.7 are green, the OpenAPI diff gate reports
  additions only, and the plugin ABI gate reports no break.
- [ ] 4.9 Code pull request, title `feat(console): add the shared websocket protocol to the console`. Read
  `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections from the operator's point of view: a REPL that stops losing its place, and a legacy
  script that does not notice anything. No file under `openspec/`, no session link, no attribution, no em or en dash. Verify with
  `gh pr view -R OneLiteFeatherNET/ingot --json title,body,files`.

## 5. Dashboard web REPL (`feat(ui)`, `test(ui)`)

- [x] 5.1 `test(ui)`: failing Vitest tests for the socket client in `reposilite-frontend/src/store/console/`: hello and welcome handling, the
  `resume` with boot and `seq`, de-duplication by `(node, seq)`, an `execute` without a `result` at a drop shows as unknown and is not sent
  again, `busy` is shown, 1008 stops the reconnect, the backoff steps (1 s, doubling, 30 s cap) with fake timers. Verify: red before 5.3.
- [ ] 5.2 `test(ui)`: failing component tests for `ConsoleView.vue`: Enter sends `execute` and no HTTP request; Tab sends one `complete`;
  typing sends no frame; the confirmation dialog defaults to cancel and hides the arguments of a sensitive command; a failed result shows
  exit code 1 and its stderr lines as errors; `data` renders as a table; history stays per tab (a stubbed `sessionStorage`) and skips a
  sensitive line; the level filter runs in the client. Verify: red before 5.3.
- [x] 5.3 `test(ui)`: failing test that the Nitro socket proxy (`reposilite-frontend/server/lib/console-socket.js`) relays version 1 text frames
  in both directions and forwards the `cookie` and `origin` headers unchanged. Verify: green on the current proxy, which needs no change.
- [ ] 5.4 `feat(ui)`: replace the HTTP input and the SSE stream of `reposilite-frontend/src/store/console/connection.ts` and
  `reposilite-frontend/src/components/console/ConsoleView.vue` with the socket client, completion, the confirmation dialog, structured
  results and the connection state. Keep the ANSI converter and the scroll helpers. Verify: 5.1 to 5.3 are green, the frontend tests and
  build pass, and the dashboard end-to-end tests (`dashboard-e2e-tests`) are updated where they assume SSE.
- [ ] 5.5 `test(ui)`: extend the dashboard end-to-end test to run `status` over the socket and to decline a `token-revoke` confirmation,
  run through `heavy`. Verify: the scenario passes against a server started by the test, and the token is still valid afterwards.
- [ ] 5.6 Code pull request, title `feat(ui): run the dashboard console as a web repl`. Same description rules as 4.9.

## 6. The CLI, exec mode (`feat(cli)`, `test(cli)`)

- [x] 6.1 `test(cli)`: failing tests for configuration resolution as a pure function of flags, an environment map and a config file in a
  temporary directory: order, contexts, refusal of an inline token, no `--token`, no `REPOSILITE_` fallback.
- [x] 6.2 `test(cli)`: failing tests for the token sources (env, file, credentials helper as a stub program), the file permission warning,
  and a check that no output stream ever contains the secret.
- [x] 6.3 `test(cli)`: failing tests for exec mode against a stub HTTP server: exit codes `0` to `6`, `--yes`, `--keep-going`, JSON lines,
  stdin script with comments, an argument with a space, no prompt in a pipe, stdout holds only command output, and exit `6` when
  `GET /api/console/commands` answers `404`.
- [x] 6.4 `test(cli)`: failing tests for TLS: a custom CA against a test server with a generated certificate in a temporary directory, the
  `--insecure` warning, no insecure switch in the config file.
- [ ] 6.5 `feat(cli)`: add the Gradle module `ingot-cli` to `settings.gradle.kts` with its first code. Implement configuration, tokens, exec
  mode and TLS with picocli and `java.net.http.HttpClient`. Without a terminal, bare `ingot` prints usage and exits with code 2, because the
  interactive mode arrives in group 7. Verify: 6.1 to 6.4 are green and `./gradlew :ingot-cli:build` passes.
- [x] 6.6 Code pull request, title `feat(cli): add the ingot command line client`. Read the template and fill it as in 4.9. The CLI stays in this
  repository (open question 2, settled), so the title keeps its type and scope and nothing moves.

## 7. The CLI REPL over the shared websocket (`feat(cli)`, `test(cli)`)

- [x] 7.1 `test(cli)`: failing tests for the REPL with an in-process WebSocket stub on an ephemeral port, driven by a scripted dumb terminal:
  the hello carries protocol 1 and the token; no HTTP request is made during a session; Tab sends one `complete`; `stop` asks `y/N`,
  defaults to no and sends `accept` false unless the answer is yes; a sensitive line is not written to the history file; `Ctrl-C` stops the
  wait for a result and the connection stays open; `Ctrl-D` on an empty line exits with 0; a drop during a command reports it as unknown
  and does not send it again; the reconnect carries the `resume` of the last `seq`; a missing-credentials answer with `404` from
  `GET /api/console/commands` exits with 6 and runs nothing; a refused token exits with 4; no colour when stdout is not a terminal.
  Verify: red before 7.2, and the backoff uses an injected clock.
- [ ] 7.2 `feat(cli)`: add JLine 3 and the REPL on the protocol client: line editing, history with owner-only permissions, completion from
  `complete` frames, multi-line output, the node in the prompt, the confirmation prompt, and the reconnect of `console-websocket`. Add the
  JLine licence to `allowed-licenses.json` if needed. Verify: 7.1 is green and `./gradlew :ingot-cli:build` passes.
- [ ] 7.3 Code pull request, title `feat(cli): run the repl over the shared websocket protocol`. Same description rules as 4.9.

## 8. Packaging and images (`build(cli)`, `ci(cli)`, `test(cli)`)

- [ ] 8.1 `test(cli)`: failing smoke test that runs the shaded jar with `--version` and `exec status` against the stub server, and a
  bare `ingot` without a terminal, to check the entry point. Verify: red before 8.2.
- [ ] 8.2 `build(cli)`: a runnable shaded jar `ingot-cli-<version>.jar`, a CLI `Dockerfile` with the build stage pinned to
  `--platform=$BUILDPLATFORM`, the JRE base pinned by tag and digest, a non-root user, entrypoint `ingot`. Add the base image to `renovate.json`
  and the module version to `release-please-config.json`. Verify: 8.1 is green, and the image builds for both architectures locally through
  `heavy`, and the REPL starts.
- [ ] 8.3 Evaluate a GraalVM native binary (open question 4): record build time, size and the reflection configuration cost in the pull
  request description, and only add it if the build sustains it. Verify: the result is written down either way.
- [x] 8.4 Code pull request, title `build(cli): package the cli as a jar and a container image`. Same description rules as 4.9.
- [ ] 8.5 `ci(cli)`: extend the "Container images" job to start the CLI image against the server image of the same run, assert `status`
  and `help` in exec mode, and assert that a version 1 hello gets a `welcome`. Add the second architecture build on pull requests like the
  server image, and extend the `changes` filter deny list for the CLI paths. Verify: the three drop-in questions of `CLAUDE.md` still pass
  for the server image.
- [x] 8.6 `ci(cli)`: release job for `ghcr.io/onelitefeathernet/ingot-cli` with `sbom: true` and `provenance: mode=max` on releases only, the
  registry back-off of the dashboard image, jar upload, signing and SBOM attachment. Verify: a dry run on a branch builds without pushing.
- [x] 8.7 Code pull request, title `ci(cli): build and publish the cli image`. Same description rules as 4.9.

## 9. Documentation (`docs(console)`, `test(docs)`)

The reference pages exist on `main` (#454) under `docs/gitbook/references/` and `docs/gitbook/manage/operate/`, not under
`docs/gitbook/reference/` as first planned.

- [x] 9.1 `test(docs)`: failing check that every `INGOT_` name in the CLI source appears in `docs/gitbook/references/environment-variables.md`
  or in the CLI reference, and that every link in the changed pages resolves. Verify: red on the names that are not documented yet.
- [x] 9.2 `docs(console)`: update `docs/gitbook/references/console-commands.md` with the version 1 protocol (frames, authentication, limits,
  close codes, reconnect), correct its authorization wording to what the code checks (open question 16), and update the row of
  `/api/console/sock` in `docs/gitbook/references/http-api/http-api.md`. Verify: 9.1 is green for the console page.
- [x] 9.3 `docs(console)`: update `docs/gitbook/references/cli.md` and `docs/gitbook/manage/operate/run-the-cli.md` for the REPL on the shared
  protocol, the exit code `6` against an older server, the `scope` column of the command listing, and the reconnect behaviour. Add every new
  variable to `docs/gitbook/references/environment-variables.md`. Verify: 9.1 is green.
- [x] 9.4 Code pull request, title `docs(console): document the websocket protocol and the cli repl`. Same description rules as 4.9.

## 10. Drop-in check

- [ ] 10.1 No commit. After 4.9 and 6.6, the Container images job stays green with the unmodified upstream compose file, the adoption of an
  existing `/app/data`, and `PUID`, `PGID` and `REPOSILITE_OPTS`. The legacy WebSocket golden test (4.1) stays green, and a `docker attach`
  to the server still reaches its stdin console. If any check fails, the change is wrong, not the test.
