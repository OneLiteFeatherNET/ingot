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
import { h } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import FactoryResetModal from './FactoryResetModal.vue'

let wrapper

const openDialog = async () => {
  wrapper = mount(FactoryResetModal, {
    props: { callback: vi.fn() },
    slots: { button: () => h('button', { type: 'button' }, 'Reset') },
    attachTo: document.body
  })
  await wrapper.find('button').trigger('click')
  await flushPromises()
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('FactoryResetModal', () => {
  it('renders the open dialog in the body, not inside the page component', async () => {
    await openDialog()

    const title = document.getElementById('factory-reset-title')
    expect(title, 'the dialog title is in the document').not.toBeNull()
    expect(wrapper.element.contains(title), 'the dialog is teleported out of the page').toBe(false)
  })

  it('announces the open dialog as an alert dialog', async () => {
    await openDialog()

    const alert = document.querySelector('[role="alertdialog"]')
    expect(alert, 'an element with role alertdialog is in the document').not.toBeNull()
    expect(alert.contains(document.getElementById('factory-reset-title'))).toBe(true)
  })
})
