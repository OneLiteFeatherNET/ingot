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
import SettingsReviewSheet from './SettingsReviewSheet.vue'
import { diffSettings, type SettingsChange } from './settingsDiff'

const SCHEMAS = {
  maven: {
    type: 'object',
    title: 'Maven',
    properties: {
      repositories: {
        type: 'array',
        title: 'Repositories',
        items: {
          type: 'object',
          title: 'Repository',
          properties: {
            id: { type: 'string', title: 'Id' },
            visibility: { type: 'string', title: 'Visibility' },
            storageProvider: {
              title: 'Storage provider',
              oneOf: [
                {
                  type: 'object',
                  title: 'S3',
                  properties: {
                    type: { type: 'string', const: 's3' },
                    secretKey: { type: 'string', title: 'Secret key' }
                  }
                }
              ]
            }
          }
        }
      }
    }
  },
  frontend: {
    type: 'object',
    title: 'Frontend',
    properties: { title: { type: 'string', title: 'Site title' } }
  }
}

const SAVED = {
  maven: {
    repositories: [
      { id: 'releases', visibility: 'PUBLIC', storageProvider: { type: 's3', secretKey: 'old-secret' } },
      { id: 'snapshots', visibility: 'PRIVATE' }
    ]
  },
  frontend: { title: 'Ingot' }
}

const DRAFT = {
  maven: {
    repositories: [
      { id: 'releases', visibility: 'PRIVATE', storageProvider: { type: 's3', secretKey: 'new-secret' } },
      { id: 'staging', visibility: 'PUBLIC' }
    ]
  },
  frontend: { title: 'Ingot 2' }
}

const CHANGES = diffSettings(SAVED, DRAFT, SCHEMAS)

let wrapper: VueWrapper

const mountSheet = (props: Record<string, unknown> = {}) => {
  wrapper = mount(SettingsReviewSheet, {
    props: { open: true, changes: CHANGES, invalid: false, saving: false, ...props },
    attachTo: document.body
  }) as VueWrapper
  return wrapper
}

// The sheet is read from the document, where it is attached, by its dialog role and its name.
const sheet = (): HTMLElement => {
  const element = document.querySelector<HTMLElement>('[role="dialog"][aria-labelledby]')
  if (!element) throw new Error('no review sheet is shown')
  return element
}
// A button is found by its text, or by its accessible name when it is an icon button.
const buttonIn = (name: string): HTMLButtonElement => {
  const button = [...sheet().querySelectorAll('button')]
    .find(item => item.textContent.trim() === name || item.getAttribute('aria-label') === name)
  if (!button) throw new Error(`no ${name} button in the review sheet`)
  return button
}

afterEach(() => {
  wrapper?.unmount()
  document.body.innerHTML = ''
})

describe('SettingsReviewSheet', () => {
  it('draws each change group as an MdCard', async () => {
    mountSheet()
    await flushPromises()

    expect(sheet().querySelectorAll('[data-variant="outlined"]').length, 'every group is a card').toBeGreaterThan(0)
  })

  it('is announced as a labelled dialog', async () => {
    mountSheet()
    await flushPromises()

    expect(document.querySelector('[role="dialog"][aria-labelledby]'), 'a dialog is shown').not.toBeNull()
    expect(document.getElementById(sheet().getAttribute('aria-labelledby') ?? '')?.textContent).toContain('Review changes')
  })

  it('lists the changes under their domain and entry', async () => {
    mountSheet()
    await flushPromises()

    const text = sheet().textContent
    expect(text).toContain('Maven')
    expect(text).toContain('Repositories: releases')
    expect(text).toContain('Visibility')
    expect(text).toContain('Frontend')
  })

  it('shows the old and the new value of a changed field', async () => {
    mountSheet()
    await flushPromises()

    expect(sheet().textContent).toContain('"PUBLIC" → "PRIVATE"')
  })

  it('shows a masked secret as changed, without its values', async () => {
    mountSheet()
    await flushPromises()

    expect(sheet().textContent).toContain('Secret key')
    expect(sheet().textContent).not.toMatch(/old-secret|new-secret/)
  })

  it('marks added and removed entries', async () => {
    mountSheet()
    await flushPromises()

    expect(sheet().textContent).toContain('Added')
    expect(sheet().textContent).toContain('Removed')
  })

  it('reverts the change whose Revert action was pressed', async () => {
    mountSheet()
    await flushPromises()

    const revertVisibility = sheet().querySelector<HTMLButtonElement>('[aria-label="Revert Visibility"]')
    revertVisibility?.click()
    await flushPromises()

    const emitted = wrapper.emitted('revert')
    expect(emitted, 'a revert is requested').toHaveLength(1)
    expect((emitted?.[0]?.[0] as SettingsChange | undefined)?.id).toBe(CHANGES.find(change => change.label === 'Visibility')?.id)
  })

  it('discards all changes and saves from the footer', async () => {
    mountSheet()
    await flushPromises()

    buttonIn('Discard all').click()
    buttonIn('Save').click()
    await flushPromises()

    expect(wrapper.emitted('discard')).toHaveLength(1)
    expect(wrapper.emitted('save')).toHaveLength(1)
  })

  it('does not allow saving while the draft has invalid entries, and says why', async () => {
    mountSheet({ invalid: true })
    await flushPromises()

    expect(buttonIn('Save').disabled).toBe(true)
    expect(sheet().textContent).toContain('Fix the errors before saving.')
  })

  it('says there is nothing to review when no change is left', async () => {
    mountSheet({ changes: [] })
    await flushPromises()

    expect(sheet().textContent).toContain('No unsaved changes')
  })

  it('asks the parent to close it when the close action is pressed', async () => {
    mountSheet()
    await flushPromises()

    buttonIn('Close').click()

    expect(wrapper.emitted('update:open')).toEqual([[false]])
  })

  it('is the shared side sheet, pinned to the end edge at the wide width', async () => {
    mountSheet()
    await flushPromises()

    const panel = sheet().querySelector('[data-md-sheet-part="panel"]')
    expect(panel?.getAttribute('data-md-sheet-side'), 'the panel is pinned to the end edge').toBe('end')
    expect(panel?.classList.contains('w-md-sheet-wide'), 'the panel takes the wide sheet width').toBe(true)
  })

  it('draws the scrim with the scrim role, not as an inline style', async () => {
    mountSheet()
    await flushPromises()

    expect(document.querySelector('[style*="color-mix"]'), 'no element carries an inline scrim colour').toBeNull()
  })

  it('moves the focus into the sheet when it opens', async () => {
    wrapper = mount(SettingsReviewSheet, {
      props: { open: false, changes: CHANGES, invalid: false, saving: false },
      attachTo: document.body
    }) as VueWrapper
    await wrapper.setProps({ open: true })
    await flushPromises()

    expect(sheet().contains(document.activeElement), 'the focus is inside the sheet').toBe(true)
  })

  it('asks to close on Escape and returns the focus to the control that opened it', async () => {
    const opener = document.createElement('button')
    document.body.appendChild(opener)
    opener.focus()
    wrapper = mount(SettingsReviewSheet, {
      props: { open: false, changes: CHANGES, invalid: false, saving: false },
      attachTo: document.body
    }) as VueWrapper
    await wrapper.setProps({ open: true })
    await flushPromises()

    sheet().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()

    expect(wrapper.emitted('update:open'), 'Escape asks to close').toEqual([[false]])
    expect(document.activeElement, 'the focus is back on the opener').toBe(opener)
  })

  it('lays a change row out on the medium window class, not on the default breakpoints', async () => {
    mountSheet()
    await flushPromises()

    const row = sheet().querySelector('li')!
    expect(row.classList.contains('medium:flex-row'), 'side by side from 600 pixels').toBe(true)
    expect(row.classList.contains('medium:justify-between')).toBe(true)
    const defaults = [...sheet().querySelectorAll('*')].flatMap(element => [...element.classList]).filter(name => /^(sm|md|lg|xl|2xl):/.test(name))
    expect(defaults, 'no default breakpoint prefix in the sheet').toEqual([])
  })
})
