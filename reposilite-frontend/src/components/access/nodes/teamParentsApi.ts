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

import { etagOf, type createClient } from '../../../store/client'
import { bodyAs, permissionNodesApi, type NodeEntry } from './permissionNodesApi'

/*
 * Thin functions over the identity client for the parent teams of a team. `client` is what
 * createClient returns (the session holds it), so a test hands in a fake with `identity.teams`
 * and `identity.nodes`.
 *
 *   parents(name)                    -> { parents, etag }
 *   setParents(name, parents, etag)  -> { parents, etag }   sends { parents } with If-Match
 *   teamNames()                      -> [teamName]          every team, for the suggestions
 *   team(name)                       -> { nodes, parents }  what the walk up the tree needs
 *
 * An answer without an entity tag gives etag null, and the next write is then unconditional.
 */

// The identity endpoints this module calls. A test hands in a fake with the same shape.
export interface TeamsClient {
  identity: Pick<ReturnType<typeof createClient>['identity'], 'teams' | 'nodes'>
}

// The bodies of the identity answers this module reads.
interface ParentsBody {
  parents?: unknown[] | null
}

interface TeamListBody {
  name: string
}

const namesOf = (data: ParentsBody | null | undefined): string[] => (data?.parents ?? []).map(String)

const teamParentsApi = (client: TeamsClient) => {
  const teams = () => client.identity.teams

  const parents = async (name: string): Promise<{ parents: string[]; etag: string | null }> => {
    const response = await bodyAs<ParentsBody>(teams().parents(name))
    return { parents: namesOf(response.data), etag: etagOf(response) }
  }

  return {
    parents,
    async setParents(name: string, list: string[], etag: string | null): Promise<{ parents: string[]; etag: string | null }> {
      const response = await bodyAs<ParentsBody>(teams().setParents(name, list, etag))
      return { parents: response?.data?.parents ? namesOf(response.data) : [...list], etag: etagOf(response) }
    },
    async teamNames(): Promise<string[]> {
      const response = await bodyAs<TeamListBody[]>(teams().list())
      return (response.data ?? []).map((team: { name: string }) => team.name)
    },
    async team(name: string): Promise<{ nodes: NodeEntry[]; parents: string[] }> {
      const [nodes, above] = await Promise.all([permissionNodesApi(client).load('team', name), parents(name)])
      return { nodes: nodes.nodes, parents: above.parents }
    }
  }
}

export { teamParentsApi }
