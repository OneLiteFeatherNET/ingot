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
 * The create sequence is a list of actions with the same labels the review step shows, so the
 * user sees exactly what will run. Each action is retried on its own: a repository that was
 * saved is never saved twice, and a token that was created is never created again.
 */

import { validateNewId } from './collections'
import { validateTokenName } from '../../store/tokens'
import { errorMessage } from '../../helpers/toast'

export const STEPS = [
  { key: 'basics', label: 'Basics' },
  { key: 'storage', label: 'Storage' },
  { key: 'access', label: 'Access' },
  { key: 'review', label: 'Review' }
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

export const emptyDraft = () => ({
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
  // `tokenName` stays null until the user edits it, so it keeps following the repository id.
  access: { mode: 'create', tokenName: null, existingToken: '', read: true, write: true }
})

/* Which optional repository fields the schema defines. Other agents add them; the wizard shows them only when present. */
export const optionalFieldsOf = (itemSchema) => {
  const properties = itemSchema?.properties ?? {}
  return {
    description: 'description' in properties,
    tags: 'tags' in properties
  }
}

export const tokenNameOf = (draft) => {
  if (draft.access.tokenName !== null) return draft.access.tokenName
  return draft.basics.id ? `${draft.basics.id}-deploy` : ''
}

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

export const validateAccess = (draft, { tokens = [] } = {}) => {
  const { access } = draft
  if (access.mode === 'none') return {}

  const errors = {}
  if (access.mode === 'create') {
    const name = tokenNameOf(draft)
    const problem = validateTokenName(name, tokens)
    if (problem === 'empty') errors.tokenName = 'Enter a token name.'
    if (problem === 'invalid') errors.tokenName = "Token name cannot contain ':' or '/'"
    if (problem === 'duplicate') errors.tokenName = `A token named '${name.trim()}' already exists`
  }
  if (access.mode === 'grant' && access.existingToken === '') {
    errors.existingToken = 'Choose a token to grant access to.'
  }
  if (!access.read && !access.write) errors.permissions = 'Select read, write or both.'
  return errors
}

export const validateStep = (key, draft, { existingIds = [], tokens = [] } = {}) => {
  if (key === 'basics') return validateBasics(draft.basics, { existingIds })
  if (key === 'storage') return validateStorage(draft.storage)
  if (key === 'access') return validateAccess(draft, { tokens })
  return {}
}

/* The first of the three input steps with a problem, or null. The review step has no input of its own. */
export const firstInvalidStep = (draft, context) => STEPS
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

/* The entry the settings store receives. Optional fields are written only when the schema defines them. */
export const repositoryEntry = (draft, { optional }) => {
  const entry = {
    id: draft.basics.id,
    visibility: draft.basics.visibility,
    storageProvider: storageProviderOf(draft.storage)
  }
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

const permissionWords = ({ read, write }) => {
  if (read && write) return 'read and write'
  if (read) return 'read'
  if (write) return 'write'
  return 'no access'
}

export const describeAccess = (draft) => {
  const { access } = draft
  const route = `/${draft.basics.id}`
  if (access.mode === 'create') return `New token "${tokenNameOf(draft)}" with ${permissionWords(access)} on ${route}`
  if (access.mode === 'grant') return `Existing token "${access.existingToken}" with ${permissionWords(access)} on ${route}`
  return 'No token now. Add access later from the tokens page.'
}

/* The review table: what the repository will be, in the order the wizard asked for it. */
export const summaryRows = (draft, { optional }) => {
  const rows = [
    { label: 'Repository', value: draft.basics.id },
    { label: 'Visibility', value: describeVisibility(draft.basics.visibility) }
  ]
  if (optional.description) rows.push({ label: 'Description', value: draft.basics.description.trim() || 'None' })
  if (optional.tags) rows.push({ label: 'Tags', value: parseTags(draft.basics.tags).join(', ') || 'None' })
  rows.push({ label: 'Storage', value: describeStorage(draft.storage) })
  rows.push({ label: 'Access', value: describeAccess(draft) })
  return rows
}

export const routeOf = (draft) => ({
  path: `/${draft.basics.id}`,
  read: draft.access.read,
  write: draft.access.write
})

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
 * The create sequence. `ports` is the only thing that touches the stores:
 *   addEntry(entry)            adds the repository to the draft settings
 *   saveSettings()             saves the settings, rejects when they are not saved
 *   createToken(name)          creates a persistent token, resolves with its secret
 *   grantRoute(name, route)    adds the route to a token, rejects when it is not saved
 * `result` collects what the success step shows.
 */
export const buildActions = (draft, { optional }, ports) => {
  const id = draft.basics.id
  const mode = draft.access.mode
  const tokenName = mode === 'create' ? tokenNameOf(draft) : draft.access.existingToken
  const route = routeOf(draft)
  const entry = repositoryEntry(draft, { optional })
  const result = { secret: null, tokenName: mode === 'none' ? null : tokenName }

  let entryAdded = false
  const actions = [{
    key: 'settings',
    label: `Save repository "${id}"`,
    run: async () => {
      // Added once: a retry after a failed save must not add the same repository a second time.
      if (!entryAdded) {
        ports.addEntry(entry)
        entryAdded = true
      }
      await ports.saveSettings()
    }
  }]

  if (mode === 'create') {
    actions.push({
      key: 'token',
      label: `Create token "${tokenName}"`,
      run: async () => {
        result.secret = await ports.createToken(tokenName)
      }
    })
  }

  if (mode === 'create' || mode === 'grant') {
    actions.push({
      key: 'route',
      label: `Give "${tokenName}" ${permissionWords(draft.access)} on /${id}`,
      run: () => ports.grantRoute(tokenName, route)
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

/* What a failed step means for the repository, so the user knows what the retry will and will not touch. */
export const failureNote = (key, mode) => {
  if (key === 'settings') return 'The repository was not saved. Nothing else has been changed.'
  if (key === 'token') return 'The repository is saved, but its deploy token was not created. Retry creates only the token.'
  if (mode === 'create') return 'The repository and its token exist, but the route was not added. Retry adds only the route.'
  return 'The repository is saved, but the token was not given access to it. Retry adds only the route.'
}
