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
import { createAccessPorts } from './repositoryAccessPorts'

const TEAM = {
  name: 'developers',
  members: [
    { principal: 'alice', kind: 'USER', source: 'MANUAL' },
    { principal: 'bob', kind: 'USER', source: 'SYNC' },
    { principal: 'ci-bot', kind: 'MACHINE', source: 'MANUAL' }
  ],
  grants: [
    { repository: null, pathPrefix: null, role: 'READ' },
    { repository: 'releases', pathPrefix: 'com/example', role: 'DEPLOY' }
  ]
}

// A fake identity client. Every call is recorded by vi.fn, so a test reads the requests back.
const fakeIdentity = ({ team = TEAM, etag = '"5"' } = {}) => {
  const tokens = { create: vi.fn(async () => ({ data: { token: { name: 'team-deploy' }, secret: 'secret-1' } })) }
  return {
    teams: {
      get: vi.fn(async () => ({ data: team, headers: { etag } })),
      create: vi.fn(async () => ({ data: { name: 'new-team' } })),
      setGrants: vi.fn(async () => ({ data: team })),
      setMembers: vi.fn(async () => ({ data: team }))
    },
    principals: { create: vi.fn(async () => ({ data: { name: 'team-deploy' } })) },
    principalTokens: vi.fn(() => tokens),
    tokens
  }
}

describe('create the team', () => {
  it('sends only the name of the team', async () => {
    const identity = fakeIdentity()

    await createAccessPorts(identity).createTeam('team-team')

    expect(identity.teams.create).toHaveBeenCalledWith({ name: 'team-team' })
  })
})

describe('grant the team', () => {
  it('sends the repository, no path prefix and the chosen role, with the entity tag of the team', async () => {
    const identity = fakeIdentity({ team: { ...TEAM, grants: [] } })

    await createAccessPorts(identity).grantTeam('developers', { repository: 'team', role: 'MAINTAIN' })

    expect(identity.teams.setGrants).toHaveBeenCalledWith(
      'developers',
      [{ repository: 'team', pathPrefix: null, role: 'MAINTAIN' }],
      '"5"'
    )
  })

  it('keeps the grants the team already has and appends the new one last', async () => {
    const identity = fakeIdentity()

    await createAccessPorts(identity).grantTeam('developers', { repository: 'team', role: 'DEPLOY' })

    const [, grants] = identity.teams.setGrants.mock.calls[0]
    expect(grants).toEqual([
      { repository: null, pathPrefix: null, role: 'READ' },
      { repository: 'releases', pathPrefix: 'com/example', role: 'DEPLOY' },
      { repository: 'team', pathPrefix: null, role: 'DEPLOY' }
    ])
  })

  it('reads the team before it writes, so the write carries a current tag', async () => {
    const identity = fakeIdentity()
    const order = []
    identity.teams.get.mockImplementation(async () => { order.push('get'); return { data: TEAM, headers: { etag: '"7"' } } })
    identity.teams.setGrants.mockImplementation(async () => { order.push('setGrants') })

    await createAccessPorts(identity).grantTeam('developers', { repository: 'team', role: 'READ' })

    expect(order).toEqual(['get', 'setGrants'])
    expect(identity.teams.setGrants.mock.calls[0][2]).toBe('"7"')
  })

  it('does not write again when the team already has exactly this grant, so a retry is harmless', async () => {
    const team = { ...TEAM, grants: [{ repository: 'team', pathPrefix: null, role: 'DEPLOY' }] }
    const identity = fakeIdentity({ team })

    await createAccessPorts(identity).grantTeam('developers', { repository: 'team', role: 'DEPLOY' })

    expect(identity.teams.setGrants).not.toHaveBeenCalled()
  })
})

describe('create the machine account', () => {
  it('creates a principal of kind MACHINE with the name', async () => {
    const identity = fakeIdentity()

    await createAccessPorts(identity).createMachine('team-deploy')

    expect(identity.principals.create).toHaveBeenCalledWith({ kind: 'MACHINE', name: 'team-deploy' })
  })
})

describe('add the machine account to the team', () => {
  it('sends the manual members plus the machine account, with the entity tag of the team', async () => {
    const identity = fakeIdentity()

    await createAccessPorts(identity).addMachineToTeam('developers', 'team-deploy')

    expect(identity.teams.setMembers).toHaveBeenCalledWith('developers', ['alice', 'ci-bot', 'team-deploy'], '"5"')
  })

  it('leaves the synchronized members out, because the server keeps them itself', async () => {
    const identity = fakeIdentity()

    await createAccessPorts(identity).addMachineToTeam('developers', 'team-deploy')

    expect(identity.teams.setMembers.mock.calls[0][1]).not.toContain('bob')
  })

  it('does not write again when the machine account is a member already, so a retry is harmless', async () => {
    const identity = fakeIdentity()

    await createAccessPorts(identity).addMachineToTeam('developers', 'ci-bot')

    expect(identity.teams.setMembers).not.toHaveBeenCalled()
  })
})

describe('create the token of the machine account', () => {
  it('creates it on the machine account, named like the account, with the chosen scopes', async () => {
    const identity = fakeIdentity()

    await createAccessPorts(identity).createMachineToken('team-deploy', ['READ'])

    expect(identity.principalTokens).toHaveBeenCalledWith('team-deploy')
    expect(identity.tokens.create).toHaveBeenCalledWith({ name: 'team-deploy', expiresAt: null, scopes: ['READ'] })
  })

  it('answers with the secret, which the server shows only once', async () => {
    const identity = fakeIdentity()

    const secret = await createAccessPorts(identity).createMachineToken('team-deploy', ['READ', 'WRITE'])

    expect(secret).toBe('secret-1')
  })
})
