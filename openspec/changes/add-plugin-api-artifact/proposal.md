# Proposal

## Why

A plugin author compiles against `net.onelitefeather.ingot:ingot`, the full server bundle. The plugin API reference says that
repository requires credentials for reads as well (`docs/gitbook/references/plugins/plugin-api.md`). A third party who wants to
write a plugin therefore needs an account, downloads the whole server to compile a handful of interfaces, and sees every internal
class of the server, including the ones it must not depend on.

Two plugins planned in the portal proposals (`add-gradle-plugin-portal-plugin`, `add-minecraft-plugin-portal-plugin`) are meant to
live in their own repositories and to be built only against published artifacts. They cannot start while the only published
artifact is the server bundle. Nothing in the monorepo shows a plugin built from a published artifact today: the example plugin
compiles with `compileOnly(project(":reposilite-backend"))`.

## What Changes

- Publish a second Maven artifact `net.onelitefeather.ingot:ingot-plugin-api`, built from the same sources as the bundle and
  containing only the classes that make up the plugin API. The set is an explicit allowlist of classes, not a list of packages,
  because a package prefix would also carry internal classes of the same package (design D1).
- Add the marker annotation `@ExperimentalIngotPluginApi` in `net.onelitefeather.ingot.plugin`. Later changes use it for new
  plugin-facing types that may still change.
- Fail the build when a class of the slim artifact references a class that is neither in the artifact nor on the allowlist, so
  that the artifact can never be compiled against and then fail at runtime with a missing class (design D2).
- Add a consumer fixture that compiles a sample plugin against the slim artifact alone, with no project dependency, so the
  claim "built only against the published API" is checked on every change.
- Document both coordinates in the plugin API reference and state which one a plugin uses.

Operators change nothing. The `ingot` bundle keeps its coordinates and its contents, and the existing example plugin keeps
compiling against the project. The slim artifact is a second publication, not a replacement.

### Out of scope

- Any new plugin-facing type beyond the marker. Enrichment, attributes, facets and access checks are separate changes that each
  add their own package to the allowlist.
- Changing the bundle, renaming a package, or moving any class of `com.reposilite.*`.
- Enforcing API compatibility between releases. The japicmp report and its gate are `extend-plugin-abi-gate`.
- Publishing to a public repository or Maven Central (open question Q1 in the design, settled 2026-11-17: the repository that already hosts `ingot`).
- Nightly publication of the slim artifact (open question Q3 in the design, settled 2026-11-17: release only).

## Capabilities

### New Capabilities

- `plugin-api-artifact`: the slim artifact, its allowlist, the closure check, the consumer fixture, the marker annotation and the
  release rule.

## Impact

- **Type and scope.** The change ships under `feat(plugin)`. Its commits are `build(plugin)`, `test(plugin)`, `ci(plugin)` and
  `docs(plugin)`, one type each. The pull request titles are:

  `feat(plugin): publish a slim plugin api artifact` (the change itself, carrying the artifact and the closure check)

  `ci(plugin): build and check the slim plugin api artifact` (the consumer job on pull requests and the release step)

  `docs(plugin): document the plugin api coordinates` (the reference page)

- **Breaking marker.** No breaking change. No configuration key, environment variable, volume, manifest, image or plugin has to
  change. The release gains one more published file.
- **Binary surface.** No class of the bundle changes. The new artifact is a subset of the existing classes, compiled from the same
  sources.
- **Order.** This is the first prerequisite. `add-artifact-enrichment-hook`, `add-search-document-attributes`,
  `add-search-attribute-facets` and `expose-plugin-access-checks` depend on it, because each of them adds a package to the
  allowlist and is checked by the consumer fixture. It does not depend on any other change.

## Verification

- `openspec validate add-plugin-api-artifact --strict --no-interactive` passes.
- `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-plugin-api-artifact` prints nothing.
- In the code pull requests: the closure check and the allowlist tests pass with fixture class files in a temporary directory, the
  consumer fixture compiles against the slim jar only, and the bundle task output is unchanged.
