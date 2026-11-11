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
import DryRunStep from './DryRunStep.vue'
import { createWizardStore } from '../../store/migration/wizardStore'
import { ApiProblem } from '../../store/migration/migrationApi'
import { planFingerprint } from '../../store/migration/gating'
import { fakeApi, mountWith, cleanup, manualRuntime } from './testing'

afterEach(cleanup)

const lossRows = [
  { objectType: 'repository', nexusId: 'releases', target: 'releases-ingot', status: 'MAPPED', reason: 'Maven hosted repository', consequence: 'Copied', action: 'None' },
  { objectType: 'repository', nexusId: 'snapshots', target: 'snapshots', status: 'MAPPED', reason: 'Maven hosted repository', consequence: 'Copied', action: 'None' },
  { objectType: 'privilege', nexusId: 'nx-admin', target: null, status: 'UNSUPPORTED', reason: 'Privileges are not imported', consequence: 'The privilege is lost', action: 'Recreate it in Ingot' },
]

const dryRunJob = (overrides = {}) => ({
  id: 7,
  kind: 'DRY_RUN',
  status: 'COMPLETED',
  pauseReason: null,
  owner: null,
  lastError: null,
  repositories: [],
  results: {},
  summary: { transferBytes: 2048, formats: { maven2: 2 }, blockers: [] },
  ...overrides,
})

// A wizard with a connection, a discovery and a mapping in which `releases` is renamed.
const mappedWizard = ({ rename = true } = {}) => {
  const wizard = createWizardStore()
  wizard.setConnection({ url: 'https://nexus.example.test', user: 'importer', passwordMode: 'reference', reference: '${env:NEXUS_PASSWORD}' })
  wizard.setDiscovery({ repositories: [], formats: {} })
  wizard.setMapping([
    { nexusName: 'releases', targetName: rename ? 'releases-ingot' : 'releases', included: true, supported: true },
    { nexusName: 'snapshots', targetName: 'snapshots', included: true, supported: true },
  ], 'refuse')
  return wizard
}

const finishedApi = (overrides = {}) => fakeApi({
  createJob: vi.fn(async () => ({ id: 7, kind: 'DRY_RUN', status: 'QUEUED' })),
  getJob: vi.fn(async () => dryRunJob()),
  report: vi.fn(async () => ({ rows: lossRows, summary: dryRunJob().summary })),
  ...overrides,
})

describe('the Dry run step', () => {
  it('asks for a mapping with an included repository before it starts a dry run', async () => {
    const wizard = createWizardStore()
    const { wrapper, api } = await mountWith(DryRunStep, { wizard, at: '/settings/migration/dry-run' })

    expect(wrapper.text()).toMatch(/map at least one repository/i)
    expect(api.createJob).not.toHaveBeenCalled()
  })

  it('starts a dry run with the included repositories, their target names and the conflict policy', async () => {
    const api = finishedApi()
    const { wrapper } = await mountWith(DryRunStep, { wizard: mappedWizard(), api, at: '/settings/migration/dry-run' })

    await wrapper.find('#migration-dry-run-start').trigger('click')
    await flushPromises()

    expect(api.createJob).toHaveBeenCalledWith({
      kind: 'DRY_RUN',
      url: 'https://nexus.example.test',
      user: 'importer',
      password: '${env:NEXUS_PASSWORD}',
      repositories: ['releases', 'snapshots'],
      targets: { releases: 'releases-ingot' },
      conflictPolicy: 'refuse',
    })
  })

  it('shows the progress of the dry run until it finishes', async () => {
    const runtime = manualRuntime()
    const running = dryRunJob({ status: 'RUNNING', repositories: [{ nexusName: 'releases', targetName: 'releases-ingot', type: 'hosted', status: 'PLANNED' }] })
    const api = finishedApi({ getJob: vi.fn().mockResolvedValueOnce(running).mockResolvedValue(dryRunJob()) })
    const { wrapper } = await mountWith(DryRunStep, { wizard: mappedWizard(), api, runtime, at: '/settings/migration/dry-run' })

    await wrapper.find('#migration-dry-run-start').trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="progressbar"]').exists(), 'a progress bar while the dry run runs').toBe(true)
    expect(wrapper.text()).toContain('Running')

    await runtime.advance(2000)

    expect(wrapper.text()).toContain('Completed')
    expect(wrapper.find('[role="progressbar"]').exists(), 'the bar is gone when the dry run is done').toBe(false)
  })

  it('shows the loss report with the count of each status and a filter by status', async () => {
    const wizard = mappedWizard()
    const { wrapper } = await mountWith(DryRunStep, { wizard, api: finishedApi(), at: '/settings/migration/dry-run' })
    await wrapper.find('#migration-dry-run-start').trigger('click')
    await flushPromises()

    expect(wrapper.find('table[data-test="report"] caption').exists(), 'the report table has a caption').toBe(true)
    expect(wrapper.text()).toMatch(/2 mapped/i)
    expect(wrapper.text()).toMatch(/1 unsupported/i)

    await wrapper.find('#migration-report-status').setValue('UNSUPPORTED')

    const rows = wrapper.findAll('table[data-test="report"] tbody tr')
    expect(rows).toHaveLength(1)
    expect(rows[0].text()).toContain('nx-admin')
  })

  it('links the report as JSON and as CSV, so the browser downloads it with the session', async () => {
    const { wrapper } = await mountWith(DryRunStep, { wizard: mappedWizard(), api: finishedApi(), at: '/settings/migration/dry-run' })
    await wrapper.find('#migration-dry-run-start').trigger('click')
    await flushPromises()

    expect(wrapper.find('a[href*="/jobs/7/report?format=json"]').exists()).toBe(true)
    expect(wrapper.find('a[href*="/jobs/7/report?format=csv"]').exists()).toBe(true)
  })

  it('shows a taken name with its proposal, and taking the proposal makes the accepted report stale', async () => {
    const wizard = mappedWizard({ rename: false })
    const takenRows = [
      { objectType: 'repository', nexusId: 'releases', target: 'releases-nexus', status: 'MAPPED', reason: 'The name releases is taken, so the proposal is releases-nexus.', consequence: 'Copied', action: 'None' },
    ]
    const api = finishedApi({ report: vi.fn(async () => ({ rows: takenRows, summary: dryRunJob().summary })) })
    const { wrapper } = await mountWith(DryRunStep, { wizard, api, at: '/settings/migration/dry-run' })
    await wrapper.find('#migration-dry-run-start').trigger('click')
    await flushPromises()
    await wrapper.find('#migration-accept-check').setValue(true)
    await wrapper.find('#migration-accept-report').trigger('click')

    await wrapper.find('#migration-take-proposal-releases').trigger('click')
    await flushPromises()

    const releases = wizard.state.mapping.repositories.find(repository => repository.nexusName === 'releases')
    expect(releases.targetName).toBe('releases-nexus')
    expect(wrapper.text(), 'the sync gate names the change').toMatch(/changed after the loss report was accepted/i)
  })

  it('shows the server refusal of a reserved name beside the button and keeps the mapping', async () => {
    const api = finishedApi({
      createJob: vi.fn(async () => { throw new ApiProblem({ status: 400, title: 'Bad Request', detail: 'The name releases-ingot is reserved' }) }),
    })
    const wizard = mappedWizard()
    const before = JSON.stringify(wizard.state.mapping)
    const { wrapper } = await mountWith(DryRunStep, { wizard, api, at: '/settings/migration/dry-run' })

    await wrapper.find('#migration-dry-run-start').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('The name releases-ingot is reserved')
    expect(JSON.stringify(wizard.state.mapping)).toBe(before)
  })

  it('lists the blockers and offers to show each one in the report', async () => {
    const blocked = dryRunJob({ summary: { transferBytes: 10, formats: {}, blockers: ['nx-admin cannot be imported'] } })
    const rows = [...lossRows, { objectType: 'privilege', nexusId: 'nx-admin', target: null, status: 'UNSUPPORTED', reason: 'Not imported', consequence: '', action: '' }]
    const api = finishedApi({ getJob: vi.fn(async () => blocked), report: vi.fn(async () => ({ rows, summary: blocked.summary })) })
    const { wrapper } = await mountWith(DryRunStep, { wizard: mappedWizard(), api, attachTo: document.body, at: '/settings/migration/dry-run' })
    await wrapper.find('#migration-dry-run-start').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('nx-admin cannot be imported')

    wrapper.find('#migration-blocker-show-0').element.click()
    await flushPromises()

    expect(document.activeElement?.id).toBe('migration-report-row-nx-admin')
  })

  it('keeps the accept control disabled until the operator has read the report', async () => {
    const { wrapper } = await mountWith(DryRunStep, { wizard: mappedWizard(), api: finishedApi(), at: '/settings/migration/dry-run' })
    await wrapper.find('#migration-dry-run-start').trigger('click')
    await flushPromises()

    expect(wrapper.find('#migration-accept-report').attributes('disabled')).toBeDefined()

    await wrapper.find('#migration-accept-check').setValue(true)

    expect(wrapper.find('#migration-accept-report').attributes('disabled')).toBeUndefined()
  })

  it('records the acceptance for this dry run and this mapping, in the page only', async () => {
    const wizard = mappedWizard()
    const { wrapper } = await mountWith(DryRunStep, { wizard, api: finishedApi(), at: '/settings/migration/dry-run' })
    await wrapper.find('#migration-dry-run-start').trigger('click')
    await flushPromises()

    await wrapper.find('#migration-accept-check').setValue(true)
    await wrapper.find('#migration-accept-report').trigger('click')

    expect(wizard.state.acceptance).toEqual({ dryRunJobId: 7, fingerprint: planFingerprint(wizard.state.mapping) })
    expect(window.localStorage.length).toBe(0)
  })

  it('shows the newest dry run of the jobs with its report after a reload, without starting another', async () => {
    const wizard = mappedWizard()
    wizard.setJobs([dryRunJob()])
    const api = finishedApi()
    const { wrapper } = await mountWith(DryRunStep, { wizard, api, at: '/settings/migration/dry-run' })

    expect(api.createJob).not.toHaveBeenCalled()
    expect(api.report).toHaveBeenCalledWith(7, 'json')
    expect(wrapper.findAll('table[data-test="report"] tbody tr')).toHaveLength(3)
  })
})
