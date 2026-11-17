# Proposal

## Maintainer decisions

The maintainer decided each question below. The requirements and tasks already follow these decisions.

1. **One change, split by type.** The change stays one change. The runtime work ships under `feat(plugin)`. The `ci`, `docs` and
   `feat(ui)` groups are separate commits and pull requests of the same feature.
2. **Experimental is sticky, applied strictly.** A plugin module that opts in to an `@ExperimentalIngot*` API is Experimental,
   whatever its shipping status. `elasticsearch-plugin` and `meilisearch-plugin` are Experimental. `opensearch-plugin` is the one
   Experimental jar in the image, and it stays switched off by default. These plugins move to Official once the search API is
   stabilised.
3. **Maintainer-owned plugins without a contract test stay Community.** `swagger-plugin`, `groovy-plugin` and `example-plugin` are
   Community until a contract test exists. Promotion is then a one-line change.
4. **The four pending plugins are Experimental.** `azure-storage-plugin`, `gcs-storage-plugin`, `storage-cache-plugin` and
   `lucene-plugin` each add `tier = EXPERIMENTAL` in their own small follow-up pull request. Each one follows the annotation and
   check pull requests of this change.
5. **The support wording changes now.** Core and Official are fixed on the release lines that `SUPPORT.md` lists, on a best effort
   basis. Release lines added later (release branches, LTS) are covered automatically. `add-release-branches-and-backports` is not
   a blocker for this change.
6. **Log line format.** The existing startup line of loaded plugins stays byte for byte unchanged. The tiers go on one additional
   INFO line after it, so the drop-in contract holds for operators who parse the existing line.

## Why

Ingot ships plugins of very different weight. Some are compiled into the server, some are bundled in the image, some are
maintained by OneLiteFeather as separate jars with their own tests, some build on APIs that may still change, and some come from
third parties who are not supported by us. Today an operator sees a flat list of names and versions in the system information
page and in the support bundle. Nothing says which plugins we stand behind, which ones may break in the next minor release, or
which ones we only list. The question "is this plugin supported" is answered by reading the source.

The four tiers answer that question with evidence, not with a promise. A tier is a claim that CI checks. A plugin can only be
Official if it has the CI job and the contract tests that make the claim true. A plugin that opts in to an experimental API can
only be Experimental, so nobody is surprised when its API changes. The rules are written down once, in the GitBook, and the
startup log and the dashboard show the tier next to each plugin.

The tier never blocks a plugin. The drop-in contract of `CLAUDE.md` says that pointing an existing Reposilite installation at
Ingot must need no configuration change, and that a plugin built for Reposilite keeps loading. A tier that refused a plugin
would break that. So the tier is information and a warning, and nothing else.

## What Changes

- **An optional tier on the plugin annotation.** `com.reposilite.plugin.api.PluginTier` is a new enum with the values `CORE`,
  `OFFICIAL`, `COMMUNITY` and `EXPERIMENTAL`. `@Plugin` gets a `tier` element whose default is `COMMUNITY`. A plugin compiled
  against Reposilite does not have the element, and the JVM reads the default from the annotation type, so the plugin reads as
  Community. No existing plugin needs a change to compile or load.
- **Effective tier.** The loader computes one tier per plugin. A plugin built into the server or loaded from the bundled jars of
  the image is Core, unless it declares Experimental, in which case it is Experimental. A plugin from anywhere else keeps its
  declared tier. A plugin from anywhere else that declares Core is shown as Community, and the start logs a warning that names
  the plugin. The tier of a plugin is never a reason to refuse it, skip it, or change its start order.
- **Catalogue entry.** The store catalogue `ingot-plugin-catalogue.json` gets an optional `tier` field per entry. It is an
  additive field, so schema 1 stays. The release generator writes the tier that the jar declares. A missing field reads as
  `community`. An unknown tier value refuses that entry only, as a missing compatibility block does today.
- **Immutable per version.** The tier of a released version never changes, because the catalogue is append-only. A promotion or
  demotion ships as a new version.
- **Where the tier shows.**
  - *Catalogue entry*: the `tier` field, read by the store and by the dashboard selection, when that list exists.
  - *Startup log*: the existing line that lists loaded plugins is unchanged, byte for byte. One additional INFO line after it
    lists each plugin with its tier, for example `checksum (official)`. Each Experimental plugin also logs one warning line at start.
  - *System information page*: the existing plugin list (name and version) gets a tier label. The support bundle's
    `plugins.json` gets the same field. No new dashboard page is added, because the system information page already lists the
    plugins.
  - *GitBook*: a new page `references/plugins/plugin-tiers.md` defines the four tiers and the support policy of each. Each plugin
    page gets a line `Tier: <name>` that links to it.
  - *SUPPORT.md*: a section names the four tiers. Its existing text stays unchanged: community support only, no paid tier and no
    SLA. Official means a plugin maintained and tested by the OneLiteFeather maintainers, fixed on the supported release lines, on a
    best effort community basis.
- **Evidence rules and a CI check.** A Gradle task checks every plugin module against the rules in `plugin-tiers`. The check runs
  on every pull request in its own CI job. It fails when a declared tier has no evidence, or when a plugin uses an experimental
  API without declaring Experimental. The release generator runs the same check before it writes the catalogue.
- **Declarations.** The plugin modules in the repository declare their tier. The built-in plugins of the backend need no
  declaration, because their origin makes them Core.

### Out of scope

- A plugin of the image is never removed or refused because of its tier. Changing which plugins ship in the image is a separate
  decision.
- Support for LTS lines and the release branch model. That belongs to `add-release-branches-and-backports`, which may follow later.
  This change only refers to the lines `SUPPORT.md` lists.
- A paid tier, a service level agreement or any restriction of a feature by tier. `SUPPORT.md` keeps saying that it describes
  community support only, that there is no paid tier and no SLA, and that no feature depends on a support arrangement. Official
  does not mean paid support.
- A verified signature of the tier claim of a plugin loaded from the plugins directory. The tier of such a jar is what the jar
  declares, and the dashboard and the log say so. Only the signed catalogue of OneLiteFeather is evidence.
- A tier filter or a tier column in a plugin browser. The plugin store and the GitBook pages show the tier, and nothing filters on it.
- Tier-specific behaviour of the plugin API, such as different timeouts or resource limits per tier.
- Any change to the plugin packages. They stay `com.reposilite.*`, and the new enum is in `com.reposilite.plugin.api`.
- The four pending plugin changes themselves. Their own changes add their declaration (decision 4).

## Capabilities

### New Capabilities

- `plugin-tiers`: the tier enum and its annotation element, the effective tier and its origin rules, the warnings, the evidence
  rules, the CI check, and the places where the tier is shown.

### Modified Capabilities

- `plugin-store`: the catalogue entry gets the optional `tier` field, and a mismatch with the loaded jar is a warning.
- `plugin-catalogue-publishing`: the generator writes the tier from the jar, runs the tier check, and keeps the tier of a
  released version fixed.
- `support-policy`: `SUPPORT.md` describes the four tiers and ties Core and Official support to the release lines it lists.

## Impact

- **Type and scope.** The runtime change ships under `feat(plugin)`. The change also contains `ci(plugin)`, `feat(ui)`,
  `docs(gitbook)` and `docs(support)` commits (decision 1). The pull request titles are the ones listed in `tasks.md`. The
  proposal pull request is:

  `docs(openspec): propose add-plugin-support-tiers`

- **Breaking marker.** No breaking change. The tier is an optional annotation element with a default that equals the current
  behaviour, and an optional catalogue field. No environment variable, configuration key, volume, manifest, image or plugin that an
  existing installation uses has to change. The existing startup log line is unchanged byte for byte (decision 6), and the tier
  information is one additional line, so an operator who parses the existing line sees no difference. No plugin is refused, skipped
  or started in a different order because of its tier.
- **Binary surface.** One new public enum and one new annotation element in `com.reposilite.plugin.api`. The plugin API dump of
  `plugin-abi-gate` reports both as additions. Nothing is removed or changed.
- **Dependencies.** The change depends on nothing that is not on `main`, except the four pending plugin changes, which depend on
  it and not the other way round. The release-branch change `add-release-branches-and-backports` is parallel. This change refers
  to the release lines that `SUPPORT.md` lists, and it does not depend on how those lines are branched.
- **Order.** The code pull requests are listed in `tasks.md` in the order that keeps each of them buildable and passing alone.

## Verification

- Every unit test runs without network, database or system clock, with an injected `Clock` where time is involved.
- The tier resolution is tested as a pure function with a table of origin, declared tier and expected tier.
- The start log is tested with a captured appender, not by reading console output.
- The tier check task has its own tests against fixture modules in temporary directories: a module with an experimental opt-in
  and no declaration fails, an Official declaration without a contract test fails, and a Community module passes.
- `openspec validate add-plugin-support-tiers --strict --no-interactive` passes, and
  `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-plugin-support-tiers` prints nothing.
