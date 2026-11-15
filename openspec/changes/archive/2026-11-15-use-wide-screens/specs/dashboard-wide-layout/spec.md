# Spec Delta

## ADDED Requirements

### Requirement: Wide pages use the full width

Lists, tables and the file browser listing SHALL use the full width of the content column at every width from 1240 pixels, with no
maximum width. Their columns SHALL be shown as the column priority of the layout allows for that width.

#### Scenario: Repository table on a 27 inch window

- **WHEN** a visitor opens the repositories page at 2560 pixels wide
- **THEN** the table uses the full content width, and its Address and Storage columns are shown

#### Scenario: Columns show earlier on wide screens

- **WHEN** a visitor opens the repositories page at 1920 pixels wide
- **THEN** the Version policy and Visibility columns are shown, and the Storage column is not

### Requirement: Reading pages keep a measure

Settings forms, the Maintenance page text, the account page, the migration steps and the getting started text SHALL NOT be wider than
960 pixels, at any width. They SHALL start at the beginning of the content column.

#### Scenario: Settings form on a wide window

- **WHEN** a visitor opens a settings domain at 2560 pixels wide
- **THEN** the form is at most 960 pixels wide and does not grow with the window

### Requirement: Dashboard grid by width

The dashboard SHALL lay out its status tiles and charts in one column below 1920 pixels, in two columns from 1920 pixels, and in three
columns from 2560 pixels. The instance status panel SHALL span the full row at every width. The order of the content SHALL NOT change
with the number of columns.

#### Scenario: Dashboard at 1920 pixels

- **WHEN** a visitor opens the dashboard at 1920 pixels wide
- **THEN** two tiles sit side by side in the first row

#### Scenario: Dashboard at 2560 pixels

- **WHEN** a visitor opens the dashboard at 2560 pixels wide
- **THEN** three tiles sit side by side in the first row

### Requirement: Repository overview beside its snippet

From 1920 pixels the repository overview SHALL show the browse list and the snippet card side by side, with the list in the wider
column. Below 1920 pixels the snippet card SHALL sit below the list.

#### Scenario: Overview at 1920 pixels

- **WHEN** a visitor opens a repository overview at 1920 pixels wide
- **THEN** the snippet card is beside the browse list

#### Scenario: Overview below 1920 pixels

- **WHEN** a visitor opens a repository overview at 1280 pixels wide
- **THEN** the snippet card is below the browse list
