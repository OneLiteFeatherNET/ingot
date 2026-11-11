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
import VerifyStep from './VerifyStep.vue'
import { createWizardStore } from '../../store/migration/wizardStore'
import { fakeApi, mountWith, cleanup } from './testing'

afterEach(cleanup)

const PASSWORD = 'literal-pw-7731'

const importJob = (id = 9, status = 'COMPLETED') => ({
  id,
  kind: 'IMPORT',
  status,
  pauseReason: null,
  repositories: [
    { nexusName: 'releases', targetName: 'releases', type: 'hosted', status: 'COPIED' },
    { nexusName: 'snapshots', targetName: 'snapshots', type: 'hosted', status: 'COPIED' },
  ],
  results: {},
  summary: null,
})

const verifyJob = (status, repositories) => ({
  id: 12,
  kind: 'VERIFY',
  status,
  pauseReason: null,
  repositories,
  results: {},
  summary: null,
})

const wizardWith = ({ jobs = [importJob()], literal = true } = {}) => {
  const wizard = createWizardStore()
  wizard.setConnection({ url: 'https://nexus.example.test', user: 'importer', passwordMode: literal ? 'literal' : 'reference', reference: literal ? undefined : '${env:NEXUS_PASSWORD}' })
  if (literal) wizard.passwords.remember(PASSWORD)
  wizard.setJobs(jobs)
  return wizard
}

describe('the Verify step', () => {
  it('asks for a started import before it offers a verify', async () => {
    const { wrapper, api } = await mountWith(VerifyStep, { wizard: wizardWith({ jobs: [] }), at: '/settings/migration/verify' })

    expect(wrapper.text()).toMatch(/start the import first/i)
    expect(api.createJob).not.toHaveBeenCalled()
  })

  it('starts a verify job that checks the import job, with the connection in the body', async () => {
    const api = fakeApi({ createJob: vi.fn(async () => ({ id: 12, kind: 'VERIFY', status: 'QUEUED' })), getJob: vi.fn(async () => verifyJob('RUNNING', [])) })
    const { wrapper } = await mountWith(VerifyStep, { api, wizard: wizardWith(), at: '/settings/migration/verify' })

    await wrapper.find('#migration-verify-start').trigger('click')
    await flushPromises()

    expect(api.createJob).toHaveBeenCalledWith({
      kind: 'VERIFY',
      url: 'https://nexus.example.test',
      user: 'importer',
      password: PASSWORD,
      verifyImportJob: 9,
    })
  })

  it('shows VERIFIED or MISMATCH in words for each repository', async () => {
    const finished = verifyJob('COMPLETED', [
      { nexusName: 'releases', targetName: 'releases', type: 'hosted', status: 'VERIFIED' },
      { nexusName: 'snapshots', targetName: 'snapshots', type: 'hosted', status: 'MISMATCH' },
    ])
    const api = fakeApi({ getJob: vi.fn(async () => finished) })
    const { wrapper } = await mountWith(VerifyStep, { api, wizard: wizardWith({ jobs: [importJob(), finished] }), at: '/settings/migration/verify' })

    const table = wrapper.find('table[data-test="verify"]')
    expect(table.text()).toContain('Verified')
    expect(table.text()).toContain('Mismatch')
  })

  it('lists the paths that do not match, from the verify job', async () => {
    const finished = verifyJob('COMPLETED', [{ nexusName: 'snapshots', targetName: 'snapshots', type: 'hosted', status: 'MISMATCH' }])
    const api = fakeApi({
      conflicts: vi.fn(async () => [{ repository: 'snapshots', path: 'org/acme/lib/2.0/lib-2.0.pom', detail: 'checksum differs' }]),
      getJob: vi.fn(async () => finished),
    })
    const { wrapper } = await mountWith(VerifyStep, { api, wizard: wizardWith({ jobs: [importJob(), finished] }), at: '/settings/migration/verify' })

    expect(api.conflicts).toHaveBeenCalledWith(12)
    expect(wrapper.text()).toContain('org/acme/lib/2.0/lib-2.0.pom')
  })

  it('asks for the password again after a reload before it starts a verify', async () => {
    const api = fakeApi({ createJob: vi.fn(async () => ({ id: 12, kind: 'VERIFY', status: 'QUEUED' })), getJob: vi.fn(async () => verifyJob('RUNNING', [])) })
    const wizard = wizardWith()
    wizard.passwords.forget()
    const { wrapper } = await mountWith(VerifyStep, { api, wizard, at: '/settings/migration/verify' })

    expect(wrapper.find('#migration-verify-password').exists(), 'the password is asked for').toBe(true)

    await wrapper.find('#migration-verify-password').setValue(PASSWORD)
    await wrapper.find('#migration-verify-start').trigger('click')
    await flushPromises()

    expect(api.createJob).toHaveBeenCalledWith(expect.objectContaining({ kind: 'VERIFY', password: PASSWORD, verifyImportJob: 9 }))
  })

  it('sends a secret reference without asking for a password', async () => {
    const api = fakeApi({ createJob: vi.fn(async () => ({ id: 12, kind: 'VERIFY', status: 'QUEUED' })), getJob: vi.fn(async () => verifyJob('RUNNING', [])) })
    const { wrapper } = await mountWith(VerifyStep, { api, wizard: wizardWith({ literal: false }), at: '/settings/migration/verify' })

    expect(wrapper.find('#migration-verify-password').exists()).toBe(false)

    await wrapper.find('#migration-verify-start').trigger('click')
    await flushPromises()

    expect(api.createJob).toHaveBeenCalledWith(expect.objectContaining({ password: '${env:NEXUS_PASSWORD}' }))
  })
})
