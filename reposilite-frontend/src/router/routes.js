/*
 * Copyright (c) 2026 OneLiteFeather
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * The address space of the dashboard. Every route renders the same shell; the section is
 * read from `meta.section`, and `meta.manager` marks the routes that need the manager
 * permission. Route names are the contract the links and the other views build on.
 *
 * The section segments (search, account, dashboard, access, tokens, console, maintenance, audit, settings) are top level names that a
 * repository could also use. The server reserves them (DashboardPaths in the backend), so a full
 * page load of /<name> returns the dashboard document, and a repository with such a name is
 * reached under /browse/<name> from inside the dashboard.
 */

import { ACCESS_NODES, ADMIN_NODES, INSTANCE_ADMIN_NODE } from '../store/nodeAccess'

// `manager` stays on every admin route: it is what a session without a node list is judged by, and
// what tells a visitor who is not signed in to sign in. `node` is the permission node the route needs,
// and `anyNode` lists alternatives of which one is enough.
const adminRoute = (section, node) => ({ section, manager: true, node })
const ACCESS_USERS = adminRoute('access', ADMIN_NODES.users)
// Machine accounts are principals, so listing them is a principal endpoint: the users area, as the server decides it.
const ACCESS_MACHINES = adminRoute('access', ADMIN_NODES.users)
const ACCESS_TEAMS = adminRoute('access', ADMIN_NODES.teams)
const ACCESS_ANY = { section: 'access', manager: true, anyNode: ACCESS_NODES }
const ACCESS_PROVIDERS = adminRoute('access', INSTANCE_ADMIN_NODE)

// The tabs of the access section in the order they are offered, with the node each one needs.
const ACCESS_TABS = [
  { key: 'users', route: 'access-users', node: ADMIN_NODES.users },
  { key: 'machines', route: 'access-machines', node: ADMIN_NODES.users },
  { key: 'teams', route: 'access-teams', node: ADMIN_NODES.teams },
]

const ROUTES = [
  { name: 'overview', path: '/', meta: { section: 'overview' } },
  { name: 'browse', path: '/browse/:qualifier(.*)*', meta: { section: 'overview' } },
  // The artifact search, open to everyone. The query is in the address; see components/search/searchQuery.js.
  { name: 'search', path: '/search', meta: { section: 'search' } },
  // Open to every signed in principal. `signedIn` shows the sign in prompt to a visitor.
  { name: 'account', path: '/account', meta: { section: 'account', signedIn: true } },
  { name: 'dashboard', path: '/dashboard', meta: adminRoute('dashboard', ADMIN_NODES.dashboard) },
  // The access section: users, machine accounts and teams. The detail routes name the principal or
  // the team in `:name`. `/access` has no page of its own and opens the user list.
  { name: 'access', path: '/access', redirect: { name: 'access-users' }, meta: ACCESS_ANY },
  { name: 'access-users', path: '/access/users', meta: ACCESS_USERS },
  { name: 'access-user', path: '/access/users/:name', meta: ACCESS_USERS },
  { name: 'access-machines', path: '/access/machines', meta: ACCESS_MACHINES },
  { name: 'access-machine', path: '/access/machines/:name', meta: ACCESS_MACHINES },
  // The permission check: `principal`, `node` and `token` come from the query, so a check can be linked.
  { name: 'access-check', path: '/access/check', meta: ACCESS_ANY },
  { name: 'access-teams', path: '/access/teams', meta: ACCESS_TEAMS },
  { name: 'access-team', path: '/access/teams/:name', meta: ACCESS_TEAMS },
  // The sign in providers of the instance. Each is a page of its own, so a deep link opens its tab.
  { name: 'access-sso', path: '/access/sso', meta: ACCESS_PROVIDERS },
  { name: 'access-ldap', path: '/access/ldap', meta: ACCESS_PROVIDERS },
  // The token pages of the old dashboard. A token is a machine account now, so these links keep
  // working and end at the machine accounts. `new` was the create form and has no machine of its own.
  { name: 'tokens', path: '/tokens', redirect: { name: 'access-machines' }, meta: ACCESS_MACHINES },
  { name: 'token-new', path: '/tokens/new', redirect: { name: 'access-machines' }, meta: ACCESS_MACHINES },
  {
    name: 'token',
    path: '/tokens/:name',
    redirect: (to) => ({ name: 'access-machine', params: { name: to.params.name } }),
    meta: ACCESS_MACHINES
  },
  { name: 'console', path: '/console', meta: adminRoute('console', ADMIN_NODES.console) },
  { name: 'maintenance', path: '/maintenance', meta: adminRoute('maintenance', ADMIN_NODES.maintenance) },
  { name: 'audit', path: '/audit', meta: adminRoute('audit', ADMIN_NODES.audit) },
  { name: 'settings', path: '/settings/:domain?', meta: adminRoute('settings', ADMIN_NODES.settings) },
  {
    name: 'settings-entry',
    path: '/settings/:domain/:collection/:entry',
    meta: adminRoute('settings', ADMIN_NODES.settings)
  },
  { name: 'not-found', path: '/:pathMatch(.*)*', meta: { section: null } },
]

export { ROUTES, ACCESS_TABS }
