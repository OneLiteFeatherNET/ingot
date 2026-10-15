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

import { describe, it, expect, afterEach } from 'vitest'
import { markRaw } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { createAjv } from '@jsonforms/core'
import { vanillaRenderers, defaultStyles } from '@dzikoysk/vue-vanilla'
import { tester as textTester } from '../renderers/MdTextFieldRenderer.vue'
import MdTextFieldRenderer from '../renderers/MdTextFieldRenderer.vue'
import { tester as selectTester } from '../renderers/MdSelectRenderer.vue'
import MdSelectRenderer from '../renderers/MdSelectRenderer.vue'
import EntryDetail from './EntryDetail.vue'

const renderers = markRaw([
  { tester: textTester, renderer: MdTextFieldRenderer },
  { tester: selectTester, renderer: MdSelectRenderer },
  ...vanillaRenderers
])

const collection = {
  key: 'repositories',
  title: 'Repositories',
  noun: 'repository',
  itemSchema: {
    type: 'object',
    properties: {
      id: { type: 'string', title: 'Id' },
      visibility: { type: 'string', enum: ['PUBLIC', 'PRIVATE'], title: 'Visibility' },
      limits: {
        type: 'object',
        title: 'Limits',
        properties: { maxSize: { type: 'integer', title: 'Max size' } }
      }
    }
  }
}

const releases = { id: 'releases', visibility: 'PUBLIC', limits: { maxSize: 10 } }

let wrapper

const mountDetail = (props = {}) => {
  wrapper = mount(EntryDetail, {
    props: {
      collection,
      id: 'releases',
      entry: releases,
      renderers,
      validator: markRaw(createAjv({ useDefaults: true })),
      ...props
    },
    global: { provide: { styles: defaultStyles } },
    attachTo: document.body
  })
  return wrapper
}

const heading = () => wrapper.get('[data-test="entry-title"]')
const labels = () => wrapper.findAll('label').map(label => label.text())

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('EntryDetail', () => {
  it('shows the id of the entry as the heading of the pane', () => {
    mountDetail()

    expect(heading().text()).toBe('releases')
  })

  it('leaves the id out of the form, because renaming it would move the entry', () => {
    mountDetail()

    expect(labels()).not.toContain('Id')
  })

  it('draws the scalar properties in the General group', () => {
    mountDetail()

    expect(wrapper.text()).toContain('General')
    expect(labels()).toContain('Visibility')
  })

  it('gives a nested object its own titled section', () => {
    mountDetail()

    expect(wrapper.text()).toContain('Limits')
    expect(labels()).toContain('Max size')
  })

  it('reports the entry with the edited value and the id kept', async () => {
    mountDetail()

    await wrapper.find('select').setValue('PRIVATE')

    const latest = wrapper.emitted('change').at(-1)[0]
    expect(latest.visibility).toBe('PRIVATE')
    expect(latest.id).toBe('releases')
  })

  it('says that the repository was not found when there is no entry for the id', () => {
    mountDetail({ id: 'ghost', entry: null })

    expect(wrapper.text()).toContain('Repository not found')
    expect(wrapper.text()).toContain('ghost')
  })

  it('offers a way back to the list when the entry is not found', async () => {
    mountDetail({ id: 'ghost', entry: null })

    await wrapper.findAll('button').find(button => button.text() === 'Back to repositories').trigger('click')

    expect(wrapper.emitted('back')).toEqual([[]])
  })

  it('offers a back button on narrow screens that returns to the list', async () => {
    mountDetail()

    await wrapper.find('[aria-label="Back to repositories"]').trigger('click')

    expect(wrapper.emitted('back')).toEqual([[]])
  })

  it('puts the delete action in a danger zone at the end of the pane', () => {
    mountDetail()

    const zone = wrapper.get('[data-test="danger-zone"]')
    expect(zone.text()).toContain('Delete repository')
  })

  it('opens the delete confirmation without deleting anything yet', async () => {
    mountDetail()

    await wrapper.findAll('button').find(button => button.text() === 'Delete repository').trigger('click')
    await flushPromises()

    expect(document.getElementById('delete-entry-title')?.textContent).toContain('Delete releases?')
    expect(wrapper.emitted('delete')).toBeUndefined()
  })

  it('deletes the entry once the confirmation has the exact id', async () => {
    mountDetail()
    await wrapper.findAll('button').find(button => button.text() === 'Delete repository').trigger('click')
    await flushPromises()
    const input = document.getElementById('delete-entry-confirm-input')
    input.value = 'releases'
    input.dispatchEvent(new Event('input'))
    await flushPromises()

    document.getElementById('delete-entry-confirm').click()
    await flushPromises()

    expect(wrapper.emitted('delete')).toEqual([[]])
  })
})
