---
description: Build a plugin for Ingot with Gradle, register it as a service, handle events, and add a route.
---

# Write a plugin

This page builds a small plugin that logs when a file is deployed and adds one HTTP route. It follows the layout of the example plugin in the repository (`reposilite-plugins/example-plugin`) and of the checksum plugin (`reposilite-plugins/checksum-plugin`). The full contract is in [Plugin API](../reference/plugin-api.md).

## Prerequisites

* JDK 17 or newer. The server targets JVM 17 and runs on Java 21 in the container image.
* Gradle with the Kotlin DSL. This page uses Kotlin. Java works with the same API, see the Java tab in the reference.
* The Ingot version you run. Build the plugin against that version.
* Access to the Ingot Maven artifacts. See step 1.

## Steps

{% stepper %}
{% step %}
### Declare the Ingot dependency

Use the server bundle as a `compileOnly` dependency. Do not bundle it into your jar. The server already provides its classes at runtime, and a second copy would load a second, conflicting set.

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.4.0"
    id("com.gradleup.shadow") version "9.4.2"
}

group = "com.example.ingot"
version = "1.0.0"

repositories {
    mavenCentral()

    // Ingot depends on com.reposilite:journalist, which is not on Maven Central.
    maven("https://maven.reposilite.com/releases") {
        content { includeGroup("com.reposilite") }
    }

    // Ingot artifacts. This repository requires credentials, also for reads.
    maven("https://repo.onelitefeather.dev/onelitefeather-releases") {
        credentials {
            username = providers.gradleProperty("ingotRepoUser").orNull
            password = providers.gradleProperty("ingotRepoPassword").orNull
        }
    }
}

dependencies {
    // Replace <ingot-version> with the version of the server you run.
    compileOnly("net.onelitefeather.ingot:ingot:<ingot-version>")
}

tasks.shadowJar {
    archiveFileName.set("my-plugin.jar")
    mergeServiceFiles()
}
```

{% hint style="warning" %}
The release repository refuses anonymous requests. An unauthenticated request for the `ingot` artifact returned HTTP 401 when this page was checked. Ask the maintainers for a read account. If you cannot get one, build Ingot locally and use `mavenLocal()`: from a checkout of the Ingot repository, run `./gradlew :reposilite-backend:publishToMavenLocal`, then add `mavenLocal()` to `repositories`. That publish task was not run while writing this page.
{% endhint %}
{% endstep %}

{% step %}
### Write the plugin class

Every plugin extends `ReposilitePlugin` and carries a `@Plugin` annotation. The `name` must be unique among the plugins in one server.

```kotlin
// src/main/kotlin/com/example/ingot/ExamplePlugin.kt
package com.example.ingot

import com.reposilite.maven.api.DeployEvent
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposiliteInitializeEvent
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.event

@Plugin(name = "example")
class ExamplePlugin : ReposilitePlugin() {

    override fun initialize(): Facade? {
        event { _: ReposiliteInitializeEvent ->
            logger.info("Example plugin has been loaded")
        }

        event { event: DeployEvent ->
            logger.info("Deployed ${event.gav} to ${event.repository.name} by ${event.by}")
        }

        // This plugin does not offer an API to other plugins, so it returns null.
        return null
    }

}
```

`event { }` and `facade<T>()` are Kotlin helpers in the `com.reposilite.plugin` package. In Java, call `extensions().registerEvent(EventClass.class, listener)` instead.
{% endstep %}

{% step %}
### Register the class as a service

Ingot finds plugins through the Java `ServiceLoader`. Create a file that names your plugin class. Without this file, the server ignores the jar.

File `src/main/resources/META-INF/services/com.reposilite.plugin.api.ReposilitePlugin`:

```text
com.example.ingot.ExamplePlugin
```

List one class per line. The example plugin in the repository uses the same file.
{% endstep %}

{% step %}
### Add a route

Routes are added in a `RoutingSetupEvent` listener. Each route is a `ReposiliteRoute` with a path, one or more methods and a handler. The handler sets `response`, and Ingot writes it to the client.

Add this block inside `initialize()`, before `return null`:

```kotlin
event { event: RoutingSetupEvent ->
    event.register(
        ReposiliteRoute<String>("/api/example/hello", GET) {
            accessed {
                response = "hello".asSuccess()
            }
        }
    )
}
```

Add the imports:

```kotlin
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.RoutingSetupEvent
import io.javalin.community.routing.Route.GET
import panda.std.asSuccess
```

`accessed { }` lets anonymous callers in. Use `authenticated { }`, `authorized(path) { }` or `managerOnly { }` to restrict the route. A plain string is sent as text. Any other object is sent as JSON.

{% hint style="warning" %}
A route answers the request with whatever the handler sets. Choose the access helper on purpose. Do not expose data through `accessed { }` unless anonymous callers may read it.
{% endhint %}
{% endstep %}

{% step %}
### Build the jar

```bash
./gradlew shadowJar
```

The jar is written to `build/libs/my-plugin.jar`. Copy it to the plugin directory and restart the server, as described in [Install a plugin](install-a-plugin.md).
{% endstep %}

{% step %}
### Check the plugin

1. Start the server and confirm that the startup log lists `example` and prints `Example plugin has been loaded`.
2. Request the route. Use the port your deployment exposes (8080 in the image):

   ```bash
   curl -i http://localhost:8080/api/example/hello
   ```

   The response has status `200` and the body `hello`.
3. Deploy any file to a repository. The log contains a `Deployed` line with the path.
{% endstep %}
{% endstepper %}

## Result

The startup log lists your plugin. The route answers `hello`. Deploying a file writes a `Deployed` line to the log.

## Next steps

* `load()`, lifecycle order, and the list of events: [Plugin API](../reference/plugin-api.md).
* Serving your own dashboard files or placeholders: [Serve a custom frontend](serve-custom-frontend.md).
* Plugin dependencies: set `dependencies = ["maven"]` (or another plugin name) in `@Plugin`. Ingot initializes dependencies first and sorts the rest by name. A dependency name that matches no loaded plugin stops startup with `Unknown dependency`.
