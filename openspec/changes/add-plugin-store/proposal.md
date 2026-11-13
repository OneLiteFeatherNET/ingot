# Proposal

## Open maintainer questions

Each question carries the default this proposal uses. The change can be written and reviewed with the defaults; a different
answer changes only the lines named here.

1. **Signature mechanism.** Default: OpenPGP detached signatures (`.asc`), verified with Bouncy Castle against public keys the
   operator trusts. Reason: Maven repositories already publish `.asc` files, the mirror code of Ingot already handles them, and
   an operator can pin a public key in a file without any online trust root. Alternative: Sigstore or cosign bundles, compared in
   `design.md` (D4). Question: confirm PGP for version one.
2. **Failure at startup.** Default: a plugin that cannot be downloaded, verified, loaded or initialised is skipped and reported.
   The server starts, the dashboard and `GET /api/plugin-store/status` show the reason, and the store causes no restart loop.
   Known limit: a store plugin that fails inside `initialize` may already have registered some routes, and those stay until the
   next restart. Question: confirm skip-and-report over failing the start.
3. **Precedence for one plugin id.** Default: a jar the operator placed by hand in the plugins directory beats a store plugin,
   and a store plugin beats a bundled jar. A store plugin whose id is taken by a manual jar is listed as shadowed. Reason: the
   manual jar is the most explicit operator decision, and the bundled rule already exists. Question: confirm this order.
4. **Compatibility enforcement.** Default: each catalogue entry declares the Ingot versions it supports (`min` inclusive,
   `below` exclusive) and the store's `pluginApi` integer. The dashboard refuses to select an incompatible entry, startup skips an
   incompatible jar, and an entry without a compatibility block is refused. Question: confirm the strict refusal of an entry
   without a block.
5. **Official catalogue URL and signing key.** Placeholders only: `<official-repository-url>` and `<official-key-fingerprint>`.
   No URL is invented here. Default: this change configures no official source, so with no source configured nothing changes.
   Question: which host serves the official repository, which key signs, and who holds the private key (see
   `publish-plugin-catalogue`, question 1).
6. **Replicas with different plugin sets.** Default: each replica reports the set it loaded and whether it has a restart pending.
   The dashboard names the replicas that differ and warns, and it does not block a selection change. Question: confirm
   report-only, or refuse selection changes while replicas disagree.
7. **Where trusted keys live.** Default: in local configuration (`INGOT_PLUGIN_TRUSTED_KEYS`), not in the database. Reason: a
   dashboard write must never be able to add a trusted key or a source, and local configuration is the file a GitOps workflow
   controls. Question: confirm that sources and keys stay local and read-only in the dashboard.

## Why

An operator who runs Ingot for a team installs a plugin today by copying a jar onto each server and restarting it. In a cluster
every replica needs the same copy placed by hand, and nothing records which copy that is. The operator has no list to choose
from, cannot tell whether a plugin supports the Ingot version they run, and has no evidence that the jar they downloaded is the
one its publisher released. Plugin authors publish jars on release pages, each with its own habit of checksums, and the operator
has to trust whatever they find.

The bundled OpenSearch plugin shows the gap from the other side. It ships in every image, and switching it on is one environment
variable on every replica, with no record of who switched it and no way to see from the dashboard which replica runs it. A plugin
that the operator wants but that is not bundled has no such path at all.

The fix is a catalogue that lives where Maven artifacts already live, a selection that the instance administrator makes once and
every replica reads, and a rule that a jar is loaded only when its signature verifies. The operator keeps the last word. A jar
placed by hand keeps working untouched, nothing is downloaded when nothing is configured, and a source that is down does not stop
the server from starting.

## What Changes

- **Two ways to select plugins.** The local configuration key `plugins`, set through the environment variable `INGOT_PLUGINS`
  (fallback `REPOSILITE_PLUGINS`), takes a list of coordinates `groupId:artifactId:version`. The shared settings domain `plugins`
  holds the selection an instance administrator makes in the dashboard. Every replica reads both and loads the union, once at
  start. A change takes effect after a restart, and the dashboard says that a restart is pending. Nothing is hot loaded.
- **Catalogue in a Maven repository.** A source is a base URL of a Maven repository, served over `https`. The source holds the
  plugin jars as ordinary Maven artifacts with their `.asc` signatures, and one catalogue index file, `ingot-plugin-catalogue.json`,
  with its own `.asc`. The operator lists sources in `INGOT_PLUGIN_SOURCES` (fallback `REPOSILITE_PLUGIN_SOURCES`). An Ingot
  instance serving a mirror of the repository is a valid source, which covers air-gapped installations. The official source is a
  placeholder (see question 5).
- **Mandatory signatures.** A jar from the store is loaded only when its detached signature verifies against a key listed in
  `INGOT_PLUGIN_TRUSTED_KEYS`, and its SHA-256 equals the value in the catalogue. The catalogue itself must verify the same way.
  Unsigned content loads only when `INGOT_PLUGIN_ALLOW_UNSIGNED` is `true`, which logs a warning at every start and marks the
  plugin as unverified in the status.
- **Download, cache and offline start.** Verified jars are kept in a cache directory under the data directory,
  `<data>/plugin-cache`, never in the plugins directory. A verified cached jar loads when the source is down, after its signature
  and digest are checked again against the local copy.
- **Startup and precedence.** The plugins directory loads first, as today. Store plugins load next. Bundled jars load last, as
  today. For one plugin id the order is: a manual jar, then a store plugin, then a bundled jar. A plugin that cannot be obtained,
  verified or loaded is skipped and reported, and the server starts.
- **Compatibility.** Every catalogue entry declares the Ingot versions it works with and the store's `pluginApi` integer. The
  dashboard refuses to select an incompatible entry, and startup skips such a jar with the reason.
- **Dashboard page "Plugin store"** in the administration tree, group System. It shows the restart state, the replicas, the
  catalogue with the selection toggles, the sources and the trusted key fingerprints. Only the instance administrator sees it.
- **REST API** under `/api/plugin-store`: status, catalogue and a refresh action. The selection is written through the existing
  `/api/settings/domain/plugins`, with its optimistic concurrency. All routes are documented in OpenAPI.
- **Audit log** entries for a changed selection, a refused download, a failed verification, an unverified load and a skipped
  plugin.
- **Documentation** in `docs/gitbook`: a how-to to install plugins from the store, a how-to to run a catalogue mirror, a reference
  for every variable, the catalogue format and the status fields, and the settings domain page.

### Out of scope

- Hot loading and unloading of plugins. A restart remains the only point where the set changes.
- Sandboxing. A plugin still runs in the server process with full access, as `docs/gitbook/how-to/install-a-plugin.md` says.
  The store makes trust decisions about provenance, not about behaviour.
- Publishing plugins from the dashboard or uploading a plugin jar through the API.
- Running the official repository and its release pipeline. `publish-plugin-catalogue` covers the release side only.
- Reviews, ratings, categories or search over the catalogue beyond name and description.
- Migrating plugin jars that operators already placed by hand into the store. They keep working as they are.
- A plugin dependency resolver. The loader's existing dependency graph applies, and a missing dependency is reported.
- Changing the plugin API packages `com.reposilite.*`.

## Capabilities

### New Capabilities

- `plugin-store`: the two selections, the catalogue format and its sources, signature and digest verification, the cache and
  offline start, the startup order and failure isolation, compatibility, the restart and replica reporting, permissions, audit,
  read-only mode and the REST API.

## Impact

- **Type and scope.** The change ships under `feat(plugin)`. Its commits are `test(plugin)`, `feat(plugin)` and `docs(plugin)`.
  The pull request title is:

  `feat(plugin): install plugins from a catalogue in a maven repository`

- **Breaking marker.** No breaking change. Every new variable (`INGOT_PLUGINS`, `INGOT_PLUGIN_SOURCES`,
  `INGOT_PLUGIN_TRUSTED_KEYS`, `INGOT_PLUGIN_ALLOW_UNSIGNED`, `INGOT_PLUGIN_STORE_DISABLED`) is unset by default and has its
  `REPOSILITE_` spelling as fallback. With nothing configured the store does nothing: no network call, no cache directory, no
  log line above debug, and the plugins directory and the bundled jars behave exactly as before. A shared settings document
  without a `plugins` key reads as the defaults. The image, the volume, the manifest, the configuration file and every existing
  plugin keep working unchanged. No operator has to change anything.
- **Routes and settings.** New: `/api/plugin-store/*` and the shared settings domain `plugins`. Existing routes keep their
  answers. The route prefix `/api/plugins/<plugin>/` stays reserved for plugin data routes, which is why the store does not use
  `/api/plugins/store`.
- **Dependency.** `org.bouncycastle:bcpg` joins `reposilite-backend`, next to `bcprov` and `bcpkix` that are already there. It adds
  the OpenPGP support the signature check needs.
- **Binary surface.** The plugin API packages gain no member. The store lives in the server, under `net.onelitefeather.ingot.plugin`
  for the new classes, and it does not change `PluginLoader.BUNDLED_SWITCHES`.
- **Order.** No hard dependency on an in-flight change. The compatibility integer is defined here, and `gate-plugin-abi` may later
  own the ABI check of the plugin API. The page is first-party and uses the administration navigation (`restructure-navigation`),
  so it does not wait for `add-plugin-ui-extension-point`.

## Verification

- Unit tests of the catalogue parser, the coordinate parser, the compatibility rule, the precedence and the cache record, with a
  fixed `Clock`, temporary directories and a committed test key pair that no production configuration trusts.
- Loader tests of the startup order with manual, store and bundled jars that share an id, and of a failing download, a failing
  signature and a failing initialise, each of which must leave the server started.
- Integration tests against a loopback HTTP server on an ephemeral port, covering an offline start with a verified cache, an
  unreachable source, a mirror served by Ingot, and the read-only refusals.
- Dashboard tests of the page filter by node and of the restart banner.
- The OpenAPI diff gate passes with the new routes only as additions. The container job's upgrade rehearsal keeps passing with no
  variable set.
- `openspec validate add-plugin-store --strict` passes and
  `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-plugin-store` prints nothing.
