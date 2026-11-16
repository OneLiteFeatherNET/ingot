# Spec Delta

## ADDED Requirements

### Requirement: Tabs of the system information page

The page `/settings/system-info` SHALL show its content in four tabs, in this order: Overview, Plugins, Environment and Configuration.
Every value of the system information SHALL be on exactly one tab.

#### Scenario: Four tabs in order

- **WHEN** a visitor opens the system information page
- **THEN** the tab list has the tabs Overview, Plugins, Environment and Configuration, in that order, and Overview is selected

### Requirement: Overview cards

The Overview SHALL show one card for each of Ingot, Java, Operating system, Database, Storage and Cluster, each labelled by its heading.
The cards SHALL be in a grid of three columns from 1240 pixels, and in one column at phone width.

#### Scenario: Overview cards

- **WHEN** the Overview is shown at 1440 pixels wide
- **THEN** the six cards are drawn in a grid of three columns, and each card is labelled by its heading

#### Scenario: Phone width

- **WHEN** the Overview is shown at 320 pixels wide
- **THEN** the cards are drawn in one column and the page does not scroll sideways

#### Scenario: Long values stay reachable

- **WHEN** a visitor opens the Java card
- **THEN** the input arguments are hidden behind a disclosure labelled "Input arguments", and opening it shows every argument

### Requirement: Active tab in the address

The selected tab SHALL be the query parameter `tab` of the page, with the values `overview`, `plugins`, `environment` and `configuration`.
A missing or unknown value SHALL select Overview. Opening a tab SHALL replace the history entry rather than add one.

#### Scenario: Deep link keeps the tab

- **WHEN** a visitor opens `/settings/system-info?tab=environment`
- **THEN** the Environment tab is selected and its panel is shown

#### Scenario: Reload keeps the tab

- **WHEN** a visitor selects Configuration and reloads the page
- **THEN** the Configuration tab is selected again

#### Scenario: Unknown value

- **WHEN** a visitor opens the page with `tab=unknown`
- **THEN** the Overview tab is selected

### Requirement: Section anchors open their tab

The section anchors of the page SHALL keep working. A fragment `#support-ingot`, `#support-java`, `#support-os`, `#support-database`,
`#support-storage` or `#support-cluster` SHALL select Overview; `#support-plugins` SHALL select Plugins; `#support-environment` and
`#support-properties` SHALL select Environment; `#support-configuration` SHALL select Configuration. The page SHALL then scroll the element
with that id into view. Any other fragment SHALL be left to the browser.

#### Scenario: Anchor into a tab

- **WHEN** a visitor opens `/settings/system-info#support-properties`
- **THEN** the Environment tab is selected and the system properties are scrolled into view

#### Scenario: Anchor wins over the tab

- **WHEN** a visitor opens `/settings/system-info?tab=plugins#support-cluster`
- **THEN** the Overview tab is selected and the Cluster card is scrolled into view

### Requirement: Filter of the long tabs

The Environment and Configuration tabs SHALL have a filter field labelled "Filter by key or value". The filter SHALL narrow the rows of
the environment variables, the system properties and the local configuration to the rows whose key or value contains the typed text, without
regard to case. The filter SHALL NOT change any value that the page shows, and it SHALL NOT request any value from the server.

#### Scenario: Filter by key

- **WHEN** a visitor types "java" into the filter on Environment
- **THEN** only the rows whose key or value contains "java" are shown, in both lists

#### Scenario: No match

- **WHEN** the typed text matches no row of a list
- **THEN** that list says "No entry matches the filter."

#### Scenario: Masked value

- **WHEN** a row holds a value that the server masked
- **THEN** the filter matches the masked text the page shows, and the page shows no other value for that row

#### Scenario: Shared configuration

- **WHEN** a visitor opens Configuration with a filter typed
- **THEN** the local rows are narrowed, and the shared configuration is shown whole with a note that the filter does not apply to it

### Requirement: One navigation entry and one page for support

The navigation SHALL list one entry for this page, labelled "System and support", in the group Monitoring and support. The address
`/settings/support` SHALL redirect to `/settings/system-info`, and the route name `support` SHALL remain resolvable. No address that was
served before this change SHALL answer 404. The query and the fragment of the old address SHALL be kept by the redirect.

#### Scenario: Old address redirects

- **WHEN** a visitor opens `/settings/support`
- **THEN** the visitor is shown `/settings/system-info`

#### Scenario: Old address with a fragment

- **WHEN** a visitor opens `/settings/support#support-configuration`
- **THEN** the page is shown with the Configuration tab selected

#### Scenario: One entry

- **WHEN** a principal with the console area opens the navigation of the Administration
- **THEN** the Monitoring and support group lists one entry for the system information and the support bundle, and no entry for `support`

### Requirement: Download action on every tab

The page header SHALL show the action "Download support bundle" on every tab, while the support pages are on. The header SHALL show,
in a disclosure labelled "What is removed", the note on what the bundle removes or masks.

#### Scenario: Action on every tab

- **WHEN** a visitor opens the Configuration tab and the support pages are on
- **THEN** the "Download support bundle" action is shown in the header

### Requirement: Support states replace the content

While the availability check answers that the support pages are off, or the check fails, the page SHALL show the state text of its
support page in place of the tabs and the download action.

#### Scenario: Pages turned off

- **WHEN** the availability check answers that the support pages are off
- **THEN** the page shows "The support pages are turned off on this instance." and no tab and no download action

#### Scenario: Check cannot be made

- **WHEN** the availability check fails
- **THEN** the page shows "The support bundle cannot be checked right now." and no download action

### Requirement: Keyboard operation of the tabs

The tabs SHALL be the general tabs component of this change (`src/components/tabs/`), which follows the WAI-ARIA tabs pattern: each tab
is a `tab` with `aria-selected` and `aria-controls`, each panel is a `tabpanel` labelled by its tab, and only the selected tab is in the tab order. Right and Left SHALL move to
the next and the previous tab, Home and End to the first and the last, and the arrow keys SHALL select the tab they move to.

#### Scenario: Arrow keys select

- **WHEN** the focus is on Overview and the visitor presses Right
- **THEN** Plugins takes the focus and is selected

#### Scenario: End

- **WHEN** the focus is on Overview and the visitor presses End
- **THEN** Configuration takes the focus and is selected

### Requirement: Stable anchors of the merged page

The root of the page SHALL be `data-ingot-component="system-info-view"` and SHALL carry `data-ingot-component-alias="support-view"`. The
registry SHALL declare the alias `support-view` with `replacedBy` set to `system-info-view`. The parts `plugin-tier`, `support-action`,
`support-note` and `filter` SHALL be registered for the component, and the states `loading`, `error` and `empty` SHALL be the states the
component uses.

#### Scenario: Plugin selector on the old name

- **WHEN** a plugin selects `[data-ingot-component="support-view"]` on the page
- **THEN** the selector matches the merged page, through the alias

#### Scenario: Registry check

- **WHEN** the anchor checks run on the frontend
- **THEN** every alias on the root is declared, every declared alias is on the root, and no declared alias has reached its removal version

### Requirement: No change of data or access

The page SHALL show the same data as before this change, from the same requests. It SHALL keep the masking of the server and the access rule
of the console area. The change SHALL NOT add, remove or change any endpoint, permission node, configuration key or stored data.

#### Scenario: Same requests

- **WHEN** the page is opened
- **THEN** it requests the system information and the availability of the support pages, as the two pages did before

#### Scenario: Access

- **WHEN** a principal without the console area opens `/settings/system-info`
- **THEN** the page is refused as it was refused before this change
