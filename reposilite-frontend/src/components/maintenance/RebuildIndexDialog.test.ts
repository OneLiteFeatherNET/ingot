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
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import RebuildIndexDialog from './RebuildIndexDialog.vue'

let wrapper: VueWrapper | null = null
let opener: HTMLButtonElement | null = null

const openDialog = async (props = {}) => {
  wrapper = mount(RebuildIndexDialog, {
    props: { modelValue: false, repositories: ['releases', 'snapshots'], running: false, ...props },
    attachTo: document.body
  }) as VueWrapper
  await wrapper.setProps({ modelValue: true })
  await flushPromises()
}

const dialog = () => document.querySelector('[role="dialog"]') as HTMLElement

const confirmButton = () => document.getElementById('search-index-rebuild-confirm') as HTMLElement

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  opener?.remove()
  opener = null
  document.body.querySelectorAll('.vfm').forEach(element => element.remove())
})

describe('RebuildIndexDialog dialog contract', () => {
  it('names the dialog by its heading', async () => {
    await openDialog()

    const name = document.getElementById(dialog().getAttribute('aria-labelledby') ?? '')
    expect(name?.textContent?.trim(), 'the dialog is named by its heading').toBe('Rebuild the search index?')
  })

  it('moves focus into the dialog when it opens', async () => {
    await openDialog()

    expect(dialog().contains(document.activeElement), 'focus is inside the dialog, not on the page behind it').toBe(true)
  })

  it('returns focus to the button that opened it when it is cancelled', async () => {
    opener = document.createElement('button')
    document.body.append(opener)
    opener.focus()
    await openDialog()

    ;(document.getElementById('search-index-rebuild-cancel') as HTMLElement).click()
    await wrapper?.setProps({ modelValue: false })
    await flushPromises()

    expect(document.activeElement, 'focus returns to the opener').toBe(opener)
  })

  it('closes on Escape', async () => {
    await openDialog()

    ;(document.activeElement as HTMLElement).dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()

    expect(wrapper?.emitted('update:modelValue'), 'Escape asks the parent to close').toEqual([[false]])
  })

  it('has one filled button, the rebuild action, and a text cancel action', async () => {
    await openDialog()

    const filled = [...document.querySelectorAll<HTMLElement>('.vfm [data-variant="filled"]')]
    expect(filled.map(button => button.textContent?.trim()), 'only the rebuild action is filled').toEqual(['Rebuild'])
    expect(document.getElementById('search-index-rebuild-cancel')?.getAttribute('data-variant'), 'cancel is a text button').toBe('text')
  })
})

describe('RebuildIndexDialog confirmation', () => {
  it('confirms a rebuild of all repositories with an empty request body', async () => {
    await openDialog()

    confirmButton().click()
    await flushPromises()

    expect(wrapper?.emitted('confirm'), 'one confirmation is sent').toEqual([[{}]])
  })

  it('confirms a rebuild of the chosen repository only', async () => {
    await openDialog()

    const scope = document.getElementById('search-index-scope') as HTMLSelectElement
    scope.value = 'releases'
    scope.dispatchEvent(new Event('change'))
    await flushPromises()
    confirmButton().click()
    await flushPromises()

    expect(wrapper?.emitted('confirm'), 'the request names the chosen repository').toEqual([[{ repositories: ['releases'] }]])
  })

  it('does not offer the confirmation again while a rebuild is running', async () => {
    await openDialog({ running: true })

    expect(confirmButton().hasAttribute('disabled') || confirmButton().closest('[disabled]') !== null, 'the confirm action is disabled').toBe(true)
  })
})

// Type contract, checked by vue-tsc: repositories is a list of names. Never called at runtime.
// @ts-expect-error repositories is a list, a single name is not
const mountWithOneRepository = () => mount(RebuildIndexDialog, { props: { modelValue: false, repositories: 'releases' } })
void mountWithOneRepository
