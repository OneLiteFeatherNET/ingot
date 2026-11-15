# Design

## Context

`.github/SECURITY.md` is the file GitHub shows in its security tab and the file the Scorecard `Security-Policy` check
reads. Its reporting channel works today: `gh api repos/OneLiteFeatherNET/ingot/private-vulnerability-reporting` returns
`{"enabled":true}`, so the advisory form is a real first channel. What is missing is the commitment around it.

Facts checked against the worktree and origin/main:

- `.github/SECURITY.md:5-6` says Ingot "has not published its own release yet". `:8-14` is the table with the row
  `Ingot main (unreleased)`. `:24` is the `TODO(onelitefeather): security contact address` marker. `:28-29` promise
  acknowledgement "within a few days". `:36-37` describe disclosure without a window and without a CVE.
- Releases on GitHub: `v1.0.0` (2026-08-08), `v1.1.0` (2026-08-09) and `v1.1.1` (2026-10-16, marked Latest), checked with
  `gh release list`.
- No `.well-known/` directory and no `security.txt` exists. The server contains no route for either. The only
  `.well-known` references in the backend are the OIDC discovery client in
  `reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/identity/login/oidc/`.
- `.github/workflows/scorecard.yml` is on main and its runs on 2026-11-11 completed successfully.

## Decision 1: where security.txt is served

The repository holds the reference copy at `.well-known/security.txt`. RFC 9116 (section 3) places the served file at
`/.well-known/security.txt` on the website of the organisation it describes. The project website does not exist yet, so the
copy in the repository is the reference until it does.

Alternative considered: a route in the Ingot server. Rejected for this change. Every operator runs Ingot on their own host,
so such a route would publish OneLiteFeather's contact on third-party installations, and it is a runtime feature. It stays
a follow-up.

## Decision 2: Canonical and the mailbox

`Canonical` is optional in RFC 9116 (section 2.5.2). It is left out until a served URL exists, because a Canonical value that
does not match the URL the file is fetched from makes the file untrusted. The Contact field carries only the advisory form
for now. A mailbox line is added when `add-repository-trust-files` supplies the address. A placeholder inside `security.txt`
would make the published file invalid, so none is written.

## Decision 3: response targets

- Acknowledgement within 14 days. This is the value of the OpenSSF Best Practices criterion `vulnerability_report_response`,
  which is a passing-level requirement for projects that accept private reports. It is a default the maintainer confirms.
- Coordinated disclosure window of 90 days from the report. It is a default, not derived from a project rule. It can be
  extended by agreement, which the policy states, and the maintainer confirms the number.
- The policy keeps its existing sentence that a fixed patch timeline cannot be guaranteed, because the project is run by
  volunteers.

## Decision 4: CVE handling

GitHub Security Advisories can request a CVE for the repository. The policy names that path and does not promise that every
advisory gets one.

## Decision 5: Supported Versions wording

The section states the current fact only: fixes are made on the latest minor line, which is `1.1.x` at the time of writing,
and on `main`. Older Ingot lines are described as not covered by a published statement yet. The policy text does not link a
file that does not exist, and it does not mention OpenSpec change names, because the policy is public. Upstream rows stay as
they are.

## Decision 6: Expires

RFC 9116 section 2.5.5 recommends a date less than a year ahead and sets no maximum. The file uses six months after its merge
date. Renewal is a release step, so a stale file is noticed before its date passes.
