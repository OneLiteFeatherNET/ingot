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
import ConnectStep from './ConnectStep.vue'
import DryRunStep from './DryRunStep.vue'
import JobProgress from './JobProgress.vue'
import { createWizardStore } from '../../store/migration/wizardStore'
import { planFingerprint } from '../../store/migration/gating'
import { fakeApi, mountWith, cleanup, manualRuntime, discovery } from './testing'

afterEach(cleanup)

const connectedWizard = () => {
  const wizard = createWizardStore()
  wizard.setConnection({ url: 'https://nexus.example.test', user: 'importer', passwordMode: 'reference', reference: '${env:NEXUS_PASSWORD}' })
  wizard.setDiscovery(discovery())
  wizard.setMapping([
    { nexusName: 'releases', targetName: 'releases', included: true, supported: true },
    { nexusName: 'snapshots', targetName: 'snapshots', included: true, supported: true },
  ], 'refuse')
  return wizard
}

const dryRunJob = () => ({ id: 7, kind: 'DRY_RUN', status: 'COMPLETED', pauseReason: null, repositories: [], results: {}, summary: { transferBytes: 1, formats: {}, blockers: [] } })

const lossRows = [
  { objectType: 'repository', nexusId: 'snapshots', target: 'snapshots', status: 'MAPPED', reason: 'Maven', consequence: 'Copied', action: 'None' },
  { objectType: 'repository', nexusId: 'releases', target: 'releases', status: 'MAPPED', reason: 'Maven', consequence: 'Copied', action: 'None' },
]

const progressJob = (settled, total) => ({
  id: 9,
  kind: 'IMPORT',
  status: 'RUNNING',
  pauseReason: null,
  repositories: Array.from({ length: total }, (_, index) => ({
    nexusName: `repository-${index}`,
    targetName: `repository-${index}`,
    type: 'hosted',
    status: index < settled ? 'COPIED' : 'PLANNED',
  })),
  results: {},
  summary: null,
})

describe('the structure of the migration page', () => {
  it('renders one heading of level two per step, and marks the current step in the step list', async () => {
    const { wrapper } = await mountWith(MigrationView, { props: { step: 'connect' }, wizard: connectedWizard(), at: '/settings/migration/connect' })

    expect(wrapper.findAll('h2'), 'one heading per step').toHaveLength(1)
    const current = wrapper.find('nav[aria-label="Migration steps"] [aria-current="step"]')
    expect(current.text()).toContain('Connect')
  })

  it('moves focus to the heading of the step when the step changes', async () => {
    const { wrapper } = await mountWith(MigrationView, { props: { step: 'connect' }, wizard: connectedWizard(), at: '/settings/migration/connect', attachTo: document.body })

    await wrapper.setProps({ step: 'discover' })
    await flushPromises()

    expect(document.activeElement?.id, 'the focus is on the Discover heading').toBe('migration-discover-title')
  })

  it('marks a closed step as unavailable, with the reason in the text of the step', async () => {
    const { wrapper } = await mountWith(MigrationView, { props: { step: 'connect' }, at: '/settings/migration/connect' })

    const sync = wrapper.findAll('nav a').find(link => link.text().includes('Sync'))
    expect(sync.attributes('aria-disabled')).toBe('true')
    expect(sync.text()).toMatch(/finish a dry run first/i)
  })
})

describe('the forms', () => {
  it('moves focus to an error summary that links to each invalid field when a submit fails validation', async () => {
    const { wrapper } = await mountWith(ConnectStep, { attachTo: document.body })

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(document.activeElement?.id).toBe('migration-connect-errors')
    expect(wrapper.find('#migration-connect-errors a[href="#migration-connect-url"]').exists()).toBe(true)
    expect(wrapper.find('#migration-connect-errors a[href="#migration-connect-user"]').exists()).toBe(true)
  })

  it('links every invalid field to its error text with aria-describedby', async () => {
    const { wrapper } = await mountWith(ConnectStep)

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const input = wrapper.find('#migration-connect-url')
    const describedBy = input.attributes('aria-describedby')
    expect(describedBy, 'the field names its message').toBeTruthy()
    expect(document.getElementById(describedBy) ?? wrapper.find(`#${describedBy}`).element.textContent).toMatch(/Enter the Nexus URL/)
    expect(input.attributes('aria-invalid')).toBe('true')
  })
})

describe('the tables', () => {
  it('sorts the loss report by Nexus id from a button in the header, and states the order in aria-sort', async () => {
    const api = fakeApi({
      getJob: vi.fn(async () => dryRunJob()),
      createJob: vi.fn(async () => ({ id: 7, kind: 'DRY_RUN', status: 'QUEUED' })),
      report: vi.fn(async () => ({ rows: lossRows, summary: dryRunJob().summary })),
    })
    const wizard = connectedWizard()
    const { wrapper } = await mountWith(DryRunStep, { api, wizard, at: '/settings/migration/dry-run' })
    await wrapper.find('#migration-dry-run-start').trigger('click')
    await flushPromises()

    const header = wrapper.find('#migration-sort-nexus-id').element.closest('th')
    expect(header.getAttribute('aria-sort')).toBe('none')

    await wrapper.find('#migration-sort-nexus-id').trigger('click')

    expect(wrapper.find('#migration-sort-nexus-id').element.closest('th').getAttribute('aria-sort')).toBe('ascending')
    const firstRow = wrapper.findAll('table[data-test="report"] tbody tr')[0]
    expect(firstRow.text(), 'ascending puts releases first').toContain('releases')
  })
})

describe('the progress of a job', () => {
  it('exposes the overall progress as a labelled bar with its value', async () => {
    const { wrapper } = await mountWith(JobProgress, { props: { job: progressJob(2, 4), label: 'Import progress' } })

    const bar = wrapper.find('[role="progressbar"]')
    expect(bar.attributes('aria-label')).toBe('Import progress')
    expect(bar.attributes('aria-valuenow')).toBe('50')
  })

  it('exposes no value while the job reports no total, so the bar is indeterminate', async () => {
    const { wrapper } = await mountWith(JobProgress, { props: { job: progressJob(0, 0), label: 'Import progress' } })

    expect(wrapper.find('[role="progressbar"]').attributes('aria-valuenow')).toBeUndefined()
  })

  it('announces progress through one polite live region, at most once in ten seconds', async () => {
    const runtime = manualRuntime()
    const { wrapper } = await mountWith(JobProgress, { props: { job: progressJob(0, 100), label: 'Import progress' }, runtime })
    const live = () => wrapper.find('[role="status"]')
    expect(live().attributes('aria-live') ?? 'polite').toBe('polite')

    let last = live().text()
    let changes = 0
    for (let second = 1; second <= 25; second++) {
      await runtime.advance(1000)
      await wrapper.setProps({ job: progressJob(second, 100) })
      const text = live().text()
      if (text !== last) {
        changes += 1
        last = text
      }
    }

    expect(changes, 'a one point a second move is announced at most twice in 25 seconds').toBeLessThanOrEqual(2)
  })

  it('announces no percentage while the total is unknown', async () => {
    const { wrapper } = await mountWith(JobProgress, { props: { job: progressJob(0, 0), label: 'Import progress' } })

    expect(wrapper.find('[role="status"]').text()).not.toContain('%')
    expect(wrapper.find('[role="status"]').text()).not.toMatch(/percent/i)
  })
})
