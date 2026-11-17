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
import type { components } from '../../types/api'
import { accessView, signInSources, kindLabel } from './accessView'

type OwnAccessSummary = components['schemas']['OwnAccessSummaryDto']

// A fixed summary: no clock, no network, a fresh object for every test.
const summary = (overrides: Partial<OwnAccessSummary> = {}): OwnAccessSummary => ({
  principal: { name: 'alice', displayName: 'Alice Example', kind: 'USER', providers: ['ldap'] },
  instanceAdmin: false,
  teams: [{ name: 'releases-maintainers', parents: ['maintainers'] }],
  nodes: [
    'ingot.repository.releases.read',
    'ingot.repository.releases.deploy',
    'ingot.admin.users',
    'ingot.artifact.net.example.deploy',
    'ingot.repository.releases.artifact.net.example:lib.read',
    'ingot.plugin.custom',
  ],
  denials: [{ node: 'ingot.repository.releases.delete', overridden: false }],
  grants: [{ team: 'releases-maintainers', repository: 'releases', pathPrefix: 'net/example', role: 'DEPLOY' }],
  tokenNarrowed: false,
  token: null,
  ...overrides,
})

const meaningOf = (node: string, nodes: string[]) => {
  const view = accessView(summary({ nodes, denials: [] }))
  const entry = view.permissions.allowed.find(item => item.node === node)
  return entry?.meaning
}

describe('accessView', () => {
  it('reports ready when a summary is given', () => {
    expect(accessView(summary()).status).toBe('ready')
  })

  it('reports loading and unavailable without inventing any rights', () => {
    expect(accessView('loading').status).toBe('loading')
    const unavailable = accessView('unavailable')
    expect(unavailable.status).toBe('unavailable')
    expect(unavailable.permissions.allowed, 'no rights are drawn while unavailable').toEqual([])
  })

  it('lists a team with its direct parents', () => {
    const view = accessView(summary())

    expect(view.teams, 'the team and its direct parent').toEqual([{ name: 'releases-maintainers', parents: ['maintainers'] }])
  })

  it('states whether the principal is an instance administrator and whether a token narrows the list', () => {
    const view = accessView(summary({ instanceAdmin: true, tokenNarrowed: true }))

    expect(view.instanceAdmin).toBe(true)
    expect(view.tokenNarrowed).toBe(true)
  })

  it('builds one plain sentence for a repository action', () => {
    expect(meaningOf('ingot.repository.releases.deploy', ['ingot.repository.releases.deploy'])).toBe('Deploy to releases')
  })

  it('builds one plain sentence for an admin area', () => {
    expect(meaningOf('ingot.admin.users', ['ingot.admin.users'])).toBe('Manage users')
  })

  it('builds one plain sentence for every administrative area', () => {
    expect(meaningOf('ingot.admin.*', ['ingot.admin.*'])).toBe('Full instance administration')
  })

  it('builds one plain sentence for an artifact node without a repository', () => {
    expect(meaningOf('ingot.artifact.net.example.deploy', ['ingot.artifact.net.example.deploy'])).toBe('Deploy artifacts under net.example')
  })

  it('builds one plain sentence for an artifact node with a repository', () => {
    const node = 'ingot.repository.releases.artifact.net.example:lib.read'

    expect(meaningOf(node, [node])).toBe('Read artifacts under net.example:lib in releases')
  })

  it('shows a node the table does not know verbatim, under Other', () => {
    const view = accessView(summary({ nodes: ['ingot.plugin.custom'], denials: [] }))

    const entry = view.permissions.allowed.find(item => item.node === 'ingot.plugin.custom')
    expect(entry, 'the unknown node is kept, not dropped').toBeDefined()
    expect(entry!.meaning).toBe('ingot.plugin.custom')
    expect(entry!.group).toBe('Other')
  })

  it('lists denials apart from the allowed nodes', () => {
    const view = accessView(summary())

    expect(view.permissions.denied.map(item => item.node)).toEqual(['ingot.repository.releases.delete'])
    expect(view.permissions.allowed.map(item => item.node), 'a denial is not drawn as allowed').not.toContain('ingot.repository.releases.delete')
  })

  it('draws a repository grant with its path prefix, role and the team it comes through', () => {
    const view = accessView(summary())

    expect(view.scopes).toEqual([
      expect.objectContaining({
        type: 'repository',
        repository: 'releases',
        pathPrefix: 'net/example',
        role: 'DEPLOY',
        via: 'releases-maintainers',
      }),
    ])
  })

  it('shows a grant of a type the view does not know as one plain line, and does not drop it', () => {
    const view = accessView(summary({
      grants: [{ team: 'releases-maintainers', repository: null, pathPrefix: null, role: 'READ' }],
    }))

    expect(view.scopes, 'no repository scope is drawn for it').toEqual([])
    expect(view.unknown, 'the grant is kept as one line').toHaveLength(1)
    expect(view.unknown[0].text).toContain('releases-maintainers')
  })
})

describe('signInSources', () => {
  it('names a linked directory provider in capitals', () => {
    expect(signInSources(['ldap'], 'USER')).toEqual(['LDAP'])
  })

  it('names a password account as Local when it has no provider listed', () => {
    expect(signInSources([], 'USER')).toEqual(['Local'])
  })

  it('shows an unknown provider id as the id itself', () => {
    expect(signInSources(['saml'], 'USER')).toEqual(['saml'])
  })
})

describe('kindLabel', () => {
  it('reads the kinds of a principal in plain words', () => {
    expect(kindLabel('USER')).toBe('User')
    expect(kindLabel('MACHINE')).toBe('Machine')
  })
})
