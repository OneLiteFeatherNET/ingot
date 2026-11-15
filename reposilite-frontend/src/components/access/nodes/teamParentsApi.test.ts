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

import { describe, it, expect, vi } from 'vitest'
import { teamParentsApi } from './teamParentsApi'

/*
 * The api turns the answers of the teams and nodes clients into plain values. The client is a fake
 * with the shape of client.identity.
 */

const answer = (data, etag = '"1"') => ({ data, headers: etag ? { etag } : {} })

const makeClient = () => ({
  identity: {
    teams: {
      parents: vi.fn(async () => answer({ parents: ['developers'] }, '"p4"')),
      setParents: vi.fn(async (name, parents) => answer({ parents }, '"p5"')),
      list: vi.fn(async () => answer([{ name: 'ops' }, { name: 'developers' }]))
    },
    nodes: {
      get: vi.fn(async () => answer({ nodes: [{ node: 'ingot.admin.users', allowed: true }] }, '"n1"'))
    }
  }
})

describe('teamParentsApi', () => {
  it('loads the parents of a team with the entity tag of the answer', async () => {
    const client = makeClient()

    const result = await teamParentsApi(client).parents('ops')

    expect(client.identity.teams.parents).toHaveBeenCalledWith('ops')
    expect(result).toEqual({ parents: ['developers'], etag: '"p4"' })
  })

  it('sends the list and the entity tag when it saves the parents', async () => {
    const client = makeClient()

    const result = await teamParentsApi(client).setParents('ops', ['developers', 'staff'], '"p4"')

    expect(client.identity.teams.setParents).toHaveBeenCalledWith('ops', ['developers', 'staff'], '"p4"')
    expect(result).toEqual({ parents: ['developers', 'staff'], etag: '"p5"' })
  })

  it('lists the names of all teams', async () => {
    expect(await teamParentsApi(makeClient()).teamNames()).toEqual(['ops', 'developers'])
  })

  it('reads the nodes and the parents of a team for the walk', async () => {
    const client = makeClient()

    const team = await teamParentsApi(client).team('ops')

    expect(client.identity.nodes.get).toHaveBeenCalledWith('teams', 'ops')
    expect(team).toEqual({ nodes: [{ node: 'ingot.admin.users', allowed: true }], parents: ['developers'] })
  })
})
