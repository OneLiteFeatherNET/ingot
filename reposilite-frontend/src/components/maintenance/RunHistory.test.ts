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
import { mount, RouterLinkStub } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import RunHistory from './RunHistory.vue'
import { normalizeRun } from './cleanup'

let wrapper: VueWrapper

const run = (id: number, policyId: string, startedAt: string, overrides = {}) => normalizeRun({
  id: String(id),
  policyId,
  trigger: 'MANUAL',
  startedAt,
  finishedAt: startedAt,
  status: 'SUCCEEDED',
  deletedCount: 0,
  deletedBytes: 0,
  failures: [],
  ...overrides
})

const mountHistory = (props = {}) => {
  wrapper = mount(RunHistory, {
    props: { runs: [], policyIds: ['snapshots-weekly', 'mirror-cache-nightly'], ...props },
    global: { stubs: { RouterLink: RouterLinkStub } }
  }) as VueWrapper
  return wrapper
}

const rowIds = () => wrapper.findAll('li').map(item => item.attributes('data-run-id'))

afterEach(() => {
  wrapper?.unmount()
})

describe('RunHistory latest run per policy', () => {
  it('shows only the latest run of each policy, whatever order the runs arrive in', () => {
    mountHistory({
      runs: [
        run(1, 'snapshots-weekly', '2026-10-01T10:00:00Z'),
        run(3, 'snapshots-weekly', '2026-10-03T10:00:00Z'),
        run(2, 'mirror-cache-nightly', '2026-10-02T10:00:00Z'),
        run(4, 'mirror-cache-nightly', '2026-10-01T09:00:00Z')
      ]
    })

    expect(rowIds(), 'one row per policy, the newest run of each').toEqual(['3', '2'])
  })

  it('draws no list of every run', () => {
    mountHistory({
      runs: Array.from({ length: 20 }, (_, index) => run(index + 1, 'snapshots-weekly', `2026-10-${String(index + 1).padStart(2, '0')}T10:00:00Z`))
    })

    expect(rowIds(), 'a single row for the policy with twenty runs').toEqual(['20'])
  })

  it('shows that no run has happened yet when the list is empty', () => {
    mountHistory({ runs: [] })

    expect(wrapper.text()).toContain('No runs yet.')
  })
})

describe('RunHistory rows', () => {
  it('shows the policy, trigger, status and what was deleted', () => {
    mountHistory({
      runs: [run(7, 'snapshots-weekly', '2026-10-14T16:00:00Z', { trigger: 'SCHEDULED', status: 'PARTIAL', deletedCount: 2, deletedBytes: 2048 })]
    })

    const text = wrapper.find('li').text()

    expect(text).toContain('snapshots-weekly')
    expect(text).toContain('Scheduled')
    expect(text).toContain('Partly failed')
    expect(text).toContain('2 artifacts deleted')
  })

  it('keeps the failures folded away until they are asked for', () => {
    mountHistory({
      runs: [run(7, 'snapshots-weekly', '2026-10-14T16:00:00Z', { status: 'PARTIAL', failures: ['net/x/app/0.9-SNAPSHOT: locked'] })]
    })

    const details = wrapper.find('details')

    expect(details.exists()).toBe(true)
    expect(details.element.open).toBe(false)
    expect(details.text()).toContain('net/x/app/0.9-SNAPSHOT: locked')
  })

  it('has no failure list for a run without failures', () => {
    mountHistory({ runs: [run(4, 'snapshots-weekly', '2026-10-01T10:00:00Z')] })

    expect(wrapper.find('details').exists()).toBe(false)
  })
})

describe('RunHistory shared components', () => {
  it('draws the runs as a divided md-list with one md-list-item per policy', () => {
    mountHistory({
      runs: [
        run(3, 'snapshots-weekly', '2026-10-03T10:00:00Z'),
        run(2, 'mirror-cache-nightly', '2026-10-02T10:00:00Z')
      ]
    })

    const items = wrapper.findAll('ul > li[data-run-id]')

    expect(items.map(item => item.attributes('data-run-id')), 'one row per policy').toEqual(['3', '2'])
    expect(items.every(item => item.find('[data-test="list-item-row"]').exists()), 'each run is an md-list-item row').toBe(true)
    expect(items.every(item => item.find('[data-test="item-rule"]').exists()), 'the md-list draws the rule between the rows').toBe(true)
  })

  it('folds the failures into an md-disclosure', () => {
    mountHistory({
      runs: [run(7, 'snapshots-weekly', '2026-10-14T16:00:00Z', { status: 'PARTIAL', failures: ['net/x/app/0.9-SNAPSHOT: locked'] })]
    })

    const summary = wrapper.find('details > summary')

    expect(summary.find('svg[aria-hidden="true"]').exists(), 'the failures sit in the shared disclosure, with its chevron').toBe(true)
    expect(summary.classes(), 'the summary carries the state layer of the shared disclosure').toContain('md-state-layer')
    expect(summary.text(), 'the summary counts the failures').toContain('1 failure')
  })
})

describe('RunHistory link to all runs', () => {
  it('links "All runs" to the Jobs page filtered to the cleanup source', () => {
    mountHistory({ runs: [] })

    const link = wrapper.findComponent(RouterLinkStub)

    expect(link.props('to')).toEqual({ name: 'jobs', query: { kind: 'cleanup' } })
    expect(link.text()).toBe('All runs')
  })

  it('draws no policy select, since the tab shows one run per policy', () => {
    mountHistory({ runs: [] })

    expect(wrapper.find('select').exists()).toBe(false)
  })
})
