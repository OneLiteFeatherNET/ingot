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
import { mount } from '@vue/test-utils'
import { h } from 'vue'
import MdCodeBlock from './MdCodeBlock.vue'

const SNIPPET = '<dependency>\n  <groupId>org.example</groupId>\n</dependency>'

// The label is required by the component, so the helper always passes one; a test may name another label.
const mountBlock = (
  { label = 'Maven snippet' }: { label?: string } = {},
  slots: Record<string, () => unknown> = {}
) =>
  mount(MdCodeBlock, {
    props: { label },
    slots: { default: () => SNIPPET, ...slots }
  })

describe('MdCodeBlock content', () => {
  it('keeps the lines of the code as written, inside a pre and code element', () => {
    const wrapper = mountBlock()

    expect(wrapper.find('pre code').text()).toBe(SNIPPET)
  })

  it('uses the monospace type role of the catalogue', () => {
    expect(mountBlock().classes(), 'the block is set in md-code-medium').toContain('md-code-medium')
  })

  it('never wraps a line, so long lines scroll sideways', () => {
    expect(mountBlock().find('[role="region"]').classes()).toContain('whitespace-pre')
  })
})

describe('MdCodeBlock scroll region', () => {
  it('is a named, focusable region that scrolls sideways inside its own box', () => {
    const region = mountBlock({ label: 'Maven snippet' }).find('[role="region"]')

    expect(region.exists(), 'the code sits in a region').toBe(true)
    expect(region.attributes('aria-label'), 'the region is named after its label').toBe('Maven snippet')
    expect(region.attributes('tabindex'), 'a keyboard user can reach the scroll area').toBe('0')
    expect(region.classes()).toContain('overflow-x-auto')
  })
})

describe('MdCodeBlock actions', () => {
  it('renders the actions slot outside the scroll region, so the actions do not scroll away', () => {
    const wrapper = mountBlock({ label: 'Maven snippet' }, { actions: () => h('button', { 'data-test': 'action' }, 'Copy') })

    expect(wrapper.find('[data-test="action"]').exists()).toBe(true)
    expect(wrapper.find('[role="region"] [data-test="action"]').exists(), 'the action is not inside the region').toBe(false)
  })

  it('leaves room on the right of the code for the actions, so they never cover a line', () => {
    const withActions = mountBlock({ label: 'Maven snippet' }, { actions: () => h('button', 'Copy') })
    const withoutActions = mountBlock()

    expect(withActions.find('[role="region"]').classes()).toContain('pr-12')
    expect(withoutActions.find('[role="region"]').classes()).not.toContain('pr-12')
  })
})

describe('MdCodeBlock anchors', () => {
  it('carries the md-code-block anchor on the root element', () => {
    expect(mountBlock().attributes('data-ingot-component')).toBe('md-code-block')
  })
})
