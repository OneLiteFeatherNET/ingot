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

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import {
  TEAM,
  confirm,
  confirmDialog,
  failure,
  installClient,
  makeTeamsApi,
  mountTeamDetail,
  typeInto
} from './teamTestKit'

const holder = vi.hoisted(() => ({ session: null }))

vi.mock('../../store/session', () => ({ useSession: () => holder.session }))

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.response?.data?.message ?? error?.message ?? `${error}`
}))

describe('TeamDetail members', () => {
  let wrapper
  let teams

  const row = (name) => wrapper.findAll('[data-test="member"]')
    .find(member => member.find('[data-test="member-name"]').text() === name)

  const candidates = () => [...document.querySelectorAll('[data-test="candidate"]')]
  const candidateNames = () => candidates().map(candidate => candidate.querySelector('.md-body-small').textContent.trim())

  const openPicker = async () => {
    await wrapper.find('[data-test="add-member"]').trigger('click')
    await flushPromises()
  }

  const pick = async (name) => {
    const label = candidates().find(candidate => candidate.querySelector('.md-body-small').textContent.trim() === name)
    label.querySelector('input').click()
    await flushPromises()
  }

  const addPicked = async () => {
    document.querySelector('[data-test="add-members"]').click()
    await flushPromises()
  }

  beforeEach(async () => {
    teams = makeTeamsApi()
    installClient(holder, teams)
    wrapper = await mountTeamDetail()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    document.body.innerHTML = ''
  })

  describe('rows', () => {
    it('shows every member with the kind of its principal', async () => {
      expect(wrapper.findAll('[data-test="member"]').map(member => member.find('[data-test="member-name"]').text()))
        .toEqual(['alice', 'bob', 'ci-bot'])
      expect(row('ci-bot').find('[data-test="kind-chip"]').text()).toBe('Machine')
      expect(row('alice').find('[data-test="kind-chip"]').text()).toBe('User')
    })

    it('shows the source of every membership', async () => {
      expect(row('alice').find('[data-test="source-chip"]').text()).toBe('Manual')
      expect(row('bob').find('[data-test="source-chip"]').text()).toBe('Synced')
    })

    it('marks a disabled principal with a chip', async () => {
      expect(row('bob').find('[data-test="disabled-chip"]').exists()).toBe(true)
      expect(row('alice').find('[data-test="disabled-chip"]').exists()).toBe(false)
    })

    it('makes a synced member read-only and says why', async () => {
      expect(row('bob').find('[data-test="remove-member"]').exists()).toBe(false)
      expect(row('bob').find('[data-test="sync-note"]').text()).toBe('Synced from the identity provider')
    })

    it('lets a manual member be removed', async () => {
      expect(row('alice').find('[data-test="remove-member"]').exists()).toBe(true)
    })
  })

  describe('removing a member', () => {
    const askRemove = async (name) => {
      await row(name).find('[data-test="remove-member"]').trigger('click')
      await flushPromises()
    }

    it('asks before it sends anything', async () => {
      await askRemove('alice')

      expect(confirmDialog().textContent).toContain('alice')
      expect(teams.setMembers).not.toHaveBeenCalled()
    })

    it('replaces the manual members without the removed one and keeps the synced one out of the payload', async () => {
      await askRemove('alice')
      await confirm()

      expect(teams.setMembers).toHaveBeenCalledWith('developers', ['ci-bot'], '"5"')
    })

    it('shows the server message in the dialog when the last instance admin would be removed', async () => {
      teams.setMembers.mockRejectedValue(failure(409, 'The last instance admin cannot be removed'))
      await askRemove('alice')
      await confirm()

      expect(confirmDialog().querySelector('[data-test="confirm-failure"]').textContent)
        .toBe('The last instance admin cannot be removed')
      expect(row('alice'), 'the member is still listed').toBeDefined()
    })

    it('opens the conflict dialog when the team changed elsewhere', async () => {
      teams.setMembers.mockRejectedValue(failure(412, 'Precondition failed'))
      await askRemove('alice')
      await confirm()

      expect(document.querySelector('[role="alertdialog"]').textContent).toContain('Team changed elsewhere')
    })

    it('sends the same change with the fresh entity tag when the conflict is overwritten', async () => {
      teams.setMembers.mockRejectedValueOnce(failure(412, 'Precondition failed'))
      teams.get.mockResolvedValue({ data: TEAM, headers: { etag: '"9"' } })
      await askRemove('alice')
      await confirm()

      const overwrite = [...document.querySelectorAll('[role="alertdialog"] button')]
        .find(button => button.textContent.trim() === 'Overwrite')
      overwrite.click()
      await flushPromises()
      const confirmOverwrite = [...document.querySelectorAll('[role="alertdialog"] button')]
        .find(button => button.textContent.trim() === 'Confirm overwrite')
      confirmOverwrite.click()
      await flushPromises()

      expect(teams.setMembers).toHaveBeenLastCalledWith('developers', ['ci-bot'], '"9"')
    })
  })

  describe('the principal picker', () => {
    it('offers the principals that are no members yet', async () => {
      await openPicker()

      expect(candidateNames()).toEqual(['carol', 'dave', 'builder'])
    })

    it('excludes the members of either source', async () => {
      await openPicker()

      const names = candidateNames()

      expect(names).not.toContain('alice')
      expect(names, 'bob is a SYNC member').not.toContain('bob')
      expect(names).not.toContain('ci-bot')
    })

    it('lists a disabled principal with a chip and allows it', async () => {
      await openPicker()

      const carol = candidates().find(candidate => candidate.textContent.includes('carol'))

      expect(carol.textContent).toContain('Disabled')
      expect(carol.querySelector('input').disabled).toBe(false)
    })

    it('filters by name and display name', async () => {
      await openPicker()

      typeInto('[data-test="picker-search"]', 'builder')
      await flushPromises()

      expect(candidateNames()).toEqual(['builder'])
    })

    it('adds the picked principals to the manual members and sends the entity tag', async () => {
      await openPicker()
      await pick('dave')
      await pick('carol')
      await addPicked()

      expect(teams.setMembers).toHaveBeenCalledWith('developers', ['alice', 'ci-bot', 'dave', 'carol'], '"5"')
    })

    it('adds nothing until somebody is picked', async () => {
      await openPicker()

      expect(document.querySelector('[data-test="add-members"]').disabled).toBe(true)
    })

    it('shows the new member in the list after the add', async () => {
      await openPicker()
      await pick('dave')
      await addPicked()

      expect(row('dave')).toBeDefined()
    })

    it('shows the server message in the picker when the add is rejected', async () => {
      teams.setMembers.mockRejectedValue(failure(400, 'Unknown principal dave'))
      await openPicker()
      await pick('dave')
      await addPicked()

      expect(document.querySelector('[data-test="add-failure"]').textContent).toBe('Unknown principal dave')
    })
  })
})
