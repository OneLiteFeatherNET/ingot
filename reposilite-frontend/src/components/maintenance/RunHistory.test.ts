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
import { mount } from '@vue/test-utils'
import RunHistory from './RunHistory.vue'
import { normalizeRun } from './cleanup'

let wrapper

const run = (id, policyId, startedAt, overrides = {}) => normalizeRun({
  id,
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
    props: { runs: [], policyIds: ['snapshots-weekly', 'mirror-cache-nightly'], policy: '', ...props }
  })
  return wrapper
}

const rowIds = () => wrapper.findAll('li').map(item => item.attributes('data-run-id'))

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('RunHistory order', () => {
  it('lists the newest run first, whatever order the runs arrive in', () => {
    mountHistory({
      runs: [
        run(1, 'snapshots-weekly', '2026-10-01T10:00:00Z'),
        run(3, 'snapshots-weekly', '2026-10-03T10:00:00Z'),
        run(2, 'mirror-cache-nightly', '2026-10-02T10:00:00Z')
      ]
    })

    expect(rowIds()).toEqual(['3', '2', '1'])
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

describe('RunHistory filter', () => {
  it('shows only the runs of the policy it is filtered to', () => {
    mountHistory({
      runs: [
        run(1, 'snapshots-weekly', '2026-10-01T10:00:00Z'),
        run(2, 'mirror-cache-nightly', '2026-10-02T10:00:00Z')
      ],
      policy: 'mirror-cache-nightly'
    })

    expect(rowIds()).toEqual(['2'])
  })

  it('reports the chosen policy so the parent can keep the filter', async () => {
    mountHistory({ runs: [] })
    const select = wrapper.find('select')

    select.element.value = 'snapshots-weekly'
    await select.trigger('change')

    expect(wrapper.emitted('update:policy')[0]).toEqual(['snapshots-weekly'])
  })
})
