# Design

## Context

Facts checked on the worktree and on origin/main:

- `.github/CONTRIBUTING.md:12-14` tells readers to ask in "our community channel" and holds `TODO(onelitefeather): community
  link`.
- `.github/README.md:172` holds `<!-- TODO(onelitefeather): community link for user questions and discussion. -->` in the
  Contributing section.
- `.github/ISSUE_TEMPLATE/config.yml` has three TODO comments (community link, documentation link and whether Discussions is
  enabled), and two commented contact entries with `TODO(onelitefeather)` URLs. Its header says the upstream Discord and
  documentation were removed on purpose and must be replaced by Ingot equivalents.
- `.github/CODE_OF_CONDUCT.md` has no attribution line. Its opening paragraphs describe the scope of the code, and the reporting
  placeholder at line 36 belongs to `add-repository-trust-files`.
- GitHub Discussions is disabled on the repository (`has_discussions: false`). The repository has no `GOVERNANCE.md` yet; it is
  created by `add-repository-trust-files`.
- The CNCF Code of Conduct is an adaptation of the Contributor Covenant 2.0 and routes project-agnostic reports to its Code of
  Conduct Committee (`cncf/foundation`, `code-of-conduct.md`, read on the day of writing). The CNCF page checked did not state whether a
  project must adopt it on acceptance, so that claim is not relied on here.

## Decision 1: decision rules in GOVERNANCE.md

These rules are defaults that the maintainer confirms (task 2.1). They are written so that an ordinary change does not need a
meeting, and a disagreement has an end.

- **Contributor.** Anyone who has a change merged. Contributors have no rights in the repository beyond opening issues and pull
  requests.
- **Committer.** A person with write access who may review and merge changes that a maintainer has approved in the same area.
  A maintainer nominates a committer, and the maintainers decide by majority. Write access is granted by the organisation
  owners, as the settings allow.
- **Maintainer.** A person listed in `MAINTAINERS.md`. Maintainers decide releases, security advisories, changes to the
  governance and the adoption of new maintainers, and they are the reviewers named by `CODEOWNERS`.
- **Lazy consensus.** An ordinary change proposed in an issue or a pull request is accepted when no maintainer has objected
  within seven days of the proposal, and at least one maintainer has approved the pull request. An objection has to state the
  concrete problem, so that the author can act on it.
- **Conflict resolution.** An unresolved objection is discussed in the issue or pull request first. If it stays unresolved, the
  maintainers decide by majority of the listed maintainers. A tie keeps the current state. Security reports follow the security
  policy and are not decided in public.

## Decision 2: the upstream relation

It is not repeated. `add-repository-trust-files` states that Ingot is independent of Reposilite and that upstream changes arrive
through `.github/MAINTAINING.md`. The new sections refer to that statement and do not restate it.

## Decision 3: code of conduct text

The current text is kept. Adopting the CNCF text is not done, for three reasons:

- The CNCF text routes project-agnostic reports to the CNCF Code of Conduct Committee. Ingot is not a CNCF project, so that
  route would send reports to a body with no authority over Ingot.
- The Ingot file has its own scope section that names the OneLiteFeather communities, which the CNCF text does not cover.
- Replacing the text would be a larger change than the task needs, and it would change every reader's expectations of a
  document already in use.

If the project applies to the CNCF, the question is asked again then. The open question on the origin of the current wording
(which, if it is adapted from the Contributor Covenant, needs an attribution line under that licence) is recorded in
`tasks.md`, not answered here.

## Decision 4: community link

GitHub Discussions is the default channel. It needs no new account, no new service and no invented address, and it is in the
repository. The link is `https://github.com/OneLiteFeatherNET/ingot/discussions`. It is used in `CONTRIBUTING.md`, in the README
Contributing section and in the issue template as a contact link. The Discussions setting is switched on by the maintainer before
the code pull request, and the task verifies that the link answers HTTP 200.

The documentation link is `https://github.com/OneLiteFeatherNET/ingot/tree/main/docs/gitbook`, the GitBook source in this
repository. It replaces the upstream documentation that the template says was removed on purpose. It is replaced when a site
exists.

## Decision 5: the issue template

The commented entries are enabled with the two links of decision 4, and the three TODO comments at the top of the file are
removed, since the Discussions decision is now made. The security contact entry, which is already live, is not changed.
