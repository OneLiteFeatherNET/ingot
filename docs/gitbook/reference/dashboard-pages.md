---
description: Reference for every dashboard route of Ingot. Lists the path, the page it opens, who can open it, and the source file that defines it, plus which paths the server answers itself.
---

# Dashboard pages

The dashboard is a single-page app. This page lists its routes, the permission each route asks for, and the paths the server answers with the app shell.

Two sources define the routes:

* The backend list `APP_ROUTES` in `reposilite-backend/src/main/kotlin/com/reposilite/frontend/DashboardPaths.kt`. It decides which paths the server answers with `index.html`.
* The router table `ROUTES` in `reposilite-frontend/src/router/routes.js`. It decides which page the app shows for a path.

All paths are relative to the base path of the instance (`basePath`, default `/`). The router uses clean URLs (HTML5 history), not `#/` URLs. See [Old hash links](#old-hash-links).

## Routes

In the table, "Router" means `reposilite-frontend/src/router/routes.js` and "Backend" means `DashboardPaths.kt`. `:name` marks a path parameter in the router. The "Who can open it" column describes the client-side check in the router. The server does not check a page route. See [What the server checks](#what-the-server-checks).

| Route path | Route name | Page | Who can open it (client-side) | Defined in |
| --- | --- | --- | --- | --- |
| `/` | `overview` | Overview. Lists the repositories. | Everyone, no sign-in needed. | Router. The backend serves `/` with its own handler, not through `APP_ROUTES`. |
| `/browse` and `/browse/<path>` | `browse` | Overview section. Shows the folder listing of the repository path. `/browse` alone shows the repository list. | Everyone, no sign-in needed. | Router (`/browse/:qualifier(.*)*`) and Backend (`/browse`, `/browse/<path>`) |
| `/search` | `search` | Search. The artifact search. The query is kept in the address. | Everyone, no sign-in needed. | Router and Backend |
| `/account` | `account` | My account. Profile, password and the tokens the user created. | Signed-in principals. Client-side only: the page shows a sign-in prompt to a visitor. | Router and Backend |
| `/dashboard` | `dashboard` | Dashboard. Sections **Instance status**, **Resources** and **Traffic**, and a getting started card. | Node `ingot.admin.dashboard`. | Router and Backend |
| `/access` | `access` | No page of its own. Redirects to the first of **Users**, **Machine accounts** or **Teams** that the user may open, or to **Users** if none matches. | Any one of `ingot.admin.users`, `ingot.admin.tokens`, `ingot.admin.teams`. | Router and Backend (`/access`) |
| `/access/users` | `access-users` | Access, tab **Users**. | Node `ingot.admin.users`. | Router. Backend (`/access/<path>`) |
| `/access/users/:name` | `access-user` | Access, tab **Users**, detail of one user. | Node `ingot.admin.users`. | Router. Backend (`/access/<path>`) |
| `/access/machines` | `access-machines` | Access, tab **Machine accounts**. | Node `ingot.admin.tokens`. | Router. Backend (`/access/<path>`) |
| `/access/machines/:name` | `access-machine` | Access, tab **Machine accounts**, detail of one machine account. | Node `ingot.admin.tokens`. | Router. Backend (`/access/<path>`) |
| `/access/teams` | `access-teams` | Access, tab **Teams**. | Node `ingot.admin.teams`. | Router. Backend (`/access/<path>`) |
| `/access/teams/:name` | `access-team` | Access, tab **Teams**, detail of one team. | Node `ingot.admin.teams`. | Router. Backend (`/access/<path>`) |
| `/access/check` | `access-check` | Access, tab **Check**. The permission check. `principal`, `node` and `token` come from the query string. | Any one of `ingot.admin.users`, `ingot.admin.tokens`, `ingot.admin.teams`. | Router. Backend (`/access/<path>`) |
| `/access/sso` | `access-sso` | Access, tab **SSO**. The single sign-on providers. | Node `ingot.admin.*` (full instance admin). | Router. Backend (`/access/<path>`) |
| `/access/ldap` | `access-ldap` | Access, tab **LDAP**. The LDAP provider. | Node `ingot.admin.*` (full instance admin). | Router. Backend (`/access/<path>`) |
| `/tokens` | `tokens` | Redirects to `/access/machines`. Keeps old links working. | Node `ingot.admin.tokens`. | Router and Backend |
| `/tokens/new` | `token-new` | Redirects to `/access/machines`. | Node `ingot.admin.tokens`. | Router. Backend (`/tokens/{name}`) |
| `/tokens/:name` | `token` | Redirects to `/access/machines/:name`. | Node `ingot.admin.tokens`. | Router. Backend (`/tokens/{name}`) |
| `/console` | `console` | Console. The remote console. The query keys `level` and `q` hold the level switches and the text filter. | Node `ingot.admin.console`. | Router and Backend |
| `/maintenance` | `maintenance` | Maintenance. Storage usage, search index and cleanup policies. | Node `ingot.admin.maintenance`. | Router and Backend |
| `/audit` | `audit` | Audit log. The audit events. The filters are kept in the query string. | Node `ingot.admin.audit`. | Router and Backend |
| `/settings` and `/settings/:domain` | `settings` | Settings. The shared configuration. The optional `:domain` selects a settings domain. | Node `ingot.admin.settings`. | Router (`/settings/:domain?`) and Backend (`/settings`, `/settings/<path>`) |
| `/settings/:domain/:collection/:entry` | `settings-entry` | Settings, one entry of a collection in a domain. | Node `ingot.admin.settings`. | Router. Backend (`/settings/<path>`) |
| any other path (`/:pathMatch(.*)*`) | `not-found` | Not-found view, rendered by the app. | Everyone. | Router only. See [Unlisted paths](#unlisted-paths). |

Rules for the "Who can open it" column:

* A node is allowed when an entry of the session's node list equals it, or matches it with `*` in place of a segment. A lone `*` entry allows every node. The check is in `reposilite-frontend/src/store/nodeAccess.js`.
* A session that has no node list falls back to the instance admin flag.
* The labels of the navigation (**Overview**, **Search**, **Dashboard**, **Access**, **Console**, **Maintenance**, **Audit**, **Settings**) are defined in `reposilite-frontend/src/pages/index-tabs.js`. A section is left out of the navigation when the user lacks its node. The title of `/account` is **My account**.
* The tab labels of **Access** (**Users**, **Machine accounts**, **Teams**, **SSO**, **LDAP**, **Check**) are defined in `reposilite-frontend/src/components/access/AccessView.vue`. A tab is left out when the user lacks its node.

### Client-side guards

The router runs two guards, in `reposilite-frontend/src/router/create.js` and `reposilite-frontend/src/router/guard.js`.

| Guard | Applies to | Behavior |
| --- | --- | --- |
| Password change | Every route except `account` | While the server asks the principal for a new password, the router redirects to `account`. |
| Admin route | Routes whose meta marks them as admin pages (`manager: true`): `dashboard`, all `access-*` routes, `tokens`, `token-new`, `token`, `console`, `maintenance`, `audit`, `settings`, `settings-entry` | Waits until the session is known. A user with the node goes in. An anonymous visitor stays on the URL and sees the prompt **Sign in to open** the section, with a **Sign in** button, and the sign-in dialog opens. A signed-in user without the node is redirected to `overview` with the notice "You do not have access to that page". |

The `account` route is not an admin route. The sign-in prompt for it comes from the page component `reposilite-frontend/src/pages/IndexPage.vue`, not from the router guard.

{% hint style="warning" %}
These guards run in the browser. They decide what the dashboard shows. They do not protect data. The server checks each API call on its own. See [What the server checks](#what-the-server-checks).
{% endhint %}

## Paths the server answers

The server serves the app shell only while `defaultFrontend` is `true` (the default, in `LocalConfiguration.kt`). With `defaultFrontend` set to `false`, this instance registers none of the routes in this section. The route `/api/frontend/settings` stays registered in both cases.

With `defaultFrontend` on, `ResourcesFrontendHandler` (`reposilite-backend/src/main/kotlin/com/reposilite/frontend/infrastructure/FrontendHandler.kt`) answers:

| Path | Methods | Answer |
| --- | --- | --- |
| `/` | GET, HEAD | `index.html` |
| `/index.html` | GET, HEAD | `index.html` |
| `/assets/<path>` | GET | The bundled asset. |
| Every pattern in `APP_ROUTES` | GET, HEAD | `index.html`, unless a repository with the same first segment decides otherwise. See [Repository ids that collide with a route](#repository-ids-that-collide-with-a-route). |

`APP_ROUTES` holds these patterns:

| Pattern | Matches |
| --- | --- |
| `/browse` | The repository list. |
| `/browse/<path>` | Any path below `/browse`. |
| `/search` | The search page. |
| `/dashboard` | The dashboard page. |
| `/tokens` | The old token page. |
| `/tokens/{name}` | One segment below `/tokens`, for example `/tokens/new`. |
| `/console` | The console page. |
| `/maintenance` | The maintenance page. |
| `/audit` | The audit page. |
| `/settings` | The settings page. |
| `/settings/<path>` | Any path below `/settings`. |
| `/access` | The access page. |
| `/access/<path>` | Any path below `/access`. |
| `/account` | The account page. |

The backend serves every route with the same `index.html`. It does not render a page and does not check a permission for these paths. The app in the browser reads the path and shows the page.

### Unlisted paths

The backend has no catch-all that returns `index.html`. A request for a path that no route claims gets the server's not-found page (`NotFoundHandler`, status 404). The `not-found` route of the router renders only after navigation inside the app.

A path can be in the backend list but not in the router. The backend accepts any path below `/access`, `/settings` and `/browse`. For example `/access/unknown` returns `index.html`, and the router has no `/access/unknown`, so the app shows its not-found view.

### Differences between the two sources

| Path | Backend `APP_ROUTES` | Router `ROUTES` |
| --- | --- | --- |
| `/` | No (served by a separate handler) | Yes (`overview`) |
| `/browse`, `/browse/<path>` | Yes | Yes (one pattern) |
| `/search`, `/account`, `/dashboard`, `/console`, `/maintenance`, `/audit` | Yes | Yes |
| `/tokens`, `/tokens/{name}` | Yes | Yes (`tokens`, `token-new`, `token`; all redirects) |
| `/settings`, `/settings/<path>` | Yes | Yes (`settings` takes none or one segment below `/settings`, `settings-entry` takes three) |
| `/access`, `/access/<path>` | Yes | Yes (`access` and eight routes below it) |
| `/index.html`, `/assets/<path>` | Served, but not in `APP_ROUTES` | No |
| `/:pathMatch(.*)*` | No | Yes (`not-found`) |

### Repository ids that collide with a route

The first segment of each route is a reserved segment: `browse`, `search`, `dashboard`, `tokens`, `console`, `maintenance`, `audit`, `settings`, `access` and `account`. The server also reserves `api` and `assets`. A repository can still use one of these ids. `DashboardRouteDecision` decides who answers a request on such a path:

| Request | Answered by |
| --- | --- |
| No repository has that id | The dashboard. |
| HEAD | The repository. |
| A top-level browser navigation (`Sec-Fetch-Mode: navigate` and `Sec-Fetch-Dest: document`) | The dashboard. |
| Any other request | The repository. |

At startup the server logs a warning for each repository whose id equals a reserved segment. It does not reject the configuration. The contents of such a repository stay reachable in the dashboard under `/browse/<id>`. For the segments `api` and `assets`, the warning says that GET requests may be answered by the dashboard or the API instead of the repository.

## What the server checks

A page route returns the same file to everyone. The data on each page comes from `/api` endpoints, and those endpoints check the caller. The table lists what the code checks. The router permission and the server check are not always the same.

| Page | Endpoints the server checks | Server check |
| --- | --- | --- |
| Overview, Search | Public maven and search endpoints | Repository visibility decides what the caller sees. |
| My account | `/api/identity/me/*` | The caller must be authenticated. |
| Dashboard | `/api/status/instance`, `/api/status/snapshots`, `/api/status/failures`, `/api/statistics/resolved/unique`, `/api/statistics/resolved/all` | Instance admin (`managerOnly`). |
| Access, tab **Users** | `/api/identity/principals` (list, create) and the other `users` endpoints | Node `ingot.admin.users`. |
| Access, tab **Machine accounts** | `/api/identity/principals` (list), and `/api/identity/principals/{name}/tokens` (list, create, delete) | The principal list checks `ingot.admin.users`. The token endpoints check `ingot.admin.tokens`. |
| Access, tab **Teams** | The `/api/identity/teams` endpoints | Node `ingot.admin.teams`. |
| Access, tab **Check** | `/api/identity/check` | Authenticated. Instance admins may check any principal, others only their own. |
| Access, tabs **SSO** and **LDAP** | `/api/settings/domain/{name}` (PUT), `/api/auth/providers/oidc/{id}/test`, `/api/auth/providers/ldap/test` | Instance admin (`managerOnly`). |
| Console | `/api/console/execute`, `/api/console/log` | Instance admin. |
| Maintenance | `/api/maven/storage`, `/api/maven/search/index` and its `rebuild`, `verify` and `cancel` endpoints, `/api/cleanup/*` | Instance admin (`managerOnly`). |
| Audit | `/api/audit`, `/api/audit/export` | Instance admin (`managerOnly`). |
| Settings | `/api/settings/domains`, `/api/settings/schema/{name}`, `/api/settings/domain/{name}` | Instance admin (`managerOnly`). |

"Instance admin" here means the principal is allowed `ingot.admin.*` (`PermissionEvaluator.isInstanceAdmin`), after token narrowing. A principal that holds only a single area node such as `ingot.admin.audit` can open the **Audit** route in the router, but the audit endpoints still answer 403. Only the `users`, `tokens` and `teams` areas are checked per area on the server (`managerOnly("users")`, `managerOnly("tokens")`, `managerOnly("teams")` in `IdentityApiEndpoints.kt`).

{% hint style="warning" %}
The mapping of each page to the exact endpoints it calls was read from the endpoint code and the API client (`reposilite-frontend/src/store/client.js`), not from a running instance. Check the page behavior on your own instance before you rely on a single-area admin token for the pages **Dashboard**, **Console**, **Maintenance**, **Audit** and **Settings**.
{% endhint %}

## Old hash links

The router resolves old `#/<path>?tab=<section>` links on the first navigation (`reposilite-frontend/src/router/legacy.js`). The mapping:

| Old link | Opens |
| --- | --- |
| `#/...` with `tab` equal to `dashboard`, `tokens`, `console` or `settings` | The route of that name. Other query keys are kept. |
| `#/<path>` with any other or no `tab` | `browse` with the path, or `overview` if the path is empty. |
| Anything that does not start with `#/` | Not handled. |

## Related

* [Search filters and API](search-filters-and-api.md)
* [Artifact permission nodes](artifact-permission-nodes.md)
* [Review the audit log](../how-to/review-audit-log.md)
