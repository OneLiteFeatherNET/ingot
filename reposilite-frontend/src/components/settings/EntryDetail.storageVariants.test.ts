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
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createAjv } from '@jsonforms/core'
import { vanillaRenderers, defaultStyles } from '@dzikoysk/vue-vanilla'
import { tester as textTester } from '../renderers/MdTextFieldRenderer.vue'
import MdTextFieldRenderer from '../renderers/MdTextFieldRenderer.vue'
import { tester as selectTester } from '../renderers/MdSelectRenderer.vue'
import MdSelectRenderer from '../renderers/MdSelectRenderer.vue'
import EntryDetail from './EntryDetail.vue'
import type { SettingsSchema } from './collections'
import type { Entry } from '../../store/maven/repositoryTypes'

const renderers = markRaw([
  { tester: textTester, renderer: MdTextFieldRenderer },
  { tester: selectTester, renderer: MdSelectRenderer },
  ...vanillaRenderers
])

// The served repository item: the built-in storage variants and no plugin variant, as the server serves it without the plugin.
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
      storageProvider: {
        oneOf: [
          { type: 'object', title: 'File system Storage Provider', properties: { type: { type: 'string', const: 'fs' }, quota: { type: 'string', title: 'Quota' } } },
          { type: 'object', title: 'S3 Storage Provider', properties: { type: { type: 'string', const: 's3' }, bucketName: { type: 'string', title: 'Bucket' } } }
        ]
      }
    }
  } as SettingsSchema
}

let wrapper: VueWrapper

const mountEntry = (entry: Entry & { id: string }) => {
  wrapper = mount(EntryDetail, {
    props: {
      collection,
      id: entry.id,
      entry,
      renderers,
      validator: markRaw(createAjv({ useDefaults: true })),
      typed: true,
      otherIds: []
    },
    global: { provide: { styles: defaultStyles } },
    attachTo: document.body
  }) as VueWrapper
  return wrapper
}

afterEach(() => {
  wrapper?.unmount()
})

describe('a stored storage type the served schema does not offer', () => {
  const entry = { id: 'releases', type: 'HOSTED', visibility: 'PUBLIC', storageProvider: { type: 'azure-blob', quota: '10GB' } }

  it('says that the storage provider is not installed', async () => {
    mountEntry(entry)
    await flushPromises()

    const note = wrapper.find('[data-test="unavailable-storage"]')
    expect(note.exists(), 'the note about the missing storage provider is shown').toBe(true)
    expect(note.text()).toContain('azure-blob')
    expect(note.text()).toContain('not installed')
  })

  it('does not write the storage of the entry as a file system block', async () => {
    mountEntry(entry)
    await flushPromises()

    const changes = wrapper.emitted<[Entry]>('change') ?? []
    const types = changes.map(([change]) => (change?.storageProvider as { type?: string } | undefined)?.type)
    expect(types.every(type => type === 'azure-blob'), 'the stored type is kept, never replaced by fs').toBe(true)
  })

  it('shows no note for a storage provider the schema offers', async () => {
    mountEntry({ id: 'releases', type: 'HOSTED', visibility: 'PUBLIC', storageProvider: { type: 's3', bucketName: 'artifacts' } })
    await flushPromises()

    expect(wrapper.find('[data-test="unavailable-storage"]').exists()).toBe(false)
  })
})
