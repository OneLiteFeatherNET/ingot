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

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import JobsView from './JobsView.vue'
import JobDetailView from './JobDetailView.vue'

// The client is swapped per test through this holder. The views read it through the session.
const session = vi.hoisted(() => ({ client: null as unknown as ReturnType<typeof fakeClient> }))

vi.mock('../../store/session', () => ({
  useSession: () => ({ client: { get value() { return session.client } } })
}))

const job = (overrides = {}) => ({
  id: 'index:412',
  source: 'index',
  kind: 'index.verify',
  label: 'Verify',
  trigger: 'MANUAL',
  triggerDetail: null,
  state: 'FAILED',
  startedAt: '2026-11-10T10:00:00Z',
  finishedAt: '2026-11-10T10:05:00Z',
  durationMs: 300000,
  counts: [],
  failures: [],
  failuresTruncated: false,
  ...overrides
})

const page = (items: unknown[], nextCursor: string | null = null) => ({
  items,
  nextCursor,
  sourceErrors: [],
  kinds: ['index.rebuild', 'index.verify', 'cleanup.policy']
})

const fakeClient = (overrides = {}) => ({
  jobs: {
    list: vi.fn(() => Promise.resolve({ data: page([job()]) })),
    detail: vi.fn(() => Promise.resolve({ data: job() })),
    ...overrides
  }
})

const routes = [
  { name: 'jobs', path: '/settings/jobs', component: { render: () => null } },
  { name: 'job', path: '/settings/jobs/:id', component: { render: () => null } }
]

let wrapper: VueWrapper | undefined

const mountAt = async (address: string) => {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(address)
  await router.isReady()
  wrapper = mount(JobsView, { global: { plugins: [router] } }) as VueWrapper
  await flushPromises()
  return { router, wrapper }
}

const buttonNamed = (text: string) => wrapper!.findAll('button').find(button => button.text() === text)

beforeEach(() => {
  session.client = fakeClient()
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('JobsView address and first page', () => {
  it('reads the kind and the state from the address and asks the server for them', async () => {
    await mountAt('/settings/jobs?kind=index&state=FAILED')

    expect(session.client.jobs.list).toHaveBeenCalledWith(
      expect.objectContaining({ kind: 'index', state: 'FAILED' })
    )
  })

  it('shows the state of each run of the first page', async () => {
    session.client = fakeClient({
      list: vi.fn(() => Promise.resolve({ data: page([job({ id: 'cleanup:3', source: 'cleanup', label: 'Cleanup: nightly', state: 'PARTIAL' })]) }))
    })
    await mountAt('/settings/jobs')

    expect(wrapper!.text()).toContain('Cleanup: nightly')
    expect(wrapper!.text()).toContain('Partly failed')
  })

  it('draws no Cancel action on the list', async () => {
    await mountAt('/settings/jobs')

    expect(wrapper!.findAll('button').some(button => /cancel/i.test(button.text()))).toBe(false)
  })
})

describe('JobsView paging', () => {
  it('appends the next page with the cursor of the first and does not load the first page again', async () => {
    session.client = fakeClient({
      list: vi.fn((params: Record<string, string>) => Promise.resolve({
        data: params.cursor
          ? page([job({ id: 'index:300', label: 'Rebuild' })])
          : page([job({ id: 'index:412' })], 'cursor-1')
      }))
    })
    await mountAt('/settings/jobs?kind=index')

    await buttonNamed('Load more')!.trigger('click')
    await flushPromises()

    const calls = session.client.jobs.list.mock.calls as unknown as Array<[Record<string, string>]>
    expect(calls.map(call => call[0].cursor), 'the cursor of each request').toEqual([undefined, 'cursor-1'])
    expect(wrapper!.findAll('[data-job-id]').map(item => item.attributes('data-job-id'))).toEqual(['index:412', 'index:300'])
  })

  it('draws no Load more control when the server has no next page', async () => {
    await mountAt('/settings/jobs')

    expect(buttonNamed('Load more')).toBeUndefined()
  })
})

describe('Jobs pages window classes', () => {
  it('lays the filters out in three columns from the medium window class on the list', async () => {
    const { wrapper } = await mountAt('/settings/jobs')

    const classes = wrapper.find('#jobs-kind').element.closest('.grid')!.className.split(/\s+/)
    expect(classes, 'three columns from the medium window class').toContain('medium:grid-cols-3')
    expect(classes, 'no default Tailwind breakpoint').not.toContain('sm:grid-cols-3')
  })

  it('lays the facts out in two columns from the medium window class on the detail', async () => {
    wrapper = mount(JobDetailView, { props: { id: 'index:412' }, global: { stubs: { RouterLink: RouterLinkStub } } }) as VueWrapper
    await flushPromises()

    const classes = wrapper.find('dl').classes()
    expect(classes, 'two columns from the medium window class').toContain('medium:grid-cols-2')
    expect(classes, 'no default Tailwind breakpoint').not.toContain('sm:grid-cols-2')
  })
})

describe('JobDetailView failures', () => {
  it('lists the failures of a run and notes that more exist', async () => {
    session.client = fakeClient({
      detail: vi.fn(() => Promise.resolve({
        data: job({
          failures: [{ message: 'net/x/app/0.9-SNAPSHOT: locked' }],
          failuresTruncated: true
        })
      }))
    })
    wrapper = mount(JobDetailView, { props: { id: 'index:412' }, global: { stubs: { RouterLink: RouterLinkStub } } }) as VueWrapper
    await flushPromises()

    expect(wrapper.text()).toContain('net/x/app/0.9-SNAPSHOT: locked')
    expect(wrapper.text()).toContain('Only the first 50 failures are listed.')
  })

  it('draws no Cancel action on the detail of a running run', async () => {
    session.client = fakeClient({
      detail: vi.fn(() => Promise.resolve({ data: job({ state: 'RUNNING', finishedAt: null, durationMs: null }) }))
    })
    wrapper = mount(JobDetailView, { props: { id: 'index:412' }, global: { stubs: { RouterLink: RouterLinkStub } } }) as VueWrapper
    await flushPromises()

    expect(wrapper.text()).toContain('Running')
    expect(wrapper.findAll('button').some(button => /cancel/i.test(button.text()))).toBe(false)
  })
})
