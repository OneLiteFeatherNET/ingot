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

import { describe, it, expect } from 'vitest'
import {
  ROLES,
  buildTeamCreateBody,
  candidatePrincipals,
  filterTeams,
  grantPayload,
  grantScopeLabel,
  manualMembers,
  normalizePathPrefix,
  pathPrefixLabel,
  teamDetailOf,
  validateTeamName
} from './accessTeams'

const TEAMS = [
  { name: 'developers', description: 'Everyone who writes code', memberCount: 4, nodeCount: 2 },
  { name: 'release', description: 'Ships the artifacts', memberCount: 1, nodeCount: 1 },
  { name: 'ops', description: null, memberCount: 0, nodeCount: 0 }
]

describe('filterTeams', () => {
  it('keeps every team for an empty query', () => {
    expect(filterTeams(TEAMS, '  ')).toHaveLength(3)
  })

  it('matches the name, ignoring case', () => {
    expect(filterTeams(TEAMS, 'DEVEL').map(team => team.name)).toEqual(['developers'])
  })

  it('matches the description', () => {
    expect(filterTeams(TEAMS, 'ships').map(team => team.name)).toEqual(['release'])
  })

  it('copes with a team without a description', () => {
    expect(filterTeams(TEAMS, 'null')).toEqual([])
  })
})

describe('normalizePathPrefix', () => {
  it('removes leading and trailing slashes', () => {
    expect(normalizePathPrefix('/releases/com/example/')).toBe('releases/com/example')
  })

  it('keeps inner slashes', () => {
    expect(normalizePathPrefix('a/b')).toBe('a/b')
  })

  it.each([null, undefined, '', '   ', '/', '///'])('turns %j into null, the whole repository', (value) => {
    expect(normalizePathPrefix(value)).toBeNull()
  })
})

describe('labels of a grant', () => {
  it('names a null repository "Every repository"', () => {
    expect(grantScopeLabel(null)).toBe('Every repository')
  })

  it('names a repository by its id', () => {
    expect(grantScopeLabel('releases')).toBe('releases')
  })

  it('names a null path prefix "Whole repository"', () => {
    expect(pathPrefixLabel(null)).toBe('Whole repository')
  })

  it('shows a path prefix without outer slashes', () => {
    expect(pathPrefixLabel('/releases/')).toBe('releases')
  })
})

describe('ROLES', () => {
  it('lists the four roles of the new model with an explanation each', () => {
    expect(ROLES.map(role => role.value)).toEqual(['READ', 'DEPLOY', 'MAINTAIN', 'ADMIN'])
    expect(ROLES.every(role => role.explanation.length > 0)).toBe(true)
  })
})

describe('grantPayload', () => {
  it('sends null for an unset repository and an unset path prefix', () => {
    expect(grantPayload({ repository: '', pathPrefix: '', role: 'READ' }))
      .toEqual({ repository: null, pathPrefix: null, role: 'READ' })
  })

  it('normalizes the path prefix', () => {
    expect(grantPayload({ repository: 'releases', pathPrefix: '/com/example/', role: 'DEPLOY' }))
      .toEqual({ repository: 'releases', pathPrefix: 'com/example', role: 'DEPLOY' })
  })
})

describe('manualMembers', () => {
  it('lists the MANUAL members and leaves the SYNC ones out', () => {
    const detail = {
      members: [
        { principal: 'alice', kind: 'USER', source: 'MANUAL' },
        { principal: 'bob', kind: 'USER', source: 'SYNC' },
        { principal: 'ci-bot', kind: 'MACHINE', source: 'MANUAL' }
      ]
    }

    expect(manualMembers(detail)).toEqual(['alice', 'ci-bot'])
  })
})

describe('candidatePrincipals', () => {
  const principals = [
    { name: 'alice', displayName: 'Alice Example', kind: 'USER', disabled: false },
    { name: 'bob', displayName: 'Bob', kind: 'USER', disabled: true },
    { name: 'ci-bot', displayName: 'CI bot', kind: 'MACHINE', disabled: false }
  ]

  it('excludes the principals that are members already, whatever their source', () => {
    const members = [{ principal: 'alice', source: 'SYNC' }]

    expect(candidatePrincipals(principals, members, '').map(principal => principal.name)).toEqual(['bob', 'ci-bot'])
  })

  it('keeps disabled principals', () => {
    expect(candidatePrincipals(principals, [], '').some(principal => principal.disabled)).toBe(true)
  })

  it('filters by name and display name', () => {
    expect(candidatePrincipals(principals, [], 'example').map(principal => principal.name)).toEqual(['alice'])
  })
})

describe('buildTeamCreateBody', () => {
  it('trims and leaves blank optional fields out', () => {
    expect(buildTeamCreateBody({ name: ' qa ', description: '  ', externalRef: '' })).toEqual({ name: 'qa' })
  })

  it('keeps a description and an external reference', () => {
    expect(buildTeamCreateBody({ name: 'qa', description: 'Testers', externalRef: 'idp:qa' }))
      .toEqual({ name: 'qa', description: 'Testers', externalRef: 'idp:qa' })
  })
})

describe('validateTeamName', () => {
  it('asks for a name', () => {
    expect(validateTeamName('  ')).toBe('A name is required')
  })

  it("rejects a '/'", () => {
    expect(validateTeamName('a/b')).not.toBe('')
  })

  it('rejects the reserved machine: prefix', () => {
    expect(validateTeamName('machine:ci')).toContain('reserved')
  })

  it('accepts a plain name', () => {
    expect(validateTeamName('release-managers')).toBe('')
  })
})

describe('teamDetailOf', () => {
  it('fills the lists a sparse answer leaves out', () => {
    expect(teamDetailOf({ name: 'qa' })).toMatchObject({ members: [], grants: [], instanceRole: false })
  })


  it('fills the parents a sparse answer leaves out', () => {
    expect(teamDetailOf({ name: 'qa' })).toMatchObject({ parents: [] })
  })

  it('keeps the parents the server sent', () => {
    expect(teamDetailOf({ name: 'ops', parents: ['developers'] })).toMatchObject({ parents: ['developers'] })
  })
})
