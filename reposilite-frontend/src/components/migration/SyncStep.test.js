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
import SyncStep from './SyncStep.vue'
import { createWizardStore } from '../../store/migration/wizardStore'
import { planFingerprint } from '../../store/migration/gating'
import { fakeApi, mountWith, cleanup, manualRuntime } from './testing'

afterEach(cleanup)

const PASSWORD = 'literal-pw-7731'

const importJob = (id, status, extra = {}) => ({
  id,
  kind: 'IMPORT',
  status,
  pauseReason: null,
  owner: 'replica-a',
  lastError: null,
  repositories: [
    { nexusName: 'releases', targetName: 'releases', type: 'hosted', status: 'COPIED' },
    { nexusName: 'snapshots', targetName: 'snapshots', type: 'hosted', status: 'CONFLICTED' },
  ],
  results: {},
  summary: null,
  ...extra,
})

const dryRunJob = () => ({
  id: 4,
  kind: 'DRY_RUN',
  status: 'COMPLETED',
  pauseReason: null,
  repositories: [],
  results: {},
  summary: { transferBytes: 10, formats: {}, blockers: [] },
})

const mapping = { repositories: [
  { nexusName: 'releases', targetName: 'releases', included: true, supported: true },
  { nexusName: 'snapshots', targetName: 'snapshots', included: true, supported: true },
], conflictPolicy: 'refuse' }

// A wizard whose dry run was accepted for the mapping it holds, with a literal password held in memory.
const acceptedWizard = ({ jobs = [dryRunJob()], accepted = true, literal = true } = {}) => {
  const wizard = createWizardStore()
  wizard.setConnection({ url: 'https://nexus.example.test', user: 'importer', passwordMode: literal ? 'literal' : 'reference', reference: literal ? undefined : '${env:NEXUS_PASSWORD}' })
  wizard.setDiscovery({ repositories: [], formats: {} })
  wizard.setMapping(mapping.repositories, mapping.conflictPolicy)
  if (literal) wizard.passwords.remember(PASSWORD)
  if (accepted) wizard.accept({ dryRunJobId: 4, fingerprint: planFingerprint(mapping) })
  wizard.setJobs(jobs)
  return wizard
}

describe('the Sync step', () => {
  it('attaches to the unfinished import job on load and shows its progress', async () => {
    const api = fakeApi({ getJob: vi.fn(async () => importJob(9, 'RUNNING')) })
    const wizard = acceptedWizard({ jobs: [dryRunJob(), importJob(9, 'RUNNING')] })
    const { wrapper } = await mountWith(SyncStep, { api, wizard, at: '/settings/migration/sync' })

    expect(api.getJob).toHaveBeenCalledWith(9)
    expect(wrapper.find('[role="progressbar"]').exists()).toBe(true)
  })

  it('shows each repository with its result in words', async () => {
    const api = fakeApi({ getJob: vi.fn(async () => importJob(9, 'RUNNING')) })
    const wizard = acceptedWizard({ jobs: [dryRunJob(), importJob(9, 'RUNNING')] })
    const { wrapper } = await mountWith(SyncStep, { api, wizard, at: '/settings/migration/sync' })

    const table = wrapper.find('table[data-test="repositories"]')
    expect(table.find('caption').exists()).toBe(true)
    expect(table.text()).toContain('Copied')
    expect(table.text()).toContain('Conflicted')
  })

  it('shows a read-only pause in words and offers no password form', async () => {
    const api = fakeApi({ getJob: vi.fn(async () => importJob(9, 'PAUSED', { pauseReason: 'READ_ONLY' })) })
    const wizard = acceptedWizard({ jobs: [dryRunJob(), importJob(9, 'PAUSED', { pauseReason: 'READ_ONLY' })] })
    const { wrapper } = await mountWith(SyncStep, { api, wizard, at: '/settings/migration/sync' })

    expect(wrapper.text()).toMatch(/read-only mode/i)
    expect(wrapper.text()).toMatch(/resumes by itself/i)
    expect(wrapper.find('#migration-sync-password').exists(), 'no credential form for a read-only pause').toBe(false)
  })

  it('asks for the password again for a credentials pause and sends it only to the credentials route', async () => {
    const api = fakeApi({ getJob: vi.fn(async () => importJob(9, 'PAUSED', { pauseReason: 'CREDENTIALS_NEEDED' })) })
    const wizard = acceptedWizard({ jobs: [dryRunJob(), importJob(9, 'PAUSED', { pauseReason: 'CREDENTIALS_NEEDED' })], literal: false })
    const { wrapper } = await mountWith(SyncStep, { api, wizard, at: '/settings/migration/sync' })

    expect(wrapper.find('#migration-sync-password').exists()).toBe(true)

    await wrapper.find('#migration-sync-password').setValue(PASSWORD)
    await wrapper.find('#migration-sync-send-password').trigger('click')
    await flushPromises()

    expect(api.supplyCredentials).toHaveBeenCalledWith(9, PASSWORD)
    expect(api.createJob, 'no other call carries the password').not.toHaveBeenCalled()
  })

  it('shows the buttons that follow the job state', async () => {
    const api = fakeApi({ getJob: vi.fn(async () => importJob(9, 'RUNNING')) })
    const wizard = acceptedWizard({ jobs: [dryRunJob(), importJob(9, 'RUNNING')] })
    const { wrapper } = await mountWith(SyncStep, { api, wizard, at: '/settings/migration/sync' })

    expect(wrapper.find('#migration-sync-pause').exists()).toBe(true)
    expect(wrapper.find('#migration-sync-resume').exists()).toBe(false)
  })

  it('offers resume for a job the operator paused', async () => {
    const api = fakeApi({ getJob: vi.fn(async () => importJob(9, 'PAUSED', { pauseReason: 'OPERATOR' })) })
    const wizard = acceptedWizard({ jobs: [dryRunJob(), importJob(9, 'PAUSED', { pauseReason: 'OPERATOR' })] })
    const { wrapper } = await mountWith(SyncStep, { api, wizard, at: '/settings/migration/sync' })

    expect(wrapper.find('#migration-sync-resume').exists()).toBe(true)
    expect(wrapper.find('#migration-sync-pause').exists()).toBe(false)
  })

  it('asks for confirmation before cancelling, and sends nothing until the cancel is confirmed', async () => {
    const api = fakeApi({ getJob: vi.fn(async () => importJob(9, 'RUNNING')) })
    const wizard = acceptedWizard({ jobs: [dryRunJob(), importJob(9, 'RUNNING')] })
    const { wrapper } = await mountWith(SyncStep, { api, wizard, at: '/settings/migration/sync', attachTo: document.body })

    await wrapper.find('#migration-sync-cancel').trigger('click')
    await flushPromises()

    expect(document.body.textContent).toMatch(/cancel the import/i)
    expect(api.cancelJob, 'no request before the confirmation').not.toHaveBeenCalled()

    document.getElementById('migration-sync-cancel-confirm').click()
    await flushPromises()

    expect(api.cancelJob).toHaveBeenCalledWith(9)
  })

  it('lists the conflicts of the import with both checksums', async () => {
    const api = fakeApi({
      getJob: vi.fn(async () => importJob(9, 'COMPLETED')),
      conflicts: vi.fn(async () => [{ repository: 'snapshots', path: 'com/acme/app/1.0/app-1.0.jar', detail: 'sha1 3f2a versus 9b1c' }]),
    })
    const wizard = acceptedWizard({ jobs: [dryRunJob(), importJob(9, 'COMPLETED')] })
    const { wrapper } = await mountWith(SyncStep, { api, wizard, at: '/settings/migration/sync' })

    expect(api.conflicts).toHaveBeenCalledWith(9)
    expect(wrapper.text()).toContain('com/acme/app/1.0/app-1.0.jar')
    expect(wrapper.text()).toContain('sha1 3f2a versus 9b1c')
  })

  it('keeps the start closed until the dry run is accepted, and says why', async () => {
    const wizard = acceptedWizard({ accepted: false, jobs: [dryRunJob()] })
    const { wrapper, api } = await mountWith(SyncStep, { wizard, at: '/settings/migration/sync' })

    expect(wrapper.find('#migration-sync-start').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toMatch(/accept the loss report/i)
    expect(api.createJob).not.toHaveBeenCalled()
  })

  it('starts the import with the mapping the accepted dry run was made for', async () => {
    const api = fakeApi({ getJob: vi.fn(async () => importJob(9, 'RUNNING')) })
    const wizard = acceptedWizard()
    const { wrapper } = await mountWith(SyncStep, { api, wizard, at: '/settings/migration/sync' })

    await wrapper.find('#migration-sync-start').trigger('click')
    await flushPromises()

    expect(api.createJob).toHaveBeenCalledWith({
      kind: 'IMPORT',
      url: 'https://nexus.example.test',
      user: 'importer',
      password: PASSWORD,
      repositories: ['releases', 'snapshots'],
      targets: {},
      conflictPolicy: 'refuse',
    })
  })

  it('keeps the import running while the tab is hidden and reads it again when it is shown', async () => {
    const runtime = manualRuntime()
    const api = fakeApi({ getJob: vi.fn(async () => importJob(9, 'RUNNING')) })
    const wizard = acceptedWizard({ jobs: [dryRunJob(), importJob(9, 'RUNNING')] })
    await mountWith(SyncStep, { api, wizard, runtime, at: '/settings/migration/sync' })

    runtime.setHidden(true)
    await runtime.advance(60000)
    const whileHidden = api.getJob.mock.calls.length

    runtime.setHidden(false)
    await flushPromises()

    expect(whileHidden, 'no read while the tab is hidden').toBe(1)
    expect(api.getJob.mock.calls.length, 'one read at once when shown again').toBe(2)
  })
})
