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

import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
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
