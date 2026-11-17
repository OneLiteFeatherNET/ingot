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
import KeyValueTree from './KeyValueTree.vue'

type Row = [string, string]

// Twelve service variables of one Kubernetes service, plus a few others: 21 keys in total, above the open threshold.
const serviceRows = (): Row[] => [
  ...['ADDR', 'PORT', 'PROTO', 'ADDR_IPV6', 'ADDR_ZONE', 'PORT_NAME', 'TLS', 'WEIGHT', 'ID', 'NAME', 'MODE', 'LIMIT']
    .map((suffix): Row => [`INGOT_DASHBOARD_PORT_8080_TCP_${suffix}`, '[withheld]']),
  ['PUID', '1000'],
  ['PGID', '1000'],
  ['JAVA_HOME', '/usr/lib/jvm/temurin'],
  ['REPOSILITE_OPTS', '--port=8080'],
  ['TZ', 'Europe/Berlin'],
  ['HOSTNAME', 'ingot-0'],
  ['KUBERNETES_SERVICE_HOST', '10.0.0.1'],
  ['KUBERNETES_SERVICE_PORT', '443'],
  ['LANG', 'C.UTF-8'],
]

const smallRows = (): Row[] => [
  ['INGOT_TOKEN_A', 'one'],
  ['INGOT_TOKEN_B', 'two'],
  ['PUID', '1000'],
]

const mountTree = (props: Partial<{ entries: Row[], split: string, query: string, label: string }> = {}) =>
  mount(KeyValueTree, {
    props: { entries: serviceRows(), split: 'underscore', query: '', label: 'Environment variables', ...props },
  })

const region = (wrapper: ReturnType<typeof mountTree>) => wrapper.find('[role="region"]')
const detailsOf = (wrapper: ReturnType<typeof mountTree>, summary: string) =>
  wrapper.findAll('details').find((item) => item.find('summary').text().includes(summary))
const isOpen = (element: { element: Element } | undefined) => (element?.element as HTMLDetailsElement | undefined)?.open === true
const buttonNamed = (wrapper: ReturnType<typeof mountTree>, name: string) =>
  wrapper.findAll('button').find((button) => button.text() === name)

describe('KeyValueTree', () => {
  it('shows each leaf as a key and a value, with the value exactly as it was given', () => {
    const wrapper = mountTree({ entries: smallRows() })

    const puid = wrapper.findAll('dt').find((term) => term.text() === 'PUID')
    expect(puid?.element.nextElementSibling?.textContent?.trim(), 'the value of PUID is shown as given').toBe('1000')
    expect(wrapper.text(), 'a withheld value stays withheld').toContain('one')
  })

  it('keeps the keys in a monospace font', () => {
    expect(region(mountTree()).classes(), 'the tree uses the monospace font').toContain('font-mono')
  })

  it('exposes the tree as a named, keyboard-focusable region with its own scroll', () => {
    const wrapper = mountTree({ label: 'Environment variables' })

    expect(region(wrapper).attributes('aria-label'), 'the region is named by the label').toBe('Environment variables')
    expect(region(wrapper).attributes('tabindex'), 'the region can take the keyboard focus').toBe('0')
  })

  it('collapses every group when there are more than 20 keys in total', () => {
    const wrapper = mountTree({ entries: serviceRows() })

    const groups = wrapper.findAll('details')
    expect(groups.length, 'the service variables and the Kubernetes variables are groups').toBeGreaterThan(0)
    expect(groups.every((group) => !isOpen(group)), 'no group starts open').toBe(true)
  })

  it('opens every group when there are 20 keys or fewer in total', () => {
    const entries = serviceRows().slice(0, 20)

    const wrapper = mountTree({ entries })

    expect(wrapper.findAll('details').length, 'there are groups to open').toBeGreaterThan(0)
    expect(wrapper.findAll('details').every((group) => isOpen(group)), 'every group starts open').toBe(true)
  })

  it('shows the number of keys below each group next to its label', () => {
    const wrapper = mountTree({ entries: serviceRows() })

    expect(detailsOf(wrapper, 'INGOT_DASHBOARD_PORT_8080_TCP')?.find('summary').text(), 'the service group counts its 12 keys').toContain('12')
  })

  it('opens the path to a match and marks the matching text', () => {
    // given: the collapsed tree of more than 20 keys
    const wrapper = mountTree({ entries: serviceRows(), query: 'TCP_TLS' })

    // then: the group that holds the match is open, and the match is highlighted
    expect(isOpen(detailsOf(wrapper, 'INGOT_DASHBOARD_PORT_8080_TCP')), 'the group on the path to the match opens').toBe(true)
    const marks = wrapper.findAll('mark').map((mark) => mark.text())
    expect(marks, 'the matching text is marked').toContain('TCP_TLS')
  })

  it('finds a value and marks it too', () => {
    const wrapper = mountTree({ entries: serviceRows(), query: 'temurin' })

    expect(wrapper.findAll('mark').map((mark) => mark.text()), 'the matching value is marked').toContain('temurin')
  })

  it('shows "No key matches" when nothing matches the query', () => {
    const wrapper = mountTree({ entries: serviceRows(), query: 'no-such-key-anywhere' })

    expect(wrapper.text(), 'the empty result is announced').toContain('No key matches')
    expect(wrapper.findAll('dt'), 'no leaf is shown').toHaveLength(0)
  })

  it('restores the collapsed state when the query is cleared', async () => {
    // given: a query that opened the group of the match
    const wrapper = mountTree({ entries: serviceRows(), query: 'TCP_TLS' })
    expect(isOpen(detailsOf(wrapper, 'INGOT_DASHBOARD_PORT_8080_TCP')), 'open while the query is active').toBe(true)

    // when: the query is cleared
    await wrapper.setProps({ query: '' })

    // then: the group is collapsed again
    expect(isOpen(detailsOf(wrapper, 'INGOT_DASHBOARD_PORT_8080_TCP')), 'collapsed again after the query is cleared').toBe(false)
  })

  it('opens every group with Expand all', async () => {
    const wrapper = mountTree({ entries: serviceRows() })

    await buttonNamed(wrapper, 'Expand all')?.trigger('click')

    expect(wrapper.findAll('details').every((group) => isOpen(group)), 'every group is open').toBe(true)
  })

  it('closes every group with Collapse all', async () => {
    const wrapper = mountTree({ entries: serviceRows() })
    await buttonNamed(wrapper, 'Expand all')?.trigger('click')

    await buttonNamed(wrapper, 'Collapse all')?.trigger('click')

    expect(wrapper.findAll('details').some((group) => isOpen(group)), 'no group is open').toBe(false)
  })

  it('renders the expand and collapse buttons as text buttons above the tree', () => {
    const wrapper = mountTree()

    expect(buttonNamed(wrapper, 'Expand all'), 'the Expand all button exists').toBeDefined()
    expect(buttonNamed(wrapper, 'Collapse all'), 'the Collapse all button exists').toBeDefined()
  })
})
