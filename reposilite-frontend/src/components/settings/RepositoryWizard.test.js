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

import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest'
import { ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createConfigurationStore, configurationStoreKey } from '../../store/configuration'
import RepositoryWizard from './RepositoryWizard.vue'

// The configuration module reads the session once when it is imported, so the holder starts with an empty client.
const holder = vi.hoisted(() => ({ session: { client: { value: null } } }))

// The stores are built on a fake client below; the wizard reaches the same client through the session. Toasts stay off the page.
vi.mock('../../store/session', () => ({ useSession: () => holder.session }))
vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  createInfoToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))
vi.mock('../../store/placeholders', () => ({
  default: () => ({ basePath: '/', productionUrl: 'https://repo.example.net/' }),
  frontendSecretsHidden: () => false
}))

const MAVEN_SCHEMA = {
  type: 'object',
  title: 'Maven',
  properties: {
    repositories: {
      title: 'Repositories',
      type: 'array',
      items: {
        type: 'object',
        title: 'Maven Repository',
        properties: {
          id: { type: 'string', title: 'Id' },
          visibility: { type: 'string', enum: ['PUBLIC', 'HIDDEN', 'PRIVATE'], title: 'Visibility' },
          type: { type: 'string', enum: ['HOSTED', 'PROXY', 'GROUP'], title: 'Type' },
          storageProvider: { type: 'object' },
          storagePolicy: { type: 'string', enum: ['PRIORITIZE_UPSTREAM_METADATA', 'STRICT'] },
          metadataMaxAge: { type: 'integer' },
          resolutionCacheMaxEntries: { type: 'integer' },
          proxied: {
            type: 'array',
            items: {
              type: 'object',
              properties: {
                reference: { type: 'string' },
                store: { type: 'boolean' },
                authorization: { type: 'object' }
              }
            }
          }
        },
        additionalProperties: false
      }
    }
  }
}

const clone = (value) => JSON.parse(JSON.stringify(value))

const FRONTEND_SCHEMA = { type: 'object', title: 'Frontend', properties: { title: { type: 'string' } } }
const SCHEMAS = { maven: MAVEN_SCHEMA, frontend: FRONTEND_SCHEMA }

// A server with one team (one manual and one synchronized member, one grant) and one user. Writes land in `state`.
const fakeIdentity = () => {
  const state = {
    teams: {
      developers: {
        name: 'developers',
        members: [
          { principal: 'alice', kind: 'USER', source: 'MANUAL' },
          { principal: 'bob', kind: 'USER', source: 'SYNC' }
        ],
        grants: [{ repository: null, pathPrefix: null, role: 'READ' }],
        nodes: [],
        instanceRole: false
      }
    },
    principals: [{ name: 'alice', kind: 'USER' }]
  }
  const machineTokens = { create: vi.fn(async (body) => ({ data: { token: { name: body.name }, secret: 'secret-1' } })) }
  return {
    state,
    machineTokens,
    teams: {
      list: vi.fn(async () => ({ data: Object.values(state.teams).map(team => ({ name: team.name })) })),
      get: vi.fn(async (name) => ({ data: clone(state.teams[name]), headers: { etag: '"1"' } })),
      create: vi.fn(async (body) => {
        state.teams[body.name] = { name: body.name, members: [], grants: [], nodes: [], instanceRole: false }
        return { data: clone(state.teams[body.name]) }
      }),
      setGrants: vi.fn(async (name, grants) => {
        state.teams[name].grants = clone(grants)
        return { data: clone(state.teams[name]) }
      }),
      setMembers: vi.fn(async (name, members) => {
        const team = state.teams[name]
        team.members = [
          ...team.members.filter(member => member.source === 'SYNC'),
          ...members.map(principal => ({ principal, kind: 'USER', source: 'MANUAL' }))
        ]
        return { data: clone(team) }
      })
    },
    nodes: {
      get: vi.fn(async (kind, name) => ({ data: { nodes: clone(state.teams[name].nodes) }, headers: { etag: '"1"' } })),
      set: vi.fn(async (kind, name, nodes) => {
        state.teams[name].nodes = clone(nodes)
        return { data: { nodes: clone(nodes) } }
      })
    },
    principals: {
      list: vi.fn(async () => ({ data: clone(state.principals) })),
      create: vi.fn(async (body) => {
        state.principals.push({ name: body.name, kind: body.kind })
        return { data: clone(body) }
      })
    },
    principalTokens: vi.fn(() => machineTokens)
  }
}

// A server with one existing repository. The legacy token routes are spies the wizard must never call.
const fakeClient = () => {
  const state = {
    maven: { repositories: [{ id: 'releases', visibility: 'PUBLIC' }] },
    frontend: { title: 'Ingot Repository' }
  }
  return {
    state,
    identity: fakeIdentity(),
    settings: {
      domains: vi.fn(async () => ({ data: ['maven', 'frontend'] })),
      // A copy per answer, so a test that changes the schema it was given does not change the next test's.
      schema: vi.fn(async (domain) => ({ data: clone(SCHEMAS[domain]) })),
      fetch: vi.fn(async (domain) => ({ data: clone(state[domain]) })),
      update: vi.fn(async (domain, content) => { state[domain] = clone(content) })
    },
    tokens: {
      list: vi.fn(async () => ({ data: [] })),
      createOrUpdate: vi.fn(),
      update: vi.fn()
    }
  }
}

let wrapper
let router
let client
let configuration

const $ = (selector) => document.querySelector(selector)

const setText = async (selector, value) => {
  const input = $(selector)
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

const pick = async (selector, value) => {
  const select = $(selector)
  select.value = value
  select.dispatchEvent(new Event('change'))
  await flushPromises()
}

const choose = async (selector) => {
  const input = $(selector)
  input.checked = true
  input.dispatchEvent(new Event('change'))
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

const click = async (selector) => {
  $(selector).click()
  await flushPromises()
}

const heading = () => $('#wizard-step-title')?.textContent?.trim()

const next = () => click('#wizard-next')

// Fills the basics with a valid id and walks through storage to the access step, the way a user does.
const reachAccessStep = async (id = 'team') => {
  await setText('#wizard-repository-id', id)
  await next()
  await next()
}

// The access step with the existing team chosen, and then the review.
const reachReview = async ({ id = 'team', machine = false } = {}) => {
  await reachAccessStep(id)
  await pick('#wizard-team', 'developers')
  if (machine) await choose('#wizard-machine')
  await next()
}

// Opens the wizard on the basics step, which is the second step, after the type. `onType` keeps it on the type step.
const mountWizard = async ({ open = true, onType = false } = {}) => {
  await configuration.fetchConfiguration()
  wrapper = mount(RepositoryWizard, {
    props: { modelValue: open },
    attachTo: document.body,
    global: {
      plugins: [router],
      provide: { [configurationStoreKey]: configuration }
    }
  })
  await flushPromises()
  if (open && !onType) await next()
}

beforeEach(async () => {
  client = fakeClient()
  holder.session = { client: ref(client) }
  configuration = createConfigurationStore(ref(client))
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { render: () => null } },
      { name: 'settings-entry', path: '/settings/:domain/:collection/:entry', component: { render: () => null } }
    ]
  })
  await router.push('/')
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('RepositoryWizard steps', () => {
  it('opens on the type step, with hosted chosen, and asks the basics next', async () => {
    await mountWizard({ onType: true })

    expect(heading()).toBe('Type')
    expect($('#wizard-type-hosted').checked).toBe(true)
    await next()
    expect(heading()).toBe('Basics')
  })

  it('opens the basics step with an id field and no error before any attempt', async () => {
    await mountWizard()

    expect(heading()).toBe('Basics')
    expect($('#wizard-repository-id')).not.toBeNull()
    expect($('#wizard-repository-id').getAttribute('aria-invalid')).not.toBe('true')
  })

  it('stays on the basics step while the id is empty, and says why', async () => {
    await mountWizard()

    await next()

    expect(heading()).toBe('Basics')
    expect(document.body.textContent).toContain('Enter an id.')
  })

  it('moves on to storage once the id is valid, with the default quota', async () => {
    await mountWizard()

    await setText('#wizard-repository-id', 'team')
    await next()

    expect(heading()).toBe('Storage')
    expect($('#wizard-quota').value).toBe('100%')
  })

  it('refuses an id that another repository already uses', async () => {
    await mountWizard()

    await setText('#wizard-repository-id', 'releases')
    await next()

    expect(heading()).toBe('Basics')
    expect(document.body.textContent).toContain('A repository with this id already exists.')
  })

  it('asks for a bucket once S3 is chosen, and does not move on without one', async () => {
    await mountWizard()
    await setText('#wizard-repository-id', 'team')
    await next()

    await choose('input[name="storage-kind"][value="s3"]')
    expect($('#wizard-bucket')).not.toBeNull()
    await next()

    expect(heading()).toBe('Storage')
    expect(document.body.textContent).toContain('Enter the bucket name.')
  })

  it('shows the description field only when the schema defines one', async () => {
    await mountWizard()
    expect($('#wizard-description')).toBeNull()

    configuration.schemas.value.maven.properties.repositories.items.properties.description = { type: 'string' }
    await flushPromises()

    expect($('#wizard-description')).not.toBeNull()
  })
})

describe('RepositoryWizard access step', () => {
  it('offers the teams that exist, loaded from the teams endpoint', async () => {
    await mountWizard()

    await reachAccessStep('team')

    const options = [...$('#wizard-team').options].map(option => option.value)
    expect(heading()).toBe('Access')
    expect(client.identity.teams.list).toHaveBeenCalled()
    expect(options).toEqual(['', 'developers'])
  })

  it('no longer offers a token name or a token to choose', async () => {
    await mountWizard()

    await reachAccessStep('team')

    expect($('#wizard-token-name')).toBeNull()
    expect($('#wizard-existing-token')).toBeNull()
    expect($('#wizard-read')).toBeNull()
  })

  it('does not move on without a team, and says so', async () => {
    await mountWizard()
    await reachAccessStep('team')

    await next()

    expect(heading()).toBe('Access')
    expect(document.body.textContent).toContain('Choose a team.')
  })

  it('explains the chosen role in one line', async () => {
    await mountWizard()
    await reachAccessStep('team')

    await pick('#wizard-role', 'MAINTAIN')

    expect(document.body.textContent).toContain('Read, deploy and delete artifacts.')
  })

  it('asks for the name of a new team, suggested from the repository id', async () => {
    await mountWizard()
    await reachAccessStep('team')

    await choose('input[name="access-mode"][value="new"]')

    expect($('#wizard-team')).toBeNull()
    expect($('#wizard-new-team').value).toBe('team-team')
  })

  it('refuses a new team name that exists already', async () => {
    await mountWizard()
    await reachAccessStep('team')
    await choose('input[name="access-mode"][value="new"]')

    await setText('#wizard-new-team', 'developers')
    await next()

    expect(heading()).toBe('Access')
    expect(document.body.textContent).toContain("A team named 'developers' already exists")
  })

  it('asks for the machine account only when it is wanted, with a name suggested from the repository id', async () => {
    await mountWizard()
    await reachAccessStep('team')
    expect($('#wizard-machine-name')).toBeNull()

    await choose('#wizard-machine')

    expect($('#wizard-machine-name').value).toBe('team-deploy')
  })

  it('refuses a machine account name that another principal has', async () => {
    await mountWizard()
    await reachAccessStep('team')
    await pick('#wizard-team', 'developers')
    await choose('#wizard-machine')

    await setText('#wizard-machine-name', 'alice')
    await next()

    expect(heading()).toBe('Access')
    expect(document.body.textContent).toContain("A principal named 'alice' already exists")
  })

  it('does not mention the tokens page', async () => {
    await mountWizard()

    await reachAccessStep('team')
    await choose('input[name="access-mode"][value="none"]')

    expect(document.body.textContent).not.toContain('tokens page')
  })
})

describe('RepositoryWizard review', () => {
  it('shows what will be created, and changes nothing until Create is pressed', async () => {
    await mountWizard()

    await reachReview()

    expect(heading()).toBe('Review')
    expect(document.body.textContent).toContain('Filesystem, quota 100%')
    expect(document.body.textContent).toContain('Team "developers" gets the Deploy role on team')
    expect(client.settings.update).not.toHaveBeenCalled()
    expect(client.identity.nodes.set).not.toHaveBeenCalled()
    expect(client.identity.principals.create).not.toHaveBeenCalled()
  })

  it('warns that unsaved changes elsewhere are saved together with the repository', async () => {
    await mountWizard()
    configuration.setDomainData('frontend', { title: 'Edited' })

    await reachReview()

    expect(document.body.textContent).toContain('Creating the repository saves them too')
  })
})

describe('RepositoryWizard create sequence', () => {
  it('saves the repository and gives the chosen team the chosen role on it', async () => {
    await mountWizard()
    await reachAccessStep('team')
    await pick('#wizard-team', 'developers')
    await pick('#wizard-role', 'MAINTAIN')
    await next()
    console.log('DBG', heading(), document.body.innerHTML.slice(0,300))
    console.log('DBG2', heading(), document.querySelector('#wizard-create') !== null)

    await click('#wizard-create')

    expect(client.state.maven.repositories.map(repo => repo.id)).toEqual(['releases', 'team'])
    expect(client.identity.nodes.set).toHaveBeenCalledWith(
      'teams',
      'developers',
      [
        { node: 'ingot.repository.team.read', allowed: true },
        { node: 'ingot.repository.team.deploy', allowed: true },
        { node: 'ingot.repository.team.delete', allowed: true }
      ],
      '"1"'
    )
    expect(client.identity.state.teams.developers.grants, 'the path-less grant is no longer written').toEqual([
      { repository: null, pathPrefix: null, role: 'READ' }
    ])
  })

  it('creates a new team before it is granted', async () => {
    await mountWizard()
    await reachAccessStep('team')
    await choose('input[name="access-mode"][value="new"]')
    await next()

    await click('#wizard-create')

    expect(client.identity.teams.create).toHaveBeenCalledWith({ name: 'team-team' })
    expect(client.identity.teams.create.mock.invocationCallOrder[0])
      .toBeLessThan(client.identity.nodes.set.mock.invocationCallOrder[0])
    expect(client.identity.state.teams['team-team'].nodes).toEqual([
      { node: 'ingot.repository.team.read', allowed: true },
      { node: 'ingot.repository.team.deploy', allowed: true }
    ])
  })

  it('creates the machine account, adds it to the team keeping the manual members, and creates its token', async () => {
    await mountWizard()
    await reachReview({ machine: true })

    await click('#wizard-create')

    expect(client.identity.principals.create).toHaveBeenCalledWith({ kind: 'MACHINE', name: 'team-deploy' })
    expect(client.identity.teams.setMembers).toHaveBeenCalledWith('developers', ['alice', 'team-deploy'], '"1"')
    expect(client.identity.principalTokens).toHaveBeenCalledWith('team-deploy')
    expect(client.identity.machineTokens.create).toHaveBeenCalledWith({
      name: 'team-deploy',
      expiresAt: null,
      scopes: ['READ', 'WRITE']
    })
    expect($('[data-test="secret"]').textContent.trim()).toBe('secret-1')
    expect(document.body.textContent).toContain("won't see this secret again")
  })

  it('calls the steps in the order that makes a failure clean', async () => {
    await mountWizard()
    await reachReview({ machine: true })

    await click('#wizard-create')

    const order = [
      client.settings.update,
      client.identity.nodes.set,
      client.identity.principals.create,
      client.identity.teams.setMembers,
      client.identity.machineTokens.create
    ].map(spy => spy.mock.invocationCallOrder[0])
    expect(order, 'every call happened').not.toContain(undefined)
    expect(order).toEqual([...order].sort((a, b) => a - b))
  })

  it('creates no machine account and no token when none is wanted', async () => {
    await mountWizard()
    await reachReview()

    await click('#wizard-create')

    expect(client.identity.principals.create).not.toHaveBeenCalled()
    expect(client.identity.machineTokens.create).not.toHaveBeenCalled()
    expect($('[data-test="secret"]')).toBeNull()
  })

  it('does not call the old token routes', async () => {
    await mountWizard()
    await reachReview({ machine: true })

    await click('#wizard-create')

    expect(client.tokens.createOrUpdate).not.toHaveBeenCalled()
    expect(client.tokens.update).not.toHaveBeenCalled()
  })

  it('shows the error of a failed step and what was created before it', async () => {
    client.identity.teams.setMembers.mockRejectedValueOnce(new Error('412 precondition failed'))
    await mountWizard()
    await reachReview({ machine: true })

    await click('#wizard-create')

    expect($('[data-step="member"]').dataset.state).toBe('failed')
    expect($('[data-step="token"]').dataset.state).toBe('pending')
    expect(document.body.textContent).toContain('412 precondition failed')
    const created = $('[data-step="member"] [data-test="created"]').textContent
    expect(created).toContain('Repository "team" is saved.')
    expect(created).toContain('Machine account "team-deploy" exists.')
    expect(client.identity.machineTokens.create).not.toHaveBeenCalled()
  })

  it('keeps what was created when a step fails, and retries only that step', async () => {
    client.identity.teams.setMembers.mockRejectedValueOnce(new Error('412 precondition failed'))
    await mountWizard()
    await reachReview({ machine: true })
    await click('#wizard-create')

    await click('[data-step="member"] [data-test="retry"]')

    expect(client.settings.update).toHaveBeenCalledTimes(1)
    expect(client.identity.principals.create).toHaveBeenCalledTimes(1)
    expect(client.identity.teams.setMembers).toHaveBeenCalledTimes(2)
    expect($('[data-test="secret"]').textContent.trim()).toBe('secret-1')
  })

  it('leaves the access steps untouched when the repository itself cannot be saved', async () => {
    client.settings.update.mockRejectedValueOnce(new Error('409 conflict'))
    await mountWizard()
    await reachReview()

    await click('#wizard-create')

    expect($('[data-step="settings"]').dataset.state).toBe('failed')
    expect($('[data-step="grant"]').dataset.state).toBe('pending')
    expect(client.identity.nodes.set).not.toHaveBeenCalled()
    expect(document.body.textContent).toContain('409 conflict')
  })

  it('takes an unsaved repository back out of the settings when the wizard is cancelled', async () => {
    client.settings.update.mockRejectedValueOnce(new Error('409 conflict'))
    await mountWizard()
    await reachReview()
    await click('#wizard-create')

    await click('#wizard-cancel')

    expect(configuration.configurations.value.maven.repositories.map(repo => repo.id)).toEqual(['releases'])
  })

  it('keeps a saved repository when the wizard is cancelled after an access step failed', async () => {
    client.identity.nodes.set.mockRejectedValueOnce(new Error('412 precondition failed'))
    await mountWizard()
    await reachReview()
    await click('#wizard-create')

    await click('#wizard-cancel')

    expect(configuration.configurations.value.maven.repositories.map(repo => repo.id)).toContain('team')
  })

  it('navigates to the repository only when Done is pressed, because the secret is shown there', async () => {
    await mountWizard()
    await reachReview({ machine: true })
    await click('#wizard-create')

    expect(router.currentRoute.value.fullPath).toBe('/')

    await click('#wizard-done')

    expect(router.currentRoute.value.fullPath).toBe('/settings/maven/repositories/team')
  })
})

// The option cards are the labels of their radios, so the card is found from its input.
const optionInputs = name => [...document.querySelectorAll(`input[name="${name}"]`)]
const optionCard = input => input.closest('label')
const keyDown = (element, key) => element.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }))

describe('RepositoryWizard option cards', () => {
  it('selects a visibility card when a click lands on its description text', async () => {
    await mountWizard()
    const [, , hidden] = optionInputs('visibility')

    optionCard(hidden).querySelector('[data-part="card-content"]').click()
    await flushPromises()

    expect(hidden.checked, 'the card that was clicked is chosen').toBe(true)
    expect(optionCard(hidden).className, 'the chosen card shows its selection').toContain('bg-secondary-container')
  })

  it('shows only the chosen visibility card as selected', async () => {
    await mountWizard()
    const [first, second] = optionInputs('visibility')

    optionCard(second).click()
    await flushPromises()

    expect(optionCard(second).className).toContain('bg-secondary-container')
    expect(optionCard(first).className, 'the card that is not chosen keeps the outline').not.toContain('bg-secondary-container')
  })

  it('moves the visibility selection with the down arrow', async () => {
    await mountWizard()
    const [first, second] = optionInputs('visibility')
    optionCard(first).click()
    await flushPromises()

    keyDown(first, 'ArrowDown')
    await flushPromises()

    expect(second.checked, 'the arrow key chooses the next card').toBe(true)
    expect(document.activeElement, 'the focus moves with the selection').toBe(second)
  })

  it('moves the access selection with the arrow keys and wraps around', async () => {
    await mountWizard()
    await reachAccessStep('team')
    const modes = optionInputs('access-mode')
    const last = modes[modes.length - 1]
    optionCard(modes[0]).click()
    await flushPromises()

    keyDown(modes[0], 'ArrowUp')
    await flushPromises()

    expect(last.checked, 'up from the first card wraps to the last').toBe(true)
  })
})

describe('RepositoryWizard snippets', () => {
  it('shows Maven and Gradle snippets with the real repository URL and id', async () => {
    await mountWizard()
    await reachReview({ machine: true })
    await click('#wizard-create')

    const maven = $('[data-snippet="maven-settings"]').textContent
    const distribution = $('[data-snippet="maven-distribution"]').textContent
    const gradle = $('[data-snippet="gradle"]').textContent

    expect(maven).toContain('<id>team</id>')
    expect(distribution).toContain('<url>https://repo.example.net/team</url>')
    expect(gradle).toContain('uri("https://repo.example.net/team")')
    expect(gradle).toContain('System.getenv("INGOT_DEPLOY_SECRET")')
    expect($('[data-snippet="environment"]').textContent).toContain('export INGOT_DEPLOY_USERNAME="team-deploy"')
  })
})

describe('type step', () => {
  it('offers hosted, proxy and group as three choices of one group', async () => {
    await mountWizard({ onType: true })

    const radios = ['hosted', 'proxy', 'group'].map(type => $(`#wizard-type-${type}`))
    expect(radios.map(radio => radio.name)).toEqual(['repository-type', 'repository-type', 'repository-type'])
    expect(radios.map(radio => radio.value)).toEqual(['HOSTED', 'PROXY', 'GROUP'])
  })

  it('shows storage for a hosted repository, and no remote or member fields', async () => {
    await mountWizard()
    await setText('#wizard-repository-id', 'team')
    await next()

    expect(heading()).toBe('Storage')
    expect($('#wizard-quota')).not.toBeNull()
    expect($('#wizard-remote-url-0')).toBeNull()
  })

  it('asks for the remote addresses and the cache for a proxy, and no storage', async () => {
    await mountWizard({ onType: true })
    await choose('#wizard-type-proxy')
    await next()
    await setText('#wizard-repository-id', 'central')
    await next()

    expect(heading()).toBe('Remote repositories')
    expect($('#wizard-remote-url-0')).not.toBeNull()
    expect($('#wizard-remote-login-0')).not.toBeNull()
    expect($('#wizard-remote-password-0')).not.toBeNull()
    expect($('#wizard-storage-policy')).not.toBeNull()
    expect($('#wizard-quota'), 'a proxy has no storage').toBeNull()
    expect($('#wizard-bucket')).toBeNull()
  })

  it('asks for the members from the existing repositories for a group, and no storage', async () => {
    await mountWizard({ onType: true })
    await choose('#wizard-type-group')
    await next()
    await setText('#wizard-repository-id', 'all')
    await next()

    expect(heading()).toBe('Members')
    const options = [...$('#wizard-members-add').options].map(option => option.value)
    expect(options).toEqual(['', 'releases'])
    expect($('#wizard-quota'), 'a group has no storage').toBeNull()
  })

  it('shows the access roles of a proxy without the deploy and maintain roles', async () => {
    await mountWizard({ onType: true })
    await choose('#wizard-type-proxy')
    await next()
    await setText('#wizard-repository-id', 'central')
    await next()
    await setText('#wizard-remote-url-0', 'https://repo.example.net/maven')
    await next()
    await pick('#wizard-team', 'developers')

    const roles = [...$('#wizard-role').options].map(option => option.value)
    expect(roles).toEqual(['READ', 'ADMIN'])
  })

  it('creates a proxy with its remote address and no storage', async () => {
    await mountWizard({ onType: true })
    await choose('#wizard-type-proxy')
    await next()
    await setText('#wizard-repository-id', 'central')
    await next()
    await setText('#wizard-remote-url-0', 'https://repo.example.net/maven')
    await next()
    await pick('#wizard-team', 'developers')
    await next()

    await click('#wizard-create')

    const created = client.state.maven.repositories.find(repo => repo.id === 'central')
    expect(created).toEqual({
      id: 'central',
      visibility: 'PUBLIC',
      type: 'PROXY',
      proxied: [{ reference: 'https://repo.example.net/maven', store: true }]
    })
  })

  it('creates a group that lists its members in the order they were added', async () => {
    client.state.maven.repositories.push({ id: 'snapshots', visibility: 'PUBLIC' })
    await mountWizard({ onType: true })
    await choose('#wizard-type-group')
    await next()
    await setText('#wizard-repository-id', 'all')
    await next()
    await pick('#wizard-members-add', 'snapshots')
    await click('#wizard-members-add-button')
    await pick('#wizard-members-add', 'releases')
    await click('#wizard-members-add-button')
    await next()
    await pick('#wizard-team', 'developers')
    await next()

    await click('#wizard-create')

    const created = client.state.maven.repositories.find(repo => repo.id === 'all')
    expect(created).toEqual({
      id: 'all',
      visibility: 'PUBLIC',
      type: 'GROUP',
      proxied: [{ reference: 'snapshots' }, { reference: 'releases' }]
    })
  })
})
