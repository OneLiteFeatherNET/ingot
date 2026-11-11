# Spec Delta

## Purpose

A contributor learns how an ordinary change is accepted, how a disagreement ends and where to ask a question, without a
meeting or a private channel.

## ADDED Requirements

### Requirement: Governance names the roles

`GOVERNANCE.md` SHALL describe the roles contributor, committer and maintainer, and SHALL state what each role may decide or
merge. It SHALL say how a committer is nominated.

#### Scenario: Contributor asks what a committer may merge

- **WHEN** a contributor reads the roles section of `GOVERNANCE.md`
- **THEN** the section states that a committer may merge changes a maintainer has approved in the same area

### Requirement: Ordinary changes are accepted by lazy consensus

`GOVERNANCE.md` SHALL state that an ordinary change is accepted when no maintainer objects within seven days of the proposal and
at least one maintainer has approved it. An objection SHALL state a concrete problem.

#### Scenario: Maintainer does not respond

- **WHEN** a proposal is open for seven days with one maintainer approval and no objection
- **THEN** the governance text allows the change to be merged

### Requirement: Conflicts end in a stated decision

`GOVERNANCE.md` SHALL state that an unresolved objection is decided by a majority of the listed maintainers, that a tie keeps the
current state, and that security reports are not decided in public.

#### Scenario: Two maintainers disagree

- **WHEN** an objection stays unresolved after discussion
- **THEN** the governance text names the majority rule and the tie rule that decide it

### Requirement: Community links resolve

The README, `.github/CONTRIBUTING.md` and `.github/ISSUE_TEMPLATE/config.yml` SHALL NOT contain a `TODO(onelitefeather)` community
link or documentation link, and every community link SHALL resolve to a page of the repository or of GitHub Discussions.

#### Scenario: New user looks for help

- **WHEN** a new user opens the issue chooser or the Contributing section of the README
- **THEN** a community link and a documentation link are shown, and both open a page

### Requirement: Code of conduct decision is recorded

The change SHALL keep the current code of conduct text unchanged, and the decision and its reasons SHALL be recorded in the code
pull request description.

#### Scenario: Maintainer asks why the CNCF text is not used

- **WHEN** a maintainer reviews the code of conduct decision
- **THEN** the pull request description states the reasons and the open question on the text's origin

### Requirement: Governance changes no runtime behaviour

The change SHALL NOT alter code, configuration keys, environment variables, the container image or the Helm chart.

#### Scenario: Drop-in check

- **WHEN** the change is reviewed against the drop-in contract
- **THEN** the changed paths are limited to `.github/` and `GOVERNANCE.md`, and an existing installation needs no change
