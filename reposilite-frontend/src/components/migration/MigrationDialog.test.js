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
import MigrationDialog from './MigrationDialog.vue'

const mounted = []

afterEach(() => {
  mounted.splice(0).forEach(wrapper => wrapper.unmount())
  document.body.innerHTML = ''
})

// A page with a button that opens the dialog, as a step has. The dialog is teleported to the body.
const mountDialog = async (props = {}) => {
  const opener = document.createElement('button')
  opener.id = 'opener'
  opener.textContent = 'Open'
  document.body.appendChild(opener)
  const wrapper = mount(MigrationDialog, {
    props: { modelValue: false, titleId: 'dialog-title', title: 'Replace existing files?', ...props },
    slots: {
      default: '<p>Existing files will be replaced.</p>',
      actions: '<button id="first-action">Cancel</button><button id="second-action">Replace</button>',
    },
    attachTo: document.body,
  })
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, opener }
}

describe('the migration dialog', () => {
  it('moves focus into the dialog when it opens', async () => {
    const { wrapper } = await mountDialog()

    await wrapper.setProps({ modelValue: true })
    await flushPromises()

    expect(document.querySelector('[role="dialog"]').contains(document.activeElement), 'the focus is inside the dialog').toBe(true)
    expect(document.querySelector('[role="dialog"]').getAttribute('aria-labelledby')).toBe('dialog-title')
  })

  it('closes on Escape and returns focus to the element that opened it', async () => {
    const { wrapper, opener } = await mountDialog()
    opener.focus()
    await wrapper.setProps({ modelValue: true })
    await flushPromises()

    document.querySelector('[role="dialog"]').dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])

    await wrapper.setProps({ modelValue: false })
    await flushPromises()

    expect(document.activeElement?.id, 'focus is back on the opener').toBe('opener')
  })

  it('keeps Tab inside the dialog, from the last control back to the first', async () => {
    const { wrapper } = await mountDialog()
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    document.getElementById('second-action').focus()

    document.querySelector('[role="dialog"]').dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }))
    await flushPromises()

    expect(document.activeElement?.id, 'Tab from the last control goes to the first').toBe('first-action')
  })

  it('keeps Shift+Tab inside the dialog, from the first control to the last', async () => {
    const { wrapper } = await mountDialog()
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    document.getElementById('first-action').focus()

    document.querySelector('[role="dialog"]').dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true }))
    await flushPromises()

    expect(document.activeElement?.id).toBe('second-action')
  })
})
