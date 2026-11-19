# Proposal

## Why

The server builds two things at compile time, from its own classes only: the OpenAPI document served at `/openapi`, and the JSON
schemas of the settings domains. A plugin that loads from its own jar is in neither.

- **The API document.** `kapt` runs `openapi-annotation-processor` on the backend's main source set only. It writes
  `openapi-plugin/.index` and `openapi-plugin/openapi-default.json` into the server's classes. `JavalinConfiguration.configureOpenApi`
  registers Javalin's `OpenApiPlugin`, which reads that index through the server's class loader on the first request to `/openapi`.
  The `@OpenApi` routes of a plugin jar never reach the served document, the Swagger UI or the committed snapshot
  `docs/openapi/ingot-openapi.json`.
- **The settings schema.** The same processor writes `json-schemes/index` and one file `json-schemes/<class name>` per `@JsonSchema`
  class. `SharedConfigurationFacade` reads them with `JsonSchemaLoader`, which sees only the server's index. A domain of a plugin jar logs
  `Cannot find scheme for class ..., scheme has to be generated at runtime` (`SharedConfigurationFacade.kt`, line 156) on every start
  and is served a schema from the runtime generator, which differs from the compile-time one.

That was harmless while every documented route and every settings domain lived in the server. It is not harmless once a core feature
moves into an embedded plugin (`add-embedded-plugin-jars`). `extract-cleanup-plugin` and `extract-javadoc-plugin` record this as
decision D13: after the move an existing installation would see a different API document and a new warning on every start, which breaks
the drop-in contract. D13 names this change as the owner of the shared server part, because it is generic to every plugin jar and
neither extraction owns it. Its merge on main is a gate of both move pull requests.

## Premise check

- `JsonSchemaLoader` comes from the Javalin OpenAPI library and calls
  `JsonSchemaLoader::class.java.getResourceAsStream("/json-schemes/index")`. It takes no class loader, so the server cannot ask it to
  read a plugin jar. The plugin lookup is a second step in `SharedConfigurationFacade`, not a change of the library.
- The test source sets do not run the processor (`reposilite-backend/build.gradle.kts`, the exclusion of `kaptTest`, `kaptIntegration`
  and `kaptTestFixtures`), and the empty `json-schemes/index` of the test resources keeps the tests on the runtime generator. The change
  must not move them off it.
- `OpenApiSnapshotIntegrationTest` sorts the keys of the served document before it compares it with the snapshot. Where a merged entry
  sits in the served document therefore does not change the snapshot, only whether it is there.
- No plugin ships a fragment today. Every existing plugin of the plugins directory, from Reposilite or from Ingot, keeps exactly the
  behaviour it has now.

## Type and scope

- Conventional Commits type and scope: `feat(plugin)`. The server learns to read what a plugin jar carries, which is a new capability
  of the plugin system and changes nothing an operator already uses.
- The proposal pull request is `docs(openspec): propose add-plugin-api-fragments`.
- The code ships as three pull requests, in this order. Inside a pull request the commits have one type each (a failing test is
  `test(plugin)`, the Gradle step is `build(plugin)`, the change is `feat(plugin)`, the plugin author page is `docs(plugin)`), and
  squash-merging makes the pull request title the one commit on main.
  1. `feat(plugin): merge the api document fragments of loaded plugins`
  2. `feat(plugin): read the settings schema of a plugin from its own jar`
  3. `build(plugin): generate the api document fragment and settings schema of a plugin module`

  Pull request 3 is a build step only, so it carries the `build(plugin)` title, as the build hook of `add-embedded-plugin-jars` did
  (maintainer decision 6 of that change). Pull requests 1 and 2 are the gate of the two extractions and do not wait for it.

## What Changes

- **Fragment merge.** When the served document is built, a new step of the definition processor of `configureOpenApi` reads
  `META-INF/ingot/openapi/<plugin id>.json` for every registered plugin id, through the class loader of that plugin's class, and adds
  the fragment's `paths` and `components.schemas` to the server document. Plugins are merged in ascending order of their id. A key that
  is already present is not replaced: the server wins over every plugin, and a plugin with a lower id wins over a higher one. Each
  conflict logs one WARN line naming the plugin and the key. A fragment that does not parse, or whose `paths` or `components.schemas`
  is not an object, is skipped with one WARN line naming the plugin. The merge never fails the start and never fails the request.
  It runs before the existing processors (extensible enums, the second factor alternative, the login challenge, the security schemes),
  so they apply to plugin entries too.
- **Schema lookup in the plugin jar.** For a settings domain whose type is not in the server's index and whose class loader is not the
  server's, `SharedConfigurationFacade` reads `json-schemes/<type name>` through `type.classLoader`. Only when that finds nothing does it
  log today's warning and generate the schema at runtime. Types on the server's class loader keep today's path exactly.
- **Build step for a plugin module.** A function in `buildSrc` (`ingot.plugin.PluginApiFragments`) that a plugin module calls with its
  plugin id. It adds `openapi-annotation-processor` to the module's `kapt`, moves the generated `openapi-plugin/openapi-default.json` to
  `META-INF/ingot/openapi/<plugin id>.json`, and leaves the generated `openapi-plugin/.index` and `json-schemes/index` out of the
  module's classes and jar, so the server's indexes stay the only ones on any classpath, including the flat integration classpath. The
  `json-schemes/<class name>` files keep their names. A check task fails the module's build when its jar carries either index file or a
  fragment under another id.
- **Drop-in.** With no plugin fragment the merge step returns the document it was given, so the served document is byte-identical to
  today, and `OpenApiSnapshotIntegrationTest` passes without a refresh. The committed snapshot stays the full merged document: when a
  feature moves into an embedded plugin, its routes stay in the snapshot.
- **Third-party plugins.** Any plugin jar, embedded or in the plugins directory, takes part by shipping the files. Shipping them is the
  opt-in, so a plugin that ships nothing is not affected.
- **Plugin author page.** `docs/gitbook/manage/plugins/write-a-plugin.md` gains a section on documenting routes and settings of a
  plugin, with the build step for modules of this repository and the plain Gradle lines for a third-party build.

### Out of scope

- Applying the build step to `cleanup-plugin` or `javadoc-plugin`. Each extraction applies it in its own module (task 2.6 of
  `extract-cleanup-plugin`, the matching task of `extract-javadoc-plugin`).
- Merging anything other than `paths` and `components.schemas`: top-level `tags`, `servers`, `security`, `info` and
  `components.securitySchemes` stay the server's alone.
- Removing or rewriting a server entry from a plugin. A plugin can add, never replace.
- Changing `JsonSchemaLoader` or the processor, and changing the runtime generator.
- A Gradle plugin published for third-party authors. The step is `buildSrc` of this repository.
- A new public type, annotation or interface of the plugin API (see "Plugin API" below).
- Packages `com.reposilite.journalist` and `org.panda_lang.*`, as set out in `CLAUDE.md`.

## Plugin API

No runtime type becomes public. The server reads the two files by a fixed path, so a plugin author needs no class, only the file layout.
The new runtime classes live in `net.onelitefeather.ingot.plugin.openapi` and `net.onelitefeather.ingot.plugin.schema` and are Kotlin
`internal`, so the plugin ABI gate reports no change and `reposilite-backend/plugin-api-classes.txt` is not touched. The file layout
(`META-INF/ingot/openapi/<plugin id>.json`, `json-schemes/<class name>`) is documented on the plugin API page as experimental, in the
same words as `@ExperimentalIngotPluginApi`. If a later change makes a runtime type public, it goes to `net.onelitefeather.ingot.*`,
carries `@ExperimentalIngotPluginApi` and gets an entry in `reposilite-backend/plugin-api-classes.txt`.

## Operator impact

No BREAKING item. No environment variable, configuration key, volume, manifest, plugin list or image reference changes. With no plugin
fragment the served document, the Swagger UI and the settings schemas are byte-identical to today, and the start log has no new line.
An installation with a plugin that ships a fragment sees that plugin's routes in the API document, and the settings page of that plugin
gets its compile-time schema without the warning.

## Capabilities

### New Capabilities

- `plugin-api-fragments`: the fragment file of a plugin, the merge into the served document with its order, conflict and failure rules,
  the schema lookup in the plugin's own class loader, the build step of a plugin module, and the drop-in guarantee.

### Modified Capabilities

None. `openapi-document` keeps its requirements: every route the server registers stays documented, and the merge only adds entries.

## Impact

- **Modules.** `reposilite-backend` (`JavalinConfiguration`, `SharedConfigurationFacade`, two new internal classes) and `buildSrc`.
  No plugin module changes in this change.
- **Docs.** `docs/gitbook/manage/plugins/write-a-plugin.md` and `docs/gitbook/references/plugins/plugin-api.md`.
- **Order.** Follows `add-embedded-plugin-jars`. Pull requests 1 and 2 are the gate of the move pull requests of `extract-cleanup-plugin`
  (task 1a.1) and `extract-javadoc-plugin` (task 2a.1).

## Open questions for the maintainer

1. **Commit type of the build step.** Recommended default: its own pull request titled `build(plugin): ...`, as decision 6 of
   `add-embedded-plugin-jars`. The alternative is to fold it into pull request 1 under `feat(plugin)`, which makes that pull request mix
   a build change into the gate.
2. **Third-party plugins.** Recommended default: yes, every loaded plugin is merged when it ships a fragment, because the loader already
   treats all jars the same and the opt-in is the file itself. The alternative is to merge only plugins with origin `EMBEDDED` or
   `BUILT_IN`, which keeps third-party routes out of the document even when the author documents them.
3. **Conflict between two plugins.** Recommended default: the lower plugin id wins and one WARN line is logged, so the result does not
   depend on load order. The alternative is to drop both entries.
4. **Plugin id check.** Recommended default: the build step takes the id as an argument and does not read `@Plugin`. A mismatch shows up
   as a missing fragment, which the extraction guard tests catch. The alternative is a check that reads the annotation from the classes.

## Verification

- `npx -y @fission-ai/openspec@latest validate add-plugin-api-fragments --strict` passes.
- `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-plugin-api-fragments` prints nothing.
- After pull request 1, `OpenApiSnapshotIntegrationTest` passes without `-PrefreshOpenApiSnapshot`, and the bytes served at `/openapi`
  equal those of `origin/main` before the change.
- After pull request 2, the settings tests pass unedited, and a fixture plugin jar with its schema logs no `Cannot find scheme` line.
