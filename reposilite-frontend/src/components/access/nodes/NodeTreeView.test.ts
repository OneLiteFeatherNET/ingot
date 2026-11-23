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
import type { Component } from 'vue'
import { mount } from '@vue/test-utils'
import NodeTreeView from './NodeTreeView.vue'
import MdTransition from '../../md/MdTransition.vue'
import { buildTree } from './nodeTree'

const rows = () => [
  { node: 'ingot.repository.releases.read', allowed: true },
  { node: 'ingot.repository.releases.deploy', allowed: false },
  { node: 'ingot.admin.users', allowed: true },
]

const mountTree = (props: Record<string, unknown> = {}) =>
  mount(NodeTreeView, {
    props: { items: buildTree(rows()), ...props },
    // The test utilities stub Vue <Transition>, so the real one is mounted, as in the browser.
    global: { stubs: { transition: false } },
  })

const group = (wrapper: ReturnType<typeof mountTree>, key: string) => wrapper.find<HTMLDetailsElement>(`[data-key="${key}"] > details`)

describe('NodeTreeView expand motion', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('opens a group with the expand pattern and no appear', () => {
    const wrapper = mountTree({ expandAll: true })

    const transition = wrapper.findComponent(MdTransition as Component) as unknown as { props: (key: string) => unknown }
    expect(transition.props('pattern'), 'a group uses the expand pattern').toBe('expand')
    expect(transition.props('appear'), 'a group that is open at load has no transition on the first paint').toBe(false)
  })

  it('wraps the rows of a group in the clipping grid of the expand pattern', () => {
    const wrapper = mountTree({ expandAll: true })

    const body = group(wrapper, 'ingot').find(':scope > div')
    expect(body.classes(), 'the wrapper is the one-row grid').toContain('expand-rows')
    expect(body.element.firstElementChild?.classList.contains('overflow-hidden'), 'the row clips while it is shorter').toBe(true)
  })

  it('turns the chevron on the short3 duration token', () => {
    const wrapper = mountTree({ expandAll: true })

    expect(group(wrapper, 'ingot').find('summary svg').classes()).toContain('duration-md-short3')
  })

  it('turns the chevron when the group is clicked open', async () => {
    const wrapper = mountTree({ expandAll: false })
    const summary = group(wrapper, 'ingot').find('summary')
    expect(summary.find('svg').classes(), 'a closed group points right').not.toContain('rotate-90')

    await summary.trigger('click')

    expect(group(wrapper, 'ingot').element.open, 'the group opens').toBe(true)
    expect(summary.find('svg').classes(), 'an open group points down').toContain('rotate-90')
  })

  it('keeps the group open until its leave has ended', async () => {
    vi.useFakeTimers()
    const wrapper = mountTree({ expandAll: true })

    await group(wrapper, 'ingot').find('summary').trigger('click')
    expect(group(wrapper, 'ingot').element.open, 'the rows stay visible while the group collapses').toBe(true)

    await vi.runAllTimersAsync()
    expect(group(wrapper, 'ingot').element.open, 'the group closes when the leave has ended').toBe(false)
  })

  it('keeps the path, the node count and the state in the summary', () => {
    const wrapper = mountTree({ expandAll: true })

    const summary = group(wrapper, 'ingot').find('summary')
    expect(summary.text()).toContain('ingot')
    expect(summary.text()).toContain('3 nodes')
    expect(summary.find('[data-test="tree-state"]').text()).toBe('Mixed')
  })
})
