# Spec Delta

## Purpose

Every commit a contributor adds through a pull request carries a Developer Certificate of Origin sign-off that names the author, or it is recognised as an upstream change that the fork cherry-picked. Bot pull requests are exempt as a whole. The check reports first and enforces after a dated switch. No operator-facing behaviour changes.

## ADDED Requirements

### Requirement: Pull request commits are checked for sign-off

The repository SHALL run a DCO check from `.github/workflows/dco.yml` on pull requests into `main`. The check SHALL examine every non-merge commit between the base and the head of the pull request.

#### Scenario: Signed commit

- **WHEN** a commit carries a `Signed-off-by` line whose address equals the commit's author address
- **THEN** the commit passes the check

#### Scenario: Unsigned commit

- **WHEN** a commit carries no sign-off and no cherry-pick trailer
- **THEN** the commit is reported with its sha and the step fails in `enforce` mode and only warns in `report` mode

### Requirement: Upstream cherry-picks are exempt by evidence

A commit with a `(cherry picked from commit <sha>)` trailer SHALL pass when the named commit is an ancestor of the upstream `main` branch of `dzikoysk/reposilite`. The author name of a commit MUST NOT be used as an exemption.

#### Scenario: Cherry-pick of an upstream commit

- **WHEN** a commit names an upstream commit that is an ancestor of `upstream/main`
- **THEN** the commit passes without a sign-off

#### Scenario: Forged trailer

- **WHEN** a commit names a sha that is not an ancestor of `upstream/main`, or the upstream history cannot be fetched
- **THEN** the commit is reported as unverified and fails in `enforce` mode

### Requirement: Bot pull requests are exempt

A pull request whose author is a GitHub App account (`user.type` equal to `Bot`) SHALL pass the check as a whole. Human pull requests MUST NOT be exempted by any commit author.

#### Scenario: Dependency update

- **WHEN** Dependabot or Renovate opens a pull request into `main`
- **THEN** the DCO check passes without sign-offs on its commits

### Requirement: The check reports first and enforces after a switch

The check SHALL read the repository variable `DCO_MODE`. An unset value or `report` SHALL NOT fail the step. `enforce` SHALL fail the step for every failing commit. Any other value SHALL fail the step with a message that names the value.

#### Scenario: Report mode

- **WHEN** `DCO_MODE` is unset and a commit is unsigned
- **THEN** the step passes and writes a warning annotation that names the commit

#### Scenario: Misspelled mode

- **WHEN** `DCO_MODE` holds a value other than `report` or `enforce`
- **THEN** the step fails with a message that names the value

### Requirement: The check holds no write permission

The DCO workflow SHALL declare `contents: read` and no other permission, and SHALL run on the `pull_request` event only.

#### Scenario: Permissions

- **WHEN** `.github/workflows/dco.yml` is read
- **THEN** its only permission is `contents: read` and its trigger is `pull_request`

### Requirement: Contributor documentation describes the check

`.github/CONTRIBUTING.md` SHALL describe the sign-off, the report and enforce modes, the cherry-pick exemption and the commands for adding a sign-off to an existing branch. `.github/MAINTAINING.md` SHALL describe the exemption and the use of `git cherry-pick -x`.

#### Scenario: Reading the contributing guide

- **WHEN** a contributor reads the "Sign your commits" section
- **THEN** the section names `git commit -s`, `git rebase --signoff <base>` and the cherry-pick exemption
