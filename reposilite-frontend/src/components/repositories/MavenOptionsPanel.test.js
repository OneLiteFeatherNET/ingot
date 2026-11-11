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

import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { JsonForms } from '@jsonforms/vue'
import { configurationStoreKey } from '../../store/configuration'
import MavenOptionsPanel from './MavenOptionsPanel.vue'
import { loadedStore, repositorySchema, repositoriesServer } from './fixtures/repositories'

vi.mock('../../store/session', () => ({ useSession: () => ({ client: { value: null }, isManager: { value: false } }) }))

let wrapper

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

// The served maven schema with its one domain-level field, as the instance declares it.
const schemaWithOption = () => {
  const schema = repositorySchema({ versionPolicy: true, readOnly: true })
  schema.properties.resolutionCacheClusterMaxAgeSeconds = {
    type: 'integer',
    title: 'Resolution cache maximum age in cluster mode',
    description: 'Maximum age in seconds of a cached routing decision.',
    minimum: 0
  }
  return schema
}

const serverWithOption = () => ({ maven: { ...repositoriesServer().maven, resolutionCacheClusterMaxAgeSeconds: 0 } })

// A fresh store and wrapper per test. The panel reads the draft of the store it is given.
const mountPanel = async ({ schema = schemaWithOption() } = {}) => {
  const store = await loadedStore({ server: serverWithOption(), schema })
  wrapper = mount(MavenOptionsPanel, {
    global: { provide: { [configurationStoreKey]: store } },
    attachTo: document.body
  })
  await flushPromises()
  return { store }
}

const sectionOf = () => wrapper.find('[data-test="maven-options"]')

describe('the Maven options section', () => {
  it('is headed Maven options and shows the domain-level field with its label and description', async () => {
    await mountPanel()

    expect(sectionOf().exists(), 'the section is rendered').toBe(true)
    expect(sectionOf().find('h2').text(), 'the heading of the section').toBe('Maven options')
    expect(sectionOf().text(), 'the label of the field').toContain('Resolution cache maximum age in cluster mode')
    expect(sectionOf().text(), 'the description of the field').toContain('Maximum age in seconds of a cached routing decision.')
  })

  it('is not rendered when the served schema has no domain-level field', async () => {
    await mountPanel({ schema: repositorySchema({ versionPolicy: true }) })

    expect(sectionOf().exists(), 'the table is the whole page, so no section is drawn').toBe(false)
  })

  it('takes no focus on mount', async () => {
    await mountPanel()

    expect(wrapper.element.contains(document.activeElement), 'the focus stays where it was').toBe(false)
  })

  it('writes the option to the draft with the same domain object, so the repositories are kept', async () => {
    const { store } = await mountPanel()
    const before = JSON.parse(JSON.stringify(store.configurations.value.maven.repositories))
    const setDomainData = vi.spyOn(store, 'setDomainData')

    const form = wrapper.findComponent(JsonForms)
    form.vm.$emit('change', {
      data: { ...store.configurations.value.maven, resolutionCacheClusterMaxAgeSeconds: 60 },
      errors: []
    })
    await flushPromises()

    expect(setDomainData).toHaveBeenCalledWith('maven', expect.objectContaining({ resolutionCacheClusterMaxAgeSeconds: 60 }))
    expect(store.configurations.value.maven.resolutionCacheClusterMaxAgeSeconds).toBe(60)
    expect(store.configurations.value.maven.repositories, 'every repository keeps its values').toEqual(before)
    expect(store.hasUnsavedChanges.value, 'the save bar sees one change').toBe(true)
  })

  it('validates the maven domain after a change', async () => {
    const { store } = await mountPanel()
    const validateDomain = vi.spyOn(store, 'validateDomain')

    wrapper.findComponent(JsonForms).vm.$emit('change', {
      data: { ...store.configurations.value.maven, resolutionCacheClusterMaxAgeSeconds: 5 },
      errors: []
    })
    await flushPromises()

    expect(validateDomain).toHaveBeenCalledWith('maven')
  })

  it('marks an invalid value as invalid, with the same renderers as the rest of the settings', async () => {
    const { store } = await mountPanel()

    store.setDomainData('maven', { ...store.configurations.value.maven, resolutionCacheClusterMaxAgeSeconds: -1 })
    store.validateDomain('maven')
    await flushPromises()

    const input = wrapper.findAll('input').find(candidate => candidate.element.value === '-1')
    expect(input, 'the field shows the typed value').toBeDefined()
    expect(input.attributes('aria-invalid'), 'the field says it is invalid').toBe('true')
    expect(store.invalidDomains.value, 'the save bar is blocked').toContain('maven')
  })
})
