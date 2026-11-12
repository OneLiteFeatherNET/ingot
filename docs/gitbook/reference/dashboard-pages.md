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
| `/` | `overview` | Browse. Lists the repositories. | Everyone, no sign-in needed. | Router. The backend serves `/` with its own handler, not through `APP_ROUTES`. |
| `/browse` and `/browse/<path>` | `browse` | Repositories section. Shows the folder listing of the repository path. `/browse` alone shows the repository list. | Everyone, no sign-in needed. | Router (`/browse/:qualifier(.*)*`) and Backend (`/browse`, `/browse/<path>`) |
| `/search` | `search` | Search. The artifact search. The query is kept in the address. | Everyone, no sign-in needed. | Router and Backend |
| `/account` | `account` | My account. Profile, password and the tokens the user created. | Signed-in principals. Client-side only: the page shows a sign-in prompt to a visitor. | Router and Backend |
| `/dashboard` | `dashboard` | Dashboard. Sections **Instance status** and **Resources**, and a getting started card. | Node `ingot.admin.dashboard`. | Router and Backend |
| `/analytics` | `analytics` | Analytics. Section **Traffic**: the resolved requests per repository over time. | Node `ingot.admin.dashboard`. | Router and Backend |
| `/access` | `access` | No page of its own. When the user may open exactly one Access page, the address is replaced with that page. Otherwise a screen of 600px or more is forwarded to the first of **Users**, **Machine accounts** or **Teams** that the user may open (or to **Users** if none matches), and a narrower screen shows the index of the Access pages. | Any one of `ingot.admin.users`, `ingot.admin.tokens`, `ingot.admin.teams`. | Router and Backend (`/access`) |
| `/access/users` | `access-users` | Access, tab **Users**. | Node `ingot.admin.users`. | Router. Backend (`/access/<path>`) |
| `/access/users/:name` | `access-user` | Access, tab **Users**, detail of one user. | Node `ingot.admin.users`. | Router. Backend (`/access/<path>`) |
| `/access/machines` | `access-machines` | Access, tab **Machine accounts**. | Node `ingot.admin.tokens`. | Router. Backend (`/access/<path>`) |
| `/access/machines/:name` | `access-machine` | Access, tab **Machine accounts**, detail of one machine account. | Node `ingot.admin.tokens`. | Router. Backend (`/access/<path>`) |
| `/access/teams` | `access-teams` | Access, tab **Teams**. | Node `ingot.admin.teams`. | Router. Backend (`/access/<path>`) |
| `/access/teams/:name` | `access-team` | Access, tab **Teams**, detail of one team. | Node `ingot.admin.teams`. | Router. Backend (`/access/<path>`) |
| `/access/check` | `access-check` | Access, tab **Check**. The permission check. `principal`, `node` and `token` come from the query string. | Any one of `ingot.admin.users`, `ingot.admin.tokens`, `ingot.admin.teams`. | Router. Backend (`/access/<path>`) |
| `/access/sso` | `access-sso` | Access, tab **SSO**. The single sign-on providers. | Node `ingot.admin.*` (full instance admin). | Router. Backend (`/access/<path>`) |
| `/access/ldap` | `access-ldap` | Access, tab **LDAP**. The LDAP sign in to the dashboard, and the LDAP of Maven and Gradle clients (`authentication` settings domain). | Node `ingot.admin.*` (full instance admin). | Router. Backend (`/access/<path>`) |
| `/access/security` | `access-security` | Access, tab **Security**. The brute force protection (`authentication` settings domain). | Node `ingot.admin.*` (full instance admin). | Router. Backend (`/access/<path>`) |
| `/tokens` | `tokens` | Redirects to `/access/machines`. Keeps old links working. | Node `ingot.admin.tokens`. | Router and Backend |
| `/tokens/new` | `token-new` | Redirects to `/access/machines`. | Node `ingot.admin.tokens`. | Router. Backend (`/tokens/{name}`) |
| `/tokens/:name` | `token` | Redirects to `/access/machines/:name`. | Node `ingot.admin.tokens`. | Router. Backend (`/tokens/{name}`) |
| `/console` | `console` | Console. The remote console. The query keys `level` and `q` hold the level switches and the text filter. | Node `ingot.admin.console`. | Router and Backend |
| `/maintenance` | `maintenance` | Maintenance. Storage usage, search index and cleanup policies. | Node `ingot.admin.maintenance`. | Router and Backend |
| `/audit` | `audit` | Audit log. The audit events. The filters are kept in the query string. | Node `ingot.admin.audit`. | Router and Backend |
| `/settings` and `/settings/:domain` | `settings` | Settings. The shared configuration. Without `:domain` the page is the settings hub; the optional `:domain` opens that settings domain. When the user sees only one domain, `/settings` is replaced with that domain. | Node `ingot.admin.settings`. | Router (`/settings/:domain?`) and Backend (`/settings`, `/settings/<path>`) |
| `/settings/plugins/<plugin>/<page>` | `settings-plugin` | A dashboard page a server plugin contributed, drawn from the blocks the plugin registered. See [Plugin API](plugin-api.md#dashboard-pages). | The area the plugin gave the page, or the instance admin for `*`. Checked by the dashboard and by the server. | Router and Backend (below `/settings`, so no change is needed in `APP_ROUTES`) |
| `/settings/repositories` | `settings-repositories` | Administration, Repository, **Repositories**. The table of the repositories of the `maven` domain, and below it the **Maven options** of the domain. `?new=repository` opens the wizard once. | Node `ingot.admin.settings`. | Router. Backend (`/settings/<path>`) |
| `/settings/repositories/:id` | `settings-repository` | Repositories, the page of one repository. | Node `ingot.admin.settings`. | Router. Backend (`/settings/<path>`) |
| `/settings/maven` and `/settings/maven/repositories` | `settings-maven-repositories` | The Maven page is retired. Both addresses redirect to `/settings/repositories`, and `/settings/maven` keeps its query. | Node `ingot.admin.settings`, checked on the Repositories page. | Router. Backend (`/settings/<path>`) |
| `/settings/:domain/:collection/:entry` | `settings-entry` | Settings, one entry of a collection in a domain. `/settings/maven/repositories/<id>` redirects to `/settings/repositories/<id>`. | Node `ingot.admin.settings`. | Router. Backend (`/settings/<path>`) |
| `/settings/login` | `settings-login` | Redirects to `/access/sso`. The sign in settings are edited under Access. | Node `ingot.admin.*` (full instance admin). | Router. Backend (`/settings/<path>`) |
| `/settings/system-info` | `system-info` | System information. The version and build, the JVM, the operating system, the database kind and endpoint, the storage, the cluster, the plugins, and the redacted environment and configuration of the instance. Listed under Administration, in the group Support. | Node `ingot.admin.console`, and only while the support bundle is on. | Router and Backend (`/settings/<path>`) |
| `/settings/support` | `support` | Support bundle. Downloads the zip of the answering instance and states what the contract removes. Listed under Administration, in the group Support. | Node `ingot.admin.console`, and only while the support bundle is on. | Router and Backend (`/settings/<path>`) |
| `/settings/authentication` | `settings-authentication` | Redirects to `/access/ldap`. The authentication settings are edited under Access. | Node `ingot.admin.*` (full instance admin). | Router. Backend (`/settings/<path>`) |
| any other path (`/:pathMatch(.*)*`) | `not-found` | Not-found view, rendered by the app. | Everyone. | Router only. See [Unlisted paths](#unlisted-paths). |

Rules for the "Who can open it" column:

* A node is allowed when an entry of the session's node list equals it, or matches it with `*` in place of a segment. A lone `*` entry allows every node. The check is in `reposilite-frontend/src/store/nodeAccess.js`.
* A session that has no node list falls back to the instance admin flag.
* The labels of the navigation are defined in `reposilite-frontend/src/navigation/registry.js`. The route names do not change: `overview` is labelled **Browse** and `audit` is labelled **Audit log**. The title of `/account` is **My account**.
* The rail and the bottom bar show four entries, each only when the visitor may open it: **Browse** (`/`), **Search**, **Dashboard** and **Administration** (`/settings`). An anonymous visitor sees **Browse** and **Search** only. **Administration** is shown when the visitor may open any Administration page. The bottom bar on a narrow screen has no **More** item.

### Administration tree

**Administration** groups its pages under four headings: **Repository**, **Security**, **System** and **Support**. Every Administration page has the heading **Administration**, with a line under it that describes the page, and shows the same side navigation. The settings domains the visitor may open are part of that navigation on every page. The current page carries `aria-current="page"`, and the last crumb of the breadcrumb names it. A page is listed only when it exists and the visitor may open it. A group without a listed page is left out. When only one Administration page is permitted, `/settings` forwards to that page.

### Layout at four widths

The Administration pages use one layout at four widths. Below 600 pixels the navigation is the bottom bar, and the Administration tree is an index with a back arrow on each page. From 600 to 1239 pixels the rail is at the left, and the tree is still an index. From 1240 pixels the tree stands beside the page at 208 pixels wide. It stays in view while the page scrolls, below the top bar and above the save bar, and scrolls inside itself when it is taller than the window.

The pages are of two kinds. A **wide** page is a list or a table, such as Repositories, Access, Audit and the file browser listing. It takes the full width after the side navigation at every width, and the table shows more columns as the window grows: Version policy and Visibility from 1920 pixels, and Storage from 2560 pixels. A **reading** page is text or a form, such as the settings domains, Maintenance, Account, the migration steps and the system information. It keeps a measure of at most 960 pixels, at the start of the content column, so a long line does not stretch across a large monitor. The dashboard lays its charts out in two columns from 1024 pixels and in three from 2560 pixels, and the repository overview puts the browse list beside the snippet card, the list taking two thirds of the width, from 1920 pixels.

The heading and the page actions form one row above the columns from 600 pixels. The row is not sticky: it scrolls away with the page, and the top bar stays at the top of the window.

| Group | Pages and addresses |
| --- | --- |
| **Repository** | **Repositories** (`/settings/repositories`, which also edits the `maven` domain), **Cleanup policies** (`/settings/cleanup`), **Javadoc** (`/settings/javadoc`) |
| **Security** | **Users** (`/access/users`), **Machine accounts** (`/access/machines`), **Teams** (`/access/teams`), **SSO** (`/access/sso`), **LDAP** (`/access/ldap`), **Security settings** (`/access/security`), **Permission check** (`/access/check`), **Audit log** (`/audit`) |
| **System** | Every other settings domain, including the domains that plugins add, each at `/settings/<domain>` |
| **Support** | **Console** (`/console`), **Maintenance** (`/maintenance`), **Analytics** (`/analytics`) |

On a screen narrower than 600px the tree is an index list of the groups. A page opened from it shows a back arrow that returns to the index.

### Redirects to the Repositories page

Repositories used to be edited under **Settings**, then **Maven**. The Maven page is retired, and everything an operator does with it is on the Repositories page. Those addresses keep working:

| Old address | Opens |
| --- | --- |
| `/settings/maven` | `/settings/repositories`, with the query kept. |
| `/settings/maven/repositories` | `/settings/repositories`. |
| `/settings/maven/repositories/<id>` | `/settings/repositories/<id>`, the page of the same repository. |
| `/settings/maven?new=repository` | `/settings/repositories?new=repository`, which opens the wizard once. The query is kept. |

Other collections of other domains do not redirect. For example, an entry of the `cleanup` domain keeps its address.

### Page names under `/settings`

The name `repositories` is a page under `/settings`, not a settings domain. A settings domain whose id is `repositories`, such as a plugin domain, cannot be opened at `/settings/repositories`. The names of the core domains do not collide with it.

### Settings hub

Opened without a domain, `/settings` shows the hub: the settings domains as cards in the groups **Repository** (`javadoc`, `cleanup`, after the card of the Repositories page, which edits the `maven` domain) and **System** (every other domain, including the domains of plugins). A card shows the title of the domain and its saved value, for example "6 repositories" on the Repositories card, or the description of the domain when it has no collections. A card of a domain with unsaved draft changes also shows "Unsaved changes". Opening a card only changes the address and does not write the draft. A group without a card is left out. The domains `login` and `authentication` have no card, because they are edited under Access. The last line of the hub, "Sign-in and security live in Access", links to `/access`. The mapping of domains to groups is in `reposilite-frontend/src/navigation/registry.js`.

### Client-side guards

The router runs two guards, in `reposilite-frontend/src/router/create.js` and `reposilite-frontend/src/router/guard.js`.

| Guard | Applies to | Behavior |
| --- | --- | --- |
| Password change | Every route except `account` | While the server asks the principal for a new password, the router redirects to `account`. |
| Admin route | Routes whose meta marks them as admin pages (`manager: true`): `dashboard`, `analytics`, all `access-*` routes, `tokens`, `token-new`, `token`, `console`, `maintenance`, `audit`, `settings`, `settings-repositories`, `settings-repository`, `settings-entry`, `settings-login`, `settings-authentication` | Waits until the session is known. A user with the node goes in. An anonymous visitor stays on the URL and sees the prompt **Sign in to open** the section, with a **Sign in** button, and the sign-in dialog opens. A signed-in user without the node stays on the URL and sees the page name, the message "You do not have access to this page" and a link to **Repositories**. There is no redirect and no notice. |

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
| `/analytics` | The analytics page. Only this exact path, no sub-paths. |
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
| `/search`, `/account`, `/dashboard`, `/analytics`, `/console`, `/maintenance`, `/audit` | Yes | Yes |
| `/tokens`, `/tokens/{name}` | Yes | Yes (`tokens`, `token-new`, `token`; all redirects) |
| `/settings`, `/settings/<path>` | Yes | Yes (`settings` takes none or one segment below `/settings`, `settings-repositories` and `settings-repository` take the page name `repositories` with an optional id, `settings-entry` takes three, `settings-authentication` redirects `authentication`, `settings-login` redirects `login` to `/access/sso`; without a segment the page shows the settings hub) |
| `/access`, `/access/<path>` | Yes | Yes (`access` and eight routes below it; `/access` is forwarded to a page or, on a narrow screen, shows the index of the Access pages) |
| `/index.html`, `/assets/<path>` | Served, but not in `APP_ROUTES` | No |
| `/:pathMatch(.*)*` | No | Yes (`not-found`) |

### Repository ids that collide with a route

The first segment of each route is a reserved segment: `browse`, `search`, `dashboard`, `analytics`, `tokens`, `console`, `maintenance`, `audit`, `settings`, `access` and `account`. The server also reserves `api` and `assets`. A repository can still use one of these ids. `DashboardRouteDecision` decides who answers a request on such a path:

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
| Repositories, Search | Public maven and search endpoints | Repository visibility decides what the caller sees. |
| My account | `/api/identity/me/*` | The caller must be authenticated. |
| Dashboard | `/api/status/instance`, `/api/status/snapshots`, `/api/status/failures` | Instance admin (`managerOnly`). |
| Analytics | `/api/statistics/resolved/unique`, `/api/statistics/resolved/all` | Instance admin (`managerOnly`). |
| Access, tab **Users** | `/api/identity/principals` (list, create) and the other `users` endpoints | Node `ingot.admin.users`. |
| Access, tab **Machine accounts** | `/api/identity/principals` (list), and `/api/identity/principals/{name}/tokens` (list, create, delete) | The principal list checks `ingot.admin.users`. The token endpoints check `ingot.admin.tokens`. |
| Access, tab **Teams** | The `/api/identity/teams` endpoints | Node `ingot.admin.teams`. |
| Access, tab **Check** | `/api/identity/check` | Authenticated. Instance admins may check any principal, others only their own. |
| Access, tabs **SSO**, **LDAP** and **Security** | `/api/settings/domain/{name}` (PUT), `/api/auth/providers/oidc/{id}/test`, `/api/auth/providers/ldap/test` | Instance admin (`managerOnly`). |
| Console | `/api/console/execute`, `/api/console/log` | Instance admin. |
| Maintenance | `/api/maven/storage`, `/api/maven/search/index` and its `rebuild`, `verify` and `cancel` endpoints, `/api/cleanup/*` | Instance admin (`managerOnly`). |
| Audit | `/api/audit`, `/api/audit/export` | Instance admin (`managerOnly`). |
| Settings | `/api/settings/domains`, `/api/settings/schema/{name}`, `/api/settings/domain/{name}` | Instance admin (`managerOnly`). |
| Repositories | The same endpoints as Settings. Saving writes the `maven` domain with `PUT /api/settings/domain/maven`. | Instance admin (`managerOnly`). |

"Instance admin" here means the principal is allowed `ingot.admin.*` (`PermissionEvaluator.isInstanceAdmin`), after token narrowing. A principal that holds only a single area node such as `ingot.admin.audit` can open the **Audit log** route in the router, but the audit endpoints still answer 403. Only the `users`, `tokens` and `teams` areas are checked per area on the server (`managerOnly("users")`, `managerOnly("tokens")`, `managerOnly("teams")` in `IdentityApiEndpoints.kt`).

{% hint style="warning" %}
The mapping of each page to the exact endpoints it calls was read from the endpoint code and the API client (`reposilite-frontend/src/store/client.js`), not from a running instance. Check the page behavior on your own instance before you rely on a single-area admin token for the pages **Dashboard**, **Console**, **Maintenance**, **Audit log** and **Settings**.
{% endhint %}

## Old hash links

The router resolves old `#/<path>?tab=<section>` links on the first navigation (`reposilite-frontend/src/router/legacy.js`). The mapping:

| Old link | Opens |
| --- | --- |
| `#/...` with `tab` equal to `dashboard`, `tokens`, `console` or `settings` | The route of that name. Other query keys are kept. |
| `#/<path>` with any other or no `tab` | `browse` with the path, or `overview` if the path is empty. |
| Anything that does not start with `#/` | Not handled. |

## Accessibility of the navigation and the Repositories page

This section covers the Administration tree and the **Repositories** page. It describes what the pages are built to do. It is not a conformance claim.

* **Keyboard.** Every function is meant to work with the keyboard alone, in reading order. Focus is visible. Within a repository row, Tab goes from the name link to that row's **Actions** button. Enter or Space opens its menu, the arrow keys move through the items, and Escape closes the menu. Escape closes a dialog and returns focus to the control that opened it.
* **Screen reader names.** The Administration tree is a navigation landmark named **Administration**, and each group is a heading above its list of links. The repository table has a caption with the number of rows shown and the total, column headers, and the name as the row header. A sorted column reports its direction. The row's **Actions** button names its repository, for example "Actions for releases", and its menu items keep the names "Edit releases", "Copy address of releases" and "Delete releases". Type and state are given in text, not only by colour or an icon.
* **Reflow.** At a width of 320 CSS pixels and 200 percent zoom, the page is meant to need no sideways scroll for the page itself. The table scrolls inside a named, focusable region.
* **Target size.** Interactive controls are meant to be at least 24 by 24 CSS pixels.
* **Not verified yet.** No manual pass with a screen reader has been recorded on this page. The result of that pass is reported with the change that adds the page, not here. Contrast of the badges and the behaviour of each dialog with a screen reader are not checked by this section.

## Related

* [Search filters and API](search-filters-and-api.md)
* [Artifact permission nodes](artifact-permission-nodes.md)
* [Review the audit log](../how-to/review-audit-log.md)
