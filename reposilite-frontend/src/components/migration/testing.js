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

/*
 * Test fixtures of the migration components. Every call returns fresh fakes, so no test sees another
 * test's answers, timers or stored state. Nothing here is imported by the application.
 */

import { vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createWizardStore } from '../../store/migration/wizardStore'
import { MIGRATION_API, MIGRATION_WIZARD, MIGRATION_RUNTIME } from '../../store/migration/runtime'

const okConnection = (overrides = {}) => ({
  reachable: true,
  statusCode: 200,
  version: '3.70.1',
  edition: 'OSS',
  credentialAccepted: true,
  canListRepositories: true,
  canReadSettings: true,
  warnings: [],
  problem: null,
  ...overrides,
})

const discovery = (overrides = {}) => ({
  repositories: [
    { name: 'releases', format: 'maven2', type: 'hosted', supported: true, components: 120, bytes: 2048, settingsReadable: true },
    { name: 'snapshots', format: 'maven2', type: 'hosted', supported: true, components: 4, bytes: 512, settingsReadable: true },
    { name: 'npm-proxy', format: 'npm', type: 'proxy', supported: false, components: 9, bytes: 300, settingsReadable: false },
  ],
  formats: {
    maven2: { repositories: 2, components: 124, bytes: 2560 },
    npm: { repositories: 1, components: 9, bytes: 300 },
  },
  ...overrides,
})

// Every API call is a fake that answers with the value of the test, or the default above.
const fakeApi = (overrides = {}) => ({
  testConnection: vi.fn(async () => okConnection()),
  discover: vi.fn(async () => discovery()),
  createJob: vi.fn(async (job) => ({ id: 1, kind: job.kind, status: 'QUEUED' })),
  listJobs: vi.fn(async () => []),
  getJob: vi.fn(async () => { throw new Error('no job in this test') }),
  pauseJob: vi.fn(async () => undefined),
  resumeJob: vi.fn(async () => undefined),
  cancelJob: vi.fn(async () => undefined),
  supplyCredentials: vi.fn(async () => undefined),
  report: vi.fn(async () => ({ rows: [], summary: null })),
  conflicts: vi.fn(async () => []),
  deleteJob: vi.fn(async () => undefined),
  ...overrides,
})

// Timers that run only when the test advances them, and a tab the test can hide.
const manualRuntime = () => {
  let now = 0
  let nextId = 1
  const pending = new Map()
  let hidden = false
  const listeners = new Set()
  return {
    now: () => now,
    timers: {
      setTimeout: (callback, ms) => {
        const id = nextId++
        pending.set(id, { at: now + ms, callback })
        return id
      },
      clearTimeout: (id) => pending.delete(id),
    },
    visibility: {
      isHidden: () => hidden,
      subscribe: (listener) => {
        listeners.add(listener)
        return () => listeners.delete(listener)
      },
    },
    setHidden(value) {
      hidden = value
      listeners.forEach(listener => listener())
    },
    pendingCount: () => pending.size,
    async advance(ms) {
      const target = now + ms
      for (;;) {
        const due = [...pending.entries()].filter(([, entry]) => entry.at <= target).sort(([, a], [, b]) => a.at - b.at)[0]
        if (!due) break
        pending.delete(due[0])
        now = due[1].at
        due[1].callback()
        await flushPromises()
      }
      now = target
      await flushPromises()
    },
  }
}

const mounted = []

// Mounts a component with the migration routes, the fakes injected, and the address given. Call `cleanup`
// after each test so no mounted page outlives its test.
const mountWith = async (component, {
  props = {},
  api = fakeApi(),
  wizard = createWizardStore(),
  runtime = manualRuntime(),
  at = '/settings/migration/connect',
  attachTo = undefined,
} = {}) => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { name: 'settings-migration', path: '/settings/migration/:step?', component: { render: () => null } },
      { name: 'not-found', path: '/:pathMatch(.*)*', component: { render: () => null } },
    ],
  })
  await router.push(at)
  await router.isReady()
  const wrapper = mount(component, {
    props,
    attachTo,
    global: {
      plugins: [router],
      provide: {
        [MIGRATION_API]: api,
        [MIGRATION_WIZARD]: wizard,
        [MIGRATION_RUNTIME]: runtime,
      },
    },
  })
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, api, wizard, runtime, router }
}

const cleanup = () => {
  mounted.splice(0).forEach(wrapper => wrapper.unmount())
}

export { fakeApi, manualRuntime, mountWith, cleanup, okConnection, discovery }
