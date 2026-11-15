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
import { createAjv } from '@jsonforms/core'
import { parseSettingsFile, validateImport, overlayImport } from './settingsImport'

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
            visibility: { type: 'string', enum: ['PUBLIC', 'PRIVATE'], title: 'Visibility' },
            redeployment: { type: 'boolean', title: 'Redeployment' }
          }
        }
      }
    }
  },
  frontend: {
    type: 'object',
    title: 'Frontend',
    properties: { title: { type: 'string', title: 'Site title' } }
  }
}

const VALIDATOR = createAjv({ useDefaults: true, removeAdditional: false })

const validFile = () => ({
  maven: { repositories: [{ id: 'releases', visibility: 'PUBLIC', redeployment: false }] },
  frontend: { title: 'Ingot' }
})

describe('parseSettingsFile', () => {
  it('reads a JSON object of domains', () => {
    const parsed = parseSettingsFile(JSON.stringify(validFile()))

    expect(parsed).toEqual({ ok: true, value: validFile() })
  })

  it('refuses text that is not JSON, and says so', () => {
    const parsed = parseSettingsFile('{ "maven": ')

    expect(parsed.ok).toBe(false)
    expect(parsed.error).toContain('not valid JSON')
  })

  it('refuses a JSON value that is not an object of domains', () => {
    expect(parseSettingsFile('[1, 2]').error).toContain('settings object')
    expect(parseSettingsFile('"maven"').error).toContain('settings object')
  })
})

describe('validateImport', () => {
  it('accepts a valid file and returns the values of the domains it names', () => {
    const result = validateImport(validFile(), { domains: ['maven', 'frontend'], schemas: SCHEMAS, validator: VALIDATOR })

    expect(result.ok).toBe(true)
    expect(result.errors).toEqual([])
    expect(result.domains).toEqual(validFile())
  })

  it('accepts a file that names only some of the domains', () => {
    const result = validateImport({ frontend: { title: 'Ingot' } }, { domains: ['maven', 'frontend'], schemas: SCHEMAS, validator: VALIDATOR })

    expect(result.ok).toBe(true)
    expect(Object.keys(result.domains)).toEqual(['frontend'])
  })

  it('names a domain the server does not have as an error of that domain', () => {
    const result = validateImport({ stray: {} }, { domains: ['maven'], schemas: SCHEMAS, validator: VALIDATOR })

    expect(result.ok).toBe(false)
    expect(result.errors).toEqual([{ domain: 'stray', label: '', message: 'Unknown settings domain' }])
  })

  it('reports an invalid value by domain and by the field, labelled with the schema titles', () => {
    const file = validFile()
    file.maven.repositories[0].visibility = 'SECRET'

    const result = validateImport(file, { domains: ['maven', 'frontend'], schemas: SCHEMAS, validator: VALIDATOR })

    expect(result.ok).toBe(false)
    expect(result.errors).toEqual([
      expect.objectContaining({
        domain: 'maven',
        label: 'Repositories > releases > Visibility',
        message: 'must be one of "PUBLIC", "PRIVATE"'
      })
    ])
  })

  it('reports a wrong type by the entry that holds it', () => {
    const file = validFile()
    file.maven.repositories.push({ id: 'snapshots', visibility: 'PUBLIC', redeployment: 'yes' })

    const [error] = validateImport(file, { domains: ['maven'], schemas: SCHEMAS, validator: VALIDATOR }).errors

    expect(error).toMatchObject({ domain: 'maven', label: 'Repositories > snapshots > Redeployment' })
    expect(error.message).toContain('boolean')
  })

  it('hands nothing over when any domain has an error', () => {
    const file = validFile()
    file.maven.repositories[0].visibility = 'SECRET'

    const result = validateImport(file, { domains: ['maven', 'frontend'], schemas: SCHEMAS, validator: VALIDATOR })

    expect(result.domains).toEqual({})
  })

  it('does not change the file it is given', () => {
    const file = validFile()
    const before = JSON.stringify(file)

    validateImport(file, { domains: ['maven', 'frontend'], schemas: SCHEMAS, validator: VALIDATOR })

    expect(JSON.stringify(file)).toBe(before)
  })
})

describe('overlayImport', () => {
  it('keeps the draft fields that the file does not name', () => {
    const draft = { id: 'ingot-repository', title: 'Ingot', description: 'Mirror' }

    const merged = overlayImport({ frontend: { title: 'Imported' } }, { frontend: draft })

    expect(merged.frontend).toEqual({ id: 'ingot-repository', title: 'Imported', description: 'Mirror' })
  })

  it('replaces a list the file names, as a whole', () => {
    const draft = { maven: { repositories: [{ id: 'releases' }, { id: 'snapshots' }] } }

    const merged = overlayImport({ maven: { repositories: [{ id: 'plugins' }] } }, draft)

    expect(merged.maven.repositories).toEqual([{ id: 'plugins' }])
  })

  it('takes a domain the draft does not have as it is', () => {
    expect(overlayImport({ frontend: { title: 'Imported' } }, {})).toEqual({ frontend: { title: 'Imported' } })
  })

  it('does not change the draft it is given', () => {
    const draft = { frontend: { id: 'ingot-repository', title: 'Ingot' } }

    overlayImport({ frontend: { title: 'Imported' } }, draft)

    expect(draft.frontend.title).toBe('Ingot')
  })
})
