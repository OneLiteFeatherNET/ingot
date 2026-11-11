# Spec Delta

## ADDED Requirements

### Requirement: Storage figures while computing

The storage usage panel SHALL show only its progress while the server computes the figures. It SHALL NOT show a total, a
repository count or the text that no repository has been measured until the figures have arrived.

#### Scenario: Computation in progress

- **WHEN** the storage response reports that the figures are computing for an instance with seven repositories
- **THEN** the panel shows the progress and no "Total" line and no "No repository has been measured yet" text

#### Scenario: Figures arrived

- **WHEN** the storage response carries the computed figures
- **THEN** the panel shows the total and the number of repositories, as before

### Requirement: Maintenance page subtitle

The subtitle of the Maintenance page SHALL describe the page and SHALL NOT use the term "managers". The visitor's
permission is named by the frame and the sign-in text, not by the subtitle.

#### Scenario: Subtitle text

- **WHEN** a full administrator opens `/maintenance`
- **THEN** the subtitle reads "Storage usage, the search index and cleanup for this Ingot instance." and contains no "manager"
