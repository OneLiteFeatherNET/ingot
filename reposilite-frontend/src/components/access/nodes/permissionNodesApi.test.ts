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
import { permissionNodesApi } from './permissionNodesApi'

/*
 * The api hands the client the collection name, the body and the entity tag, and turns the
 * answers into { nodes, etag }. The client is a fake with the shape of client.identity.nodes.
 */

const answer = (data, etag = '"1"') => ({ data, headers: etag ? { etag } : {} })

const makeClient = (overrides = {}) => ({
  identity: {
    nodes: {
      get: vi.fn(async () => answer({ nodes: [{ node: 'ingot.admin.users', allowed: true }] }, '"4"')),
      set: vi.fn(async (kind, name, nodes) => answer({ nodes }, '"5"')),
      catalog: vi.fn(async () => answer({ nodes: [{ node: '*', description: 'Everything', kind: 'WILDCARD' }] })),
      templates: vi.fn(async () => answer({ templates: [{ id: 'read', label: 'Read', nodes: ['x'] }] })),
      ...overrides
    }
  }
})

describe('permissionNodesApi', () => {
  it('loads the nodes of a principal with the entity tag of the answer', async () => {
    const client = makeClient()

    const result = await permissionNodesApi(client).load('principal', 'alice')

    expect(client.identity.nodes.get).toHaveBeenCalledWith('principals', 'alice')
    expect(result).toEqual({ nodes: [{ node: 'ingot.admin.users', allowed: true }], etag: '"4"' })
  })

  it('loads the nodes of a team from the teams collection', async () => {
    const client = makeClient()

    await permissionNodesApi(client).load('team', 'developers')

    expect(client.identity.nodes.get).toHaveBeenCalledWith('teams', 'developers')
  })

  it('gives a null entity tag when the answer carries none', async () => {
    const client = makeClient({ get: vi.fn(async () => answer({ nodes: [] }, null)) })

    const result = await permissionNodesApi(client).load('team', 'developers')

    expect(result.etag).toBeNull()
  })

  it('saves exactly the node and allowed fields with the entity tag it is given', async () => {
    const client = makeClient()
    const rows = [{ node: 'ingot.admin.users', allowed: false, extra: 'dropped' }]

    await permissionNodesApi(client).save('principal', 'alice', rows, '"4"')

    expect(client.identity.nodes.set).toHaveBeenCalledWith(
      'principals',
      'alice',
      [{ node: 'ingot.admin.users', allowed: false }],
      '"4"'
    )
  })

  it('returns the saved nodes and the new entity tag', async () => {
    const client = makeClient()

    const result = await permissionNodesApi(client).save('team', 'ops', [{ node: '*', allowed: true }], '"4"')

    expect(result).toEqual({ nodes: [{ node: '*', allowed: true }], etag: '"5"' })
  })

  it('rejects a subject kind it does not know', async () => {
    await expect(permissionNodesApi(makeClient()).load('robot', 'x')).rejects.toThrow('Unknown subject kind: robot')
  })

  it('reads the catalog with the prefix', async () => {
    const client = makeClient()

    const result = await permissionNodesApi(client).catalog('ingot.')

    expect(client.identity.nodes.catalog).toHaveBeenCalledWith('ingot.')
    expect(result).toEqual([{ node: '*', description: 'Everything', kind: 'WILDCARD' }])
  })

  it('reads the templates', async () => {
    const result = await permissionNodesApi(makeClient()).templates()

    expect(result).toEqual([{ id: 'read', label: 'Read', nodes: ['x'] }])
  })
})
