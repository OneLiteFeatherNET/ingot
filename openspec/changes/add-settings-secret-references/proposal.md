# Proposal

## Why

The shared settings of Ingot hold the secrets an operator needs to run the instance: the S3 access and secret keys, the LDAP
bind password, the OIDC client secret, the mirror credentials and their proxy URLs. Today each of them is stored as plain text.
The database row holds it, and a GitOps shared configuration file holds it in a ConfigMap. The settings API hides these values
only from the dashboard and only when `hideSettingsSecrets` is on. An operator who keeps the configuration in Git, or who has
a secret store that already writes files, has to paste the secret into the file or the row by hand.

The external secret stores of Kubernetes and Compose (External Secrets Operator, Secrets Store CSI, SOPS, Docker secrets) all
deliver a secret as a file or as a Secret that is mounted as one. The only thing missing is a way to say in the settings
"this value is the content of that file" without writing the value itself.

## What Changes

- **Opt-in switch.** `INGOT_SETTINGS_SECRET_REFERENCES=true` (with `REPOSILITE_SETTINGS_SECRET_REFERENCES` as fallback) turns
  reference resolution on. It defaults to `false`. Read from the environment only, so a stored settings document cannot switch
  it on.
- **References.** With the switch on, a `@Sensitive` text setting may contain `${env:NAME}` and `${file:/absolute/path}`. Each
  reference is replaced by the variable's value or by the file's content when the setting is loaded. Several references can
  appear in one value, for example in an endpoint that embeds credentials. `$${` is written for a literal `${`. Any other
  `${` text stays as it is.
- **Allowlists.** Two environment settings name what a reference may read: `INGOT_SETTINGS_SECRET_ENV` (a comma separated list
  of variable names) and `INGOT_SETTINGS_SECRET_FILES` (a comma separated list of absolute directories). A reference outside
  these lists is refused. Both default to empty, so a switched-on instance resolves nothing until the operator lists it.
- **Only in memory.** The stored document keeps the literal reference. The resolved value is held in a separate in-memory view
  that the consumers read (S3, LDAP, OIDC, mirror credentials and proxy). It is never written back, never returned by the
  settings API, never part of the rendered document, the audit diff or a log line.
- **Shown as written.** The settings API returns a reference as the operator wrote it, even when `hideSettingsSecrets` is on,
  because a reference is not a secret. A literal secret is still replaced with `********`.
- **When it is read.** References are resolved when a settings document is loaded: at start, on a reload of the shared
  configuration and when a newer document is read from the database. They are not read again for every request.
- **Failures.** A reference that cannot be resolved stops the start for that instance, as a broken shared configuration does
  today. On a reload the previous resolved settings stay in place and an error is logged. A write whose reference is refused
  or cannot be resolved is answered with `400` and names the field and the reference, never the value.
- **Documentation.** `docs/gitbook/reference/environment-variables.md` for the three settings, `docs/gitbook/reference/configuration-and-startup.md`
  for the shared settings, and `docs/gitbook/how-to/use-s3-storage.md`, where the statement that keys are stored in plain text
  is corrected for references.

### Out of scope

- File values for local settings (`INGOT_X_FILE`) and the `_FILE` spelling of other variables. Those belong to `add-file-secrets`.
- The reload of the shared configuration file. That is `add-shared-configuration-reload`, which this change builds on.
- The Helm chart, including a `secretFiles` value and the External Secrets and CSI examples. The chart belongs to `add-file-secrets`
  and the chart changes of the cloud native waves.
- Expansion of references in fields that are not `@Sensitive`, in the local configuration file and in the dashboard.
- Re-reading a file for each request, watching a file for changes, and a secret store SDK in Ingot.
- Encryption of the stored document. The stored value stays the reference, so nothing secret is stored at all.

## Capabilities

### New Capabilities

- `settings-secret-references`: the switch, the reference syntax, the allowlists, the in-memory resolved view, what the API and
  the logs show, when references are read, and the failure rules.

## Impact

- **Type and scope.** The change ships under `feat(configuration)`. The pull request title, squash-merged onto main, is:

  `feat(configuration): resolve secret references in shared settings`

- **Dependencies.** `add-file-secrets` (on the `docs/openspec-wave1` branch, not yet on main) provides the file reading rules
  that this change reuses: one trailing line break removed, UTF-8 only, and no content in an error. `add-shared-configuration-reload`
  (N1) provides the reload path that rotated secrets use. The code pull request is branched from main after both have merged.
- **Breaking marker.** No breaking change. The switch defaults to off, and with it off every value is stored, shown and used
  exactly as before. A Reposilite installation pointed at Ingot needs no change. No configuration key, volume, manifest or
  plugin changes. The title carries no `!` and no commit carries a `BREAKING CHANGE:` footer.
- **Behaviour that changes by default.** None. A `${env:...}` text in a secret field is used as plain text until the switch is on.
- **Operator-visible with the switch on.** A stored reference is resolved on load. An instance resolves against its own
  environment and mounted files, so a reference that resolves on one replica and not on another stops only that replica at
  start. The documentation says that all replicas that share a document must be able to resolve its references.
- **Code.** `reposilite-backend`: `configuration/shared` (the resolver, the facade view, masking and restore), the consumers in
  `storage/s3`, `maven` (mirror credentials and proxy), and `net/onelitefeather/ingot/identity/login` (LDAP and OIDC), plus
  `docs/gitbook/`.

## Verification

- Targeted tests per commit, run through `HEAVY_SLOTS=1 heavy` with `--no-daemon --max-workers=2`. Unit tests with
  `:reposilite-backend:test --tests '<pattern>'`, one integration test with `:reposilite-backend:integration --tests '<pattern>'`.
- `openspec validate add-settings-secret-references --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-settings-secret-references` prints nothing.
- The Container images CI job passes. With the switch unset, an unmodified upstream compose file starts with only the image line changed.
