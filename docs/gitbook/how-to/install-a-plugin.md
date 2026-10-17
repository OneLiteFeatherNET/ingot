---
description: Install a plugin jar into an Ingot server, restart it, and confirm the plugin loaded.
---

# Install a plugin

Ingot loads plugins from a directory at startup. This page covers copying a plugin jar into that directory and checking that the server loaded it. To build your own plugin, see [Write a plugin](write-a-plugin.md).

## Prerequisites

* A plugin jar that you built or trust. A plugin runs inside the server process with full access to it, so only install code you have reviewed.
* A plugin built against the same Ingot version that you run.
* Shell access to the host, or to the container that runs Ingot.

## Steps

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

## Result

The startup log lists your plugin name in the loaded plugins section. The log does not contain an `Invalid JAR file` warning for your jar.

{% hint style="warning" %}
If the log shows `Invalid JAR file` for your file, the file is not a readable jar. Copy it again and check that the file is complete.
{% endhint %}

## Remove a plugin

Stop the server, delete the jar from the plugin directory, and start the server again. A plugin is not unloaded while the server runs.

## Troubleshooting

| Symptom | Cause |
| --- | --- |
| The plugin name is missing from the log | The jar is not in the plugin directory, does not end in `.jar`, or sits in a subdirectory. The server was not restarted after the copy. |
| `Invalid JAR file` warning | The file is not a valid jar archive. |
| Startup stops with `does not have @Plugin annotation` | The class is listed in `META-INF/services/com.reposilite.plugin.api.ReposilitePlugin` but has no `@Plugin` annotation. |
| Startup stops with `is already registered` | Two jars in the plugin directory declare a plugin with the same name. Keep one of them. |

For the plugin contract, see [Plugin API](../reference/plugin-api.md).
