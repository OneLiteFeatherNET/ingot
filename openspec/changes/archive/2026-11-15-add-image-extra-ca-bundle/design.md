# Design

## What exists on main

- The chart already mounts the bundle and names the path. `charts/ingot/templates/_helpers.tpl:564-568` defines the path
  `/etc/ingot/extra-ca`, `charts/ingot/templates/deployment.yaml:129-133` mounts it read-only,
  `deployment.yaml:182-185` sets `INGOT_EXTRA_CA_CERTS`, and `deployment.yaml:121-124` and `:217-220` give the container an
  `emptyDir` at `/tmp` when the bundle is on. `charts/ingot/values.yaml:62-63` states that the image must read the variable.
  `charts/ingot/templates/_helpers.tpl:574-588` rejects a second source and a user-set `INGOT_EXTRA_CA_CERTS`.
- The chart imports nothing into a trust store. Its design records that "the image owns the merge" and that "the mount path is
  part of this change's spec, so the image change must read the same path"
  (`openspec/changes/add-chart-network-and-trust/design.md`, section "The CA bundle is a mount contract between chart and
  image"). This change aligns with that contract: same variable, same directory-of-PEM-files meaning, writable `/tmp`.
- The image side is missing. `entrypoint.sh` (78 lines) does not mention the variable, and `Dockerfile` installs no CA tooling.
  Until this change lands, the chart value has no effect, and the docs must not promise it does.
- The server has no trust code of its own. A search for `SSLContext`, `TrustManager` and `trustStore` in
  `reposilite-backend/src/main` and `reposilite-plugins` finds nothing, so every outbound TLS call (mirror proxying, OIDC
  discovery, JDBC, the NATS `tls` scheme) uses the JVM default trust store. `docs/gitbook/how-to/configure-cluster-bus.md:81`
  confirms this for the bus. One JVM-wide setting therefore reaches all of them, and no application code changes.

## Decisions

### Merge into a copy at start, not into the JDK store

The JDK store sits in the image layer, and a read-only root filesystem or a non-root user cannot write to it. The entrypoint
copies `$JAVA_HOME/lib/security/cacerts` into a directory made by `mktemp -d` under `${TMPDIR:-/tmp}`, imports into the copy,
and points the JVM at the copy with `-Djavax.net.ssl.trustStore`. This is the same shape the chart's design describes, and it
needs no write access outside `/tmp`. The copy starts as the full public set, so a bundle with one private CA only adds to it.

Alternatives rejected: `update-ca-certificates` writes to `/etc/ssl` and `/usr/lib`, needs root, and the JRE reads the Java
store, not the system bundle, unless the base image links them. Baking the certificates into a derived image works but breaks
the "one changed line" promise for operators who need it. A JVM-wide `javax.net.ssl.trustStore` pointing at the mounted
directory itself would replace the public CAs, which is the workaround this change exists to remove.

### The variable names a directory, with every certificate in every file

`INGOT_EXTRA_CA_CERTS` is a directory, because that is what the chart mounts and what a ConfigMap or Secret yields. Files whose
name starts with a dot are skipped, since a Kubernetes volume holds `..data` and timestamped directories beside the symlinked
keys. Symlinks to files are followed. A PEM file may hold several certificates (a chain), and `keytool -importcert` imports only
one per call, so the entrypoint splits each file at the `-----BEGIN CERTIFICATE-----` markers into single-certificate files
in the private directory and imports each under the alias `ingot-extra-<file name>-<index>`. A file with no certificate block
counts as an error. Splitting is done with `awk`, which the base image has and the entrypoint already uses.

The fallback `REPOSILITE_EXTRA_CA_CERTS` follows the repository naming rule. The `INGOT_` name wins when both are set, as for the
other container variables. The chart's own design already names the fallback.

### Runs before the privilege split, for both paths

The step is a shell function called once before the `if [ "$(id -u)" != 0 ]` branch at `entrypoint.sh:17`. It appends the two
JVM options to `JAVA_OPTS`, which both branches already expand unquoted (`entrypoint.sh:21` and `:75`). Nothing in the two `exec`
lines changes, so `fix-entrypoint-signal-handling` can rewrite the root `exec` independently. The private directory is mode 0755
and the copy mode 0644, so the service account (uid from `PUID`) reads it after the drop, and no `chown` is needed whatever
`PUID` is. When the process is not root, the same code writes to the same place, which must be writable by that uid, and the
default `/tmp` is.

### Password and logging

The JDK store password `changeit` is public and the copy is not secret, so the options carry it in plain text. The entrypoint
prints one line per start to stderr: the number of certificates imported and the file names, never the certificate contents.

### Fail closed

A variable that is set but cannot be honoured stops the start with exit code 1 and one line that names `INGOT_EXTRA_CA_CERTS`
(or the fallback name that was used). The cases: not a directory, not readable, no certificate found, a file that `keytool`
rejects, `keytool` missing, and `JAVA_OPTS` already containing `-Djavax.net.ssl.trustStore`. Starting anyway would turn a typo
into a runtime TLS error far from the cause. The file-secret change set the pattern: a missing secret file stops the start with
exit code 1 and names the variable (`FileValuesContainerTest`).

### Unset means the old code path

The function returns at once when both variables are unset or empty, before it touches `/tmp` or `keytool`. The Verification
section of the spec and a container test compare the JVM command line with and without the variable.

### Dashboard image and chart

The dashboard image is nginx and makes no TLS call, so it gets nothing. The chart stays as merged.

## Container tests

One class beside `FileValuesContainerTest`, using the existing `ImageUnderTest.server` and Testcontainers. The CA material is
generated in the test with `keytool` or the JDK API into a `@TempDir`, so nothing is downloaded and the test is repeatable.
The import is proven by its user-visible effect: a second container serves HTTPS with a certificate from the private CA, and a
proxy repository in the server over that peer must answer. Cases:

1. A directory with one private CA and a mirror signed by it: the proxy fetch succeeds (red without the change).
2. The same as root with `PUID=1234`, and as `user: "977:977"` with a writable `/tmp`: both succeed.
3. A multi-certificate PEM file: the second certificate is trusted too.
4. The public CAs remain: no network call. The test lists the copy with `keytool -list` through `exec` and asserts that it holds
   every alias of the JDK store plus the imported ones.
5. Unset variable: the container command line has no `javax.net.ssl.trustStore` and the existing tests pass unedited.
6. Each failure of the "Fail closed" list: exit code 1 and the variable name in the output.

If a TLS peer container proves heavy for the Container images job, the fallback is to read the trust store file through
`docker exec ... keytool -list` after start. The task list names that choice as a checkpoint, not a silent downgrade.

## Risks

- `keytool` availability in the pinned JRE image must be confirmed in task 2.1. If it is missing, the fix is `Dockerfile`
  (`feat(docker)` too) and not a new package: the JRE image normally carries `keytool` in `$JAVA_HOME/bin`.
- Two changes edit `entrypoint.sh`. The new code sits in a function and one line before the branch, and the other change edits
  only the final `exec` of the root branch.
- Start time grows by a `keytool` run per certificate, tens of milliseconds each, only when the variable is set.
