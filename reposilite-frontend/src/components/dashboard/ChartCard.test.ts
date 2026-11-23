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

  it('marks the error panel with the error state, and says the failure once', () => {
    const wrapper = mount(ChartCard, {
      props: { title: 'Traffic', error: 'Cannot load statistics.' },
      slots: { default: plot }
    })

    const alert = wrapper.find('[role="alert"]')
    expect(alert.exists(), 'the failure should be announced as an alert').toBe(true)
    expect(alert.attributes('data-ingot-state'), 'the error panel should carry the error state').toBe('error')
    expect(wrapper.findAll('[role="alert"]'), 'the failure should be announced once').toHaveLength(1)
  })

  it('draws the retry as a tonal button inside the error panel', () => {
    const wrapper = mount(ChartCard, {
      props: { title: 'Traffic', error: 'Cannot load statistics.' },
      slots: { default: plot }
    })

    const retry = wrapper.find('button')
    expect(retry.attributes('data-variant'), 'the retry is a tonal button').toBe('tonal')
    expect(retry.element.closest('[role="alert"]'), 'the retry sits in the error state panel').not.toBeNull()
  })

  it('shows a loading panel with no pulse animation while the data is loading', () => {
    const wrapper = mount(ChartCard, {
      props: { title: 'Traffic', loading: true },
      slots: { default: plot }
    })

    expect(wrapper.find('[aria-busy="true"]').exists(), 'the loading state should be marked busy').toBe(true)
    expect(wrapper.find('.animate-pulse').exists(), 'the placeholder should not pulse').toBe(false)
    expect(wrapper.find('.plot').exists(), 'the plot must not render while loading').toBe(false)
  })

  it('renders the plot when there is no error', () => {
    const wrapper = mount(ChartCard, { props: { title: 'Traffic' }, slots: { default: plot } })

    expect(wrapper.find('.plot').exists(), 'a card without an error should show its content').toBe(true)
  })
})
