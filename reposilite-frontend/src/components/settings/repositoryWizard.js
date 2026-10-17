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

/*
 * The rules of the "New repository" wizard, without any Vue. The wizard component draws the
 * steps and calls these functions; the tests call them directly.
 *
 * The first step chooses the type. A hosted repository has storage, a proxy has remote
 * repositories and cache settings, and a group has members. A proxy and a group take no uploads,
 * so their access is read (or admin) only.
 *
 * The create sequence is a list of actions with the same labels the review step shows, so the
 * user sees exactly what will run. Each action is retried on its own: a repository that was
 * saved is never saved twice, and a team or a machine account that was created is never created
 * again. Access is given through a team: the team gets a role on the repository, and an optional
 * machine account with a token joins the team.
 */

import { validateNewId } from './collections'
import { errorMessage } from '../../helpers/toast'
import { validatePrincipalName } from '../access/accessPrincipals'
import { ROLES, validateTeamName } from '../access/accessTeams'

/*
 * The type of the repository. Hosted is the default, so a wizard that is not changed creates the
 * repository it always did. The description says what can be done with each type.
 */
export const TYPE_CHOICES = [
  {
    value: 'HOSTED',
    label: 'Hosted',
    who: 'Files are uploaded to this repository and kept here.'
  },
  {
    value: 'PROXY',
    label: 'Proxy',
    who: 'Files are fetched from remote repositories. Nothing can be uploaded.'
  },
  {
    value: 'GROUP',
    label: 'Group',
    who: 'Lists other repositories of this instance, in order. It has no files of its own, and nothing can be uploaded.'
  }
]

/* The address of a remote repository: http or https, with a host and an optional path. */
export const isRemoteUrl = (text) => /^https?:\/\/[^\s/]+\S*$/i.test(String(text ?? '').trim())

/* The steps a repository of this type goes through. The type comes first, the review last. */
export const stepsFor = (type) => {
  const middle = type === 'PROXY'
    ? { key: 'upstream', label: 'Remote repositories' }
    : type === 'GROUP'
      ? { key: 'members', label: 'Members' }
      : { key: 'storage', label: 'Storage' }
  return [
    { key: 'type', label: 'Type' },
    { key: 'basics', label: 'Basics' },
    middle,
    { key: 'access', label: 'Access' },
    { key: 'review', label: 'Review' }
  ]
}

export const STEPS = stepsFor('HOSTED')

/* The roles a repository of this type can be given. A proxy or a group takes no uploads, so only read and admin. */
const REMOTE_ROLES = ['READ', 'ADMIN']
export const rolesFor = (type = 'HOSTED') => ROLES.filter(role => type === 'HOSTED' || REMOTE_ROLES.includes(role.value))

/* The password policy of a proxy: an empty choice leaves it to the server. */
export const STORAGE_POLICIES = [
  { value: '', label: 'Server default' },
  { value: 'PRIORITIZE_UPSTREAM_METADATA', label: 'Prefer upstream metadata' },
  { value: 'STRICT', label: 'Strict, cached first (offline)' }
]

// Who can read is the server's rule (RepositorySecurityProvider): publishing always needs a token.
export const VISIBILITY_OPTIONS = [
  {
    value: 'PUBLIC',
    label: 'Public',
    who: 'Anyone can browse and download, without a token.'
  },
  {
    value: 'HIDDEN',
    label: 'Hidden',
    who: 'Anyone who knows an exact file URL can download it. Browsing the listing needs a token with read access.'
  },
  {
    value: 'PRIVATE',
    label: 'Private',
    who: 'Browsing and downloading need a token with read access to this repository.'
  }
]

export const DEFAULT_QUOTA = '100%'
const QUOTA_PATTERN = /^([1-9]\d*)([KkMmGg][Bb]|%)$/
const S3_OPTIONAL_KEYS = ['region', 'endpoint', 'accessKey', 'secretKey']

export const emptyRemote = () => ({ url: '', login: '', password: '' })

export const emptyDraft = () => ({
  type: 'HOSTED',
  basics: { id: '', visibility: 'PUBLIC', description: '', tags: '' },
  storage: {
    kind: 'fs',
    quota: DEFAULT_QUOTA,
    bucketName: '',
    region: '',
    endpoint: '',
    accessKey: '',
    secretKey: ''
  },
  // The remote repositories of a proxy. `store` keeps what is fetched here, which is the cache.
  upstream: {
    remotes: [emptyRemote()],
    store: true,
    storagePolicy: '',
    metadataMaxAge: '',
    resolutionCacheMaxEntries: ''
  },
  // The ids of the repositories a group lists, in order.
  members: [],
  // `newTeam` and `machineName` stay null until the user edits them, so they keep following the repository id.
  access: {
    mode: 'existing',
    team: '',
    newTeam: null,
    role: 'DEPLOY',
    machine: false,
    machineName: null,
    scope: 'WRITE'
  }
})

/* Who gets the access: a team that exists, a team created for this repository, or nobody yet. */
export const ACCESS_MODES = [
  {
    value: 'existing',
    label: 'Give a team access',
    who: 'The team gets a role on this repository. Its other grants stay as they are.'
  },
  {
    value: 'new',
    label: 'Create a team for this repository',
    who: 'A new, empty team that gets a role on this repository. Add its members later from the Access page.'
  },
  {
    value: 'none',
    label: 'Skip for now',
    who: 'Create the repository without access. Add it later from the Access page.'
  }
]

/* What a token of the machine account may do. A scope only narrows the role of the team, so write includes read. */
export const MACHINE_SCOPES = [
  { value: 'READ', label: 'Read only', scopes: ['READ'] },
  { value: 'WRITE', label: 'Read and write', scopes: ['READ', 'WRITE'] }
]

export const scopesOf = (scope) => MACHINE_SCOPES.find(entry => entry.value === scope)?.scopes ?? ['READ']

export const roleLabelOf = (role) => ROLES.find(entry => entry.value === role)?.label ?? role

export const typeLabelOf = (type) => TYPE_CHOICES.find(choice => choice.value === type)?.label ?? ''

/* What the chosen type means, in the words the choice shows. */
export const describeType = (type) => TYPE_CHOICES.find(choice => choice.value === type)?.who ?? ''

/*
 * Choosing a type. A proxy or a group takes no uploads, so its role and its machine token are read
 * only; a role it may not have is replaced by read. Going back to hosted changes nothing else.
 */
export const chooseType = (draft, type) => {
  draft.type = type
  if (type !== 'HOSTED') {
    if (!REMOTE_ROLES.includes(draft.access.role)) draft.access.role = 'READ'
    draft.access.scope = 'READ'
  }
  return draft
}

/* Which optional repository fields the schema defines. Other agents add them; the wizard shows them only when present. */
export const optionalFieldsOf = (itemSchema) => {
  const properties = itemSchema?.properties ?? {}
  return {
    description: 'description' in properties,
    tags: 'tags' in properties
  }
}

export const newTeamNameOf = (draft) => {
  if (draft.access.newTeam !== null) return draft.access.newTeam
  return draft.basics.id ? `${draft.basics.id}-team` : ''
}

export const machineNameOf = (draft) => {
  if (draft.access.machineName !== null) return draft.access.machineName
  return draft.basics.id ? `${draft.basics.id}-deploy` : ''
}

/* The team the repository is given to, or an empty string when there is none. */
export const teamNameOf = (draft) => {
  if (draft.access.mode === 'existing') return draft.access.team
  if (draft.access.mode === 'new') return newTeamNameOf(draft).trim()
  return ''
}

const sameName = (a, b) => String(a).trim().toLowerCase() === String(b).trim().toLowerCase()

export const parseTags = (text) => [...new Set(
  String(text ?? '').split(',').map(tag => tag.trim()).filter(tag => tag !== '')
)]

export const validateBasics = (basics, { existingIds = [] } = {}) => {
  const errors = {}
  const idProblem = validateNewId(basics.id, existingIds, 'repository')
  if (idProblem) errors.id = idProblem
  if (!VISIBILITY_OPTIONS.some(option => option.value === basics.visibility)) {
    errors.visibility = 'Choose who can read this repository.'
  }
  return errors
}

export const validateStorage = (storage) => {
  const errors = {}
  if (storage.kind === 's3') {
    if (storage.bucketName.trim() === '') errors.bucketName = 'Enter the bucket name.'
  } else if (!QUOTA_PATTERN.test(storage.quota.trim())) {
    errors.quota = 'Use a size such as 500MB or 10GB, or a percentage such as 90%.'
  }
  return errors
}

/* Remote repositories: at least one address, each a remote with its own login and password, and the cache settings when given. */
export const validateUpstream = (upstream) => {
  const errors = {}
  if (upstream.remotes.length === 0) errors.remote0 = 'Add at least one remote repository.'
  upstream.remotes.forEach((remote, index) => {
    const url = remote.url.trim()
    const login = remote.login.trim()
    if (url === '') {
      errors[`remote${index}`] = 'Enter the address of a remote repository.'
    } else if (!isRemoteUrl(url)) {
      errors[`remote${index}`] = 'Use an http or https address, such as https://repo.example.net/maven.'
    }
    if (login !== '' && remote.password === '') errors[`password${index}`] = 'Enter a password for this login, or remove the login.'
    if (login === '' && remote.password !== '') errors[`login${index}`] = 'Enter a login for this password, or remove the password.'
  })
  if (String(upstream.metadataMaxAge).trim() !== '' && !/^\d+$/.test(String(upstream.metadataMaxAge).trim())) {
    errors.metadataMaxAge = 'Use a whole number of seconds, 0 or more.'
  }
  if (String(upstream.resolutionCacheMaxEntries).trim() !== '' && !/^\d+$/.test(String(upstream.resolutionCacheMaxEntries).trim())) {
    errors.resolutionCacheMaxEntries = 'Use a whole number of entries, 0 or more.'
  }
  return errors
}

/* A group lists at least one repository, and each one must exist in this instance. */
export const validateMembers = (members, existingIds = []) => {
  if (members.length === 0) return { members: 'Add at least one repository the group lists.' }
  const unknown = members.find(id => !existingIds.includes(id))
  return unknown === undefined ? {} : { members: `${unknown} is not a repository of this instance.` }
}

export const validateAccess = (draft, { teams = [], principals = [] } = {}) => {
  const { access } = draft
  if (access.mode === 'none') return {}

  const errors = {}
  if (access.mode === 'existing' && access.team === '') errors.team = 'Choose a team.'
  if (access.mode === 'new') {
    const name = newTeamNameOf(draft)
    const problem = validateTeamName(name)
    if (problem) errors.newTeam = problem
    else if (teams.some(team => sameName(team.name, name))) errors.newTeam = `A team named '${name.trim()}' already exists`
  }
  if (!rolesFor(draft.type).some(role => role.value === access.role)) {
    errors.role = ROLES.some(role => role.value === access.role)
      ? 'A proxy or a group takes no uploads. Choose Read or Admin.'
      : 'Choose a role.'
  }
  if (access.machine) {
    const name = machineNameOf(draft)
    const problem = validatePrincipalName(name)
    if (problem) errors.machineName = problem
    else if (principals.some(principal => sameName(principal.name, name))) errors.machineName = `A principal named '${name.trim()}' already exists`
  }
  return errors
}

export const validateStep = (key, draft, { existingIds = [], teams = [], principals = [] } = {}) => {
  if (key === 'type') return TYPE_CHOICES.some(choice => choice.value === draft.type) ? {} : { type: 'Choose a type.' }
  if (key === 'basics') return validateBasics(draft.basics, { existingIds })
  if (key === 'storage') return validateStorage(draft.storage)
  if (key === 'upstream') return validateUpstream(draft.upstream)
  if (key === 'members') return validateMembers(draft.members, existingIds)
  if (key === 'access') return validateAccess(draft, { teams, principals })
  return {}
}

/* The first step with a problem, or null. The steps are the ones of the chosen type; the review has no input of its own. */
export const firstInvalidStep = (draft, context) => stepsFor(draft.type)
  .slice(0, -1)
  .map(step => step.key)
  .find(key => Object.keys(validateStep(key, draft, context)).length > 0) ?? null

const storageProviderOf = (storage) => {
  if (storage.kind !== 's3') return { type: 'fs', quota: storage.quota.trim() }
  const provider = { type: 's3', bucketName: storage.bucketName.trim() }
  // Keys are kept exactly as typed; only the blank ones are left out, so the server uses its default.
  S3_OPTIONAL_KEYS.forEach(key => {
    if (storage[key].trim() !== '') provider[key] = storage[key]
  })
  return provider
}

const remoteEntry = (remote, store) => {
  const entry = { reference: remote.url.trim(), store }
  if (remote.login.trim() !== '') {
    entry.authorization = { method: 'BASIC', login: remote.login.trim(), password: remote.password }
  }
  return entry
}

/* A proxy's mirrored entries and cache settings. Blank cache settings are left out, so the server default applies. */
const proxyFieldsOf = (upstream) => {
  const fields = { proxied: upstream.remotes.map(remote => remoteEntry(remote, upstream.store)) }
  if (upstream.storagePolicy) fields.storagePolicy = upstream.storagePolicy
  if (String(upstream.metadataMaxAge).trim() !== '') fields.metadataMaxAge = Number(upstream.metadataMaxAge)
  if (String(upstream.resolutionCacheMaxEntries).trim() !== '') fields.resolutionCacheMaxEntries = Number(upstream.resolutionCacheMaxEntries)
  return fields
}

/*
 * The entry the settings store receives. The type is always written. Storage belongs to a hosted
 * repository; a proxy has its remotes and cache settings; a group has its members, as references.
 * Optional fields are written only when the schema defines them.
 */
export const repositoryEntry = (draft, { optional }) => {
  const entry = {
    id: draft.basics.id,
    visibility: draft.basics.visibility,
    type: draft.type
  }
  if (draft.type === 'PROXY') Object.assign(entry, proxyFieldsOf(draft.upstream))
  else if (draft.type === 'GROUP') entry.proxied = draft.members.map(id => ({ reference: id }))
  else entry.storageProvider = storageProviderOf(draft.storage)
  if (optional.description) entry.description = draft.basics.description.trim()
  if (optional.tags) entry.tags = parseTags(draft.basics.tags)
  return entry
}

export const describeVisibility = (value) => {
  const option = VISIBILITY_OPTIONS.find(entry => entry.value === value)
  return option ? `${option.label}. ${option.who}` : ''
}

export const describeStorage = (storage) => {
  if (storage.kind !== 's3') return `Filesystem, quota ${storage.quota.trim()}`
  const region = storage.region.trim()
  return `S3 bucket "${storage.bucketName.trim()}"${region ? `, region ${region}` : ''}`
}

const machineWords = (draft) => {
  const scope = MACHINE_SCOPES.find(entry => entry.value === draft.access.scope)?.label.toLowerCase() ?? 'read only'
  return `a machine account "${machineNameOf(draft).trim()}" with a ${scope} token`
}

export const describeAccess = (draft) => {
  const { access } = draft
  if (access.mode === 'none') return 'No access now. Add it later from the Access page.'
  const team = teamNameOf(draft)
  const kind = access.mode === 'new' ? 'New team' : 'Team'
  const base = `${kind} "${team}" gets the ${roleLabelOf(access.role)} role on ${draft.basics.id}`
  return access.machine ? `${base}, and ${machineWords(draft)} joins the team` : base
}

/* The review table: what the repository will be, in the order the wizard asked for it. */
export const summaryRows = (draft, { optional }) => {
  const rows = [
    { label: 'Repository', value: draft.basics.id },
    { label: 'Type', value: typeLabelOf(draft.type) },
    { label: 'Visibility', value: describeVisibility(draft.basics.visibility) }
  ]
  if (optional.description) rows.push({ label: 'Description', value: draft.basics.description.trim() || 'None' })
  if (optional.tags) rows.push({ label: 'Tags', value: parseTags(draft.basics.tags).join(', ') || 'None' })
  if (draft.type === 'PROXY') {
    rows.push({ label: 'Remote repositories', value: draft.upstream.remotes.map(remote => remote.url.trim()).join(', ') })
    rows.push({ label: 'Cache', value: draft.upstream.store ? 'Fetched files are kept here' : 'Fetched files are not kept' })
  } else if (draft.type === 'GROUP') {
    rows.push({ label: 'Members', value: draft.members.join(', ') })
  } else {
    rows.push({ label: 'Storage', value: describeStorage(draft.storage) })
  }
  rows.push({ label: 'Access', value: describeAccess(draft) })
  return rows
}

/* The address of a repository, with no double slash whether or not the base ends in one. */
export const repositoryUrl = ({ base, id }) => `${base.replace(/\/+$/, '')}/${id}`

/*
 * The snippets for the success step. Credentials come from environment variables, so the secret
 * is never written into a build file. The variable names are the ones the snippets read.
 */
export const snippetsFor = ({ id, url, tokenName }) => {
  const username = tokenName ?? '<token name>'
  return {
    maven: {
      settings: [
        '<settings>',
        '  <servers>',
        '    <server>',
        `      <id>${id}</id>`,
        '      <username>${env.INGOT_DEPLOY_USERNAME}</username>',
        '      <password>${env.INGOT_DEPLOY_SECRET}</password>',
        '    </server>',
        '  </servers>',
        '</settings>'
      ].join('\n'),
      distributionManagement: [
        '<distributionManagement>',
        '  <repository>',
        `    <id>${id}</id>`,
        `    <url>${url}</url>`,
        '  </repository>',
        '  <snapshotRepository>',
        `    <id>${id}</id>`,
        `    <url>${url}</url>`,
        '  </snapshotRepository>',
        '</distributionManagement>'
      ].join('\n')
    },
    gradle: [
      'publishing {',
      '    repositories {',
      '        maven {',
      `            name = "${id}"`,
      `            url = uri("${url}")`,
      '            credentials {',
      '                username = System.getenv("INGOT_DEPLOY_USERNAME")',
      '                password = System.getenv("INGOT_DEPLOY_SECRET")',
      '            }',
      '        }',
      '    }',
      '}'
    ].join('\n'),
    environment: [
      `export INGOT_DEPLOY_USERNAME="${username}"`,
      'export INGOT_DEPLOY_SECRET="<the secret shown above>"'
    ].join('\n')
  }
}

/*
 * The create sequence. `ports` is the only thing that touches the stores and the server:
 *   addEntry(entry)                      adds the repository to the draft settings
 *   saveSettings()                       saves the settings, rejects when they are not saved
 *   createTeam(name)                     creates an empty team
 *   grantTeam(team, {repository, role})  appends a grant to the team, keeping its other grants
 *   createMachine(name)                  creates a machine account
 *   addMachineToTeam(team, machine)      adds the machine account to the manual members of the team
 *   createMachineToken(machine, scopes)  creates its token, resolves with the secret
 * The order is the order of a clean failure: whatever fails, everything before it exists and
 * nothing after it was started. `result` collects what the success step shows.
 */
export const buildActions = (draft, { optional }, ports) => {
  const id = draft.basics.id
  const { access } = draft
  const team = teamNameOf(draft)
  const machine = access.mode !== 'none' && access.machine ? machineNameOf(draft).trim() : null
  const entry = repositoryEntry(draft, { optional })
  const result = {
    secret: null,
    tokenName: machine,
    machineName: machine,
    teamName: access.mode === 'none' ? null : team,
    role: access.mode === 'none' ? null : access.role
  }

  let entryAdded = false
  const actions = [{
    key: 'settings',
    label: `Save repository "${id}"`,
    created: `Repository "${id}" is saved.`,
    run: async () => {
      // Added once: a retry after a failed save must not add the same repository a second time.
      if (!entryAdded) {
        ports.addEntry(entry)
        entryAdded = true
      }
      await ports.saveSettings()
    }
  }]

  if (access.mode === 'none') return { actions, result }

  if (access.mode === 'new') {
    actions.push({
      key: 'team',
      label: `Create team "${team}"`,
      created: `Team "${team}" exists.`,
      run: () => ports.createTeam(team)
    })
  }

  actions.push({
    key: 'grant',
    label: `Give team "${team}" the ${roleLabelOf(access.role)} role on ${id}`,
    created: `Team "${team}" has the ${roleLabelOf(access.role)} role on ${id}.`,
    run: () => ports.grantTeam(team, { repository: id, role: access.role })
  })

  if (machine) {
    actions.push({
      key: 'machine',
      label: `Create machine account "${machine}"`,
      created: `Machine account "${machine}" exists.`,
      run: () => ports.createMachine(machine)
    })
    actions.push({
      key: 'member',
      label: `Add "${machine}" to team "${team}"`,
      created: `"${machine}" is a member of team "${team}".`,
      run: () => ports.addMachineToTeam(team, machine)
    })
    actions.push({
      key: 'token',
      label: `Create a token for "${machine}"`,
      created: `The token of "${machine}" exists.`,
      run: async () => {
        result.secret = await ports.createMachineToken(machine, scopesOf(access.scope))
      }
    })
  }

  return { actions, result }
}

export const createProgress = (actions) => Object.fromEntries(
  actions.map(action => [action.key, { state: 'pending', error: '' }])
)

/*
 * Runs the actions in order and stops at the first failure. Actions already done are skipped, so
 * calling this again after a failure retries from the failed step onwards.
 */
export const runActions = async (actions, progress) => {
  for (const action of actions) {
    if (progress[action.key].state === 'done') continue
    progress[action.key] = { state: 'running', error: '' }
    try {
      await action.run()
      progress[action.key] = { state: 'done', error: '' }
    } catch (error) {
      progress[action.key] = { state: 'failed', error: errorMessage(error) }
      return false
    }
  }
  return true
}

const FAILURE_NOTES = {
  settings: 'The repository was not saved. Nothing else has been changed.',
  team: 'The repository is saved, but the team was not created. Retry creates only the team.',
  grant: 'The repository is saved, but the team was not given access to it. Retry adds only the grant.',
  machine: 'The team has its access, but the machine account was not created. Retry creates only the machine account.',
  member: 'The machine account exists, but it is not in the team yet, so it has no rights. Retry adds only the membership.',
  token: 'The machine account is in the team, but its token was not created. Retry creates only the token.'
}

/* What a failed step means for the repository, so the user knows what the retry will and will not touch. */
export const failureNote = (key) => FAILURE_NOTES[key] ?? 'This step failed. Retry runs only this step.'

/* What exists after the steps that are done, one sentence each, so a failure shows what was already created. */
export const createdSoFar = (actions, progress) => actions
  .filter(action => progress[action.key]?.state === 'done')
  .map(action => action.created)
