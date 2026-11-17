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
import type { KeySplit } from './keyTree'

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

const mountTree = (props: Partial<{ entries: Row[], split: KeySplit, query: string, label: string, title: string }> = {}) =>
  mount(KeyValueTree, {
    props: { entries: serviceRows(), split: 'underscore', query: '', label: 'Environment variables', title: 'Environment', ...props },
  })

type Wrapper = ReturnType<typeof mountTree>
const table = (wrapper: Wrapper) => wrapper.find('[role="table"]')
const rowOf = (wrapper: Wrapper, key: string) => wrapper.findAll('[role="row"]').find((row) => row.find('[role="rowheader"]').text() === key)
// A group is named by its button: its label, then its key count. The button tells whether the group is open.
const groupButton = (wrapper: Wrapper, label: string) =>
  wrapper.findAll('button[aria-expanded]').find((button) => button.text().includes(label))
const isExpanded = (wrapper: Wrapper, label: string) => groupButton(wrapper, label)?.attributes('aria-expanded') === 'true'
const buttonNamed = (wrapper: Wrapper, name: string) => wrapper.findAll('button').find((button) => button.text() === name)

describe('KeyValueTree', () => {
  it('shows each key and its value in one row, with the value exactly as it was given', () => {
    const wrapper = mountTree({ entries: smallRows() })

    const row = rowOf(wrapper, 'PUID')
    expect(row?.findAll('[role="cell"]').map((cell) => cell.text()), 'the value sits in the value column of the same row').toEqual(['1000'])
    expect(wrapper.text(), 'a withheld value stays withheld').toContain('one')
  })

  it('keeps every row of a section in one table, so the values line up in one column', () => {
    const wrapper = mountTree({ entries: smallRows() })

    expect(table(wrapper).exists(), 'the rows sit in one table').toBe(true)
    expect(table(wrapper).findAll('[role="cell"]').length, 'one value cell per key, the group row has none').toBe(3)
  })

  it('keeps the keys in a monospace font', () => {
    expect(table(mountTree()).classes(), 'the table uses the monospace font').toContain('font-mono')
  })

  it('wraps a long value inside its column instead of running past the page', () => {
    const wrapper = mountTree({ entries: [['LONG', 'x'.repeat(400)]] })

    expect(rowOf(wrapper, 'LONG')?.find('[role="cell"]').classes(), 'the value breaks inside its column').toContain('break-all')
  })

  it('shows an empty value as a muted placeholder, not as a blank cell', () => {
    const wrapper = mountTree({ entries: [['plugins', ''], ['PUID', '1000']] })

    const cell = rowOf(wrapper, 'plugins')?.find('[role="cell"]')
    expect(cell?.text(), 'the empty value is named').toBe('(empty)')
    expect(cell?.classes(), 'the placeholder is muted and italic').toEqual(expect.arrayContaining(['italic', 'text-on-surface-variant']))
  })

  it('names the table after its label and gives it no scroll box of its own', () => {
    const wrapper = mountTree({ label: 'Environment variables' })

    expect(table(wrapper).attributes('aria-label'), 'the table is named by the label').toBe('Environment variables')
    expect(table(wrapper).classes(), 'the table does not scroll inside the page').not.toContain('overflow-y-auto')
    expect(table(wrapper).attributes('tabindex'), 'the table is not a second tab stop').toBeUndefined()
  })

  it('collapses every group when there are more than 20 keys in total', () => {
    const wrapper = mountTree({ entries: serviceRows() })

    const groups = wrapper.findAll('button[aria-expanded]')
    expect(groups.length, 'the service variables and the Kubernetes variables are groups').toBeGreaterThan(0)
    expect(groups.every((group) => group.attributes('aria-expanded') === 'false'), 'no group starts open').toBe(true)
  })

  it('opens every group when there are 20 keys or fewer in total', () => {
    const wrapper = mountTree({ entries: serviceRows().slice(0, 20) })

    const groups = wrapper.findAll('button[aria-expanded]')
    expect(groups.length, 'there are groups to open').toBeGreaterThan(0)
    expect(groups.every((group) => group.attributes('aria-expanded') === 'true'), 'every group starts open').toBe(true)
  })

  it('shows the number of keys below each group as a count next to its label', () => {
    const wrapper = mountTree({ entries: serviceRows() })

    expect(groupButton(wrapper, 'INGOT_DASHBOARD_PORT_8080_TCP')?.text(), 'the service group counts its 12 keys').toContain('12 keys')
  })

  it('toggles one group with its own button and shows or hides its keys', async () => {
    const wrapper = mountTree({ entries: serviceRows() })
    expect(rowOf(wrapper, 'INGOT_DASHBOARD_PORT_8080_TCP_TLS'), 'the keys of a collapsed group are hidden').toBeUndefined()

    await groupButton(wrapper, 'INGOT_DASHBOARD_PORT_8080_TCP')?.trigger('click')

    expect(isExpanded(wrapper, 'INGOT_DASHBOARD_PORT_8080_TCP'), 'the group reports that it is open').toBe(true)
    expect(rowOf(wrapper, 'INGOT_DASHBOARD_PORT_8080_TCP_TLS'), 'the keys of the open group are shown').toBeDefined()
  })

  it('opens the path to a match and marks the matching text', () => {
    const wrapper = mountTree({ entries: serviceRows(), query: 'TCP_TLS' })

    expect(isExpanded(wrapper, 'INGOT_DASHBOARD_PORT_8080_TCP'), 'the group on the path to the match opens').toBe(true)
    expect(wrapper.findAll('mark').map((mark) => mark.text()), 'the matching text is marked').toContain('TCP_TLS')
  })

  it('finds a value and marks it too', () => {
    const wrapper = mountTree({ entries: serviceRows(), query: 'temurin' })

    expect(wrapper.findAll('mark').map((mark) => mark.text()), 'the matching value is marked').toContain('temurin')
  })

  it('shows "No key matches" when nothing matches the query', () => {
    const wrapper = mountTree({ entries: serviceRows(), query: 'no-such-key-anywhere' })

    expect(wrapper.text(), 'the empty result is announced').toContain('No key matches')
    expect(wrapper.findAll('[role="rowheader"]'), 'no key is shown').toHaveLength(0)
  })

  it('restores the collapsed state when the query is cleared', async () => {
    const wrapper = mountTree({ entries: serviceRows(), query: 'TCP_TLS' })
    expect(isExpanded(wrapper, 'INGOT_DASHBOARD_PORT_8080_TCP'), 'open while the query is active').toBe(true)

    await wrapper.setProps({ query: '' })

    expect(isExpanded(wrapper, 'INGOT_DASHBOARD_PORT_8080_TCP'), 'collapsed again after the query is cleared').toBe(false)
  })

  it('opens every group with Expand all and closes them with Collapse all', async () => {
    const wrapper = mountTree({ entries: serviceRows() })

    await buttonNamed(wrapper, 'Expand all')?.trigger('click')
    expect(wrapper.findAll('button[aria-expanded]').every((group) => group.attributes('aria-expanded') === 'true'), 'every group is open').toBe(true)

    await buttonNamed(wrapper, 'Collapse all')?.trigger('click')
    expect(wrapper.findAll('button[aria-expanded]').some((group) => group.attributes('aria-expanded') === 'true'), 'no group is open').toBe(false)
  })

  it('puts the title, the key count and the Expand all and Collapse all buttons in the section header', () => {
    const wrapper = mountTree({ entries: serviceRows(), title: 'Environment' })

    const heading = wrapper.find('h2')
    expect(heading.text(), 'the section is headed by its title').toBe('Environment')
    expect(heading.element.parentElement?.textContent, 'the header also holds the key count and the buttons').toEqual(
      expect.stringMatching(/21 keys.*Expand all.*Collapse all/s),
    )
  })
})
