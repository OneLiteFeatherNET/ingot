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
import StatTile from './StatTile.vue'

const meterOf = (wrapper) => wrapper.find('[role="meter"]')

describe('StatTile meter', () => {
  it('reports a value above the limit as the full meter', () => {
    const wrapper = mount(StatTile, {
      props: { label: 'Used memory', value: '54 MB', ratio: 2.16, meterText: '54 of 25 MB maximum heap in use' }
    })

    expect(meterOf(wrapper).attributes('aria-valuenow'), 'a meter cannot announce more than its maximum of 100').toBe('100')
    expect(meterOf(wrapper).attributes('aria-valuemax')).toBe('100')
    wrapper.unmount()
  })

  it('reads the value and the limit in words, not only as a percentage', () => {
    const wrapper = mount(StatTile, {
      props: { label: 'Used memory', value: '91.6 MB', ratio: 0.358, meterText: '91.6 of 256 MB maximum heap in use, 36 percent' }
    })

    expect(meterOf(wrapper).attributes('aria-valuetext'), 'the text names both numbers and the unit').toBe('91.6 of 256 MB maximum heap in use, 36 percent')
    wrapper.unmount()
  })

  it('reads a value at the limit as 100', () => {
    const wrapper = mount(StatTile, {
      props: { label: 'Used memory', value: '256 MB', ratio: 1, meterText: '256 of 256 MB maximum heap in use' }
    })

    expect(meterOf(wrapper).attributes('aria-valuenow'), 'a value equal to the limit reads as 100').toBe('100')
    wrapper.unmount()
  })
})
