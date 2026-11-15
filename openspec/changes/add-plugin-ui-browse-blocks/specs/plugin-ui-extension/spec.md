# Spec Delta

## MODIFIED Requirements

### Requirement: Declarative pages only

A plugin SHALL contribute a page only as data registered through `PluginUiFacade`. The dashboard SHALL draw it with components of
its own build. The system SHALL NOT load a script, stylesheet, frame or web component supplied by a plugin, and the page model SHALL
have no field that carries HTML, script or style. The only address that a block MAY carry is a same-origin path that meets the link
rules of `plugin-ui-browse-blocks`, and the browser SHALL NOT open any other address that a plugin supplies.

#### Scenario: Markup in text is shown as text

- **WHEN** a plugin registers a `text` block containing `<img src=x onerror=alert(1)>`
- **THEN** the dashboard shows those characters as text and executes nothing

#### Scenario: A plugin without a page changes nothing

- **WHEN** a plugin never calls `PluginUiFacade`
- **THEN** the manifest is empty for that plugin and the dashboard shows nothing new

#### Scenario: A link to another origin is not opened

- **WHEN** a plugin supplies a link whose address names another origin
- **THEN** the link is refused, and the browser opens no address of the plugin

### Requirement: Page model and limits

The system SHALL accept a page with an id of `[a-z0-9-]{1,32}`, a title of 1 to 60 characters, a group (`Repository`, `Security`,
`System`, `Support`, the Administration groups of `add-repositories-admin-page`; default `System`), an area, and 1 to 30 blocks of the types
`text`, `keyValue`, `table`, `form`, `action`, `status`, `list`, `code`, `filter` and `link`. A plugin SHALL have at most 20 pages and 64 KiB
of serialised manifest.

#### Scenario: An invalid page is dropped, the server starts

- **WHEN** a plugin registers a page with 31 blocks and another valid page
- **THEN** the first is dropped with a warning that names the plugin and the problem, the second is served, and startup continues

#### Scenario: An unknown block type degrades visibly

- **WHEN** the dashboard receives a block type it does not know
- **THEN** it shows an "unsupported block" note in its place and draws the other blocks
