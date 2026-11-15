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
import { MAX_TEAM_DEPTH, collectInheritedNodes } from './inheritedNodes'

/*
 * The walk is driven by a fake fetchTeam over a plain object that stands for the team tree, so
 * nothing here reads a network or waits on a timer.
 */

const allow = (node) => ({ node, allowed: true })

const tree = (teams) => vi.fn(async (name) => {
  if (!(name in teams)) throw new Error(`No team ${name}`)
  return teams[name]
})

const chain = (length) => Object.fromEntries(
  Array.from({ length }, (_, index) => [
    `t${index}`,
    { nodes: [allow(`ingot.admin.t${index}`)], parents: index < length - 1 ? [`t${index + 1}`] : [] }
  ])
)

describe('collectInheritedNodes', () => {
  it('gives a direct parent depth 1 and a grandparent depth 2', async () => {
    const fetchTeam = tree({
      ops: { nodes: [allow('ingot.admin.maintenance')], parents: ['developers'] },
      developers: { nodes: [allow('ingot.repository.releases.read')], parents: ['staff'] },
      staff: { nodes: [allow('ingot.admin.dashboard')], parents: [] }
    })

    const { entries } = await collectInheritedNodes({ start: ['ops'], startDepth: 1, fetchTeam })

    expect(entries.map(entry => [entry.subject, entry.depth])).toEqual([['ops', 1], ['developers', 2], ['staff', 3]])
  })

  it('lists the nearest team first when a team has several parents', async () => {
    const fetchTeam = tree({
      near: { nodes: [allow('ingot.admin.users')], parents: ['far'] },
      other: { nodes: [allow('ingot.admin.teams')], parents: [] },
      far: { nodes: [allow('ingot.admin.audit')], parents: [] }
    })

    const { entries } = await collectInheritedNodes({ start: ['near', 'other'], startDepth: 1, fetchTeam })

    expect(entries.map(entry => entry.subject)).toEqual(['near', 'other', 'far'])
  })

  it('marks the teams of a principal as TEAM at depth 0 and their ancestors as INHERITED_TEAM', async () => {
    const fetchTeam = tree({
      ops: { nodes: [allow('ingot.admin.maintenance')], parents: ['developers'] },
      developers: { nodes: [allow('ingot.repository.releases.read')], parents: [] }
    })

    const { entries } = await collectInheritedNodes({ start: ['ops'], startDepth: 0, fetchTeam })

    expect(entries).toEqual([
      { node: 'ingot.admin.maintenance', allowed: true, source: 'TEAM', subject: 'ops', depth: 0 },
      { node: 'ingot.repository.releases.read', allowed: true, source: 'INHERITED_TEAM', subject: 'developers', depth: 1 }
    ])
  })

  it('keeps the allowed state of a denied node', async () => {
    const fetchTeam = tree({ ops: { nodes: [{ node: 'ingot.admin.users', allowed: false }], parents: [] } })

    const { entries } = await collectInheritedNodes({ start: ['ops'], startDepth: 1, fetchTeam })

    expect(entries[0].allowed).toBe(false)
  })

  it('ends on a cycle and reads each team once', async () => {
    const fetchTeam = tree({
      a: { nodes: [allow('ingot.admin.users')], parents: ['b'] },
      b: { nodes: [allow('ingot.admin.teams')], parents: ['a'] }
    })

    const { entries, errors } = await collectInheritedNodes({ start: ['a'], startDepth: 1, fetchTeam })

    expect(entries.map(entry => entry.subject)).toEqual(['a', 'b'])
    expect(fetchTeam).toHaveBeenCalledTimes(2)
    expect(errors).toEqual([])
  })

  it('reads a team that is reachable twice once, at its nearest depth', async () => {
    const fetchTeam = tree({
      left: { nodes: [], parents: ['top'] },
      right: { nodes: [], parents: ['mid'] },
      mid: { nodes: [], parents: ['top'] },
      top: { nodes: [allow('ingot.admin.users')], parents: [] }
    })

    const { entries } = await collectInheritedNodes({ start: ['left', 'right'], startDepth: 1, fetchTeam })

    expect(entries).toHaveLength(1)
    expect(entries[0]).toMatchObject({ subject: 'top', depth: 2 })
  })

  it('reads the same team name written in another case once', async () => {
    const fetchTeam = tree({
      Ops: { nodes: [allow('ingot.admin.users')], parents: [] }
    })

    await collectInheritedNodes({ start: ['Ops', 'ops'], startDepth: 1, fetchTeam })

    expect(fetchTeam).toHaveBeenCalledTimes(1)
  })

  it('stops after the limit of five levels', async () => {
    const fetchTeam = tree(chain(8))

    const { entries } = await collectInheritedNodes({ start: ['t0'], startDepth: 1, fetchTeam })

    expect(MAX_TEAM_DEPTH).toBe(5)
    expect(entries.map(entry => entry.depth)).toEqual([1, 2, 3, 4, 5])
    expect(entries.map(entry => entry.subject)).toEqual(['t0', 't1', 't2', 't3', 't4'])
    expect(fetchTeam, 'the sixth team is never read').not.toHaveBeenCalledWith('t5')
  })

  it('reads one level more for a principal, because its own teams are depth 0', async () => {
    const fetchTeam = tree(chain(8))

    const { entries } = await collectInheritedNodes({ start: ['t0'], startDepth: 0, fetchTeam })

    expect(entries.map(entry => entry.depth)).toEqual([0, 1, 2, 3, 4, 5])
  })

  it('skips a team that cannot be read and reports it', async () => {
    const fetchTeam = tree({
      ops: { nodes: [allow('ingot.admin.maintenance')], parents: ['ghosts', 'developers'] },
      developers: { nodes: [allow('ingot.repository.releases.read')], parents: [] }
    })

    const { entries, errors } = await collectInheritedNodes({ start: ['ops'], startDepth: 1, fetchTeam })

    expect(errors).toEqual([{ team: 'ghosts', message: 'No team ghosts' }])
    expect(entries.map(entry => entry.subject)).toEqual(['ops', 'developers'])
  })

  it('reports a team that cannot be read and still reads the others of its level', async () => {
    const fetchTeam = tree({ other: { nodes: [allow('ingot.admin.users')], parents: [] } })

    const { entries, errors } = await collectInheritedNodes({ start: ['ghosts', 'other'], startDepth: 1, fetchTeam })

    expect(entries.map(entry => entry.subject)).toEqual(['other'])
    expect(errors).toHaveLength(1)
  })

  it('returns nothing for no start teams', async () => {
    const fetchTeam = tree({})

    const result = await collectInheritedNodes({ start: [], startDepth: 1, fetchTeam })

    expect(result).toEqual({ entries: [], errors: [] })
    expect(fetchTeam).not.toHaveBeenCalled()
  })
})
