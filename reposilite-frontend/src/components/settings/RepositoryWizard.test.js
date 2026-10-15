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
import { createTokenStore, tokenStoreKey } from '../../store/tokens'
import RepositoryWizard from './RepositoryWizard.vue'

// The session is not needed: the stores are built on a fake client below. Toasts stay off the page.
vi.mock('../../store/session', () => ({ useSession: () => ({ client: { value: null } }) }))
vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  createInfoToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))
vi.mock('../../store/placeholders', () => ({
  default: () => ({ basePath: '/', productionUrl: 'https://repo.example.net/' })
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
          storageProvider: { type: 'object' }
        },
        additionalProperties: false
      }
    }
  }
}

const clone = (value) => JSON.parse(JSON.stringify(value))

// A server with one existing repository and one token. Writes land in `state`, so a test can read them back.
const FRONTEND_SCHEMA = { type: 'object', title: 'Frontend', properties: { title: { type: 'string' } } }
const SCHEMAS = { maven: MAVEN_SCHEMA, frontend: FRONTEND_SCHEMA }

const fakeClient = () => {
  const state = {
    maven: { repositories: [{ id: 'releases', visibility: 'PUBLIC' }] },
    frontend: { title: 'Ingot Repository' }
  }
  const tokens = [{ name: 'ci', permissions: [], routes: [] }]
  const tokenNamed = (name) => tokens.find(token => token.name === name)
  return {
    state,
    settings: {
      domains: vi.fn(async () => ({ data: ['maven', 'frontend'] })),
      schema: vi.fn(async (domain) => ({ data: SCHEMAS[domain] })),
      fetch: vi.fn(async (domain) => ({ data: clone(state[domain]) })),
      update: vi.fn(async (domain, content) => { state[domain] = clone(content) })
    },
    tokens: {
      list: vi.fn(async () => ({ data: clone(tokens) })),
      createOrUpdate: vi.fn(async (name) => {
        tokens.push({ name, permissions: [], routes: [] })
        return { data: { secret: 'secret-1' } }
      }),
      update: vi.fn(async (name, request) => {
        const token = tokenNamed(name)
        token.routes = request.routes.flatMap(route => route.permissions.map(permission => ({
          path: route.path,
          permission: { identifier: permission === 'w' ? 'route:write' : 'route:read' }
        })))
        return { data: clone(token) }
      })
    }
  }
}

let wrapper
let router
let client
let configuration
let tokenStore

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

const reachReview = async (id = 'team') => {
  await reachAccessStep(id)
  await next()
}

const mountWizard = async ({ open = true } = {}) => {
  await configuration.fetchConfiguration()
  await tokenStore.fetchTokens()
  wrapper = mount(RepositoryWizard, {
    props: { modelValue: open },
    attachTo: document.body,
    global: {
      plugins: [router],
      provide: {
        [configurationStoreKey]: configuration,
        [tokenStoreKey]: tokenStore
      }
    }
  })
  await flushPromises()
}

beforeEach(async () => {
  client = fakeClient()
  configuration = createConfigurationStore(ref(client))
  tokenStore = createTokenStore({
    getClient: () => client,
    notify: { success: vi.fn(), error: vi.fn() }
  })
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
  it('opens on the basics step, with an id field and no error before any attempt', async () => {
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

  it('suggests a deploy token named after the repository', async () => {
    await mountWizard()

    await reachAccessStep('team')

    expect(heading()).toBe('Access')
    expect($('#wizard-token-name').value).toBe('team-deploy')
  })

  it('shows the description field only when the schema defines one', async () => {
    await mountWizard()
    expect($('#wizard-description')).toBeNull()

    configuration.schemas.value.maven.properties.repositories.items.properties.description = { type: 'string' }
    await flushPromises()

    expect($('#wizard-description')).not.toBeNull()
  })
})

describe('RepositoryWizard review', () => {
  it('shows what will be created, and changes nothing until Create is pressed', async () => {
    await mountWizard()

    await reachReview('team')

    expect(heading()).toBe('Review')
    expect(document.body.textContent).toContain('Filesystem, quota 100%')
    expect(document.body.textContent).toContain('New token "team-deploy" with read and write on /team')
    expect(client.settings.update).not.toHaveBeenCalled()
    expect(client.tokens.createOrUpdate).not.toHaveBeenCalled()
  })

  it('warns that unsaved changes elsewhere are saved together with the repository', async () => {
    await mountWizard()
    configuration.setDomainData('frontend', { title: 'Edited' })

    await reachReview('team')

    expect(document.body.textContent).toContain('Creating the repository saves them too')
  })
})

describe('RepositoryWizard create sequence', () => {
  it('saves the repository, creates the token, grants the route, and shows the secret once', async () => {
    await mountWizard()
    await reachReview('team')

    await click('#wizard-create')

    expect(client.state.maven.repositories.map(repo => repo.id)).toEqual(['releases', 'team'])
    expect(client.tokens.createOrUpdate).toHaveBeenCalledWith('team-deploy', expect.objectContaining({ type: 'PERSISTENT' }))
    expect(client.tokens.update).toHaveBeenCalledWith('team-deploy', expect.objectContaining({
      routes: [{ path: '/team', permissions: ['r', 'w'] }]
    }))
    expect($('[data-test="secret"]').textContent.trim()).toBe('secret-1')
    expect(document.body.textContent).toContain("won't see this secret again")
  })

  it('grants an existing token without creating one', async () => {
    await mountWizard()
    await reachAccessStep('team')
    await choose('input[name="access-mode"][value="grant"]')
    await pick('#wizard-existing-token', 'ci')
    await next()

    await click('#wizard-create')

    expect(client.tokens.createOrUpdate).not.toHaveBeenCalled()
    expect(client.tokens.update).toHaveBeenCalledWith('ci', expect.objectContaining({
      routes: [{ path: '/team', permissions: ['r', 'w'] }]
    }))
    expect($('[data-test="secret"]')).toBeNull()
  })

  it('keeps the repository when its token fails, and retries only the token', async () => {
    client.tokens.createOrUpdate.mockRejectedValueOnce(new Error('token service unavailable'))
    await mountWizard()
    await reachReview('team')

    await click('#wizard-create')

    expect($('[data-step="settings"]').dataset.state).toBe('done')
    expect($('[data-step="token"]').dataset.state).toBe('failed')
    expect(document.body.textContent).toContain('The repository is saved, but its deploy token was not created.')
    expect(client.state.maven.repositories.map(repo => repo.id)).toContain('team')

    await click('[data-step="token"] [data-test="retry"]')

    expect(client.settings.update).toHaveBeenCalledTimes(1)
    expect(client.tokens.createOrUpdate).toHaveBeenCalledTimes(2)
    expect($('[data-test="secret"]').textContent.trim()).toBe('secret-1')
  })

  it('leaves the token steps untouched when the repository itself cannot be saved', async () => {
    client.settings.update.mockRejectedValueOnce(new Error('409 conflict'))
    await mountWizard()
    await reachReview('team')

    await click('#wizard-create')

    expect($('[data-step="settings"]').dataset.state).toBe('failed')
    expect($('[data-step="token"]').dataset.state).toBe('pending')
    expect(client.tokens.createOrUpdate).not.toHaveBeenCalled()
    expect(document.body.textContent).toContain('409 conflict')
  })

  it('takes an unsaved repository back out of the settings when the wizard is cancelled', async () => {
    client.settings.update.mockRejectedValueOnce(new Error('409 conflict'))
    await mountWizard()
    await reachReview('team')
    await click('#wizard-create')

    await click('#wizard-cancel')

    expect(configuration.configurations.value.maven.repositories.map(repo => repo.id)).toEqual(['releases'])
  })

  it('keeps a saved repository when the wizard is cancelled after the token step failed', async () => {
    client.tokens.createOrUpdate.mockRejectedValueOnce(new Error('token service unavailable'))
    await mountWizard()
    await reachReview('team')
    await click('#wizard-create')

    await click('#wizard-cancel')

    expect(configuration.configurations.value.maven.repositories.map(repo => repo.id)).toContain('team')
  })

  it('navigates to the repository only when Done is pressed, because the secret is shown there', async () => {
    await mountWizard()
    await reachReview('team')
    await click('#wizard-create')

    expect(router.currentRoute.value.fullPath).toBe('/')

    await click('#wizard-done')

    expect(router.currentRoute.value.fullPath).toBe('/settings/maven/repositories/team')
  })
})

describe('RepositoryWizard snippets', () => {
  it('shows Maven and Gradle snippets with the real repository URL and id', async () => {
    await mountWizard()
    await reachReview('team')
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
