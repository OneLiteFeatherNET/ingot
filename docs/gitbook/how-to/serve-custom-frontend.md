---
description: Serve your own HTML, CSS, JavaScript and images from an Ingot server, use frontend placeholders, and choose whether the bundled dashboard stays on.
---

# Serve a custom frontend

Ingot can serve your own static files next to its API. Use this for a landing page, a status page or a replacement dashboard. The bundled dashboard can stay on, or you can turn it off so that your files answer the root path.

## Prerequisites

* Shell access to the host, or to the container that runs Ingot.
* Basic knowledge of HTML.
* Read the warning in step 6 first. Everything in the directory is public.

## Steps

1. Find the directory. It is named `static` and sits in the working directory. In the container image the working directory is `/app/data`, so the directory is `/app/data/static`. Ingot creates it on the first start if it does not exist, and fills it with a default `index.html`. It also adds a `favicon.png` when there is none.

   If you create the directory yourself, add an `index.html` too. Ingot only writes its default `index.html` when it creates the directory.

2. Put your files in the directory. Ingot maps them to URLs as follows:

   | File | URL |
   | --- | --- |
   | `static/index.html` | `/` |
   | `static/status.html` | `/status.html` |
   | `static/app.js` | `/app.js` |
   | `static/img/logo.png` | `/img/<path>`, for example `/img/logo.png` |

   Files directly in the directory are served at `/<file>`. Files in a subdirectory are served at `/<directory>/<path>`.

3. Use placeholders if you want the server to fill in values. Ingot replaces placeholders such as `{{REPOSILITE.TITLE}}` when it serves a file. Placeholders are only substituted in:

   * top-level files whose name contains `.html` or `.js`. This includes `index.html`, and it also matches names such as `data.json`.

   Files in subdirectories are served unchanged. The placeholder names and values are listed in the [Plugin API reference](../reference/plugin-api.md#frontend-placeholders).

   ```html
   <!doctype html>
   <html>
     <head>
       <meta charset="utf-8">
       <title>{{REPOSILITE.TITLE}}</title>
     </head>
     <body>
       <h1>{{REPOSILITE.TITLE}}</h1>
       <p>{{REPOSILITE.DESCRIPTION}}</p>
     </body>
   </html>
   ```

4. Choose how the bundled dashboard behaves.

   * Default: `INGOT_LOCAL_DEFAULTFRONTEND=true`. The bundled dashboard owns `/`, `/index.html` and `/assets/<path>`. These routes are registered before your files, so your `index.html` is not served at `/`. Your other top-level files are still served.
   * Off: `INGOT_LOCAL_DEFAULTFRONTEND=false`. The bundled dashboard routes are not registered, and your `index.html` answers `/`.

   In the compose file from the repository, add the variable under `environment`:

   ```yaml
   services:
     ingot:
       environment:
         - INGOT_LOCAL_DEFAULTFRONTEND=false
   ```

   The repository also ships `docker-compose.split.yml`, which runs the dashboard in its own container.

5. Restart Ingot after you change the files. The server builds its routes at startup, so a new top-level file or a new subdirectory is served only after a restart. Ingot also keeps a processed copy of each such file until the restart or until a frontend setting changes. Edits to other files, and new files inside an existing subdirectory, are read on the next request.

   ```bash
   docker compose restart ingot
   ```

6. Check the result.

   ```bash
   curl -i http://localhost:8080/
   curl -s http://localhost:8080/ | grep "<title>"
   ```

   The `<title>` line shows the title from the frontend settings, not the placeholder text.

{% hint style="warning" %}
Anyone who can reach the server can read every file in the directory. Ingot serves these files without an access check. Do not put secrets, configuration files or private data in it.
{% endhint %}

{% hint style="warning" %}
With the bundled dashboard on, a subdirectory named `assets` is shadowed by the dashboard route `/assets/<path>`. Name your directories differently, or turn the bundled dashboard off.
{% endhint %}

## Result

Your `index.html` answers the root path when the bundled dashboard is off. When it is on, the dashboard answers the root path, and your files answer their own paths. The placeholders in your top-level HTML and JavaScript files show the configured values.

## Related

* [Plugin API, frontend placeholders](../reference/plugin-api.md#frontend-placeholders): the placeholder list, and how a plugin adds its own.
* [Write a plugin](write-a-plugin.md): a plugin can register a placeholder for your files to use.
