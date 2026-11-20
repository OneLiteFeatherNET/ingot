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
import { mount, type VueWrapper } from '@vue/test-utils'
import type { createAjv } from '@jsonforms/core'
import CollectionEditor from './CollectionEditor.vue'

const EntryDetailStub = {
  template: '<div data-test="detail" />'
}

const COLLECTION = {
  key: 'webhooks',
  title: 'Webhooks',
  description: 'Where events are sent',
  noun: 'webhook',
  itemSchema: {}
}

describe('CollectionEditor detail pane', () => {
  let wrapper: VueWrapper | undefined

  const mountEditor = (selectedId: string | null) => {
    wrapper = mount(CollectionEditor, {
      props: {
        collection: COLLECTION,
        entries: [{ id: 'hook', url: 'https://example.test/hook' }],
        keys: ['hook'],
        selectedId: selectedId ?? undefined,
        renderers: [],
        validator: {} as ReturnType<typeof createAjv>
      },
      attachTo: document.body,
      global: { stubs: { EntryDetail: EntryDetailStub, EntryList: true } }
    })
  }

  // The detail pane is the element that takes the wide inset of the list-detail layout.
  const detailPane = () => wrapper!.find('.expanded\\:p-6').classes()

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('keeps the same inset on the detail pane at every width', () => {
    mountEditor('hook')

    expect(detailPane(), 'the detail pane has an inset below 840 pixels too').toContain('p-4')
    expect(detailPane(), 'the detail pane keeps the wider inset from 840 pixels').toContain('expanded:p-6')
  })

  it('never hides the detail pane below 840 pixels, with or without a selection', () => {
    mountEditor(null)

    expect(detailPane(), 'the detail pane is never hidden below 840 pixels').not.toContain('max-expanded:hidden')
  })
})
