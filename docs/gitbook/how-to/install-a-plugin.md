---
description: Install a plugin into an Ingot server, either from the plugin store or from a jar file, restart it, and confirm the plugin loaded.
---

# Install a plugin

Ingot loads plugins at startup from two places. The plugin store downloads a verified jar from a Maven repository that publishes a catalogue. The plugins directory holds jars that you copy in yourself. This page covers both, and checking that the server loaded the plugin. To build your own plugin, see [Write a plugin](write-a-plugin.md).

Use the store when a publisher lists the plugin in a catalogue you trust. Use a jar when you have the file, for example one you built. Both paths end with the same check.

## Prerequisites

* A plugin jar that you built or trust. A plugin runs inside the server process with full access to it, so only install code you have reviewed.
* A plugin built against the same Ingot version that you run.
* Shell access to the host, or to the container that runs Ingot.

## Install a jar file

1. Find the plugin directory. By default it is a directory named `plugins` inside the working directory. The container image starts Ingot with `--working-directory=/app/data`, so the directory is `/app/data/plugins` in the image. To use another directory, start Ingot with `--plugin-directory <path>` (or `-pd <path>`).

2. Copy the plugin jar into the plugin directory. Ingot creates the directory on the first start if it does not exist. Only files that end in `.jar` and sit directly in the plugin directory are loaded. Subdirectories are not scanned.

   On a host:

   ```bash
   cp my-plugin.jar /path/to/data/plugins/
   ```

   In a container started with the `docker-compose.yml` from the repository:

   ```bash
   docker compose exec ingot mkdir -p /app/data/plugins
   docker compose cp my-plugin.jar ingot:/app/data/plugins/my-plugin.jar
   ```

3. Restart Ingot. Plugins are read only at startup. Copying a jar into a running server does nothing until the next start.

   ```bash
   docker compose restart ingot
   ```

4. Check the startup log. After the `--- Loading plugins:` section, Ingot prints the names of the loaded plugins. Each name is the value of the `name` in the plugin's `@Plugin` annotation.

   ```bash
   docker compose logs ingot | grep -A 20 "Loading plugins"
   ```

## Install from the plugin store

The store reads the catalogue of each source at start, downloads the jar of each plugin you select, and checks its digest and its signature before it loads it. Nothing is downloaded unless you select a plugin. The catalogue format, the states and the limits are in [Plugin store](../reference/plugin-store.md).

Before you begin, you need:

* The coordinate of the plugin, `groupId:artifactId:version`, and the `https` address of a Maven repository that lists it.
* The public key of the publisher as an ASCII-armoured OpenPGP file, for example `plugin-keys.asc`. The server reads it as the service account, which is user `977` in the image unless you set `PUID`.

Steps:

1. Mount the key file and select the plugin. With Docker Compose:

   ```yaml
   services:
     ingot:
       environment:
         INGOT_PLUGINS: org.example:ingot-example-plugin:1.0.0
         INGOT_PLUGIN_SOURCES: https://repo.example.org/plugins
         INGOT_PLUGIN_TRUSTED_KEYS: /etc/ingot/plugin-keys.asc
       volumes:
         - ingot-data:/app/data
         - ./plugin-keys.asc:/etc/ingot/plugin-keys.asc:ro
   ```

   `INGOT_PLUGINS` takes a comma-separated list of coordinates. The local key `plugins` in `configuration.cdn` adds to the same list. `INGOT_PLUGIN_SOURCES` takes one or more `https` addresses, asked in the order given.

2. Restart the server. The store runs only at start.

   ```bash
   docker compose restart ingot
   ```

3. Check the store lines in the log. Each selected plugin gets one line that names its state:

   ```bash
   docker compose logs ingot | grep "Plugin store"
   ```

   The expected line is `Plugin store: example-plugin LOADED from https://repo.example.org/plugins`. The name is the plugin id of its catalogue entry. A plugin that does not load gets a line with its state and the reason. For example, a signature from a key that is not in `INGOT_PLUGIN_TRUSTED_KEYS` is `REFUSED`.

Things to know about the store:

* Without `INGOT_PLUGIN_TRUSTED_KEYS` the store uses no source, and the log says so at start.
* `INGOT_PLUGIN_ALLOW_UNSIGNED=true` loads an unsigned catalogue or jar. Such a plugin is `UNVERIFIED`, and the server logs a warning at every start. Use it only for a source that you run yourself.
* A signature shows who published the jar. It does not make the code safe. A store plugin runs with the same access as a jar in the plugins directory, so the caution of the prerequisites above applies.
* A verified copy is kept in `/app/data/plugin-cache`. A restart loads that copy without network access, as long as its signature still verifies against the trusted keys.
* To skip the store for one start, for example to remove a plugin that stops the server, set `INGOT_PLUGIN_STORE_DISABLED=true`, restart, then unset it.

## Result

The startup log lists your plugin name in the loaded plugins section. The log does not contain an `Invalid JAR file` warning for your jar. A plugin from the store also has a `Plugin store:` line with the state `LOADED`, `CACHED` or `UNVERIFIED`.

{% hint style="warning" %}
If the log shows `Invalid JAR file` for your file, the file is not a readable jar. Copy it again and check that the file is complete.
{% endhint %}

## Remove a plugin

Stop the server, delete the jar from the plugin directory, and start the server again. A plugin is not unloaded while the server runs.

For a plugin from the store, remove its coordinate from `INGOT_PLUGINS` (and from the local key `plugins`, when you set it), then restart the server. The verified copy stays in `/app/data/plugin-cache` until you delete it.

## Troubleshooting

| Symptom | Cause |
| --- | --- |
| The plugin name is missing from the log | The jar is not in the plugin directory, does not end in `.jar`, or sits in a subdirectory. The server was not restarted after the copy. |
| `Invalid JAR file` warning | The file is not a valid jar archive. |
| Startup stops with `does not have @Plugin annotation` | The class is listed in `META-INF/services/com.reposilite.plugin.api.ReposilitePlugin` but has no `@Plugin` annotation. |
| Startup stops with `is already registered` | Two jars in the plugin directory declare a plugin with the same name. Keep one of them. |
| `Plugin store: <id> SHADOWED` | A jar in the plugins directory has the same plugin id. The jar wins, so remove the jar or the store selection. |
| `Plugin store: <id> REFUSED` with a reason about a key, a signature or a digest | The key is missing from `INGOT_PLUGIN_TRUSTED_KEYS`, the signature does not match, or the jar differs from the catalogue. Check the key file and the source. |
| `Plugin store: <id> UNAVAILABLE` | No source could be reached and no verified copy is cached. The server starts without the plugin. Check the address and the network. |
| `Plugin store: <id> FAILED` | The plugin loaded but its class could not be initialised. The reason names the error, and the server starts without the plugin. |
| `INGOT_PLUGIN_SOURCES refused` or `INGOT_PLUGINS refused` in the log | A source is not an `https` address, or a coordinate is not `groupId:artifactId:version`. The message names the entry. |

For the plugin contract, see [Plugin API](../reference/plugin-api.md).
