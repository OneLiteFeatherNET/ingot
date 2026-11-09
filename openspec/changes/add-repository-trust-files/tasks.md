# Tasks

This change is documentation only. It has no production code, so no unit test is written. Each group is one commit of one
Conventional Commits type, all with the scope `community`. The checks named in each task are the verification, and they run
before the commit. Nothing in this change is a Gradle run, so no `heavy` job is needed. No configuration key, environment
variable or manifest changes, so no page under `docs/gitbook/` is updated.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-repository-trust-files`: this change and nothing else, branched from the remote main.
  Verify: `openspec validate add-repository-trust-files --strict` passes and the dash grep from the proposal prints nothing.
  The proposal PR is merged before the code PR is opened.

## 2. Maintainer input

- [ ] 2.1 The maintainer supplies, before any file is committed: the private security contact address, the code of conduct
  reporting address, the maintainer list (name, GitHub handle, role, how to reach them), the adopter entry for OneLiteFeather
  and the decision rules for `GOVERNANCE.md`. The maintainer also confirms that private vulnerability reporting is enabled in
  the repository settings. Record the answers in the code PR description, not in a file. Verify: no `TODO(onelitefeather)`
  marker is left in the drafts, and the security contact address is the same string in every file that names it.

## 3. Trust files

- [ ] 3.1 `docs(community)`: add `MAINTAINERS.md`, `GOVERNANCE.md` and `ADOPTERS.md` at the repository root, and link
  `MAINTAINERS.md` and `GOVERNANCE.md` from the `Maintainers` section of `.github/CONTRIBUTING.md`. `MAINTAINERS.md` links
  `.github/MAINTAINING.md` and does not repeat its content. Verify: every relative link resolves to an existing file, and
  `grep -rn 'TODO(onelitefeather)'` on the three new files prints nothing.
- [ ] 3.2 `docs(community)`: add the security and conduct contacts to `.github/SECURITY.md` and `.github/CODE_OF_CONDUCT.md`,
  replacing the markers, and add `SECURITY-INSIGHTS.yml` at the repository root with the same security contact. Use the
  OpenSSF Security Insights schema version current at apply time and check it against the upstream specification. Verify:
  `python3 -I -c 'import yaml,sys; yaml.safe_load(open(sys.argv[1]))' SECURITY-INSIGHTS.yml` exits 0, and the contact string
  in the three files is identical.

## 4. Pull request

- [ ] 4.1 Open the code pull request with the title `docs(community): add maintainers, governance and security contacts`.
  Read `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections, using the type `docs` in the type list. The description
  tells the reviewer what a reporter, an adopter or a new contributor finds now and what they find after the merge, without
  file names. It carries no session link and no attribution line. Verify: `gh pr view --json title` shows the exact title, the
  commit-style hook accepts the title and every commit, and the body has no em or en dash.
