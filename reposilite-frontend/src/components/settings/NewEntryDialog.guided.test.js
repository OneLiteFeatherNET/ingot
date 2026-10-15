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

import { describe, it, expect, vi, afterEach } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { VueFinalModal } from 'vue-final-modal'
import NewEntryDialog from './NewEntryDialog.vue'
import { guidedCreateKey } from './guidedCreate'

/*
 * When a collection supplies a guided flow, "New entry" starts that flow and the id dialog never opens.
 * Without one, the dialog works as before; NewEntryDialog.test.js covers that path.
 */

let wrapper

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

// A host with its own open state, the way EntryList holds the dialog it opens.
const mountList = (guided) => {
  const open = ref(false)
  const List = defineComponent({
    setup: () => () => h(NewEntryDialog, {
      modelValue: open.value,
      noun: 'repository',
      existingIds: [],
      'onUpdate:modelValue': (value) => { open.value = value }
    })
  })
  wrapper = mount(List, {
    attachTo: document.body,
    global: { provide: { [guidedCreateKey]: ref(guided) } }
  })
  return { open }
}

const pressNew = async ({ open }) => {
  open.value = true
  await flushPromises()
}

// The id dialog is the modal that NewEntryDialog renders; its own open state is the one that matters.
const dialogOpen = () => wrapper.findComponent(VueFinalModal).props('modelValue')

describe('NewEntryDialog with a guided flow', () => {
  it('starts the guided flow instead of opening the id dialog', async () => {
    const guided = vi.fn()
    const list = mountList(guided)

    await pressNew(list)

    expect(guided).toHaveBeenCalledTimes(1)
    expect(dialogOpen()).toBe(false)
  })

  it('closes its own open state again, so the list is not left with a dialog marked open', async () => {
    const list = mountList(vi.fn())

    await pressNew(list)

    expect(list.open.value).toBe(false)
  })

  it('still opens the id dialog when the collection has no guided flow', async () => {
    const list = mountList(null)

    await pressNew(list)

    expect(dialogOpen()).toBe(true)
  })
})
