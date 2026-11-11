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

import { describe, it, expect, afterEach, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import CutOverStep from './CutOverStep.vue'
import { createWizardStore } from '../../store/migration/wizardStore'
import { fakeApi, mountWith, cleanup } from './testing'

afterEach(cleanup)

const PASSWORD = 'literal-pw-7731'

const importJob = () => ({
  id: 9,
  kind: 'IMPORT',
  status: 'COMPLETED',
  pauseReason: null,
  repositories: [
    { nexusName: 'releases', targetName: 'releases', type: 'hosted', status: 'COPIED' },
    { nexusName: 'snapshots', targetName: 'snapshots', type: 'hosted', status: 'COPIED' },
  ],
  results: {},
  summary: null,
})

const verifyJob = (repositories, status = 'COMPLETED') => ({
  id: 12,
  kind: 'VERIFY',
  status,
  pauseReason: null,
  repositories,
  results: {},
  summary: null,
})

const verified = [
  { nexusName: 'releases', targetName: 'releases', type: 'hosted', status: 'VERIFIED' },
  { nexusName: 'snapshots', targetName: 'snapshots', type: 'hosted', status: 'VERIFIED' },
]

const wizardWith = (jobs) => {
  const wizard = createWizardStore()
  wizard.setConnection({ url: 'https://nexus.example.test', user: 'importer', passwordMode: 'literal' })
  wizard.passwords.remember(PASSWORD)
  wizard.setJobs(jobs)
  return wizard
}

const checkboxOf = (wrapper, id) => wrapper.find(`#${id}`)

describe('the Cut-over step', () => {
  it('shows the verify item as done when the newest verify job verified every repository, and does not let the operator tick it', async () => {
    const { wrapper } = await mountWith(CutOverStep, { wizard: wizardWith([importJob(), verifyJob(verified)]), at: '/settings/migration/cut-over' })

    const item = checkboxOf(wrapper, 'migration-cutover-verify')
    expect(item.element.checked, 'derived from the verify job').toBe(true)
    expect(item.attributes('disabled'), 'the operator cannot tick it').toBeDefined()
  })

  it('names a mismatching repository as blocking, and leaves the verify item unticked', async () => {
    const mismatch = [
      { nexusName: 'releases', targetName: 'releases', type: 'hosted', status: 'VERIFIED' },
      { nexusName: 'snapshots', targetName: 'snapshots', type: 'hosted', status: 'MISMATCH' },
    ]
    const { wrapper } = await mountWith(CutOverStep, { wizard: wizardWith([importJob(), verifyJob(mismatch)]), at: '/settings/migration/cut-over' })

    expect(checkboxOf(wrapper, 'migration-cutover-verify').element.checked).toBe(false)
    expect(wrapper.find('[data-test="blocking"]').text()).toContain('snapshots')
  })

  it('blocks the verify item for a conflicted repository and lists its conflicts', async () => {
    const conflicted = [
      { nexusName: 'releases', targetName: 'releases', type: 'hosted', status: 'VERIFIED' },
      { nexusName: 'snapshots', targetName: 'snapshots', type: 'hosted', status: 'CONFLICTED' },
    ]
    const api = fakeApi({ conflicts: vi.fn(async () => [{ repository: 'snapshots', path: 'a/b/c.jar', detail: 'sha1 differs' }]) })
    const wizard = wizardWith([importJob(), verifyJob(conflicted)])
    const { wrapper } = await mountWith(CutOverStep, { api, wizard, at: '/settings/migration/cut-over' })

    expect(checkboxOf(wrapper, 'migration-cutover-verify').element.checked).toBe(false)
    expect(wrapper.find('[data-test="blocking"]').text()).toContain('snapshots')
    expect(wrapper.text()).toContain('a/b/c.jar')
  })

  it('keeps the verify item unticked while no verify job has finished', async () => {
    const { wrapper } = await mountWith(CutOverStep, { wizard: wizardWith([importJob()]), at: '/settings/migration/cut-over' })

    expect(checkboxOf(wrapper, 'migration-cutover-verify').element.checked).toBe(false)
    expect(wrapper.find('[data-test="blocking"]').text()).toMatch(/verify/i)
  })

  it('keeps the manual ticks in the page only, and clears them when the page is opened again', async () => {
    const wizard = wizardWith([importJob(), verifyJob(verified)])
    const first = await mountWith(CutOverStep, { wizard, at: '/settings/migration/cut-over' })
    await checkboxOf(first.wrapper, 'migration-cutover-freeze').setValue(true)
    await checkboxOf(first.wrapper, 'migration-cutover-switch').setValue(true)
    first.wrapper.unmount()

    const second = await mountWith(CutOverStep, { wizard, at: '/settings/migration/cut-over' })

    expect(checkboxOf(second.wrapper, 'migration-cutover-freeze').element.checked).toBe(false)
    expect(window.localStorage.length, 'nothing is stored').toBe(0)
  })

  it('shows the freeze as instructions and calls no address of the server or of Nexus', async () => {
    const api = fakeApi()
    const { wrapper } = await mountWith(CutOverStep, { api, wizard: wizardWith([importJob(), verifyJob(verified)]), at: '/settings/migration/cut-over' })

    await checkboxOf(wrapper, 'migration-cutover-freeze').setValue(true)

    expect(wrapper.text()).toMatch(/read-only/i)
    for (const call of Object.values(api)) {
      expect(call.mock?.calls ?? [], 'no call is made by the freeze item').toHaveLength(0)
    }
  })

  it('offers no delta and no alias control', async () => {
    const { wrapper } = await mountWith(CutOverStep, { wizard: wizardWith([importJob(), verifyJob(verified)]), at: '/settings/migration/cut-over' })

    expect(wrapper.text()).not.toMatch(/delta|alias/i)
    expect(wrapper.find('input[id*="delta" i], input[id*="alias" i]').exists()).toBe(false)
  })

  it('shows Maven and Gradle snippets for the Ingot repositories, and no secret in them', async () => {
    const { wrapper } = await mountWith(CutOverStep, { wizard: wizardWith([importJob(), verifyJob(verified)]), at: '/settings/migration/cut-over' })

    const text = wrapper.text()
    expect(text).toContain('<mirror>')
    expect(text).toContain('repositories {')
    expect(text).toContain('releases')
    expect(text).not.toContain(PASSWORD)
    expect(text).not.toMatch(/\$\{env:/)
  })

  it('shows the freeze, verify and switch items in the order of the checklist', async () => {
    const { wrapper } = await mountWith(CutOverStep, { wizard: wizardWith([importJob(), verifyJob(verified)]), at: '/settings/migration/cut-over' })

    const ids = wrapper.findAll('input[type="checkbox"]').map(input => input.attributes('id'))
    expect(ids).toEqual(['migration-cutover-freeze', 'migration-cutover-verify', 'migration-cutover-switch'])
  })

  it('removes a finished job only after a confirmation', async () => {
    const api = fakeApi()
    const { wrapper } = await mountWith(CutOverStep, { api, wizard: wizardWith([importJob(), verifyJob(verified)]), at: '/settings/migration/cut-over', attachTo: document.body })

    await wrapper.find('#migration-cutover-delete').trigger('click')
    expect(api.deleteJob, 'no request before the confirmation').not.toHaveBeenCalled()

    document.getElementById('migration-cutover-delete-confirm').click()
    await flushPromises()

    expect(api.deleteJob).toHaveBeenCalledWith(12)
  })
})
