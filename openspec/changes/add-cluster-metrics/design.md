# Design

## Context

Verified against origin/main:

- `PrometheusPlugin.kt` registers its routes, JVM metrics, Reposilite metrics and Jetty metrics in `initialize()`. The request
  timer is a `before` handler that stores `System.currentTimeMillis()` as a context attribute, and an `after` handler that
  observes the difference into `JettyMetrics.responseTimeSummary`. The `/metrics` path is skipped.
- `ReposiliteMetrics.register(...)` and `JettyMetrics.register(...)` register into `PrometheusRegistry.defaultRegistry` unless a
  registry is passed. Both already take the registry as a parameter, so tests can use a fresh registry per test.
- `PluginLoader.loadPluginsByServiceFiles()` scans only the plugins directory, builds one `URLClassLoader` for all jars there and
  calls `extensions.registerPlugin` for each service instance. `Extensions.registerPlugin` rejects a second plugin with the same
  name.
- `PluginLoader.initialize()` runs `plugin.initialize()` in dependency order. A plugin that is loaded but not registered is
  never initialised.
- The `Dockerfile` run stage copies only `ingot-*.jar` and creates `/app/data` as a volume. `/app/data/plugins` is where a
  plugin placed by an operator lands.

## Decisions

### D1. Bundled jar lives outside the data volume

The image puts the jar in `/app/bundled-plugins/prometheus-plugin.jar`. It must not go into `/app/data/plugins`, because that
directory is on the volume, survives image upgrades, and would silently keep an old copy of the plugin after an upgrade. The
path can be overridden with the system property `ingot.bundledPlugins` (no environment variable), which is what the tests use.

### D2. The loader reads the bundled directory only behind the switch

`PluginLoader` loads from `/app/bundled-plugins` after the plugins directory, and only when `INGOT_METRICS_ENABLED` is `true`
(fallback `REPOSILITE_METRICS_ENABLED`). The switch is checked at the point of loading, so an image without the switch behaves
exactly as before, including the set of classes that are loaded at start-up.

Alternative rejected: always loading the bundled plugin and letting the plugin itself stay silent without the switch. That
would still start a second class loader, read credentials and register routes. An opt-in should cost nothing when it is off.

### D3. Precedence by plugin id, checked before registration

A user's `prometheus-plugin.jar` in the plugins directory must win. The loader therefore reads the `@Plugin` annotation of each
bundled service instance first and skips it when `extensions.getPlugins()` already holds that name.

The roadmap assumed that a duplicate would register collectors twice. `Extensions.registerPlugin` shows instead that a duplicate id
throws, which stops the server during start-up. The skip is still needed, because the bundled copy must not stop a server that
already works. Task 2.1 proves the current behaviour with a test before the rule is written. If that test shows something other
than a start-up failure, this decision is revised before task 5.2.

Alternative rejected: deleting or renaming a user's jar. The loader does not write to the plugins directory.

### D4. Timing with an injected monotonic ticker

Request durations come from an injected monotonic ticker, a `LongSupplier` that returns nanoseconds. Production passes
`System::nanoTime`; tests pass a counter they advance. `java.time.Clock` has no monotonic nanosecond reading, so the ticker is
injected the same way a `Clock` would be, and the test never reads the system time. The wall clock is removed from the request path.

### D5. Gauges are callbacks, not pollers

Each operational value is registered as a `GaugeWithCallback` or `CounterWithCallback` that reads the owning facade when Prometheus
scrapes. No thread is added, the value cannot go stale, and a facade that is not configured (for example the cluster bus when no URL
is set) simply leaves its series out.

### D6. Labels come from fixed sets

Route labels are the matched route template. Result, outcome and job labels are enums from the code. This keeps the series count
bounded by the number of routes and jobs, not by the number of repositories, artifacts, users or clients.

## Risks

- Duplicate plugin load: low to medium until task 2.1 and task 5.1 pass. Mitigation: D3 and the spike.
- Image grows and the image contract changes: low. Mitigation: no change to the entrypoint, paths, user or volume, and the
  "Container images" job covers the upgrade rehearsal.
- Operators who set the switch without credentials see a start-up failure. This is opt-in and matches the existing plugin
  behaviour; the docs say so.
