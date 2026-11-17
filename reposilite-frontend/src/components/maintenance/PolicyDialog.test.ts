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
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import PolicyDialog from './PolicyDialog.vue'
import type { PolicyPayload } from './policy'

let wrapper!: VueWrapper

const openDialog = async (props = {}) => {
  wrapper = mount(PolicyDialog, {
    props: { modelValue: false, policy: null, existingIds: [], repositoryIds: ['releases', 'snapshots'], saving: false, ...props },
    attachTo: document.body
  }) as VueWrapper
  await wrapper.setProps({ modelValue: true })
  await flushPromises()
}

const field = (id: string) => document.getElementById(id) as HTMLInputElement

const typeInto = async (id: string, value: string) => {
  field(id).value = value
  field(id).dispatchEvent(new Event('input'))
  await flushPromises()
}

const chooseRepository = async (id: string) => {
  (document.querySelector(`[data-repository="${id}"]`) as HTMLElement).click()
  await flushPromises()
}

const save = async () => {
  (document.getElementById('policy-save') as HTMLElement).click()
  await flushPromises()
}

const errorText = () => document.querySelector('#policy-form')?.textContent ?? ''

afterEach(() => {
  wrapper?.unmount()
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

    expect((wrapper.emitted('submit') as [PolicyPayload][])[0][0]).toEqual({
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
    field('policy-schedule').value = 'daily'
    field('policy-schedule').dispatchEvent(new Event('change'))
    await flushPromises()
    await typeInto('policy-schedule-time', '03:30')

    await save()

    expect((wrapper.emitted('submit') as [PolicyPayload][])[0][0].schedule).toBe('03:30')
  })

  it('refuses a daily schedule without a time', async () => {
    await openDialog()
    await typeInto('policy-id', 'mirror')
    await typeInto('policy-not-downloaded-days', '60')
    field('policy-schedule').value = 'daily'
    field('policy-schedule').dispatchEvent(new Event('change'))
    await flushPromises()

    await save()

    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(errorText()).toContain('Enter a time as HH:mm')
  })
})

describe('PolicyDialog dialog contract', () => {
  let opener: HTMLButtonElement | null = null

  afterEach(() => {
    opener?.remove()
    opener = null
    document.body.querySelectorAll('.vfm').forEach(element => element.remove())
  })

  it('names the dialog by its heading', async () => {
    await openDialog()

    const dialog = document.querySelector('[role="dialog"]') as HTMLElement
    const name = document.getElementById(dialog.getAttribute('aria-labelledby') ?? '')
    expect(name?.textContent?.trim(), 'the dialog is named by its heading').toBe('New cleanup policy')
  })

  it('moves focus into the dialog when it opens', async () => {
    await openDialog()

    const dialog = document.querySelector('[role="dialog"]') as HTMLElement
    expect(dialog.contains(document.activeElement), 'focus is inside the dialog, not on the page behind it').toBe(true)
  })

  it('returns focus to the button that opened it when it is cancelled', async () => {
    opener = document.createElement('button')
    document.body.append(opener)
    opener.focus()
    await openDialog()

    ;(document.getElementById('policy-cancel') as HTMLElement).click()
    await wrapper.setProps({ modelValue: false })
    await flushPromises()

    expect(document.activeElement, 'focus returns to the opener').toBe(opener)
  })

  it('closes on Escape', async () => {
    await openDialog()

    ;(document.activeElement as HTMLElement).dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()

    expect(wrapper.emitted('update:modelValue'), 'Escape asks the parent to close').toEqual([[false]])
  })

  it('has one filled button, the save action, and a text cancel action', async () => {
    await openDialog()

    const filled = [...document.querySelectorAll<HTMLElement>('.vfm [data-variant="filled"]')]
    expect(filled.map(button => button.textContent?.trim()), 'only the save action is filled').toEqual(['Save policy'])
    expect(document.getElementById('policy-cancel')?.getAttribute('data-variant'), 'cancel is a text button').toBe('text')
  })

  it('shows the limit error as an alert in the error tone', async () => {
    await openDialog()
    await typeInto('policy-id', 'weekly')

    await save()

    const alert = document.querySelector<HTMLElement>('.vfm [role="alert"]')
    expect(alert?.textContent?.trim(), 'the limit error is announced').toContain('Set at least one of keep latest, older than or not downloaded for.')
    expect(alert?.getAttribute('data-tone'), 'the alert is in the error tone').toBe('error')
  })
})
