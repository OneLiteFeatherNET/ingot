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
import SettingsImportErrors from './SettingsImportErrors.vue'

const ERRORS = [
  { domain: 'maven', label: 'Repositories > releases > Visibility', message: 'must be one of "PUBLIC", "PRIVATE"' },
  { domain: 'maven', label: 'Repositories > snapshots > Redeployment', message: 'must be boolean' },
  { domain: 'stray', label: '', message: 'Unknown settings domain' }
]

const mountErrors = (errors = ERRORS) => mount(SettingsImportErrors, {
  props: { errors },
  attachTo: document.body
})

describe('SettingsImportErrors', () => {
  it('is announced as an alert that the file was not imported', () => {
    const wrapper = mountErrors()

    const alert = wrapper.get('[role="alert"]')
    expect(alert.text()).toContain('The file was not imported')
    wrapper.unmount()
  })

  it('groups each error under its domain, with the field it is about', () => {
    const wrapper = mountErrors()

    const text = wrapper.text()
    expect(text).toContain('Repositories > releases > Visibility')
    expect(text).toContain('must be one of "PUBLIC", "PRIVATE"')
    expect(text).toContain('Repositories > snapshots > Redeployment')
    expect(wrapper.findAll('h3').map(heading => heading.text())).toEqual(['maven', 'stray'])
    wrapper.unmount()
  })

  it('shows an error about the file itself without a domain heading', () => {
    const wrapper = mountErrors([{ domain: '', label: '', message: 'The file is not valid JSON.' }])

    expect(wrapper.text()).toContain('The file is not valid JSON.')
    expect(wrapper.findAll('h3')).toHaveLength(0)
    wrapper.unmount()
  })

  it('asks to be dismissed', async () => {
    const wrapper = mountErrors()

    await wrapper.get('button').trigger('click')

    expect(wrapper.emitted('dismiss')).toEqual([[]])
    wrapper.unmount()
  })
})
