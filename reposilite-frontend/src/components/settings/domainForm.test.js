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
import { domainFormOf } from './domainForm'

const repositoriesProperty = {
  type: 'array',
  title: 'Repositories',
  items: { type: 'object', properties: { id: { type: 'string' } } }
}

const cacheField = {
  type: 'integer',
  title: 'Resolution cache maximum age in cluster mode'
}

describe('domainFormOf', () => {
  it('builds the form of the properties that are not collections, and leaves the collections out', () => {
    const schema = { title: 'Maven', properties: { repositories: repositoriesProperty, resolutionCacheClusterMaxAgeSeconds: cacheField } }

    const form = domainFormOf(schema, ['repositories'])

    expect(Object.keys(form.schema.properties), 'only the field that is not a collection').toEqual(['resolutionCacheClusterMaxAgeSeconds'])
    expect(form.schema.title, 'the schema keeps the domain title').toBe('Maven')
    expect(form.layout, 'the form has a layout').toBeDefined()
  })

  it('returns null when every property of the domain is a collection', () => {
    const schema = { title: 'Javadoc', properties: { repositories: repositoriesProperty } }

    expect(domainFormOf(schema, ['repositories'])).toBeNull()
  })

  it('returns null when the schema has no properties at all', () => {
    expect(domainFormOf(undefined, [])).toBeNull()
    expect(domainFormOf({ title: 'Empty' }, [])).toBeNull()
  })
})
