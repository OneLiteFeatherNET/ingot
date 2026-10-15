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
import { collectionsOf, singularOf, entryLayout, summaryOf, filterEntries, validateNewId } from './collections'

const repositoryItem = {
  type: 'object',
  properties: {
    id: { type: 'string', title: 'Id' },
    visibility: { type: 'string', enum: ['PUBLIC', 'HIDDEN', 'PRIVATE'], title: 'Visibility' },
    redeployment: { type: 'boolean', title: 'Redeployment' },
    storageProvider: { oneOf: [{ type: 'object', properties: { type: { const: 's3' } } }] },
    limits: { type: 'object', title: 'Limits', properties: { maxSize: { type: 'integer', title: 'Max size' } } }
  },
  additionalProperties: false
}

const mavenSchema = {
  title: 'Maven',
  type: 'object',
  properties: {
    name: { type: 'string', title: 'Name' },
    repositories: {
      title: 'Repositories',
      description: 'List of Maven repositories.',
      type: 'array',
      items: repositoryItem
    }
  }
}

describe('collectionsOf', () => {
  it('finds an array of objects that carry an id', () => {
    const collections = collectionsOf(mavenSchema)

    expect(collections.map(collection => collection.key)).toEqual(['repositories'])
  })

  it('takes the title and description of the array for the collection', () => {
    const [collection] = collectionsOf(mavenSchema)

    expect(collection.title).toBe('Repositories')
    expect(collection.description).toBe('List of Maven repositories.')
    expect(collection.itemSchema).toBe(repositoryItem)
  })

  it('ignores arrays of objects without an id property', () => {
    const schema = {
      type: 'object',
      properties: {
        hooks: { type: 'array', items: { type: 'object', properties: { command: { type: 'string' } } } }
      }
    }

    expect(collectionsOf(schema)).toEqual([])
  })

  it('ignores arrays of plain strings', () => {
    const schema = { type: 'object', properties: { groups: { type: 'array', items: { type: 'string' } } } }

    expect(collectionsOf(schema)).toEqual([])
  })

  it('returns no collections for a schema that is not loaded yet', () => {
    expect(collectionsOf(undefined)).toEqual([])
  })
})

describe('singularOf', () => {
  it('drops the plural s', () => {
    expect(singularOf('Repositories')).toBe('Repository')
  })

  it('turns a plural in ies into y', () => {
    expect(singularOf('Entries')).toBe('Entry')
  })

  it('keeps words that end in ss', () => {
    expect(singularOf('Access')).toBe('Access')
  })

  it('keeps a title that is already singular', () => {
    expect(singularOf('Mirror')).toBe('Mirror')
  })
})

describe('entryLayout', () => {
  it('leaves the id out of the form, because the id is the heading of the entry', () => {
    const layout = entryLayout(repositoryItem)

    expect(JSON.stringify(layout)).not.toContain('#/properties/id"')
  })

  it('puts the scalar properties in one General group first', () => {
    const [general] = entryLayout(repositoryItem).elements

    expect(general.type).toBe('Group')
    expect(general.label).toBe('General')
    expect(general.elements.map(control => control.scope)).toEqual([
      '#/properties/visibility',
      '#/properties/redeployment'
    ])
  })

  it('gives every nested object and array its own control after the General group', () => {
    const scopes = entryLayout(repositoryItem).elements.slice(1).map(element => element.scope)

    expect(scopes).toEqual(['#/properties/storageProvider', '#/properties/limits'])
  })

  it('omits the General group when every property is nested', () => {
    const schema = { type: 'object', properties: { id: { type: 'string' }, limits: { type: 'object', properties: {} } } }

    const layout = entryLayout(schema)

    expect(layout.elements.map(element => element.type)).toEqual(['Control'])
  })

  it('keeps the id when the caller excludes nothing, as a domain form does', () => {
    const layout = entryLayout({ type: 'object', properties: { id: { type: 'string' } } }, { exclude: [] })

    expect(layout.elements[0].elements.map(control => control.scope)).toEqual(['#/properties/id'])
  })
})

describe('summaryOf', () => {
  it('shows the value of the first enum or text property as the supporting line', () => {
    const summary = summaryOf(repositoryItem, { id: 'releases', visibility: 'PUBLIC', redeployment: true })

    expect(summary).toBe('PUBLIC')
  })

  it('skips properties without a value', () => {
    const schema = {
      type: 'object',
      properties: {
        id: { type: 'string' },
        description: { type: 'string' },
        visibility: { type: 'string', enum: ['PUBLIC'] }
      }
    }

    expect(summaryOf(schema, { id: 'a', visibility: 'PUBLIC' })).toBe('PUBLIC')
  })

  it('is empty when no scalar text value exists', () => {
    expect(summaryOf(repositoryItem, { id: 'releases', redeployment: true })).toBe('')
  })
})

describe('filterEntries', () => {
  const entries = [{ id: 'releases' }, { id: 'snapshots' }, { id: 'Private-Mirror' }]

  it('returns every entry with its position in the list when the query is empty', () => {
    expect(filterEntries(entries, '').map(item => item.index)).toEqual([0, 1, 2])
  })

  it('matches the id, ignoring case', () => {
    expect(filterEntries(entries, 'MIRROR').map(item => item.id)).toEqual(['Private-Mirror'])
  })

  it('keeps the index of the entry in the full list, not in the filtered list', () => {
    expect(filterEntries(entries, 'snap')).toEqual([{ index: 1, id: 'snapshots', entry: { id: 'snapshots' } }])
  })

  it('ignores surrounding spaces in the query', () => {
    expect(filterEntries(entries, '  releases ').map(item => item.id)).toEqual(['releases'])
  })
})

describe('validateNewId', () => {
  const existing = ['releases', 'snapshots']

  it('asks for an id when the field is empty', () => {
    expect(validateNewId('', existing, 'repository')).toBe('Enter an id.')
  })

  it('refuses an id that another repository already uses', () => {
    expect(validateNewId('releases', existing, 'repository')).toBe('A repository with this id already exists.')
  })

  it('treats an id that differs only in case as a duplicate', () => {
    expect(validateNewId('Releases', existing, 'repository')).toBe('A repository with this id already exists.')
  })

  it.each(['my repo', 'a/b', 'ä', 'name?', 'trailing ', 'tab\t'])('refuses %j, which is not URL-safe', (id) => {
    expect(validateNewId(id, existing, 'repository')).toBe('Use only letters, digits, dots, underscores and hyphens.')
  })

  it.each(['my-repo', 'libs.v2', 'snapshots_2', 'A1'])('accepts %j', (id) => {
    expect(validateNewId(id, existing, 'repository')).toBe('')
  })
})
