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

import { etagOf } from '../../../store/client'

/*
 * Thin functions over the identity client for permission nodes. `client` is what createClient
 * returns (the session holds it), so a test hands in a fake with `identity.nodes`.
 *
 *   load(kind, name)               -> { nodes, etag }      kind is 'principal' or 'team'
 *   save(kind, name, nodes, etag)  -> { nodes, etag }      sends { nodes } with If-Match
 *   catalog(prefix)                -> [{ node, description, kind }]
 *   templates()                    -> [{ id, label, nodes }]
 *
 * An answer without an entity tag gives etag null, and the next write is then unconditional.
 */

const COLLECTION = { principal: 'principals', team: 'teams' }

const collectionOf = (kind) => {
  const collection = COLLECTION[kind]
  if (!collection) throw new Error(`Unknown subject kind: ${kind}`)
  return collection
}

const entriesOf = (data) =>
  (data?.nodes ?? []).map(entry => ({ node: entry.node, allowed: entry.allowed === true }))

const permissionNodesApi = (client) => {
  const nodes = () => client.identity.nodes

  return {
    async load(kind, name) {
      const response = await nodes().get(collectionOf(kind), name)
      return { nodes: entriesOf(response.data), etag: etagOf(response) }
    },
    async save(kind, name, entries, etag) {
      const body = entries.map(entry => ({ node: entry.node, allowed: entry.allowed }))
      const response = await nodes().set(collectionOf(kind), name, body, etag)
      return { nodes: response?.data?.nodes ? entriesOf(response.data) : body, etag: etagOf(response) }
    },
    async catalog(prefix = '') {
      const response = await nodes().catalog(prefix)
      return response.data?.nodes ?? []
    },
    async templates() {
      const response = await nodes().templates()
      return response.data?.templates ?? []
    }
  }
}

export { permissionNodesApi }
