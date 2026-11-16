---
description: The four plugin tiers of Ingot (Core, Official, Community and Experimental), the support each one receives, the evidence a tier needs, how the effective tier of a loaded plugin is computed, and where the tier is shown.
---

# Plugin tiers

Every plugin that Ingot loads has one tier. The tier tells you what the OneLiteFeather maintainers stand behind, and what may change
under a plugin. It is information and a warning. A tier never stops a plugin from loading, and no feature depends on it. A plugin
built for Reposilite loads with the tier Community, as it did before.

The support rules are the ones of [SUPPORT.md](https://github.com/OneLiteFeatherNET/ingot/blob/main/SUPPORT.md). Ingot offers
community support only. There is no paid tier and no service level agreement, and no fix date is promised for any tier.

## The four tiers

| Tier | What it means | Support | Evidence the tier needs |
|---|---|---|---|
| [Core](#core) | Built into the server, or bundled in the image | Fixed on the supported release lines, on a best effort basis | The jar is one of the jars the image copies into `/app/bundled-plugins`, or the plugin is part of the server |
| [Official](#official) | Maintained and tested by the OneLiteFeather maintainers | Fixed on the supported release lines, on a best effort community basis | A contract test source set, and a CI job that runs the plugin |
| [Community](#community) | Any other plugin, including one maintained with the server that has no contract test yet | Community support through GitHub Discussions only | None beyond the plugin ABI check |
| [Experimental](#experimental) | A plugin that builds on an API that may still change | No promise about the API. Fixed on the same best effort basis as any other plugin | The plugin opts in to an `@ExperimentalIngot*` API |

### Core

Core plugins are the server and the plugins that ship with it. The server includes its built-in plugins, and the image bundles a
few jars in `/app/bundled-plugins`. Core plugins are fixed on the release lines that [SUPPORT.md](https://github.com/OneLiteFeatherNET/ingot/blob/main/SUPPORT.md)
lists as supported, not only on the latest release.

### Official

Official plugins are maintained and tested by the OneLiteFeather maintainers, and fixed on the supported release lines, on a best
effort community basis. A plugin can only be Official when the repository holds the evidence: a contract test source set for the
plugin, and a CI job that runs the plugin by its module name. Official does not mean paid support.

### Community

Community plugins are every plugin that does not meet the rules of another tier. A plugin that is maintained with the server but
has no contract test yet is Community. Community plugins receive community support only, through
[GitHub Discussions](https://github.com/OneLiteFeatherNET/ingot/discussions). A plugin that does not declare a tier is Community.

### Experimental

Experimental plugins opt in to an API that is marked as experimental, such as `ExperimentalIngotSearchApi` or
`ExperimentalIngotStorageApi`. Their API may change or be removed without a major release, so Experimental carries no promise about
the API. The loader logs one warning line for each Experimental plugin at every start. An Experimental plugin keeps this tier while
the API is experimental, whatever its shipping status.

## Effective tier of a loaded plugin

The loader computes one effective tier for each plugin. The origin of the plugin comes first.

| Origin of the plugin | Declared tier | Effective tier |
|---|---|---|
| Built into the server, or a jar of `/app/bundled-plugins` | Any | Core, except Experimental when the plugin declares Experimental |
| A jar of the plugins directory or of the plugin store | Core | Community. The start logs a warning that names the plugin |
| A jar of the plugins directory or of the plugin store | Community, Official or Experimental | The declared tier |
| Any origin, without a declaration | (default Community) | Community |

A jar from outside the image cannot be Core. A jar from outside the image can still declare Official in its own file. The
effective tier shows that claim as the jar's declaration. Only the signed plugin catalogue of OneLiteFeather is evidence of a tier.

## Where the tier is shown

- **Startup log.** The existing line that lists the loaded plugins stays byte for byte as it was. One additional INFO line follows
  it, with each plugin and its tier, for example `checksum (official)`. Each Experimental plugin also logs one WARN line.
- **System information.** The plugin list shows the tier next to the version of each plugin.
- **Support bundle.** The file `plugins.json` lists the tier of each plugin.
- **Plugin store.** A catalogue entry carries the tier of its jar. A missing tier reads as Community.

## Plugins of this release

| Plugin | Tier | Where it lives |
|---|---|---|
| Built-in plugins of the server | Core | In `ingot.jar` |
| `prometheus-plugin` | Core | Bundled in the image, switch `INGOT_METRICS_ENABLED` |
| `nexus-import-plugin` | Core | Bundled in the image, switch `INGOT_NEXUS_IMPORT_ENABLED` |
| `opensearch-plugin` | Experimental | Bundled in the image, switch `INGOT_OPENSEARCH_ENABLED`, off by default |
| `elasticsearch-plugin` | Experimental | Separate jar, not in the image |
| `meilisearch-plugin` | Experimental | Separate jar, not in the image |
| `checksum-plugin` | Official | Separate jar, not in the image |
| `migration-plugin` | Official | Separate jar, not in the image |
| `swagger-plugin` | Community | Separate jar, not in the image |
| `groovy-plugin` | Community | Separate jar, not in the image |
| `example-plugin` | Community | Template for plugin authors, not installed by operators |

A plugin moves to another tier in a new version of the plugin. The tier of a released version does not change.
