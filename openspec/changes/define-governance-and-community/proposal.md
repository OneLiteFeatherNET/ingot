# Proposal

## Why

A person who wants to ask a question about Ingot is sent to a community link that does not exist. `.github/CONTRIBUTING.md`
says "reach the maintainers through our community channel" and points at a placeholder. The issue template disables the
community and documentation links with the same placeholders, so the first screen a new user sees when reporting a problem
lists only the security link.

The governance text that `add-repository-trust-files` will add names the maintainers and the relation to upstream. It does
not say how an ordinary change is accepted without a vote, how a disagreement ends, or what a committer is. An operator who
wants to know whether the project will still exist in a year, or a contributor who wants to know whether a review is a
veto, finds no answer.

The code of conduct text carries a reporting placeholder in its own file, which the trust-files change fills. The text itself
has not been compared with the CNCF text, and the decision has never been written down.

## What Changes

- **Governance decision rules.** `GOVERNANCE.md` gains three sections after the trust-files change has created it: the roles
  contributor, committer and maintainer, lazy consensus for ordinary changes, and conflict resolution. The upstream relation
  is not repeated, because the trust-files change already states it.
- **Code of conduct decision.** The current text is kept. The reasons are recorded in `design.md` and in the code pull request
  description. Adopting the CNCF text is not done.
- **Community links.** The placeholder in `.github/CONTRIBUTING.md`, the placeholder in the README and the two commented
  contact links in `.github/ISSUE_TEMPLATE/config.yml` point at GitHub Discussions and at the documentation folder. The
  Discussions link is valid once the maintainer enables Discussions (settings table of `add-project-health-files`).

### Out of scope

- A chat channel. None is chosen, and none is linked.
- A published documentation site or handbook. The documentation link points at `docs/gitbook` on GitHub until one exists.
- The mailbox address and the code of conduct reporting address, which belong to `add-repository-trust-files`.
- `MAINTAINERS.md`, `ADOPTERS.md` and `SECURITY-INSIGHTS.yml`, which also belong to `add-repository-trust-files`.
- Legal questions about the code of conduct, such as attribution of the current text. They are an open question, not a change.

## Capabilities

### New Capabilities

- `project-governance`: the roles, the decision rules, the conflict resolution and the community links that a contributor
  follows.

## Impact

- **Type and scope.** The change ships under `docs(community)`. The pull request title, squash-merged onto main, is:

  `docs(community): define governance decisions and community links`

- **Breaking marker.** None. Documentation only. Nothing an operator configures changes.
- **Behaviour that changes by default.** None.
- **Dependencies on other changes.**
  - `add-repository-trust-files` creates `GOVERNANCE.md` and the `Maintainers` section of `.github/CONTRIBUTING.md`. This change
    adds to `GOVERNANCE.md` and must be applied after that change has merged.
  - `add-project-health-files` owns the settings change that enables GitHub Discussions. The community link must not be merged
    before that setting is on.
  - `complete-security-disclosure` and `add-project-health-files` edit other hunks of `.github/README.md`.
- **Code.** None. `GOVERNANCE.md`, `.github/CONTRIBUTING.md`, `.github/README.md`, `.github/ISSUE_TEMPLATE/config.yml`.

## Verification

- `openspec validate define-governance-and-community --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/define-governance-and-community` prints nothing. The new files must not
  contain an em or en dash either.
- After merge, no `TODO(onelitefeather): community link` and no `TODO(onelitefeather): documentation link` marker is left in the
  changed files, and the community link answers HTTP 200.
