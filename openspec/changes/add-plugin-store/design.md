# Design

This design follows the answers in the open questions of `proposal.md`. Where a decision depends on one of them, the question
number is named.

## Context

- The loader reads the plugins directory first, then the bundled jars behind switches
  (`reposilite-backend/src/main/kotlin/com/reposilite/plugin/PluginLoader.kt`). Each plugins-directory jar set is loaded in one
  `URLClassLoader` whose parent is the server class loader. A manual jar wins over a bundled jar with the same id, and the
  duplicate check is already there (`loadBundledPlugins`, the `containsKey` test).
- `plugin.initialize()` runs after every plugin is loaded, and an exception there stops the start today. The store has to change
  that for its own plugins only (D5).
- Shared settings are one JSON document with one key per domain, stored in the database by default and refreshed every 10 seconds
  (`docs/gitbook/reference/settings-domains.md`). The settings API uses `ETag` and `If-Match`. Unknown domains read as empty.
- Local configuration keys are camel case (`searchEngine`, `hideSettingsSecrets`), and their environment spelling is
  `INGOT_LOCAL_<KEY>`. The maintainers decided that the plugin list uses `INGOT_PLUGINS` instead. The local key is `plugins`.
- Plugin routes below `/api/plugins/<plugin>/` are reserved for the data routes of `add-plugin-ui-extension-point`.
- Outbound connections of Ingot, including proxy repositories, use the JVM default `SSLContext` and the JVM proxy properties.
  The internal CA bundle of `add-image-extra-ca-bundle` extends that context at start.
- Bouncy Castle `bcprov` and `bcpkix` are already dependencies of `reposilite-backend`. OpenPGP support needs `bcpg`.

## Decisions

### D1: two selections, one effective set

The effective set is the union of two lists.

| Source | Where | Who changes it | Survives a restart |
|---|---|---|---|
| Local | `INGOT_PLUGINS`, or the local key `plugins` in `configuration.cdn` | the operator, through configuration (GitOps friendly) | yes |
| Shared | the shared settings domain `plugins` in the database | an instance administrator, in the dashboard | yes, and every replica sees it |

Rules:

- An entry is a coordinate `groupId:artifactId:version`. Blank entries are ignored. Anything else is refused at start with a message
  that names the variable and the bad value, and the store loads nothing from the list (it does not guess).
- The same coordinate in both lists is one entry. The same `groupId:artifactId` with two versions is a conflict: the local version
  wins, the dashboard shows the shared one as overridden, and the dashboard cannot remove a local entry (it is shown as "set in
  configuration").
- The shared domain has one field, `selected`, a list of coordinates. Its JSON schema is published like any other domain, so
  `GET /api/settings/schema/plugins` works. A domain that is missing reads as an empty selection.
- The selection is written with `PUT /api/settings/domain/plugins` and `If-Match`. The store page uses the same call, so the
  optimistic concurrency and the audit trail of settings writes apply without a second write path.

Why not one list only: a local-only list needs a rollout for each change, which the dashboard is meant to avoid in a cluster. A
database-only list cannot be reviewed in git. Both together cover both operators.

### D2: sources and trusted keys are local and read-only

`INGOT_PLUGIN_SOURCES` is a comma-separated list of base URLs, in order. `INGOT_PLUGIN_TRUSTED_KEYS` is a file or a directory of
ASCII-armoured OpenPGP public keys, read once at start. Both are local configuration, shown read-only in the dashboard with their
fingerprints.

The reason is the trust boundary. A source decides which bytes are offered as plugins, and a trusted key decides which bytes are
accepted. If an instance administrator could write either into the database, a single compromised administrator account would turn
the store into an arbitrary download and an arbitrary loader. Keeping them local (question 7) means changing them needs the
configuration path, which is the one an operator reviews.

Source order decides ties. When two sources list the same plugin id, the first source in the list wins and the other entry is shown
as shadowed with its source name. A duplicate is never merged.

### D3: the catalogue and the Maven layout

Every source is a Maven repository. A plugin is an ordinary artifact at its standard path:

```
<base>/net/onelitefeather/ingot/opensearch-plugin/1.0.0/opensearch-plugin-1.0.0.jar
<base>/net/onelitefeather/ingot/opensearch-plugin/1.0.0/opensearch-plugin-1.0.0.jar.asc
<base>/ingot-plugin-catalogue.json
<base>/ingot-plugin-catalogue.json.asc
```

The catalogue is the authority. Its format is version 1:

```json
{
  "schema": 1,
  "generated": "2026-11-01T00:00:00Z",
  "plugins": [
    {
      "id": "opensearch-plugin",
      "name": "OpenSearch search backend",
      "description": "Answers artifact search from an OpenSearch cluster.",
      "publisher": "<official-publisher-name>",
      "coordinates": "net.onelitefeather.ingot:opensearch-plugin:1.0.0",
      "sha256": "<64 hex characters>",
      "compatibility": { "ingot": { "min": "1.2.0", "below": "2.0.0" }, "pluginApi": 1 },
      "yanked": false
    }
  ]
}
```

Rules:

- `schema` must be `1`. Any other value refuses the whole catalogue with a reason; unknown fields are ignored, so version 1 readers
  accept later additions.
- `id` must equal the `@Plugin` name of the loaded class. A mismatch refuses the jar, because the id is the key of precedence and of
  the audit trail.
- The `sha256` is the authority for the jar. The `.sha256` sidecar that Maven tools may publish is not read, because it sits next to
  the jar and is not signed on its own.
- `yanked: true` keeps the entry visible and refuses new selections. A yanked jar that is already cached still loads, so a yank does
  not break a running cluster. The status says `yanked`.
- An entry without `compatibility` is refused (question 4).

An Ingot instance can be a source. Its Maven repository serves the catalogue and the jars as files. The extension allow-list of a
hosted or mirror repository must include `.json` and `.asc`, and the how-to page says so. An air-gapped operator mirrors the official
repository into an internal Ingot and points `INGOT_PLUGIN_SOURCES` at it; the signatures still come from the publisher's key, so the
mirror is not trusted to vouch for itself.

### D4: signatures, PGP by default (question 1)

Default: OpenPGP detached signatures, verified with Bouncy Castle `bcpg`.

| Criterion | OpenPGP `.asc` | Sigstore / cosign bundle |
|---|---|---|
| Already used in Maven repositories | yes, the convention of Maven Central and the mirror code | not in Maven repositories, bundles are a separate file |
| Verification offline | yes, with a pinned public key in a file | needs the Sigstore trusted root, refreshed from the internet or pinned by hand |
| Identity model | a key the operator pins | a keyless identity (OIDC subject) plus a transparency log entry |
| Library cost | one module (`bcpg`), already in the same family as the existing dependencies | a Sigstore client library, a larger dependency with its own trust root handling |
| Revocation and rotation | the operator replaces the key file | the trusted root and the identity rules change |
| Fit with an air-gapped operator | good | weaker, the trusted root must be carried in |

PGP is the default because the operator, not a third party, decides which key to trust, and the check works without any network.
Sigstore gives stronger provenance for CI-built artifacts, and the release side (`publish-plugin-catalogue`) can add an attestation
later without changing the verification of the store. If the maintainers pick Sigstore (question 1), the verification step is the
only part that changes, and the catalogue format gains a `bundle` field.

Verification of one jar, in this order, and any failure refuses the jar with its step name:

1. The catalogue verifies against a trusted key (`ingot-plugin-catalogue.json.asc`), unless `INGOT_PLUGIN_ALLOW_UNSIGNED=true`.
2. The entry exists, is not refused by its compatibility block, and its `sha256` is the value of the downloaded file.
3. The jar's `.asc` verifies the jar bytes against a trusted key. The key fingerprint is recorded.
4. The jar opens as a jar and its `@Plugin` name equals the `id` (D3).

A key listed in `INGOT_PLUGIN_TRUSTED_KEYS` with an expired subkey refuses the signature and says so.

### D5: startup sequence and failure isolation (question 2)

`PluginLoader.initialize()` gains one step between the plugins directory and the bundled jars:

1. The plugins directory loads, unchanged.
2. The store resolves the effective set (D1). For each entry it checks the local cache first (D7), then the sources, within a
   startup budget of 60 seconds for the whole store. Entries that do not finish inside the budget are skipped as `UNAVAILABLE`.
3. Verified store jars load into one `URLClassLoader`, the same way as the plugins directory.
4. The bundled jars load last, unchanged. A bundled jar with an id that a store plugin already has is skipped as today.

Failure rules:

- A failure in steps 2 or 3 (download, verification, compatibility, a missing or unreadable file) skips that one entry, records the
  reason and the step name, writes an audit entry, and lets the server start.
- A plugin whose `initialize` throws is recorded as `FAILED` and the server starts. This is the one place where the store departs
  from the current rule that a failing initialise stops the start. Known limit: routes the plugin registered before the exception stay
  until the next restart. The page says so.
- No retry loop runs at startup. Retries happen at the next start, or when the operator presses "Check again", which downloads and
  verifies into the cache and marks a restart as pending. Nothing loads without a restart.
- Recovery when the selection itself breaks the server: `INGOT_PLUGIN_STORE_DISABLED=true` skips the store entirely for one start,
  so the operator reaches the dashboard, removes the entry, and starts again. The variable is documented as a recovery switch, not a
  normal setting.

### D6: precedence (question 3)

For one plugin id, the order is manual jar, store plugin, bundled jar. The implementation keeps the existing rule that the first
registration of an id wins and later ones are skipped, and it adds the reason. A store plugin whose id is taken by a manual jar is
recorded as `SHADOWED` with "a jar in the plugins directory has the same id", and the dashboard shows both. A bundled jar taken by a
store plugin is recorded as `SHADOWED` too, which is what happens to the bundled OpenSearch jar when the store selects the same
plugin: the store copy has the same content, so the behaviour is the same and the status explains it.

The reason for this order is that a manual jar is a decision the operator made on the host, the most explicit of the three, and the
dashboard must not override it silently.

### D7: cache layout, offline start and pruning

Verified jars live in `<data>/plugin-cache/<groupId path>/<artifactId>/<version>/` with three files: the jar, its `.asc`, and
`record.json` (the sha256, the key fingerprint, the source name and the verification time from the injected `Clock`). Every file is
written to a temporary name in the same directory and moved into place atomically, so a crash never leaves a half-written jar that
looks verified.

On start, a cached entry is used when:

- the local jar's SHA-256 equals `record.json`;
- its `.asc` verifies against a currently trusted key (so rotating a key removes the trust of older cached jars, by design);
- the entry is not refused by the compatibility rule of the running Ingot version.

This check needs no network, so an offline start with a verified cache loads the plugin. A yanked entry still loads from the cache
(D3). Cached entries that are no longer in the effective set are removed on the next start, after the store has loaded.

The cache is local to each replica and is never shared. Replicas do not need a shared filesystem, and each one verifies its own copy.

### D8: compatibility (question 4)

Two checks, both strict:

- `compatibility.ingot`: `min` is inclusive and `below` is exclusive, compared as dotted numeric versions of the running Ingot
  version. A version outside the range refuses the entry with "Ingot X is outside [min, below)".
- `compatibility.pluginApi`: an integer defined by this change. It is `1` today and moves only when the plugin API changes in a way
  that breaks existing plugins. `gate-plugin-abi` may later own the ABI check, and then this field is checked against that value.

The dashboard disables the selection toggle for an incompatible entry and says why, so an operator never has to find the refusal in
the log. An entry without a `compatibility` block is refused at parse time.

### D9: restart pending and replicas (question 6)

Each replica records, at start and on each refresh of the shared settings, the effective set it loaded, as a list of coordinate and
sha256 per plugin, in a per-replica row of the database. The same database that holds the settings holds these rows, so the status
of every replica is one read.

Two states are computed from it:

- `restart pending` on a replica when its loaded list differs from the current effective set. The dashboard shows a banner with the
  replicas it applies to.
- `replicas differ` when two replicas with a recent row report different loaded lists. The dashboard names the replicas and warns.
  It does not block a selection change, because the store is designed to converge after a rolling restart.

A replica whose row is older than three minutes is shown as stale, not as failed.

### D10: permissions, audit, read-only mode

- Every `/api/plugin-store` route and the `plugins` domain require the full instance administrator (`*`). The dashboard page checks
  the same node before it draws anything, and the server filters nothing else because the page has no partial view.
- Audit entries: a changed selection (with the added and removed coordinates, never a secret), a refused download (source, coordinate,
  step), a failed verification (step, key fingerprint if known), a load with an unverified jar (only with the switch on), a skipped
  plugin (reason), and a refresh requested from the dashboard. The names follow `docs/gitbook/reference/audit-events.md`.
- Read-only mode (`add-read-only-mode`): the selection cannot change and the refresh action is refused with the read-only problem
  body. Startup loading is unaffected, because it writes only the plugin cache under the data directory, never repository content or
  shared configuration.

### D11: network

The store uses the same outbound client as the proxy repositories (`docs/gitbook/reference/outbound-connections.md`). So the JVM
proxy properties from `JAVA_OPTS` and the internal CA bundle of `INGOT_EXTRA_CA_CERTS` apply, and no store-specific proxy setting
exists.

Limits: `https` sources only; 30 seconds connect and read timeouts; the catalogue is refused above 5 MiB, a jar above 64 MiB, a
signature above 64 KiB; redirects must stay on `https`. An operator who needs plain HTTP inside a network uses an internal CA, not a
plain source.

### D12: REST API and OpenAPI

| Route | Purpose |
|---|---|
| `GET /api/plugin-store/status` | The effective set, every entry with its state (`LOADED`, `CACHED`, `SHADOWED`, `REFUSED`, `UNAVAILABLE`, `FAILED`, `UNVERIFIED`, `YANKED`), the reason, the source and the fingerprint, the replicas, and `restartPending`. |
| `GET /api/plugin-store/catalogue` | The merged catalogue of all sources in order, with the compatibility result and the selection state of each entry. |
| `POST /api/plugin-store/refresh` | Re-reads the sources, verifies and caches the effective set, and marks a restart as pending when something changed. It never loads a plugin. |

The selection is `GET` and `PUT /api/settings/domain/plugins` (D1). Every route is documented in the OpenAPI description with its
response schema and its problem bodies, and the change adds only paths and schemas, so the OpenAPI diff gate reports additions.

### D13: dashboard

One first-party page, "Plugin store", in the administration tree, group System (`restructure-navigation`). It is not a plugin-UI page
and it does not depend on `add-plugin-ui-extension-point`. Sections, top to bottom:

1. A banner when a restart is pending on any replica, with the replicas named.
2. The status of each effective entry, with its reason.
3. The catalogue table: name, publisher, version, compatibility, state, and a toggle that selects or deselects. A selection asks for
   confirmation and says that a restart is needed.
4. Sources and trusted keys, read-only, with their fingerprints.

The page is drawn only for the instance administrator. It uses the existing dashboard components and sends no script, so the browser
policy of `add-plugin-ui-extension-point` and the split image stays unchanged.

### D14: documentation

- `docs/gitbook/how-to/install-plugins-from-the-store.md`: choosing a plugin, the restart, reading the status, recovery with the
  kill switch.
- `docs/gitbook/how-to/run-a-plugin-catalogue-mirror.md`: mirroring a source into an Ingot instance, the extension list, the trusted
  key, and the air-gapped case.
- `docs/gitbook/reference/plugin-store.md`: every variable with its fallback, the catalogue format, the states, the routes and the
  audit names.
- `docs/gitbook/reference/environment-variables.md`, `configuration-and-startup.md`, `settings-domains.md` (the `plugins` domain),
  `audit-events.md`, `dashboard-pages.md`, `upgrade-changes.md` (new variables, no breaking change), `install-a-plugin.md` (a link to
  the store) and `SUMMARY.md`.

## Testing

Every test follows F.I.R.S.T.: the `Clock` is injected, temporary directories are per test, the test key pair is a committed fixture
that no production configuration trusts, the loopback server uses an ephemeral port, and no test sleeps. Unit tests cover the parser,
the compatibility rule, the precedence, the cache record and the state computation. Integration tests cover the offline start, an
unreachable source, a source served by Ingot and the read-only refusals. Frontend tests cover the node filter and the restart banner.
