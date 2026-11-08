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
  editable: () => true,
  ...overrides
})

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
  it('orders the groups Repository, Instance, Plugins and puts an unnamed domain in Plugins', () => {
    const groups = hubGroups(fixture())

    expect(groups.map(group => [group.heading, group.cards.map(card => card.key)])).toEqual([
      ['Repository', ['maven']],
      ['Instance', ['web']],
      ['Plugins', ['acme']]
    ])
  })

  it('gives a card its title, the saved summary and the address of its domain', () => {
    const [repository] = hubGroups(fixture())

    expect(repository.cards[0]).toEqual({
      key: 'maven',
      title: 'Maven',
      summary: '6 repositories',
      unsaved: false,
      to: { name: 'settings', params: { domain: 'maven' } }
    })
  })

  it('marks a domain with a draft change and keeps its saved summary', () => {
    const [repository] = hubGroups(fixture({ unsaved: ['maven'] }))

    expect(repository.cards[0].unsaved).toBe(true)
    expect(repository.cards[0].summary, 'the card shows the saved value, not the draft').toBe('6 repositories')
  })

  it('leaves out a group whose domains the visitor may not edit', () => {
    const groups = hubGroups(fixture({ editable: (domain) => domain !== 'web' }))

    expect(groups.map(group => group.heading)).toEqual(['Repository', 'Plugins'])
  })

  it('leaves out the domains that are edited under Access', () => {
    const groups = hubGroups(fixture({ domains: ['maven', 'login', 'authentication'] }))

    expect(groups.flatMap(group => group.cards.map(card => card.key))).toEqual(['maven'])
  })
})
