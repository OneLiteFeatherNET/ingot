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
import RepositoryMembersPicker from './RepositoryMembersPicker.vue'

const renderers = markRaw([
  { tester: textTester, renderer: MdTextFieldRenderer },
  { tester: selectTester, renderer: MdSelectRenderer },
  ...vanillaRenderers
])

// The maven repository item as the server describes it, with the fields of each type.
const collection = {
  key: 'repositories',
  title: 'Repositories',
  noun: 'repository',
  itemSchema: {
    type: 'object',
    properties: {
      id: { type: 'string', title: 'Id' },
      visibility: { type: 'string', enum: ['PUBLIC', 'PRIVATE'], title: 'Visibility' },
      type: { type: 'string', enum: ['HOSTED', 'PROXY', 'GROUP'], title: 'Type' },
      redeployment: { type: 'boolean', title: 'Redeployment' },
      storageProvider: {
        type: 'object',
        title: 'Storage provider',
        properties: { type: { type: 'string' }, quota: { type: 'string', title: 'Quota' } }
      },
      proxied: {
        type: 'array',
        title: 'Mirrored repositories',
        items: { type: 'object', properties: { reference: { type: 'string', title: 'Link' } } }
      },
      storagePolicy: { type: 'string', enum: ['PRIORITIZE_UPSTREAM_METADATA', 'STRICT'], title: 'Storage policy' },
      metadataMaxAge: { type: 'integer', title: 'Max age of metadata file' }
    }
  }
}

let wrapper

const mountEntry = (entry, props = {}) => {
  wrapper = mount(EntryDetail, {
    props: {
      collection,
      id: entry.id,
      entry,
      renderers,
      validator: markRaw(createAjv({ useDefaults: true })),
      typed: true,
      otherIds: ['releases', 'central'],
      ...props
    },
    global: { provide: { styles: defaultStyles } },
    attachTo: document.body
  })
  return wrapper
}

const typeSelect = () => wrapper.get('#entry-detail-type')
const chooseType = async (type) => {
  await wrapper.get('#entry-detail-type').setValue(type)
  await flushPromises()
}
const form = () => wrapper.get('[data-test="entry-form"]').text()
const button = (label) => wrapper.findAll('button').find(item => item.text() === label)
const lastChange = () => wrapper.emitted('change')?.at(-1)?.[0]
// The form reports its own state when it mounts, so a test that needs "no change" counts what came after.
const changeCount = () => wrapper.emitted('change')?.length ?? 0

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('EntryDetail repository type selector', () => {
  it('offers the type of the repository, with the choice that leaves a repository as it was', () => {
    mountEntry({ id: 'releases', visibility: 'PUBLIC', type: 'HOSTED' })

    expect(typeSelect().element.value).toBe('HOSTED')
    const options = wrapper.findAll('#entry-detail-type option').map(option => [option.attributes('value'), option.text()])
    expect(options).toEqual([
      ['', 'Not set, as before'],
      ['HOSTED', 'Hosted'],
      ['PROXY', 'Proxy'],
      ['GROUP', 'Group']
    ])
  })

  it('shows a repository without a type with every setting, as before', () => {
    mountEntry({ id: 'old', visibility: 'PUBLIC', storageProvider: { quota: '1GB' }, proxied: [{ reference: 'https://a.example/' }] })

    expect(typeSelect().element.value).toBe('')
    expect(form()).toContain('Storage provider')
    expect(form()).toContain('Mirrored repositories')
    expect(wrapper.find('[data-test="type-violations"]').exists()).toBe(false)
  })
})

describe('EntryDetail fields by type', () => {
  it('shows a hosted repository its storage, and not the mirrored repositories', () => {
    mountEntry({ id: 'releases', visibility: 'PUBLIC', type: 'HOSTED', storageProvider: { quota: '10GB' } })

    expect(form()).toContain('Storage provider')
    expect(form()).not.toContain('Mirrored repositories')
    expect(form()).not.toContain('Storage policy')
  })

  it('shows a proxy its remote repositories, its cache settings and its cache storage, and no upload settings', () => {
    mountEntry({
      id: 'central',
      visibility: 'PUBLIC',
      type: 'PROXY',
      proxied: [{ reference: 'https://a.example/' }],
      storageProvider: { type: 'fs', quota: '1GB' }
    })

    expect(form()).toContain('Mirrored repositories')
    expect(form()).toContain('Storage policy')
    expect(form(), 'the storage of a proxy is its cache storage').toContain('Cache storage')
    expect(form()).not.toContain('Storage provider')
    expect(form()).not.toContain('Redeployment')
  })

  it('shows a group its members as an ordered list, and neither storage nor the mirrored list', () => {
    mountEntry({ id: 'all', visibility: 'PUBLIC', type: 'GROUP', proxied: [{ reference: 'releases' }] })

    expect(wrapper.findComponent(RepositoryMembersPicker).exists()).toBe(true)
    expect(form()).not.toContain('Storage provider')
    expect(form()).not.toContain('Mirrored repositories')
  })
})

describe('EntryDetail switching the type', () => {
  it('asks before a switch that would remove settings, and names what would be removed', async () => {
    mountEntry({ id: 'releases', visibility: 'PUBLIC', type: 'HOSTED', storageProvider: { quota: '10GB' }, redeployment: true })
    const before = changeCount()

    await chooseType('PROXY')

    const notice = wrapper.get('[data-test="type-removals"]').text()
    expect(notice).toContain('Redeployment')
    expect(notice, 'the storage is kept as the cache storage of the proxy').not.toContain('Storage')
    expect(button('Switch and remove')).toBeDefined()
    expect(changeCount(), 'nothing is changed before the user confirms').toBe(before)
  })

  it('applies the switch when the removal is confirmed, and writes the type with the storage it keeps', async () => {
    mountEntry({ id: 'releases', visibility: 'PUBLIC', type: 'HOSTED', storageProvider: { quota: '10GB' }, redeployment: true })
    await chooseType('PROXY')

    await button('Switch and remove').trigger('click')

    expect(lastChange()).toEqual({ id: 'releases', visibility: 'PUBLIC', type: 'PROXY', storageProvider: { quota: '10GB' } })
    expect(wrapper.find('[data-test="type-removals"]').exists()).toBe(false)
  })

  it('keeps the type when the removal is cancelled', async () => {
    mountEntry({ id: 'releases', visibility: 'PUBLIC', type: 'HOSTED', storageProvider: { quota: '10GB' }, redeployment: true })
    await chooseType('PROXY')
    const before = changeCount()

    await button('Keep Hosted').trigger('click')

    expect(changeCount()).toBe(before)
    expect(wrapper.find('[data-test="type-removals"]').exists()).toBe(false)
  })

  it('switches at once, with no question, when nothing would be removed', async () => {
    mountEntry({ id: 'releases', visibility: 'PUBLIC', type: 'HOSTED' })

    await chooseType('GROUP')

    expect(wrapper.find('[data-test="type-removals"]').exists()).toBe(false)
    expect(lastChange()).toEqual({ id: 'releases', visibility: 'PUBLIC', type: 'GROUP' })
  })

  it('asks before a switch that only drops empty entries, and says how many will be removed', async () => {
    mountEntry({ id: 'all', visibility: 'PUBLIC', proxied: [{ reference: 'releases' }, { reference: '' }] })
    const before = changeCount()

    await chooseType('GROUP')

    expect(wrapper.get('[data-test="type-blank-entries"]').text()).toBe('1 empty entry will be removed.')
    expect(button('Switch and remove')).toBeDefined()
    expect(changeCount(), 'nothing is changed before the user confirms').toBe(before)
  })

  it('removes the empty entries from the form when the switch is confirmed, and keeps the others', async () => {
    mountEntry({ id: 'all', visibility: 'PUBLIC', proxied: [{ reference: 'releases' }, { reference: '' }, { reference: 'snapshots' }] })
    await chooseType('GROUP')

    await button('Switch and remove').trigger('click')

    expect(lastChange()).toEqual({ id: 'all', visibility: 'PUBLIC', type: 'GROUP', proxied: [{ reference: 'releases' }, { reference: 'snapshots' }] })
  })

  it('removes the type when the repository goes back to not set', async () => {
    mountEntry({ id: 'releases', visibility: 'PUBLIC', type: 'HOSTED' })

    await chooseType('')

    expect(lastChange()).toEqual({ id: 'releases', visibility: 'PUBLIC' })
  })
})

describe('EntryDetail cache storage of a proxy and storage of a group', () => {
  it('warns that the cache storage stays configured but unused when a proxy becomes a group, and keeps it', async () => {
    mountEntry({
      id: 'central',
      visibility: 'PUBLIC',
      type: 'PROXY',
      proxied: [{ reference: 'https://a.example/' }],
      storageProvider: { type: 's3', bucketName: 'onelitefeather-proxy' }
    })
    const before = changeCount()

    await chooseType('GROUP')

    expect(wrapper.get('[data-test="type-warning"]').text()).toBe('The cache storage stays configured, but a group does not use it.')
    expect(changeCount(), 'nothing is changed before the user confirms').toBe(before)

    await button('Switch and remove').trigger('click')

    expect(lastChange()).toEqual({
      id: 'central',
      visibility: 'PUBLIC',
      type: 'GROUP',
      storageProvider: { type: 's3', bucketName: 'onelitefeather-proxy' }
    })
  })

  it('asks a switch that removes nothing but leaves storage unused, and offers Switch rather than Switch and remove', async () => {
    mountEntry({ id: 'central', visibility: 'PUBLIC', type: 'PROXY', storageProvider: { type: 'fs', quota: '1GB' } })

    await chooseType('GROUP')

    expect(wrapper.find('[data-test="type-removals"]').text()).toContain('stays configured')
    expect(button('Switch')).toBeDefined()
    expect(button('Switch and remove')).toBeUndefined()
  })

  it('notes under a group that its storage stays configured and unused, and hides that storage from the form', () => {
    mountEntry({ id: 'all', visibility: 'PUBLIC', type: 'GROUP', proxied: [{ reference: 'releases' }], storageProvider: { type: 'fs' } })

    expect(wrapper.get('[data-test="unused-storage"]').text()).toBe('The storage stays configured, but a group does not use it.')
    expect(form()).not.toContain('Storage provider')
    expect(form()).not.toContain('Cache storage')
  })

  it('notes nothing under a group without storage', () => {
    mountEntry({ id: 'all', visibility: 'PUBLIC', type: 'GROUP', proxied: [{ reference: 'releases' }] })

    expect(wrapper.find('[data-test="unused-storage"]').exists()).toBe(false)
  })
})

describe('EntryDetail rules of the type', () => {
  it('shows a proxy without remote repositories next to the type, before the save', () => {
    mountEntry({ id: 'central', visibility: 'PUBLIC', type: 'PROXY', proxied: [] })

    expect(wrapper.get('[data-test="type-violations"]').text()).toContain('A proxy needs at least one remote repository.')
  })

  it('shows a hosted repository that mirrors others as a violation', () => {
    mountEntry({ id: 'releases', visibility: 'PUBLIC', type: 'HOSTED', proxied: [{ reference: 'central' }] })

    expect(wrapper.get('[data-test="type-violations"]').text()).toContain('A hosted repository cannot mirror other repositories.')
  })

  it('shows a server refusal next to the type', () => {
    mountEntry({ id: 'all', visibility: 'PUBLIC', type: 'GROUP', proxied: [{ reference: 'releases' }] }, {
      serverError: 'Not saved: Maven. A group cannot contain itself.'
    })

    expect(wrapper.get('[data-test="server-violation"]').text()).toContain('A group cannot contain itself.')
  })
})

describe('EntryDetail group members', () => {
  it('adds a repository to the group at the end and saves it as a reference', async () => {
    mountEntry({ id: 'all', visibility: 'PUBLIC', type: 'GROUP', proxied: [{ reference: 'releases' }] })

    await wrapper.get('#entry-members-add').setValue('central')
    await wrapper.get('#entry-members-add-button').trigger('click')
    await flushPromises()

    expect(lastChange()).toEqual({
      id: 'all',
      visibility: 'PUBLIC',
      type: 'GROUP',
      proxied: [{ reference: 'releases' }, { reference: 'central' }]
    })
  })

  it('moves a member up, and saves the new order', async () => {
    mountEntry({ id: 'all', visibility: 'PUBLIC', type: 'GROUP', proxied: [{ reference: 'releases' }, { reference: 'central' }] })

    await wrapper.get('button[aria-label="Move central up"]').trigger('click')

    expect(lastChange().proxied).toEqual([{ reference: 'central' }, { reference: 'releases' }])
  })
})
