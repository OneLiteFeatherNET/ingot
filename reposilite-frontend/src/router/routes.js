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
 * The section segments (dashboard, tokens, console, settings) are top level names that a
 * repository could also use. Inside the dashboard the browser still reaches such a repository
 * under /browse/<name>; a full page load of /<name> is answered by the server, not the dashboard.
 */

const ROUTES = [
  { name: 'overview', path: '/', meta: { section: 'overview' } },
  { name: 'browse', path: '/browse/:qualifier(.*)*', meta: { section: 'overview' } },
  { name: 'dashboard', path: '/dashboard', meta: { section: 'dashboard', manager: true } },
  { name: 'tokens', path: '/tokens', meta: { section: 'tokens', manager: true } },
  // `new` is reserved: it opens the create form rather than a token of that name.
  { name: 'token', path: '/tokens/:name', meta: { section: 'tokens', manager: true } },
  { name: 'console', path: '/console', meta: { section: 'console', manager: true } },
  { name: 'settings', path: '/settings/:domain?', meta: { section: 'settings', manager: true } },
  {
    name: 'settings-entry',
    path: '/settings/:domain/:collection/:entry',
    meta: { section: 'settings', manager: true }
  },
  { name: 'not-found', path: '/:pathMatch(.*)*', meta: { section: null } },
]

export { ROUTES }
