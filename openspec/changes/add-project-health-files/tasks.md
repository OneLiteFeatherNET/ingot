# Tasks

This change is documentation and repository metadata only. It has no production code, so no unit test is written. The checks
named in each task replace it. Each group is one commit of one Conventional Commits type, `docs`, with the scope `project`.
No configuration key, environment variable or manifest changes, so no page under `docs/gitbook/` is updated. No file in this
change may contain an em or en dash.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-project-health-files`: this change only, branched from the remote main. Verify:
  `openspec validate add-project-health-files --strict` passes and the dash grep from the proposal prints nothing. The proposal
  PR is merged before the code PR is opened.

## 2. Maintainer input

- [ ] 2.1 The maintainer confirms the repository settings table in the proposal, and in particular that the team
  `ingot-maintainers` has write or maintain access to the repository. The maintainer makes the changes by hand. Verify, with
  read-only calls: `gh api repos/OneLiteFeatherNET/ingot/teams` lists the team with a write or maintain permission, and
  `gh api repos/OneLiteFeatherNET/ingot/private-vulnerability-reporting` still reports `enabled: true`.
- [ ] 2.2 The maintainer confirms the six exemption reasons in `design.md` decision 5, or replaces them. Verify: each reason
  in the code pull request description matches the file.
- [ ] 2.3 The maintainer confirms every row of the two roadmap tables in `design.md` decision 3, and names any change that is
  already implemented or no longer wanted. Verify: the ROADMAP in the code pull request lists only confirmed rows.

## 3. Status, badge and roadmap

- [ ] 3.1 `docs(project)`: in `.github/README.md`, replace the sentence that no Ingot release has been cut with a link to the
  releases page, keeping the note that the quickstart builds from source. Verify: `grep -n 'No Ingot release has been cut'`
  on the file prints nothing, and the link resolves.
- [ ] 3.2 `docs(project)`: add the OpenSSF Scorecard badge to the README header, next to the licence badge, using the image and
  link in `design.md` decision 2. Verify: the image URL returns HTTP 200 and the badge renders in the GitHub preview.
- [ ] 3.3 `docs(project)`: add `ROADMAP.md` at the repository root with the confirmed rows of task 2.3, each linking
  `openspec/changes/<name>/proposal.md`, and the statement that dates are not promised and planning detail lives outside the
  repository. Verify: every link resolves to an existing proposal on the branch.

## 4. Owners and health report

- [ ] 4.1 `docs(project)`: add `.github/CODEOWNERS` with the single line from `design.md` decision 4. Verify: `gh api` on the
  team confirms the slug, and the file is read by GitHub's CODEOWNERS validation (the repository's CODEOWNERS errors view
  shows no error).
- [ ] 4.2 `docs(project)`: add `.clomonitor.yml` with the exemptions of `design.md` decision 5, each with its confirmed reason.
  Verify: the file parses as YAML with `python3 -I -c 'import yaml,sys; yaml.safe_load(open(sys.argv[1]))'`, and every check name
  appears in the CLOMonitor check list (`docs/checks.md` of `cncf/clomonitor`).

## 5. Verification

- [ ] 5.1 Relative links in every changed file resolve (a markdown link check, relative links offline). Verify: the check prints
  no broken link.
- [ ] 5.2 After merge on main: CLOMonitor passes `readme`, `roadmap`, `maintainers`, `changelog`, `contributing`,
  `security_policy`, `code_of_conduct` and `openssf_scorecard_badge`, and the exempted checks show their reasons. Verify: the
  CLOMonitor report of the repository, read in the browser or from its API.

## 6. Pull request

- [ ] 6.1 Open the code pull request with the title `docs(project): add roadmap, codeowners and the scorecard badge`. Read
  `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells a reader what they see on the repository
  front page now, without file names. It carries no session link and no attribution line. Verify: `gh pr view --json title`
  shows the exact title, the commit-style hook accepts the title and every commit, and the body has no em or en dash.
