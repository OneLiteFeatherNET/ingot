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
import type { VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { Router } from 'vue-router'
import PublicRepositoriesDialog from './PublicRepositoriesDialog.vue'

// The dialog takes an id and an optional description of each public repository.
interface DialogRepository {
  id: string
  description?: string
}

// Unannotated on purpose: the fixtures also carry the visibility, which the dialog does not take as a prop.
const REPOSITORIES = [
  { id: 'mirror', visibility: 'PUBLIC', description: 'Mirror of upstream' },
  { id: 'releases', visibility: 'PUBLIC', description: '' }
]

let wrapper: VueWrapper | undefined
let router: Router

const mountDialog = async (props: { modelValue?: boolean, repositories?: DialogRepository[] } = {}): Promise<void> => {
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { render: () => null } },
      { path: '/settings/:domain/:collection/:entry', name: 'entry', component: { render: () => null } }
    ]
  })
  await router.push('/')
  wrapper = mount(PublicRepositoriesDialog, {
    attachTo: document.body,
    props: { modelValue: true, repositories: REPOSITORIES, ...props },
    global: { plugins: [router] }
  })
  await flushPromises()
}

// The dialog is teleported to the body, so the checks read the document.
const dialog = (): Element | null => document.querySelector('[role="dialog"]')
const buttonNamed = (text: string): HTMLButtonElement | undefined =>
  [...(dialog()?.querySelectorAll('button') ?? [])].find(button => button.textContent?.trim() === text)

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('PublicRepositoriesDialog', () => {
  it('explains that anyone can read the listed repositories without signing in', async () => {
    await mountDialog()

    expect(dialog()?.textContent).toContain('Anyone can read these repositories without signing in.')
  })

  it('lists each public repository with its id and description', async () => {
    await mountDialog()

    const rows = [...(dialog()?.querySelectorAll('li') ?? [])].map(row => row.textContent?.replace(/\s+/g, ' ').trim())
    expect(rows[0], 'the first row names the mirror repository').toContain('mirror')
    expect(rows[0], 'the first row shows the description of the mirror repository').toContain('Mirror of upstream')
    expect(rows[1], 'the second row names the releases repository').toContain('releases')
  })

  it('links each row to the visibility setting of its repository', async () => {
    await mountDialog()

    const links = [...(dialog()?.querySelectorAll('a') ?? [])]
    expect(links.map(link => link.getAttribute('href'))).toEqual([
      '/settings/repositories/mirror',
      '/settings/repositories/releases'
    ])
  })

  it('tells the admin when no repository is public and offers nothing to confirm', async () => {
    await mountDialog({ repositories: [] })

    expect(dialog()?.textContent).toContain('No repository is public.')
    expect(buttonNamed('Looks right'), 'there is nothing to confirm').toBeUndefined()
  })

  it('emits confirm when the admin chooses "Looks right"', async () => {
    await mountDialog()

    buttonNamed('Looks right')?.click()
    await flushPromises()

    expect(wrapper?.emitted('confirm')).toHaveLength(1)
  })

  it('navigates to the repository and closes the dialog when a row is followed', async () => {
    await mountDialog()

    dialog()?.querySelector('a')?.click()
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/settings/repositories/mirror')
    expect(wrapper?.emitted('update:modelValue')).toEqual([[false]])
  })

  it('keeps the heading, the rows and the confirmation in one content block', async () => {
    await mountDialog()

    // The heading sits in a header block, which sits in the content block that also holds the rows and the actions.
    const content = dialog()?.querySelector('h2')?.parentElement?.parentElement
    expect(content?.querySelectorAll('li'), 'the content block holds every row').toHaveLength(2)
    expect(buttonNamed('Looks right') && content?.contains(buttonNamed('Looks right') ?? null), 'the content block holds the confirmation').toBe(true)
  })

  it('names the dialog by its heading', async () => {
    await mountDialog()

    const labelledBy = dialog()?.getAttribute('aria-labelledby') ?? ''
    expect(document.getElementById(labelledBy)?.textContent?.trim(), 'the dialog is named by the Public repositories heading').toBe('Public repositories')
  })

  it('moves focus into the dialog when it opens, to the first row action', async () => {
    await mountDialog()

    expect(document.activeElement?.textContent?.trim(), 'focus lands on the first Change visibility action').toBe('Change visibility')
  })

  it('returns focus to the button that opened the dialog when it closes', async () => {
    const opener = document.createElement('button')
    document.body.append(opener)
    opener.focus()
    await mountDialog()

    await wrapper?.setProps({ modelValue: false })
    await flushPromises()

    const focused = document.activeElement
    opener.remove()
    expect(focused, 'focus is back on the button that opened the dialog').toBe(opener)
  })

  it('closes on Escape and asks the page to close it', async () => {
    await mountDialog()

    dialog()?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()

    expect(wrapper?.emitted('update:modelValue'), 'Escape asks the page to close the dialog').toEqual([[false]])
  })

  it('offers exactly one filled button, the confirmation', async () => {
    await mountDialog()

    const filled = [...(dialog()?.querySelectorAll('[data-variant="filled"]') ?? [])].map(button => button.textContent?.trim())
    expect(filled, 'the only filled button confirms the list').toEqual(['Looks right'])
  })
})
