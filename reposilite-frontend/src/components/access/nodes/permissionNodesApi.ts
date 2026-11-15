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

import type { AxiosResponse } from 'axios'
import { etagOf, type createClient } from '../../../store/client'
import type { components } from '../../../types/api'

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

/** One listed node: the schema of the identity api. */
export type NodeEntry = components['schemas']['NodeDto']

/** One suggestion of the node catalog. */
export interface CatalogItem {
  node: string
  description?: string
  kind: string
}

/** One template: lists of nodes with placeholders. */
export interface NodeTemplate {
  id: string
  label: string
  nodes: string[]
}

type Client = ReturnType<typeof createClient>

// The bodies of the identity answers this module reads.
interface NodesBody {
  nodes?: NodeEntry[] | null
}

interface CatalogBody {
  nodes?: CatalogItem[] | null
}

interface TemplatesBody {
  templates?: NodeTemplate[] | null
}

// Types the answer of a request. The client hands back axios responses with untyped data.
export const bodyAs = <T>(request: Promise<AxiosResponse>): Promise<AxiosResponse<T>> => request as Promise<AxiosResponse<T>>

// The identity endpoints this module calls. A test hands in a fake with the same shape.
export interface NodesClient {
  identity: Pick<Client['identity'], 'nodes'>
}

const COLLECTION: Record<string, string> = { principal: 'principals', team: 'teams' }

const collectionOf = (kind: string): string => {
  const collection = COLLECTION[kind]
  if (!collection) throw new Error(`Unknown subject kind: ${kind}`)
  return collection
}

const entriesOf = (data: NodesBody | null | undefined): NodeEntry[] =>
  (data?.nodes ?? []).map(entry => ({ node: entry.node, allowed: entry.allowed === true }))

const permissionNodesApi = (client: NodesClient) => {
  const nodes = () => client.identity.nodes

  return {
    async load(kind: string, name: string): Promise<{ nodes: NodeEntry[]; etag: string | null }> {
      const response = await bodyAs<NodesBody>(nodes().get(collectionOf(kind), name))
      return { nodes: entriesOf(response.data), etag: etagOf(response) }
    },
    async save(kind: string, name: string, entries: NodeEntry[], etag: string | null): Promise<{ nodes: NodeEntry[]; etag: string | null }> {
      const body = entries.map(entry => ({ node: entry.node, allowed: entry.allowed }))
      const response = await bodyAs<NodesBody>(nodes().set(collectionOf(kind), name, body, etag))
      return { nodes: response?.data?.nodes ? entriesOf(response.data) : body, etag: etagOf(response) }
    },
    async catalog(prefix = ''): Promise<CatalogItem[]> {
      const response = await bodyAs<CatalogBody>(nodes().catalog(prefix))
      return response.data?.nodes ?? []
    },
    async templates(): Promise<NodeTemplate[]> {
      const response = await bodyAs<TemplatesBody>(nodes().templates())
      return response.data?.templates ?? []
    }
  }
}

export { permissionNodesApi }
