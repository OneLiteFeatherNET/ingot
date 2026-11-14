# Spec Delta

## Purpose

Every dashboard view builds its content from shared components: one card, one section heading per level, one row geometry,
one table, one code sample, one field variant and one set of window classes. Views use Tailwind utilities that resolve to the
Material Design 3 tokens, and nothing else.

## ADDED Requirements

### Requirement: Cards are one shared component

A card of the dashboard SHALL be drawn by `MdCard`. The card SHALL have one corner radius (`rounded-corner-medium`) and one
padding (`p-4`) at every window size. A view SHALL NOT draw a card with its own corner radius or padding classes. A card that
holds a table or a full-bleed list SHALL use `padding="none"` on `MdCard`, which removes the padding and clips the content to
the radius.

#### Scenario: A hand-built card is replaced

- **WHEN** a view that drew a card with `rounded-corner-medium` or `rounded-corner-large` by hand is migrated
- **THEN** the card is an `MdCard`, its radius and padding come from the component, and the view carries no corner radius class

#### Scenario: Full-bleed content

- **WHEN** a card holds a table or a list that fills its width
- **THEN** the card is an `MdCard` with `padding="none"`, the content touches the card edge, and the corners of the content follow the card radius

#### Scenario: One radius in every view

- **WHEN** the dashboard is rendered at 390 pixels and at 1920 pixels in light and dark
- **THEN** every `MdCard` has the same corner radius at each width and in each theme

### Requirement: Section headings follow one scale per level

A section of the dashboard SHALL be drawn by `MdSection`, which renders a heading and its content. A level 2 heading SHALL use
`md-title-medium` and a level 3 heading SHALL use `md-title-small`. A view SHALL NOT mix the other title sizes for section headings.
A heading SHALL NOT be nested under a heading of a lower level than its parent, and a level 3 heading SHALL have a level 2 parent
section on the page. The heading SHALL align with the content of its section.

#### Scenario: Headings of one level share a size

- **WHEN** a full administrator opens the System information page and the Repositories overview
- **THEN** every section heading of level 2 uses the same type size, and every level 3 heading uses the same smaller type size

#### Scenario: No heading without a parent

- **WHEN** a repository access card is shown
- **THEN** its heading is a level 3 heading inside a level 2 section, and the page has no level 4 heading

#### Scenario: Heading aligned with content

- **WHEN** a section heading and its content are shown at 390 pixels
- **THEN** the heading starts at the same horizontal position as the content

### Requirement: Rows share one list geometry

A row of a list SHALL be drawn by `MdListItem` inside an `MdList`. A two-line row SHALL have a minimum height of 56 pixels, a
one-line row a minimum height of 48 pixels, and a horizontal inset of 16 pixels. The list SHALL draw the divider between rows, and
no row or list SHALL use an arbitrary child selector to draw dividers.

#### Scenario: Selected row

- **WHEN** a full administrator selects a team in the Teams list
- **THEN** the selected row is drawn by `MdListItem` with the row geometry of every other row of that list

#### Scenario: Dividers

- **WHEN** a list of rows is rendered with more than one row
- **THEN** each row except the last has one divider, drawn by the list, and no row draws its own divider

#### Scenario: Row geometry

- **WHEN** a two-line row and a one-line row are rendered at 390 pixels
- **THEN** the two-line row is at least 56 pixels high, the one-line row is at least 48 pixels high, and both have a 16 pixel inset

### Requirement: Selected row is marked once per list

A row that is the selected row of its list SHALL carry `aria-current="true"`, and the list SHALL compute its selected key once for all
of its rows. No other row SHALL carry the attribute.

#### Scenario: One selected row

- **WHEN** a full administrator selects a team in the Teams list
- **THEN** exactly one row carries `aria-current="true"`

#### Scenario: Selection computed once

- **WHEN** the Principals list renders its rows
- **THEN** the selected key is computed once for the list, not once per row

### Requirement: Link rows use the navigation item

A row that links to another page SHALL be drawn by `MdNavigationItem`, so its hover, focus and current states are the same in every
list of the dashboard.

#### Scenario: Current link row

- **WHEN** the page of a link row is open
- **THEN** the link row is drawn by `MdNavigationItem` and carries `aria-current="page"`

### Requirement: Key and value pairs and status labels use one component each

A list of label and value pairs SHALL be drawn by `MdKeyValueList`, and a status label SHALL be drawn by `MdBadge`. A view SHALL NOT
draw a definition list or a status label with its own classes. A status label SHALL take its tone from a status value of a closed
set, not from a colour prop.

#### Scenario: Instance status

- **WHEN** the Instance status panel is rendered
- **THEN** its pairs are drawn by `MdKeyValueList` and its status labels by `MdBadge`, and the status label takes its tone from the
  status value

#### Scenario: Unknown status

- **WHEN** a status label is given a value outside the closed set
- **THEN** the label is drawn in the neutral tone and no colour is passed by the caller

### Requirement: Sortable headers announce their sort state

A table with sortable columns SHALL be drawn by `MdDataTable`. A sortable header SHALL contain a button, and the header cell SHALL carry
`aria-sort` set to `ascending` or `descending` when it is the sorted column and `none` otherwise. Activating the button SHALL sort the
rows by that column, and activating it again SHALL reverse the order.

#### Scenario: Sorting

- **WHEN** a full administrator activates the Name header of the repositories table twice
- **THEN** the rows are sorted ascending and then descending by name, and the Name header carries `aria-sort` with the current
  direction while the other headers carry `aria-sort="none"`

### Requirement: Table columns follow the window classes

A column that is hidden at a window size SHALL be hidden by its window class, not by a width literal. A table SHALL scroll horizontally
inside its container when its columns do not fit, and SHALL NOT set a minimum width of its own.

#### Scenario: Column visibility

- **WHEN** the repositories table is rendered at 840 pixels and at 1920 pixels
- **THEN** the columns with a window class priority are shown only from that window class on

#### Scenario: Narrow table

- **WHEN** the table's columns need more width than the container at 390 pixels
- **THEN** the container scrolls horizontally, and the page does not scroll horizontally

### Requirement: Code samples use the code components and no style block

Inline code SHALL be drawn by `MdCode`, a block of code by `MdCodeBlock`, and a block with a copy action by `MdCopyableCode`. A
code sample SHALL NOT be drawn with a `pre` element that carries its own classes outside those components. A view SHALL NOT contain
a `<style>` block. The app-wide scrollbar rule SHALL NOT exist; a code block that wants a thin scrollbar SHALL use the `md-scrollbar`
utility.

#### Scenario: Snippets card

- **WHEN** the snippets card of a repository is rendered
- **THEN** each snippet is an `MdCopyableCode` inside an `MdCodeBlock`, the card has no `<style>` block, and the title is at the
  card's own inset

#### Scenario: Anchors kept

- **WHEN** a plugin stylesheet selects `[data-ingot-component="repository-snippet"]` or `[data-ingot-component="artifact-snippet"]`
- **THEN** each selector matches the root of its snippet

#### Scenario: Scrollbars

- **WHEN** a page without a code block is rendered at 390 pixels
- **THEN** its scrollbars are the browser default, because no app-wide scrollbar rule is drawn

### Requirement: Native controls are replaced by primitives

A radio button, checkbox, select, switch, progress indicator, button and icon button in the dashboard SHALL be drawn by `MdRadio`,
`MdCheckbox`, `MdSelect`, `MdSwitch`, `MdLinearProgress`, `MdButton` and `MdIconButton`. A link that looks like a button SHALL be an
`MdButton` with `href`. A view SHALL NOT write a native `progress` element or a radio or checkbox input of its own.

#### Scenario: Native control replaced

- **WHEN** the migration steps of the migration wizard are rendered
- **THEN** their radio buttons, checkboxes and progress indicators are the primitives, and no `input type="radio"` or `progress`
  element is written in the view

### Requirement: One field variant for every form

Every text field of the dashboard SHALL use the same variant, which is `outlined` unless a field states otherwise in its own props.

#### Scenario: One field variant

- **WHEN** the Audit settings, the password card and the token dialog are rendered
- **THEN** their text fields are all `outlined`

### Requirement: Visible control size and touch target are separate

A visible control SHALL be 40 pixels high, and a control whose touch target needs more SHALL use `md-touch-target` instead of a larger
visible size.

#### Scenario: Visible size and touch target

- **WHEN** a control that is 40 pixels high is rendered at 390 pixels
- **THEN** its touch target is at least 48 by 48 pixels through `md-touch-target`, and its visible height stays 40 pixels

#### Scenario: Save bar

- **WHEN** a form with unsaved changes is shown at 390 pixels
- **THEN** the save bar is an `MdSaveBar` that stays above the bottom bar and the safe area, and the view carries no offset in a style
  block

### Requirement: Breakpoints are the MD3 window classes only

The layout SHALL use only the MD3 window classes `medium` (600 pixels), `expanded` (840 pixels), `large` (1200 pixels) and
`xlarge` (1600 pixels), with the compact window below 600 pixels having no prefix. The theme SHALL reset every default
breakpoint of Tailwind, so that `sm:`, `md:`, `lg:`, `xl:` and `2xl:` do not exist. A view SHALL NOT use a width literal in a
utility, a media query or a `max-[...]` variant.

#### Scenario: Window classes exist

- **WHEN** the theme in `src/style.css` is read
- **THEN** `--breakpoint-*: initial` is declared, and the breakpoints are `--breakpoint-medium: 600px`, `--breakpoint-expanded: 840px`,
  `--breakpoint-large: 1200px` and `--breakpoint-xlarge: 1600px`, and no other breakpoint

#### Scenario: Former literal

- **WHEN** the side navigation is shown at 1230 pixels and at 1240 pixels
- **THEN** the navigation layout is the same at both widths, because the threshold is the `large` window class

#### Scenario: Gutter kept

- **WHEN** a page is shown at 390 pixels and at 840 pixels
- **THEN** the side gutter is 16 pixels at 390 pixels and 24 pixels at 840 pixels, as the shared container rule sets it

### Requirement: Views carry no custom classes, no style blocks and no raw values

A view or a shared component of the dashboard SHALL NOT define a class of its own in a `<style>` block, SHALL NOT use an arbitrary value
or an `!important` modifier in a utility, SHALL NOT use a raw colour or a Tailwind palette shade, and SHALL NOT fade content with an
opacity utility in place of a token. Font weight and size SHALL come from the type scale classes. The style gate of
`add-ui-style-gate` SHALL pass with an empty baseline at the end of this change.

#### Scenario: Style block removed

- **WHEN** the file list of the dashboard is searched for a `<style>` block
- **THEN** no view or component contains one

#### Scenario: Settings page without custom rules

- **WHEN** the settings view is rendered with a read-only input
- **THEN** the read-only look is drawn by a shared field rule, and no rule in the view targets `.settings-view`

#### Scenario: Gate baseline empty

- **WHEN** the style gate runs at the end of the change
- **THEN** the gate reports no violation and its baseline has no entry

### Requirement: Anchors stay stable

No `data-ingot-component` and no `data-ingot-part` SHALL be removed or moved off its semantic element. When two components merge,
both names SHALL stay, with the outer element keeping the wrapper's name and the inner element the name of the component it
replaces. New names SHALL be added to `src/anchors/registry.json` with a test in `src/anchors/anchors.test.js`, and the generated
anchor reference SHALL be regenerated in the same change.

#### Scenario: Merged component keeps both names

- **WHEN** the table of the repositories page is drawn by an `MdDataTable` inside the existing `repositories-table` component
- **THEN** the `repositories-table` name stays on the component root, the `MdDataTable` keeps its own name on its element, and both names
  resolve in the rendered page

#### Scenario: Registry test

- **WHEN** the registry gains a name
- **THEN** `anchors.test.js` passes, and the generated reference lists the new name

### Requirement: The change is not breaking

This change SHALL NOT change a configuration key, an environment variable, a deployment manifest, a container image, a route, a
permission node, a settings API, stored data or a plugin API. The visible differences SHALL be listed in each wave's pull request
with before and after screenshots at 320, 600 and 1440 pixels in light and dark.

#### Scenario: No operator change

- **WHEN** an operator upgrades an existing installation to the release that contains this change
- **THEN** no configuration or manifest needs an edit, and the dashboard shows the same data at the same routes

#### Scenario: Visible change listed

- **WHEN** a wave changes the position of a column or the width of a breakpoint threshold
- **THEN** the wave's pull request lists the change and carries a before and after screenshot at each width named in this requirement
