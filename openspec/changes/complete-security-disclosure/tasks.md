# Tasks

This change is documentation only. It has no production code, so no unit test is written. The checks named in each task
replace it. Each group is one commit of one Conventional Commits type, `docs`, with the scope `security`. No configuration
key, environment variable or manifest changes, so no page under `docs/gitbook/` is updated. No file in this change may
contain an em or en dash.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose complete-security-disclosure`: this change only, branched from the remote main. Verify:
  `openspec validate complete-security-disclosure --strict` passes and the dash grep from the proposal prints nothing. The
  proposal PR is merged before the code PR is opened.

## 2. Maintainer input

- [ ] 2.1 The maintainer confirms the defaults before the code PR: acknowledgement within 14 days, disclosure window of 90
  days, CVE through the GitHub Security Advisory, `Preferred-Languages: en`, an Expires date at most six months after the
  merge, and the repository copy as the reference for `security.txt`. The answers go into the code pull request description.
  Verify: no default is changed without the maintainer's answer.

## 3. Policy and security.txt

- [ ] 3.1 `docs(security)`: in `.github/SECURITY.md`, replace the first paragraph and the `Supported Versions` table with the
  current fact from `design.md` decision 5, add the acknowledgement target to `Reporting a Vulnerability` and the window and
  CVE handling to `Disclosure`. Do not touch the mailbox sentence or its marker, which belong to `add-repository-trust-files`.
  Verify: `git diff` touches only those sections, and `grep -n 'not published its own release\|unreleased'` on the file prints
  nothing.
- [ ] 3.2 `docs(security)`: add `.well-known/security.txt` with `Contact` set to the advisory form, `Policy` set to
  `https://github.com/OneLiteFeatherNET/ingot/security/policy`, `Expires` set to the agreed date and `Preferred-Languages: en`.
  Verify: a parse check run with `python3 -I` confirms that Contact and Expires appear exactly once, that Expires is a future
  RFC 3339 date no more than twelve months ahead, and that the file is UTF-8 with no em or en dash.

## 4. Verification

- [ ] 4.1 Relative links and the advisory form link in the changed files resolve (a markdown link check; relative links
  checked offline). Verify: the check prints no broken link.
- [ ] 4.2 Consistency with the trust files: once `add-repository-trust-files` has merged, its `SECURITY-INSIGHTS.yml` names the
  same advisory channel and the same 14-day and 90-day values. If it does not, the difference is reported to the maintainer
  before this pull request merges. Verify: `grep -n '14\|90'` on that file shows the same values.
- [ ] 4.3 After merge on main: the Scorecard `Security-Policy` check and the CLOMonitor `security_policy` check pass.
  Verify: the next Scorecard run on main completes, and the CLOMonitor report for the repository lists both as passing.

## 5. Pull request

- [ ] 5.1 Open the code pull request with the title `docs(security): complete the disclosure policy and add security.txt`.
  Read `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells a reporter what they see now and what
  they see after the merge, without file names. It carries no session link and no attribution line. Verify:
  `gh pr view --json title` shows the exact title, the commit-style hook accepts the title and every commit, and the body
  has no em or en dash.
