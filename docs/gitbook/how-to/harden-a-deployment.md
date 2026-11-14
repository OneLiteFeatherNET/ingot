---
description: A checklist to harden an Ingot deployment: unprivileged start, read-only root filesystem, dropped capabilities, network policy, TLS, trusted proxies, settings secrets, tokens, audit forwarding and release verification, with Docker Compose and Helm snippets.
---

# Harden a deployment

Ingot starts with the same defaults as Reposilite, so an existing deployment keeps working after the image change. Hardening is therefore a set of switches that you turn on yourself. This page lists them in the order we recommend, says what each one protects, and gives a snippet for Docker Compose and for the Helm chart.

Work through the steps in order. Each one can be applied and checked on its own, and the snippets combine into one file at the end of the Compose section.

## Why the image does not start unprivileged

The image sets no `USER`. It starts as root, and the entrypoint switches to the service account `reposilite` (uid and gid `977`) before it starts Java. Only that path can take over a data directory owned by someone else and honour `PUID` and `PGID`.

The price is that the container needs three things at start: the ability to change the owner of the data volume, the ability to switch groups, and the ability to switch users. Steps 1 and 3 remove that need. Once the volume is owned by `977`, you pin the user and nothing else in the start path needs privileges.

Before you pin the user, check the owner of the data directory:

```sh
docker exec <container> ls -ldn /app/data
```

The third column must be `977`. If it is not, start the container once as root and let the entrypoint change the owner. Do not pin the user first: the server would then run as `977` into a directory it cannot write.

## Checklist

| Step | What it protects against | Compose | Helm chart |
|---|---|---|---|
| 1. Run unprivileged | A compromised process with root inside the container | `user: "977:977"` | `deployment.securityProfile: restricted` |
| 2. No privilege escalation | A setuid binary raising its privileges | `no-new-privileges:true` | set by the `restricted` profile |
| 3. Drop all capabilities | Kernel features that a root process could use | `cap_drop: [ALL]` | set by the `restricted` profile |
| 4. Read-only root filesystem | Changes to the image, and writes outside the volumes | `read_only: true` and two writable paths | set by the `restricted` profile |
| 5. Seccomp | System calls that the server never needs | Docker default profile, do not disable it | `RuntimeDefault`, set by the `restricted` profile |
| 6. Network policy | Traffic from pods or hosts that do not need the server | Do not publish the port | `networkPolicy.enabled: true` |
| 7. TLS | Credentials and tokens on the wire | Proxy or `enable-tls` | Ingress `tls` |
| 8. Security headers | Browser-side attacks on the dashboard | Nothing to set | Nothing to set |
| 9. Trusted proxies | A client faking its address in login lockout and the audit log | `INGOT_TRUSTED_PROXIES` | `INGOT_TRUSTED_PROXIES` via `env` |
| 10. Settings secrets | Reading secrets from the database file | `INGOT_SETTINGS_KEY_FILE` | `INGOT_SETTINGS_KEY_FILE` via `env` and a Secret |
| 11. Token hygiene | Long-lived credentials in CI and scripts | Machine accounts and rotation | Same, outside the chart |
| 12. Admin recovery | Locking yourself out, and a temporary token left behind | Documented procedure | Same |
| 13. Audit forwarding | Losing the record of who did what | `INGOT_AUDIT_SINK_STDOUT` | Same, through the log collector |
| 14. Verify what you run | A tampered image or chart | `cosign verify` | `cosign verify` |
| 15. Keep up with releases | Known vulnerabilities in old builds | Watch releases and upgrade | Same |

## 1. Run unprivileged

Pin the user, as shown in the Compose section. In the chart, the `restricted` profile runs the pod and the container as `977`, sets `fsGroup` to `977`, and requires a non-root user:

```yaml
deployment:
  securityProfile: restricted
```

Use `restricted-openshift` on clusters that assign the uid. It sets the same fields without the fixed ids. Do not combine a profile with `deployment.podSecurityContext.enabled` or `deployment.containerSecurityContext.enabled`, because the chart refuses that combination.

Two effects of the pinned user are easy to miss:

* The entrypoint does not run its account and ownership code when it is not root. `PUID` and `PGID` have no effect then, so set them only when you start as root.
* The entrypoint runs the server directly with `exec java` and does not drop privileges again, which is what makes the other steps possible.

## 2. No privilege escalation

`no-new-privileges` stops a process from gaining privileges through a setuid binary or a file capability. The repository's `docker-compose.yml` sets it. In a pod, `allowPrivilegeEscalation: false` does the same, and the `restricted` profile sets it.

## 3. Drop all capabilities

Drop every capability and add none back. The server does not need any of them once it runs as `977`.

This step depends on step 1. A root start needs `CAP_SETUID` and `CAP_SETGID` to switch to the service account. We tested a root start with all capabilities dropped, and the entrypoint stops with `runuser: cannot set groups: Operation not permitted`. Drop the capabilities only with the user pinned.

## 4. Read-only root filesystem

With `read_only: true` the image's own files cannot be changed. The server needs three writable paths:

| Path | Why it is written | How to provide it |
|---|---|---|
| `/app/data` | The database, the configuration, plugins, artifacts and the working directory of the server | A volume that the user `977` owns |
| `/var/log/reposilite` | The log files written by the tinylog file writer | A volume. Without one the server still starts, but logs an error about the file writer at every start |
| `/tmp` | Java temporary files, and the SQLite driver, which extracts its native library here and maps it from there | A `tmpfs` with exec allowed |

The `/tmp` mount needs the `exec` option. Docker and Podman mount `tmpfs` without exec by default. With the default, the SQLite driver fails to map its native library, and the server stops during the database start with `failed to map segment from shared object`. The failure and the fix were reproduced with the 1.1.1 release image. The extra CA step of the entrypoint also writes to `/tmp` when `INGOT_EXTRA_CA_CERTS` is set.

The Helm chart's `restricted` profile mounts `emptyDir` volumes at `/tmp` and `/var/log/reposilite`. Check on your nodes that the `/tmp` volume allows exec, because the SQLite driver needs it. We did not run the chart on a cluster while writing this page.

## 5. Seccomp

Docker applies its default seccomp profile to every container unless you disable it. Leave it that way, and never set `seccomp:unconfined`.

In a pod, set the profile explicitly. The `restricted` profile sets it on the pod and on the container:

```yaml
securityContext:
  seccompProfile:
    type: RuntimeDefault
```

## 6. Network policy

Ingot serves the artifacts, the API and the metrics on port `8080`. Limit who can reach it.

With Compose, do not publish the port to the host. Put the server on a network that only your reverse proxy or the dashboard can join, as the split file `docker-compose.split.yml` does.

With Kubernetes, turn on the chart's NetworkPolicy and name the peers that may reach the port:

```yaml
networkPolicy:
  enabled: true
  ingress:
    fromNamespaces:
      - kubernetes.io/metadata.name: gateway-system
    fromPods:
      - app.kubernetes.io/name: prometheus
```

The policy needs a network plugin that enforces it, such as Calico or Cilium. Another plugin accepts the policy and enforces nothing. The chart's `networkPolicy.egress` section can also restrict outgoing traffic. See [Run on Kubernetes](../set-up/deploy/run-on-kubernetes.md) for the egress rules and the database and S3 endpoints that must stay reachable.

## 7. TLS

Terminate TLS at the proxy or ingress in front of Ingot, as described in [Terminate TLS at the gateway](../set-up/network/terminate-tls-at-the-gateway.md) and [Run behind a reverse proxy](../set-up/network/run-behind-reverse-proxy.md). If you want Ingot to serve HTTPS itself, follow [Enable TLS](../set-up/network/enable-tls.md).

Once users reach Ingot over HTTPS only, set `INGOT_HSTS_MAX_AGE` to a number of seconds. Ingot then sends `Strict-Transport-Security` on HTTPS requests. The variable is listed in the [environment variables](../reference/environment-variables.md) reference.

## 8. Security headers

Ingot sends its security headers on every response, and the dashboard document has its own rules. They need no setup, and your proxy should not remove them. The meaning of each header is in [Security headers](../explanation/security-headers.md).

## 9. Trusted proxies

Behind a reverse proxy, Ingot reads the client address from the forwarded header. Login lockout, brute-force protection and the audit log use that address. If you do not list your proxies, any client can send the header and choose the address it appears as. Ingot then logs a startup warning.

List the addresses or CIDR ranges of your proxies in `INGOT_TRUSTED_PROXIES`. Ingot reads the header only from a connection that comes from one of them:

```yaml
environment:
  - INGOT_TRUSTED_PROXIES=10.0.0.0/8
```

A malformed entry stops the start and names the entry, so a typo does not silently trust more than you meant. The rules are in [Trust your proxies](../set-up/network/run-behind-reverse-proxy.md#trust-your-proxies) and in the [environment variables](../reference/environment-variables.md) reference.

## 10. Settings secrets

The shared settings store secret values, such as provider client secrets, in the database. Setting `INGOT_SETTINGS_KEY_FILE` seals them with a key that the database does not hold. Generate the key and keep it outside the database:

```sh
openssl rand -base64 32
```

Pass the key as a file, so that it does not appear in `docker inspect` output or in the environment of other processes:

```yaml
services:
  ingot:
    environment:
      - INGOT_SETTINGS_KEY_FILE=/run/secrets/settings_key
    secrets:
      - settings_key

secrets:
  settings_key:
    file: ./settings_key.txt
```

The file must be readable by uid `977`. Every instance that shares the database needs the same key, and a different key stops the instance at start.

In Kubernetes, put the key in a Secret and mount it as a file, or deliver it with your GitOps tool. Keep the database password in a Secret too: the chart reads it through `database.existingSecret`, see [Run on Kubernetes](../set-up/deploy/run-on-kubernetes.md).

The steps for a first setup, a key rotation and a return to a build without encryption are in [Encrypt settings secrets](../configure/encrypt-settings-secrets.md). The Kubernetes delivery of the secrets is in [Manage secrets with GitOps](../set-up/deploy/manage-secrets-with-gitops.md), and the key variables are in [Settings encryption](../reference/settings-encryption.md).

## 11. Token hygiene

* Give each CI job or script its own machine account with the narrowest permissions it needs. Rotate its token when the job changes. See [Use tokens in CI](use-tokens-in-ci.md) for the steps and [Restrict a token to artifacts](restrict-token-to-artifacts.md) for limiting a token to a path.
* Do not keep a temporary admin token in the startup options. A value in `INGOT_OPTS` is part of the container configuration and shows in `docker inspect`. Start with `--token` once, create a permanent administrator, and remove the flag.
* Revoke a token as soon as it is no longer used, and rotate a secret that may have leaked. [Use tokens in CI](use-tokens-in-ci.md) describes both.

## 12. Admin recovery

Before you need it, make sure that at least two people can sign in as an administrator, or that you know how to start a temporary admin token. [Recover admin access](recover-admin-access.md) describes the setup code, the `--token` flag and the wait for a lockout to end.

A temporary token created with `--token` exists only while the server runs. Once the server restarts it is gone, unless you pass the flag again. Note when you used it, so that you can check the audit events from that period.

## 13. Audit forwarding

Ingot writes every audit event to its database. To keep a copy outside the instance, write the events as JSON lines to standard output, where your log collector picks them up:

```yaml
environment:
  - INGOT_AUDIT_SINK_STDOUT=true
```

Or append them to a file. A relative path is resolved against the working directory `/app/data`, which is writable in the read-only setup:

```yaml
environment:
  - INGOT_AUDIT_SINK_FILE=audit/ingot-audit.jsonl
```

Use [Forward audit events to a SIEM](forward-audit-events-to-a-siem.md) for the collector side, including rotation and filtering. The event names are listed in [Audit events](../reference/audit-events.md).

## 14. Verify what you run

Every release image and the Helm chart are signed by the release workflow of this repository. The standalone jar carries a build provenance attestation. The check does not need a key from us, only the identity of the workflow on `main`:

```sh
cosign verify \
  --certificate-identity https://github.com/OneLiteFeatherNET/ingot/.github/workflows/release-please.yml@refs/heads/main \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com \
  ghcr.io/onelitefeathernet/ingot:<version>

cosign verify \
  --certificate-identity https://github.com/OneLiteFeatherNET/ingot/.github/workflows/release-please.yml@refs/heads/main \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com \
  ghcr.io/onelitefeathernet/charts/ingot:<version>
```

Replace `<version>` with a release number. Nightly images carry no signature, so the check fails for `nightly` by design. Release images also carry an SBOM and a provenance attestation, which the release build pushes with the image.

For the jar and its SBOMs, follow [Verify the SBOMs of a release](verify-release-sboms.md). The full list of checks is in [Verify a release](../../../.github/RELEASE.md#verify-a-release).

## 15. Keep up with releases

* Watch the releases of the repository, or subscribe to its release feed, and read the [upgrade changes](../reference/upgrade-changes.md) before each upgrade.
* Back up before each upgrade and verify the result, as described in [Back up before upgrading](back-up-before-upgrading.md) and [Verify an upgrade](verify-an-upgrade.md).
* Security fixes are made for the latest minor release line and for `main`. The [security policy](../../../.github/SECURITY.md) lists the supported versions. Report a vulnerability through the security advisory form of the repository, not through a public issue.

## Compose file with the hardening applied

This file combines the steps above for a single server behind a reverse proxy. It is a starting point, and it does not publish the port.

{% hint style="info" %}
`INGOT_TRUSTED_PROXIES`, the settings commands and the `--snapshot-database` flag are not in the release 1.1.1. Use a release that includes them, and check its changelog before you rely on one of these settings.
{% endhint %}

```yaml
services:
  ingot:
    image: ghcr.io/onelitefeathernet/ingot:<version> # a release that reads INGOT_TRUSTED_PROXIES
    user: "977:977"                              # step 1
    read_only: true                              # step 4
    tmpfs:
      - /tmp:exec                                # step 4, exec is required for the SQLite driver
    cap_drop:
      - ALL                                      # step 3
    security_opt:
      - no-new-privileges:true                   # step 2
    environment:
      - INGOT_TRUSTED_PROXIES=10.0.0.10          # step 9, the address of your reverse proxy
      - INGOT_SETTINGS_KEY_FILE=/run/secrets/settings_key  # step 10
      - INGOT_AUDIT_SINK_STDOUT=true             # step 13
    secrets:
      - settings_key
    volumes:
      - ingot-data:/app/data
      - ingot-logs:/var/log/reposilite
    restart: unless-stopped
    # No ports entry: the reverse proxy reaches the server on a shared network.

volumes:
  ingot-data: {}
  ingot-logs: {}

secrets:
  settings_key:
    file: ./settings_key.txt
```

The `ingot-data` volume is created empty by Compose and takes the owner of the image directory, `977`, so the pinned user can write to it from the first start. Keep `./settings_key.txt` out of version control.

## Helm values with the hardening applied

```yaml
deployment:
  securityProfile: restricted                    # steps 1 to 5
serviceAccount:
  automountServiceAccountToken: false            # no API token in the pod
networkPolicy:
  enabled: true                                  # step 6
  ingress:
    fromNamespaces:
      - kubernetes.io/metadata.name: gateway-system
env:                                             # Helm replaces the whole list, so keep JAVA_OPTS
  - name: JAVA_OPTS
    value: "-Xmx64M"
  - name: INGOT_TRUSTED_PROXIES                  # step 9
    value: 10.0.0.0/8
```

The chart's `env` list replaces its default list as a whole. The default holds `JAVA_OPTS`, so copy that entry, or set the heap size another way.

## Related

* [Run on Kubernetes](../set-up/deploy/run-on-kubernetes.md) covers the chart values for the database, storage and ingress.
* The file `docker-compose.split.yml` in the repository runs the dashboard in its own container, read-only, with all capabilities dropped and no-new-privileges set. See [Run the dashboard in a separate container](../set-up/deploy/run-split-dashboard-container.md).
* [Configuration and startup](../reference/configuration-and-startup.md) lists the startup parameters.
