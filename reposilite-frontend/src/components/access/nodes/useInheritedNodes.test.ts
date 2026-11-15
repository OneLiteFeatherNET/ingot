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
import { useInheritedNodes } from './useInheritedNodes'

vi.mock('../../../helpers/toast', () => ({
  errorMessage: (error) => error?.response?.data?.message ?? error?.message ?? `${error}`
}))

/*
 * The composable is driven through a fake api (the shape teamParentsApi returns), so every call
 * resolves as a promise and nothing waits on a timer.
 */

const failure = (status, message) =>
  Object.assign(new Error(message), { response: { status, data: { message } } })

const TEAMS = () => ({
  ops: { nodes: [{ node: 'ingot.admin.maintenance', allowed: true }], parents: ['developers'] },
  developers: { nodes: [{ node: 'ingot.repository.releases.read', allowed: true }], parents: [] }
})

const makeApi = (teams = TEAMS()) => ({
  team: vi.fn(async (name) => {
    if (!(name in teams)) throw failure(404, `Team '${name}' not found`)
    return teams[name]
  })
})

const open = async (source, api = makeApi()) => {
  const inherited = useInheritedNodes(() => source, () => api)
  await inherited.load()
  return { inherited, api }
}

describe('useInheritedNodes', () => {
  it('reads the nodes of the start teams and of their ancestors', async () => {
    const { inherited } = await open({ teams: ['ops'], depth: 0 })

    expect(inherited.entries.value.map(entry => [entry.subject, entry.depth, entry.source]))
      .toEqual([['ops', 0, 'TEAM'], ['developers', 1, 'INHERITED_TEAM']])
    expect(inherited.status.value).toBe('ready')
  })

  it('reads the nodes and the parents of each team through the api', async () => {
    const { api } = await open({ teams: ['ops'], depth: 1 })

    expect(api.team).toHaveBeenCalledWith('ops')
    expect(api.team).toHaveBeenCalledWith('developers')
  })

  it('stays idle and asks for no api when there is no team to read', async () => {
    const getApi = vi.fn(() => makeApi())
    const inherited = useInheritedNodes(() => ({ teams: [], depth: 1 }), getApi)

    await inherited.load()

    expect(inherited.status.value).toBe('idle')
    expect(inherited.entries.value).toEqual([])
    expect(getApi).not.toHaveBeenCalled()
  })

  it('warns about a team that cannot be read and keeps the rest', async () => {
    const { inherited } = await open({ teams: ['ops', 'ghosts'], depth: 1 })

    expect(inherited.warnings.value).toEqual(["Could not read the nodes of team ghosts: Team 'ghosts' not found"])
    expect(inherited.entries.value.map(entry => entry.subject)).toEqual(['ops', 'developers'])
  })

  it('warns once when the api itself is unavailable', async () => {
    const inherited = useInheritedNodes(() => ({ teams: ['ops'], depth: 1 }), () => { throw new Error('No client') })

    await inherited.load()

    expect(inherited.entries.value).toEqual([])
    expect(inherited.warnings.value).toEqual(['Could not read the inherited nodes: No client'])
  })

  it('clears the earlier result when the source becomes empty', async () => {
    const source = { teams: ['ops'], depth: 1 }
    const inherited = useInheritedNodes(() => source, () => makeApi())
    await inherited.load()

    source.teams = []
    await inherited.load()

    expect(inherited.entries.value).toEqual([])
    expect(inherited.status.value).toBe('idle')
  })

  it('drops a load that a newer load overtook', async () => {
    let release
    const slow = new Promise(resolve => { release = resolve })
    const teams = {
      slow: { nodes: [{ node: 'ingot.admin.users', allowed: true }], parents: [] },
      fast: { nodes: [], parents: [] }
    }
    const api = {
      team: vi.fn(async (name) => {
        if (name === 'slow') await slow
        return teams[name]
      })
    }
    const source = { teams: ['slow'], depth: 1 }
    const inherited = useInheritedNodes(() => source, () => api)

    const first = inherited.load()
    source.teams = ['fast']
    await inherited.load()
    release()
    await first

    expect(inherited.entries.value, 'the newer, empty answer stays').toEqual([])
  })

  it('reset clears the entries and the warnings', async () => {
    const { inherited } = await open({ teams: ['ops', 'ghosts'], depth: 1 })

    inherited.reset()

    expect(inherited.entries.value).toEqual([])
    expect(inherited.warnings.value).toEqual([])
    expect(inherited.status.value).toBe('idle')
  })
})
