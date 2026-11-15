# Spec Delta

## Purpose

A server plugin can contribute a dashboard page, a menu entry and a settings section without a frontend rebuild and without
running any plugin code in the browser. The page is data that the dashboard draws with its own components.

## ADDED Requirements

### Requirement: Declarative pages only

A plugin SHALL contribute a page only as data registered through `PluginUiFacade`. The dashboard SHALL draw it with components of
its own build. The system SHALL NOT load a script, stylesheet, frame or web component supplied by a plugin, and the page model SHALL
have no field that carries HTML, script or style.

#### Scenario: Markup in text is shown as text

- **WHEN** a plugin registers a `text` block containing `<img src=x onerror=alert(1)>`
- **THEN** the dashboard shows those characters as text and executes nothing

#### Scenario: A plugin without a page changes nothing

- **WHEN** a plugin never calls `PluginUiFacade`
- **THEN** the manifest is empty for that plugin and the dashboard shows nothing new

### Requirement: Page model and limits

The system SHALL accept a page with an id of `[a-z0-9-]{1,32}`, a title of 1 to 60 characters, a group (`Repository`, `Security`,
`System`, `Support`, the Administration groups of `add-repositories-admin-page`; default `System`), an area, and 1 to 30 blocks of the types `text`, `keyValue`, `table`, `form`, `action`
and `status`. A plugin SHALL have at most 20 pages and 64 KiB of serialised manifest.

#### Scenario: An invalid page is dropped, the server starts

- **WHEN** a plugin registers a page with 31 blocks and another valid page
- **THEN** the first is dropped with a warning that names the plugin and the problem, the second is served, and startup continues

#### Scenario: An unknown block type degrades visibly

- **WHEN** the dashboard receives a block type it does not know
- **THEN** it shows an "unsupported block" note in its place and draws the other blocks

### Requirement: Permission per page

Every page SHALL name one admin area (`settings`, `users`, `teams`, `tokens`, `maintenance`, `audit`, `console`, `dashboard`) or
`*` for the full instance administrator. The manifest SHALL contain only pages the caller may open, and the dashboard SHALL check
the same node before drawing the page.

#### Scenario: A caller without the area does not see the page

- **WHEN** a signed-in caller without the page's area requests the manifest
- **THEN** the page is not in it, and no menu entry for it is drawn

#### Scenario: A deep link without the permission shows no access

- **WHEN** such a caller opens `/settings/plugins/<plugin>/<page>`
- **THEN** the dashboard shows the no access state at that address and loads no data for the page

#### Scenario: An unknown area is refused at registration

- **WHEN** a plugin registers a page with the area `billing`
- **THEN** the page is dropped with a warning that lists the valid areas

### Requirement: Manifest endpoint

`GET /api/frontend/plugin-ui` SHALL return the contributed pages and settings sections for the caller, with a `version` field (1)
and an `enabledPlugins` field (see the next requirement).
It SHALL answer `401` to an anonymous caller. It SHALL be served the same way when the dashboard is a separate container.

#### Scenario: Signed-in caller gets their pages

- **WHEN** a caller with `ingot.admin.maintenance` requests the manifest and one page names `maintenance`
- **THEN** the answer contains that page with its blocks and `version` 1

#### Scenario: Anonymous caller is refused

- **WHEN** a request without credentials asks for the manifest
- **THEN** the answer is `401` and no page is named

#### Scenario: The split dashboard reads the same manifest

- **WHEN** the dashboard image proxies the request to the server
- **THEN** the answer is identical to the one the server gives its bundled dashboard

### Requirement: Enabled plugins are reported

The manifest SHALL carry `enabledPlugins`, the names of the loaded and enabled server plugins (for example `nexus-import`), so that a
first-party page of a plugin can appear only while it runs. It SHALL be present for every signed-in caller, list names only, ignore
`INGOT_LOCAL_PLUGINUI`, and omit a bundled plugin that is switched off.

#### Scenario: Bundled plugin switched on

- **WHEN** `INGOT_NEXUS_IMPORT_ENABLED=true` and a signed-in caller requests the manifest
- **THEN** `enabledPlugins` contains `nexus-import`

#### Scenario: Bundled plugin switched off

- **WHEN** the switch of the plugin is unset
- **THEN** `enabledPlugins` does not contain its name

#### Scenario: Extension point switched off

- **WHEN** `INGOT_LOCAL_PLUGINUI=false` and the plugin `nexus-import` is enabled
- **THEN** the manifest has no pages and `enabledPlugins` still contains `nexus-import`

### Requirement: Calls stay below the plugin prefix

A block path SHALL start with `/api/plugins/<plugin>/`, where `<plugin>` is derived from the registering plugin and not supplied by
it. An `action` method SHALL be `POST`, `PUT` or `DELETE`. The dashboard SHALL refuse a call outside the prefix and SHALL send the
CSRF header on every write.

#### Scenario: A path outside the prefix is refused

- **WHEN** a plugin registers a block with the path `/api/tokens`
- **THEN** the page is dropped with a warning naming the path

#### Scenario: A tampered manifest does not widen the call

- **WHEN** the dashboard receives a block whose path is outside the prefix
- **THEN** it makes no request and shows an error in the block

### Requirement: Data routes require the page area

For a plugin that registered a page, the server SHALL require one of the areas named by its pages for every request below
`/api/plugins/<plugin>/`, after authentication, answering `401` or `403` with the usual problem body. A plugin that registered no
page SHALL see no change.

#### Scenario: A route the plugin forgot to guard is guarded

- **WHEN** a caller without the page's area calls `GET /api/plugins/<plugin>/anything`
- **THEN** the answer is `403` and the plugin's handler does not run

#### Scenario: Another plugin's routes are untouched

- **WHEN** a plugin without a page serves `/api/plugins/<other>/status` as before
- **THEN** the request is handled exactly as it was before this change

### Requirement: Address and menu

A page SHALL be opened at `/settings/plugins/<plugin>/<page>`, with route name `settings-plugin`, placed before the settings entry
route. The navigation registry SHALL show a contributed page in the menu group it names, SHALL never place it in the bottom bar,
and SHALL never replace or reorder a built-in page. Group names SHALL be those of `add-repositories-admin-page`. Without that change the page SHALL appear in the Plugins category of Settings.

#### Scenario: The address resolves in both deploy modes

- **WHEN** `/settings/plugins/example/status` is requested from the bundled dashboard and from the split image
- **THEN** both answer with the dashboard document and the page is drawn

#### Scenario: A built-in route keeps its meaning

- **WHEN** `/settings/maven/repositories/releases` is opened
- **THEN** it opens the settings entry as before, not a plugin page

### Requirement: Settings section placement

A plugin MAY register a label and a group (`Repository` or `System`, default `System`) for an existing settings domain of its own. Without a section the domain SHALL behave as it
does today. A settings domain named `plugins` SHALL be reported at startup as colliding with the reserved address.

#### Scenario: A section places the domain

- **WHEN** a plugin registers a section for its domain with the label "Mirror checks" and the group Repository
- **THEN** Settings shows the domain under Repository with that label

#### Scenario: No section, unchanged placement

- **WHEN** a plugin has a settings domain and registers no section
- **THEN** the domain is shown in Plugins under its identifier or title, as before

### Requirement: Browser policy is unchanged

The Content-Security-Policy of the dashboard document SHALL stay byte for byte what it is, in the bundled server and in the dashboard
image. The system SHALL add no source to `script-src`, `connect-src`, `frame-src` or `img-src` for this feature.

#### Scenario: Policy equals the previous value

- **WHEN** a dashboard document is requested from the server and from the image
- **THEN** each carries the policy it carried before this change, and the two agree

### Requirement: Operator switch

`INGOT_LOCAL_PLUGINUI` (fallback `REPOSILITE_LOCAL_PLUGINUI`) SHALL default to `true`. When `false`, the manifest SHALL have no pages and no sections (`enabledPlugins` is unaffected), the
data route filter SHALL not be installed and no contributed page or section SHALL be shown.

#### Scenario: Switched off

- **WHEN** `INGOT_LOCAL_PLUGINUI=false` and a plugin has registered a page
- **THEN** the manifest has no pages and the address of the page shows the not found state

#### Scenario: Default needs no configuration

- **WHEN** an existing Reposilite configuration is started without the variable
- **THEN** the switch is on and nothing in the dashboard changes
