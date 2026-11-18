# Spec Delta

## Purpose

A contributor learns how an ordinary change is decided, how a disagreement ends and where to ask a question, without a meeting or a
private channel. The decision rules are those of `GOVERNANCE.md` on main, which this change keeps.

## ADDED Requirements

### Requirement: Governance names the roles

`GOVERNANCE.md` SHALL describe the roles contributor, maintainer and lead maintainer, and SHALL state that the lead maintainer appoints
new maintainers.

#### Scenario: Contributor asks who may merge

- **WHEN** a contributor reads the roles section of `GOVERNANCE.md`
- **THEN** the section states that maintainers review and merge changes, and that the lead maintainer is the maintainer marked as lead in `MAINTAINERS.md`

### Requirement: Ordinary changes are decided by the maintainers

`GOVERNANCE.md` SHALL state that an ordinary change is decided by the lead maintainer while Ingot has one maintainer, and that it needs
the approval of one maintainer once Ingot has two or more.

#### Scenario: One maintainer

- **WHEN** Ingot has one maintainer and a pull request from outside the project is open
- **THEN** the governance text states that the pull request merges only with the lead maintainer's approval

### Requirement: Conflicts end in a stated decision

`GOVERNANCE.md` SHALL state that an objection which stays open is decided by the lead maintainer, including a tie, that the outcome is
recorded in the pull request or the issue, and that security reports follow `SECURITY.md` and are not decided in public.

#### Scenario: Two maintainers disagree

- **WHEN** an objection stays open after discussion, including a tie between maintainers
- **THEN** the governance text names the lead maintainer as the one who decides

#### Scenario: Security report

- **WHEN** a security report is raised as a public objection
- **THEN** the governance text states that it is handled under `SECURITY.md` and is not decided in public

### Requirement: Community links resolve

The README, `.github/CONTRIBUTING.md` and `.github/ISSUE_TEMPLATE/config.yml` SHALL NOT contain a `TODO(onelitefeather)` community
link or documentation link, and every community link SHALL resolve to a page of the repository or of GitHub Discussions.

#### Scenario: New user looks for help

- **WHEN** a new user opens the issue chooser or the Contributing section of the README
- **THEN** a community link and a documentation link are shown, and both open a page

### Requirement: Code of conduct decision is recorded

The change SHALL keep the current code of conduct text unchanged, and the decision, its reasons and the open question on the text's
origin SHALL be recorded in the code pull request description.

#### Scenario: Maintainer asks why the CNCF text is not used

- **WHEN** a maintainer reviews the code of conduct decision
- **THEN** the pull request description states the reasons and the open question on the text's origin

### Requirement: Governance changes no runtime behaviour

The change SHALL NOT alter code, configuration keys, environment variables, the container image or the Helm chart.

#### Scenario: Drop-in check

- **WHEN** the change is reviewed against the drop-in contract
- **THEN** the changed paths are limited to `.github/` and `GOVERNANCE.md`, and an existing installation needs no change
