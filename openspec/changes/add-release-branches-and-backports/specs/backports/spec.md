# Spec Delta

## Purpose

A fix that lands on `main` reaches a supported release line through a reviewed pull request, and a change that cannot be applied
cleanly is reported to the people who can resolve it. Only security fixes reach a previous line; everything else ships with the
next release of `main`.

## ADDED Requirements

### Requirement: A backport label starts a backport

A merged pull request into `main` that carries a label `backport:<major>.<minor>` for a line with a release branch and whose title
is an eligible security fix SHALL get a backport pull request into `release/<major>.<minor>.x`, opened by the release bot.

#### Scenario: A labelled security fix gets a backport pull request

- **WHEN** a merged `fix(security)` pull request carries `backport:1.1` and `release/1.1.x` exists
- **THEN** a pull request into `release/1.1.x` is opened with the same title as the source pull request

#### Scenario: A label for a line without a branch is refused

- **WHEN** the label names a line that has no release branch
- **THEN** no branch is created and the source pull request receives a comment that names the missing line

### Requirement: Only security fixes are backported to a previous line

Only a `fix(security)` commit SHALL be backported to a previous line. Every other change SHALL stay on `main` and SHALL NOT be
backported, whatever labels it carries.

#### Scenario: A security fix is backported

- **WHEN** a merged pull request titled `fix(security): ...` without a breaking marker carries a backport label for a line with a branch
- **THEN** a backport pull request is opened into that release branch

#### Scenario: A non-security fix is refused

- **WHEN** a merged pull request titled `fix: ...` carries a backport label
- **THEN** no backport pull request is opened and the source pull request receives a comment that says the fix ships with the next release of `main`

#### Scenario: A change that is not a security fix is never backported

- **WHEN** a `docs`, `test`, `build`, `ci`, `refactor`, `chore` or `revert` pull request carries a backport label
- **THEN** no backport pull request is opened

#### Scenario: A performance change is refused

- **WHEN** a merged pull request titled `perf: ...` carries a backport label
- **THEN** no backport pull request is opened and the source pull request receives a comment that gives the reason

#### Scenario: A feature is refused

- **WHEN** a merged pull request with title type `feat` carries a backport label
- **THEN** no backport pull request is opened and the source pull request receives a comment that gives the reason

#### Scenario: A breaking security fix is refused

- **WHEN** a merged pull request titled `fix(security)!: ...` or with a `BREAKING CHANGE:` footer carries a backport label
- **THEN** no backport pull request is opened

#### Scenario: No label overrides the security-only rule

- **WHEN** a merged pull request titled `docs: ...` carries a backport label and any other label
- **THEN** no backport pull request is opened

### Requirement: A backport keeps the commit conventions

A backport commit SHALL keep the message of the source commit, including its Conventional Commits title, and SHALL carry a sign-off
line. It SHALL contain no AI attribution, no `Co-Authored-By` trailer for a tool and no session link. The pull request body SHALL
name the source pull request.

#### Scenario: The backport commit is signed off

- **WHEN** the cherry-pick succeeds
- **THEN** the commit message contains a `Signed-off-by` line naming the actor who applied the label, and the
  `(cherry picked from commit ...)` trailer

#### Scenario: The label actor has no public email

- **WHEN** the actor who applied the label has no public email address
- **THEN** the job comments on the source pull request, and the maintainer adds the sign-off before the backport is merged

### Requirement: A conflict is reported on the source pull request

When the cherry-pick conflicts, the job SHALL abort, remove its branch and comment on the source pull request with the commands a
maintainer runs to finish the backport by hand.

#### Scenario: A conflicting cherry-pick leaves a comment and no branch

- **WHEN** the cherry-pick of a source commit conflicts on `release/1.1.x`
- **THEN** the source pull request receives one comment with the manual steps and no backport branch remains on the remote

### Requirement: Backports never reach main

A release branch SHALL NOT be merged into `main`. A change reaches `main` first and is cherry-picked to a release branch, never the
other way round.

#### Scenario: A pull request from a release branch into main is refused

- **WHEN** a pull request has `release/1.1.x` as its head and `main` as its base
- **THEN** the check fails and the pull request is not merged
