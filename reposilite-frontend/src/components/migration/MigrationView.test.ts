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
import MigrationView from './MigrationView.vue'
import { createWizardStore } from '../../store/migration/wizardStore'
import { fakeApi, mountWith, cleanup } from './testing'

afterEach(cleanup)

const job = (id, kind, status, extra = {}) => ({ id, kind, status, pauseReason: null, repositories: [], results: {}, summary: null, ...extra })

// The step the address names, as the router holds it after the page settled.
const stepOf = (router) => router.currentRoute.value.params.step

const connectedWizard = () => {
  const wizard = createWizardStore()
  wizard.setConnection({ url: 'https://nexus.example.test', user: 'importer', passwordMode: 'reference', reference: '${env:NEXUS_PASSWORD}' })
  wizard.setDiscovery({ repositories: [], formats: {} })
  return wizard
}

describe('the Migration page', () => {
  it('opens the step the address names when that step is open', async () => {
    const wizard = createWizardStore()
    wizard.setConnection({ url: 'https://nexus.example.test', user: 'importer', passwordMode: 'reference', reference: '${env:NEXUS_PASSWORD}' })
    const { wrapper, router } = await mountWith(MigrationView, { props: { step: 'discover' }, wizard, at: '/settings/migration/discover' })

    expect(stepOf(router)).toBe('discover')
    expect(wrapper.find('h2').text()).toBe('Discover')
  })

  it('sends a closed address to the first open step, with the reason in words', async () => {
    const { wrapper, router } = await mountWith(MigrationView, { props: { step: 'sync' }, at: '/settings/migration/sync' })

    expect(stepOf(router), 'a fresh page opens Connect').toBe('connect')
    expect(wrapper.find('[role="status"]').text()).toMatch(/connection/i)
  })

  it('sends a step the page does not know to the first open step', async () => {
    const { router } = await mountWith(MigrationView, { props: { step: 'nonsense' }, at: '/settings/migration/nonsense' })

    expect(stepOf(router)).toBe('connect')
  })

  it('opens the step the jobs decide after a reload, not the one the address names', async () => {
    const api = fakeApi({ listJobs: async () => [job(2, 'IMPORT', 'RUNNING')] })
    const { router, wizard } = await mountWith(MigrationView, { props: { step: 'dry-run' }, api, at: '/settings/migration/dry-run' })

    expect(stepOf(router), 'the running import opens Sync').toBe('sync')
    expect(wizard.state.jobs.map(entry => entry.id)).toEqual([2])
  })

  it('sends a reload on Mapping with no job to Connect, because the mapping lives in the page only', async () => {
    const { router } = await mountWith(MigrationView, { props: { step: 'mapping' }, at: '/settings/migration/mapping' })

    expect(stepOf(router)).toBe('connect')
  })

  it('reads the jobs once when it opens, and not again for each step', async () => {
    const api = fakeApi({ listJobs: vi.fn(async () => [job(3, 'VERIFY', 'COMPLETED')]) })
    const { wrapper } = await mountWith(MigrationView, { props: { step: 'connect' }, api, at: '/settings/migration/connect' })
    await flushPromises()

    expect(api.listJobs).toHaveBeenCalledTimes(1)
    expect(wrapper.find('h2').text()).toBe('Connect')
  })

  it('shows an access denied state, and no step, when the jobs cannot be read for the permission', async () => {
    const { ApiProblem } = await import('../../store/migration/migrationApi')
    const api = fakeApi({ listJobs: vi.fn(async () => { throw new ApiProblem({ status: 403, title: 'Forbidden', detail: 'refused' }) }) })
    const { wrapper } = await mountWith(MigrationView, { props: { step: 'connect' }, api, at: '/settings/migration/connect' })
    await flushPromises()

    expect(wrapper.text()).toMatch(/do not have access to the import/i)
    expect(wrapper.find('h2').exists(), 'no step is shown without data').toBe(false)
  })

  it('drops the literal password when the visitor leaves the wizard', async () => {
    const wizard = connectedWizard()
    wizard.passwords.remember('literal-pw-7731')
    const { wrapper } = await mountWith(MigrationView, { props: { step: 'connect' }, wizard, at: '/settings/migration/connect' })

    wrapper.unmount()

    expect(wizard.passwords.recall()).toBeNull()
  })
})
