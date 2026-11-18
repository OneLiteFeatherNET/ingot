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

- **Governance text stays as it is.** `GOVERNANCE.md` on main holds the decision rules: the roles, the lead maintainer's decisions and
  the tie rule. This change keeps them and adds one sentence: security reports follow `SECURITY.md` and are not decided in public. The
  rules of the first draft (a committer role, a seven-day lazy consensus for ordinary changes, a majority vote with a tie that keeps the
  current state) are not adopted, because the file on main does not have them.
- **Code of conduct decision.** The current text is kept. The reasons are recorded in `design.md` and in the code pull request
  description. Its origin is not confirmed, and the maintainer decides on an attribution line once a source is named.
- **Community links.** GitHub Discussions is the community channel and is enabled. The issue template's documentation contact link
  points at the `docs/gitbook` folder on GitHub instead of `docs.onelitefeather.net/ingot`.

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
  - `add-repository-trust-files` has merged. It created `GOVERNANCE.md`, which this change reads.
  - GitHub Discussions is enabled on the repository (`has_discussions` is `true`, checked on 2026-11-18).
  - `complete-security-disclosure` and `add-project-health-files` edit other hunks of `.github/README.md`.
- **Code.** None. `GOVERNANCE.md` (one sentence) and `.github/ISSUE_TEMPLATE/config.yml` (the documentation link).

## Verification

- `openspec validate define-governance-and-community --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/define-governance-and-community` prints nothing. The new files must not
  contain an em or en dash either.
- After merge, no `TODO(onelitefeather): community link` and no `TODO(onelitefeather): documentation link` marker is left in the
  changed files, and the community link answers HTTP 200.
