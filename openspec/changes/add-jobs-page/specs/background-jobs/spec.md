## ADDED Requirements

### Requirement: One paged list of background runs

The Jobs page SHALL list the runs of every visible source in one list, newest first by start time, and SHALL load it in pages. The
server SHALL answer a page of at most 100 runs, 50 by default, with an opaque cursor to the next page. A page SHALL NOT carry a
total count. The list SHALL NOT load every run at once and SHALL NOT scroll endlessly.

#### Scenario: First page

- **WHEN** a visitor opens the Jobs page on an instance with 120 index runs and 30 cleanup runs
- **THEN** the page shows the 50 newest runs of both sources in start order, and a "Load more" control

#### Scenario: Next page without repeats

- **WHEN** the visitor loads the next page with the cursor of the last run shown, and a new run starts in between
- **THEN** the next page holds the next older runs, no run is shown twice, and the new run is not part of that page

#### Scenario: Size limit

- **WHEN** a request asks for a page size of 500
- **THEN** the server answers at most 100 runs

### Requirement: One run shape for every source

Every run SHALL carry the fields `id`, `source`, `kind`, `label`, `trigger`, `state`, `startedAt`, `finishedAt`, `durationMs`,
`counts`, `failures` and `cancellable`. The `id` SHALL be `<source>:<local id>` and unique across sources.

#### Scenario: Run of two sources in one list

- **WHEN** the index and the cleanup sources both hold runs
- **THEN** each run carries its own `source` and an `id` that differs from every run of the other source

### Requirement: Normalised states and triggers

A source SHALL map its own statuses to the states `QUEUED`, `RUNNING`, `PAUSED`, `SUCCEEDED`, `PARTIAL`, `FAILED` and `CANCELLED`, and
its own triggers to `MANUAL`, `SCHEDULED` or `SYSTEM`. The index status `COMPLETED` SHALL map to `SUCCEEDED`, and the index triggers
`STARTUP`, `GAP` and `BACKEND` SHALL map to `SYSTEM`.

#### Scenario: Completed index verify

- **WHEN** a verify run of the search index finishes with status `COMPLETED` after fixing rows
- **THEN** the run is listed with the state `SUCCEEDED`, and the counts show the rows fixed

#### Scenario: Startup rebuild

- **WHEN** the index rebuilds because the instance started for the first time
- **THEN** the run is listed with the trigger `SYSTEM`, and its detail names the raw trigger `STARTUP`

#### Scenario: Cleanup run with failures

- **WHEN** a cleanup run finishes with status `PARTIAL`
- **THEN** the run is listed with the state `PARTIAL`, and its failures are listed in the detail

### Requirement: Filters by kind, state and trigger

The Jobs page SHALL filter the list by kind, state and trigger. The filters SHALL be held in the address, so that a filtered list can be
linked and reloaded. The kind filter SHALL accept a source id, which selects every kind of that source, or a kind. The server SHALL
apply the filters before it pages, so that a filtered page is full while matching runs exist.

#### Scenario: Index runs from the maintenance link

- **WHEN** a visitor opens `/settings/jobs?kind=index`
- **THEN** only the runs of the index source are listed, of both kinds `index.rebuild` and `index.verify`

#### Scenario: Failed runs only

- **WHEN** a visitor filters by the state `FAILED`
- **THEN** only the runs in the state `FAILED` are listed, and a page is full while such runs remain

#### Scenario: Unknown kind

- **WHEN** a request filters by a kind that no visible source has
- **THEN** the server answers an empty list and the kinds the visitor may read, and no error

### Requirement: Run detail and cancel

The Jobs page SHALL show a run detail for each run, with its counts and its failures. A failure list SHALL show at most 50 failures,
with a note when more exist. A run whose source supports cancelling SHALL show a Cancel action while the run is live, and a cancel of
a live run SHALL answer `204`. A cancel of a finished run SHALL answer `409`, and a run that does not exist or that the visitor may not read
SHALL answer `404`.

#### Scenario: Cancel a running verify

- **WHEN** a visitor cancels a running verify from its detail
- **THEN** the run stops after the batch in flight, and the detail shows the state `CANCELLED`

#### Scenario: Cleanup run has no cancel

- **WHEN** a visitor opens the detail of a cleanup run
- **THEN** the detail shows no Cancel action, because the run is synchronous and its `cancellable` is false

#### Scenario: Cancel of a finished run

- **WHEN** a visitor cancels a run that has finished
- **THEN** the server answers `409` and the page says the run has already finished

### Requirement: Visibility follows the node of each source

The Jobs endpoints SHALL show a run only to a visitor who may read its source. The search index and the cleanup policies SHALL
require the maintenance area `ingot.admin.maintenance`, and the migration source SHALL require `ingot.admin.*`. A plugin source
SHALL declare the node it requires, and the default SHALL be `ingot.admin.*`.

#### Scenario: Maintenance area only

- **WHEN** a visitor holds `ingot.admin.maintenance` and not `ingot.admin.*`
- **THEN** the list holds the index and cleanup runs, no migration run, and the kind filter offers no migration kind

### Requirement: Unreadable sources stay hidden

A visitor who may read none of the sources SHALL receive `403`, with the message that names the area. A run of a source the visitor
may not read SHALL answer `404`, as a run that does not exist, and the kind filter SHALL offer only the kinds the visitor may read.

#### Scenario: No source readable

- **WHEN** a visitor holds neither node
- **THEN** the Jobs endpoints answer `403` with the message that names the area

#### Scenario: Run of an unreadable source

- **WHEN** a visitor who may not read the migration source asks for a migration run by its id
- **THEN** the server answers `404`

### Requirement: Plugins register job sources

A plugin SHALL register a job source through the registration event of the plugin API, during its initialisation. A registered source
SHALL appear on the Jobs page with its runs. A plugin that is disabled SHALL have its sources removed, with no error on the page.

#### Scenario: Plugin source listed

- **WHEN** a plugin registers a source with the id `javadoc` and two runs
- **THEN** the Jobs page lists those two runs with the source `javadoc`, and the kind filter offers its kinds

#### Scenario: Disabled plugin

- **WHEN** the plugin of a registered source is disabled
- **THEN** its runs are not listed and the Jobs page shows no error for it

### Requirement: Job source contract

A source SHALL return its runs sorted by start time, newest first, and SHALL honour the state and trigger filters before it pages. A source
SHALL NOT return a run twice across pages. The plugin API SHALL change only by addition: no existing type, method or event SHALL change its
signature.

#### Scenario: Failing source

- **WHEN** one source throws while its page is read
- **THEN** the other sources' runs are listed, and the response names the failed source in `sourceErrors`

#### Scenario: Additive plugin API

- **WHEN** the plugin ABI gate compares the plugin API with the 3.5.28 release
- **THEN** it reports the new types as additions and no existing signature as changed

### Requirement: The Jobs page never deletes a run

The Jobs page and its endpoints SHALL NOT delete a run. Each source SHALL keep its own history and its own retention. The cleanup source
SHALL keep the newest 20 runs of each policy, as it does today, and the migration source SHALL keep a job until an operator deletes it.

#### Scenario: Page read does not prune

- **WHEN** a visitor reads the Jobs page, with and without filters, and pages through the whole list
- **THEN** no run row is written or deleted by the read, and each source's history is the same before and after

### Requirement: Existing endpoints keep their shape

The introduction of the Jobs endpoints SHALL NOT change the path, the method, the response shape or the bound of any existing endpoint that
lists runs. `GET /api/maven/search/index` SHALL keep its `runs` array, `GET /api/cleanup/runs` SHALL keep its list, and the nexus
import job routes SHALL keep their behaviour.

#### Scenario: Search index status unchanged

- **WHEN** a client reads `GET /api/maven/search/index` after the change
- **THEN** the response holds the same `runs` array of the newest 20 runs as before the change
