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
import ConflictDialog from './ConflictDialog.vue'

// The modal would teleport to the body; the stub keeps its slot inline so the buttons can be found.
const mountDialog = (conflict, extra = {}) =>
  mount(ConflictDialog, {
    props: { conflict, ...extra },
    global: {
      stubs: {
        VueFinalModal: { template: '<div><slot /></div>' }
      }
    }
  })

const settingsConflict = (overrides = {}) => ({
  kind: 'settings',
  name: 'maven',
  title: 'Maven',
  reviewed: false,
  server: null,
  ...overrides
})

const button = (wrapper, label) =>
  wrapper.findAll('button').find(candidate => candidate.text() === label)

describe('ConflictDialog', () => {
  it('names the settings that changed elsewhere', () => {
    const wrapper = mountDialog(settingsConflict())

    expect(wrapper.text()).toContain('Settings changed elsewhere')
    expect(wrapper.text()).toContain('Maven')
  })

  it('offers review, reload and overwrite for settings', () => {
    const wrapper = mountDialog(settingsConflict())

    expect(button(wrapper, 'Review')).toBeDefined()
    expect(button(wrapper, 'Reload and discard my changes')).toBeDefined()
    expect(button(wrapper, 'Overwrite')).toBeDefined()
  })

  it('offers no review for a token, which is reloaded or overwritten only', () => {
    const wrapper = mountDialog({ kind: 'token', name: 'ci', title: 'ci', reviewed: false, server: null })

    expect(wrapper.text()).toContain('Token changed elsewhere')
    expect(button(wrapper, 'Review')).toBeUndefined()
    expect(button(wrapper, 'Reload and discard my changes')).toBeDefined()
  })

  it('names a team that changed elsewhere and offers no review', () => {
    const wrapper = mountDialog({ kind: 'team', name: 'developers' })

    expect(wrapper.text()).toContain('Team changed elsewhere')
    expect(wrapper.text()).toContain('developers')
    expect(button(wrapper, 'Review')).toBeUndefined()
  })

  it('names the subject whose permission nodes changed elsewhere and offers no review', () => {
    const wrapper = mountDialog({ kind: 'nodes', name: 'alice' })

    expect(wrapper.text()).toContain('Permission nodes changed elsewhere')
    expect(wrapper.text()).toContain('alice')
    expect(button(wrapper, 'Review')).toBeUndefined()
  })

  it('emits review when the user asks to review the settings', async () => {
    const wrapper = mountDialog(settingsConflict())

    await button(wrapper, 'Review').trigger('click')

    expect(wrapper.emitted('review')).toHaveLength(1)
  })

  it('emits reload when the user chooses to discard their changes', async () => {
    const wrapper = mountDialog(settingsConflict())

    await button(wrapper, 'Reload and discard my changes').trigger('click')

    expect(wrapper.emitted('reload')).toHaveLength(1)
  })

  it('does not overwrite on the first press and emits overwrite only after the confirmation', async () => {
    const wrapper = mountDialog(settingsConflict())

    await button(wrapper, 'Overwrite').trigger('click')
    expect(wrapper.emitted('overwrite'), 'the first press only asks for confirmation').toBeUndefined()

    await button(wrapper, 'Confirm overwrite').trigger('click')
    expect(wrapper.emitted('overwrite')).toHaveLength(1)
  })

  it('says how many changes the review lists once the settings were reviewed', () => {
    const wrapper = mountDialog(settingsConflict({ reviewed: true, server: {} }), { differenceCount: 2 })

    expect(wrapper.text()).toContain('2 changes differ')
  })

  it('says that nothing differs when the review finds no change', () => {
    const wrapper = mountDialog(settingsConflict({ reviewed: true, server: {} }), { differenceCount: 0 })

    expect(wrapper.text()).toContain('Nothing differs')
  })

  it('keeps Review available after a review, so the sheet can be opened again', () => {
    const wrapper = mountDialog(settingsConflict({ reviewed: true, server: {} }), { differenceCount: 1 })

    expect(button(wrapper, 'Review')).toBeDefined()
  })

  it('shows no count before the settings were reviewed', () => {
    const wrapper = mountDialog(settingsConflict())

    expect(wrapper.text()).not.toContain('differ')
  })

  it('emits dismiss when the user keeps the draft', async () => {
    const wrapper = mountDialog(settingsConflict())

    await button(wrapper, 'Keep my draft').trigger('click')

    expect(wrapper.emitted('dismiss')).toHaveLength(1)
  })
})

// The shared dialog frame is teleported to the body and read from the document, so these tests mount the real modal.
describe('ConflictDialog in the shared dialog frame', () => {
  let wrapper

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    document.querySelectorAll('.vfm').forEach(element => element.remove())
  })

  const mountModal = async (conflict) => {
    wrapper = mount(ConflictDialog, { props: { conflict }, attachTo: document.body })
    await flushPromises()
  }

  const modalRoot = () => document.querySelector('.vfm')

  it('is announced as an alertdialog named by its heading', async () => {
    await mountModal(settingsConflict())

    const root = modalRoot()
    expect(root?.getAttribute('role'), 'the modal root carries the alertdialog role').toBe('alertdialog')
    const name = document.getElementById(root?.getAttribute('aria-labelledby') ?? '')
    expect(name?.textContent, 'the accessible name is the heading').toContain('Settings changed elsewhere')
  })

  it('uses no fixed id for its heading', async () => {
    await mountModal(settingsConflict())

    expect(document.getElementById('conflict-title'), 'the heading id is generated per instance').toBeNull()
  })

  it('draws the scrim through the dialog frame, not as an inline style', async () => {
    await mountModal(settingsConflict())

    const overlay = document.querySelector('.vfm__overlay')
    expect(overlay?.getAttribute('style') ?? '', 'the overlay has no inline colour').not.toContain('color-mix')
  })

  it('is drawn at the 560px size of the shared frame', async () => {
    await mountModal(settingsConflict())

    const frame = document.querySelector('.vfm__content > div')
    expect(frame?.classList.contains('max-w-140'), 'the frame caps the width at 560px').toBe(true)
  })

  it('puts the focus on Keep my draft when it opens', async () => {
    await mountModal(settingsConflict())

    expect(document.activeElement?.textContent?.trim(), 'the safe choice has the focus').toBe('Keep my draft')
  })
})
