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
import { flushPromises } from '@vue/test-utils'
import DiscoverStep from './DiscoverStep.vue'
import { createWizardStore } from '../../store/migration/wizardStore'
import { fakeApi, mountWith, cleanup } from './testing'

const URL_VALUE = 'https://nexus.example.test'
const USER = 'importer'
const PASSWORD = 'literal-pw-7731'

afterEach(cleanup)

// A wizard in which the connection test has passed. The password is held when `remembered` is true.
const connectedWizard = ({ remembered = true, mode = 'literal' } = {}) => {
  const wizard = createWizardStore()
  wizard.setConnection({ url: URL_VALUE, user: USER, passwordMode: mode, reference: mode === 'reference' ? '${env:NEXUS_PASSWORD}' : undefined, version: '3.70.1' })
  if (remembered && mode === 'literal') wizard.passwords.remember(PASSWORD)
  return wizard
}

describe('the Discover step', () => {
  it('asks for the connection test first when no connection passed in this visit', async () => {
    const { wrapper, api } = await mountWith(DiscoverStep, { at: '/settings/migration/discover' })

    expect(wrapper.text()).toMatch(/test the connection/i)
    expect(api.discover).not.toHaveBeenCalled()
  })

  it('sends the connection with the remembered password in the body of the discovery', async () => {
    const wizard = connectedWizard()
    const { wrapper, api } = await mountWith(DiscoverStep, { wizard, at: '/settings/migration/discover' })

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(api.discover).toHaveBeenCalledWith({ url: URL_VALUE, user: USER, password: PASSWORD })
  })

  it('asks for the password again after a reload, before it sends the request', async () => {
    const wizard = connectedWizard({ remembered: false })
    const { wrapper, api } = await mountWith(DiscoverStep, { wizard, at: '/settings/migration/discover' })

    expect(wrapper.find('#migration-discover-password').exists(), 'the password field is shown').toBe(true)

    await wrapper.find('#migration-discover-password').setValue(PASSWORD)
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(api.discover).toHaveBeenCalledWith({ url: URL_VALUE, user: USER, password: PASSWORD })
  })

  it('sends a secret reference as the password without asking for one', async () => {
    const wizard = connectedWizard({ mode: 'reference' })
    const { wrapper, api } = await mountWith(DiscoverStep, { wizard, at: '/settings/migration/discover' })

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('#migration-discover-password').exists()).toBe(false)
    expect(api.discover).toHaveBeenCalledWith({ url: URL_VALUE, user: USER, password: '${env:NEXUS_PASSWORD}' })
  })

  it('shows each repository with its format, type, component count and size', async () => {
    const wizard = connectedWizard()
    const { wrapper } = await mountWith(DiscoverStep, { wizard, at: '/settings/migration/discover' })

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const table = wrapper.find('table[data-test="repositories"]')
    expect(table.find('caption').exists(), 'the table has a caption').toBe(true)
    const row = table.findAll('tbody tr').find(entry => entry.text().includes('releases'))
    expect(row.text()).toContain('maven2')
    expect(row.text()).toContain('hosted')
    expect(row.text()).toContain('120')
  })

  it('lists an unsupported format as excluded', async () => {
    const wizard = connectedWizard()
    const { wrapper } = await mountWith(DiscoverStep, { wizard, at: '/settings/migration/discover' })

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const row = wrapper.find('table[data-test="repositories"]').findAll('tbody tr').find(entry => entry.text().includes('npm-proxy'))
    expect(row.text()).toMatch(/excluded/i)
  })

  it('shows the format histogram with the repositories, components and size of each format', async () => {
    const wizard = connectedWizard()
    const { wrapper } = await mountWith(DiscoverStep, { wizard, at: '/settings/migration/discover' })

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const histogram = wrapper.find('table[data-test="formats"]')
    expect(histogram.find('caption').exists()).toBe(true)
    expect(histogram.text()).toContain('maven2')
    expect(histogram.text()).toContain('124')
  })

  it('keeps the discovery in the page only', async () => {
    const wizard = connectedWizard()
    const { wrapper } = await mountWith(DiscoverStep, { wizard, at: '/settings/migration/discover' })

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wizard.state.discovery).not.toBeNull()
    expect(window.localStorage.length).toBe(0)
  })

  it('shows the problem beside the button when the discovery is refused', async () => {
    const { ApiProblem } = await import('../../store/migration/migrationApi')
    const api = fakeApi({
      discover: async () => { throw new ApiProblem({ status: 403, title: 'Forbidden', detail: 'The credential may not read this area of Nexus' }) },
    })
    const wizard = connectedWizard()
    const { wrapper } = await mountWith(DiscoverStep, { api, wizard, at: '/settings/migration/discover' })

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('The credential may not read this area of Nexus')
    expect(wizard.state.discovery).toBeNull()
  })
})
