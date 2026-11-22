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
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import CleanupPoliciesPanel from './CleanupPoliciesPanel.vue'
import { createErrorToast, createSuccessToast } from '../../helpers/toast'

// The client is swapped per test through this holder. The panel reads it through the session.
const session = vi.hoisted(() => ({ client: null as unknown as FakeClient }))

vi.mock('../../store/session', () => ({
  useSession: () => ({ client: { get value() { return session.client } } })
}))

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  createWarningToast: vi.fn(),
  createInfoToast: vi.fn(),
  errorMessage: (error: (Error & { response?: { data?: { message?: string } } }) | undefined) => error?.response?.data?.message ?? error?.message ?? String(error)
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

const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T

// The client answers the way the server does: the settings domain holds the policy list, the
// maven domain holds the repositories, and the cleanup endpoints act on the policy they name.
interface FakeOptions {
  policies?: unknown[]
  runs?: unknown[]
  update?: (name: string, body: { policies: unknown[] }) => Promise<unknown>
  dryRun?: () => Promise<unknown>
  run?: () => Promise<unknown>
}

const fakeClient = ({ policies = [SNAPSHOTS, MIRROR], runs = [], update, dryRun, run }: FakeOptions = {}) => ({
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

type FakeClient = ReturnType<typeof fakeClient>

let wrapper!: VueWrapper

const mountPanel = async () => {
  wrapper = mount(CleanupPoliciesPanel, { attachTo: document.body }) as VueWrapper
  await flushPromises()
  return wrapper
}

// The props of a shared component, read as a record.
const propsOf = (component: VueWrapper) => component.props() as Record<string, unknown>

const buttonLabelled = (label: string) => document.querySelector(`[aria-label="${label}"]`) as HTMLElement
const pageButton = (text: string) => wrapper.findAll('button').find(button => button.text() === text)?.element as HTMLElement

const field = (id: string) => document.getElementById(id) as HTMLInputElement

const typeInto = async (id: string, value: string) => {
  field(id).value = value
  field(id).dispatchEvent(new Event('input'))
  await flushPromises()
}

const clickIn = async (element: Element | null) => {
  (element as HTMLElement).click()
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

  // The panel is a filled card on surface-container-low, so a policy block steps one role up, never the same role.
  it('draws each policy one surface step above its card', async () => {
    await mountPanel()

    const policy = wrapper.find('[data-policy="snapshots-weekly"]')

    expect(policy.classes(), 'a block nested in a card is surface-container').toContain('bg-surface-container')
    expect(policy.classes(), 'not the role of its card').not.toContain('bg-surface-container-low')
  })

  it('sets the policy name as a level 3 heading in the title-small role', async () => {
    await mountPanel()

    const heading = wrapper.get('[data-policy="snapshots-weekly"] h3')

    expect(heading.classes(), 'a level 3 heading takes the title-small role').toContain('md-title-small')
    expect(heading.classes(), 'not the level 2 role').not.toContain('md-title-medium')
  })

  it('draws each policy as a shared card list item on the container tone', async () => {
    await mountPanel()

    const policy = (wrapper.findAllComponents({ name: 'MdCard' }) as VueWrapper[])
      .find(card => card.attributes('data-policy') === 'snapshots-weekly')

    expect((policy?.element as HTMLElement | undefined)?.tagName,'the policy is a list item drawn by MdCard').toBe('LI')
    expect(policy && propsOf(policy).tone,'the policy steps one surface role above its card').toBe('container')
  })
})

describe('CleanupPoliciesPanel section', () => {
  it('titles the card with a level 2 MdSection that keeps the heading id of the panel', async () => {
    await mountPanel()

    const section = wrapper.findComponent({ name: 'MdSection' }) as VueWrapper
    const heading = wrapper.get('#cleanup-policies-title')

    expect(section.exists(), 'the card holds the shared section').toBe(true)
    expect(propsOf(section).level,'the panel is the first level under the h1 of the frame, so its section is level 2').toBe(2)
    expect(heading.element.tagName, 'the title is a level 2 heading').toBe('H2')
    expect(heading.classes(), 'a level 2 title takes the title-medium role').toContain('md-title-medium')
  })

  it('puts the new policy action in the section header', async () => {
    await mountPanel()

    expect(wrapper.get('[data-test="section-header"]').text(), 'the new policy action sits beside the title').toContain('New policy')
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

describe('CleanupPoliciesPanel preview dialog', () => {
  const previewDialog = () => [...document.querySelectorAll<HTMLElement>('[role="dialog"]')]
    .find(dialog => document.getElementById(dialog.getAttribute('aria-labelledby') ?? '')?.textContent?.includes('Preview')) ?? null

  const openFromButton = async () => {
    const opener = buttonLabelled('Preview snapshots-weekly')
    opener.focus()
    await clickIn(opener)
    return opener
  }

  it('names the preview dialog by its heading', async () => {
    await mountPanel()
    await openFromButton()

    const dialog = previewDialog()
    const heading = document.getElementById(dialog?.getAttribute('aria-labelledby') ?? '')

    expect(heading?.textContent?.trim(), 'aria-labelledby resolves to the heading text').toBe('Preview snapshots-weekly')
  })

  it('draws the preview in the shared dialog frame at the 560px cap', async () => {
    await mountPanel()
    await openFromButton()

    const frame = previewDialog()?.querySelector('.vfm__content > div')

    expect(frame, 'the preview uses the shared MdDialog frame').not.toBeNull()
    expect(frame?.classList.contains('max-w-140'), 'the frame keeps the 560px cap of the default size').toBe(true)
    expect(document.body.innerHTML, 'the old 768px frame is gone').not.toContain('max-w-3xl')
  })

  it('moves the focus into the preview dialog when it opens', async () => {
    await mountPanel()
    await openFromButton()

    expect(previewDialog()?.contains(document.activeElement), 'the focus is inside the dialog').toBe(true)
  })

  it('closes the preview on Escape and returns the focus to the Preview button', async () => {
    await mountPanel()
    const opener = await openFromButton()

    previewDialog()?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()

    expect(previewDialog(), 'Escape closes the preview').toBeNull()
    expect(document.activeElement, 'the focus is back on the Preview button').toBe(opener)
  })

  it('returns the focus to the Preview button after Close', async () => {
    await mountPanel()
    const opener = await openFromButton()

    await clickIn([...previewDialog()!.querySelectorAll('button')].find(button => button.textContent?.trim() === 'Close')!)

    expect(document.activeElement, 'the focus is back on the Preview button').toBe(opener)
  })

  it('shows a failed preview as an error alert inside the dialog', async () => {
    session.client = fakeClient({ dryRun: () => Promise.reject(new Error('the policy is gone')) })
    await mountPanel()
    await openFromButton()

    const alert = previewDialog()?.querySelector('[role="alert"]')

    expect(alert?.getAttribute('data-ingot-component'), 'the failure is an MdAlert').toBe('md-alert')
    expect(alert?.getAttribute('data-ingot-state'), 'the alert is in the error state').toBe('error')
    expect(alert?.textContent, 'the alert carries the server message').toContain('the policy is gone')
  })
})

describe('CleanupPoliciesPanel run now', () => {
  it('keeps the run button disabled until the policy id is typed exactly', async () => {
    await mountPanel()
    await clickIn(buttonLabelled('Run snapshots-weekly now'))

    await typeInto('run-now-input', 'snapshots-weekly ')
    expect((document.getElementById('run-now-confirm') as HTMLElement).hasAttribute('disabled')).toBe(true)

    await typeInto('run-now-input', 'snapshots-weekly')
    expect((document.getElementById('run-now-confirm') as HTMLElement).hasAttribute('disabled')).toBe(false)
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
