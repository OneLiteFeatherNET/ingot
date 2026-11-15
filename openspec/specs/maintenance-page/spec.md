# maintenance-page Specification

## Purpose
The Maintenance page of the dashboard: how the storage usage panel shows its figures while the server computes them, and the
subtitle the page gives itself.

## Requirements

### Requirement: Storage figures while computing

While the server computes the storage figures, the storage usage panel SHALL show the progress in place of the figures. It SHALL NOT
show a total, a repository count or the text that no repository has been measured. The panel title and the Refresh control stay in
place, and a computed date stays visible when the figures were computed before.

#### Scenario: Computation in progress

- **WHEN** the storage response reports that the figures are computing for an instance with seven repositories
- **THEN** the panel shows the progress and no "Total" line and no "No repository has been measured yet" text

#### Scenario: Figures arrived

- **WHEN** the storage response carries the computed figures
- **THEN** the panel shows the total and the number of repositories

### Requirement: Maintenance page subtitle

The subtitle of the Maintenance page SHALL describe the page and SHALL NOT use the term "managers". The visitor's permission is named by the sign-in text of the page, not by the subtitle.

#### Scenario: Subtitle text

- **WHEN** a full administrator opens `/maintenance`
- **THEN** the subtitle reads "Storage usage, the search index and cleanup for this Ingot instance." and contains no "manager"
