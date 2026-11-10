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
import SupportView from './SupportView.vue'

// The client is swapped per test through this holder. The view reads it through the session.
const session = vi.hoisted(() => ({ client: null }))

vi.mock('../../store/session', () => ({
  useSession: () => ({ client: { get value() { return session.client } } })
}))

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))

const mountView = (client) => {
  session.client = client
  return mount(SupportView)
}

afterEach(() => {
  session.client = null
})

describe('SupportView', () => {
  it('offers the download of the support bundle', async () => {
    const wrapper = mountView({ support: { availability: vi.fn(async () => ({ data: { enabled: true } })), bundle: vi.fn() } })
    await flushPromises()

    const button = wrapper.findAll('button').find(item => item.text().includes('Download support bundle'))
    expect(button, 'download button').toBeDefined()
  })

  it('states that secrets are removed and that the zip is still read before it is attached', async () => {
    const wrapper = mountView({ support: { availability: vi.fn(async () => ({ data: { enabled: true } })) } })
    await flushPromises()

    expect(wrapper.text()).toContain('Secrets are removed or masked')
    expect(wrapper.text()).toContain('Read the zip before you attach it')
  })

  it('says the pages are turned off and offers no download when the feature is off', async () => {
    const wrapper = mountView({ support: { availability: vi.fn(async () => ({ data: { enabled: false } })) } })
    await flushPromises()

    expect(wrapper.text()).toContain('turned off')
    const button = wrapper.findAll('button').find(item => item.text().includes('Download support bundle'))
    expect(button, 'download button with the feature off').toBeUndefined()
  })
})
