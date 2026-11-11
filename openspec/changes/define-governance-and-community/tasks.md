# Tasks

This change is documentation only. It has no production code, so no unit test is written. The checks named in each task
replace it. Each group is one commit of one Conventional Commits type, `docs`, with the scope `community`. No configuration
key, environment variable or manifest changes, so no page under `docs/gitbook/` is updated. No file in this change may contain
an em or en dash. This change is applied after `add-repository-trust-files` has merged, because it adds to `GOVERNANCE.md`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose define-governance-and-community`: this change only, branched from the remote main. Verify:
  `openspec validate define-governance-and-community --strict` passes and the dash grep from the proposal prints nothing. The
  proposal PR is merged before the code PR is opened.

## 2. Maintainer input

- [ ] 2.1 The maintainer confirms the decision rules of `design.md` decision 1: the three roles, the seven-day lazy consensus,
  the majority decision and the tie rule, and how a committer is nominated. Verify: the answers are recorded in the code pull
  request description, and the text of `GOVERNANCE.md` matches them.
- [ ] 2.2 The maintainer confirms the code of conduct decision (keep the current text) and states where the current wording came
  from. If the text is adapted from the Contributor Covenant, the maintainer decides on an attribution line under that licence,
  and the attribution is added in the same pull request. Verify: the decision is recorded in the pull request description.
- [ ] 2.3 The maintainer confirms that GitHub Discussions is enabled (`has_discussions: true` in `gh api
  repos/OneLiteFeatherNET/ingot`), or chooses another channel before the code pull request. Verify: the value is read before
  task 3.2 starts.

## 3. Governance and community links

- [ ] 3.1 `docs(community)`: in `GOVERNANCE.md`, add the sections `Roles`, `Lazy consensus` and `Conflict resolution` with the
  text of `design.md` decision 1, refer to the upstream statement of the trust-files change without repeating it. Verify: the
  headings exist, the file has no `TODO(onelitefeather)` marker, and the upstream statement is not duplicated (`grep -n
  'upstream'` shows one statement).
- [ ] 3.2 `docs(community)`: replace the community placeholder in `.github/CONTRIBUTING.md` with the Discussions link, in the
  paragraph that now says "GitHub Discussions" instead of "community channel", and replace the README placeholder at line 172
  with the same link and the documentation link. Verify: `grep -rn 'TODO(onelitefeather): community link'` on the two files
  prints nothing, and both links answer HTTP 200.
- [ ] 3.3 `docs(community)`: in `.github/ISSUE_TEMPLATE/config.yml`, remove the three TODO comments at the top, and enable the
  community and documentation contact links with the two URLs of `design.md` decision 4. The security entry stays as it is.
  Verify: the file parses as YAML with `python3 -I -c 'import yaml,sys; yaml.safe_load(open(sys.argv[1]))'`, and no `TODO`
  remains in it.

## 4. Verification

- [ ] 4.1 Relative links and the two external links in the changed files resolve (a markdown link check). Verify: the check
  prints no broken link.
- [ ] 4.2 Open questions recorded: the code pull request description lists the answers of tasks 2.1 to 2.3, and states the
  documentation link as a default until a site exists. Verify: the description contains the three answers and no contact
  address that the maintainer has not supplied.
- [ ] 4.3 After merge on main: CLOMonitor passes `contributing` and `code_of_conduct`, and `github_discussions` passes once the
  setting is on. Verify: the CLOMonitor report of the repository.

## 5. Pull request

- [ ] 5.1 Open the code pull request with the title `docs(community): define governance decisions and community links`. Read
  `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells a new contributor what they find now when
  they look for help, and how a decision is reached, without file names. It carries no session link and no attribution line.
  Verify: `gh pr view --json title` shows the exact title, the commit-style hook accepts the title and every commit, and the body
  has no em or en dash.
