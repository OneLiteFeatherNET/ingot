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
 * The section segments (account, dashboard, access, tokens, console, maintenance, audit, settings) are top level names that a
 * repository could also use. The server reserves them (DashboardPaths in the backend), so a full
 * page load of /<name> returns the dashboard document, and a repository with such a name is
 * reached under /browse/<name> from inside the dashboard.
 */

const ACCESS = { section: 'access', manager: true }

const ROUTES = [
  { name: 'overview', path: '/', meta: { section: 'overview' } },
  { name: 'browse', path: '/browse/:qualifier(.*)*', meta: { section: 'overview' } },
  // Open to every signed in principal. `signedIn` shows the sign in prompt to a visitor.
  { name: 'account', path: '/account', meta: { section: 'account', signedIn: true } },
  { name: 'dashboard', path: '/dashboard', meta: { section: 'dashboard', manager: true } },
  // The access section: users, machine accounts and teams. The detail routes name the principal or
  // the team in `:name`. `/access` has no page of its own and opens the user list.
  { name: 'access', path: '/access', redirect: { name: 'access-users' }, meta: ACCESS },
  { name: 'access-users', path: '/access/users', meta: ACCESS },
  { name: 'access-user', path: '/access/users/:name', meta: ACCESS },
  { name: 'access-machines', path: '/access/machines', meta: ACCESS },
  { name: 'access-machine', path: '/access/machines/:name', meta: ACCESS },
  { name: 'access-teams', path: '/access/teams', meta: ACCESS },
  { name: 'access-team', path: '/access/teams/:name', meta: ACCESS },
  // The sign in providers of the instance. Each is a page of its own, so a deep link opens its tab.
  { name: 'access-sso', path: '/access/sso', meta: ACCESS },
  { name: 'access-ldap', path: '/access/ldap', meta: ACCESS },
  // The token pages of the old dashboard. A token is a machine account now, so these links keep
  // working and end at the machine accounts. `new` was the create form and has no machine of its own.
  { name: 'tokens', path: '/tokens', redirect: { name: 'access-machines' }, meta: ACCESS },
  { name: 'token-new', path: '/tokens/new', redirect: { name: 'access-machines' }, meta: ACCESS },
  {
    name: 'token',
    path: '/tokens/:name',
    redirect: (to) => ({ name: 'access-machine', params: { name: to.params.name } }),
    meta: ACCESS
  },
  { name: 'console', path: '/console', meta: { section: 'console', manager: true } },
  { name: 'maintenance', path: '/maintenance', meta: { section: 'maintenance', manager: true } },
  { name: 'audit', path: '/audit', meta: { section: 'audit', manager: true } },
  { name: 'settings', path: '/settings/:domain?', meta: { section: 'settings', manager: true } },
  {
    name: 'settings-entry',
    path: '/settings/:domain/:collection/:entry',
    meta: { section: 'settings', manager: true }
  },
  { name: 'not-found', path: '/:pathMatch(.*)*', meta: { section: null } },
]

export { ROUTES }
