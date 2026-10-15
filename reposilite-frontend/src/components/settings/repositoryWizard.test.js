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
  teamNameOf,
  machineNameOf,
  scopesOf,
  createdSoFar,
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
    createTeam: vi.fn(async (name) => { calls.push(`createTeam:${name}`) }),
    grantTeam: vi.fn(async (team, grant) => { calls.push(`grantTeam:${team}:${grant.repository}:${grant.role}`) }),
    createMachine: vi.fn(async (name) => { calls.push(`createMachine:${name}`) }),
    addMachineToTeam: vi.fn(async (team, machine) => { calls.push(`addMachineToTeam:${team}:${machine}`) }),
    createMachineToken: vi.fn(async (machine, scopes) => { calls.push(`createMachineToken:${machine}:${scopes.join('+')}`); return 'secret-1' }),
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
  const TEAMS = [{ name: 'developers' }]
  const PRINCIPALS = [{ name: 'alice' }, { name: 'ci-bot' }]

  const accessDraft = (access = {}) => {
    const draft = draftFor('team')
    draft.access = { ...draft.access, ...access }
    return draft
  }

  it('starts by giving an existing team the deploy role, with no machine account', () => {
    expect(emptyDraft().access).toMatchObject({ mode: 'existing', team: '', role: 'DEPLOY', machine: false })
  })

  it('needs nothing when access is skipped', () => {
    expect(validateAccess(accessDraft({ mode: 'none' }), { teams: [], principals: [] })).toEqual({})
  })

  it('asks for a team when an existing one is to get the access', () => {
    expect(validateAccess(accessDraft(), { teams: TEAMS, principals: [] }).team).toBe('Choose a team.')
  })

  it('accepts a chosen existing team', () => {
    expect(validateAccess(accessDraft({ team: 'developers' }), { teams: TEAMS, principals: [] })).toEqual({})
  })

  it('suggests the name of a new team from the repository id until it is edited', () => {
    const draft = accessDraft({ mode: 'new' })
    expect(teamNameOf(draft)).toBe('team-team')
    draft.access.newTeam = 'builders'
    expect(teamNameOf(draft)).toBe('builders')
  })

  it('refuses an empty name for a new team', () => {
    expect(validateAccess(accessDraft({ mode: 'new', newTeam: ' ' }), { teams: [], principals: [] }).newTeam).toBe('A name is required')
  })

  it('refuses the reserved machine prefix for a new team', () => {
    expect(validateAccess(accessDraft({ mode: 'new', newTeam: 'machine:ci' }), { teams: [], principals: [] }).newTeam)
      .toBe("Names starting with 'machine:' are reserved")
  })

  it('refuses a new team name that exists already, ignoring case', () => {
    expect(validateAccess(accessDraft({ mode: 'new', newTeam: 'Developers' }), { teams: TEAMS, principals: [] }).newTeam)
      .toBe("A team named 'Developers' already exists")
  })

  it('suggests the name of the machine account from the repository id until it is edited', () => {
    const draft = accessDraft({ machine: true })
    expect(machineNameOf(draft)).toBe('team-deploy')
    draft.access.machineName = 'builder'
    expect(machineNameOf(draft)).toBe('builder')
  })

  it('refuses a machine account name with a colon or a slash', () => {
    expect(validateAccess(accessDraft({ team: 'developers', machine: true, machineName: 'ci:deploy' }), { teams: TEAMS, principals: [] }).machineName)
      .toBe("A name must not contain ':' or '/'")
  })

  it('refuses a machine account name that another principal has', () => {
    expect(validateAccess(accessDraft({ team: 'developers', machine: true, machineName: 'CI-Bot' }), { teams: TEAMS, principals: PRINCIPALS }).machineName)
      .toBe("A principal named 'CI-Bot' already exists")
  })

  it('does not check the machine account name when no machine account is wanted', () => {
    expect(validateAccess(accessDraft({ team: 'developers', machine: false, machineName: 'ci-bot' }), { teams: TEAMS, principals: PRINCIPALS })).toEqual({})
  })

  it('maps the chosen scope to the backend scopes, where write includes read', () => {
    expect(scopesOf('READ')).toEqual(['READ'])
    expect(scopesOf('WRITE')).toEqual(['READ', 'WRITE'])
  })
})

describe('step order', () => {
  const context = { existingIds: [], teams: [{ name: 'developers' }], principals: [] }

  it('lists the steps in the order the wizard shows them', () => {
    expect(STEPS.map(step => step.key)).toEqual(['basics', 'storage', 'access', 'review'])
  })

  it('reports the first step that has a problem, so the user is sent back to it', () => {
    const draft = draftFor('')
    draft.storage = { kind: 's3', bucketName: '' }
    draft.access.team = 'developers'
    expect(firstInvalidStep(draft, context)).toBe('basics')

    draft.basics.id = 'team'
    expect(firstInvalidStep(draft, context)).toBe('storage')
  })

  it('reports no problem for a complete draft', () => {
    const draft = draftFor('team')
    draft.access.team = 'developers'
    expect(firstInvalidStep(draft, context)).toBeNull()
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
  const draftWithTeam = () => {
    const draft = draftFor('team')
    draft.access.team = 'developers'
    return draft
  }

  it('names the repository, who can read it, its storage and the team that gets the access', () => {
    const rows = Object.fromEntries(summaryRows(draftWithTeam(), { optional: NO_OPTIONAL }).map(row => [row.label, row.value]))
    expect(rows.Repository).toBe('team')
    expect(rows.Visibility).toContain('Anyone can browse')
    expect(rows.Storage).toBe('Filesystem, quota 100%')
    expect(rows.Access).toBe('Team "developers" gets the Deploy role on team')
  })

  it('says that the team is new when one is created for the repository', () => {
    const draft = draftFor('team')
    draft.access.mode = 'new'
    expect(describeAccess(draft)).toBe('New team "team-team" gets the Deploy role on team')
  })

  it('adds the machine account and its token to the sentence', () => {
    const draft = draftWithTeam()
    draft.access.machine = true
    expect(describeAccess(draft)).toBe(
      'Team "developers" gets the Deploy role on team, and a machine account "team-deploy" with a read and write token joins the team'
    )
  })

  it('says that no access is given when it is skipped', () => {
    const draft = draftFor('team')
    draft.access.mode = 'none'
    expect(describeAccess(draft)).toBe('No access now. Add it later from the Access page.')
  })

  it('does not point to the tokens page, which no longer creates routes', () => {
    const draft = draftFor('team')
    draft.access.mode = 'none'
    expect(describeAccess(draft)).not.toContain('tokens page')
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
  const draftFor2 = ({ mode = 'existing', machine = false, role = 'DEPLOY', scope = 'WRITE' } = {}) => {
    const draft = draftFor('team')
    draft.access = { ...draft.access, mode, team: mode === 'existing' ? 'developers' : '', role, machine, scope }
    return draft
  }

  it('saves the repository before anything is given to a team', async () => {
    const { ports, calls } = recordingPorts()
    const { actions } = buildActions(draftFor2(), { optional: NO_OPTIONAL }, ports)
    await runActions(actions, createProgress(actions))
    expect(calls.indexOf('saveSettings')).toBeLessThan(calls.indexOf('grantTeam:developers:team:DEPLOY'))
  })

  it('gives an existing team the chosen role on the repository, and creates no team', async () => {
    const { ports, calls } = recordingPorts()
    const { actions } = buildActions(draftFor2({ role: 'MAINTAIN' }), { optional: NO_OPTIONAL }, ports)
    expect(await runActions(actions, createProgress(actions))).toBe(true)
    expect(calls).toEqual(['addEntry', 'saveSettings', 'grantTeam:developers:team:MAINTAIN'])
  })

  it('creates a new team first, then grants it', async () => {
    const { ports, calls } = recordingPorts()
    const { actions } = buildActions(draftFor2({ mode: 'new' }), { optional: NO_OPTIONAL }, ports)
    await runActions(actions, createProgress(actions))
    expect(calls).toEqual(['addEntry', 'saveSettings', 'createTeam:team-team', 'grantTeam:team-team:team:DEPLOY'])
  })

  it('creates the machine account, adds it to the team, then creates its token, in that order', async () => {
    const { ports, calls } = recordingPorts()
    const { actions } = buildActions(draftFor2({ mode: 'new', machine: true }), { optional: NO_OPTIONAL }, ports)
    await runActions(actions, createProgress(actions))
    expect(calls).toEqual([
      'addEntry',
      'saveSettings',
      'createTeam:team-team',
      'grantTeam:team-team:team:DEPLOY',
      'createMachine:team-deploy',
      'addMachineToTeam:team-team:team-deploy',
      'createMachineToken:team-deploy:READ+WRITE'
    ])
  })

  it('narrows the token to read when read only is chosen', async () => {
    const { ports, calls } = recordingPorts()
    const { actions } = buildActions(draftFor2({ machine: true, scope: 'READ' }), { optional: NO_OPTIONAL }, ports)
    await runActions(actions, createProgress(actions))
    expect(calls).toContain('createMachineToken:team-deploy:READ')
  })

  it('does nothing but save the repository when access is skipped', async () => {
    const { ports, calls } = recordingPorts()
    const draft = draftFor2({ mode: 'none', machine: true })
    const { actions } = buildActions(draft, { optional: NO_OPTIONAL }, ports)
    await runActions(actions, createProgress(actions))
    expect(calls).toEqual(['addEntry', 'saveSettings'])
  })

  it('stops at a failed settings save and leaves the access steps pending', async () => {
    const { ports } = recordingPorts({ saveSettings: vi.fn(async () => { throw new Error('conflict') }) })
    const { actions } = buildActions(draftFor2({ machine: true }), { optional: NO_OPTIONAL }, ports)
    const progress = createProgress(actions)
    expect(await runActions(actions, progress)).toBe(false)
    expect(progress.settings).toEqual({ state: 'failed', error: 'conflict' })
    expect(progress.grant.state).toBe('pending')
    expect(progress.token.state).toBe('pending')
    expect(ports.grantTeam).not.toHaveBeenCalled()
  })

  it('stops at a failed grant, does not create the machine account, and keeps what was created', async () => {
    const { ports } = recordingPorts({ grantTeam: vi.fn(async () => { throw new Error('412 precondition failed') }) })
    const { actions } = buildActions(draftFor2({ mode: 'new', machine: true }), { optional: NO_OPTIONAL }, ports)
    const progress = createProgress(actions)

    expect(await runActions(actions, progress)).toBe(false)

    expect(progress.grant).toEqual({ state: 'failed', error: '412 precondition failed' })
    expect(progress.machine.state).toBe('pending')
    expect(ports.createMachine).not.toHaveBeenCalled()
    expect(createdSoFar(actions, progress)).toEqual(['Repository "team" is saved.', 'Team "team-team" exists.'])
  })

  it('retries only the failed step once the earlier ones are done', async () => {
    const { ports } = recordingPorts({
      addMachineToTeam: vi.fn()
        .mockRejectedValueOnce(new Error('412 precondition failed'))
        .mockResolvedValueOnce(undefined)
    })
    const { actions } = buildActions(draftFor2({ mode: 'new', machine: true }), { optional: NO_OPTIONAL }, ports)
    const progress = createProgress(actions)

    expect(await runActions(actions, progress)).toBe(false)
    expect(progress.member.state).toBe('failed')

    expect(await runActions(actions, progress)).toBe(true)
    expect(ports.saveSettings).toHaveBeenCalledTimes(1)
    expect(ports.createTeam).toHaveBeenCalledTimes(1)
    expect(ports.createMachine).toHaveBeenCalledTimes(1)
    expect(ports.addMachineToTeam).toHaveBeenCalledTimes(2)
    expect(ports.createMachineToken).toHaveBeenCalledTimes(1)
  })

  it('keeps the one-time secret of the token it created', async () => {
    const { ports } = recordingPorts()
    const { actions, result } = buildActions(draftFor2({ machine: true }), { optional: NO_OPTIONAL }, ports)
    await runActions(actions, createProgress(actions))
    expect(result.secret).toBe('secret-1')
    expect(result.tokenName).toBe('team-deploy')
  })

  it('tells the user that the machine account is not in the team yet when only the membership failed', () => {
    expect(failureNote('member')).toContain('not in the team yet')
  })

  it('tells the user that the repository is kept when only the grant failed', () => {
    expect(failureNote('grant')).toContain('is saved')
  })
})
