# Tasks

This change is documentation only. It has no production code, so no unit test is written. The checks named in each task
replace it. Each group is one commit of one Conventional Commits type, `docs`, with the scope `community`. No configuration
key, environment variable or manifest changes, so no page under `docs/gitbook/` is updated. No file in this change may contain
an em or en dash. `GOVERNANCE.md` on main already holds the decision rules, so this change does not wait for another change; it
adds one sentence to that file (decision 1 of `design.md`).

## 1. Proposal

- [x] 1.1 `docs(openspec): propose define-governance-and-community`: this change only, branched from the remote main. Verify:
  `openspec validate define-governance-and-community --strict` passes and the dash grep from the proposal prints nothing. The
  proposal PR is merged before the code PR is opened.

## 2. Maintainer input

- [x] 2.1 Decided 2026-11-18: the rules of `GOVERNANCE.md` on main apply, as decision 1 of `design.md` records. The lead maintainer
  decides a tie. Verify: the answer is recorded in the code pull request description, and `GOVERNANCE.md` keeps its roles and
  decision sections unchanged apart from the one added sentence.
- [ ] 2.2 Open: the origin of the code of conduct wording. The text is kept (decision 3 of `design.md`), and it does not name the
  Contributor Covenant. The maintainer names a source. If the source is the Contributor Covenant or another licensed text, the
  maintainer decides on an attribution line under that licence, and the attribution is added in the same pull request. Verify: the
  decision is recorded in the pull request description.
- [x] 2.3 Done on 2026-11-18: GitHub Discussions is enabled (`has_discussions` is `true`). Verify: the value is read again before the
  code pull request is opened.

## 3. Governance and community links

- [x] 3.1 `docs(community)`: in `GOVERNANCE.md`, add one sentence under "How decisions are taken": security reports follow
  `.github/SECURITY.md` and are not decided in public. Nothing else in the file changes, so the roles and the decision rules stay as
  they are on main. Verify: `git diff origin/main -- GOVERNANCE.md` shows one added line, the file has no `TODO(onelitefeather)` marker,
  and the sentence is present once.
- [x] 3.2 Already on main: `.github/CONTRIBUTING.md` and `.github/README.md` link GitHub Discussions, and no community placeholder is
  left. No edit. Verify: `git grep -n 'TODO(onelitefeather)' -- .github GOVERNANCE.md` prints nothing, and the Discussions link answers
  HTTP 200.
- [x] 3.3 `docs(community)`: in `.github/ISSUE_TEMPLATE/config.yml`, change the documentation contact link to
  `https://github.com/OneLiteFeatherNET/ingot/tree/main/docs/gitbook` (decision 4 of `design.md`), and change the header comment's
  sentence about the documentation site to say that documentation is in the `docs/gitbook` folder of this repository. The three TODO
  comments are already gone on main. The security entry and the commented upstream entry stay as they are. Verify: the file parses as
  YAML with `python3 -I -c 'import yaml,sys; yaml.safe_load(open(sys.argv[1]))' <path>`, and no `TODO` remains in it.

## 4. Verification

- [x] 4.1 Relative links and the two external links in the changed files resolve (a markdown link check). Verify: the check
  prints no broken link.
- [x] 4.2 Decisions recorded: the code pull request description lists the answers of tasks 2.1 and 2.3, the open origin of task 2.2,
  and the documentation link of decision 4 as decided on 2026-11-18. Verify: the description contains these answers and no contact
  address that the maintainer has not supplied.
- [ ] 4.3 After merge on main: CLOMonitor passes `contributing` and `code_of_conduct`, and `github_discussions` passes once the
  setting is on. Verify: the CLOMonitor report of the repository.

## 5. Pull request

- [x] 5.1 Open the code pull request with the title `docs(community): define governance decisions and community links`. Read
  `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells a new contributor what they find now when
  they look for help, and how a decision is reached, without file names. It carries no session link and no attribution line.
  Verify: `gh pr view --json title` shows the exact title, the commit-style hook accepts the title and every commit, and the body
  has no em or en dash.
