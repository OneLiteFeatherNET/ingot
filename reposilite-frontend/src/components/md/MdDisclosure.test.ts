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

import { afterEach, describe, expect, it, vi } from 'vitest'
import { h } from 'vue'
import type { Component } from 'vue'
import { mount } from '@vue/test-utils'
import MdDisclosure from './MdDisclosure.vue'
import MdTransition from './MdTransition.vue'

// The disclosure is a details element, so the browser owns the toggle. These tests drive the element's open state and
// its toggle event directly, which is what a browser does when the summary is activated.
const mountDisclosure = (props: Record<string, unknown> = {}) =>
  mount(MdDisclosure, {
    props,
    // The test utilities stub Vue <Transition>, so the real one is mounted, as in the browser.
    global: { stubs: { transition: false } },
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

  it('draws the state layer on the summary, so hover, focus and press show on the control', () => {
    const wrapper = mountDisclosure()

    expect(wrapper.find('summary').classes(), 'the summary carries the md state layer').toContain('md-state-layer')
  })

  it('rounds the summary, so its state layer has a shape', () => {
    const wrapper = mountDisclosure()

    expect(wrapper.find('summary').classes(), 'the state layer takes the corner of the summary').toContain('rounded-corner-small')
  })
})

describe('MdDisclosure native semantics', () => {
  it('is a native details element and sets no aria-expanded, because the semantics are native', () => {
    const wrapper = mountDisclosure()

    expect(wrapper.element instanceof HTMLDetailsElement, 'the root is a native details').toBe(true)
    expect(wrapper.find('[aria-expanded]').exists(), 'nothing duplicates the native state').toBe(false)
  })

  it('toggles the open attribute of the details element with the open prop', async () => {
    const wrapper = mountDisclosure()
    expect(wrapper.attributes('open'), 'closed has no open attribute').toBeUndefined()

    await wrapper.setProps({ open: true })

    expect(wrapper.attributes('open'), 'open sets the attribute').toBeDefined()
  })

  it('hides the content while collapsed', () => {
    const wrapper = mountDisclosure()

    expect(wrapper.find('details > div').isVisible(), 'the body is not visible while collapsed').toBe(false)
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

describe('MdDisclosure focus', () => {
  it('keeps the focus on the summary when the body opens and closes', async () => {
    const wrapper = mount(MdDisclosure, {
      attachTo: document.body,
      slots: { summary: () => 'Advanced options', default: () => h('p', 'Retention and quotas') },
    })
    const summary = wrapper.find('summary').element as HTMLElement
    const details = wrapper.find('details')

    summary.focus()
    details.element.open = true
    await details.trigger('toggle')
    expect(document.activeElement, 'opening keeps the focus on the control').toBe(summary)

    details.element.open = false
    await details.trigger('toggle')
    expect(document.activeElement, 'closing keeps the focus on the control').toBe(summary)
    wrapper.unmount()
  })
})

describe('MdDisclosure anchors', () => {
  it('carries the md-disclosure anchor on the root element', () => {
    const wrapper = mountDisclosure()

    expect(wrapper.attributes('data-ingot-component')).toBe('md-disclosure')
  })
})

describe('MdDisclosure expand motion', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  const click = async (wrapper: ReturnType<typeof mountDisclosure>) => {
    await wrapper.find('summary').trigger('click')
  }

  // The leave of the transition ends on the next frame in jsdom, which has no stylesheet.
  const endTransition = async () => {
    await vi.runAllTimersAsync()
  }

  it('wraps the body in MdTransition with the expand pattern and no appear', () => {
    const wrapper = mountDisclosure({ open: true })

    const transition = wrapper.findComponent(MdTransition as Component) as unknown as { props: (key: string) => unknown }
    expect(transition.props('pattern'), 'the body uses the expand pattern').toBe('expand')
    expect(transition.props('appear'), 'a section that is open at load has no transition on the first paint').toBe(false)
  })

  it('puts the body in a grid wrapper that clips it, so the rows can go from 0fr to 1fr', () => {
    const wrapper = mountDisclosure({ open: true })

    const grid = wrapper.find('details > div')
    expect(grid.classes(), 'the wrapper is a one-row grid, which the expand classes take to 0fr').toEqual(['expand-rows'])
    expect(grid.text()).toContain('Retention and quotas')
    expect(grid.element.firstElementChild?.classList.contains('overflow-hidden'), 'the row clips the body while it is shorter').toBe(true)
  })

  it('turns the chevron on the short3 duration token', () => {
    const wrapper = mountDisclosure()

    expect(wrapper.find('summary svg').classes()).toContain('duration-md-short3')
  })

  it('opens the body and emits true when the summary is clicked', async () => {
    const wrapper = mountDisclosure()

    await click(wrapper)

    expect(wrapper.find('details').element.open, 'the details opens at once').toBe(true)
    expect(wrapper.find('details > div').isVisible(), 'the body is inserted for the enter transition').toBe(true)
    expect(wrapper.emitted('update:open')).toEqual([[true]])
  })

  it('keeps the open attribute of a closing disclosure until the transition has ended', async () => {
    vi.useFakeTimers()
    const wrapper = mountDisclosure({ open: true })

    await click(wrapper)

    expect(wrapper.find('details').element.open, 'the body stays visible while it collapses').toBe(true)
    expect(wrapper.emitted('update:open')).toEqual([[false]])
    await endTransition()
    expect(wrapper.find('details').element.open, 'the details closes when the leave has ended').toBe(false)
  })

  it('reopens the section on a second toggle during the close, and keeps it open', async () => {
    vi.useFakeTimers()
    // Like v-model:open, the parent hands the emitted value back as the prop.
    const wrapper: ReturnType<typeof mountDisclosure> = mountDisclosure({
      open: true,
      'onUpdate:open': (open: boolean) => wrapper.setProps({ open }),
    })

    await click(wrapper)
    await click(wrapper)
    await endTransition()

    expect(wrapper.find('details').element.open, 'the section stays open after the reopen').toBe(true)
    expect(wrapper.text()).toContain('Retention and quotas')
    expect(wrapper.emitted('update:open')).toEqual([[false], [true]])
  })

  it('closes after its prop goes to false, once the transition has ended', async () => {
    vi.useFakeTimers()
    const wrapper = mountDisclosure({ open: true })

    await wrapper.setProps({ open: false })
    await endTransition()

    expect(wrapper.find('details').element.open).toBe(false)
    expect(wrapper.emitted('update:open'), 'a change of the prop is not echoed').toBeUndefined()
  })
})
