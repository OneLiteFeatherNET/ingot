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

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createConfigurationStore, configurationStoreKey } from '../../store/configuration'
import { createTokenStore, tokenStoreKey } from '../../store/tokens'
import GettingStartedCard from './GettingStartedCard.vue'

// Whether the signed-in user manages the instance is set per test; the card reads it through the session.
const session = vi.hoisted(() => ({ isManager: null }))
vi.mock('../../store/session', async () => {
  const { ref: vueRef } = await import('vue')
  session.isManager = vueRef(true)
  return { useSession: () => ({ isManager: session.isManager, client: { value: null } }) }
})
vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  createInfoToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))

const MAVEN_SCHEMA = {
  type: 'object',
  properties: {
    repositories: {
      type: 'array',
      items: {
        type: 'object',
        properties: { id: { type: 'string' }, visibility: { type: 'string' }, description: { type: 'string' } },
        additionalProperties: false
      }
    }
  }
}
const FRONTEND_SCHEMA = {
  type: 'object',
  properties: { title: { type: 'string' }, organizationLogo: { type: 'string' } }
}

const clone = (value) => JSON.parse(JSON.stringify(value))

const DEFAULT_REPOSITORIES = [
  { id: 'releases', visibility: 'PUBLIC', description: 'Release artifacts' },
  { id: 'snapshots', visibility: 'PUBLIC', description: 'Snapshot artifacts' },
  { id: 'private', visibility: 'PRIVATE', description: '' },
  { id: 'mirror', visibility: 'PUBLIC', description: 'Mirror of upstream' }
]

// A server where the default repositories exist, one public team repository and a read-only token.
const fakeClient = ({ failDomains = false, repositories = DEFAULT_REPOSITORIES } = {}) => {
  const state = {
    maven: { repositories: clone(repositories) },
    frontend: { title: 'Ingot Repository', organizationLogo: 'https://avatars.githubusercontent.com/u/103827826' }
  }
  const schemas = { maven: MAVEN_SCHEMA, frontend: FRONTEND_SCHEMA }
  return {
    settings: {
      domains: vi.fn(async () => {
        if (failDomains) throw new Error('settings are unavailable')
        return { data: ['maven', 'frontend'] }
      }),
      schema: vi.fn(async (domain) => ({ data: schemas[domain] })),
      fetch: vi.fn(async (domain) => ({ data: clone(state[domain]) })),
      update: vi.fn()
    },
    tokens: {
      list: vi.fn(async () => ({
        data: [{ name: 'reader', routes: [{ path: '/releases', permission: { identifier: 'route:read' } }] }]
      }))
    }
  }
}

let wrapper
let router
let configuration
let tokenStore

const $ = (selector) => document.querySelector(selector)

const mountCard = async ({ client = fakeClient() } = {}) => {
  configuration = createConfigurationStore(ref(client))
  tokenStore = createTokenStore({ getClient: () => client, notify: { success: vi.fn(), error: vi.fn() } })
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { render: () => null } },
      { name: 'settings', path: '/settings/:domain?', component: { render: () => null } },
      { name: 'token', path: '/tokens/:name', component: { render: () => null } },
      { path: '/settings/:domain/:collection/:entry', component: { render: () => null } }
    ]
  })
  await router.push('/')
  wrapper = mount(GettingStartedCard, {
    attachTo: document.body,
    global: {
      plugins: [router],
      provide: { [configurationStoreKey]: configuration, [tokenStoreKey]: tokenStore }
    }
  })
  await flushPromises()
}

beforeEach(() => {
  localStorage.clear()
  session.isManager.value = true
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('GettingStartedCard', () => {
  it('shows each step with whether it is done, once the setup has loaded', async () => {
    await mountCard()

    expect($('[data-step-action="repository"]')).not.toBeNull()
    expect($('li[data-state="done"] [data-step-action="repository"]')).not.toBeNull()
    expect($('li[data-state="open"] [data-step-action="branding"]')).not.toBeNull()
    expect($('li[data-state="open"] [data-step-action="deploy-token"]')).not.toBeNull()
    expect(document.body.textContent).toContain('1 of 4 steps done')
  })

  it('sends each open step to the place that completes it', async () => {
    await mountCard()

    expect($('[data-step-action="deploy-token"]').getAttribute('href')).toBe('/tokens/new')
    expect($('[data-step-action="repository"]').getAttribute('href')).toBe('/settings/maven?new=repository')
  })

  it('does not show itself to someone who does not manage the instance', async () => {
    session.isManager.value = false
    await mountCard()

    expect($('#getting-started-title')).toBeNull()
  })

  it('hides itself when dismissed, and stays hidden on the next visit', async () => {
    await mountCard()

    $('#getting-started-title').closest('section').querySelector('button').click()
    await flushPromises()
    expect($('#getting-started-title')).toBeNull()

    wrapper.unmount()
    wrapper = undefined
    await mountCard()

    expect($('#getting-started-title')).toBeNull()
  })

  it('shows an explanation instead of a checklist that can never complete when the settings fail to load', async () => {
    await mountCard({ client: fakeClient({ failDomains: true }) })

    expect($('[role="alert"]').textContent).toContain('Some steps could not be checked')
    expect($('[data-step-action]')).toBeNull()
  })

  describe('public repository review', () => {
    const reviewStep = () => $('li [data-step-action="public-review"]')?.closest('li')
    const openReview = () => $('[data-step-action="public-review"]').click()
    // The first text of each row is the repository id.
    const dialogIds = () => [...document.querySelectorAll('[role="dialog"] li')].map(row => row.querySelector('span').textContent.trim())

    it('is to do with a Review action while a public repository is not yet confirmed', async () => {
      await mountCard()

      expect(reviewStep().dataset.state).toBe('open')
      expect($('[data-step-action="public-review"]').textContent.trim()).toBe('Review')
      expect($('[data-step-action="public-review"]').getAttribute('href'), 'the review opens a dialog, it does not navigate').toBeNull()
    })

    it('lists every public repository in the review, sorted by id', async () => {
      await mountCard()
      openReview()
      await flushPromises()

      expect(dialogIds()).toEqual(['mirror', 'releases', 'snapshots'])
    })

    it('marks the step done once the admin confirms the list, and remembers it in this browser', async () => {
      await mountCard()
      openReview()
      await flushPromises()

      const looksRight = [...document.querySelectorAll('[role="dialog"] button')].find(button => button.textContent.trim() === 'Looks right')
      looksRight.click()
      await flushPromises()

      expect(reviewStep().dataset.state).toBe('done')
      expect(document.body.textContent).toContain('2 of 4 steps done')
      expect(JSON.parse(localStorage.getItem('ingot.checklist.publicReview'))).toEqual(['mirror', 'releases', 'snapshots'])
    })

    it('stays confirmed on the next visit when the public set has not changed', async () => {
      localStorage.setItem('ingot.checklist.publicReview', JSON.stringify(['mirror', 'releases', 'snapshots']))
      await mountCard()

      expect(reviewStep().dataset.state).toBe('done')
    })

    it('is to do again when a repository became public after the confirmation', async () => {
      localStorage.setItem('ingot.checklist.publicReview', JSON.stringify(['mirror', 'releases']))
      await mountCard()

      expect(reviewStep().dataset.state, 'snapshots is public but was never confirmed').toBe('open')
    })

    it('is done straight away when no repository is public', async () => {
      await mountCard({
        client: fakeClient({
          repositories: [{ id: 'releases', visibility: 'PRIVATE' }, { id: 'mirror', visibility: 'PRIVATE' }]
        })
      })

      expect(reviewStep().dataset.state).toBe('done')
    })
  })
})
