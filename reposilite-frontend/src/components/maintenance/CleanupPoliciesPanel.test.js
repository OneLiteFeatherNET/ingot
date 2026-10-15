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

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import CleanupPoliciesPanel from './CleanupPoliciesPanel.vue'
import { createErrorToast, createSuccessToast } from '../../helpers/toast'

// The client is swapped per test through this holder. The panel reads it through the session.
const session = vi.hoisted(() => ({ client: null }))

vi.mock('../../store/session', () => ({
  useSession: () => ({ client: { get value() { return session.client } } })
}))

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  createWarningToast: vi.fn(),
  createInfoToast: vi.fn(),
  errorMessage: (error) => error?.response?.data?.message ?? error?.message ?? `${error}`
}))

const SNAPSHOTS = {
  id: 'snapshots-weekly',
  enabled: false,
  repositories: ['snapshots'],
  scope: 'SNAPSHOTS',
  keepLatest: 3,
  olderThanDays: 30,
  notDownloadedForDays: null,
  groupFilter: '',
  schedule: 'manual'
}

const MIRROR = {
  id: 'mirror-nightly',
  enabled: true,
  repositories: ['mirror'],
  scope: 'MIRROR_CACHE',
  keepLatest: null,
  olderThanDays: null,
  notDownloadedForDays: 60,
  groupFilter: 'net/onelitefeather/.*',
  schedule: '03:30'
}

const PREVIEW = {
  policyId: 'snapshots-weekly',
  evaluatedAt: '2026-10-15T10:00:00Z',
  count: 1,
  totalBytes: 1024,
  deletions: [{ repository: 'snapshots', path: 'net/x/app/1.0-SNAPSHOT', version: '1.0-SNAPSHOT', sizeBytes: 1024, reason: 'older than 30 days' }],
  warnings: []
}

const RUN = {
  id: 7,
  policyId: 'snapshots-weekly',
  trigger: 'MANUAL',
  startedAt: '2026-10-15T10:00:00Z',
  finishedAt: '2026-10-15T10:00:02Z',
  status: 'SUCCEEDED',
  deletedCount: 1,
  deletedBytes: 1024,
  failures: []
}

const clone = (value) => JSON.parse(JSON.stringify(value))

// The client answers the way the server does: the settings domain holds the policy list, the
// maven domain holds the repositories, and the cleanup endpoints act on the policy they name.
const fakeClient = ({ policies = [SNAPSHOTS, MIRROR], runs = [], update, dryRun, run } = {}) => ({
  settings: {
    fetch: vi.fn((name) => Promise.resolve({
      data: name === 'cleanup'
        ? { policies: clone(policies) }
        : { repositories: [{ id: 'releases' }, { id: 'snapshots' }, { id: 'mirror' }] }
    })),
    update: vi.fn(update ?? (() => Promise.resolve({ data: '' })))
  },
  cleanup: {
    runs: vi.fn(() => Promise.resolve({ data: clone(runs) })),
    dryRun: vi.fn(dryRun ?? (() => Promise.resolve({ data: clone(PREVIEW) }))),
    run: vi.fn(run ?? (() => Promise.resolve({ data: clone(RUN) })))
  }
})

let wrapper

const mountPanel = async () => {
  wrapper = mount(CleanupPoliciesPanel, { attachTo: document.body })
  await flushPromises()
  return wrapper
}

const buttonLabelled = (label) => document.querySelector(`[aria-label="${label}"]`)
const pageButton = (text) => wrapper.findAll('button').find(button => button.text() === text).element

const field = (id) => document.getElementById(id)

const typeInto = async (id, value) => {
  field(id).value = value
  field(id).dispatchEvent(new Event('input'))
  await flushPromises()
}

const clickIn = async (element) => {
  element.click()
  await flushPromises()
}

const savedPolicies = () => session.client.settings.update.mock.calls[0][1].policies

const submitDialog = async () => {
  await clickIn(document.getElementById('policy-save'))
}

beforeEach(() => {
  session.client = fakeClient()
  vi.mocked(createErrorToast).mockClear()
  vi.mocked(createSuccessToast).mockClear()
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  document.body.innerHTML = ''
})

describe('CleanupPoliciesPanel list', () => {
  it('lists every policy with its scope and its schedule', async () => {
    await mountPanel()

    const text = wrapper.text()

    expect(text).toContain('snapshots-weekly')
    expect(text).toContain('Snapshots')
    expect(text).toContain('Manual only')
    expect(text).toContain('Daily at 03:30')
  })

  it('shows the last run of a policy with its status', async () => {
    session.client = fakeClient({ runs: [RUN] })
    await mountPanel()

    expect(wrapper.text()).toContain('Last run: Succeeded')
  })

  it('says a policy has never run when no run is recorded for it', async () => {
    await mountPanel()

    expect(wrapper.text()).toContain('Never run')
  })
})

describe('CleanupPoliciesPanel save', () => {
  it('writes the whole policy list with the new policy appended', async () => {
    await mountPanel()
    await clickIn(pageButton('New policy'))
    await typeInto('policy-id', 'weekly-releases')
    await typeInto('policy-keep-latest', '5')
    await clickIn(document.querySelector('[data-repository="releases"]'))

    await submitDialog()

    expect(session.client.settings.update).toHaveBeenCalledTimes(1)
    expect(session.client.settings.update.mock.calls[0][0]).toBe('cleanup')
    expect(savedPolicies()).toEqual([
      SNAPSHOTS,
      MIRROR,
      {
        id: 'weekly-releases',
        enabled: false,
        repositories: ['releases'],
        scope: 'SNAPSHOTS',
        keepLatest: 5,
        olderThanDays: null,
        notDownloadedForDays: null,
        groupFilter: null,
        schedule: 'manual'
      }
    ])
  })

  it('reads the current list from the server before it writes', async () => {
    await mountPanel()
    await clickIn(pageButton('New policy'))
    await typeInto('policy-id', 'weekly')
    await typeInto('policy-keep-latest', '5')

    await submitDialog()

    expect(session.client.settings.fetch).toHaveBeenCalledWith('cleanup')
  })

  it('sends nothing when the new policy would delete everything', async () => {
    await mountPanel()
    await clickIn(pageButton('New policy'))
    await typeInto('policy-id', 'everything')

    await submitDialog()

    expect(session.client.settings.update).not.toHaveBeenCalled()
  })

  it('changes only the policy being edited and keeps its id', async () => {
    await mountPanel()
    await clickIn(buttonLabelled('Edit policy snapshots-weekly'))
    await typeInto('policy-keep-latest', '5')

    await submitDialog()

    // A blank group filter is written as null, the same as the other empty limits.
    expect(savedPolicies()).toEqual([{ ...SNAPSHOTS, keepLatest: 5, groupFilter: null }, MIRROR])
  })

  it('turns a policy on from its switch and writes the list', async () => {
    await mountPanel()

    await clickIn(wrapper.findAll('[role="switch"]')[0].element)

    expect(savedPolicies()[0]).toEqual({ ...SNAPSHOTS, enabled: true })
  })

  it('shows the server message in an error toast when the save fails', async () => {
    session.client = fakeClient({
      update: () => Promise.reject(Object.assign(new Error('Request failed'), { response: { status: 400, data: { message: 'Repository releases is read only' } } }))
    })
    await mountPanel()

    await clickIn(buttonLabelled('Edit policy snapshots-weekly'))
    await typeInto('policy-keep-latest', '5')
    await submitDialog()

    expect(createErrorToast).toHaveBeenCalledWith('Repository releases is read only')
  })
})

describe('CleanupPoliciesPanel delete', () => {
  it('removes only that policy from the settings once the user confirms', async () => {
    await mountPanel()
    await clickIn(buttonLabelled('Delete policy snapshots-weekly'))

    await clickIn(document.getElementById('delete-policy-confirm'))

    expect(savedPolicies()).toEqual([MIRROR])
  })

  it('leaves the policy in place when the user cancels', async () => {
    await mountPanel()
    await clickIn(buttonLabelled('Delete policy snapshots-weekly'))

    await clickIn(document.getElementById('delete-policy-cancel'))

    expect(session.client.settings.update).not.toHaveBeenCalled()
  })

  it('does not touch any artifact when a policy is deleted', async () => {
    await mountPanel()
    await clickIn(buttonLabelled('Delete policy snapshots-weekly'))

    await clickIn(document.getElementById('delete-policy-confirm'))

    expect(session.client.cleanup.run).not.toHaveBeenCalled()
    expect(session.client.cleanup.dryRun).not.toHaveBeenCalled()
  })
})

describe('CleanupPoliciesPanel preview', () => {
  it('asks the server for a dry run of the policy and shows the table', async () => {
    await mountPanel()

    await clickIn(buttonLabelled('Preview snapshots-weekly'))

    expect(session.client.cleanup.dryRun).toHaveBeenCalledWith('snapshots-weekly')
    expect(document.querySelector('table')?.textContent).toContain('net/x/app/1.0-SNAPSHOT')
  })

  it('previews a disabled policy as well', async () => {
    await mountPanel()

    const preview = buttonLabelled('Preview snapshots-weekly')

    expect(preview.hasAttribute('disabled')).toBe(false)
    await clickIn(preview)
    expect(session.client.cleanup.dryRun).toHaveBeenCalledTimes(1)
  })
})

describe('CleanupPoliciesPanel run now', () => {
  it('keeps the run button disabled until the policy id is typed exactly', async () => {
    await mountPanel()
    await clickIn(buttonLabelled('Run snapshots-weekly now'))

    await typeInto('run-now-input', 'snapshots-weekly ')
    expect(document.getElementById('run-now-confirm').hasAttribute('disabled')).toBe(true)

    await typeInto('run-now-input', 'snapshots-weekly')
    expect(document.getElementById('run-now-confirm').hasAttribute('disabled')).toBe(false)
  })

  it('starts the run only after the confirmation and reports its result', async () => {
    await mountPanel()
    await clickIn(buttonLabelled('Run snapshots-weekly now'))
    await typeInto('run-now-input', 'snapshots-weekly')

    await clickIn(document.getElementById('run-now-confirm'))

    expect(session.client.cleanup.run).toHaveBeenCalledWith('snapshots-weekly')
    expect(document.body.textContent).toContain('1 artifact deleted')
  })

  it('reloads the run history and tells the parent a run finished', async () => {
    await mountPanel()
    await clickIn(buttonLabelled('Run snapshots-weekly now'))
    await typeInto('run-now-input', 'snapshots-weekly')

    await clickIn(document.getElementById('run-now-confirm'))

    expect(session.client.cleanup.runs).toHaveBeenCalledTimes(2)
    expect(wrapper.emitted('ran')).toHaveLength(1)
  })

  it('runs a disabled policy by hand as well', async () => {
    await mountPanel()

    expect(buttonLabelled('Run snapshots-weekly now').hasAttribute('disabled')).toBe(false)
  })
})
