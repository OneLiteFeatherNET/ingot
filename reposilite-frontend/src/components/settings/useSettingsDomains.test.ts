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
import { defineComponent, h, ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { useSettingsDomains } from './useSettingsDomains'
import { ADMIN_NODES } from '../../store/nodeAccess'

// The session is replaced per test through this holder, so each test builds its own client and visitor.
const holder = vi.hoisted(() => ({ can: null, client: null }))

vi.mock('../../store/session', () => ({
  useSession: () => ({ can: holder.can, client: holder.client })
}))

const SCHEMAS = {
  web: { title: 'Web' },
  maven: { title: 'Maven' },
  authentication: { title: 'Authentication' }
}

const fakeClient = () => ({
  settings: {
    domains: vi.fn(async () => ({ data: ['web', 'maven', 'authentication'] })),
    schema: vi.fn(async (domain) => ({ data: SCHEMAS[domain] }))
  }
})

// A component that uses the composable and hands over what it returns, so a test reads the public result only.
const probe = (result) => defineComponent({
  setup() {
    const { domains, titleOf } = useSettingsDomains()
    result.domains = domains
    result.titleOf = titleOf
    return () => h('span')
  }
})

const mountProbe = async () => {
  const result = {}
  const wrapper = mount(probe(result))
  await flushPromises()
  return { result, wrapper }
}

let wrapper
afterEach(() => wrapper?.unmount())

describe('useSettingsDomains', () => {
  it('lists the settings domains the visitor may open, without the ones edited under Access', async () => {
    holder.can = () => true
    holder.client = ref(fakeClient())
    const mounted = await mountProbe()
    wrapper = mounted.wrapper

    expect(mounted.result.domains.value, 'the authentication domain is edited under Access and maven on the Repositories page, so both are left out').toEqual(['web'])
  })

  it('takes the title of a domain from its schema, as the settings page does', async () => {
    holder.can = () => true
    holder.client = ref(fakeClient())
    const mounted = await mountProbe()
    wrapper = mounted.wrapper

    expect(mounted.result.titleOf('web')).toBe('Web')
  })

  it('makes no request for the domains to a visitor without the settings node', async () => {
    const client = fakeClient()
    holder.can = (node) => node !== ADMIN_NODES.settings
    holder.client = ref(client)
    const mounted = await mountProbe()
    wrapper = mounted.wrapper

    expect(client.settings.domains, 'the domain list is not requested without the settings node').not.toHaveBeenCalled()
    expect(mounted.result.domains.value).toEqual([])
  })

  it('requests the domains once for one client, however many pages use the composable', async () => {
    const client = fakeClient()
    holder.can = () => true
    holder.client = ref(client)
    const first = await mountProbe()
    const second = await mountProbe()
    wrapper = first.wrapper
    const other = second.wrapper

    expect(client.settings.domains, 'one request per session').toHaveBeenCalledTimes(1)
    other.unmount()
  })
})
