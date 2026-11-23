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
import { mount } from '@vue/test-utils'
import WizardStepIndicator from './WizardStepIndicator.vue'
import WizardChoiceCard from './WizardChoiceCard.vue'
import WizardFieldGroup from './WizardFieldGroup.vue'
import WizardRemoteFields from './WizardRemoteFields.vue'
import WizardReviewSummary from './WizardReviewSummary.vue'
import WizardProgressList from './WizardProgressList.vue'
import type { ProgressEntry } from '../repositoryWizard'

describe('WizardStepIndicator', () => {
  const steps = [{ key: 'type', label: 'Type' }, { key: 'basics', label: 'Basics' }, { key: 'review', label: 'Review' }]

  it('numbers the steps and labels each one', () => {
    const items = mount(WizardStepIndicator, { props: { steps, stepIndex: 0 } }).findAll('li')
    expect(items.map(item => item.text())).toEqual(['1Type', '2Basics', '3Review'])
  })

  it('marks only the current step', () => {
    const items = mount(WizardStepIndicator, { props: { steps, stepIndex: 1 } }).findAll('li')
    expect(items.map(item => item.attributes('aria-current'))).toEqual([undefined, 'step', undefined])
  })

  it('gives the grid one column per step', () => {
    const list = mount(WizardStepIndicator, { props: { steps, stepIndex: 0 } }).get('ol')
    expect(list.attributes('style')).toContain('repeat(3, minmax(0, 1fr))')
  })
})

describe('WizardChoiceCard', () => {
  const props = { modelValue: 'a', value: 'a', name: 'group', label: 'Hosted', who: 'For your own artifacts' }

  it('shows the label and who it is for', () => {
    const wrapper = mount(WizardChoiceCard, { props })
    expect(wrapper.text()).toContain('Hosted')
    expect(wrapper.text()).toContain('For your own artifacts')
  })

  it('checks the radio when the group value is its own', () => {
    const input = mount(WizardChoiceCard, { props }).get('input[type="radio"]')
    expect((input.element as HTMLInputElement).checked).toBe(true)
  })

  it('leaves the radio unchecked for another group value', () => {
    const input = mount(WizardChoiceCard, { props: { ...props, modelValue: 'b' } }).get('input[type="radio"]')
    expect((input.element as HTMLInputElement).checked).toBe(false)
  })

  it('reports its value when it is chosen', async () => {
    const wrapper = mount(WizardChoiceCard, { props: { ...props, modelValue: 'b' } })
    await wrapper.get('input[type="radio"]').setValue(true)
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['a'])
  })
})

describe('WizardFieldGroup', () => {
  it('frames its content in a fieldset with the legend', () => {
    const wrapper = mount(WizardFieldGroup, { props: { legend: 'Who can read it' }, slots: { default: '<p>inside</p>' } })
    expect(wrapper.get('fieldset legend').text()).toBe('Who can read it')
    expect(wrapper.get('fieldset').text()).toContain('inside')
  })
})

describe('WizardRemoteFields', () => {
  const props = { remote: { url: 'https://a.example', login: 'me', password: '' }, index: 1, errors: {}, removable: true }

  it('shows the three fields with the remote number', () => {
    const wrapper = mount(WizardRemoteFields, { props })
    expect((wrapper.get('#wizard-remote-url-1').element as HTMLInputElement).value).toBe('https://a.example')
    expect((wrapper.get('#wizard-remote-login-1').element as HTMLInputElement).value).toBe('me')
    expect((wrapper.get('#wizard-remote-password-1').element as HTMLInputElement).type).toBe('password')
  })

  it('reports an edited field with its name', async () => {
    const wrapper = mount(WizardRemoteFields, { props })
    await wrapper.get('#wizard-remote-login-1').setValue('other')
    expect(wrapper.emitted('edit')?.[0]).toEqual(['login', 'other'])
  })

  it('shows the error of the address', () => {
    const wrapper = mount(WizardRemoteFields, { props: { ...props, errors: { remote1: 'Enter an http address.' } } })
    expect(wrapper.text()).toContain('Enter an http address.')
  })

  it('reports the removal', async () => {
    const wrapper = mount(WizardRemoteFields, { props })
    await wrapper.get('button[aria-label="Remove address 2"]').trigger('click')
    expect(wrapper.emitted('remove')).toHaveLength(1)
  })

  it('offers no removal for the only remote', () => {
    const wrapper = mount(WizardRemoteFields, { props: { ...props, removable: false } })
    expect(wrapper.find('button').exists()).toBe(false)
  })
})

describe('WizardReviewSummary', () => {
  it('lists each row as a term and its value', () => {
    const wrapper = mount(WizardReviewSummary, { props: { rows: [{ label: 'Id', value: 'central' }, { label: 'Type', value: 'Hosted' }] } })
    expect(wrapper.findAll('dt').map(node => node.text())).toEqual(['Id', 'Type'])
    expect(wrapper.findAll('dd').map(node => node.text())).toEqual(['central', 'Hosted'])
  })
})

describe('WizardProgressList', () => {
  const actions = [{ key: 'settings', label: 'Save the repository' }, { key: 'member', label: 'Create the team' }]
  const base = (states: Record<string, ProgressEntry>) => ({ actions, progress: states, created: [] as string[], running: false })

  it('shows the state of each action', () => {
    const wrapper = mount(WizardProgressList, {
      props: base({ settings: { state: 'done', error: '' }, member: { state: 'pending', error: '' } })
    })
    const rows = wrapper.findAll('li')
    expect(rows[0].text()).toContain('Done')
    expect(rows[1].text()).toContain('Waiting')
  })

  it('shows no failure for a row that did not fail', () => {
    const wrapper = mount(WizardProgressList, {
      props: base({ settings: { state: 'running', error: '' }, member: { state: 'pending', error: '' } })
    })
    expect(wrapper.find('[data-test="retry"]').exists()).toBe(false)
  })

  it('shows the error, what exists and a retry for a failed row', async () => {
    const wrapper = mount(WizardProgressList, {
      props: { ...base({ settings: { state: 'done', error: '' }, member: { state: 'failed', error: 'boom' } }), created: ['Repository saved'] }
    })
    const failed = wrapper.get('[data-step="member"]')
    expect(failed.text()).toContain('boom')
    expect(failed.get('[data-test="created"]').text()).toContain('Repository saved')
    await failed.get('[data-test="retry"]').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })

  it('disables the retry while a run is going', () => {
    const wrapper = mount(WizardProgressList, {
      props: { ...base({ settings: { state: 'failed', error: 'x' }, member: { state: 'pending', error: '' } }), running: true }
    })
    expect(wrapper.get('[data-test="retry"]').attributes('disabled')).toBeDefined()
  })
})
