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

// @vitest-environment jsdom

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createErrorToast } from '../../helpers/toast'
import TeamParentsCard from './TeamParentsCard.vue'
import { answer, failure } from './teamTestKit'

/*
 * The card reads the parents and the team names through the teams client and saves the whole list
 * with the entity tag it loaded. The client is a fake, so nothing here touches the network, and
 * every test builds its own wrapper and client.
 */

const holder = vi.hoisted(() => ({ session: null }))

vi.mock('../../store/session', () => ({ useSession: () => holder.session }))

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.response?.data?.message ?? error?.message ?? `${error}`
}))

const TEAM_NAMES = ['developers', 'ops', 'release', 'staff']

describe('TeamParentsCard', () => {
  let wrapper
  let teams

  const mountCard = async (props = { name: 'ops' }) => {
    wrapper = mount(TeamParentsCard, { props, attachTo: document.body })
    await flushPromises()
  }

  const input = () => wrapper.find('input[role="combobox"]')
  const chips = () => wrapper.findAll('[data-test="parent"]').map(chip => chip.find('span').text())
  const options = () => wrapper.findAll('[data-test="suggestion"]').map(option => option.text())
  const save = () => wrapper.find('[data-test="save-parents"]')
  const error = () => wrapper.find('[data-test="parents-error"]')

  const type = async (text) => {
    await input().trigger('focus')
    await input().setValue(text)
  }
  const press = async (key) => {
    await input().trigger('keydown', { key })
  }

  beforeEach(() => {
    vi.mocked(createErrorToast).mockClear()
    teams = {
      parents: vi.fn(async () => answer({ parents: ['developers'] }, '"p1"')),
      setParents: vi.fn(async (name, parents) => answer({ parents }, '"p2"')),
      list: vi.fn(async () => answer(TEAM_NAMES.map(name => ({ name }))))
    }
    holder.session = { client: ref({ identity: { teams } }) }
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    document.body.innerHTML = ''
  })

  describe('rendering', () => {
    it('shows the parent teams as removable chips', async () => {
      await mountCard()

      expect(wrapper.find('h4').text()).toBe('Inherits from')
      expect(chips()).toEqual(['developers'])
      expect(wrapper.find('button[aria-label="Remove developers"]').exists()).toBe(true)
    })

    it('reads the parents of the named team', async () => {
      await mountCard({ name: 'ops' })

      expect(teams.parents).toHaveBeenCalledWith('ops')
    })

    it('says so when the team has no parent', async () => {
      teams.parents.mockResolvedValue(answer({ parents: [] }, '"p1"'))
      await mountCard()

      expect(wrapper.find('[data-test="parents-empty"]').exists()).toBe(true)
      expect(chips()).toEqual([])
    })

    it('shows the failure with a retry that reads the parents again', async () => {
      teams.parents.mockRejectedValueOnce(failure(500, 'Store unavailable'))
      await mountCard()

      expect(wrapper.find('[data-test="parents-load-error"]').text()).toContain('Store unavailable')

      await wrapper.find('[data-test="parents-retry"]').trigger('click')
      await flushPromises()

      expect(wrapper.find('[data-test="parents-load-error"]').exists()).toBe(false)
      expect(chips()).toEqual(['developers'])
    })

    it('makes the save button tonal, so the node editor save stays the one filled button', async () => {
      await mountCard()

      expect(save().attributes('data-variant')).toBe('tonal')
    })
  })

  describe('adding a parent', () => {
    it('offers the teams that match the text and are neither the team itself nor a parent yet', async () => {
      await mountCard()

      await type('')

      expect(options()).toEqual(['release', 'staff'])
    })

    it('filters the suggestions by the typed text', async () => {
      await mountCard()

      await type('st')

      expect(options()).toEqual(['staff'])
    })

    it('adds the highlighted suggestion with the arrow keys and Enter', async () => {
      await mountCard()
      await type('')

      await press('ArrowDown')
      await press('ArrowDown')
      await press('Enter')

      expect(chips()).toEqual(['developers', 'staff'])
    })

    it('starts at the last suggestion with ArrowUp', async () => {
      await mountCard()
      await type('')

      await press('ArrowUp')
      await press('Enter')

      expect(chips()).toEqual(['developers', 'staff'])
    })

    it('marks the highlighted option as selected for assistive technology', async () => {
      await mountCard()
      await type('')

      await press('ArrowDown')

      const selected = wrapper.findAll('[role="option"]').filter(option => option.attributes('aria-selected') === 'true')
      expect(selected.map(option => option.text())).toEqual(['release'])
      expect(input().attributes('aria-activedescendant')).toBe(selected[0].attributes('id'))
    })

    it('exposes the suggestions as a listbox that the input controls', async () => {
      await mountCard()
      await type('')

      const listbox = wrapper.find('[role="listbox"]')

      expect(input().attributes('aria-controls')).toBe(listbox.attributes('id'))
      expect(input().attributes('aria-expanded')).toBe('true')
    })

    it('closes the list with Escape', async () => {
      await mountCard()
      await type('')

      await press('Escape')

      expect(input().attributes('aria-expanded')).toBe('false')
    })

    it('adds a typed team name with Enter', async () => {
      await mountCard()

      await type('release')
      await press('Enter')

      expect(chips()).toEqual(['developers', 'release'])
      expect(input().element.value, 'the field is empty again').toBe('')
    })

    it('writes the name the way the team list spells it', async () => {
      await mountCard()

      await type('STAFF')
      await press('Enter')

      expect(chips()).toEqual(['developers', 'staff'])
    })

    it('adds a suggestion that is clicked', async () => {
      await mountCard()
      await type('st')

      await wrapper.find('[data-test="suggestion"]').trigger('click')

      expect(chips()).toEqual(['developers', 'staff'])
    })

    it('adds the typed name with the Add button', async () => {
      await mountCard()
      await type('release')

      await wrapper.find('[data-test="add-parent"]').trigger('click')

      expect(chips()).toEqual(['developers', 'release'])
    })

    it('refuses the team itself as a parent with a clear message', async () => {
      await mountCard({ name: 'ops' })

      await type('ops')
      await press('Enter')

      expect(wrapper.text()).toContain('A team cannot inherit from itself')
      expect(chips()).toEqual(['developers'])
    })

    it('refuses the team itself whatever the case', async () => {
      await mountCard({ name: 'ops' })

      await type('OPS')
      await press('Enter')

      expect(wrapper.text()).toContain('A team cannot inherit from itself')
    })

    it('refuses a team that is already a parent', async () => {
      await mountCard()

      await type('developers')
      await press('Enter')

      expect(wrapper.text()).toContain('developers is already a parent')
      expect(chips()).toEqual(['developers'])
    })

    it('refuses a name that is no team', async () => {
      await mountCard()

      await type('ghosts')
      await press('Enter')

      expect(wrapper.text()).toContain('There is no team named ghosts')
      expect(chips()).toEqual(['developers'])
    })

    it('does not send anything for a refused name', async () => {
      await mountCard({ name: 'ops' })

      await type('ops')
      await press('Enter')

      expect(save().attributes('disabled'), 'nothing changed, so nothing to save').toBeDefined()
      expect(teams.setParents).not.toHaveBeenCalled()
    })

    it('clears the message when the text changes', async () => {
      await mountCard({ name: 'ops' })
      await type('ops')
      await press('Enter')

      await input().setValue('rel')

      expect(wrapper.text()).not.toContain('A team cannot inherit from itself')
    })
  })

  describe('removing a parent', () => {
    it('removes the chip whose button is pressed', async () => {
      await mountCard()

      await wrapper.find('button[aria-label="Remove developers"]').trigger('click')

      expect(chips()).toEqual([])
      expect(wrapper.find('[data-test="parents-empty"]').exists()).toBe(true)
    })

    it('offers a removed team as a suggestion again', async () => {
      await mountCard()
      await wrapper.find('button[aria-label="Remove developers"]').trigger('click')

      await type('dev')

      expect(options()).toEqual(['developers'])
    })
  })

  describe('saving', () => {
    it('keeps save disabled while nothing changed', async () => {
      await mountCard()

      expect(save().attributes('disabled')).toBeDefined()
    })

    it('enables save after a change', async () => {
      await mountCard()

      await wrapper.find('button[aria-label="Remove developers"]').trigger('click')

      expect(save().attributes('disabled')).toBeUndefined()
    })

    it('puts the whole list with the entity tag it loaded', async () => {
      await mountCard()
      await type('staff')
      await press('Enter')

      await save().trigger('click')
      await flushPromises()

      expect(teams.setParents).toHaveBeenCalledWith('ops', ['developers', 'staff'], '"p1"')
    })

    it('puts an empty list when the last parent was removed', async () => {
      await mountCard()
      await wrapper.find('button[aria-label="Remove developers"]').trigger('click')

      await save().trigger('click')
      await flushPromises()

      expect(teams.setParents).toHaveBeenCalledWith('ops', [], '"p1"')
    })

    it('tells the page that the parents were saved and is clean again', async () => {
      await mountCard()
      await wrapper.find('button[aria-label="Remove developers"]').trigger('click')

      await save().trigger('click')
      await flushPromises()

      expect(wrapper.emitted('saved')).toHaveLength(1)
      expect(save().attributes('disabled')).toBeDefined()
    })

    it('uses the entity tag of the last answer for the next save', async () => {
      await mountCard()
      await wrapper.find('button[aria-label="Remove developers"]').trigger('click')
      await save().trigger('click')
      await flushPromises()
      await type('staff')
      await press('Enter')

      await save().trigger('click')
      await flushPromises()

      expect(teams.setParents).toHaveBeenLastCalledWith('ops', ['staff'], '"p2"')
    })

    it('shows the message of a 409 on the card and not as a toast', async () => {
      const message = 'Adding staff as a parent of ops would create a cycle: ops -> staff -> ops'
      teams.setParents.mockRejectedValue(failure(409, message))
      await mountCard()
      await type('staff')
      await press('Enter')

      await save().trigger('click')
      await flushPromises()

      expect(error().text()).toContain(message)
      expect(createErrorToast).not.toHaveBeenCalled()
      expect(wrapper.emitted('saved')).toBeUndefined()
    })

    it('shows the depth message of a 409 on the card', async () => {
      teams.setParents.mockRejectedValue(failure(409, 'Inheritance deeper than 5 levels is not allowed'))
      await mountCard()
      await type('staff')
      await press('Enter')

      await save().trigger('click')
      await flushPromises()

      expect(error().text()).toContain('Inheritance deeper than 5 levels is not allowed')
    })

    it('keeps the draft after a rejected save so it can be fixed', async () => {
      teams.setParents.mockRejectedValue(failure(409, 'would create a cycle'))
      await mountCard()
      await type('staff')
      await press('Enter')

      await save().trigger('click')
      await flushPromises()

      expect(chips()).toEqual(['developers', 'staff'])
      expect(save().attributes('disabled')).toBeUndefined()
    })

    it('clears the message when the list is edited again', async () => {
      teams.setParents.mockRejectedValue(failure(409, 'would create a cycle'))
      await mountCard()
      await type('staff')
      await press('Enter')
      await save().trigger('click')
      await flushPromises()

      await wrapper.find('button[aria-label="Remove staff"]').trigger('click')

      expect(error().exists()).toBe(false)
    })

    it('offers a reload when the parents were changed by somebody else', async () => {
      teams.setParents.mockRejectedValue(failure(412, 'Team ops was changed by somebody else, reload and try again'))
      await mountCard()
      await wrapper.find('button[aria-label="Remove developers"]').trigger('click')
      await save().trigger('click')
      await flushPromises()

      expect(error().text()).toContain('changed by somebody else')

      teams.parents.mockResolvedValue(answer({ parents: ['staff'] }, '"p9"'))
      await wrapper.find('[data-test="parents-reload"]').trigger('click')
      await flushPromises()

      expect(chips()).toEqual(['staff'])
      expect(error().exists()).toBe(false)
    })
  })

  it('reads the parents of the new team when the name changes', async () => {
    await mountCard({ name: 'ops' })

    await wrapper.setProps({ name: 'release' })
    await flushPromises()

    expect(teams.parents).toHaveBeenLastCalledWith('release')
  })

  it('still lets a name be added when the team list cannot be read, the server checks it', async () => {
    teams.list.mockRejectedValue(failure(500, 'Boom'))
    await mountCard()

    await type('release')
    await press('Enter')

    expect(chips()).toEqual(['developers', 'release'])
  })
})
