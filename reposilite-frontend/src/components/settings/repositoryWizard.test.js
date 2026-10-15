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
import {
  STEPS,
  VISIBILITY_OPTIONS,
  emptyDraft,
  optionalFieldsOf,
  tokenNameOf,
  validateBasics,
  validateStorage,
  validateAccess,
  firstInvalidStep,
  parseTags,
  repositoryEntry,
  describeVisibility,
  describeStorage,
  describeAccess,
  summaryRows,
  repositoryUrl,
  snippetsFor,
  buildActions,
  createProgress,
  runActions,
  failureNote
} from './repositoryWizard'

const NO_OPTIONAL = { description: false, tags: false }
const BOTH_OPTIONAL = { description: true, tags: true }

const draftFor = (id) => {
  const draft = emptyDraft()
  draft.basics.id = id
  return draft
}

// Every port records its name, so a test can read the order in which the sequence ran.
const recordingPorts = (overrides = {}) => {
  const calls = []
  const ports = {
    addEntry: vi.fn(() => { calls.push('addEntry') }),
    saveSettings: vi.fn(async () => { calls.push('saveSettings') }),
    createToken: vi.fn(async (name) => { calls.push(`createToken:${name}`); return 'secret-1' }),
    grantRoute: vi.fn(async (name, route) => { calls.push(`grantRoute:${name}:${route.path}`) }),
    ...overrides
  }
  return { ports, calls }
}

describe('basics step', () => {
  it('asks for an id, because a repository cannot be created without one', () => {
    expect(validateBasics({ id: '', visibility: 'PUBLIC' }, { existingIds: [] }).id).toBe('Enter an id.')
  })

  it('accepts letters, digits, dots, underscores and hyphens', () => {
    expect(validateBasics({ id: 'team.a_b-2', visibility: 'PUBLIC' }, { existingIds: [] })).toEqual({})
  })

  it('rejects characters that cannot appear in a repository address', () => {
    const { id } = validateBasics({ id: 'team/a', visibility: 'PUBLIC' }, { existingIds: [] })
    expect(id).toBe('Use only letters, digits, dots, underscores and hyphens.')
  })

  it('rejects an id that another repository already uses, ignoring case', () => {
    const { id } = validateBasics({ id: 'releases', visibility: 'PUBLIC' }, { existingIds: ['Releases'] })
    expect(id).toBe('A repository with this id already exists.')
  })

  it('requires one of the three visibilities', () => {
    const { visibility } = validateBasics({ id: 'team', visibility: 'OPEN' }, { existingIds: [] })
    expect(visibility).toBeDefined()
  })

  it('explains who can read each visibility, so the choice is made with its consequences in view', () => {
    expect(VISIBILITY_OPTIONS.map(option => option.value)).toEqual(['PUBLIC', 'HIDDEN', 'PRIVATE'])
    VISIBILITY_OPTIONS.forEach(option => expect(option.who.length).toBeGreaterThan(20))
  })

  it('says that a private repository needs a token with read access to be browsed', () => {
    expect(describeVisibility('PRIVATE')).toContain('token with read access')
  })
})

describe('storage step', () => {
  it('starts with the filesystem provider and the default quota of 100 percent', () => {
    expect(emptyDraft().storage).toMatchObject({ kind: 'fs', quota: '100%' })
  })

  it('accepts quotas in megabytes, gigabytes and percent', () => {
    ;['500MB', '10gb', '90%'].forEach(quota => {
      expect(validateStorage({ kind: 'fs', quota })).toEqual({})
    })
  })

  it('rejects a quota without a unit', () => {
    expect(validateStorage({ kind: 'fs', quota: '500' }).quota).toBeDefined()
  })

  it('requires a bucket name for S3', () => {
    expect(validateStorage({ kind: 's3', bucketName: '  ' }).bucketName).toBe('Enter the bucket name.')
  })

  it('does not require region, endpoint or keys for S3', () => {
    expect(validateStorage({ kind: 's3', bucketName: 'artifacts', region: '', endpoint: '', accessKey: '', secretKey: '' })).toEqual({})
  })
})

describe('access step', () => {
  it('suggests the deploy token name from the repository id until the name is edited', () => {
    const draft = draftFor('team')
    expect(tokenNameOf(draft)).toBe('team-deploy')
    draft.access.tokenName = 'ci'
    expect(tokenNameOf(draft)).toBe('ci')
  })

  it('needs no token when access is skipped', () => {
    const draft = draftFor('team')
    draft.access.mode = 'none'
    expect(validateAccess(draft, { tokens: [] })).toEqual({})
  })

  it('refuses an empty name for a new token', () => {
    const draft = draftFor('')
    expect(validateAccess(draft, { tokens: [] }).tokenName).toBe('Enter a token name.')
  })

  it('refuses a token name that already exists', () => {
    const draft = draftFor('team')
    expect(validateAccess(draft, { tokens: [{ name: 'team-deploy' }] }).tokenName)
      .toBe("A token named 'team-deploy' already exists")
  })

  it('refuses a token name with a colon or a slash', () => {
    const draft = draftFor('team')
    draft.access.tokenName = 'ci:deploy'
    expect(validateAccess(draft, { tokens: [] }).tokenName).toBe("Token name cannot contain ':' or '/'")
  })

  it('requires a token to be picked when an existing one is granted', () => {
    const draft = draftFor('team')
    draft.access = { mode: 'grant', tokenName: null, existingToken: '', read: true, write: true }
    expect(validateAccess(draft, { tokens: [{ name: 'ci' }] }).existingToken).toBe('Choose a token to grant access to.')
  })

  it('requires read, write or both for the route', () => {
    const draft = draftFor('team')
    draft.access.read = false
    draft.access.write = false
    expect(validateAccess(draft, { tokens: [] }).permissions).toBe('Select read, write or both.')
  })
})

describe('step order', () => {
  it('lists the steps in the order the wizard shows them', () => {
    expect(STEPS.map(step => step.key)).toEqual(['basics', 'storage', 'access', 'review'])
  })

  it('reports the first step that has a problem, so the user is sent back to it', () => {
    const draft = draftFor('')
    draft.storage = { kind: 's3', bucketName: '' }
    expect(firstInvalidStep(draft, { existingIds: [], tokens: [] })).toBe('basics')

    draft.basics.id = 'team'
    expect(firstInvalidStep(draft, { existingIds: [], tokens: [] })).toBe('storage')
  })

  it('reports no problem for a complete draft', () => {
    expect(firstInvalidStep(draftFor('team'), { existingIds: [], tokens: [] })).toBeNull()
  })
})

describe('repository entry', () => {
  it('builds a filesystem repository with its quota and nothing the schema does not define', () => {
    expect(repositoryEntry(draftFor('team'), { optional: NO_OPTIONAL })).toEqual({
      id: 'team',
      visibility: 'PUBLIC',
      storageProvider: { type: 'fs', quota: '100%' }
    })
  })

  it('includes description and tags only when the schema has them', () => {
    const draft = draftFor('team')
    draft.basics.description = 'Build artifacts'
    draft.basics.tags = 'maven, java, maven'
    expect(repositoryEntry(draft, { optional: BOTH_OPTIONAL })).toMatchObject({
      description: 'Build artifacts',
      tags: ['maven', 'java']
    })
  })

  it('keeps only the S3 settings that were filled in', () => {
    const draft = draftFor('team')
    draft.storage = { kind: 's3', bucketName: 'artifacts', region: 'eu-central-1', endpoint: '', accessKey: '', secretKey: 'secret' }
    expect(repositoryEntry(draft, { optional: NO_OPTIONAL }).storageProvider).toEqual({
      type: 's3',
      bucketName: 'artifacts',
      region: 'eu-central-1',
      secretKey: 'secret'
    })
  })

  it('finds the optional fields in the item schema, so the wizard shows only what the server accepts', () => {
    expect(optionalFieldsOf({ properties: { id: {}, tags: {} } })).toEqual({ description: false, tags: true })
  })

  it('parses tags from a comma separated list without blanks or repeats', () => {
    expect(parseTags(' a, ,b,a ')).toEqual(['a', 'b'])
  })
})

describe('review summary', () => {
  it('names the repository, who can read it, its storage and the token it will create', () => {
    const rows = Object.fromEntries(summaryRows(draftFor('team'), { optional: NO_OPTIONAL }).map(row => [row.label, row.value]))
    expect(rows.Repository).toBe('team')
    expect(rows.Visibility).toContain('Anyone can browse')
    expect(rows.Storage).toBe('Filesystem, quota 100%')
    expect(rows.Access).toBe('New token "team-deploy" with read and write on /team')
  })

  it('says that no token is created when access is skipped', () => {
    const draft = draftFor('team')
    draft.access.mode = 'none'
    expect(describeAccess(draft)).toBe('No token now. Add access later from the tokens page.')
  })

  it('lists description and tags only when the schema has them', () => {
    const labels = summaryRows(draftFor('team'), { optional: NO_OPTIONAL }).map(row => row.label)
    expect(labels).not.toContain('Description')
    expect(labels).not.toContain('Tags')
  })

  it('describes an S3 bucket by its name and region', () => {
    expect(describeStorage({ kind: 's3', bucketName: 'artifacts', region: 'eu-central-1' }))
      .toBe('S3 bucket "artifacts", region eu-central-1')
  })
})

describe('repository url', () => {
  it('joins the base address and the id once, whether or not the base ends in a slash', () => {
    expect(repositoryUrl({ base: 'https://repo.example.net/', id: 'team' })).toBe('https://repo.example.net/team')
    expect(repositoryUrl({ base: 'https://repo.example.net/ingot', id: 'team' })).toBe('https://repo.example.net/ingot/team')
  })
})

describe('snippets', () => {
  const snippets = snippetsFor({ id: 'team', url: 'https://repo.example.net/team', tokenName: 'team-deploy' })

  it('writes the Maven server with the repository id and credentials from the environment', () => {
    expect(snippets.maven.settings).toContain('<id>team</id>')
    expect(snippets.maven.settings).toContain('${env.INGOT_DEPLOY_USERNAME}')
    expect(snippets.maven.settings).toContain('${env.INGOT_DEPLOY_SECRET}')
  })

  it('points distributionManagement at the repository URL', () => {
    expect(snippets.maven.distributionManagement).toContain('<url>https://repo.example.net/team</url>')
  })

  it('writes the Gradle publishing block with the URL and credentials read from the environment', () => {
    expect(snippets.gradle).toContain('url = uri("https://repo.example.net/team")')
    expect(snippets.gradle).toContain('System.getenv("INGOT_DEPLOY_SECRET")')
  })

  it('names the environment variables to export, with the token name filled in', () => {
    expect(snippets.environment).toContain('export INGOT_DEPLOY_USERNAME="team-deploy"')
  })

  it('leaves a placeholder for the username when no token was created', () => {
    const anonymous = snippetsFor({ id: 'team', url: 'https://repo.example.net/team', tokenName: null })
    expect(anonymous.environment).toContain('export INGOT_DEPLOY_USERNAME="<token name>"')
  })
})

describe('create sequence', () => {
  it('saves the repository before any token is created', async () => {
    const { ports, calls } = recordingPorts()
    const { actions } = buildActions(draftFor('team'), { optional: NO_OPTIONAL }, ports)
    await runActions(actions, createProgress(actions))
    expect(calls.indexOf('saveSettings')).toBeLessThan(calls.indexOf('createToken:team-deploy'))
  })

  it('creates the token, then grants it read and write on the repository route', async () => {
    const { ports, calls } = recordingPorts()
    const { actions } = buildActions(draftFor('team'), { optional: NO_OPTIONAL }, ports)
    expect(await runActions(actions, createProgress(actions))).toBe(true)
    expect(calls).toEqual(['addEntry', 'saveSettings', 'createToken:team-deploy', 'grantRoute:team-deploy:/team'])
  })

  it('grants an existing token without creating one', async () => {
    const { ports, calls } = recordingPorts()
    const draft = draftFor('team')
    draft.access = { mode: 'grant', tokenName: null, existingToken: 'ci', read: true, write: false }
    const { actions } = buildActions(draft, { optional: NO_OPTIONAL }, ports)
    await runActions(actions, createProgress(actions))
    expect(calls).toEqual(['addEntry', 'saveSettings', 'grantRoute:ci:/team'])
  })

  it('stops at a failed settings save and leaves the token steps pending', async () => {
    const { ports } = recordingPorts({ saveSettings: vi.fn(async () => { throw new Error('conflict') }) })
    const { actions } = buildActions(draftFor('team'), { optional: NO_OPTIONAL }, ports)
    const progress = createProgress(actions)
    expect(await runActions(actions, progress)).toBe(false)
    expect(progress.settings).toEqual({ state: 'failed', error: 'conflict' })
    expect(progress.token.state).toBe('pending')
    expect(progress.route.state).toBe('pending')
    expect(ports.createToken).not.toHaveBeenCalled()
  })

  it('retries only the failed step once the repository is saved', async () => {
    const { ports } = recordingPorts({
      createToken: vi.fn()
        .mockRejectedValueOnce(new Error('token name taken'))
        .mockResolvedValueOnce('secret-2')
    })
    const { actions } = buildActions(draftFor('team'), { optional: NO_OPTIONAL }, ports)
    const progress = createProgress(actions)

    expect(await runActions(actions, progress)).toBe(false)
    expect(progress.settings.state).toBe('done')
    expect(progress.token.state).toBe('failed')

    expect(await runActions(actions, progress)).toBe(true)
    expect(ports.addEntry).toHaveBeenCalledTimes(1)
    expect(ports.saveSettings).toHaveBeenCalledTimes(1)
    expect(ports.createToken).toHaveBeenCalledTimes(2)
  })

  it('keeps the one-time secret of the token it created', async () => {
    const { ports } = recordingPorts()
    const { actions, result } = buildActions(draftFor('team'), { optional: NO_OPTIONAL }, ports)
    await runActions(actions, createProgress(actions))
    expect(result.secret).toBe('secret-1')
    expect(result.tokenName).toBe('team-deploy')
  })

  it('tells the user that the repository is kept when only the token step failed', () => {
    expect(failureNote('token', 'create')).toContain('is saved')
  })
})
