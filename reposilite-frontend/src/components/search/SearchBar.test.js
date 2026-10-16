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
import SearchBar from './SearchBar.vue'

const mountBar = (props = {}, slots = {}) =>
  mount(SearchBar, { props: { modelValue: '', label: 'Search things', ...props }, slots })

describe('SearchBar', () => {
  it('is a search landmark', () => {
    const wrapper = mountBar()

    expect(wrapper.element.tagName).toBe('FORM')
    expect(wrapper.attributes('role')).toBe('search')
  })

  it('labels the input and uses the label as its placeholder', () => {
    const input = mountBar({ label: 'Search by id' }).find('input')

    expect(input.attributes('aria-label')).toBe('Search by id')
    expect(input.attributes('placeholder')).toBe('Search by id')
  })

  it('shows the value it is given', () => {
    const input = mountBar({ modelValue: 'nightly' }).find('input')

    expect(input.element.value).toBe('nightly')
  })

  it('reports every keystroke as an update of the value', async () => {
    const wrapper = mountBar()

    await wrapper.find('input').setValue('nightly')

    expect(wrapper.emitted('update:modelValue')).toEqual([['nightly']])
  })

  it('offers no clear button while the box is empty', () => {
    expect(mountBar().find('button[aria-label="Clear search"]').exists()).toBe(false)
  })

  it('clears the value through the clear button', async () => {
    const wrapper = mountBar({ modelValue: 'nightly' })

    await wrapper.find('button[aria-label="Clear search"]').trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([['']])
  })

  it('does not reload the page when the form is submitted', async () => {
    const wrapper = mountBar({ modelValue: 'nightly' })
    const event = new Event('submit', { cancelable: true })

    wrapper.element.dispatchEvent(event)

    expect(event.defaultPrevented, 'a submit must not navigate away').toBe(true)
  })

  it('renders its default slot under the bar', () => {
    const wrapper = mountBar({}, { default: '<div data-test="under-bar">chips</div>' })

    expect(wrapper.find('[data-test="under-bar"]').exists()).toBe(true)
  })

  it('reports a submit, so a page can run the query on Enter', async () => {
    const wrapper = mountBar({ modelValue: 'nightly' })

    await wrapper.trigger('submit')

    expect(wrapper.emitted('submit')).toHaveLength(1)
  })

  it('hands the key presses of the input to the page', async () => {
    const wrapper = mountBar()

    await wrapper.find('input').trigger('keydown', { key: 'ArrowDown' })

    expect(wrapper.emitted('keydown')?.[0][0].key).toBe('ArrowDown')
  })

  it('lets the page focus the input', () => {
    const wrapper = mount(SearchBar, {
      props: { modelValue: '', label: 'Search things' },
      attachTo: document.body
    })

    wrapper.vm.focus()

    expect(document.activeElement).toBe(wrapper.find('input').element)
    wrapper.unmount()
  })

  it('lets the page take the focus out of the input', () => {
    const wrapper = mount(SearchBar, {
      props: { modelValue: '', label: 'Search things' },
      attachTo: document.body
    })
    wrapper.vm.focus()

    wrapper.vm.blur()

    expect(document.activeElement).not.toBe(wrapper.find('input').element)
    wrapper.unmount()
  })
})
