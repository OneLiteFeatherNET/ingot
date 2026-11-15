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

import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import SystemInfoView from './SystemInfoView.vue'

// The client is swapped per test through this holder. The view reads it through the session.
const session = vi.hoisted(() => ({ client: null }))

vi.mock('../../store/session', () => ({
  useSession: () => ({ client: { get value() { return session.client } } })
}))

const FIXTURE = {
  instance: 'ingot-node-1',
  generatedAt: '2026-11-10T10:00:00Z',
  redactionRuleVersion: 1,
  ingot: { version: '1.2.3', buildTime: 'unknown', commit: 'unknown' },
  jvm: { vendor: 'Eclipse Adoptium', version: '21.0.4', uptimeMillis: 5000, heapUsedMb: 120.5, heapMaxMb: 512, processors: 4, threads: 30, inputArguments: ['-Xmx512m', '-Dfoo.password=[withheld]'] },
  operatingSystem: { name: 'Linux', version: '6.8', architecture: 'amd64', container: true },
  database: { kind: 'mariadb', endpoint: 'db.example:3306' },
  storage: [{ repository: 'releases', kind: 's3', region: 'eu-central-1' }],
  cluster: null,
  plugins: [{ name: 'metrics', version: '1.2.3', dependencies: ['status'] }],
  environment: { PUID: '1000', PLANTED_ENV: '[withheld]' },
  systemProperties: { 'ingot.supportBundle.enabled': 'true' },
  configuration: {
    local: { hostname: '0.0.0.0', keyPassword: '[withheld]' },
    shared: { storage: { type: 's3', secretKey: '********' } }
  }
}

const mountView = (client) => {
  session.client = client
  return mount(SystemInfoView)
}

afterEach(() => {
  session.client = null
})

describe('SystemInfoView', () => {
  it('renders every section of the page from the system information', async () => {
    // when: the page is opened
    const wrapper = mountView({ support: { systemInfo: vi.fn(async () => ({ data: FIXTURE })), availability: vi.fn() } })
    await flushPromises()

    // then: each section has its heading
    const headings = wrapper.findAll('h2').map(heading => heading.text())
    expect(headings).toEqual(expect.arrayContaining([
      'Ingot', 'Java', 'Operating system', 'Database', 'Storage', 'Cluster', 'Plugins', 'Environment', 'System properties', 'Configuration'
    ]))
  })

  it('shows the withheld values as written, never a value that was not sent', async () => {
    const wrapper = mountView({ support: { systemInfo: vi.fn(async () => ({ data: FIXTURE })) } })
    await flushPromises()

    expect(wrapper.text()).toContain('[withheld]')
    expect(wrapper.text()).toContain('mariadb')
    expect(wrapper.text()).toContain('db.example:3306')
  })

  it('tells the visitor that the pages are turned off when the server answers 404', async () => {
    const wrapper = mountView({
      support: { systemInfo: vi.fn(async () => Promise.reject({ response: { status: 404 } })) }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('turned off')
  })
})
