# Proposal

## Why

A plugin tells an operator its name and version, and since the tier change it tells them how far Ingot stands behind it. It
does not tell them what the plugin is for. An operator who finds `checksum` or `lucene` in the system information page has to
read the plugin's repository or documentation to learn whether it is the feature they need. The author knows that answer in
one sentence, and the annotation is the one place where the author already states the plugin's metadata.

The `@Plugin` annotation gets an optional `description`. It is read at start, kept with the other plugin metadata, and shown
where the plugins are listed. A plugin that does not set it looks exactly as it does today.

## What Changes

- **Annotation.** `com.reposilite.plugin.api.Plugin` gets one element, `String description() default ""`. A plugin compiled
  against Reposilite does not have the element, and the JVM reads the default from the annotation type, so the plugin reads
  with an empty description. No existing plugin needs a change to compile or load.
- **Normalisation.** The description is plain text. Leading and trailing whitespace is removed, and control characters are
  removed. A description longer than 280 characters is cut to 280 characters and the start logs one WARN line that names the
  plugin. A description is never a reason to refuse, skip or reorder a plugin.
- **Plugin registry.** The description of each registered plugin is available from the plugin registry, next to its name,
  version and tier. A plugin without a description reads as the empty text.
- **System information page.** The existing plugin list (name, version and tier) shows the description under the name when it
  is set. The page is read-only and gets no new control.
- **Support bundle.** `system-info.json` and `plugins.json` gain an optional `description` field per plugin. The field is
  written only when the description is not empty, so the output for a plugin without a description is unchanged.
- **Store catalogue.** An entry of `ingot-plugin-catalogue.json` may carry an optional `description`. The store reads it when
  present. A missing field reads as the empty text, so schema 1 stays.
- **GitBook.** The plugin API reference lists the element in its `@Plugin` table, with its limit and its normalisation, and the
  page on writing a plugin shows an example.
- **Commit type.** Every group ships under `feat(plugin)`. The frontend and documentation groups are part of the same feature
  and keep that type.

### Out of scope

- A new dashboard page or a plugin browser. The description appears in the existing lists only.
- Markup in the description. It is shown as text, never as HTML, Markdown or a link.
- Localisation. One description per plugin, in the language its author writes.
- Filtering or searching plugins by description. The search index does not read plugin metadata.
- Changing the generator of the store catalogue. The generator's writing of `description` from the jar is a follow-up when the
  maintainer asks for it. Until then the store reads the field only, and entries without it show no description.
- Any change to the plugin packages. They stay `com.reposilite.*`, and no existing element changes its name or default.
- Any change to `Plugin.name`, `version`, `dependencies` or `settings`.

## Capabilities

### New Capabilities

- `plugin-description`: the optional annotation element, its normalisation and limit, the plugin registry value, the places
  where the description is shown (system information page, support bundle and the store catalogue entry) and the rule that an
  absent description changes no output.

## Impact

- **Operators:** no configuration key, environment variable, volume, manifest, image or plugin change. An existing installation
  shows the same lists until a plugin sets a description. No operator action is required before or after the upgrade.
  Nothing here is BREAKING.
- **Plugin authors:** one optional element on `@Plugin`. A plugin built for Reposilite keeps compiling and loading unchanged.
- **Plugin API binary surface:** one new element with a default in `com.reposilite.plugin.api`. The plugin API dump of
  `extend-plugin-abi-gate` reports it as an addition. Nothing is removed or changed.
- **Code:** `reposilite-backend` (the annotation, the registry, the support information and the catalogue entry), and
  `reposilite-frontend` (the system information list). The GitBook pages named above.
- **Commits:** the change ships under `feat(plugin)`. The code pull requests are listed in `tasks.md`, each with its title.
  The proposal pull request is:

  `docs(openspec): propose add-plugin-description`

## Verification

- Every unit test runs without network, database or system clock. The normalisation is a pure function with a table of inputs.
- The start warning is tested with a captured appender, not by reading console output.
- The support information tests check that a plugin without a description produces the same JSON as before.
- `openspec validate add-plugin-description --strict` passes, and `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-plugin-description`
  prints nothing.
