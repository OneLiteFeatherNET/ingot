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
import { attentionRows, glanceRows, unsavedTreeKeys } from './settingsHub'

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

const twoCollectionSchema = () => ({
  title: 'Cleanup',
  properties: {
    policies: { type: 'array', title: 'Policies', items: { type: 'object', title: 'Cleanup policy', properties: { id: { type: 'string' } } } },
    exclusions: { type: 'array', title: 'Exclusions', items: { type: 'object', title: 'Exclusion', properties: { id: { type: 'string' } } } }
  }
})

const plainSchema = () => ({
  title: 'Web',
  description: 'General web settings',
  properties: { forwardedIp: { type: 'string' } }
})

const entries = (count) => Array.from({ length: count }, (_, index) => ({ id: `item-${index}` }))

// The settings links of the Administration tree, as the view passes them: the Repositories page edits the maven domain.
const links = () => [
  { key: 'page:repositories', label: 'Repositories', to: { name: 'settings-repositories' } },
  { key: 'settings:cleanup', label: 'Cleanup policies', to: { name: 'settings', params: { domain: 'cleanup' } } },
  { key: 'settings:javadoc', label: 'Javadoc', to: { name: 'settings', params: { domain: 'javadoc' } } },
  { key: 'settings:web', label: 'Reverse proxy', to: { name: 'settings', params: { domain: 'web' } } }
]

describe('glanceRows', () => {
  it('counts the saved entries of a collection under the label of its tree entry', () => {
    const rows = glanceRows({
      links: links(),
      schemas: { maven: collectionSchema(), cleanup: { properties: {} }, javadoc: { properties: {} }, web: plainSchema() },
      saved: { maven: { repositories: entries(7) }, cleanup: {}, javadoc: {}, web: {} }
    })

    expect(rows.find(row => row.key === 'page:repositories'), 'the Repositories page counts the maven domain').toEqual({
      key: 'page:repositories',
      label: 'Repositories',
      count: 7
    })
  })

  it('counts zero for a collection the saved value does not hold', () => {
    const rows = glanceRows({
      links: [{ key: 'settings:cleanup', label: 'Cleanup policies', to: { name: 'settings', params: { domain: 'cleanup' } } }],
      schemas: { cleanup: twoCollectionSchema() },
      saved: { cleanup: {} }
    })

    expect(rows.map(row => row.count), 'every collection is listed, empty ones with 0').toEqual([0, 0])
  })

  it('names each collection of a domain with two collections after the domain and the collection', () => {
    const rows = glanceRows({
      links: [{ key: 'settings:cleanup', label: 'Cleanup policies', to: { name: 'settings', params: { domain: 'cleanup' } } }],
      schemas: { cleanup: twoCollectionSchema() },
      saved: { cleanup: { policies: entries(3), exclusions: [] } }
    })

    expect(rows).toEqual([
      { key: 'settings:cleanup:policies', label: 'Cleanup policies, Policies', count: 3 },
      { key: 'settings:cleanup:exclusions', label: 'Cleanup policies, Exclusions', count: 0 }
    ])
  })

  it('gives no row for a domain without a collection', () => {
    const rows = glanceRows({ links: links(), schemas: { web: plainSchema() }, saved: { web: {} } })

    expect(rows.filter(row => row.key.includes('web')), 'the reverse proxy has no count').toEqual([])
  })

  it('counts the collection of a plugin domain under its section label', () => {
    const rows = glanceRows({
      links: [{ key: 'settings:acme', label: 'Acme mirrors', to: { name: 'settings', params: { domain: 'acme' } } }],
      schemas: { acme: collectionSchema() },
      saved: { acme: { repositories: entries(2) } }
    })

    expect(rows).toEqual([{ key: 'settings:acme', label: 'Acme mirrors', count: 2 }])
  })
})

describe('attentionRows', () => {
  it('lists the tree entries whose key has an unsaved draft, in the order of the tree, with their targets', () => {
    const rows = attentionRows(links(), ['settings:javadoc', 'page:repositories'])

    expect(rows).toEqual([
      { key: 'page:repositories', label: 'Repositories', to: { name: 'settings-repositories' } },
      { key: 'settings:javadoc', label: 'Javadoc', to: { name: 'settings', params: { domain: 'javadoc' } } }
    ])
  })

  it('gives no row without a draft', () => {
    expect(attentionRows(links(), [])).toEqual([])
  })
})

describe('unsavedTreeKeys', () => {
  it('maps a domain with a draft to its settings entry of the tree', () => {
    expect(unsavedTreeKeys(['javadoc'])).toEqual(['settings:javadoc'])
  })

  it('maps the maven draft to the Repositories page, since that page edits the domain', () => {
    expect(unsavedTreeKeys(['maven', 'javadoc'])).toEqual(['page:repositories', 'settings:javadoc'])
  })

  it('gives no key for no draft', () => {
    expect(unsavedTreeKeys([])).toEqual([])
  })
})
