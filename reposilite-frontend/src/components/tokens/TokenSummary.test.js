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

import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import TokenSummary from './TokenSummary.vue'

// Fixed reference time, so the relative expiry text does not depend on the day the test runs.
const NOW = Date.UTC(2026, 0, 1)

const token = (overrides = {}) => ({
  name: 'ci',
  identifier: { type: 'PERSISTENT', value: 1 },
  createdAt: [2025, 12, 1],
  expiresAt: null,
  description: '',
  permissions: [],
  routes: [],
  ...overrides
})

describe('TokenSummary', () => {
  it('shows the remaining lifetime as visible text', () => {
    const wrapper = mount(TokenSummary, {
      props: { token: token({ expiresAt: [2026, 1, 4] }), now: NOW }
    })

    expect(wrapper.text()).toContain('3 days left')
  })

  it('shows that an expired token has expired', () => {
    const wrapper = mount(TokenSummary, {
      props: { token: token({ expiresAt: [2025, 12, 31] }), now: NOW }
    })

    expect(wrapper.text()).toContain('expired')
  })

  it('shows that a token without an expiry never expires', () => {
    const wrapper = mount(TokenSummary, { props: { token: token(), now: NOW } })

    expect(wrapper.text()).toContain('never expires')
  })
})
