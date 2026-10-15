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
import { diffSettings, revertChange } from './settingsDiff'

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
          title: 'Repository',
          properties: {
            id: { type: 'string', title: 'Id' },
            visibility: { type: 'string', title: 'Visibility' },
            redeployment: { type: 'boolean', title: 'Redeployment' },
            storageProvider: {
              title: 'Storage provider',
              oneOf: [
                {
                  type: 'object',
                  title: 'Filesystem',
                  properties: {
                    type: { type: 'string', const: 'fs' },
                    quota: { type: 'string', title: 'Quota' }
                  }
                },
                {
                  type: 'object',
                  title: 'S3',
                  properties: {
                    type: { type: 'string', const: 's3' },
                    secretKey: { type: 'string', title: 'Secret key' },
                    endpoint: { type: 'string', title: 'Endpoint' }
                  }
                }
              ]
            }
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
        properties: {
          primaryColour: { type: 'string', title: 'Primary colour' }
        }
      }
    }
  }
}

const fs = (quota) => ({ type: 'fs', quota })
const s3 = (secretKey, endpoint = 'https://s3.example') => ({ type: 's3', secretKey, endpoint })

const repo = (id, visibility, storageProvider = fs('10GB')) => ({ id, visibility, redeployment: false, storageProvider })

const savedMaven = (repositories) => ({ maven: { repositories } })

describe('diffSettings', () => {
  it('reports nothing when the draft equals the saved state', () => {
    const saved = { maven: { repositories: [repo('releases', 'PUBLIC')] }, frontend: { title: 'Ingot' } }

    expect(diffSettings(saved, JSON.parse(JSON.stringify(saved)), SCHEMAS)).toEqual([])
  })

  it('reports a nested field as one change with its path and labels', () => {
    const saved = { frontend: { title: 'Ingot', appearance: { primaryColour: '#6750a4' } } }
    const draft = { frontend: { title: 'Ingot', appearance: { primaryColour: '#ff0000' } } }

    const changes = diffSettings(saved, draft, SCHEMAS)

    expect(changes).toHaveLength(1)
    expect(changes[0]).toMatchObject({
      domain: 'frontend',
      domainTitle: 'Frontend',
      kind: 'changed',
      group: null,
      path: ['appearance', 'primaryColour'],
      label: 'Appearance > Primary colour',
      before: '#6750a4',
      after: '#ff0000'
    })
  })

  it('matches entries by id, so a reordered list with one edit is one change', () => {
    const saved = savedMaven([repo('releases', 'PUBLIC'), repo('snapshots', 'PRIVATE')])
    const draft = savedMaven([repo('snapshots', 'PRIVATE'), repo('releases', 'PRIVATE')])

    const changes = diffSettings(saved, draft, SCHEMAS)

    expect(changes).toHaveLength(1)
    expect(changes[0]).toMatchObject({
      kind: 'changed',
      group: { key: 'repositories', id: 'releases' },
      label: 'Visibility',
      before: 'PUBLIC',
      after: 'PRIVATE'
    })
  })

  it('reports only the reordering as no change', () => {
    const saved = savedMaven([repo('releases', 'PUBLIC'), repo('snapshots', 'PRIVATE')])
    const draft = savedMaven([repo('snapshots', 'PRIVATE'), repo('releases', 'PUBLIC')])

    expect(diffSettings(saved, draft, SCHEMAS)).toEqual([])
  })

  it('reports an added entry as one added change for that entry', () => {
    const saved = savedMaven([repo('releases', 'PUBLIC')])
    const draft = savedMaven([repo('releases', 'PUBLIC'), repo('staging', 'PRIVATE')])

    const changes = diffSettings(saved, draft, SCHEMAS)

    expect(changes).toHaveLength(1)
    expect(changes[0]).toMatchObject({
      kind: 'added',
      group: { key: 'repositories', id: 'staging' },
      path: ['repositories', { id: 'staging' }]
    })
  })

  it('reports a removed entry as one removed change, with its saved position', () => {
    const saved = savedMaven([repo('releases', 'PUBLIC'), repo('snapshots', 'PRIVATE'), repo('plugins', 'PUBLIC')])
    const draft = savedMaven([repo('releases', 'PUBLIC'), repo('plugins', 'PUBLIC')])

    const changes = diffSettings(saved, draft, SCHEMAS)

    expect(changes).toHaveLength(1)
    expect(changes[0]).toMatchObject({
      kind: 'removed',
      group: { key: 'repositories', id: 'snapshots' },
      index: 1
    })
  })

  it('reports a changed variant of a oneOf as one change, not one per field', () => {
    const saved = savedMaven([repo('releases', 'PUBLIC', fs('10GB'))])
    const draft = savedMaven([repo('releases', 'PUBLIC', s3('abc'))])

    const changes = diffSettings(saved, draft, SCHEMAS)

    expect(changes).toHaveLength(1)
    expect(changes[0]).toMatchObject({
      kind: 'changed',
      path: ['repositories', { id: 'releases' }, 'storageProvider'],
      label: 'Storage provider'
    })
    expect(changes[0].before).toEqual(fs('10GB'))
  })

  it('reports a field of the same oneOf variant with the variant in its label', () => {
    const saved = savedMaven([repo('releases', 'PUBLIC', fs('10GB'))])
    const draft = savedMaven([repo('releases', 'PUBLIC', fs('50GB'))])

    const changes = diffSettings(saved, draft, SCHEMAS)

    expect(changes).toHaveLength(1)
    expect(changes[0]).toMatchObject({ label: 'Storage provider > Quota', before: '10GB', after: '50GB' })
  })

  it('masks a secret as changed, without its values', () => {
    const saved = savedMaven([repo('releases', 'PUBLIC', s3('old-secret'))])
    const draft = savedMaven([repo('releases', 'PUBLIC', s3('new-secret'))])

    const [change] = diffSettings(saved, draft, SCHEMAS).filter(entry => entry.path.at(-1) === 'secretKey')

    expect(change.masked, 'the secret is masked').toBe(true)
    expect(change.before).toBeNull()
    expect(change.after).toBeNull()
    expect(JSON.stringify(change), 'neither value leaks into the change').not.toMatch(/old-secret|new-secret/)
  })

  it('does not change its inputs', () => {
    const saved = savedMaven([repo('releases', 'PUBLIC')])
    const draft = savedMaven([repo('releases', 'PRIVATE')])
    const before = JSON.stringify([saved, draft])

    diffSettings(saved, draft, SCHEMAS)

    expect(JSON.stringify([saved, draft])).toBe(before)
  })
})

describe('revertChange', () => {
  it('puts a changed field back to its saved value and leaves the other fields alone', () => {
    const saved = savedMaven([repo('releases', 'PUBLIC'), repo('snapshots', 'PRIVATE')])
    const draft = savedMaven([repo('releases', 'PRIVATE'), repo('snapshots', 'PUBLIC')])
    const [change] = diffSettings(saved, draft, SCHEMAS)

    const next = revertChange(draft.maven, saved.maven, change)

    expect(next.repositories.find(entry => entry.id === 'releases').visibility).toBe('PUBLIC')
    expect(next.repositories.find(entry => entry.id === 'snapshots').visibility, 'the other entry keeps its edit').toBe('PUBLIC')
  })

  it('removes an added entry', () => {
    const saved = savedMaven([repo('releases', 'PUBLIC')])
    const draft = savedMaven([repo('releases', 'PUBLIC'), repo('staging', 'PRIVATE')])
    const [change] = diffSettings(saved, draft, SCHEMAS)

    const next = revertChange(draft.maven, saved.maven, change)

    expect(next.repositories.map(entry => entry.id)).toEqual(['releases'])
  })

  it('puts a removed entry back at its saved position', () => {
    const saved = savedMaven([repo('releases', 'PUBLIC'), repo('snapshots', 'PRIVATE'), repo('plugins', 'PUBLIC')])
    const draft = savedMaven([repo('releases', 'PUBLIC'), repo('plugins', 'PUBLIC')])
    const [change] = diffSettings(saved, draft, SCHEMAS)

    const next = revertChange(draft.maven, saved.maven, change)

    expect(next.repositories.map(entry => entry.id)).toEqual(['releases', 'snapshots', 'plugins'])
  })

  it('restores the saved variant of a oneOf', () => {
    const saved = savedMaven([repo('releases', 'PUBLIC', fs('10GB'))])
    const draft = savedMaven([repo('releases', 'PUBLIC', s3('abc'))])
    const [change] = diffSettings(saved, draft, SCHEMAS)

    const next = revertChange(draft.maven, saved.maven, change)

    expect(next.repositories[0].storageProvider).toEqual(fs('10GB'))
  })

  it('does not change the draft it is given', () => {
    const saved = savedMaven([repo('releases', 'PUBLIC')])
    const draft = savedMaven([repo('releases', 'PRIVATE')])
    const [change] = diffSettings(saved, draft, SCHEMAS)

    revertChange(draft.maven, saved.maven, change)

    expect(draft.maven.repositories[0].visibility).toBe('PRIVATE')
  })

  it('reverts every change one at a time until nothing differs', () => {
    const saved = {
      maven: { repositories: [repo('releases', 'PUBLIC', fs('10GB')), repo('snapshots', 'PRIVATE')] },
      frontend: { title: 'Ingot', appearance: { primaryColour: '#6750a4' } }
    }
    const draft = {
      maven: {
        repositories: [repo('releases', 'PRIVATE', s3('abc')), repo('staging', 'PUBLIC')]
      },
      frontend: { title: 'Ingot 2', appearance: { primaryColour: '#ff0000' } }
    }
    let current = JSON.parse(JSON.stringify(draft))

    for (let guard = 0; guard < 20; guard++) {
      const changes = diffSettings(saved, current, SCHEMAS)
      if (changes.length === 0) break
      const change = changes[0]
      current = { ...current, [change.domain]: revertChange(current[change.domain], saved[change.domain], change) }
    }

    expect(diffSettings(saved, current, SCHEMAS), 'every change was reverted').toEqual([])
  })
})
