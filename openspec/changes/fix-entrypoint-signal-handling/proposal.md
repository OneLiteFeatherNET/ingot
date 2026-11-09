# Proposal

## Why

Ingot is stopped many times in its life: by `docker stop`, by a rolling update, by a node drain. Each stop is supposed to
end with a clean shutdown: statistics are flushed, uploads are closed and the database leases are released. That only
happens if the server receives the termination signal itself.

When the container starts as root, which is the default and the path that applies PUID and PGID, the entrypoint starts the
server through `runuser` (`entrypoint.sh:71`). `runuser` stays in front of the JVM, so the signal reaches the wrapper and not
the server. The reading of the util-linux source is that the JVM then gets no clean stop within the grace period and is
killed. That is not reproduced yet. The first task proves it, and this change only goes ahead if it shows up. The start as
a non-root user (`user: "977:977"`) execs the JVM directly and is not affected.

## What Changes

- **Privilege drop.** The root branch of `entrypoint.sh` replaces `exec runuser -u reposilite --` with
  `exec setpriv --reuid=<PUID> --regid=<PGID> --init-groups --`. The server then runs as PID 1 in the container and receives
  `SIGTERM` itself. `setpriv` replaces the process, so no wrapper stays behind.
- **Environment.** `runuser` sets `HOME`, `USER`, `LOGNAME` and `SHELL` for the new account. The entrypoint exports the same
  four values explicitly, so the server sees the environment it sees today.
- **Unchanged.** The argument list of the JVM, `JAVA_OPTS`, `INGOT_OPTS` and `REPOSILITE_OPTS`, the PUID and PGID handling,
  the ownership fix of `/app` and `/var/log/reposilite`, and the non-root branch.
- **Regression test.** A container test starts the image as root with the default settings, stops it with a 30 second
  timeout and asserts exit code 143 and the shutdown log line. The test is written first and fails on the current entrypoint.

### Out of scope

- An init process such as `tini` or `dumb-init`, and a signal-forwarding shell wrapper. The alternatives in `design.md`
  explains why `setpriv` is enough.
- The shutdown order inside the server, the statistics flush and the drain before readiness fails. Those belong to
  `fix-statistics-shutdown-flush` and `add-graceful-drain`.
- The Helm chart's `terminationGracePeriodSeconds` and `lifecycle`, and the Dockerfile. Neither changes.
- Running the image without root by default. The Dockerfile keeps its `USER`-less start.

## Capabilities

### New Capabilities

- `container-entrypoint`: how the image starts the server, which process receives the termination signal, and which
  identity, environment and arguments the server gets.

## Impact

- **Type and scope.** The change ships under `fix(docker)`. The test commit uses `test(docker)` and the fix commit uses
  `fix(docker)`. The pull request title, squash-merged onto main, is:

  `fix(docker): deliver SIGTERM straight to the server process`

- **Breaking marker.** No breaking change. No configuration key, environment variable, volume, manifest or plugin changes.
  A stop that used to end in a kill now ends in a normal shutdown with exit code 143. The title carries no `!`.
- **Documentation.** No file under `docs/gitbook/` changes, because no setting, environment variable or manifest changes.
- **Code.** `entrypoint.sh` and `reposilite-backend/src/containerTest/` (one test class). `ci.yml` is not changed, because the
  Container images job already runs `:reposilite-backend:containerTest`.
- **Dependencies.** None on another change. Wave A changes do not touch the same files. `add-graceful-drain` (F8) and
  `fix-statistics-shutdown-flush` (F6) depend on this change, because their shutdown checks only run once the signal arrives.
- **Assumption.** `setpriv` comes from the `util-linux` package, which already provides `runuser` in the base image, so the
  image gains no package. The container test confirms this.

## Verification

- The container test of group 2 fails on the current entrypoint and passes after group 3.
- The existing container tests pass unedited, including the PUID and PGID cases, the uid 1000 case and the upgrade rehearsal.
- `shellcheck entrypoint.sh` reports nothing new.
- `openspec validate fix-entrypoint-signal-handling --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/fix-entrypoint-signal-handling` prints nothing.
