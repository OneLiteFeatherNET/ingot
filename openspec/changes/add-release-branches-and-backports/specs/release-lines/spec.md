# Spec Delta

## Purpose

A release line that is still supported can receive a patch release from its own branch, the support tables and the labels say
the same thing as the mechanism, and `latest` keeps pointing at the newest line.

## ADDED Requirements

### Requirement: The support lines are listed in one file

The repository SHALL list every release line in `support-lines.json`, with its kind (`regular` or `lts`) and its end of life
date. The support tables in `SUPPORT.md` and `.github/SECURITY.md` and the set of `backport:` labels SHALL be generated from or
checked against that file.

#### Scenario: A table that differs from the file fails the check

- **WHEN** the rendered support table of `SUPPORT.md` differs from the table rendered from `support-lines.json`
- **THEN** the check fails and names the file

#### Scenario: A regular line ends 90 days after its successor ships

- **WHEN** a line with kind `regular` has a successor released on a given date
- **THEN** its end of life date is that date plus 90 days

### Requirement: A release branch is cut when the next line ships

A release branch `release/<major>.<minor>.x` SHALL be created from the last tag of the previous line when a release with patch
version 0 is created on `main`. The branch SHALL NOT be created before that release. The branch SHALL point at the previous
line's last tag, not at the tip of `main`.

#### Scenario: No branch exists while the line is the newest

- **WHEN** the newest line has not been superseded
- **THEN** no release branch exists for it

#### Scenario: The branch starts at the last release of the old line

- **WHEN** release 2.0.0 is created on `main` and the last release of the 1.1 line is `v1.1.1`
- **THEN** `release/1.1.x` points at the commit of `v1.1.1`

#### Scenario: An existing branch that points elsewhere is not overwritten

- **WHEN** the branch already exists and does not point at the last tag of the old line
- **THEN** the cut job fails and does not move the branch

### Requirement: Release Please produces only patch releases on a release branch

Release Please SHALL run for each release branch with that branch as its target, its own manifest and its own changelog, and with
the `always-bump-patch` versioning strategy. Every version it produces on a branch SHALL have the branch's major and minor.

#### Scenario: A backported feature is a patch release

- **WHEN** a `feat` commit is backported to `release/1.1.x` and released
- **THEN** the version is `1.1.<patch + 1>`

#### Scenario: A version outside the line is refused

- **WHEN** a release run on `release/1.1.x` would produce a version that does not start with `1.1.`
- **THEN** the release step fails before a tag is created

### Requirement: The GitHub Latest flag and the container latest tag stay on the newest line

The GitHub release marked Latest SHALL be the highest version tag across all lines. The container tag `latest` SHALL point at the
highest version across all lines. A release on an older line SHALL NOT move either of them.

#### Scenario: A patch on an old line leaves latest alone

- **WHEN** `1.1.2` is released from `release/1.1.x` while `2.0.0` is the highest version
- **THEN** the Latest flag and the container tag `latest` still refer to `2.0.0`

#### Scenario: The version tag of the old line moves with its patches

- **WHEN** `1.1.2` is released from `release/1.1.x`
- **THEN** the container tags `1.1.2` and `1.1` point at that image, and the tag `1` points at it while no version `1.2` or higher exists

### Requirement: Nightly images are built from main only

The rolling `nightly` container tag SHALL be pushed only for runs on `main`. A push to a release branch SHALL NOT move it.

#### Scenario: A push to a release branch leaves nightly alone

- **WHEN** a commit is pushed to `release/1.1.x` and no release is created
- **THEN** no image is pushed and the `nightly` tag is unchanged

### Requirement: Release branches get the checks of main

Pull requests into release branches SHALL run the same checks as pull requests into `main`, including the image builds for both
architectures, the drop-in contract checks and the plugin API check. Release images SHALL carry an SBOM and provenance.

#### Scenario: A backport that breaks the plugin API fails its check

- **WHEN** a pull request into `release/1.1.x` changes the plugin API
- **THEN** the plugin API check fails and the pull request cannot be merged

### Requirement: A window ends with a reviewed change

When a line reaches its end of life date, a scheduled job SHALL open a pull request that marks the line archived in
`support-lines.json`, re-renders the tables and removes the `backport:<line>` label from open pull requests. The branch SHALL be
renamed to `archive/<line>.x`. Tags SHALL NOT be deleted or moved.

#### Scenario: The window ends on the date in the file

- **WHEN** the scheduled job runs on or after the end of life date of a line
- **THEN** it opens the archiving pull request for that line and changes nothing else

### Requirement: LTS is a data change

A line with kind `lts` SHALL be accepted by the check with any end of life date later than the regular one, and adding such a line
SHALL NOT require a change to the release workflow.

#### Scenario: An LTS line is added as data only

- **WHEN** a line with kind `lts` and a longer end of life date is added to `support-lines.json`
- **THEN** the check passes and no workflow file changes
