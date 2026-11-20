---
description: Reference for the Ingot plugin API - coordinates, loading, lifecycle, the @Plugin annotation, events, facades, routes and frontend placeholders.
---

# Plugin API

This page lists the types a plugin uses. For a step-by-step build, see [Write a plugin](../../manage/plugins/write-a-plugin.md). For installing a jar, see [Install a plugin](../../manage/plugins/install-a-plugin.md).

## Coordinates and packages

| Item | Value |
| --- | --- |
| Maven coordinate | `net.onelitefeather.ingot:ingot:<version>`, the server bundle. Use `compileOnly`. |
| Plugin API coordinate | `net.onelitefeather.ingot:ingot-plugin-api:<version>`, the slim artifact with the plugin API types only. Use `compileOnly`. It is published with each release next to the bundle. |
| Release repository | `https://repo.onelitefeather.dev/onelitefeather-releases`. Requires credentials, also for reads. |
| Snapshot repository | `https://repo.onelitefeather.dev/onelitefeather-snapshots`, for versions that contain `SNAPSHOT`. |
| Journalist dependency | `https://maven.reposilite.com/releases`, group `com.reposilite`. The server depends on it, so plugin builds need this repository too. |
| Packages | `com.reposilite.*`. The plugin API is in `com.reposilite.plugin.api`. |
| Plugin directory | `plugins` in the working directory. Change it with `--plugin-directory` or `-pd`. |
| Java target | 17 |

Plugins written for Reposilite compile against Ingot unchanged, apart from the dependency coordinate.

A plugin picks one of the two coordinates. The slim artifact holds the classes listed in [plugin-api-classes.txt](https://github.com/OneLiteFeatherNET/ingot/blob/main/reposilite-backend/plugin-api-classes.txt), which are the new Ingot types of the plugin API. A plugin that uses only those classes, and the legacy classes that the server provides and that [plugin-api-provided.txt](https://github.com/OneLiteFeatherNET/ingot/blob/main/reposilite-backend/plugin-api-provided.txt) names (the `com.reposilite.plugin.api.*` base classes and the `com.reposilite.journalist` library), follows the rule of the plugin API. The slim artifact does not contain the legacy base classes, so a plugin that extends one of them, such as `ReposilitePlugin`, still compiles against the bundle, as it does today. The [consumer fixture](https://github.com/OneLiteFeatherNET/ingot/tree/main/reposilite-test/plugin-api-consumer) is a sample plugin that compiles against the slim artifact alone.

Existing plugins need no change. The bundle keeps its coordinate and its contents, and the slim artifact is an addition that a plugin can move to when it wants to.

## Loading and lifecycle

Ingot loads plugins in this order:

1. The loader scans the plugin directory for `.jar` files directly inside it. A file that does not open as a jar is skipped with an `Invalid JAR file` warning.
2. All accepted jars share one class loader. The server's own classes are its parent.
3. `ServiceLoader` reads `META-INF/services/com.reposilite.plugin.api.ReposilitePlugin` in each jar. Each class found is registered by its `@Plugin` name. Two plugins with the same name are an error. A class without `@Plugin` is an error.
4. `load(PluginLoader)` runs on every plugin. No order is guaranteed.
5. `initialize()` runs on every plugin, sorted by name, with plugins from `dependencies` first. A non-null return value is registered as a facade.
6. The server emits `ReposiliteInitializeEvent`, then `ReposilitePostInitializeEvent`.
7. The web server starts. The HTTP server events and `RoutingSetupEvent` are emitted during this step.
8. The server emits `ReposiliteStartedEvent`.

On shutdown, `ReposiliteDisposeEvent` is emitted before the web server stops.

## The @Plugin annotation

| Attribute | Type | Default | Meaning |
| --- | --- | --- | --- |
| `name` | String | none, required | Unique name of the plugin. Other plugins use it in `dependencies`. |
| `version` | String | the server version | Version of the plugin. |
| `description` | String | empty, which means no description | Short plain text on what the plugin is for. Control characters and line breaks are removed, and a text longer than 280 characters is cut with one warning at start. The text is never read as markup, and it never stops the plugin from loading. The plugin card on the system information page shows it under the plugin name. |
| `dependencies` | String array | empty | Names of plugins that must initialize first. |
| `settings` | class of `SharedSettings` | `SharedSettings` | Settings class owned by the plugin. Read it with `SharedConfigurationFacade.getDomainSettings<T>()`. |

### Embedded plugins and the version rule

A plugin jar that the server embeds sets `@Plugin.version` to the same value as the `Implementation-Version` of its manifest. The loader does not read the annotation for the version rule. It reads the manifest, and the default of the annotation is the server version, which would make an embedded plugin look newer than any plugin that is numbered on its own.

A jar of the plugins directory with the same `name` replaces the embedded plugin only when its manifest version is higher, as described in [Update a plugin](../../manage/plugins/install-a-plugin.md#update-a-plugin). A jar that loses the comparison is still created by the server, so its constructor runs at start, but `load` and `initialize` do not run for it. Keep constructors free of side effects, because they can run for a copy that is not used.

## The ReposilitePlugin base class

| Member | Purpose |
| --- | --- |
| `initialize(): Facade?` | Abstract. The main entry point. Register listeners and routes here. Return a facade for other plugins, or `null`. |
| `load(loader: PluginLoader)` | Optional. Runs before any `initialize()`. Use it only for work that does not depend on other plugins. |
| `extensions(): Extensions` | The registry of events and facades. |
| `getLogger(): Logger` | The plugin logger. In Kotlin, `logger`. |

## Events

Listeners run synchronously, on the thread that emitted the event. Listeners for the same event run in priority order: `Priorities.HIGH` (0.0), `Priorities.DEFAULT` (1.0), then `Priorities.LOW` (2.0). Lower values run first. Override `priority()` in an `EventListener` to change the order.

| Event (package) | Emitted | Carries |
| --- | --- | --- |
| `ReposiliteInitializeEvent` (`com.reposilite.plugin.api`) | Before the server starts. | `reposilite` |
| `ReposilitePostInitializeEvent` (`com.reposilite.plugin.api`) | After initialization, before the web server starts. | `reposilite` |
| `ReposiliteStartedEvent` (`com.reposilite.plugin.api`) | After the server started. | `reposilite` |
| `ReposiliteDisposeEvent` (`com.reposilite.plugin.api`) | At shutdown, before the web server stops. | `reposilite` |
| `HttpServerConfigurationEvent` (`com.reposilite.web.api`) | While the web server is configured. | `reposilite`, `config` (Javalin config) |
| `HttpServerInitializationEvent` (`com.reposilite.web.api`) | When the web server is initialized. | `reposilite`, `config` |
| `HttpServerStartedEvent`, `HttpServerStoppedEvent` (`com.reposilite.web.api`) | When the HTTP server starts or stops. | none |
| `RoutingSetupEvent` (`com.reposilite.web.api`) | When routes are collected. | `reposilite`; `register(route)`, `registerRoutes(...)` |
| `CommandsSetupEvent` (`com.reposilite.console.api`) | When console commands are collected. | `registerCommand(command)` |
| `PreResolveEvent` (`com.reposilite.maven.api`) | Before a file is looked up. | `accessToken`, `repository`, `gav` |
| `ResolvedFileEvent` (`com.reposilite.maven.api`) | After a file request is resolved. | `accessToken`, `repository`, `gav`, `result` (writable) |
| `ResolvedFileDataEvent` (`com.reposilite.maven.api`) | After the file data is resolved. | `accessToken`, `repository`, `gav`, `result` (writable) |
| `DeployEvent` (`com.reposilite.maven.api`) | After a file is stored by a deployment. | `repository`, `gav`, `by` |
| `DeleteEvent` (`com.reposilite.maven.api`) | After a file is removed. | `repository`, `gav`, `by`, `cleanupRunId` |
| `AuditEvent` (`com.reposilite.audit.api`) | When an audited action happens. | `action`, `actor`, `repository`, `path`, `details`, `success` |
| `LoginThrottledEvent` (`com.reposilite.plugin.api`, experimental) | After a sign-in is answered with a delay by the account throttle, while the switch is on. Once per answered request. | `name`, `address`, `path` (`dashboard`, `basic`, `bearer` or `console`), `retryAfterSeconds` |
| `NewSignInEvent` (`com.reposilite.plugin.api`, experimental) | After a successful sign-in from an address that is new for the name, while the account throttle is on. | `name`, `address`, `path`, `userAgentHash` (SHA-256 in hex, or `null`) |
| `ArtifactSearchBackendRegistrationEvent` (`net.onelitefeather.ingot.search.backend`, experimental) | After every plugin has initialized. Register in `initialize` or `load`. | `register(factory)` |

Do not put secret values in `AuditEvent.details`. Use names, counts and field paths.

The sign-in events carry a name, an address and a path, and never a secret, a password or a second-factor code. `NewSignInEvent` carries the user agent only as its SHA-256 hash. Both events are published only while the account throttle is on (`INGOT_LOGIN_ACCOUNT_THROTTLE=true`). A listener that throws is logged and does not change the answer to the request.

The search backend event is experimental, like the other types of the search backend API, which carry `@ExperimentalIngotSearchApi`. A plugin registers a search backend in it, see [Write a search backend plugin](../../manage/plugins/write-a-search-backend-plugin.md).

## Kotlin helpers

These are in the `com.reposilite.plugin` package.

| Helper | Purpose |
| --- | --- |
| `event<E> { }` | Register a listener for event `E`. |
| `facade<F>()` | Return a registered facade. Throws `IllegalStateException` if no facade of that type is registered. Declare the owning plugin in `dependencies`. |
| `reposilite()` | Return the running `Reposilite` server. |
| `parameters()` | Return the server parameters, such as `workingDirectory` and `pluginDirectory`. |

## Facades

Facades are registered by Ingot's built-in plugins. A plugin gets one with `facade<T>()`, or with `extensions().facade(T.class)` in Java.

| Facade (package) | Use |
| --- | --- |
| `Reposilite` (`com.reposilite`) | The server itself. |
| `MavenFacade` (`com.reposilite.maven`) | `findFile(LookupRequest)`, `deployFile(DeployRequest)`, `getRepository(name)`, `getRepositories()`. |
| `RepositoryRegistry` (`net.onelitefeather.ingot.repository`) | `repository(name)` and `repositories()`, read-only, with name, visibility, effective type, storage provider and `storesProxiedFiles` for each repository. Not yet stable for plugins: the format SPI adds a `format` member to `RegisteredRepository`, and an added member breaks implementors. |
| `LocalConfiguration` (`com.reposilite.configuration.local`) | Values from `configuration.cdn`, such as `defaultFrontend`, `basePath` and `hideSettingsSecrets`. |
| `SharedConfigurationFacade` (`com.reposilite.configuration.shared`) | Shared settings, through `getDomainSettings<T>()`. |
| `FrontendFacade` (`com.reposilite.frontend`) | Frontend placeholders, see below. |
| `AccessTokenFacade` (`com.reposilite.token`) | Access tokens. |
| `AuthenticationFacade` (`com.reposilite.auth`) | Credential checks. |
| `AuditFacade` (`com.reposilite.audit`) | Audit log. |
| `StatisticsFacade` (`com.reposilite.statistics`) | Counters of resolved requests. |
| `StorageFacade` (`com.reposilite.storage`) | Storage providers. |
| `FailureFacade` (`com.reposilite.status`) | Records failures. |
| `StatusFacade` (`com.reposilite.status`) | Server status. |

## Job sources

A plugin that keeps a record of its background runs can list them on the [Jobs page](../jobs.md). The plugin registers a job source on the `JobRegistrationEvent` of the Ingot namespace (experimental). The types are in the jobs package of the Ingot namespace, not in `com.reposilite.*`.

The core fires `JobRegistrationEvent` at `ReposilitePostInitializeEvent`, after every plugin has initialized. Listen for it in `initialize()`, and register the source on the registry that the event carries. The registration returns a handle. Closing the handle removes the source again.

* A source registered after the event is logged by the core and not listed.
* When the plugin is disabled, its sources leave the registry. Their rows disappear from the Jobs page, and the history stays in the plugin's own tables.

A source has these members:

| Member | Contract |
| --- | --- |
| `id` | The source id, such as `billing`. It contains neither a colon nor a pipe. |
| `kinds` | The kinds of run the source keeps, such as `billing.invoice`. |
| `requiredNode` | The node a visitor needs to read the runs of the source. The default is `ingot.admin.*`. Choose the narrowest node that fits the data. |
| `page(query, before, limit)` | Returns the runs strictly older than `before` (all runs when it is null), at most `limit` of them, sorted by start time, newest first, then by local id. The server passes a limit of at most 100. |
| `detail(localId)` | Returns the run with that local id, or null. |

The `page` method honours the `state` and `trigger` of the query by mapping them to its own values, so a page is full when rows exist. It never returns a run twice across the pages of one cursor. A run has the id `<source>:<local id>`, a kind, a label, a trigger, a state, the start and finish times, counts and failures, as described in the [Jobs reference](../jobs.md#fields-of-a-run).

A source throws for a store error. The server then leaves the source out of that page and names it in `sourceErrors`, and the other sources still list their runs.

Failures and counts can name paths and URLs of the instance, and the page shows them only to the nodes that read the source. Never put a credential in a failure.

## Routes

A route is a `ReposiliteRoute<R>`:

```kotlin
ReposiliteRoute<R>(path: String, vararg methods: Route, handler: ContextDsl<R>.() -> Unit)
```

`Route` is `io.javalin.community.routing.Route`, for example `GET`. Register routes in a `RoutingSetupEvent` listener.

Inside the handler, `ctx` is the Javalin `Context`. Use these helpers to decide who may call the route:

| Helper | Caller |
| --- | --- |
| `accessed { }` | Anyone, including anonymous callers. The block receives the token or `null`. |
| `authenticated { }` | Any valid token. |
| `authorized(path) { }` | A token that may access `path`. |
| `managerOnly(area?) { }` | A manager token, or a token with access to the admin `area`. |

Read path and query values with `requireParameter(name)`, `parameter(name)` and `queryParameter(name)`. Read a request body with `body<T>()`.

Set `response` to a `Result<R, ErrorResponse>`. Use `notFoundError(message)` from `com.reposilite.shared` for an error. The server writes the response as follows:

| Value | Written as |
| --- | --- |
| `String` | Plain text |
| `InputStream` | Streamed body |
| `HtmlResponse` | HTML |
| `ErrorResponse` | Error with its status code, as described in [API errors](../http-api/api-errors.md) |
| Any other object | JSON |

If two routes have the same method and path, the first one registered wins.

## API document fragments (experimental)

A plugin documents its routes and settings with files in its own jar. The server reads them through the class loader of the plugin class, by plugin id:

* `META-INF/ingot/openapi/<plugin id>.json` is merged into the document served at `/openapi`. The server's entries win, and between plugins the lower id wins.
* `json-schemes/<class name>` is the JSON schema of a settings class, named after `Class.name`.

A plugin jar must not hold `openapi-plugin/.index` or `json-schemes/index`. How to produce the files, and the merge rules in full: [Write a plugin, Document routes and settings](../../manage/plugins/write-a-plugin.md#document-routes-and-settings).

The layout is experimental and may change until it is declared stable. No type of the plugin API is involved, so the compatibility checks do not cover it.

## Repository formats (experimental)

A repository format is the part of Ingot that knows one layout, such as Maven. The format SPI lives in `net.onelitefeather.ingot.format`, and it is experimental:

| Type | Purpose |
| --- | --- |
| `RepositoryFormat` | The id of a format, its path rules, its storage hints, where it is mounted and the handler of its requests. |
| `RepositoryFormats` | The installed formats, keyed by id. Duplicate ids stop the start and name both classes. |
| `PathRules` | Which files are generated metadata, which are resolution metadata, which reads count as downloads and which index path a write maps to. |
| `StorageHints` | The keys that a write makes stale in an S3 head cache. |
| `FormatMount` | Where a format is served. Only `RepositoryPath` (`/{repository}/<path>`) is served in this version. |
| `RepositoryRequestHandler`, `RepositoryRequest`, `FormatContext` | The handler of the requests of a format, the request it receives and the facades it may look up. |

Every type carries `@ExperimentalIngotFormatApi`, a Kotlin opt-in marker. Code that implements or calls these types needs `@OptIn(ExperimentalIngotFormatApi::class)`, and the members may change until the format SPI is declared stable.

## Storage capabilities (experimental)

A storage provider or its settings states what it can do by implementing an interface of `net.onelitefeather.ingot.storage.capability`. Core asks the interface, so a provider that implements none of them keeps the defaults. The rules are in [Storage capabilities](../repositories/storage-providers.md#storage-capabilities).

| Type | Implemented on | Purpose |
| --- | --- | --- |
| `ClusterCacheAware` | Provider | `bind(clusterBus, maxAge)` once the cluster bus is open, and `invalidate(keys)`. |
| `ClusterBus` | The cluster plugin | The bus handle that `bind` receives. A provider receives it and does not implement it. It has no members in this version. |
| `RelatedKeyAware` | Provider | `useRelatedKeys(rule)` for the related keys of the format. |
| `UsageCapability` | Provider | `reportsUsage`, `supportsQuota` and `totalCapacityBytes()`. |
| `QuotaSettings` | Settings | `quota`, as configured. |
| `ReadinessProbe` | Provider | `probe()` returns a `ReadinessOutcome`: `Ok`, `NotFoundOk` or `Failed(reason)`. |
| `SupportDetails` | Settings or provider | `describe()` returns non-sensitive pairs for the support bundle. |
| `SharedNamespaceAware` | Settings | `namespaceKey(repositoryName)`, `overlaps(key, otherKey)` and `overlapMessage()`. |

Every type carries `@ExperimentalIngotStorageApi`. Code that implements or calls these types needs `@OptIn(ExperimentalIngotStorageApi::class)`.

`RelatedKeyAware` was also declared as `com.reposilite.storage.s3.RelatedKeyAware`. That name stays on the JVM as a deprecated interface without members, so compiled plugins still link. A provider that implements the old name also implements `net.onelitefeather.ingot.storage.capability.RelatedKeyAware`, which core checks. New code uses the new name.

Limits of this version:

* Formats are found only on the class path of the server, through the service file `META-INF/services/net.onelitefeather.ingot.format.RepositoryFormat`. A format jar in the plugins directory is not loaded yet.
* The settings classes, `RepositorySettings` and `MavenSettings`, are not covered by the compatibility promise. `RepositorySettings` has a new `format` key as its last constructor parameter. Its previous constructor is kept, so code compiled against it still links, but `copy` has a new signature and a plugin that calls it must be compiled again.
* `RepositoryRegistry` and `RegisteredRepository` are not stable either. `RegisteredRepository` now reports the `format` id of each repository.

## Frontend placeholders

The dashboard and the files in the custom frontend directory can contain placeholders such as `{{REPOSILITE.TITLE}}`. The server replaces them when it serves a file. See [Serve a custom frontend](../../configure/dashboard/serve-custom-frontend.md) for which files are scanned.

Ingot provides these placeholders:

| Placeholder | Value |
| --- | --- |
| `{{REPOSILITE.BASE_PATH}}` | The base path, from `basePath`. |
| `{{REPOSILITE.VITE_BASE_PATH}}` | The base path in the form the dashboard build expects. |
| `{{REPOSILITE.ID}}` | The frontend setting `id`. |
| `{{REPOSILITE.TITLE}}` | The frontend setting `title`. Escaped for a JavaScript string inside assets. |
| `{{REPOSILITE.DESCRIPTION}}` | The frontend setting `description`. Escaped for a JavaScript string inside assets. |
| `{{REPOSILITE.ORGANIZATION_WEBSITE}}` | The frontend setting `organizationWebsite`. |
| `{{REPOSILITE.ORGANIZATION_LOGO}}` | The frontend setting `organizationLogo`. |
| `{{REPOSILITE.PRIVACY_POLICY}}` | The frontend setting `privacyPolicy`. |
| `{{REPOSILITE.ICP_LICENSE}}` | The frontend setting `icpLicense`. |
| `{{REPOSILITE.FAVICON}}` | `appearance.favicon`, or `favicon.png` when it is blank. Only in assets, not in the settings response. |
| `{{REPOSILITE.JAVADOC_SUFFIXES}}` | Registered by the javadoc plugin. Empty when javadoc is disabled. |

A plugin adds its own placeholder with `FrontendFacade.registerPlaceholder(key, value)`. The value is a `Reference<String>`, so a `computed { }` value updates the served files when it changes. The plugin must list `frontend` in `dependencies`:

```kotlin
@Plugin(name = "example", dependencies = ["frontend"])
class ExamplePlugin : ReposilitePlugin() {

    override fun initialize(): Facade? {
        facade<FrontendFacade>().registerPlaceholder(
            key = "{{EXAMPLE.GREETING}}",
            value = mutableReference("hello"),
        )
        return null
    }

}
```

Rules for placeholders:

* Keys use only ASCII characters. A key with other characters makes processing of the asset fail.
* Values are inserted as plain text. Only the bundled `TITLE` and `DESCRIPTION` values are escaped. Escape your own values if they go into a JavaScript string or an HTML attribute.
* Placeholders are substituted in the served copy. Changing a value clears the cached copies.

## Java

The same plugin in Java:

```java
import com.reposilite.plugin.api.Facade;
import com.reposilite.plugin.api.Plugin;
import com.reposilite.plugin.api.ReposiliteInitializeEvent;
import com.reposilite.plugin.api.ReposilitePlugin;
import org.jetbrains.annotations.Nullable;

@Plugin(name = "example")
public final class ExamplePlugin extends ReposilitePlugin {

    @Override
    public @Nullable Facade initialize() {
        extensions().registerEvent(ReposiliteInitializeEvent.class, event -> {
            getLogger().info("Example plugin has been loaded");
        });
        return null;
    }

}
```

The service file is the same: `META-INF/services/com.reposilite.plugin.api.ReposilitePlugin` with the class name.

## Compatibility checks

A plugin built for Reposilite 3.5.28 is expected to keep running on Ingot. The build checks that in two ways. Both are report-only for now: a finding does not fail the build or the pull request check.

| Item | Value |
| --- | --- |
| Baseline | `com.reposilite:reposilite:3.5.28`, the release Ingot forked from. |
| Packages compared | `com.reposilite.plugin`, `com.reposilite.maven`, `com.reposilite.storage`, `com.reposilite.token`, `com.reposilite.shared`, `com.reposilite.web.api`, `com.reposilite.status` and their `api` and `application` subpackages, as listed in `reposilite-backend/build.gradle.kts`. |
| Not compared | Ingot's own packages (`net.onelitefeather.ingot.*`) and `com.reposilite.journalist`. |

The two checks:

- **Binary report.** `./gradlew :reposilite-backend:pluginApiReport` compares the 3.5.28 jar with the jar of the current build, at bytecode level, and lists every incompatible change in the packages above.
- **Binary canaries.** The `checksum-plugin` and `migration-plugin` jars that upstream published for 3.5.28 are loaded unchanged into a fresh server. Run them with `./gradlew :reposilite-backend:integration --tests '*UpstreamPluginCanary*'`.

The bundled plugins in `reposilite-plugins/checksum-plugin` and `reposilite-plugins/migration-plugin` are compiled and tested against the current backend as well. A pull request that edits their `src/main` is flagged in the job summary, because such an edit can hide a break of the plugin API.

Where the results are:

- Locally, the report is written to `reposilite-backend/build/reports/plugin-api/report.html` and `report.md`.
- On a pull request, the **Plugin API** job writes the Markdown report and the canary results to its job summary, and uploads the HTML report as the `plugin-api-report` artifact.

The gate becomes blocking in a later change, once the first report has been reviewed and each accepted difference is recorded with a reason.

## Dashboard pages

A plugin can add pages to the dashboard without shipping JavaScript. It declares each page as data through the `plugin-ui` facade, and the dashboard draws the page with its own components. No plugin code, script, frame or HTML runs in the browser. A plugin may ship one checked stylesheet, see [Stylesheet](#stylesheet). The administrator's session therefore never runs plugin code, and the browser policy of the dashboard stays as it is. The reasons are in [Security headers](../architecture/security-headers.md#plugin-pages). For what a plugin can target on the dashboard today and what is still missing, see [Extend the dashboard from a plugin](../../manage/plugins/extend-the-dashboard-from-a-plugin.md).

### Register a page

Add `plugin-ui` to `dependencies`, get the registrar of your own plugin with `forPlugin(this)`, and register the page:

```kotlin
@Plugin(name = "example", dependencies = ["plugin-ui"])
class ExamplePlugin : ReposilitePlugin() {

    override fun initialize(): Facade? {
        facade<PluginUiFacade>().forPlugin(this).registerPage(
            PluginUiPage(
                id = "status",
                title = "Example status",
                group = "System",
                area = "maintenance",
                blocks = listOf(
                    PluginUiBlock.Text("The job runs every hour."),
                    PluginUiBlock.Status(source = "/api/plugins/example/status", refreshSeconds = 10),
                    PluginUiBlock.Action(
                        label = "Run now",
                        method = "POST",
                        path = "/api/plugins/example/run",
                        confirm = "Run the job now?",
                    ),
                ),
            )
        )
        return null
    }

}
```

The same from Java:

```java
@Plugin(name = "example", dependencies = {"plugin-ui"})
public class ExamplePlugin extends ReposilitePlugin {

    @Override
    public @Nullable Facade initialize() {
        PluginUiFacade pluginUi = extensions().facade(PluginUiFacade.class);
        pluginUi.forPlugin(this).registerPage(new PluginUiPage(
            "status",
            "Example status",
            null,
            "System",
            "maintenance",
            List.of(
                new PluginUiBlock.Text("The job runs every hour."),
                new PluginUiBlock.Status("/api/plugins/example/status", 10)
            )
        ));
        return null;
    }

}
```

The page opens at `/settings/plugins/<plugin>/<page>` and is listed in the menu group it names. A page the caller may not open does not appear in the menu, and its address shows the no access state. Registration returns `true` when the page was accepted.

### Page fields

| Field | Rule |
| --- | --- |
| `id` | Lower case letters, digits and hyphens, 1 to 32 characters. Unique within the plugin. |
| `title` | 1 to 60 characters. |
| `description` | Optional, at most 200 characters. |
| `group` | `Repository`, `Security`, `System` or `Support`. Default `System`. |
| `area` | The admin area that opens the page: `settings`, `users`, `teams`, `tokens`, `maintenance`, `audit`, `console` or `dashboard`, or `*` for the full instance administrator. |
| `blocks` | 1 to 30 blocks, drawn in order. |

The value of `group` stays the same, and the dashboard shows each group under the heading in this table. A page declared with `Security` is therefore listed under **Access**, and one declared with `Support` under **Monitoring and support**. A value outside the four is refused by the server.

| Group value | Heading shown |
| --- | --- |
| `Repository` | Artifacts |
| `Security` | Access |
| `System` | Instance |
| `Support` | Monitoring and support |

A plugin has at most 20 pages, and the serialised pages of one plugin take at most 64 KiB.

### Blocks

The `type` of a block selects its shape. There is no block that carries HTML, a script or a style. A link is a same-origin path, with the rules under "Links" below.

| Block | Fields | What the dashboard does |
| --- | --- | --- |
| `Text` | `text`, 1 to 2000 characters | Shows the text as plain characters. |
| `KeyValue` | `source` | Reads the object `source` returns with GET and shows one row per key. |
| `Table` | `source`, `columns` (1 to 12 of `key` and `label`) | Reads the array `source` returns with GET. Shows at most 500 rows and says how many it cut. |
| `Form` | `source`, `target`, `method` (`PUT` or `POST`), `schema`, `uiSchema` | Reads the object `source` returns, draws a form from the JSON schema and sends the result to `target`. A schema may refer only into its own document (`#...`). |
| `Action` | `label`, `method` (`POST`, `PUT` or `DELETE`), `path`, `confirm`, `danger` | Shows a button. With `confirm` it asks for that text first. The request carries the CSRF header. |
| `Status` | `source`, `refreshSeconds` (5 to 300) | Reads the object `source` returns with GET. The object has `state`, `message` and `progress` (0 to 1). Polls at the interval, and only while the page is visible. |
| `Code` | `text` (1 to 4000 characters), `label` (optional, at most 60 characters), `language` (`plain`, `kotlin`, `groovy`, `xml` or `properties`) | Shows the text as plain characters in a box, with a Copy action that copies the text and nothing else. If the browser refuses the copy, the text is selected. The language only names the block for assistive technology. |
| `Link` | `label` (1 to 60 characters), `href` | Shows a link to a same-origin path, opened in the same tab. A refused `href` shows the label and the note "Link not shown". |
| `List` | `source`, `pageSize` (1 to 100, default 50) | Reads the object `source` returns with GET. It has `items` and `next`. Each item has a `title` (1 to 200 characters), an optional `subtitle` (at most 400), `chips` (at most 8 texts of 1 to 40 characters) and an optional `href`, checked under "Links". `next` is an opaque cursor, or null for the last page. "Load more" asks for the next page with `cursor=<next>`. Shows at most 100 items and says how many it cut. An item without a title is not shown, and the count of those is stated. The list decides nothing about who may read an item: the plugin sends only the items the viewer may read. |

A block shows a value that is not of the expected shape as text, and a block type this version of the dashboard does not know as an unsupported note.

### Settings sections

A plugin can give one of its own settings domains a label and a menu group:

```kotlin
facade<PluginUiFacade>().forPlugin(this).registerSettingsSection(
    PluginUiSection(domain = "example", label = "Mirror checks", group = "Repository")
)
```

The domain must be the settings domain of the plugin (its `@Plugin` `settings` class). Any other domain is refused. The group is `Repository` or `System`.

### Routes of a page

Every path of a block starts with `/api/plugins/<plugin>/`, where `<plugin>` is the name the plugin is registered under. Paths may use the characters `A-Z a-z 0-9 . _ ~ -` and `/`, and may not contain empty, `.` or `..` segments. A path outside the prefix refuses the whole page, and the warning names the path.

For a plugin with pages, the server requires one of the areas of its pages for every request below `/api/plugins/<plugin>/`, before the route runs. An anonymous caller gets `401`, and a caller without any of the areas gets `403`. The check is a floor: a route that needs more still checks it. A plugin without pages is not affected, and neither is a plugin whose name only starts with the name of another.

### Links

A `Link` carries an `href` that is a path of the dashboard or a repository, such as `/browse/<repository>/<path>`. All of these rules must hold. The server checks them when the plugin registers the page, and the dashboard checks them again before it draws a link:

- It starts with one `/`. A second `/` or a backslash is refused, so no other host can be named.
- It does not start with `/api/` or `/assets/`. Data routes are not shown as pages.
- Its characters are `A-Z a-z 0-9 . _ ~ - / ? = &`. A percent sign, a colon, a space and a fragment are refused.
- No segment before the `?` is empty, `.` or `..`.
- It is at most 500 characters.

A link does not decide who may read its target. The target keeps its own checks. The same rules apply to the `href` of a list item, which the dashboard checks for each row before it draws the row.

### Refused registrations

A page or section that breaks a rule is dropped with a warning in the log that names the plugin and the problem. The server starts and the other pages are served. The warning is the place to look when a page does not appear.

### Stylesheet

A plugin can ship one stylesheet for the dashboard. Put the file in the resources of the plugin jar, and register it on the registrar of the plugin:

```kotlin
facade<PluginUiFacade>().forPlugin(this).registerStylesheet("dashboard.css")
```

The server reads the file when the plugin registers it, checks it, and wraps it in the layer `ingot-plugins`, which the dashboard declares after its own utility layers. Registration returns `true` when the sheet was accepted. A plugin makes one call: a second call is dropped, and so is a first call whose sheet failed.

The sheet is checked against these rules, and a sheet that breaks one is dropped with a warning that names the plugin and the rule:

- at most 64 KiB, UTF-8, without a byte order mark and without an `@charset` rule,
- no `@import`, `@font-face` or `@namespace`,
- no `src()`, `image-set()` or `-webkit-image-set()`, and `url()` only with a `data:image/` argument,
- no backslash outside a quoted string,
- no `expression(`, `behavior:` or `-moz-binding`,
- balanced braces.

The dashboard loads the sheet after the manifest, only for a signed in caller. The manifest lists it in `stylesheets` with its path and the SHA-256 of the served bytes, and the sheet is served at `/api/frontend/plugin-styles/<plugin>/<hash>.css`, where `<hash>` is the first 16 hexadecimal characters of that digest. The answer is cached for a year. A worked example and the authoring rules are in [Extend the dashboard from a plugin](../../manage/plugins/extend-the-dashboard-from-a-plugin.md#ship-a-stylesheet).

### Reader pages

A reader page is a dashboard page that anonymous visitors may read, for example the versions of a repository. It is registered like an admin page, with its own class and no area or menu group:

```kotlin
@OptIn(ExperimentalIngotPluginApi::class)
facade<PluginUiFacade>().forPlugin(this).registerReaderPage(
    PluginUiReaderPage(
        id = "releases",
        title = "Releases",
        blocks = listOf(PluginUiBlock.Table(source = "/api/plugins/example/releases", columns = listOf(PluginUiColumn("version", "Version")))),
    )
)
```

The classes are in `net.onelitefeather.ingot.plugin.ui` and carry `@ExperimentalIngotPluginApi`, so a plugin opts in. The rules are the rules of admin pages, with these differences:

- The blocks only read. `text`, `keyValue` and `table` are accepted. A `form`, an `action` or a `status` block is refused with a warning, and the rest of the page is still served.
- A plugin takes at most 20 reader pages, counted apart from its admin pages, and the serialised reader pages of one plugin take at most 64 KiB.
- Every source is a route of the plugin below `/api/plugins/<plugin>/`, as for admin pages.
- A reader source must filter its rows for the viewer and must not answer with more than a bounded size. The rules for serving reader pages to visitors follow in a later change.

### Switch

`INGOT_LOCAL_PLUGINUI=false` turns the contributions off for the whole instance. The manifest is then empty and the contributed pages are not served. The plugins themselves keep running. The variable defaults to `true`, and a plugin that never registers a page is not affected by it.

`INGOT_LOCAL_PLUGINREADERS=false` hides every reader page, and leaves the `/plugins/...` address unclaimed. Admin pages are not affected. The variable defaults to `true`, and `INGOT_LOCAL_PLUGINUI=false` switches the reader pages off as well.

`INGOT_LOCAL_PLUGINSTYLES=false` turns off the stylesheets of all plugins. The manifest lists no stylesheet and the stylesheet address answers `404`. Pages and settings sections are not affected. The variable defaults to `true`, and an instance without a plugin stylesheet sees no change.
