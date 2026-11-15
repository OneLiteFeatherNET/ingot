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
import { h } from 'vue'
import { mount } from '@vue/test-utils'
import ChartCard from './ChartCard.vue'

const plot = () => h('p', { class: 'plot' }, 'plot')

describe('ChartCard', () => {
  it('shows the error in place of the plot', () => {
    const wrapper = mount(ChartCard, {
      props: { title: 'Traffic', error: 'Cannot load statistics.' },
      slots: { default: plot }
    })

    expect(wrapper.text(), 'the error message should be visible').toContain('Cannot load statistics.')
    expect(wrapper.find('.plot').exists(), 'the plot must not render while the card is in error').toBe(false)
  })

  it('offers a retry button while in error', () => {
    const wrapper = mount(ChartCard, {
      props: { title: 'Traffic', error: 'Cannot load statistics.' },
      slots: { default: plot }
    })

    expect(wrapper.find('button').text(), 'the error state should offer a retry').toBe('Retry')
  })

  it('emits retry when the retry button is pressed', async () => {
    const wrapper = mount(ChartCard, {
      props: { title: 'Traffic', error: 'Cannot load statistics.' },
      slots: { default: plot }
    })

    await wrapper.find('button').trigger('click')

    expect(wrapper.emitted('retry'), 'pressing retry should ask the owner to load again').toHaveLength(1)
  })

  it('renders the plot when there is no error', () => {
    const wrapper = mount(ChartCard, { props: { title: 'Traffic' }, slots: { default: plot } })

    expect(wrapper.find('.plot').exists(), 'a card without an error should show its content').toBe(true)
  })
})
