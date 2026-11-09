# Proposal

## Why

Every local setting of Ingot can come from an environment variable, and a secret among them, such as the TLS key password,
has to be written into that variable's value. Container platforms keep secrets in files mounted into the container, and the
official Postgres and MariaDB images already let an operator point a setting at such a file (`POSTGRES_PASSWORD_FILE`). An
operator of Ingot has no such option: the secret either sits in the manifest in plain text, or a wrapper has to read it and
export it before Ingot starts.

## What Changes

- **File values.** For every local setting, `INGOT_LOCAL_<KEY>_FILE` names a file whose content is the value. The
  `REPOSILITE_LOCAL_<KEY>_FILE` spelling is read as well, and `INGOT_` wins when both are set, as for every other variable.
  The file is read once at start. Trailing line breaks are removed and nothing else is changed.
- **The plain variable wins.** When a key has both a plain variable and a `_FILE` variable in the same source (environment or
  system properties), the plain variable is used, the file is not read, and one warning names both variables.
- **An unreadable file stops the start.** A file that does not exist, cannot be read or is not UTF-8 stops the start before
  any port is bound. The message names the variable as the operator set it and the path, and never the content. The process
  ends with status 1 and one line, as it already does for an unknown deploy target.
- **System property form.** `ingot.local.<key>.file` and `reposilite.local.<key>.file` follow the same rules.
- **Documentation.** `docs/gitbook/reference/environment-variables.md`, `docs/gitbook/reference/configuration-and-startup.md`
  and the variable table in `docs/gitbook/how-to/install-with-docker.md`.

### Out of scope

- Expansion of `${VAR}` inside values (`INGOT_CONFIG_EXPAND_ENV`). That is a later, separate change.
- The Helm chart mounting secrets and pointing `_FILE` variables at them. The chart belongs to the deployment work.
- Secrets outside the local configuration, such as `INGOT_CLUSTER_PASSWORD`. They keep their current names.
- Re-reading files while Ingot runs. A changed secret takes effect at the next start, as a changed variable does today.
- Any change to `entrypoint.sh` or the image. The file is read by the server process itself.

## Capabilities

### New Capabilities

- `file-secrets`: the `_FILE` spelling of local settings, its precedence against plain values, the reading rules, and the
  start failure for an unreadable file.

## Impact

- **Type and scope.** The change ships under `feat(configuration)`. The pull request title, squash-merged onto main, is:

  `feat(configuration): read local settings from files with _FILE variables`

- **Order.** This change follows `report-configuration-errors` (Q6). Both change `LocalConfigurationFactory`, and Q6 decides how
  warnings from the factory reach the log. The code pull request is branched from the main branch after Q6 has merged.
- **Breaking marker.** No breaking change. The `_FILE` names are new, and no existing variable, property, default or
  manifest changes. A Reposilite installation pointed at Ingot needs no change. The title carries no `!` and no commit carries
  a `BREAKING CHANGE:` footer.
- **Behaviour that changes by default.** A variable named `INGOT_LOCAL_<KEY>_FILE` used to be reported as an unknown setting.
  It is now read. Nothing else changes for an operator who does not set one.
- **Code.** `reposilite-backend`: `configuration/local` (the factory, one new exception), `ReposiliteFactory` (passes the root
  journalist to the factory, see `design.md`) and `ReposiliteLauncher` (one line on the start failure). Plus `docs/gitbook/`.

## Verification

- Targeted tests per commit, run through `heavy` with `--max-workers=2`. The container test runs through `heavy` as well.
- `openspec validate add-file-secrets --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-file-secrets` prints nothing.
- The Container images CI job passes. The factory runs on the start path of the image, so an unmodified upstream compose file
  must still start with only the image line changed.
