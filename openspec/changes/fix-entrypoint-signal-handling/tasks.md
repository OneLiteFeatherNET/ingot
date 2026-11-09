# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `docker`. The test is written
and seen to fail before the entrypoint changes. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock,
no fixed ports, no shared mutable state, a temporary directory per test, and assertions with messages. Waits on container
start and stop are bounded by the Testcontainers wait strategy and the 30 second stop timeout, not by sleeps. Gradle runs go
through `heavy` with `--no-daemon --max-workers=2`, targeted and never the full suite, for example
`HEAVY_SLOTS=1 heavy ./gradlew :reposilite-backend:containerTest --tests '<pattern>' --no-daemon --max-workers=2`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose fix-entrypoint-signal-handling`: this change and nothing else. Verify:
  `openspec validate fix-entrypoint-signal-handling --strict` passes and the dash grep from the proposal prints nothing.

## 2. Reproduce the signal loss

- [ ] 2.1 `test(docker)`: add a case to `ServerImageContractTest` (or a class beside it) that starts the image as root with
  default settings, waits until the server reports its address, stops it with a 30 second timeout and asserts exit code 143.
  Also assert that the shutdown log line appears; identify that line by running the image once and reading the output, since
  `main` only logs the console shutdown request. Verify: the case fails on the current entrypoint with the observed exit code
  in the message (expected 143, the proof is 137 or a late exit), and the other container tests are unchanged.
- [ ] 2.2 Gate: if 2.1 passes on `main` (exit 143 already), stop here. Write no entrypoint change, report the finding, and
  withdraw this change before any code PR is opened.

## 3. Fix the privilege drop

- [ ] 3.1 `fix(docker)`: in the root branch of `entrypoint.sh`, replace `exec runuser -u reposilite --` with
  `exec setpriv --reuid=$USER_ID --regid=$GROUP_ID --init-groups --`, and export `HOME`, `USER`, `LOGNAME` and `SHELL` for the
  service account before the exec. Verify: the case of 2.1 passes, the existing container tests pass unedited (PUID and PGID,
  uid 1000, data adoption, upgrade rehearsal, legacy `REPOSILITE_OPTS`), `shellcheck entrypoint.sh` reports nothing new, and
  `setpriv` is present in the image (`docker run --rm --entrypoint sh <image> -c 'command -v setpriv'`).

## 4. Pull request

- [ ] 4.1 Open the pull request for groups 2 and 3 with the title `fix(docker): deliver SIGTERM straight to the server process`.
  The description follows the repository template and names the scenario from the user's side: a stop ends with a clean
  shutdown instead of a kill.
