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

import { describe, it, expect, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import AddMemberDialog from './AddMemberDialog.vue'

const dialog = () => document.querySelector<HTMLElement>('[role="dialog"]')!

describe('AddMemberDialog', () => {
  let wrapper: VueWrapper | undefined

  const open = async (props: Record<string, unknown> = {}) => {
    wrapper = mount(AddMemberDialog, {
      props: { modelValue: true, teamName: 'qa', ...props },
      attachTo: document.body
    })
    await flushPromises()
  }

  afterEach(() => {
    wrapper?.unmount()
    document.body.innerHTML = ''
  })

  const PRINCIPALS = [
    { name: 'alice', displayName: 'Alice Example', kind: 'USER', disabled: false },
    { name: 'ci-bot', displayName: '', kind: 'MACHINE', disabled: true }
  ]

  const candidates = () => [...dialog().querySelectorAll<HTMLElement>('[data-test="candidate"]')]

  it('builds the candidate rows from the shared list rows', async () => {
    await open({ principals: PRINCIPALS })

    expect(wrapper!.findComponent({ name: 'MdList' }).exists(), 'the candidates sit in an MdList').toBe(true)
    const items = wrapper!.findAllComponents({ name: 'MdListItem' })
    expect(items.map(item => item.attributes('data-test')), 'every candidate is an MdListItem').toEqual(['candidate', 'candidate'])
  })

  it('names the checkbox of each candidate by its own label', async () => {
    await open({ principals: PRINCIPALS })

    const row = candidates()[0]
    const input = row.querySelector<HTMLInputElement>('input[type="checkbox"]')!
    const label = row.querySelector<HTMLLabelElement>('label')!
    expect(label.htmlFor, 'the label names the checkbox by its id').toBe(input.id)
    expect(label.contains(input), 'the checkbox sits outside the text, so it takes its own clicks').toBe(false)
  })

  it('ticks a candidate when its label is clicked and enables the add button', async () => {
    await open({ principals: PRINCIPALS })

    candidates()[1].querySelector<HTMLLabelElement>('label')!.click()
    await flushPromises()

    expect(candidates()[1].querySelector<HTMLInputElement>('input')!.checked, 'the label ticks its checkbox').toBe(true)
    expect(dialog().querySelector<HTMLButtonElement>('[data-test="add-members"]')!.disabled, 'one pick enables the add button').toBe(false)
  })

  it('shows a failed load of the principals as an error tone alert', async () => {
    await open({ loadError: 'the directory is down' })

    const alert = dialog().querySelector<HTMLElement>('[role="alert"]')!
    expect(alert.textContent, 'the alert names the load failure').toContain('Could not load the principals: the directory is down')
    expect(alert.getAttribute('data-tone'), 'the load failure is an error tone alert').toBe('error')
  })

  it('shows a failed add as an error tone alert', async () => {
    await open({ failure: 'The team is locked' })

    const alert = dialog().querySelector<HTMLElement>('[data-test="add-failure"]')!
    expect(alert.textContent, 'the alert shows the failure').toContain('The team is locked')
    expect(alert.getAttribute('data-tone'), 'the failed add is an error tone alert').toBe('error')
  })
})
