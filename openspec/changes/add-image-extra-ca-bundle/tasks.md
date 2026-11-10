# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `docker`. The tests are written and
seen to fail before the entrypoint changes. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock, no fixed
ports, no shared mutable state, a temporary directory per test (the CA files are generated into a `@TempDir`), and assertions
with messages. Waits on container start are bounded by the Testcontainers wait strategy. Gradle runs go through `heavy` with
`--no-daemon --max-workers=2`, targeted and never the full suite, for example
`HEAVY_SLOTS=1 heavy ./gradlew :reposilite-backend:containerTest --tests '<pattern>' --no-daemon --max-workers=2`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-image-extra-ca-bundle`: this change and nothing else. Verify:
  `openspec validate add-image-extra-ca-bundle --strict` passes and the dash grep from the proposal prints nothing.

## 2. Tests first

- [ ] 2.1 Confirm in the built image that `keytool` exists and where the JDK store is:
  `docker run --rm --entrypoint sh ingot:ci -c 'command -v keytool; ls -l "$JAVA_HOME/lib/security/cacerts"'`. If `keytool` is
  missing, add it in the `Dockerfile` inside group 3 and say so in the pull request.
- [ ] 2.2 `test(docker)`: add `ExtraCaBundleContainerTest` beside `FileValuesContainerTest`. Generate a private CA and a server
  certificate into a `@TempDir`, run an HTTPS peer container that serves a Maven file with that certificate, mount the CA
  directory into the server image, and assert that a proxy repository over the peer answers. Cases: single CA as root, root
  with `PUID=1234`, `user: "977:977"` with a writable `/tmp`, read-only root filesystem with a `/tmp` tmpfs, a two-certificate
  file, a Kubernetes-style directory with `..data` and symlinks, the `REPOSILITE_` fallback, the public CAs kept (the copy lists every alias of the JDK store, no network call), and
  the unset variable leaving the JVM command line without a trust store option.
- [ ] 2.3 `test(docker)`: add the failure cases to the same class: missing directory, empty directory, a file with no
  certificate, and `JAVA_OPTS` with a trust store. Each asserts exit code 1 and the variable name in the output.
- [ ] 2.4 Verify: the cases of 2.2 and 2.3 fail on the current entrypoint, and the existing container tests are unchanged.
  Checkpoint: if the peer container is too heavy for the Container images job, switch to checking the copy with
  `keytool -list` after start and record that in the pull request.

## 3. Entrypoint

- [ ] 3.1 `feat(docker)`: add the import function to `entrypoint.sh` as described in `design.md`, called once before the
  root test at the top, appending the two trust store options to `JAVA_OPTS`. Handle PEM splitting, dot entries, aliases, the
  exit code 1 failures and the one-line report. Verify: all cases of group 2 pass, `shellcheck entrypoint.sh` reports nothing
  new, the existing container tests pass unedited (PUID and PGID, uid 1000, data adoption, upstream upgrade rehearsal, legacy
  `REPOSILITE_OPTS`), and the three drop-in questions of `CLAUDE.md` still hold.

## 4. Documentation

- [ ] 4.1 `docs(docker)`: add `INGOT_EXTRA_CA_CERTS` and its fallback to the container variables of
  `docs/gitbook/reference/environment-variables.md`. Add a section to `docs/gitbook/how-to/install-with-docker.md` with a
  Compose file that mounts a directory read-only, the failure messages and the note that the copy lives in `/tmp`. Link it
  from the extra CA section of `docs/gitbook/how-to/run-on-kubernetes.md` as the image side of `extraCaCerts`. Verify: the
  documented example is the one the test uses, and the dash grep prints nothing.

## 5. Pull request

- [ ] 5.1 Open the pull request for groups 2 to 4 with the title
  `feat(docker): import extra ca certificates into the trust store at start`. The description follows the repository template
  and tells the story from the operator's side: a private CA in a mounted folder is trusted at start, the public ones still
  are, and nothing changes when the variable is unset.
