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
