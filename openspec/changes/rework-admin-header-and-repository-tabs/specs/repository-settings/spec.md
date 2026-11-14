# Spec Delta

## ADDED Requirements

### Requirement: Repository settings in tabs

The page of one repository, at `/settings/repositories/<name>`, SHALL show its fields in six tabs under the repository type
section: General, Storage, Access, Mirror and proxy, Policy and Advanced, in that order. Every field of the repository
entry SHALL be in exactly one tab. The type section and the delete action SHALL NOT be a tab; the delete action SHALL be
drawn below the tab panel on every tab.

#### Scenario: Six tabs in order

- **WHEN** a visitor opens a repository page
- **THEN** the tab list has the tabs General, Storage, Access, Mirror and proxy, Policy and Advanced, in that order, and General
  is selected

#### Scenario: Every field has one tab

- **WHEN** the fields of the repository entry are compared with the tabs
- **THEN** each field is in exactly one tab, and no tab names a field that the entry does not have

#### Scenario: Delete on every tab

- **WHEN** a visitor opens the Policy tab
- **THEN** the delete action is still drawn below the tab panel

### Requirement: Tab address

The selected tab SHALL be the query parameter `tab` of the repository page, with the keys General, Storage, Access, Mirror,
Policy and Advanced in lower case as listed in the design. A missing or unknown key SHALL open General. A change of tab SHALL
replace the history entry, so that the back button does not step through the tabs. A rename of the repository SHALL keep the
selected tab.

#### Scenario: Deep link

- **WHEN** a visitor opens `/settings/repositories/<name>?tab=storage`
- **THEN** the Storage tab is selected and its panel is shown

#### Scenario: Unknown key

- **WHEN** a visitor opens the page with `tab=unknown`
- **THEN** the General tab is selected

#### Scenario: Rename keeps the tab

- **WHEN** a visitor renames the repository on the Storage tab
- **THEN** the address has the new name and still has `tab=storage`

### Requirement: Keyboard operation of the tabs

The tab list SHALL follow the WAI-ARIA tabs pattern. Each tab SHALL be a tab control with `aria-selected` and the panel it
controls, and the selected tab SHALL be the only one in the tab order. Tab SHALL leave the list and enter the panel, which
SHALL be labelled by its tab.

#### Scenario: Arrow keys

- **WHEN** the focus is on the General tab and the visitor presses Right
- **THEN** the Storage tab takes the focus and is selected

#### Scenario: Home and End

- **WHEN** the focus is on the Policy tab and the visitor presses End
- **THEN** the Advanced tab takes the focus and is selected

#### Scenario: Narrow width

- **WHEN** the page is drawn at 320 pixels wide
- **THEN** every tab can be reached with the arrow keys, and the page does not scroll sideways

### Requirement: Tabs are accessible at every width

The page SHALL have no axe violation on any tab at 320, 600 and 1440 pixels wide.

#### Scenario: Axe on each tab

- **WHEN** an accessibility check runs on each tab at 320, 600 and 1440 pixels
- **THEN** no violation is reported

### Requirement: One draft and one save across tabs

The changes of a repository SHALL stay one draft of the settings, as before. Switching tabs SHALL keep every value of the entry,
including a value of a field that has focus, and SHALL NOT save anything.

#### Scenario: Value kept across tabs

- **WHEN** a visitor types a description on General, opens Storage and returns to General
- **THEN** the description is the typed value, and the save bar counts the change once

#### Scenario: Hidden value is saved

- **WHEN** a visitor changes a value on Storage and saves from the Policy tab
- **THEN** the saved entry has the changed value

### Requirement: Problems on other tabs are visible

The page SHALL have one save action, the save bar of the settings page. A problem in a field of a tab that is not open SHALL be
counted on that tab, and the save bar SHALL name it and link to the first tab with a problem.

#### Scenario: Problem on another tab

- **WHEN** a field on the Storage tab has a problem and the visitor is on the General tab
- **THEN** the Storage tab shows the number of problems, and the save bar links to the Storage tab
