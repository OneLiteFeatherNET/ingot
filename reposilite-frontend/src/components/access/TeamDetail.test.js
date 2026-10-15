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
import { createErrorToast, createSuccessToast } from '../../helpers/toast'
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

describe('TeamDetail', () => {
  let wrapper
  let teams

  const mountDetail = async (name) => {
    wrapper = await mountTeamDetail(name)
  }

  const conflictDialog = () => document.querySelector('[role="alertdialog"]')

  beforeEach(() => {
    vi.mocked(createSuccessToast).mockClear()
    vi.mocked(createErrorToast).mockClear()
    teams = makeTeamsApi()
    installClient(holder, teams)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    document.body.innerHTML = ''
  })

  describe('loading', () => {
    it('reads the team named by the address', async () => {
      await mountDetail('developers')

      expect(teams.get).toHaveBeenCalledWith('developers')
      expect(wrapper.find('#team-title').text()).toBe('developers')
      expect(wrapper.find('[data-test="description"]').text()).toBe('Everyone who writes code')
    })

    it('says so when the team does not exist', async () => {
      teams.get.mockRejectedValue(failure(404, 'No such team'))
      await mountDetail('ghosts')

      expect(wrapper.find('[data-test="missing"]').text()).toContain('ghosts does not exist')
    })

    it('shows no instance admin chip for a team without the role', async () => {
      await mountDetail()

      expect(wrapper.find('[data-test="admin-badge"]').exists()).toBe(false)
    })

    it('shows the instance admin chip for a team with the role', async () => {
      teams.get.mockResolvedValue({ data: { ...TEAM, instanceRole: true }, headers: { etag: '"5"' } })
      await mountDetail()

      expect(wrapper.find('[data-test="admin-badge"]').text()).toBe('Instance admin')
    })
  })

  describe('description and external reference', () => {
    const edit = async (description, externalRef) => {
      await wrapper.find('[data-test="edit-details"]').trigger('click')
      await wrapper.find('#team-description').setValue(description)
      await wrapper.find('#team-external-ref').setValue(externalRef)
      await wrapper.find('[data-test="edit-form"]').trigger('submit')
      await flushPromises()
    }

    it('patches the description and the external reference with the entity tag it loaded', async () => {
      await mountDetail()

      await edit('Writes code', 'idp:devs')

      expect(teams.update).toHaveBeenCalledWith(
        'developers',
        { description: 'Writes code', externalRef: 'idp:devs' },
        '"5"'
      )
    })

    it('sends a blank external reference as null', async () => {
      await mountDetail()

      await edit('Writes code', '  ')

      expect(teams.update.mock.calls[0][1]).toEqual({ description: 'Writes code', externalRef: null })
    })

    it('shows the new description after the save', async () => {
      await mountDetail()

      await edit('Writes code', '')

      expect(wrapper.find('[data-test="description"]').text()).toBe('Writes code')
    })

    it('uses the entity tag of the last answer for the next write', async () => {
      await mountDetail()

      await edit('First', '')
      await edit('Second', '')

      expect(teams.update.mock.calls.map(call => call[2])).toEqual(['"5"', '"6"'])
    })

    it('opens the conflict dialog when the server answers 412', async () => {
      teams.update.mockRejectedValue(failure(412, 'Precondition failed'))
      await mountDetail()

      await edit('Writes code', '')

      expect(conflictDialog().textContent).toContain('Team changed elsewhere')
      expect(conflictDialog().textContent).toContain('developers')
    })

    it('reads the team again when the conflict is reloaded', async () => {
      teams.update.mockRejectedValue(failure(412, 'Precondition failed'))
      await mountDetail()
      await edit('Writes code', '')
      teams.get.mockClear()

      const reload = [...conflictDialog().querySelectorAll('button')]
        .find(button => button.textContent.trim() === 'Reload and discard my changes')
      reload.click()
      await flushPromises()

      expect(teams.get).toHaveBeenCalledTimes(1)
      expect(conflictDialog(), 'the conflict is settled').toBeNull()
    })
  })

  describe('instance admin', () => {
    const toggle = async () => {
      await wrapper.find('[data-test="instance-admin"]').trigger('click')
      await flushPromises()
    }

    it('asks before it grants and says what the role means', async () => {
      await mountDetail()

      await toggle()

      expect(confirmDialog().textContent).toContain('every action on every repository')
      expect(teams.grantInstanceAdmin, 'nothing is sent before the confirmation').not.toHaveBeenCalled()
    })

    it('grants with a PUT and the entity tag after the confirmation', async () => {
      await mountDetail()

      await toggle()
      await confirm()

      expect(teams.grantInstanceAdmin).toHaveBeenCalledWith('developers', '"5"')
      expect(wrapper.find('[data-test="admin-badge"]').exists()).toBe(true)
    })

    it('does not grant when the dialog is cancelled', async () => {
      await mountDetail()

      await toggle()
      const cancel = [...confirmDialog().querySelectorAll('button')].find(button => button.textContent.trim() === 'Cancel')
      cancel.click()
      await flushPromises()

      expect(teams.grantInstanceAdmin).not.toHaveBeenCalled()
    })

    it('revokes with a DELETE and the entity tag after the confirmation', async () => {
      teams.get.mockResolvedValue({ data: { ...TEAM, instanceRole: true }, headers: { etag: '"5"' } })
      await mountDetail()

      await toggle()
      await confirm()

      expect(teams.revokeInstanceAdmin).toHaveBeenCalledWith('developers', '"5"')
      expect(wrapper.find('[data-test="admin-badge"]').exists()).toBe(false)
    })

    it('shows the server message in the dialog when the last instance admin is revoked', async () => {
      teams.get.mockResolvedValue({ data: { ...TEAM, instanceRole: true }, headers: { etag: '"5"' } })
      teams.revokeInstanceAdmin.mockRejectedValue(failure(409, 'The last instance admin cannot be removed'))
      await mountDetail()

      await toggle()
      await confirm()

      expect(confirmDialog().querySelector('[data-test="confirm-failure"]').textContent)
        .toBe('The last instance admin cannot be removed')
      expect(wrapper.find('[data-test="admin-badge"]').exists(), 'the role is still shown').toBe(true)
    })

    it('opens the conflict dialog on a 412 and closes the confirmation', async () => {
      teams.grantInstanceAdmin.mockRejectedValue(failure(412, 'Precondition failed'))
      await mountDetail()

      await toggle()
      await confirm()

      expect(conflictDialog()).not.toBeNull()
      expect(document.querySelector('[data-test="confirm-button"]'), 'only the conflict dialog stays').toBeNull()
    })
  })

  describe('deleting', () => {
    const askDelete = async () => {
      await wrapper.find('[data-test="delete-team"]').trigger('click')
      await flushPromises()
    }

    it('keeps the button disabled until the team name is typed', async () => {
      await mountDetail()

      await askDelete()
      typeInto('[data-test="confirm-typed"]', 'develop')
      await flushPromises()

      expect(confirmDialog().querySelector('[data-test="confirm-button"]').disabled).toBe(true)
    })

    it('deletes the team once the exact name is typed', async () => {
      await mountDetail()

      await askDelete()
      typeInto('[data-test="confirm-typed"]', 'developers')
      await flushPromises()
      await confirm()

      expect(teams.remove).toHaveBeenCalledWith('developers')
    })

    it('tells the page that the team is gone and shows a snackbar', async () => {
      await mountDetail()

      await askDelete()
      typeInto('[data-test="confirm-typed"]', 'developers')
      await flushPromises()
      await confirm()

      expect(wrapper.emitted('deleted')).toEqual([['developers']])
      expect(createSuccessToast).toHaveBeenCalledWith('Team developers deleted')
    })

    it('shows the server message in the dialog when the last instance admin team is deleted', async () => {
      teams.remove.mockRejectedValue(failure(409, 'The last instance admin cannot be removed'))
      await mountDetail()

      await askDelete()
      typeInto('[data-test="confirm-typed"]', 'developers')
      await flushPromises()
      await confirm()

      expect(confirmDialog().querySelector('[data-test="confirm-failure"]').textContent)
        .toBe('The last instance admin cannot be removed')
      expect(wrapper.emitted('deleted')).toBeUndefined()
    })
  })
})
