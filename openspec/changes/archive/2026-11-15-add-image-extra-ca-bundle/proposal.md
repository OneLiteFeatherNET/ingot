# Proposal

## Why

An operator who runs Ingot with `docker run` or Compose often has an internal certificate authority. A mirror source, an
OIDC issuer, a database or a NATS broker presents a certificate that the public trust store inside the image does not know,
and the server refuses the connection. Ingot has no setting for an extra CA. The only workaround today is to replace the JDK
trust store through `JAVA_OPTS`, which also drops the public CAs that every other mirror needs, so the operator ends up
maintaining a copy of the full trust store by hand and rebuilding it with every JDK update.

The Kubernetes operator already has the better path. The chart (change `add-chart-network-and-trust`, merged as PR #123)
mounts a ConfigMap or Secret with PEM files at `/etc/ingot/extra-ca` and sets `INGOT_EXTRA_CA_CERTS` to that path. The chart
does not import anything. It leaves the import to the image, and the image does not read the variable yet
(`entrypoint.sh` has no reference to it), so that chart setting has no effect today. A plain Docker or Compose user cannot use
the same mechanism at all.

The next consumer is the planned Nexus import, which connects to the old Nexus over HTTPS. An internal Nexus very often runs
on a self-signed or private-CA certificate, so the import cannot start for those operators without this.

## What Changes

- **Opt-in import at start.** The entrypoint reads `INGOT_EXTRA_CA_CERTS`, with `REPOSILITE_EXTRA_CA_CERTS` as the fallback.
  When it is set, it names a directory. The entrypoint copies the JDK trust store (`$JAVA_HOME/lib/security/cacerts`) to a
  fresh private directory under `${TMPDIR:-/tmp}`, imports every certificate found in the PEM files of that directory into the
  copy with `keytool`, and adds `-Djavax.net.ssl.trustStore=<copy>` and `-Djavax.net.ssl.trustStorePassword=changeit` to the
  JVM options. The public CAs stay trusted because the copy starts as a full copy of the JDK store.
- **Both start paths.** The step runs before the privilege drop, so it applies to the root start and to the `user:` start. The
  copy is readable by the service account and is written only under the temporary directory, so a read-only root filesystem
  works when `/tmp` is writable, which is what the chart provides.
- **Loud failures.** A variable that names a missing or unreadable directory, a directory without a certificate, a file that
  is not a certificate, or a `JAVA_OPTS` that already sets a trust store stops the start with exit code 1 and one line that
  names the variable. A trust setting that silently does nothing would leave the operator debugging TLS errors.
- **Unchanged.** With the variable unset or empty, the entrypoint runs exactly the code it runs today and the JVM gets no new
  option. `JAVA_OPTS`, `INGOT_OPTS`, `PUID`, `PGID`, the paths, the user and the healthcheck do not change.
- **Container tests.** Tests in `reposilite-backend/src/containerTest/` prove the import, both start paths, the unchanged
  default and the failures. They run in the existing "Container images" CI job.
- **Documentation.** The variable goes into the container variables of `docs/gitbook/reference/environment-variables.md`, a
  how-to section in `docs/gitbook/how-to/install-with-docker.md` shows a Compose file with a mounted directory, and
  `run-on-kubernetes.md` links to it as the image side of the chart setting.

### Out of scope

- Any change to the Helm chart. Its values, mount path and `/tmp` emptyDir stay as merged.
- A setting inside the server (a configuration key) for extra CAs, per-connection trust (a different CA for the mirror and for
  the database) and hot reload of the bundle. The bundle is read once at start.
- Client certificates (mutual TLS) and private keys. Only trust anchors are imported.
- Replacing the public trust store. The copy always starts as the full JDK store, and there is no switch for "only my CA".
- Importing certificates into the dashboard image. nginx does not call out over TLS.
- A rebuild of the image with certificates baked in. Mounting is the supported path.

## Capabilities

### New Capabilities

- `container-extra-ca-bundle`: how the server image turns a directory of PEM files into additional trust anchors at start,
  which paths and variables it reads, and which failures stop the start.

## Impact

- **Type and scope.** The change ships under `feat(docker)`. The tests and the documentation are separate commits of the type
  `test(docker)` and `docs(docker)`. The pull request title, squash-merged onto main, is:

  `feat(docker): import extra ca certificates into the trust store at start`

- **Breaking marker.** No breaking change. The variable is new and opt-in, and an unset variable leaves the start unchanged.
  An existing Reposilite deployment is adopted with no change. The title carries no `!`.
- **Drop-in checks.** The three questions of `CLAUDE.md` are answered by the existing container tests, which stay unedited:
  an upstream Compose file starts with only the image line swapped, an existing `/app/data` is adopted, and `PUID`, `PGID` and
  `REPOSILITE_OPTS` still take effect.
- **Code.** `entrypoint.sh`, and `reposilite-backend/src/containerTest/` (one new class). The `Dockerfile` does not change,
  because `keytool` ships in the JRE of the base image (to confirm in task 2.1). `ci.yml` does not change, because the
  "Container images" job already runs `:reposilite-backend:containerTest` and the upgrade rehearsal.
- **Dependencies.**
  - `add-chart-network-and-trust` is already merged. This change reads the path and variable it fixed.
  - `fix-entrypoint-signal-handling` edits the root branch of `entrypoint.sh` too. The two touch different lines. Whichever
    lands second rebases, and the container tests of both run again.
  - The Nexus import change depends on this one for self-signed Nexus servers, and nothing here depends on it.
- **Documentation.** `docs/gitbook/reference/environment-variables.md`, `docs/gitbook/how-to/install-with-docker.md` and
  `docs/gitbook/how-to/run-on-kubernetes.md`.

## Verification

- The new container tests fail before the entrypoint change and pass after it.
- The existing container tests pass unedited, including the PUID and PGID cases and the upgrade rehearsal.
- `shellcheck entrypoint.sh` reports nothing new.
- `openspec validate add-image-extra-ca-bundle --strict` passes.
- `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-image-extra-ca-bundle` prints nothing.
