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
import { diffSettings, groupChanges, formatChangeValue } from './settingsDiff'

const SCHEMAS = {
  maven: {
    type: 'object',
    title: 'Maven',
    properties: {
      repositories: {
        type: 'array',
        title: 'Repositories',
        items: {
          type: 'object',
          properties: {
            id: { type: 'string', title: 'Id' },
            visibility: { type: 'string', title: 'Visibility' }
          }
        }
      }
    }
  },
  frontend: {
    type: 'object',
    title: 'Frontend',
    properties: {
      title: { type: 'string', title: 'Site title' },
      appearance: {
        type: 'object',
        title: 'Appearance',
        properties: { primaryColour: { type: 'string', title: 'Primary colour' } }
      }
    }
  }
}

const repo = (id, visibility) => ({ id, visibility })

describe('groupChanges', () => {
  it('groups the changes by domain, then by the entry they belong to, in order', () => {
    const saved = {
      frontend: { title: 'Ingot', appearance: { primaryColour: '#6750a4' } },
      maven: { repositories: [repo('releases', 'PUBLIC'), repo('snapshots', 'PRIVATE')] }
    }
    const draft = {
      frontend: { title: 'Ingot 2', appearance: { primaryColour: '#ff0000' } },
      maven: { repositories: [repo('releases', 'PRIVATE'), repo('snapshots', 'PUBLIC')] }
    }

    const sections = groupChanges(diffSettings(saved, draft, SCHEMAS))

    expect(sections.map(section => section.domain)).toEqual(['frontend', 'maven'])
    expect(sections[0].groups.map(group => group.id)).toEqual([null])
    expect(sections[0].groups[0].changes).toHaveLength(2)
    expect(sections[1].groups.map(group => group.id)).toEqual(['releases', 'snapshots'])
    expect(sections[1].groups[0].title).toBe('Repositories')
  })

  it('returns no sections when nothing changed', () => {
    expect(groupChanges([])).toEqual([])
  })
})

describe('formatChangeValue', () => {
  it('quotes strings and writes numbers and booleans as they are', () => {
    expect(formatChangeValue('PUBLIC')).toBe('"PUBLIC"')
    expect(formatChangeValue(4)).toBe('4')
    expect(formatChangeValue(false)).toBe('false')
  })

  it('counts the items of a list', () => {
    expect(formatChangeValue(['a'])).toBe('1 item')
    expect(formatChangeValue(['a', 'b'])).toBe('2 items')
  })

  it('summarises an object by its plain fields and leaves the sensitive ones out', () => {
    expect(formatChangeValue({ type: 's3', quota: '10GB', secretKey: 'hidden', endpoint: 'https://s3.example' }))
      .toBe('type: "s3", quota: "10GB"')
  })

  it('says when a value is not set', () => {
    expect(formatChangeValue(undefined)).toBe('not set')
  })
})
