# Proposal

## Why

`.github/SECURITY.md` still says that Ingot "has not published its own release yet". Releases `v1.0.0`, `v1.1.0` and
`v1.1.1` exist, so the policy describes a state that ended months ago, and its support table lists only an unreleased
`main`. The policy also names no response time and no disclosure window. A reporter who wants to know when to expect an
answer, and when a flaw will become public, has to ask.

An operator deciding whether to upgrade asks the same questions an auditor asks: where to report, how long until an
answer, how long a fix may stay private, and who issues the CVE. The repository answers none of them in writing. The
OpenSSF Best Practices criterion `vulnerability_report_response` asks for an initial answer within 14 days, and the
policy does not commit to that yet.

No `security.txt` (RFC 9116) exists either, so a researcher who looks at the standard location finds nothing to read.

## What Changes

- **Supported Versions.** The stale first paragraph and the table of `.github/SECURITY.md` are replaced by the current fact:
  security fixes are made on the latest minor release line and on `main`. Older lines are named as not covered yet. No
  window is promised. The final table and the support window are decided by `document-support-policy`, which replaces this
  section when it merges.
- **Response targets.** The reporting section names an acknowledgement within 14 days and the disclosure section names a
  coordinated disclosure window of 90 days, extended only by agreement with the reporter.
- **CVE handling.** A CVE is requested through the GitHub Security Advisory opened for the report.
- **`security.txt`.** A new `.well-known/security.txt` in the repository follows RFC 9116. Its Contact is the advisory form,
  its Policy is the policy page, its Expires date is at most six months ahead and its preferred language is English. The
  project website serves it at `/.well-known/security.txt` once such a site exists.

Two things stay as they are on purpose. The mailbox sentence and its `TODO(onelitefeather)` marker in `.github/SECURITY.md`
belong to `add-repository-trust-files`, and the paragraph that asks reporters to report upstream too is correct and is kept.

### Out of scope

- A route in the Ingot server that answers `/.well-known/security.txt`. That is a code change, and it would publish the
  project's contact on every operator's installation. It is a follow-up that needs its own decision.
- `SECURITY-INSIGHTS.yml`, the security contact address and the code of conduct contact. They belong to
  `add-repository-trust-files` (task 3.2). This change only requires that the values agree with this policy.
- `SUPPORT.md` and the final support table. They belong to `document-support-policy`.
- Any change to the advisory form, to private vulnerability reporting or to repository settings.

## Capabilities

### New Capabilities

- `security-disclosure`: the response targets, the disclosure window, the CVE process, the current support statement and
  the `security.txt` file that points at them.

## Impact

- **Type and scope.** The change ships under `docs(security)`. The pull request title, squash-merged onto main, is:

  `docs(security): complete the disclosure policy and add security.txt`

- **Breaking marker.** None. Documentation only. No configuration key, environment variable, volume, manifest, plugin or
  image changes, and a Reposilite installation pointed at Ingot behaves exactly as before.
- **Behaviour that changes by default.** None.
- **Dependencies on other changes.**
  - `add-repository-trust-files` edits the contact sentence of the same `Reporting a Vulnerability` section. The hunks
    differ, so the second change to merge rebases onto the first.
  - `document-support-policy` replaces the `Supported Versions` section written here. Its proposal already names this file.
- **Code.** None. `.github/SECURITY.md` and `.well-known/security.txt`.
- **Docs.** No `docs/gitbook/` change, because no setting or environment variable changes.

## Verification

- `openspec validate complete-security-disclosure --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/complete-security-disclosure` prints nothing. The new files must not
  contain an em or en dash either.
- The `security.txt` parse check in `tasks.md` 3.2 passes: Contact and Expires are present once each, and Expires is a
  future RFC 3339 date no more than twelve months ahead.
- After merge, the GitHub Scorecard `Security-Policy` check and the CLOMonitor `security_policy` check pass.
