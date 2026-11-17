## ADDED Requirements

### Requirement: Latest run per kind with a link to all runs

The Search index tab SHALL show the latest run of each index kind and the live run. The Cleanup policies tab SHALL show the latest run
of each policy. Neither tab SHALL draw the full list of its runs, and each SHALL link to the Jobs page with "All runs", pre-filtered
by its source. The address `/maintenance?tab=<key>` SHALL keep opening its tab.

#### Scenario: Search index tab with a long history

- **WHEN** a full administrator opens the Search index tab on an instance with 300 index runs
- **THEN** the tab shows one card for the latest rebuild and one for the latest verify, and no list of runs

#### Scenario: Link to all index runs

- **WHEN** the administrator activates "All runs" on the Search index tab
- **THEN** the Jobs page opens at `/settings/jobs?kind=index` and lists the index runs only

#### Scenario: Cleanup tab shows one run per policy

- **WHEN** the Cleanup policies tab shows two policies with 20 runs each
- **THEN** the tab shows the latest run of each policy, two rows in all, and "All runs" links to `/settings/jobs?kind=cleanup`

#### Scenario: Address of the tab unchanged

- **WHEN** a visitor opens `/maintenance?tab=index`
- **THEN** the Search index tab opens as before
