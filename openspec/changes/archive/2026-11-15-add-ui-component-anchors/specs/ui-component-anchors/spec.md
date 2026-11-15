# Spec Delta

## Purpose

Every component of the dashboard carries a stable, documented name in the DOM, so that a plugin or a test can refer to it
across releases. The names are data, checked by the test suite, and they change nothing that a user sees or a browser runs.

## ADDED Requirements

### Requirement: Every component is named

Each component under `reposilite-frontend/src/components/` (except the icons), each page and the application shell SHALL carry the attribute `data-ingot-component` with its registered kebab-case name on the element that is its root. A root `<Teleport>` SHALL carry it on the first element inside the teleport.

#### Scenario: A component renders its name

- **WHEN** the dashboard renders `RepositoriesTable`
- **THEN** its root element has `data-ingot-component="repositories-table"`

#### Scenario: A new component without a name fails the suite

- **WHEN** a component is added under `src/components/` without the attribute
- **THEN** the anchor suite fails and names the file

### Requirement: The registry is the source of names

The names, their parts, their states and their stability SHALL be listed in `reposilite-frontend/src/anchors/registry.json`. The set of names in the source SHALL equal the set in the registry, and names SHALL be unique and kebab-case.

#### Scenario: A name missing from the registry fails the suite

- **WHEN** a template carries a component name that the registry does not list
- **THEN** the anchor suite fails and names the component

#### Scenario: Duplicate names fail the suite

- **WHEN** two components carry the same name
- **THEN** the anchor suite fails and names both files

### Requirement: Parts and states come from a closed set

A named part SHALL be marked with `data-ingot-part`, and a state SHALL be marked with `data-ingot-state`. A part value SHALL be listed for its component in the registry. A state value SHALL be one of `current`, `loading`, `error`, `empty` and `open`.

#### Scenario: An unlisted part fails the suite

- **WHEN** a template uses `data-ingot-part="banner"` and the registry does not list `banner` for that component
- **THEN** the anchor suite fails and names the part

#### Scenario: An unknown state fails the suite

- **WHEN** a template uses `data-ingot-state="busy"`
- **THEN** the anchor suite fails, because `busy` is not in the closed set

### Requirement: Dynamic identity is limited

A `data-ingot-*` attribute SHALL take a value bound at run time only where the registry allows it. The only allowed binding in this change is `data-ingot-repository`, which SHALL carry a repository name that is already shown on the same row or header. No secret, token, credential, session value, address or personal data SHALL appear in a `data-ingot-*` attribute.

#### Scenario: A repository row carries its name

- **WHEN** the repositories table shows the repository `releases`
- **THEN** that row has `data-ingot-repository="releases"`

#### Scenario: An identifier that is not allowed fails the suite

- **WHEN** a template binds `data-ingot-principal` to a value at run time
- **THEN** the anchor suite fails, because the binding is not on the allow-list

### Requirement: Plugin pages carry only validated values

A plugin page SHALL carry `data-ingot-component="plugin-page"`. Its block SHALL carry `data-ingot-block` with one of the six block types. A plugin page id MAY be carried as `data-ingot-plugin-page` only when it matches the server's id rule. A plugin name SHALL NOT appear in an attribute.

#### Scenario: A status block names its type

- **WHEN** a plugin page holds a `status` block
- **THEN** that block has `data-ingot-block="status"`

#### Scenario: A plugin name is not exposed

- **WHEN** a plugin named `example` contributes a page
- **THEN** no `data-ingot-*` attribute on the page contains `example`

### Requirement: Stability and deprecation

A name registered as `stable` SHALL NOT be removed or renamed except through an alias. A renamed name SHALL remain on the same element as `data-ingot-component-alias` until the release named by `removeIn`, and the reference page SHALL list the alias. A name registered as `experimental` MAY change in any release and SHALL be marked as such in the reference page.

#### Scenario: A rename keeps the old name for one minor release

- **WHEN** a stable component is renamed in a release
- **THEN** its element carries the new name and `data-ingot-component-alias` with the old name, and the reference page lists both

#### Scenario: An experimental name may change

- **WHEN** an experimental name is renamed
- **THEN** no alias is required and the reference page marks the name as experimental

### Requirement: The reference page is generated from the registry

The page `docs/gitbook/reference/dashboard-anchors.md` SHALL be generated from the registry by `npm run anchors:doc`. The anchor suite SHALL fail when the committed page differs from the generated one, and the failure message SHALL name the command.

#### Scenario: A stale page fails the suite

- **WHEN** a name is added to the registry and the page is not regenerated
- **THEN** the anchor suite fails and names `npm run anchors:doc`

### Requirement: No behaviour, style or policy change

The attributes SHALL NOT be read by any code in `src/` outside the anchor suite, SHALL NOT change any class, style, identifier, event handler or existing `data-testid`, and SHALL NOT change the dashboard Content-Security-Policy or the split image policy.

#### Scenario: The rendered dashboard looks the same

- **WHEN** the dashboard is rendered before and after this change with the same data
- **THEN** every element keeps its class, style, text and existing test id, and only the new attributes are added

#### Scenario: No code selects on the anchors

- **WHEN** a source file under `src/` other than the anchor suite selects on `data-ingot-`
- **THEN** the anchor suite fails

### Requirement: The names ship in production builds

The attributes SHALL be present in the production build of the dashboard, bundled or split, because a plugin author works against that build. The bundle size change SHALL be recorded in the pull request.

#### Scenario: The production build carries the names

- **WHEN** `npm run build` produces the dashboard
- **THEN** the built HTML and JavaScript contain `data-ingot-component`
