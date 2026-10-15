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
import { mount, flushPromises } from '@vue/test-utils'
import NewTeamDialog from './NewTeamDialog.vue'

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.response?.data?.message ?? error?.message ?? `${error}`
}))

const dialog = () => document.querySelector('[role="dialog"]')

const type = (id, value) => {
  const input = document.getElementById(id)
  input.value = value
  input.dispatchEvent(new Event('input'))
}

const submit = async () => {
  dialog().querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }))
  await flushPromises()
}

describe('NewTeamDialog', () => {
  let wrapper
  let api

  beforeEach(async () => {
    api = { create: vi.fn(async () => ({ data: { name: 'qa' } })) }
    wrapper = mount(NewTeamDialog, { props: { modelValue: true, api }, attachTo: document.body })
    await flushPromises()
  })

  afterEach(() => {
    wrapper.unmount()
    document.body.innerHTML = ''
  })

  it('creates the team with the name, the description and the external reference', async () => {
    type('new-team-name', 'qa')
    type('new-team-description', 'Testers')
    type('new-team-external-ref', 'idp:qa')
    await submit()

    expect(api.create).toHaveBeenCalledWith({ name: 'qa', description: 'Testers', externalRef: 'idp:qa' })
  })

  it('leaves the optional fields out of the body when they are blank', async () => {
    type('new-team-name', 'qa')
    await submit()

    expect(api.create).toHaveBeenCalledWith({ name: 'qa' })
  })

  it('announces the new team by its name', async () => {
    type('new-team-name', ' qa ')
    await submit()

    expect(wrapper.emitted('created')).toEqual([['qa']])
  })

  it('creates nothing without a name', async () => {
    await submit()

    expect(api.create, 'no request for an empty name').not.toHaveBeenCalled()
    expect(dialog().textContent).toContain('A name is required')
  })

  it('shows the server message when the name is taken and stays open', async () => {
    api.create.mockRejectedValue({ response: { status: 409, data: { message: 'Team qa already exists' } } })
    type('new-team-name', 'qa')
    await submit()

    expect(dialog().querySelector('[data-test="create-failure"]').textContent).toContain('Team qa already exists')
    expect(wrapper.emitted('created')).toBeUndefined()
  })
})
