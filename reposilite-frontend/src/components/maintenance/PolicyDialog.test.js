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
import PolicyDialog from './PolicyDialog.vue'

let wrapper

const openDialog = async (props = {}) => {
  wrapper = mount(PolicyDialog, {
    props: { modelValue: false, policy: null, existingIds: [], repositoryIds: ['releases', 'snapshots'], saving: false, ...props },
    attachTo: document.body
  })
  await wrapper.setProps({ modelValue: true })
  await flushPromises()
}

const field = (id) => document.getElementById(id)

const typeInto = async (id, value) => {
  field(id).value = value
  field(id).dispatchEvent(new Event('input'))
  await flushPromises()
}

const chooseRepository = async (id) => {
  document.querySelector(`[data-repository="${id}"]`).click()
  await flushPromises()
}

const save = async () => {
  document.getElementById('policy-save').click()
  await flushPromises()
}

const errorText = () => document.querySelector('#policy-form')?.textContent ?? ''

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('PolicyDialog validation', () => {
  it('does not send a policy with an empty id', async () => {
    await openDialog()
    await typeInto('policy-keep-latest', '3')

    await save()

    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(errorText()).toContain('Enter an id.')
  })

  it('does not send a policy with no limit, because it would delete everything', async () => {
    await openDialog()
    await typeInto('policy-id', 'weekly')

    await save()

    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(errorText()).toContain('Set at least one of keep latest, older than or not downloaded for.')
  })

  it('does not send a limit that is not a whole number of one or more', async () => {
    await openDialog()
    await typeInto('policy-id', 'weekly')
    await typeInto('policy-keep-latest', '0')

    await save()

    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(errorText()).toContain('Enter a whole number of 1 or more.')
  })

  it('does not send a group filter that is not a valid regular expression', async () => {
    await openDialog()
    await typeInto('policy-id', 'weekly')
    await typeInto('policy-keep-latest', '3')
    await typeInto('policy-group-filter', 'net/(')

    await save()

    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(errorText()).toContain('Not a valid regular expression.')
  })

  it('warns that a policy without repositories affects no repository, and still allows the save', async () => {
    await openDialog()
    await typeInto('policy-id', 'weekly')
    await typeInto('policy-keep-latest', '3')

    await save()

    expect(errorText()).toContain('This policy affects no repository.')
    expect(wrapper.emitted('submit')).toHaveLength(1)
  })

  it('does not allow an id that another policy already uses', async () => {
    await openDialog({ existingIds: ['weekly'] })
    await typeInto('policy-id', 'weekly')
    await typeInto('policy-keep-latest', '3')

    await save()

    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(errorText()).toContain('Another policy already uses this id.')
  })

  it('keeps the id of an existing policy fixed', async () => {
    await openDialog({
      policy: { id: 'weekly', enabled: true, repositories: ['releases'], scope: 'RELEASES', keepLatest: 2, olderThanDays: null, notDownloadedForDays: null, groupFilter: null, schedule: 'manual' }
    })

    expect(field('policy-id').disabled).toBe(true)
  })
})

describe('PolicyDialog submit body', () => {
  it('sends the policy in the wire shape with blanks as null', async () => {
    await openDialog()
    await typeInto('policy-id', ' weekly ')
    await typeInto('policy-keep-latest', '3')
    await chooseRepository('releases')

    await save()

    expect(wrapper.emitted('submit')[0][0]).toEqual({
      id: 'weekly',
      enabled: false,
      repositories: ['releases'],
      scope: 'SNAPSHOTS',
      keepLatest: 3,
      olderThanDays: null,
      notDownloadedForDays: null,
      groupFilter: null,
      schedule: 'manual'
    })
  })

  it('sends a daily schedule as its time', async () => {
    await openDialog()
    await typeInto('policy-id', 'mirror')
    await typeInto('policy-not-downloaded-days', '60')
    document.getElementById('policy-schedule').value = 'daily'
    document.getElementById('policy-schedule').dispatchEvent(new Event('change'))
    await flushPromises()
    await typeInto('policy-schedule-time', '03:30')

    await save()

    expect(wrapper.emitted('submit')[0][0].schedule).toBe('03:30')
  })

  it('refuses a daily schedule without a time', async () => {
    await openDialog()
    await typeInto('policy-id', 'mirror')
    await typeInto('policy-not-downloaded-days', '60')
    document.getElementById('policy-schedule').value = 'daily'
    document.getElementById('policy-schedule').dispatchEvent(new Event('change'))
    await flushPromises()

    await save()

    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(errorText()).toContain('Enter a time as HH:mm')
  })
})
