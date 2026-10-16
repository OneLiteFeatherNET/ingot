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
 * The one matcher behind every gate in the dashboard. `nodes` is the list /api/auth/me sends:
 * the allowed nodes of the session principal, literal and wildcard entries mixed. A node is
 * allowed when some entry equals it or matches it: a lone `*` matches everything, otherwise an
 * entry matches when it has the same number of segments and every segment is equal or `*`.
 * This is the rule of the server, so the client never allows more than the server would.
 */

const ADMIN_NODES = {
  dashboard: 'ingot.admin.dashboard',
  console: 'ingot.admin.console',
  maintenance: 'ingot.admin.maintenance',
  audit: 'ingot.admin.audit',
  settings: 'ingot.admin.settings',
  users: 'ingot.admin.users',
  tokens: 'ingot.admin.tokens',
  teams: 'ingot.admin.teams'
}

// The full instance admin: `ingot.admin.*`. Only a node list that allows every area holds it.
const INSTANCE_ADMIN_NODE = 'ingot.admin.*'

// The Access section is open to whoever may use at least one of its three lists.
const ACCESS_NODES = [ADMIN_NODES.users, ADMIN_NODES.tokens, ADMIN_NODES.teams]

const entryMatches = (entry, node) => {
  if (entry === '*') return true
  const entrySegments = entry.split('.')
  const nodeSegments = node.split('.')
  return entrySegments.length === nodeSegments.length
    && entrySegments.every((segment, index) => segment === '*' || segment === nodeSegments[index])
}

const canUse = (nodes, node) =>
  Array.isArray(nodes)
  && typeof node === 'string'
  && nodes.some(entry => typeof entry === 'string' && entryMatches(entry, node))

// The nodes a route asks for: `node` for one, `anyNode` for a list of which one is enough.
const requiredNodes = (meta) => [meta?.node, ...(meta?.anyNode ?? [])].filter(Boolean)

/*
 * Whether a route may be used. `can` answers for a node and `isManager` is the legacy answer.
 * A route that names no node falls back to the manager flag it carries.
 */
const routeAllowed = (meta, can, isManager) => {
  const required = requiredNodes(meta)
  if (required.length > 0) {
    return required.some(node => can(node))
  }
  return !meta?.manager || isManager
}

export { ADMIN_NODES, ACCESS_NODES, INSTANCE_ADMIN_NODE, canUse, requiredNodes, routeAllowed }
