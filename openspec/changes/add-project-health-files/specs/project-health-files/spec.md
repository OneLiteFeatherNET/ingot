# Spec Delta

## Purpose

A reader of the repository front page sees a correct release status, a visible security workflow, a public list of what is
planned, and owners who can be asked to review, without any runtime change.

## ADDED Requirements

### Requirement: README states the release status

The README SHALL NOT state that no Ingot release has been cut. It SHALL link the releases page, and it SHALL NOT name a version
number that would need updating with each release.

#### Scenario: Reader looks for a release

- **WHEN** a reader reads the status paragraph of `.github/README.md`
- **THEN** the paragraph links the releases page and does not claim that no release exists

### Requirement: README shows the Scorecard badge

The README header SHALL show the OpenSSF Scorecard badge for the repository, and the badge SHALL link the Scorecard viewer for
the repository.

#### Scenario: Reader sees the security signal

- **WHEN** a reader opens the repository front page
- **THEN** the Scorecard badge is visible in the header and links the Scorecard results

### Requirement: Roadmap lists the planned changes

`ROADMAP.md` SHALL list each confirmed proposal that is not yet on main, SHALL link each proposal, and SHALL state that dates are
not promised and that planning detail lives outside the repository.

#### Scenario: Reader asks what comes next

- **WHEN** a reader opens `ROADMAP.md`
- **THEN** every entry links a proposal under `openspec/changes/` that exists on the branch

### Requirement: Code owners are named

`.github/CODEOWNERS` SHALL assign the repository default owner to the `ingot-maintainers` team of the organisation.

#### Scenario: Pull request is opened

- **WHEN** a pull request changes any file in the repository
- **THEN** the team `ingot-maintainers` is the code owner who is asked to review it

### Requirement: Health report exemptions carry reasons

`.clomonitor.yml` SHALL exempt only checks that do not apply yet, and each exemption SHALL carry a reason that a reader can
understand.

#### Scenario: Reader opens the health report

- **WHEN** a reader opens the CLOMonitor report of the repository
- **THEN** each exempt check shows the reason from the file, and no check is exempt without one

### Requirement: Health files change no runtime behaviour

The change SHALL NOT alter code, configuration keys, environment variables, the container image or the Helm chart.

#### Scenario: Drop-in check

- **WHEN** the change is reviewed against the drop-in contract
- **THEN** the changed paths are limited to `.github/`, `ROADMAP.md` and `.clomonitor.yml`, and an existing installation needs no change
