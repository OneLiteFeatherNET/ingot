# Spec Delta

## Purpose

A server plugin can deliver a stylesheet that the dashboard loads for a signed-in caller. The stylesheet is checked when it is
registered, it cannot reach the network, it is placed in a named layer, and it is served from a path derived from its content.
No plugin script runs in the dashboard.

## ADDED Requirements

### Requirement: Stylesheets only, no script

A plugin SHALL deliver a dashboard stylesheet only as a CSS file registered through `PluginUiRegistrar.registerStylesheet`. The
dashboard SHALL NOT load a script, a module, a frame or a web component supplied by a plugin, and the stylesheet SHALL NOT carry
script-like constructs such as `expression(` or `behavior:`.

#### Scenario: A stylesheet with an expression is refused

- **WHEN** a plugin registers a stylesheet that contains `width: expression(alert(1))`
- **THEN** the stylesheet is dropped with a warning that names the plugin and the construct, and the server starts

#### Scenario: No plugin script is loaded

- **WHEN** a signed-in caller opens the dashboard with a plugin that declares a stylesheet
- **THEN** the page loads one stylesheet link for that plugin and no script element from the plugin

### Requirement: The stylesheet cannot reach the network

The validator SHALL reject a stylesheet that contains `@import`, `@font-face`, `src(`, `image-set(`, `-webkit-image-set(`, a `url(`
whose argument is not a `data:image/` URI, or a CSS escape outside a string. Its size SHALL be at most 64 KiB, its encoding UTF-8, and
its braces balanced.

#### Scenario: A remote image is refused

- **WHEN** a stylesheet contains `background: url(https://example.org/x.png)`
- **THEN** the stylesheet is dropped with a warning that names the URL's construct, and no stylesheet is served for the plugin

#### Scenario: An escaped import is refused

- **WHEN** a stylesheet contains `@\69mport "https://example.org/x.css";`
- **THEN** the stylesheet is dropped, because a CSS escape outside a string is not accepted

#### Scenario: A data image is accepted

- **WHEN** a stylesheet contains `background: url(data:image/png;base64,iVBORw0KGgo=)`
- **THEN** the stylesheet is accepted

### Requirement: The rules sit in the plugin layer

The server SHALL wrap the accepted rules in `@layer ingot-plugins`. The dashboard SHALL declare `ingot-plugins` after the Tailwind layers
in its global stylesheet, so that the order of the layers does not depend on the order in which stylesheets load.

#### Scenario: A plugin rule beats a utility class

- **WHEN** a plugin rule sets a colour on a selector that a dashboard utility class also sets
- **THEN** the plugin rule's colour is the one drawn

#### Scenario: A plugin cannot reorder the layers

- **WHEN** a stylesheet declares `@layer theme, ingot-plugins;` as a statement
- **THEN** the dashboard's layer order is unchanged, because the statement sits inside `ingot-plugins` and creates only sub-layers of it

### Requirement: Served by content hash, cached for a year

The server SHALL serve the wrapped stylesheet at `/api/frontend/plugin-styles/<plugin>/<hash>.css`, where `<hash>` is the first 16
hexadecimal characters of the SHA-256 of the served bytes. The response SHALL carry `text/css; charset=utf-8`, the `immutable` year-long
cache policy and an `ETag`. An unknown plugin, an unknown hash or the switch being off SHALL answer `404`.

#### Scenario: The current hash is served

- **WHEN** a request asks for the path that the manifest names
- **THEN** the answer is `200` with the wrapped stylesheet, the `immutable` cache policy and an `ETag` equal to the hash

#### Scenario: A stale hash is not served

- **WHEN** a request asks for a hash that no longer matches the registered stylesheet
- **THEN** the answer is `404`

### Requirement: The manifest lists stylesheets for signed-in callers

`GET /api/frontend/plugin-ui` SHALL carry a `stylesheets` list with one entry per plugin that declared a valid stylesheet, containing the
plugin name, the path and the SHA-256. The list SHALL be sent to every signed-in caller and SHALL be empty for an anonymous caller, who
receives `401`. The `version` field SHALL stay 1.

#### Scenario: A signed-in caller receives the entry

- **WHEN** a signed-in caller without any admin area requests the manifest and the plugin `example` declared a stylesheet
- **THEN** the `stylesheets` list contains the entry for `example` with its path and hash

#### Scenario: An anonymous caller is refused

- **WHEN** an anonymous caller requests the manifest
- **THEN** the answer is `401`

### Requirement: The dashboard loads only well-formed links

The dashboard SHALL add one `<link rel="stylesheet">` per manifest entry to the end of `<head>`, after the dashboard's own stylesheet, and
SHALL remove a link when its entry leaves the manifest. It SHALL NOT add a link whose `href` does not match the served path pattern.

#### Scenario: The link follows the dashboard stylesheet

- **WHEN** the manifest contains one stylesheet entry
- **THEN** the plugin's link is the last stylesheet element in `<head>`

#### Scenario: A link is removed when the plugin is gone

- **WHEN** a later manifest no longer lists the plugin
- **THEN** its link is removed from the document

#### Scenario: A tampered path adds no link

- **WHEN** a manifest entry has a path outside `/api/frontend/plugin-styles/`
- **THEN** no link is added for it

### Requirement: An operator can turn all plugin styles off

The switch `INGOT_LOCAL_PLUGINSTYLES` (fallback `REPOSILITE_LOCAL_PLUGINSTYLES`) SHALL default to `true`. When it is `false`, the
`stylesheets` list SHALL be empty and the served path SHALL answer `404`. Pages, sections and `enabledPlugins` SHALL be unaffected by it.

#### Scenario: The switch off removes plugin styles

- **WHEN** `INGOT_LOCAL_PLUGINSTYLES=false` and a plugin declared a stylesheet
- **THEN** the manifest has an empty `stylesheets` list and the path answers `404`, and the plugin's pages still appear

#### Scenario: An installation without such a plugin changes nothing

- **WHEN** no plugin declares a stylesheet
- **THEN** the manifest has an empty `stylesheets` list and the dashboard adds no link

### Requirement: The browser policy does not change

The dashboard Content-Security-Policy, in the backend and in the split image, SHALL remain byte for byte as it is, and a plugin stylesheet
SHALL be loaded as a same-origin stylesheet under the existing `style-src 'self'`.

#### Scenario: The policy is recorded and unchanged

- **WHEN** the policy tests run
- **THEN** `DASHBOARD_POLICY` and the split image policy equal their recorded values
