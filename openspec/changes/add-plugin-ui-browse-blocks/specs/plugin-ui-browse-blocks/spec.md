# Spec Delta

## Purpose

A plugin page can draw a catalogue: a list of items with tags and a link to each, a copyable code snippet, a filter whose choices the plugin
declares, and a link to an address on the same host. The dashboard draws the blocks with its own components, from data the plugin sends.

## ADDED Requirements

### Requirement: List block

A `list` block SHALL name a `source`, a GET path below `/api/plugins/<plugin>/`. The source answers an object with an `items` array, a `next`
string or null, and optionally `facets`. The dashboard draws at most 100 items of one response. An item needs a title, and MAY carry a
subtitle, at most eight chips and a link that meets the link rules.

#### Scenario: A list with two pages

- **WHEN** the source answers 100 items and a `next` string
- **THEN** the list draws 100 rows and a "Load more" control, and the control requests the source with `cursor` set to that string

#### Scenario: An item without a title

- **WHEN** the source answers an item with an empty title
- **THEN** that item is not drawn, and the list shows a note that one item was skipped

#### Scenario: Markup in an item

- **WHEN** an item title is `<img src=x onerror=alert(1)>`
- **THEN** the dashboard shows those characters as text and loads no image

#### Scenario: A source outside the prefix is refused at registration

- **WHEN** a plugin registers a list block with the source `/api/tokens`
- **THEN** the page is dropped with a warning that names the path

### Requirement: Code block

A `code` block SHALL carry a `text` of 1 to 4000 characters, an optional label, and a `language` of `plain`, `kotlin`, `groovy`, `xml` or
`properties`. The text is drawn as plain characters that wrap without a page scroll at 320 pixels.

#### Scenario: Copy succeeds

- **WHEN** a visitor activates the copy action and the clipboard accepts the text
- **THEN** the status region reads "Copied" and the clipboard holds the code text and nothing else

#### Scenario: Copy refused

- **WHEN** the clipboard refuses the write
- **THEN** the text is selected and the status region asks the visitor to copy it with the keyboard

### Requirement: Filter block

A `filter` block SHALL carry one to six controls, each a text or select control with a `key` of `[a-z][A-Za-z0-9]{0,31}` that is unique on the
page. The key `cursor` is reserved. The filter applies to the list block that follows it on the same page.

#### Scenario: A select changes the list

- **WHEN** a visitor selects the option `paper` of the control `loader`
- **THEN** the next request to the list source carries `loader=paper` and no cursor, and the list starts at its first page

#### Scenario: A filter key reserved for paging

- **WHEN** a plugin registers a control with the key `cursor`
- **THEN** the page is dropped with a warning that names the key

#### Scenario: A filter without a list

- **WHEN** a plugin registers a page whose filter block is not followed by a list
- **THEN** the page is dropped with a warning

#### Scenario: Counts only from the response

- **WHEN** the list response has no `facets` entry for the key of a select control
- **THEN** the options are shown without counts

### Requirement: Links are same-origin paths

A `link` block and a list item `href` SHALL be a path that starts with one `/`, whose second character is neither `/` nor `\`, that does not
start with `/api/` or `/assets/`, and that uses only `A-Za-z0-9._~/?=&-`. The server checks a link at registration, and the dashboard
checks it again before binding, including a link in a list response. A link opens in the same tab and carries no `target`.

#### Scenario: A link to another host is refused

- **WHEN** a plugin registers a link with the href `https://example.org/a`
- **THEN** the page is dropped with a warning that names the href

#### Scenario: A link from a response is checked by the dashboard

- **WHEN** a list response contains an item whose href is `javascript:alert(1)`
- **THEN** the row is drawn without a link and with the note "Link not shown", and no script runs

#### Scenario: A link to a repository path works

- **WHEN** a list item links to `/releases/com/example/demo/1.2.3/demo-1.2.3.jar`
- **THEN** the link is drawn and opens that path in the same tab

### Requirement: Browse blocks on reader pages

A reader page declared through `add-plugin-reader-pages` MAY use the `list`, `code`, `filter` and `link` blocks. The read-only rule of that
change still holds: a reader page SHALL NOT contain a `form`, an `action` or a `status` block.

#### Scenario: A list on a reader page

- **WHEN** a reader page declares a list whose source is a reader source of the same plugin
- **THEN** an anonymous visitor sees the list, and the plugin decides which rows it contains

### Requirement: Existing blocks keep their shape

The constructors and the `type` values of `text`, `keyValue`, `table`, `form`, `action` and `status` SHALL NOT change.

#### Scenario: A plugin built before this change

- **WHEN** a plugin built against the facade before this change registers a page with a table and an action
- **THEN** the page is accepted and drawn as before
