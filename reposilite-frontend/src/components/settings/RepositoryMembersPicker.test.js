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
import { toRaw } from 'vue'
import { mount } from '@vue/test-utils'
import RepositoryMembersPicker from './RepositoryMembersPicker.vue'

let wrapper

// The members are the mirrored entries of the group. Each one is kept as it is; only its reference names the repository.
const entry = (reference, extra = {}) => ({ reference, ...extra })

const mountPicker = (props = {}) => {
  wrapper = mount(RepositoryMembersPicker, {
    props: { modelValue: [], candidates: ['all', 'releases', 'snapshots'], ...props }
  })
  return wrapper
}

const memberRows = () => wrapper.findAll('[data-test="member"]')
const memberIds = () => memberRows().map(row => row.get('[data-test="member-id"]').text())
const button = (label) => wrapper.get(`button[aria-label="${label}"]`)
const addButton = () => wrapper.findAll('button').find(item => item.text() === 'Add')

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('RepositoryMembersPicker', () => {
  it('lists the members in order, with the place of each', () => {
    mountPicker({ modelValue: [entry('releases'), entry('all')] })

    expect(memberIds()).toEqual(['releases', 'all'])
    expect(memberRows().map(row => row.get('[data-test="member-place"]').text())).toEqual(['1', '2'])
  })

  it('offers only the repositories that are not members yet', () => {
    mountPicker({ modelValue: [entry('releases')] })

    const options = wrapper.findAll('option').map(option => option.attributes('value'))
    expect(options).toEqual(['', 'all', 'snapshots'])
  })

  it('adds the chosen repository at the end as a bare reference, keeps the others, and clears the choice', async () => {
    const releases = entry('releases', { store: true })
    mountPicker({ modelValue: [releases] })

    await wrapper.get('select').setValue('snapshots')
    await addButton().trigger('click')

    const emitted = wrapper.emitted('update:modelValue').at(-1)[0]
    expect(emitted).toEqual([entry('releases', { store: true }), { reference: 'snapshots' }])
    expect(toRaw(emitted[0])).toBe(releases)
    expect(wrapper.get('select').element.value).toBe('')
  })

  it('keeps Add disabled until a repository is chosen', async () => {
    mountPicker()

    expect(addButton().attributes('disabled')).toBeDefined()
    await wrapper.get('select').setValue('all')
    expect(addButton().attributes('disabled')).toBeUndefined()
  })

  it('moves a member up and down one place, and each end cannot move further', async () => {
    mountPicker({ modelValue: [entry('releases'), entry('all'), entry('snapshots')] })

    expect(button('Move releases up').attributes('disabled')).toBeDefined()
    expect(button('Move snapshots down').attributes('disabled')).toBeDefined()

    await button('Move all up').trigger('click')
    expect(wrapper.emitted('update:modelValue').at(-1)[0].map(item => item.reference)).toEqual(['all', 'releases', 'snapshots'])

    await button('Move releases down').trigger('click')
    expect(wrapper.emitted('update:modelValue').at(-1)[0].map(item => item.reference)).toEqual(['all', 'releases', 'snapshots'])
  })

  it('removes the member clicked and keeps the others with their settings', async () => {
    const releases = entry('releases', { authorization: { login: 'releases' } })
    const snapshots = entry('snapshots', { authorization: { login: 'snapshots' } })
    mountPicker({ modelValue: [releases, entry('all'), snapshots] })

    await button('Remove all from the group').trigger('click')

    const emitted = wrapper.emitted('update:modelValue').at(-1)[0]
    expect(emitted).toEqual([releases, snapshots])
    expect(toRaw(emitted[0])).toBe(releases)
    expect(toRaw(emitted[1])).toBe(snapshots)
  })

  it('removes only the entry clicked, when two entries name the same repository', async () => {
    const first = entry('releases', { authorization: { login: 'first' } })
    const second = entry('releases', { authorization: { login: 'second' } })
    mountPicker({ modelValue: [first, second] })

    await wrapper.findAll('button[aria-label="Remove releases from the group"]')[1].trigger('click')

    expect(wrapper.emitted('update:modelValue').at(-1)[0]).toEqual([first])
  })

  it('explains that there is nothing to add when every repository is already a member', () => {
    mountPicker({ modelValue: [entry('all'), entry('releases'), entry('snapshots')] })

    expect(wrapper.text()).toContain('Every repository is already a member')
  })

  it('says that no repository is listed yet when the instance has none', () => {
    mountPicker({ candidates: [], modelValue: [] })

    expect(wrapper.text()).toContain('There are no repositories yet')
  })

  it('shows the problem as text linked to the picker, so a screen reader announces it', () => {
    mountPicker({ modelValue: [], error: 'Add at least one repository.' })

    const alert = wrapper.get('[role="alert"]')
    expect(alert.text()).toBe('Add at least one repository.')
  })
})

describe('RepositoryMembersPicker credentials of the members', () => {
  const loopback = { method: 'LOOPBACK_LINK', login: 'reposilite-mirror', password: 'mirror-secret' }
  const mountWithCredentials = (props = {}) => mountPicker({ credentials: true, ...props })
  const editButton = (reference) => wrapper.get(`button[aria-label="Edit the credential of ${reference}"]`)

  it('shows the login of a member credential, and never its password', () => {
    mountWithCredentials({ modelValue: [entry('releases', { authorization: loopback })] })

    expect(wrapper.get('[data-test="member-credential"]').text()).toContain('reposilite-mirror')
    expect(wrapper.text()).not.toContain('mirror-secret')
  })

  it('says that a member has no credential when it has none', () => {
    mountWithCredentials({ modelValue: [entry('releases')] })

    expect(wrapper.get('[data-test="member-credential"]').text()).toBe('No credential')
  })

  it('shows no credential line and no edit control unless the picker is asked to edit credentials', () => {
    mountPicker({ modelValue: [entry('releases', { authorization: loopback })] })

    expect(wrapper.find('[data-test="member-credential"]').exists()).toBe(false)
    expect(wrapper.findAll('button').some(item => item.attributes('aria-label') === 'Edit the credential of releases')).toBe(false)
  })

  it('opens the fields of a credential with a login and a password, and only one member at a time', async () => {
    mountWithCredentials({ modelValue: [entry('releases', { authorization: loopback }), entry('all')] })

    await editButton('releases').trigger('click')

    expect(wrapper.get('#members-login-0').element.value).toBe('reposilite-mirror')
    expect(wrapper.get('#members-password-0').attributes('type')).toBe('password')
    expect(wrapper.find('#members-login-1').exists()).toBe(false)
  })

  it('writes a changed login into the credential, and keeps the method and every other key of it', async () => {
    const releases = entry('releases', { store: true, authorization: { ...loopback, extra: 'kept' } })
    mountWithCredentials({ modelValue: [releases, entry('all')] })
    await editButton('releases').trigger('click')

    await wrapper.get('#members-login-0').setValue('new-mirror')

    const emitted = wrapper.emitted('update:modelValue').at(-1)[0]
    expect(emitted[0]).toEqual(entry('releases', { store: true, authorization: { ...loopback, login: 'new-mirror', extra: 'kept' } }))
    expect(emitted[1]).toEqual(entry('all'))
  })

  it('gives a member without a credential a loopback credential when a login is typed', async () => {
    mountWithCredentials({ modelValue: [entry('releases')] })
    await editButton('releases').trigger('click')

    await wrapper.get('#members-login-0').setValue('reposilite-mirror')

    expect(wrapper.emitted('update:modelValue').at(-1)[0]).toEqual([entry('releases', { authorization: { method: 'LOOPBACK_LINK', login: 'reposilite-mirror' } })])
  })

  it('writes a typed password into the credential without showing the stored one', async () => {
    mountWithCredentials({ modelValue: [entry('releases', { authorization: { ...loopback, password: '********' } })] })
    await editButton('releases').trigger('click')

    await wrapper.get('#members-password-0').setValue('rotated-secret')

    expect(wrapper.emitted('update:modelValue').at(-1)[0][0].authorization.password).toBe('rotated-secret')
  })
})
