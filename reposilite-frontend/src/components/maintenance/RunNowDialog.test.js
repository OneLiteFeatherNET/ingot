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
import prettyBytes from 'pretty-bytes'
import RunNowDialog from './RunNowDialog.vue'
import { normalizeRun } from './cleanup'

let wrapper

const openDialog = async (props = {}) => {
  wrapper = mount(RunNowDialog, {
    props: { modelValue: false, policyId: 'snapshots-weekly', running: false, result: null, error: null, ...props },
    attachTo: document.body
  })
  await wrapper.setProps({ modelValue: true })
  await flushPromises()
}

const input = () => document.getElementById('run-now-input')
const runButton = () => document.getElementById('run-now-confirm')

const typeText = async (value) => {
  input().value = value
  input().dispatchEvent(new Event('input'))
  await flushPromises()
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('RunNowDialog confirmation', () => {
  it('keeps the run button disabled while nothing is typed', async () => {
    await openDialog()

    expect(runButton().hasAttribute('disabled')).toBe(true)
  })

  it('keeps the run button disabled while the typed id is only a part of the id', async () => {
    await openDialog()

    await typeText('snapshots')

    expect(runButton().hasAttribute('disabled')).toBe(true)
  })

  it('keeps the run button disabled when the case differs', async () => {
    await openDialog()

    await typeText('Snapshots-Weekly')

    expect(runButton().hasAttribute('disabled')).toBe(true)
  })

  it('enables the run button once the id is typed exactly', async () => {
    await openDialog()

    await typeText('snapshots-weekly')

    expect(runButton().hasAttribute('disabled')).toBe(false)
  })

  it('asks for the run when the confirmed button is pressed', async () => {
    await openDialog()
    await typeText('snapshots-weekly')

    runButton().click()
    await flushPromises()

    expect(wrapper.emitted('confirm')).toHaveLength(1)
  })

  it('clears the typed id each time the dialog opens', async () => {
    await openDialog()
    await typeText('snapshots-weekly')
    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ modelValue: true })
    await flushPromises()

    expect(input().value).toBe('')
  })

  it('shows the failure of the run in the dialog', async () => {
    await openDialog({ error: 'The policy is locked by another run' })

    expect(document.querySelector('[role="alert"]')?.textContent).toContain('The policy is locked by another run')
  })
})

describe('RunNowDialog result', () => {
  it('summarises a finished run with its status, count and bytes', async () => {
    const result = normalizeRun({
      id: 9,
      policyId: 'snapshots-weekly',
      trigger: 'MANUAL',
      startedAt: '2026-10-15T10:00:00Z',
      finishedAt: '2026-10-15T10:00:02Z',
      status: 'PARTIAL',
      deletedCount: 2,
      deletedBytes: 2048,
      failures: ['net/x/app/0.9-SNAPSHOT: the file is locked']
    })
    await openDialog({ result })

    const text = document.body.textContent

    expect(text).toContain('Partly failed')
    expect(text).toContain(`2 artifacts deleted, ${prettyBytes(2048)}`)
    expect(text).toContain('net/x/app/0.9-SNAPSHOT: the file is locked')
  })

  it('no longer offers the run button once a result is shown', async () => {
    const result = normalizeRun({ id: 9, policyId: 'snapshots-weekly', startedAt: '2026-10-15T10:00:00Z', status: 'SUCCEEDED' })
    await openDialog({ result })

    expect(runButton()).toBeNull()
  })
})
