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
import ConfirmActionDialog from './ConfirmActionDialog.vue'

const dialog = () => document.querySelector('[role="dialog"]')
const confirmButton = () => dialog().querySelector('[data-test="confirm-button"]')

const type = async (value) => {
  const input = dialog().querySelector('input')
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

describe('ConfirmActionDialog', () => {
  let wrapper

  const open = async (props = {}) => {
    wrapper = mount(ConfirmActionDialog, {
      props: { modelValue: true, title: 'Delete user', confirmLabel: 'Delete', ...props },
      slots: { default: 'This cannot be undone.' },
      attachTo: document.body
    })
    await flushPromises()
  }

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    document.body.innerHTML = ''
  })

  it('shows the title and the explanation', async () => {
    await open()

    expect(dialog().textContent).toContain('Delete user')
    expect(dialog().textContent).toContain('This cannot be undone.')
  })

  it('confirms at once when no text has to be typed', async () => {
    await open()

    confirmButton().click()
    await flushPromises()

    expect(wrapper.emitted('confirm'), 'a plain confirmation needs no typing').toHaveLength(1)
  })

  it('keeps the button disabled until the text is typed exactly', async () => {
    await open({ typeToConfirm: 'alice' })

    expect(confirmButton().disabled, 'nothing typed yet').toBe(true)
    await type('Alice')
    expect(confirmButton().disabled, 'the match is case sensitive').toBe(true)
    await type('alic')
    expect(confirmButton().disabled, 'a partial text does not count').toBe(true)
    await type('alice')
    expect(confirmButton().disabled).toBe(false)
  })

  it('does not confirm while the typed text differs', async () => {
    await open({ typeToConfirm: 'alice' })

    await type('bob')
    dialog().querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }))
    await flushPromises()

    expect(wrapper.emitted('confirm')).toBeUndefined()
  })

  it('emits confirm once the text matches', async () => {
    await open({ typeToConfirm: 'alice' })

    await type('alice')
    confirmButton().click()
    await flushPromises()

    expect(wrapper.emitted('confirm')).toHaveLength(1)
  })

  it('shows the failure it is given, such as the server message of a 409', async () => {
    await open({ failure: 'The last instance admin cannot be deleted.' })

    expect(dialog().querySelector('[role="alert"]').textContent).toContain('The last instance admin cannot be deleted.')
  })

  it('disables the button while the request runs', async () => {
    await open({ busy: true })

    expect(confirmButton().disabled).toBe(true)
  })
})
