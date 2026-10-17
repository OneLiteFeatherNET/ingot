---
description: Reference for the Ingot plugin API - coordinates, loading, lifecycle, the @Plugin annotation, events, facades, routes and frontend placeholders.
---

# Plugin API

This page lists the types a plugin uses. For a step-by-step build, see [Write a plugin](../how-to/write-a-plugin.md). For installing a jar, see [Install a plugin](../how-to/install-a-plugin.md).

## Coordinates and packages

| Item | Value |
| --- | --- |
| Maven coordinate | `net.onelitefeather.ingot:ingot:<version>`, the server bundle. Use `compileOnly`. |
| Release repository | `https://repo.onelitefeather.dev/onelitefeather-releases`. Requires credentials, also for reads. |
| Snapshot repository | `https://repo.onelitefeather.dev/onelitefeather-snapshots`, for versions that contain `SNAPSHOT`. |
| Journalist dependency | `https://maven.reposilite.com/releases`, group `com.reposilite`. The server depends on it, so plugin builds need this repository too. |
| Packages | `com.reposilite.*`. The plugin API is in `com.reposilite.plugin.api`. |
| Plugin directory | `plugins` in the working directory. Change it with `--plugin-directory` or `-pd`. |
| Java target | 17 |

Plugins written for Reposilite compile against Ingot unchanged, apart from the dependency coordinate.

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
| `dependencies` | String array | empty | Names of plugins that must initialize first. |
| `settings` | class of `SharedSettings` | `SharedSettings` | Settings class owned by the plugin. Read it with `SharedConfigurationFacade.getDomainSettings<T>()`. |

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

Do not put secret values in `AuditEvent.details`. Use names, counts and field paths.

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
| `ErrorResponse` | Error with its status code |
| Any other object | JSON |

If two routes have the same method and path, the first one registered wins.

## Frontend placeholders

The dashboard and the files in the custom frontend directory can contain placeholders such as `{{REPOSILITE.TITLE}}`. The server replaces them when it serves a file. See [Serve a custom frontend](../how-to/serve-custom-frontend.md) for which files are scanned.

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
