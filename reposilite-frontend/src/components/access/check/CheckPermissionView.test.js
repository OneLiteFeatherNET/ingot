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

// @vitest-environment jsdom

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ROUTES } from '../../../router/routes'
import { createErrorToast } from '../../../helpers/toast'
import CheckPermissionView from './CheckPermissionView.vue'

const holder = vi.hoisted(() => ({ session: null }))

vi.mock('../../../store/session', () => ({ useSession: () => holder.session }))

vi.mock('../../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.response?.data?.message ?? error?.message ?? `${error}`
}))

const shell = { render: () => null }

const PRINCIPALS = [
  { name: 'alice', kind: 'USER', displayName: 'Alice Example' },
  { name: 'ci-bot', kind: 'MACHINE', displayName: 'CI bot' }
]

const CATALOG = [
  { node: 'ingot.admin.users', description: 'Manage users', kind: 'STATIC' },
  { node: 'ingot.repository.releases.read', description: 'Read releases', kind: 'REPOSITORY' }
]

const NODE = 'ingot.repository.private.read'

const ALLOWED = {
  allowed: true,
  decision: { source: 'USER', subject: 'alice', node: NODE, allowed: true, depth: 0 },
  trace: [
    { source: 'USER', subject: 'alice', node: NODE, allowed: true, matched: true, reason: 'User node beats team node' },
    { source: 'TEAM', subject: 'developers', node: NODE, allowed: false, matched: false, reason: 'Not consulted: a higher layer already decided' }
  ]
}

const DENIED = {
  allowed: false,
  decision: { source: 'DEFAULT', subject: null, node: null, allowed: false, depth: null },
  trace: [
    { source: 'DEFAULT', subject: null, node: null, allowed: false, matched: true, reason: 'No node matches, denied by default' }
  ]
}

const failure = (status, message, problems) =>
  Object.assign(new Error(message), { response: { status, data: { message, problems } } })

describe('CheckPermissionView', () => {
  let wrapper
  let router
  let identity

  const mountView = async (url = '/access/check') => {
    router = createRouter({
      history: createMemoryHistory(),
      routes: ROUTES.map(route => (route.redirect ? route : { ...route, component: shell }))
    })
    await router.push(url)
    await router.isReady()
    wrapper = mount(CheckPermissionView, { attachTo: document.body, global: { plugins: [router] } })
    await flushPromises()
  }

  const type = async (field, value) => {
    await wrapper.find(`[data-test="${field}"] input`).setValue(value)
  }

  const submit = async () => {
    await wrapper.find('[data-test="check-form"]').trigger('submit')
    await flushPromises()
  }

  const text = (selector) => wrapper.find(`[data-test="${selector}"]`).text()

  beforeEach(() => {
    identity = {
      check: vi.fn(async () => ({ data: ALLOWED })),
      principals: {
        list: vi.fn(async () => ({ data: PRINCIPALS })),
        get: vi.fn(async () => ({ data: { name: 'alice', teams: [], tokens: [{ name: 'deploy' }, { name: 'docs' }] } }))
      },
      nodes: { catalog: vi.fn(async () => ({ data: { nodes: CATALOG } })) }
    }
    holder.session = { client: ref({ identity }) }
    vi.mocked(createErrorToast).mockClear()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    document.body.innerHTML = ''
  })

  describe('before a check', () => {
    it('asks for a principal and a node', async () => {
      await mountView()

      expect(text('empty')).toContain('Pick a principal and a node')
    })

    it('sends no request while the address has no question', async () => {
      await mountView()

      expect(identity.check, 'nothing is asked without a principal and a node').not.toHaveBeenCalled()
    })

    it('fills the principal from the address and waits for a node', async () => {
      await mountView('/access/check?principal=alice')

      expect(wrapper.find('[data-test="principal-field"] input').element.value).toBe('alice')
      expect(identity.check).not.toHaveBeenCalled()
    })

    it('has exactly one filled button, the Check button', async () => {
      await mountView()

      const filled = wrapper.findAll('[data-variant="filled"]')

      expect(filled.map(button => button.text())).toEqual(['Check'])
    })
  })

  describe('a deep link', () => {
    it('runs the check on load with the principal, node and token of the address', async () => {
      await mountView(`/access/check?principal=alice&node=${NODE}&token=deploy`)

      expect(identity.check).toHaveBeenCalledWith({ principal: 'alice', node: NODE, token: 'deploy' })
    })

    it('runs the check once on load', async () => {
      await mountView(`/access/check?principal=alice&node=${NODE}`)

      expect(identity.check).toHaveBeenCalledTimes(1)
    })

    it('fills the form from the address', async () => {
      await mountView(`/access/check?principal=alice&node=${NODE}&token=deploy`)

      expect(wrapper.find('[data-test="node-field"] input').element.value).toBe(NODE)
      expect(wrapper.find('select[data-test="token-select"]').element.value).toBe('deploy')
    })

    it('asks again when the address changes to another question', async () => {
      await mountView(`/access/check?principal=alice&node=${NODE}`)

      await router.push('/access/check?principal=ci-bot&node=ingot.admin.users')
      await flushPromises()

      expect(identity.check).toHaveBeenLastCalledWith({ principal: 'ci-bot', node: 'ingot.admin.users', token: undefined })
    })
  })

  describe('the verdict', () => {
    it('shows an Allowed banner for an allowed node', async () => {
      await mountView(`/access/check?principal=alice&node=${NODE}`)

      expect(text('verdict-text')).toBe('Allowed')
      expect(wrapper.find('[data-test="verdict"]').attributes('data-allowed')).toBe('true')
    })

    it('shows a Denied banner for a denied node', async () => {
      identity.check.mockResolvedValue({ data: DENIED })

      await mountView(`/access/check?principal=alice&node=${NODE}`)

      expect(text('verdict-text')).toBe('Denied')
      expect(wrapper.find('[data-test="verdict"]').attributes('data-allowed')).toBe('false')
    })

    it('names the node and the principal in the banner', async () => {
      await mountView(`/access/check?principal=alice&node=${NODE}`)

      expect(text('verdict-node')).toBe(NODE)
      expect(text('verdict-principal')).toBe('alice')
    })

    it('names the token in the banner when one was used', async () => {
      await mountView(`/access/check?principal=alice&node=${NODE}&token=deploy`)

      expect(text('verdict-token')).toBe('deploy')
    })

    it('summarises the decision with source, subject, node and depth', async () => {
      await mountView(`/access/check?principal=alice&node=${NODE}`)

      expect([text('summary-source'), text('summary-subject'), text('summary-node'), text('summary-depth')])
        .toEqual(['User', 'alice', NODE, '0'])
    })

    it('shows None for the facts a default decision does not have', async () => {
      identity.check.mockResolvedValue({ data: DENIED })

      await mountView(`/access/check?principal=alice&node=${NODE}`)

      expect([text('summary-source'), text('summary-subject'), text('summary-node'), text('summary-depth')])
        .toEqual(['Default', 'None', 'None', 'None'])
    })
  })

  describe('the derivation trace', () => {
    it('lists the steps in the order of the trace', async () => {
      await mountView(`/access/check?principal=alice&node=${NODE}`)

      const sources = wrapper.findAll('[data-test="trace-step"]').map(step => step.find('[data-test="step-source"]').text())

      expect(sources).toEqual(['User', 'Team'])
    })

    it('shows each reason of the server verbatim', async () => {
      await mountView(`/access/check?principal=alice&node=${NODE}`)

      const reasons = wrapper.findAll('[data-test="step-reason"]').map(reason => reason.text())

      expect(reasons).toEqual(['User node beats team node', 'Not consulted: a higher layer already decided'])
    })

    it('marks the step that decided as matched', async () => {
      await mountView(`/access/check?principal=alice&node=${NODE}`)

      const [deciding] = wrapper.findAll('[data-test="trace-step"]')

      expect(deciding.attributes('data-matched')).toBe('true')
      expect(deciding.find('[data-test="step-decisive"]').exists(), 'a decisive step says so in words').toBe(true)
    })

    it('mutes a step that did not decide', async () => {
      await mountView(`/access/check?principal=alice&node=${NODE}`)

      const [, muted] = wrapper.findAll('[data-test="trace-step"]')

      expect(muted.attributes('data-matched')).toBe('false')
      expect(muted.find('[data-test="step-marker"]').classes()).toContain('text-on-surface-variant')
      expect(muted.find('[data-test="step-decisive"]').exists()).toBe(false)
    })

    it('shows the subject and the matched node of a step', async () => {
      await mountView(`/access/check?principal=alice&node=${NODE}`)

      const [, team] = wrapper.findAll('[data-test="trace-step"]')

      expect(team.find('[data-test="step-subject"]').text()).toBe('developers')
      expect(team.find('[data-test="step-node"]').text()).toBe(NODE)
    })

    it('says whether a step allows or denies', async () => {
      await mountView(`/access/check?principal=alice&node=${NODE}`)

      const effects = wrapper.findAll('[data-test="step-effect"]').map(effect => effect.text())

      expect(effects).toEqual(['Allows', 'Denies'])
    })

    it('shows the default step without a node', async () => {
      identity.check.mockResolvedValue({ data: DENIED })

      await mountView(`/access/check?principal=alice&node=${NODE}`)

      expect(wrapper.find('[data-test="step-node"]').text()).toBe('No node')
      expect(wrapper.find('[data-test="step-reason"]').text()).toBe('No node matches, denied by default')
    })
  })

  describe('submitting the form', () => {
    it('writes the question into the address with replace', async () => {
      await mountView()
      const replace = vi.spyOn(router, 'replace')
      const push = vi.spyOn(router, 'push')

      await type('principal-field', 'alice')
      await type('node-field', NODE)
      await submit()

      expect(replace).toHaveBeenCalledWith({
        name: 'access-check',
        query: { principal: 'alice', node: NODE, token: undefined }
      })
      expect(push, 'a check adds no history entry').not.toHaveBeenCalled()
    })

    it('leaves the question in the address', async () => {
      await mountView()

      await type('principal-field', 'alice')
      await type('node-field', NODE)
      await submit()

      expect(router.currentRoute.value.query).toEqual({ principal: 'alice', node: NODE })
    })

    it('runs the check with the typed values', async () => {
      await mountView()

      await type('principal-field', 'alice')
      await type('node-field', NODE)
      await submit()

      expect(identity.check).toHaveBeenCalledWith({ principal: 'alice', node: NODE, token: undefined })
    })

    it('runs the check once, not again for the address it wrote', async () => {
      await mountView()

      await type('principal-field', 'alice')
      await type('node-field', NODE)
      await submit()

      expect(identity.check).toHaveBeenCalledTimes(1)
    })

    it('runs the check with the chosen token', async () => {
      await mountView()

      await type('principal-field', 'alice')
      await flushPromises()
      await type('node-field', NODE)
      await wrapper.find('select[data-test="token-select"]').setValue('docs')
      await submit()

      expect(identity.check).toHaveBeenCalledWith({ principal: 'alice', node: NODE, token: 'docs' })
    })

    it('does not run without a node', async () => {
      await mountView()

      await type('principal-field', 'alice')
      await submit()

      expect(identity.check).not.toHaveBeenCalled()
    })
  })

  describe('the pickers', () => {
    it('offers the tokens of the chosen principal', async () => {
      await mountView()

      await type('principal-field', 'alice')
      await flushPromises()

      const options = wrapper.findAll('select[data-test="token-select"] option').map(option => option.text())

      expect(identity.principals.get).toHaveBeenCalledWith('alice')
      expect(options).toEqual(['No token', 'deploy', 'docs'])
    })

    it('suggests principals while typing', async () => {
      await mountView()

      await type('principal-field', 'ci')

      const options = wrapper.findAll('[data-test="principal-field"] [data-test="option"]').map(option => option.text())

      expect(options).toEqual(['ci-botMachine account, CI bot'])
    })

    it('takes a principal from the list with the keyboard', async () => {
      await mountView()
      const input = wrapper.find('[data-test="principal-field"] input')

      await input.trigger('focus')
      await input.trigger('keydown', { key: 'ArrowDown' })
      await input.trigger('keydown', { key: 'ArrowDown' })
      await input.trigger('keydown', { key: 'Enter' })

      expect(input.element.value).toBe('ci-bot')
    })

    it('suggests nodes of the catalog', async () => {
      await mountView()

      await type('node-field', 'releases')

      const options = wrapper.findAll('[data-test="node-field"] [data-test="option"]').map(option => option.text())

      expect(options).toEqual(['ingot.repository.releases.readRead releases'])
    })

    it('takes a node from the list on click', async () => {
      await mountView()
      await type('node-field', 'releases')

      await wrapper.find('[data-test="node-field"] [data-test="option"]').trigger('click')

      expect(wrapper.find('[data-test="node-field"] input').element.value).toBe('ingot.repository.releases.read')
    })

    it('accepts a node that is not in the catalog', async () => {
      await mountView()

      await type('principal-field', 'alice')
      await type('node-field', 'ingot.repository.unknown.read')
      await submit()

      expect(identity.check).toHaveBeenCalledWith({ principal: 'alice', node: 'ingot.repository.unknown.read', token: undefined })
    })

    it('still works when the principals cannot be listed', async () => {
      identity.principals.list.mockRejectedValue(new Error('offline'))

      await mountView(`/access/check?principal=alice&node=${NODE}`)

      expect(text('verdict-text')).toBe('Allowed')
    })
  })

  describe('node grammar', () => {
    it('shows the grammar problem on the node field while typing', async () => {
      await mountView()

      await type('node-field', 'ingot.re*')

      expect(text('node-field')).toContain("a '*' must be a whole segment")
    })

    it('keeps the Check button off for an invalid node', async () => {
      await mountView()

      await type('principal-field', 'alice')
      await type('node-field', 'ingot.re*')

      expect(wrapper.find('[data-test="check"]').attributes('disabled')).toBeDefined()
    })

    it('shows no problem for a valid node', async () => {
      await mountView()

      await type('node-field', NODE)

      expect(wrapper.find('[data-test="node-field"] [aria-invalid="true"]').exists()).toBe(false)
    })
  })

  describe('failures', () => {
    it('shows the problems of a 400 on the node field', async () => {
      identity.check.mockRejectedValue(failure(400, 'Invalid node', [{ node: NODE, message: 'Unknown action read' }]))

      await mountView(`/access/check?principal=alice&node=${NODE}`)

      expect(text('node-field')).toContain('Unknown action read')
      expect(createErrorToast, 'a node problem is inline, not a toast').not.toHaveBeenCalled()
    })

    it('shows an unknown principal inline on the principal field', async () => {
      identity.check.mockRejectedValue(failure(404, 'Principal not found'))

      await mountView(`/access/check?principal=ghost&node=${NODE}`)

      expect(text('principal-field')).toContain('Principal not found')
      expect(createErrorToast).not.toHaveBeenCalled()
    })

    it('shows an unknown token inline on the token field', async () => {
      identity.check.mockRejectedValue(failure(404, 'Token not found'))

      await mountView(`/access/check?principal=alice&node=${NODE}&token=gone`)

      expect(text('token-field')).toContain('Token not found')
    })

    it('shows no verdict after a failure', async () => {
      identity.check.mockRejectedValue(failure(404, 'Principal not found'))

      await mountView(`/access/check?principal=ghost&node=${NODE}`)

      expect(wrapper.find('[data-test="verdict"]').exists()).toBe(false)
    })

    it('reports any other failure with the error toast', async () => {
      identity.check.mockRejectedValue(failure(500, 'Server exploded'))

      await mountView(`/access/check?principal=alice&node=${NODE}`)

      expect(createErrorToast).toHaveBeenCalledWith('Server exploded')
    })
  })

  describe('while the check runs', () => {
    it('shows a loading state until the answer arrives', async () => {
      let answer
      identity.check.mockReturnValue(new Promise(resolve => { answer = resolve }))

      await mountView(`/access/check?principal=alice&node=${NODE}`)

      expect(wrapper.find('[data-test="loading"]').exists()).toBe(true)
      expect(wrapper.find('[data-test="check"]').attributes('disabled'), 'no second check while one runs').toBeDefined()

      answer({ data: ALLOWED })
      await flushPromises()

      expect(wrapper.find('[data-test="loading"]').exists()).toBe(false)
      expect(text('verdict-text')).toBe('Allowed')
    })
  })
})
