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
import SettingsReviewSheet from './SettingsReviewSheet.vue'
import { diffSettings } from './settingsDiff'

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

let wrapper

const mountSheet = (props = {}) => {
  wrapper = mount(SettingsReviewSheet, {
    props: { open: true, changes: CHANGES, invalid: false, saving: false, ...props },
    attachTo: document.body
  })
  return wrapper
}

// The sheet is teleported to the body, so it is read from the document. The modal's own wrapper also
// has role="dialog", so the sheet is the one that is labelled.
const sheet = () => document.querySelector('[role="dialog"][aria-labelledby]')
const buttonIn = (name) => [...sheet().querySelectorAll('button')].find(button => button.textContent.trim() === name)

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  document.body.innerHTML = ''
})

describe('SettingsReviewSheet', () => {
  it('is announced as a labelled dialog', async () => {
    mountSheet()
    await flushPromises()

    expect(sheet(), 'a dialog is shown').not.toBeNull()
    expect(document.getElementById(sheet().getAttribute('aria-labelledby'))?.textContent).toContain('Review changes')
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

    const revertVisibility = sheet().querySelector('[aria-label="Revert Visibility"]')
    revertVisibility.click()
    await flushPromises()

    const emitted = wrapper.emitted('revert')
    expect(emitted, 'a revert is requested').toHaveLength(1)
    expect(emitted[0][0].id).toBe(CHANGES.find(change => change.label === 'Visibility').id)
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
})
