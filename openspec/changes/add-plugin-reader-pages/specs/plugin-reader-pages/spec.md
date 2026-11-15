# Spec Delta

## Purpose

A server plugin can contribute a page that anonymous visitors may read, as data, through the same facade as the administrator pages.
The page shows only what its declared, read-only sources return, and the dashboard draws it with its own components.

## ADDED Requirements

### Requirement: Reader pages are declared as read-only data

A plugin SHALL contribute a reader page only through `PluginUiFacade`, as a `PluginUiReaderPage` with an id of `[a-z0-9-]{1,32}`, a
title of 1 to 60 characters, an optional description of at most 200 characters and 1 to 30 blocks. A reader page SHALL NOT name an admin
area or a menu group. Its blocks SHALL be of the types that read only. A reader page SHALL NOT contain a `form`, an `action` or a
`status` block, and every `source` SHALL be a path below `/api/plugins/<plugin>/`.

#### Scenario: A write block is refused at registration

- **WHEN** a plugin registers a reader page that contains an `action` block
- **THEN** the page is dropped with a warning that names the plugin, the page and the block, and the server starts

#### Scenario: A source outside the prefix is refused

- **WHEN** a reader page names the source `/api/tokens`
- **THEN** the page is dropped with a warning that names the path

#### Scenario: The limits hold

- **WHEN** a plugin registers a 21st reader page
- **THEN** the page is dropped with a warning, and the admin pages of the plugin are not affected

### Requirement: Reader pages are readable without sign in

`GET /api/frontend/plugin-ui/readers` SHALL return the reader pages of every enabled plugin with a `version` field of 1 to an anonymous
caller and to a signed-in caller alike. The answer SHALL NOT contain a stylesheet or the list of enabled plugins. `GET /api/frontend/plugin-ui`
SHALL keep answering `401` to an anonymous caller and SHALL NOT repeat the reader pages.

#### Scenario: Anonymous visitor reads the reader pages

- **WHEN** a request without credentials asks for the reader manifest and one plugin registered a reader page
- **THEN** the answer contains that page and its blocks

#### Scenario: The admin manifest stays closed

- **WHEN** a request without credentials asks for `GET /api/frontend/plugin-ui`
- **THEN** the answer is `401`

#### Scenario: No reader page, no change

- **WHEN** no plugin registered a reader page
- **THEN** the reader manifest is `{ "version": 1, "pages": [] }`

### Requirement: Only declared reader sources are reachable anonymously

A `GET` or `HEAD` request whose path, below `/api/plugins/<plugin>/`, equals a source declared by a reader page of that plugin SHALL
be handled without sign in and without the admin area check. Every other request below `/api/plugins/<plugin>/` SHALL be checked as
before. A path that contains `..`, `//`, an empty segment or a percent-encoded character SHALL NOT match a declared source.

#### Scenario: Declared source read anonymously

- **WHEN** an anonymous caller sends `GET /api/plugins/<plugin>/releases` and `releases` is a reader source of that plugin
- **THEN** the plugin handler runs and answers as it declares

#### Scenario: Same path with another method

- **WHEN** an anonymous caller sends `POST /api/plugins/<plugin>/releases`
- **THEN** the answer is `401`, and the handler does not run

#### Scenario: Traversal does not match

- **WHEN** an anonymous caller sends `GET /api/plugins/<plugin>/releases/../admin`
- **THEN** the answer is `401`, and the handler does not run

#### Scenario: Another plugin's source is not exempt

- **WHEN** an anonymous caller sends `GET /api/plugins/<other>/releases` and `releases` is a reader source of a different plugin only
- **THEN** the answer is `401`

### Requirement: Reader answers are per viewer

A response of a reader source SHALL carry `Vary: Cookie, Authorization`, and SHALL carry `Cache-Control: private, no-cache` unless the
plugin set its own `Cache-Control` header. A shared cache SHALL NOT serve the answer of one viewer to another.

#### Scenario: Shared cache keeps viewers apart

- **WHEN** a reader source answers an anonymous and then a signed-in request for the same path
- **THEN** both answers name `Cookie` and `Authorization` in `Vary` and neither is marked as shared-cacheable

### Requirement: The reader switch closes the readers

`INGOT_LOCAL_PLUGINREADERS` (fallback `REPOSILITE_LOCAL_PLUGINREADERS`) SHALL default to `true`. While it is `false`, the reader manifest
SHALL contain no pages, every declared reader source SHALL answer `404` with the usual problem body without running the plugin handler,
and the dashboard address `/plugins/...` SHALL NOT be claimed. Admin pages SHALL NOT be affected by this switch.

#### Scenario: Switch off

- **WHEN** `INGOT_LOCAL_PLUGINREADERS=false` and a plugin registered a reader page with the source `releases`
- **THEN** the reader manifest is empty and `GET /api/plugins/<plugin>/releases` answers `404` without running the handler

#### Scenario: Admin page unaffected

- **WHEN** the same switch is `false` and the plugin also has an admin page
- **THEN** the admin page is listed in the admin manifest as before

### Requirement: Reader pages appear in the Browse section

The dashboard SHALL draw a reader page at `/plugins/<plugin>/<page>` for every visitor. The Browse section SHALL list the reader pages under the
heading "Plugins", in manifest order. With no reader page, the heading SHALL NOT be drawn. A reader page SHALL NOT appear in the bottom bar.
A block that the dashboard does not draw for a reader page SHALL show the "unsupported block" note, and the dashboard SHALL send no request for it.

#### Scenario: Anonymous visitor opens a reader page

- **WHEN** an anonymous visitor opens `/plugins/<plugin>/<page>`
- **THEN** the page is drawn, and no sign-in prompt is shown

#### Scenario: A form in a reader page is not drawn

- **WHEN** the manifest of a reader page contains a `form` block (a manipulated manifest)
- **THEN** the block shows the unsupported note and no request is sent

### Requirement: The address is claimed only by readers and never hides a repository from Maven

The server SHALL serve the dashboard at `/plugins/<path>` only while at least one reader page is registered. A request that is not a
browser navigation to that path SHALL be answered as it is answered without this change. The split dashboard image SHALL answer
`/plugins/...` with the document only for a browser navigation, and SHALL proxy every other request.

#### Scenario: Repository named plugins, Maven client

- **WHEN** a repository `plugins` exists, a reader page is registered, and a Maven client requests `GET /plugins/<gav>`
- **THEN** the repository answers, as before

#### Scenario: Repository named plugins, split image

- **WHEN** the split dashboard image receives `GET /plugins/<gav>` without `Sec-Fetch-Mode: navigate`
- **THEN** it proxies the request to the server

#### Scenario: No reader page

- **WHEN** no reader page is registered
- **THEN** `/plugins/...` is not claimed by the dashboard, and the startup log has no warning about it
