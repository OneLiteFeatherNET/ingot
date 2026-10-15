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
import { createErrorToast } from '../../helpers/toast'
import { failure, installClient, makeTeamsApi, mountTeamDetail, typeInto } from './teamTestKit'

const holder = vi.hoisted(() => ({ session: null }))

vi.mock('../../store/session', () => ({ useSession: () => holder.session }))

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.response?.data?.message ?? error?.message ?? `${error}`
}))

describe('TeamDetail grants', () => {
  let wrapper
  let teams

  const rows = () => wrapper.findAll('[data-test="grant"]')

  const choose = async (selector, value) => {
    const select = document.querySelector(selector)
    select.value = value
    select.dispatchEvent(new Event('change'))
    await flushPromises()
  }

  const submitEditor = async () => {
    document.querySelector('[data-test="save-grant"]').click()
    await flushPromises()
  }

  const saveGrants = async () => {
    await wrapper.find('[data-test="save-grants"]').trigger('click')
    await flushPromises()
  }

  const addGrant = async ({ repository = '', pathPrefix = '', role = 'READ' } = {}) => {
    await wrapper.find('[data-test="add-grant"]').trigger('click')
    await flushPromises()
    await choose('#grant-repository', repository)
    typeInto('#grant-path-prefix', pathPrefix)
    await choose('#grant-role', role)
    await submitEditor()
  }

  beforeEach(async () => {
    vi.mocked(createErrorToast).mockClear()
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
    it('names a grant without a repository "Every repository"', () => {
      expect(rows()[0].find('[data-test="grant-repository"]').text()).toBe('Every repository')
    })

    it('names a grant without a path prefix "Whole repository"', () => {
      expect(rows()[0].find('[data-test="grant-prefix"]').text()).toBe('Whole repository')
    })

    it('shows the repository and the path prefix of a narrow grant, without outer slashes', () => {
      expect(rows()[1].find('[data-test="grant-repository"]').text()).toBe('releases')
      expect(rows()[1].find('[data-test="grant-prefix"]').text()).toBe('com/example')
    })

    it('shows the role as a chip with a one-line explanation', () => {
      expect(rows()[1].find('[data-test="grant-role"]').text()).toBe('Deploy')
      expect(rows()[1].find('[data-test="grant-explanation"]').text()).toBe('Read, and deploy new artifacts.')
    })

    it('offers no save button while nothing changed', () => {
      expect(wrapper.find('[data-test="save-grants"]').exists()).toBe(false)
    })
  })

  describe('saving', () => {
    it('sends null for the repository and null for the path prefix of a new default grant', async () => {
      await addGrant()
      await saveGrants()

      expect(teams.setGrants).toHaveBeenCalledWith('developers', [
        { repository: null, pathPrefix: null, role: 'READ' },
        { repository: 'releases', pathPrefix: 'com/example', role: 'DEPLOY' },
        { repository: null, pathPrefix: null, role: 'READ' }
      ], '"5"')
    })

    it('normalizes a path prefix with leading and trailing slashes', async () => {
      await addGrant({ repository: 'snapshots', pathPrefix: '/org/acme/', role: 'MAINTAIN' })
      await saveGrants()

      expect(teams.setGrants.mock.calls[0][1][2])
        .toEqual({ repository: 'snapshots', pathPrefix: 'org/acme', role: 'MAINTAIN' })
    })

    it('shows the normalized path prefix in the row right away', async () => {
      await addGrant({ repository: 'snapshots', pathPrefix: '/org/acme/' })

      expect(rows()[2].find('[data-test="grant-prefix"]').text()).toBe('org/acme')
    })

    it('offers every repository and the repositories of the server in the editor', async () => {
      await wrapper.find('[data-test="add-grant"]').trigger('click')
      await flushPromises()

      const options = [...document.querySelectorAll('#grant-repository option')].map(option => option.textContent.trim())

      expect(options).toEqual(['Every repository', 'releases', 'snapshots'])
    })

    it('explains the chosen role in the editor', async () => {
      await wrapper.find('[data-test="add-grant"]').trigger('click')
      await flushPromises()
      await choose('#grant-role', 'ADMIN')

      expect(document.body.textContent).toContain('plus the repository settings and the team grants on it')
    })

    it('edits a grant in place', async () => {
      await rows()[1].find('[data-test="edit-grant"]').trigger('click')
      await flushPromises()
      await choose('#grant-role', 'MAINTAIN')
      await submitEditor()
      await saveGrants()

      expect(teams.setGrants.mock.calls[0][1][1]).toEqual({ repository: 'releases', pathPrefix: 'com/example', role: 'MAINTAIN' })
    })

    it('removes a grant', async () => {
      await rows()[0].find('[data-test="remove-grant"]').trigger('click')
      await saveGrants()

      expect(teams.setGrants.mock.calls[0][1])
        .toEqual([{ repository: 'releases', pathPrefix: 'com/example', role: 'DEPLOY' }])
    })

    it('sends an empty list when the last grant is removed', async () => {
      await rows()[1].find('[data-test="remove-grant"]').trigger('click')
      await rows()[0].find('[data-test="remove-grant"]').trigger('click')
      await saveGrants()

      expect(teams.setGrants.mock.calls[0][1]).toEqual([])
    })

    it('drops the draft on discard without a request', async () => {
      await rows()[0].find('[data-test="remove-grant"]').trigger('click')
      await wrapper.find('[data-test="discard-grants"]').trigger('click')

      expect(rows()).toHaveLength(2)
      expect(teams.setGrants).not.toHaveBeenCalled()
    })

    it('shows the server message in a snackbar when the grants are rejected', async () => {
      teams.setGrants.mockRejectedValue(failure(400, 'Unknown repository'))
      await addGrant()
      await saveGrants()

      expect(createErrorToast).toHaveBeenCalledWith('Unknown repository')
    })

    it('opens the conflict dialog when the team changed elsewhere', async () => {
      teams.setGrants.mockRejectedValue(failure(412, 'Precondition failed'))
      await addGrant()
      await saveGrants()

      expect(document.querySelector('[role="alertdialog"]').textContent).toContain('Team changed elsewhere')
    })

    it('keeps the unsaved draft when a member is added meanwhile', async () => {
      await addGrant()

      await wrapper.find('[data-test="add-member"]').trigger('click')
      await flushPromises()
      document.querySelector('[data-test="candidate"] input').click()
      await flushPromises()
      document.querySelector('[data-test="add-members"]').click()
      await flushPromises()

      expect(teams.setMembers).toHaveBeenCalledTimes(1)
      expect(rows(), 'the draft grant is still there').toHaveLength(3)
    })
  })
})
