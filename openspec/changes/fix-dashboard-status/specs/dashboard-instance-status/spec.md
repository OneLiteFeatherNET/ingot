# Spec Delta

## Purpose

The instance status is the first thing an administrator reads on the dashboard. Every number on it says what it measures, uses
the unit it names, and comes from one source. A reader can compare the figures without a second look at the documentation, and a
cluster is summarised once, with the instances below it.

## ADDED Requirements

### Requirement: Each figure names its measure and its limit

Every card of the instance status SHALL show a value whose detail names what the value measures and, where the card has a limit,
the limit it is compared with. A card SHALL NOT show a limit that is not the limit of the value it sits next to. The cards and their
sources are listed in the design.

#### Scenario: Heap is named as the maximum heap

- **WHEN** the instance status shows memory in use
- **THEN** the detail reads "of N MB maximum heap" and the meter, if any, divides the heap in use by that maximum

#### Scenario: Threads are not compared with a pool

- **WHEN** the instance status shows live threads
- **THEN** the value is labelled as live threads, the detail names the JVM, and no progress bar and no "of N" limit is shown

### Requirement: Progress bars are readable by assistive technology

A meter on the instance status SHALL have `aria-valuenow` within 0 to 100, `aria-valuemin` 0, `aria-valuemax` 100, and an `aria-valuetext` that gives the value and the limit in words. A value above the limit SHALL be shown as the full bar and SHALL NOT produce an `aria-valuenow` above 100.

#### Scenario: Meter at the limit

- **WHEN** the heap in use equals the maximum heap
- **THEN** the meter reports `aria-valuenow="100"` and its `aria-valuetext` gives both values in megabytes

#### Scenario: Value above the limit

- **WHEN** a meter receives a ratio above 1
- **THEN** the meter reports `aria-valuenow="100"` and the full bar, and no value above 100 is announced

### Requirement: Instance rows state each unit

Each instance row of a cluster SHALL give each figure with its unit, for example "91.6 MB of 256 MB maximum heap" and "54 live threads". A row SHALL NOT repeat the cluster summary in one sentence. Its fields SHALL be labelled, so that a screen reader reads each label with its value.

#### Scenario: Row with a heap figure

- **WHEN** a cluster instance has 91.6 MB in use of a 256 MB maximum heap
- **THEN** its row shows "91.6 MB of 256 MB maximum heap", with the unit on both numbers

#### Scenario: Row fields are labelled

- **WHEN** an instance row is read by a screen reader
- **THEN** each field is announced with its label, such as "Heap" or "Threads"

### Requirement: Failures are counted and described

The failures card SHALL show the number of distinct failure records of the instance since its last restart. Its subline SHALL say what the number covers, and SHALL NOT repeat the number. With no failure the subline SHALL read "None since the last restart". With failures it SHALL read "N distinct since the last restart", and the card SHALL open the list. The cluster failures card SHALL say that the records are counted per instance.

#### Scenario: No failure

- **WHEN** no failure has been recorded since the last restart
- **THEN** the card shows 0 and the subline "None since the last restart", and it does not open a list

#### Scenario: Failures on a cluster

- **WHEN** two instances each hold the same failure
- **THEN** the cluster failures card counts it twice and its subline says it is counted per instance

### Requirement: Version does not claim a build is current

The version card SHALL compare the running version with the newest release it knows. When they are equal it SHALL say "Same as latest release". When a newer release is known it SHALL name that release. When no release is known it SHALL show no comparison. The card SHALL NOT say "Up to date".

#### Scenario: Nightly build on the latest release

- **WHEN** the running version equals the newest release
- **THEN** the card says "Same as latest release" and the value is the running version

#### Scenario: Newer release known

- **WHEN** the newest release is newer than the running version
- **THEN** the card names that release as available

#### Scenario: No release known

- **WHEN** the newest release is reported as unknown
- **THEN** the card shows the running version with no comparison

### Requirement: One health statement per view

The instance status SHALL state the health of the cluster once. With a cluster, the cluster summary's health card SHALL be that statement, worst instance first, with the number of healthy instances as its detail, and the health row SHALL NOT repeat the label. Without a cluster, the health row SHALL be the statement.

#### Scenario: Cluster with two healthy instances

- **WHEN** a cluster of two instances reports both as up
- **THEN** the health card reads "Healthy" with "2 of 2 instances up", and no other line on the page says "Healthy"

#### Scenario: One instance down

- **WHEN** one instance of two reports down
- **THEN** the health card reads "Unhealthy" with "1 of 2 instances up"

#### Scenario: No cluster

- **WHEN** no cluster bus is configured
- **THEN** the health row shows its label once and no cluster health card is shown

### Requirement: The cluster is summarised once

With a cluster, the instance status SHALL show the cluster summary first, then one row per instance. The per-instance cards SHALL NOT be shown above the summary, so that no figure appears twice on the page. Without a cluster, the per-instance cards SHALL be shown as the single-instance cards, with the wording of the requirements above.

#### Scenario: Cluster present

- **WHEN** the status carries a cluster with two instances
- **THEN** the page shows the cluster summary, then two instance rows, and no per-instance cards above the summary

#### Scenario: No cluster

- **WHEN** the status carries no cluster
- **THEN** the page shows the single-instance cards and no cluster section

### Requirement: Charts identify each instance

The memory and threads charts SHALL show a legend whenever they draw more than one series, and each series SHALL carry the name of its instance. Each chart's subtitle SHALL name its unit and its measure: heap in use in megabytes, and live threads.

#### Scenario: Two instances in the charts

- **WHEN** the cluster has two instances with samples
- **THEN** each chart draws two series, shows a legend with the two instance names, and the tooltip names the instance

#### Scenario: One series

- **WHEN** the chart has one series
- **THEN** no legend is shown
