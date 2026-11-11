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
import MappingStep from './MappingStep.vue'
import { createWizardStore } from '../../store/migration/wizardStore'
import { mountWith, cleanup, discovery } from './testing'

afterEach(cleanup)

// A wizard whose discovery has finished, so the mapping has its rows.
const discoveredWizard = () => {
  const wizard = createWizardStore()
  wizard.setConnection({ url: 'https://nexus.example.test', user: 'importer', passwordMode: 'reference', reference: '${env:NEXUS_PASSWORD}' })
  wizard.setDiscovery(discovery())
  return wizard
}

const rowOf = (wrapper, name) => wrapper.findAll('tbody tr').find(entry => entry.text().includes(name))
const targetInput = (wrapper, name) => wrapper.find(`#migration-target-${name}`)
const includeBox = (wrapper, name) => wrapper.find(`#migration-include-${name}`)
const policyChoice = (wrapper, policy) => wrapper.find(`input[name="migration-conflict-policy"][data-policy="${policy}"]`)

describe('the Mapping step', () => {
  it('asks for a discovery first when the page holds none', async () => {
    const { wrapper } = await mountWith(MappingStep, { at: '/settings/migration/mapping' })

    expect(wrapper.text()).toMatch(/discover the repositories first/i)
  })

  it('lists each supported repository with a target name and an include control, and excludes the others', async () => {
    const { wrapper } = await mountWith(MappingStep, { wizard: discoveredWizard(), at: '/settings/migration/mapping' })

    expect(targetInput(wrapper, 'releases').exists(), 'the target name is a labelled input').toBe(true)
    expect(includeBox(wrapper, 'releases').exists(), 'the include control is a labelled checkbox').toBe(true)
    expect(rowOf(wrapper, 'npm-proxy').text()).toMatch(/excluded/i)
    expect(includeBox(wrapper, 'npm-proxy').exists(), 'an excluded repository has no control').toBe(false)
  })

  it('preselects refuse as the conflict policy and holds the mapping in the page', async () => {
    const wizard = discoveredWizard()
    const { wrapper } = await mountWith(MappingStep, { wizard, at: '/settings/migration/mapping' })

    expect(policyChoice(wrapper, 'refuse').element.checked).toBe(true)
    expect(wizard.state.mapping.conflictPolicy).toBe('refuse')
    expect(window.localStorage.length).toBe(0)
  })

  it('warns about an empty target name on its row', async () => {
    const wizard = discoveredWizard()
    const { wrapper } = await mountWith(MappingStep, { wizard, at: '/settings/migration/mapping' })

    await targetInput(wrapper, 'releases').setValue('')

    expect(rowOf(wrapper, 'releases').text()).toMatch(/enter a target name/i)
  })

  it('warns about every included row that shares a target name', async () => {
    const wizard = discoveredWizard()
    const { wrapper } = await mountWith(MappingStep, { wizard, at: '/settings/migration/mapping' })

    await targetInput(wrapper, 'snapshots').setValue('releases')
    await flushPromises()

    expect(rowOf(wrapper, 'releases').text()).toMatch(/shares this name/i)
    expect(rowOf(wrapper, 'snapshots').text()).toMatch(/shares this name/i)
  })

  it('writes a changed target name and selection into the page mapping', async () => {
    const wizard = discoveredWizard()
    const { wrapper } = await mountWith(MappingStep, { wizard, at: '/settings/migration/mapping' })

    await targetInput(wrapper, 'releases').setValue('releases-ingot')
    await includeBox(wrapper, 'snapshots').setValue(false)

    const releases = wizard.state.mapping.repositories.find(repository => repository.nexusName === 'releases')
    const snapshots = wizard.state.mapping.repositories.find(repository => repository.nexusName === 'snapshots')
    expect(releases.targetName).toBe('releases-ingot')
    expect(snapshots.included).toBe(false)
  })

  it('asks for confirmation before the overwrite policy applies, and keeps refuse when the dialog is cancelled', async () => {
    const wizard = discoveredWizard()
    const { wrapper } = await mountWith(MappingStep, { wizard, at: '/settings/migration/mapping', attachTo: document.body })

    await policyChoice(wrapper, 'overwrite').setValue(true)
    await flushPromises()

    expect(document.body.textContent, 'the dialog says files are replaced').toMatch(/existing files will be replaced/i)
    expect(wizard.state.mapping.conflictPolicy, 'the choice waits for the confirmation').toBe('refuse')

    document.getElementById('migration-overwrite-cancel').click()
    await flushPromises()

    expect(wizard.state.mapping.conflictPolicy).toBe('refuse')
    expect(policyChoice(wrapper, 'refuse').element.checked).toBe(true)
  })

  it('applies the overwrite policy once the operator confirms it', async () => {
    const wizard = discoveredWizard()
    const { wrapper } = await mountWith(MappingStep, { wizard, at: '/settings/migration/mapping', attachTo: document.body })

    await policyChoice(wrapper, 'overwrite').setValue(true)
    await flushPromises()
    document.getElementById('migration-overwrite-confirm').click()
    await flushPromises()

    expect(wizard.state.mapping.conflictPolicy).toBe('overwrite')
  })
})
