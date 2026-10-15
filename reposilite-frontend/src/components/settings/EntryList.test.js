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
import { mount, flushPromises } from '@vue/test-utils'
import EntryList from './EntryList.vue'

const itemSchema = {
  type: 'object',
  properties: {
    id: { type: 'string' },
    visibility: { type: 'string', enum: ['PUBLIC', 'PRIVATE'] }
  }
}

const entries = [
  { id: 'releases', visibility: 'PUBLIC' },
  { id: 'snapshots', visibility: 'PRIVATE' },
  { id: 'mirror-central', visibility: 'PUBLIC' }
]

// The keys belong to the entries, not to their ids: they stay when the entries are edited.
const keys = ['entry-1', 'entry-2', 'entry-3']

let wrapper

const mountList = (props = {}) => {
  wrapper = mount(EntryList, {
    props: { entries, keys, selectedKey: null, itemSchema, collectionTitle: 'Repositories', noun: 'repository', ...props },
    attachTo: document.body
  })
  return wrapper
}

const itemButtons = () => wrapper.findAll('li button')
const itemIds = () => itemButtons().map(button => button.find('span').text())

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('EntryList', () => {
  it('shows every entry under its id', () => {
    mountList()

    expect(itemIds()).toEqual(['releases', 'snapshots', 'mirror-central'])
  })

  it('shows the first text value of the entry as the supporting line', () => {
    mountList()

    expect(itemButtons()[1].text()).toContain('PRIVATE')
  })

  it('marks only the selected entry as the current one', () => {
    mountList({ selectedKey: 'entry-2' })

    expect(itemButtons().map(button => button.attributes('aria-current'))).toEqual([undefined, 'true', undefined])
  })

  it('emits the address of the entry that is clicked, which is its id', async () => {
    mountList()

    await itemButtons()[2].trigger('click')

    expect(wrapper.emitted('select')).toEqual([['mirror-central']])
  })

  it('filters the list by id while the user types in the search field', async () => {
    mountList()

    await wrapper.find('input').setValue('snap')

    expect(itemIds()).toEqual(['snapshots'])
  })

  it('matches the search without regard to case', async () => {
    mountList()

    await wrapper.find('input').setValue('CENTRAL')

    expect(itemIds()).toEqual(['mirror-central'])
  })

  it('says that no entry matches when the search finds nothing', async () => {
    mountList()

    await wrapper.find('input').setValue('nothing-like-this')

    expect(itemIds()).toEqual([])
    expect(wrapper.text()).toContain('No repository matches "nothing-like-this".')
  })

  it('says that the collection is empty when it has no entries', () => {
    mountList({ entries: [], keys: [] })

    expect(wrapper.text()).toContain('No repositories yet.')
  })

  it('names the add action after the entry it adds', () => {
    mountList()

    expect(wrapper.find('[data-test="new-entry"]').text()).toBe('New repository')
  })

  it('opens the new-entry dialog from the add action', async () => {
    mountList()

    await wrapper.find('[data-test="new-entry"]').trigger('click')
    await flushPromises()

    expect(document.getElementById('new-entry-title')?.textContent).toContain('New repository')
  })
})

describe('EntryList entries without a usable id', () => {
  it('never draws an empty list item for an entry without an id', () => {
    mountList({ entries: [{ visibility: 'PUBLIC' }, ...entries], keys: ['entry-0', ...keys] })

    expect(itemButtons().every(button => button.find('span').text() !== '')).toBe(true)
    expect(wrapper.findAll('li')).toHaveLength(4)
  })

  it('shows an entry without an id as "Unnamed repository", with the reason as its supporting line', () => {
    mountList({ entries: [{ visibility: 'PUBLIC' }], keys: ['entry-0'] })

    expect(itemIds()).toEqual(['Unnamed repository'])
    expect(itemButtons()[0].text()).toContain('Enter an id.')
  })

  it('shows an id that is empty as unnamed too, and does not hide the entry', () => {
    mountList({ entries: [{ id: '', visibility: 'PUBLIC' }], keys: ['entry-0'] })

    expect(itemIds()).toEqual(['Unnamed repository'])
  })

  it('marks two entries that share an id as duplicates, so both stay visible', () => {
    mountList({ entries: [{ id: 'dup' }, { id: 'dup' }], keys: ['entry-1', 'entry-2'] })

    expect(itemIds()).toEqual(['dup', 'dup'])
    expect(itemButtons().map(button => button.text()).every(text => text.includes('A repository with this id already exists.'))).toBe(true)
  })

  it('selects an entry without a usable id by its key, so the address does not name a missing id', async () => {
    mountList({ entries: [{ visibility: 'PUBLIC' }], keys: ['entry-0'] })

    await itemButtons()[0].trigger('click')

    expect(wrapper.emitted('select')).toEqual([['~entry-0']])
  })
})

describe('EntryList keys', () => {
  it('keeps the same list item on screen when the id of its entry is edited', async () => {
    mountList()
    const before = wrapper.findAll('li')[0].element

    await wrapper.setProps({ entries: [{ id: 'renamed', visibility: 'PUBLIC' }, entries[1], entries[2]] })

    expect(wrapper.findAll('li')[0].element, 'the item is the same element, so focus and state stay with it').toBe(before)
    expect(itemIds()).toEqual(['renamed', 'snapshots', 'mirror-central'])
  })

  it('does not drop an entry when a second entry is cleared', async () => {
    mountList()

    await wrapper.setProps({ entries: [{ id: '' }, entries[1], entries[2]] })

    expect(wrapper.findAll('li')).toHaveLength(3)
  })
})
