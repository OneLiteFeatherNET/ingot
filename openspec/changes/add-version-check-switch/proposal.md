# Proposal

## Why

An operator who runs Ingot on a network without internet access, or who has to tell an auditor which hosts the server talks
to, cannot answer either question from the documentation today. Ingot asks GitHub once an hour for the newest release. The
only way to stop that is a JVM system property, `-Dingot.status.remote-version-check=false`, which has to be added to
`JAVA_OPTS` or `INGOT_OPTS`. A Kubernetes or compose deployment that is configured through environment variables, which is
how every other Ingot switch works, has no way to do it without rewriting its start command. The `INGOT_` prefix exists
precisely so that this does not happen, and this one setting was left out.

The second half of the question is the list. The version check is one call among several: mirrors, OIDC and GitHub login,
LDAP, S3, a database and the cluster bus also leave the process, each only when an operator configures it. That is a strong
statement ("nothing leaves the process unless you configured it, except one call that you can turn off"), but it is nowhere
written down, and the roadmap wants it public (items N2 and part of N1).

## What Changes

- **Switch.** New setting `INGOT_STATUS_REMOTE_VERSION_CHECK`, with `REPOSILITE_STATUS_REMOTE_VERSION_CHECK` as fallback.
  `false` turns the hourly check off. Unset, empty or `true` leaves it on, exactly as today. The existing system properties
  `ingot.status.remote-version-check` and `reposilite.status.remote-version-check` keep working and keep their meaning.
  Precedence: `ingot.` property, `INGOT_` variable, `reposilite.` property, `REPOSILITE_` variable.
- **Outbound statement.** A new reference page `docs/gitbook/reference/outbound-connections.md` lists every connection Ingot
  opens, what triggers it, what it sends, whether it is on by default, and how to turn it off. It states that a fresh install
  makes exactly one outbound call, the version check, and that no telemetry, analytics or licence call exists. The page is
  linked from `docs/gitbook/SUMMARY.md`, from the environment variable reference and from the status how-to.
- **Configuration docs.** The new variable is documented in the environment variable reference and the status how-to, in the
  same pull request as the code.

### Out of scope

- Changing the default of the version check. It stays on, as it is part of the drop-in contract.
- An environment variable for `ingot.status.remote-version-url`. The URL is rarely changed and the property stays the only way.
- Any outbound call that is not there today: no new telemetry, no usage report, no licence or entitlement check.
- `SUPPORT.md`, the support window and the `Supported Versions` section of `.github/SECURITY.md`: they moved to
  `document-support-policy`.
- Other trust files: contacts in `.github/SECURITY.md` and `.github/CODE_OF_CONDUCT.md`, `MAINTAINERS.md`, `GOVERNANCE.md`,
  `ADOPTERS.md` and `SECURITY-INSIGHTS.yml` belong to `add-repository-trust-files`. `CODEOWNERS` is not part of either.
- `security.txt`. RFC 9116 requires a `Contact` field and an `Expires` field, and the contact address is not known yet. The
  file is also a web resource of a running deployment, not of this repository. It waits for the address in
  `add-repository-trust-files`.
- An outbound URL allow list or SSRF guard for admin supplied URLs. That is a separate change with its own opt-in.
- Legal and business model questions.

## Capabilities

### New Capabilities

- `remote-version-check`: the environment switch for the hourly check and its precedence against the system properties.
- `outbound-connections`: the public statement of every connection Ingot opens.

## Impact

- **Type and scope.** The change ships under `feat(status)`. The switch and its tests are `feat(status)`; the pages are
  `docs(status)` commits inside the same pull request, the way `add-read-only-mode` carries its
  documentation. The pull request title, squash-merged onto main, is:

  `feat(status): add environment switch for the remote version check`

- **Breaking marker.** No breaking change. Nothing an operator has to change: the default stays on, both system properties
  behave as before, and a Reposilite installation pointed at Ingot behaves exactly as it does today. The title carries no
  `!` and no commit carries a `BREAKING CHANGE:` footer.
- **Behaviour that changes by default.** None.
- **Opt-in.** `INGOT_STATUS_REMOTE_VERSION_CHECK=false` is the opt-out. The new variable does nothing until it is set.
- **Dependencies on other changes.**
  - `document-support-policy`: the support window was split out of this change and no longer touches any file here.
  - `add-settings-secret-references`, `add-file-secrets` and similar: unrelated. The new variable is a plain switch and
    carries no secret.
- **Code.** `reposilite-backend` (status plugin and facade, one new settings class), `docs/gitbook/`.

## Verification

- Targeted tests per commit, run through `heavy` with `--max-workers=2`.
- `openspec validate add-version-check-switch --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-version-check-switch` prints nothing.
- A start with `INGOT_STATUS_REMOTE_VERSION_CHECK=false` and a request to `GET /api/status/instance` opens no connection to the
  version endpoint (asserted by a test with a counting local server).
