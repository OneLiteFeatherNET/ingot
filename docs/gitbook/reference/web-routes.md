---
description: Reference for the web routes that the Ingot backend serves outside the JSON API, covering Maven paths, directory index, badges, javadoc, health, OpenAPI, dashboard routes, the 404 page and the settings that change them.
---

# Web routes

This page lists the routes of the Ingot backend that are not part of the JSON API under `/api/`: the Maven repository paths, the directory index, the version badge, the javadoc viewer, the health endpoint, the OpenAPI document, the dashboard routes and the 404 page. It also lists the settings that change them.

## Overview

| Method | Path | Group | Authentication |
|--------|------|-------|----------------|
| `GET`, `HEAD` | `/{repository}/<gav>` | Maven read | Optional. Depends on the repository visibility. |
| `PUT`, `POST` | `/{repository}/<gav>` | Maven deploy | Required, and the token must be authorized for the path. |
| `DELETE` | `/{repository}/<gav>` | Maven delete | Required, and the token must be authorized for the path. |
| `GET` | `/api/badge/latest/{repository}/<gav>` | Badge | Optional. Depends on the repository visibility. |
| `GET` | `/javadoc/{repository}/<gav>` | Javadoc viewer | Optional. Depends on the repository visibility. |
| `GET` | `/javadoc/{repository}/<gav>/raw/<resource>` | Javadoc resource | Optional. Depends on the repository visibility. |
| `GET` | `/api/status/health` | Health | None. |
| `GET` | `/openapi` | OpenAPI document | None added by Ingot. |
| `GET` | `/swagger` | Swagger UI | Only with the optional Swagger plugin. |
| `GET`, `HEAD` | `/`, `/index.html` | Dashboard | None. |
| `GET` | `/assets/<path>` | Dashboard assets | None. |
| `GET`, `HEAD` | Dashboard routes, see [Dashboard routes](#dashboard-routes) | Dashboard | None. |
| `GET` | `/api/frontend/settings` | Dashboard settings | None. |

`{repository}` matches one path segment. `<gav>` matches the rest of the path and can contain slashes. A request without a `<gav>`, such as `/releases`, does not match the Maven route.

{% hint style="info" %}
The server does not mount any route under the `basePath` setting. A request to `/releases/gav` is served directly. The base path only changes links and cookies that Ingot generates. See [Base path and forwarded prefix](#base-path-and-forwarded-prefix).
{% endhint %}

## Order of matching

Javalin answers a request with the first registered endpoint that matches it. It never falls through to a later endpoint. Registration order decides which route answers a path that two routes could answer.

1. Before handlers run first. When `bypassExternalCache` is on, a handler on `/api/*` adds the cache bypass headers. See [Settings that change the routes](#settings-that-change-the-routes).
2. The frontend plugin registers its routes at the highest priority, so they come before the routes of every other plugin. In this order:
    1. `GET /api/frontend/settings`.
    2. When `defaultFrontend` is `true`: `/`, `/index.html`, `/assets/<path>` and the dashboard routes, in the order of the table below.
    3. The routes for the files and directories of the `static` directory.
3. All other plugins register their routes at the default priority. This includes `/{repository}/<gav>` and the routes under `/api/`.
4. A path that no route matches ends in the 404 page. See [404 page](#404-page).

When two frontend routes have the same method and path, the first one registered is kept. With `defaultFrontend` on, `/` and `/index.html` are therefore served by the bundled dashboard and not by `static/index.html`.

{% hint style="warning" %}
Not verified: the order between `/{repository}/<gav>` and the routes under `/api/`, both registered at the default priority. The server logs a warning at startup for a repository whose id is `api` or `assets`, because requests under that path may be answered by the API or the dashboard instead of the repository. Do not use those ids.
{% endhint %}

## Maven routes

`GET /{repository}/<gav>` and `HEAD /{repository}/<gav>` read from a repository. `{repository}` is the repository id. `<gav>` is the path inside the repository.

### How a request is resolved

1. The path is checked as a location. A path with one of the characters `:`, `<`, `>`, `"`, `'`, `\`, `|`, `?` or `*`, with a run of two or more dots, or with a control character answers `400`. Repeated and trailing slashes are removed.
2. The repository is looked up by id. An unknown id answers `404` with the message `Repository <id> not found`.
3. The access check runs for the resource. See [Access by visibility](#access-by-visibility).
4. The path is looked up in the repository, and in its mirrors when it has some. A file answers with its content. A directory answers with the directory index.

### Responses

| Case | Status | Body |
|------|--------|------|
| File | `200` | The file content. With `HEAD`, the same headers and no body. |
| Directory | `200` | The HTML directory index. See [Directory index](#directory-index). |
| Path not found, repository not found, or `<gav>` is a directory where a file is needed | `404` | The 404 page with the reason in it. |
| Anonymous request to a resource that needs credentials | `401` | The 404 page. The response carries `WWW-Authenticate: Basic realm="Ingot", charset="UTF-8"`. |
| Authenticated request without the right to read | `403` | The 404 page. |
| Invalid location | `400` | A JSON error with `status` and `message`. |

For a file, the response carries these headers:

| Header | Value |
|--------|-------|
| `Content-Security-Policy` | `sandbox` |
| `Content-Disposition` | `attachment; filename="<name>"` for content types that are not human readable. |
| `Content-Length` | Set when `compressionStrategy` is `none` and the length is known. |
| `Last-Modified` | Set when the modification time is known. |
| `Cache-Control` | `public, max-age=<seconds>` when the repository does not allow redeployment and the file is not `maven-metadata.xml`. Otherwise `no-cache, no-store, max-age=0`. |

The `max-age` is 3600 seconds. The system property `reposilite.maven.maxAge` changes it. There is no `ingot.` spelling of this property.

### Access by visibility

| Visibility | File | Directory |
|------------|------|-----------|
| `PUBLIC` | Open. | Open. |
| `HIDDEN` | Open to anyone who knows the path. | Needs the read right. |
| `PRIVATE` | Needs the read right. | Needs the read right. |

A directory on the way to a subtree that the caller can read through artifact nodes can also be opened, and its listing shows only what the caller may see. See [Artifact permission nodes](artifact-permission-nodes.md).

A `GET` or `HEAD` request authenticates with the first of these that is present:

* An `Authorization: Bearer` header.
* An `Authorization: Basic` header (or `xBasic`).
* The session cookie of a dashboard login, when there is no `Authorization` header.

Credentials are optional on read routes. Credentials that are wrong are treated like no credentials, so a bad secret on a public repository still reads the file.

### Deploy and delete

| Method | Result |
|--------|--------|
| `PUT`, `POST` | Deploys the request body to the path. The header `X-Generate-Checksums: true` asks Ingot to generate checksums for the file. |
| `DELETE` | Deletes the path from the repository. |

Both need valid credentials (`401` otherwise). A token that is not authorized for the path answers `403`. An unknown repository answers `404`. The repository adds its own checks, such as the redeployment setting and the delete right, and this page does not list their statuses. The route description also lists `507` for a deploy when Ingot has not enough disk space to store the file.

A request that authenticates with the session cookie and changes state must send the value of the `ingot_csrf` cookie in the `X-CSRF-Token` header, or it answers `403`. Requests with an `Authorization` header are exempt.

## Directory index

A `GET` or `HEAD` request on a directory of a repository answers with an HTML page. There is no setting to turn it off, and there is no other format. The JSON listing is part of the API.

| Part | Content |
|------|---------|
| Content type | HTML. |
| Title and heading | `Index of <base path><request path>`. |
| `<base href>` | The base path and the request path, with a trailing slash. |
| First entry | `Parent Directory`, linked to the parent path. |
| Other entries | One list item per entry. Directories carry a trailing slash in their link and text. Each item has the class `directory` or `file`. |
| Entries shown | Only entries the caller may browse. |

The page shows the request path decoded. Links use the percent encoded path. File names and the path are escaped for HTML.

A bare `/{repository}` path has no `<gav>` and does not match the Maven route, so it gets no index. The listing of repositories and of a repository root is part of the API.

{% hint style="warning" %}
Not verified: whether `/{repository}/` with a trailing slash matches the Maven route and lists the repository root.
{% endhint %}

## Badges

`GET /api/badge/latest/{repository}/<gav>` answers with an SVG badge for the latest version of an artifact.

`<gav>` is the artifact path without the version, for example `org/example/lib`. Ingot reads the `maven-metadata.xml` in that directory of the repository, applies the filter, sorts the versions and takes the last one.

| Query parameter | Default | Meaning |
|-----------------|---------|---------|
| `name` | The `id` of the frontend settings | Text on the left side of the badge. |
| `prefix` | Empty | Text put in front of the version on the right side. |
| `color` | `4c1` | Background color of the right side, as a hex value without `#`. |
| `filter` | None | Version filter. `has:<text>` keeps versions that contain the text. `none:<text>` drops versions that contain the text. Any other value keeps versions that start with it. |

The response has the content type `image/svg+xml` and `Content-Disposition: inline; filename="latest-badge.svg"`. The text of the badge is `name` on the left and `prefix` plus the version on the right.

| Status | When |
|--------|------|
| `400` | `name`, the value or `color` contains a character other than letters, digits, whitespace and `: - . , + = ( ) [ ]`. |
| `404` | The repository is unknown, or the artifact has no `maven-metadata.xml` or no version that matches. |
| `401`, `403` | The caller may not read the path. |

The same lookup is available as JSON or plain text at `/api/maven/latest/version/{repository}/<gav>`, which is part of the API.

## Javadoc

Ingot serves the contents of javadoc jars that are stored in a repository.

| Route | Result |
|-------|--------|
| `GET /javadoc/{repository}/<gav>` | The javadoc viewer page, or a file of the unpacked javadoc. |
| `GET /javadoc/{repository}/<gav>/raw/<resource>` | A file of the unpacked javadoc. |

Both routes answer `404` when the `javadoc` settings domain has `enabled` set to `false`. A successful response carries `Content-Security-Policy: sandbox allow-scripts` and UTF-8 as the character set.

### How the viewer route resolves `<gav>`

1. A `<gav>` that ends with a slash is redirected to the same path without the slash.
2. The access check runs on `<gav>` in the repository.
3. When `<gav>` contains `/latest`, the part before the last `/latest` is taken as the artifact path. Its latest version replaces `/latest`. When no version is found, `<gav>` is used as it is.
4. When `<gav>` ends in `html`, `css`, `js`, `json`, `png` or `svg`, the answer is that file from the `javadocs` cache directory in the working directory, with the content type of the extension. By the code, a file that is not in the cache answers `200` with an empty body.
5. When `<gav>` contains `/resources/`, the answer is `404` with the message that resources are unavailable before extraction.
6. Otherwise the javadoc jar is found from `<gav>`:
    * A `<gav>` whose extension equals the extension of a configured suffix, for example `jar`, is used as the jar.
    * Any other `<gav>` is treated as a version directory. Ingot looks for `<artifactId>-<version><suffix>` in it, for each suffix in the order they are configured. The first suffix with an existing file wins. When none exists, the first suffix is used. For a `-SNAPSHOT` version, the version in the file name comes from `maven-metadata.xml`.
7. The jar is copied out of the repository and unpacked into the `javadocs` directory once. The next request reads the unpacked copy.
8. The response is the viewer page. It lists the versions of the artifact that have a javadoc file, and it shows the unpacked javadoc in a frame. A link on the page opens the same address with `/raw/index.html` appended.

A deploy of a file that ends with a configured suffix evicts the unpacked copy of that artifact directory.

A jar `<gav>` that does not end with a configured suffix answers `404`. An invalid `<gav>` answers `400`. A jar without an `index.html` in its root, or a jar over the limits answers an error and is recorded as a failure. The limits are 2 MiB per entry, 50 MiB in total and 50 000 entries.

The raw route reads `<resource>` from the unpacked javadoc of `<gav>` and unpacks the jar first when needed. The content type comes from the extension: `html`, `css`, `js`, `json`, `png` or `svg`. Other extensions answer as `application/octet-stream`. A missing resource answers `404`.

{% hint style="warning" %}
Not verified: which of the two javadoc routes answers `/javadoc/{repository}/<gav>/raw/<resource>`. The viewer route is registered first and its `<gav>` also matches paths that contain `/raw/`.
{% endhint %}

### Settings

The `javadoc` shared settings domain holds these keys. Read and write it at `/api/settings/domain/javadoc`, or in the dashboard settings.

| Key | Default | Meaning |
|-----|---------|---------|
| `enabled` | `true` | Turns the javadoc routes on or off. |
| `suffixes` | `-javadoc.jar`, `-groovydoc.jar` | File name suffixes that count as javadoc jars. |

## Health and status

| Method and path | Authentication | Response |
|-----------------|----------------|----------|
| `GET /api/status/health` | None | `200` with `{"status":"UP"}` when the web server is started. `503` with `{"status":"DOWN"}` when it is not. |
| `GET /api/status/instance` | Manager | `version`, `latestVersion`, `uptime`, `usedMemory`, `maxMemory`, `usedThreads`, `maxThreads`, `failuresCount`. |
| `GET /api/status/snapshots` | Manager | An array of `{at, memory, threads}`. |
| `GET /api/status/failures` | Manager | `{"failures": [...]}` with `id`, `timestamp`, `source`, `type`, `message`, `stacktrace` and `count` per failure, newest first. |
| `DELETE /api/status/failures` | Manager | Clears the recorded failures. |

A manager endpoint answers `401` without valid credentials and `403` for a caller who is not a manager. There is no `/api/status/ready` route.

## OpenAPI

| Path | Content | Switch |
|------|---------|--------|
| `/openapi` | The OpenAPI document of the backend, as JSON. | None. The plugin is always registered. |
| `/swagger` | Swagger UI for that document. | The optional Swagger plugin must be installed. |

The title and description of the document come from the `title` and `description` of the frontend settings. The version is the Ingot version.

Ingot adds no authentication to these paths.

{% hint style="warning" %}
Not verified: the two paths are the defaults of the Javalin OpenAPI and Swagger plugins. The backend code sets neither. Also not verified: whether the container image contains the Swagger plugin. It is a separate jar and the server jar does not include it.
{% endhint %}

## Dashboard routes

The dashboard is a single page application. These routes answer with the bundled `index.html`, with the `{{REPOSILITE.*}}` placeholders replaced. They exist only while `defaultFrontend` is `true`. The path is relative to the base path.

| Route | Sub-paths |
|-------|-----------|
| `/` and `/index.html` | None. |
| `/browse` | `/browse/<path>` |
| `/search` | None. |
| `/dashboard` | None. |
| `/tokens` | `/tokens/{name}`, with one segment after `/tokens/`. |
| `/console` | None. |
| `/maintenance` | None. |
| `/audit` | None. |
| `/settings` | `/settings/<path>` |
| `/access` | `/access/<path>` |
| `/account` | None. |
| `/assets/<path>` | Bundled files. `GET` only. |

All of these answer `GET` and `HEAD` except `/assets/<path>`. A path that is not in this list is not served the dashboard shell. There is no catch-all.

### A repository with the id of a dashboard route

A repository id can equal the first segment of a dashboard route, for example `settings`. The dashboard routes are registered before the Maven route, so the dashboard route matches first. When no repository has that id, the dashboard answers every `GET` and `HEAD`. When a repository has that id, the dashboard route hands the request on:

| Request | Answered by |
|---------|-------------|
| `HEAD` | The repository. |
| `GET` with `Sec-Fetch-Mode: navigate` and `Sec-Fetch-Dest: document` (a browser opening the address) | The dashboard. |
| Any other `GET` | The repository, with the same checks as the Maven route. |
| `PUT`, `POST`, `DELETE` | The repository. Dashboard routes do not claim these methods. |
| `GET` on the bare route, such as `/settings`, when it is not a browser navigation | `404`. The Maven route needs a `<gav>`. |

The repository is looked up on every request, so adding or removing it takes effect without a restart. On these paths the response has a `Vary` header that includes `Sec-Fetch-Mode` and `Sec-Fetch-Dest`. A dashboard route without a repository of the same id never gets this header.

Ingot does not reject such a repository id. It logs a warning at startup and whenever the repository settings change. The contents of the repository stay reachable in the dashboard under `/browse/<repository>`.

### Static directory

The directory `static` in the working directory holds files that the server serves next to the dashboard. On start, Ingot creates the directory when it is missing and copies `index.html` into it. It copies `favicon.png` into it when that file is missing.

| Route | Source |
|-------|--------|
| `GET /<file>` | Each file in the `static` directory, for example `/favicon.png`. |
| `GET /<directory>/<path>` | Each subdirectory of `static`. |
| `GET /` | `static/index.html`, but only when `defaultFrontend` is `false`. |

The routes are built at startup from the entries of the directory, so a new entry needs a restart. A resource whose name contains `.html` or `.js` has its placeholders replaced.

### Public dashboard settings

`GET /api/frontend/settings` answers without authentication. It returns the values a dashboard needs to render itself: the placeholder values, `id`, `title`, `description`, the organization and legal links, the appearance settings and `secretsHidden`. It is registered even when `defaultFrontend` is `false`, so a separately deployed dashboard can read it.

## 404 page

A request that no route answers, and every `404` of the Maven route, gets an HTML page:

* The title is `Ingot - 404 Not Found` and the heading is `Resource not found`.
* The reason is shown when there is one. Characters other than letters, digits, `/`, `.`, `-`, `_` and space are removed from it.
* The page links to `<base path>browse/<requested path>` in the dashboard.

The following examples are answered by this page:

| Request | Status |
|---------|--------|
| `GET /unknown` | `404` |
| `GET /unknown/file.jar`, where `unknown` is no repository | `404`, reason `Repository unknown not found` |
| `GET /releases/missing/file.jar` | `404` |
| A dashboard route while `defaultFrontend` is `false` | `404` |

## Settings that change the routes

| Setting | Where | Default | Effect |
|---------|-------|---------|--------|
| `defaultFrontend` | Local configuration. Environment variable `INGOT_LOCAL_DEFAULTFRONTEND`. | `true` | `false` removes `/`, `/index.html`, `/assets/<path>` and the dashboard routes. `/api/frontend/settings` stays. The reserved paths are then answered by the repositories. |
| `basePath` | Local configuration. Environment variable `INGOT_LOCAL_BASEPATH`. | `/` | See [Base path and forwarded prefix](#base-path-and-forwarded-prefix). |
| `bypassExternalCache` | Local configuration. Environment variable `INGOT_LOCAL_BYPASSEXTERNALCACHE`. | `true` | Adds `Pragma: no-cache`, `Expires: 0` and `Cache-Control: no-cache, no-store, must-revalidate, max-age=0` to every `/api/*` response. |
| `enforceSsl` | Local configuration. Environment variable `INGOT_LOCAL_ENFORCESSL`. | `false` | Redirects HTTP requests to HTTPS on `sslPort`. |
| `forwardedPrefix` | Shared settings domain `web`. | Empty | See [Base path and forwarded prefix](#base-path-and-forwarded-prefix). |
| `javadoc.enabled`, `javadoc.suffixes` | Shared settings domain `javadoc`. | See [Javadoc](#settings) | Turns the javadoc routes on or off and sets the jar suffixes. |
| `reposilite.maven.maxAge` | System property. | `3600` | The `max-age` of cacheable Maven files, in seconds. |
| `reposilite.redirect.default-repository` | System property. | Empty | See [Redirect to a default repository](#redirect-to-a-default-repository). |

The `INGOT_LOCAL_` variables also exist with the `REPOSILITE_LOCAL_` prefix, and the system properties of the local configuration exist as `ingot.local.<key>` and `reposilite.local.<key>`. When both prefixes set the same key, the `INGOT_` one wins.

### Base path and forwarded prefix

`basePath` does not move any route. The server answers at the root whatever the value is, and a reverse proxy has to remove the prefix before it forwards the request. The value is normalized to start and end with a slash. The setting changes these generated values:

* The `{{REPOSILITE.BASE_PATH}}` and `{{REPOSILITE.VITE_BASE_PATH}}` placeholders of the dashboard.
* The title, the heading, the `<base href>` and the links of the directory index.
* The link to the dashboard on the 404 page.
* The `Path` of the session cookies, the OIDC cookies and redirects, and the OIDC redirect URI.

`forwardedPrefix` names a request header, for example `X-Forwarded-Prefix`. It changes the directory index and the 404 page only:

| `forwardedPrefix` | Request | Base path used |
|-------------------|---------|----------------|
| Empty | Any | The configured `basePath`. |
| A header name | The request has that header with a value | The configured `basePath`. The value of the header is not used. |
| A header name | The request does not have that header | `/`. |

### Redirect to a default repository

When the system property `reposilite.redirect.default-repository` names a repository, Ingot reads the top level entries of that repository at startup. For each entry, for example `org`, it registers `GET` and `HEAD` on `/org/<gav>`. These routes answer like `/<default-repository>/org/<gav>`. There is no `ingot.` spelling of this property.

## Related

* [Artifact permission nodes](artifact-permission-nodes.md) for the read right on directories and files.
* [Search filters and API](search-filters-and-api.md) for the `browseLink` of a search result, which points at `/browse/<repository>/<path>`.
