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
import { domainSummary, hubGroups } from './settingsHub'
import { ADMIN_NODES } from '../../store/nodeAccess'

// Fixtures are built per test, so no test can change what another one reads.
const collectionSchema = () => ({
  title: 'Maven',
  description: 'Repositories',
  properties: {
    repositories: {
      type: 'array',
      title: 'Repositories',
      items: { type: 'object', title: 'Maven Repository', properties: { id: { type: 'string' } } }
    }
  }
})

const plainSchema = () => ({
  title: 'Web',
  description: 'General web settings',
  properties: { forwardedIp: { type: 'string' } }
})

const entries = (count) => Array.from({ length: count }, (_, index) => ({ id: `repo-${index}` }))

const fixture = (overrides = {}) => ({
  domains: ['maven', 'web', 'acme'],
  schemas: {
    maven: collectionSchema(),
    web: plainSchema(),
    acme: { title: 'Acme', properties: {} }
  },
  saved: { maven: { repositories: entries(6) }, web: {}, acme: {} },
  unsaved: [],
  titleOf: (domain) => ({ maven: 'Maven', web: 'Web', acme: 'Acme' })[domain],
  can: () => true,
  ...overrides
})

// The cards that open a settings domain, the ones this test is about. The other cards are the Access and page links.
const domainCards = (group) => group.cards.filter(card => card.to.name === 'settings')

describe('domainSummary', () => {
  it('counts the entries of a collection in its plural name', () => {
    expect(domainSummary(collectionSchema(), { repositories: entries(6) })).toBe('6 repositories')
  })

  it('uses the singular name for one entry', () => {
    expect(domainSummary(collectionSchema(), { repositories: entries(1) })).toBe('1 maven repository')
  })

  it('counts zero for a collection the saved value does not hold', () => {
    expect(domainSummary(collectionSchema(), {})).toBe('0 repositories')
  })

  it('falls back to the description of a domain without collections', () => {
    expect(domainSummary(plainSchema(), {})).toBe('General web settings')
  })

  it('is empty for a domain with neither collections nor a description', () => {
    expect(domainSummary({ properties: {} }, {})).toBe('')
  })
})

describe('hubGroups', () => {
  it('shows no card for the maven domain, and places the other domains, unnamed ones too, in System', () => {
    const groups = hubGroups(fixture())

    expect(groups.flatMap(group => domainCards(group).map(card => card.key)), 'no maven card').not.toContain('settings:maven')
    expect(domainCards(groups.find(group => group.heading === 'System')).map(card => card.key), 'the other domains').toEqual(['settings:web', 'settings:acme'])
  })

  it('gives the Repositories page the saved summary of the maven domain, since the domain is edited there', () => {
    const repository = hubGroups(fixture()).find(group => group.heading === 'Repository')

    expect(repository.cards.find(card => card.key === 'page:repositories')).toEqual({
      key: 'page:repositories',
      title: 'Repositories',
      summary: '6 repositories',
      unsaved: false,
      to: { name: 'settings-repositories' }
    })
  })

  it('marks the Repositories page when the maven draft differs, and keeps its saved summary', () => {
    const groups = hubGroups(fixture({ unsaved: ['maven'] }))
    const repositories = groups.find(group => group.heading === 'Repository').cards.find(card => card.key === 'page:repositories')

    expect(repositories.unsaved).toBe(true)
    expect(repositories.summary, 'the card shows the saved value, not the draft').toBe('6 repositories')
  })

  it('leaves out every settings domain from a visitor without the settings node', () => {
    const groups = hubGroups(fixture({ can: (node) => node !== ADMIN_NODES.settings }))

    expect(groups.flatMap(group => domainCards(group)), 'no domain card is offered').toEqual([])
  })

  it('leaves out the domains that are edited under Access, and the maven domain', () => {
    const groups = hubGroups(fixture({ domains: ['maven', 'login', 'authentication'] }))

    expect(groups.flatMap(group => domainCards(group).map(card => card.key))).toEqual([])
  })
})
