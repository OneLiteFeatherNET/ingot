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

import { describe, expect, it } from 'vitest'
import { h } from 'vue'
import { mount } from '@vue/test-utils'
import MdDisclosure from './MdDisclosure.vue'

// The disclosure is a details element, so the browser owns the toggle. These tests drive the element's open state and
// its toggle event directly, which is what a browser does when the summary is activated.
const mountDisclosure = (props: Record<string, unknown> = {}) =>
  mount(MdDisclosure, {
    props,
    slots: {
      summary: () => 'Advanced options',
      default: () => h('p', 'Retention and quotas'),
    },
  })

describe('MdDisclosure content', () => {
  it('shows the summary text in the summary element', () => {
    const wrapper = mountDisclosure()

    expect(wrapper.find('summary').text()).toContain('Advanced options')
  })

  it('keeps the body closed by default', () => {
    const wrapper = mountDisclosure()

    expect(wrapper.find('details').element.open, 'the body starts closed').toBe(false)
  })

  it('opens the body when open is true', () => {
    const wrapper = mountDisclosure({ open: true })

    expect(wrapper.find('details').element.open).toBe(true)
    expect(wrapper.text()).toContain('Retention and quotas')
  })

  it('draws a decorative chevron in the summary, hidden from assistive technology', () => {
    const wrapper = mountDisclosure()

    const chevron = wrapper.find('summary svg')

    expect(chevron.exists(), 'the summary carries a chevron').toBe(true)
    expect(chevron.attributes('aria-hidden')).toBe('true')
  })
})

describe('MdDisclosure value', () => {
  it('emits update:open with true when the browser opens the body', async () => {
    const wrapper = mountDisclosure({ open: false })
    const details = wrapper.find('details')

    details.element.open = true
    await details.trigger('toggle')

    expect(wrapper.emitted('update:open')).toEqual([[true]])
  })

  it('emits update:open with false when the browser closes the body', async () => {
    const wrapper = mountDisclosure({ open: true })
    const details = wrapper.find('details')

    details.element.open = false
    await details.trigger('toggle')

    expect(wrapper.emitted('update:open')).toEqual([[false]])
  })
})

describe('MdDisclosure anchors', () => {
  it('carries the md-disclosure anchor on the root element', () => {
    const wrapper = mountDisclosure()

    expect(wrapper.attributes('data-ingot-component')).toBe('md-disclosure')
  })
})
