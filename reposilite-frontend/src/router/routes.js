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
 * The section segments (search, account, dashboard, analytics, access, tokens, console, maintenance, audit, settings, browse, api, assets)
 * are top level names that a repository could also use. The server reserves them (DashboardPaths in the backend), so a full
 * page load of /<name> returns the dashboard document, and a repository with such a name is
 * reached under /browse/<name> from inside the dashboard.
 */

import { routeMeta } from '../navigation/registry'

const ROUTES = [
  { name: 'overview', path: '/', meta: routeMeta('overview') },
  { name: 'browse', path: '/browse/:qualifier(.*)*', meta: routeMeta('overview') },
  // The artifact search, open to everyone. The query is in the address; see components/search/searchQuery.js.
  { name: 'search', path: '/search', meta: routeMeta('search') },
  // Open to every signed in principal. `signedIn` shows the sign in prompt to a visitor.
  { name: 'account', path: '/account', meta: routeMeta('account') },
  { name: 'dashboard', path: '/dashboard', meta: routeMeta('dashboard') },
  // Usage over time. The same permission as the dashboard, so both tabs are offered to the same people.
  { name: 'analytics', path: '/analytics', meta: routeMeta('analytics') },
  // The access section: users, machine accounts and teams. The detail routes name the principal or
  // the team in `:name`. `/access` has no page of its own and opens the user list.
  { name: 'access', path: '/access', redirect: { name: 'access-users' }, meta: routeMeta('access') },
  { name: 'access-users', path: '/access/users', meta: routeMeta('access', 'users') },
  { name: 'access-user', path: '/access/users/:name', meta: routeMeta('access', 'users') },
  { name: 'access-machines', path: '/access/machines', meta: routeMeta('access', 'machines') },
  { name: 'access-machine', path: '/access/machines/:name', meta: routeMeta('access', 'machines') },
  // The permission check: `principal`, `node` and `token` come from the query, so a check can be linked.
  { name: 'access-check', path: '/access/check', meta: routeMeta('access', 'check') },
  { name: 'access-teams', path: '/access/teams', meta: routeMeta('access', 'teams') },
  { name: 'access-team', path: '/access/teams/:name', meta: routeMeta('access', 'teams') },
  // The sign in providers of the instance. Each is a page of its own, so a deep link opens its tab.
  { name: 'access-sso', path: '/access/sso', meta: routeMeta('access', 'sso') },
  { name: 'access-ldap', path: '/access/ldap', meta: routeMeta('access', 'ldap') },
  { name: 'access-security', path: '/access/security', meta: routeMeta('access', 'security') },
  // The token pages of the old dashboard. A token is a machine account now, so these links keep
  // working and end at the machine accounts. `new` was the create form and has no machine of its own.
  { name: 'tokens', path: '/tokens', redirect: { name: 'access-machines' }, meta: routeMeta('access', 'machines') },
  { name: 'token-new', path: '/tokens/new', redirect: { name: 'access-machines' }, meta: routeMeta('access', 'machines') },
  {
    name: 'token',
    path: '/tokens/:name',
    redirect: (to) => ({ name: 'access-machine', params: { name: to.params.name } }),
    meta: routeMeta('access', 'machines')
  },
  { name: 'console', path: '/console', meta: routeMeta('console') },
  { name: 'maintenance', path: '/maintenance', meta: routeMeta('maintenance') },
  { name: 'audit', path: '/audit', meta: routeMeta('audit') },
  // The authentication settings are edited under Access, so a link to their old settings tab ends there.
  { name: 'settings-authentication', path: '/settings/authentication', redirect: { name: 'access-ldap' }, meta: routeMeta('access', 'ldap') },
  { name: 'settings', path: '/settings/:domain?', meta: routeMeta('settings') },
  {
    name: 'settings-entry',
    path: '/settings/:domain/:collection/:entry',
    meta: routeMeta('settings')
  },
  { name: 'not-found', path: '/:pathMatch(.*)*', meta: { section: null } },
]

export { ROUTES }
