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

import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { configurationStoreKey } from '../../store/configuration'
import RepositoryDetail from './RepositoryDetail.vue'
import { loadedStore, repositorySchema, type RepositoryRecord, type RepositorySchema } from './fixtures/repositories'

type LoadedStore = Awaited<ReturnType<typeof loadedStore>>

// The session answers as an instance administrator, so the access card asks the identity endpoint.
const holder = vi.hoisted(() => ({
  session: {
    client: { value: null as null | { identity: { repositoryAccess: () => Promise<{ data: unknown }> } } },
    isManager: { value: true }
  }
}))
vi.mock('../../store/session', () => ({ useSession: () => holder.session }))
vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  createInfoToast: vi.fn(),
  errorMessage: (error: unknown) => (error as { message?: string } | null | undefined)?.message ?? String(error)
}))
vi.mock('../../store/placeholders', () => ({
  default: () => ({ basePath: '/', productionUrl: 'https://repo.example.net/' }),
  frontendSecretsHidden: () => false
}))

beforeEach(() => {
  holder.session.client = {
    value: {
      identity: {
        repositoryAccess: () => Promise.resolve({ data: { visibility: 'PUBLIC', entries: [] } })
      }
    }
  }
})

// jsdom has no <dialog>; the one-of renderer of the schema uses it.
HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') }

let wrapper: VueWrapper

afterEach(() => {
  wrapper?.unmount()
})

const mountDetail = async ({ id = 'releases', schema, store, address }: { id?: string; schema?: RepositorySchema; store?: LoadedStore; address?: string } = {}) => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { name: 'settings-repositories', path: '/settings/repositories', component: { render: () => null } },
      { name: 'settings-repository', path: '/settings/repositories/:id', component: { render: () => null } }
    ]
  })
  await router.push(address ?? `/settings/repositories/${encodeURIComponent(id)}`)
  const configuration = store ?? await loadedStore({ schema })
  wrapper = mount(RepositoryDetail, {
    props: { id },
    global: {
      plugins: [router],
      provide: { [configurationStoreKey]: configuration }
    },
    attachTo: document.body
  })
  await flushPromises()
  return { wrapper, router, store: configuration }
}

// The entry of the draft, read the way the page reads it, so each test compares what was stored.
const entryOf = (store: LoadedStore, id: string) => (store.configurations.value.maven.repositories as RepositoryRecord[]).find(entry => entry.id === id)!
const radioNamed = (label: string) => document.querySelector<HTMLInputElement>(`input[type="radio"][value="${label}"]`)!
const writePolicyRadios = () => Array.from(document.querySelectorAll<HTMLInputElement>('fieldset input[type="radio"]'))
const buttonNamed = (text: string) => Array.from(document.querySelectorAll<HTMLButtonElement>('button')).find(button => button.textContent?.trim() === text)!

describe('the repository detail page', () => {
  it('names the repository in the first heading of the detail, the second level heading with its id', async () => {
    await mountDetail({ id: 'releases' })

    expect(wrapper.find('h2').text(), 'the first heading of the detail is the id').toBe('releases')
  })

  it('draws the id as the heading of the header of the detail', async () => {
    await mountDetail({ id: 'releases' })

    const name = wrapper.get('[data-test="repository-name"]').element
    expect(name.tagName, 'the id is a second level heading').toBe('H2')
    expect(name.closest('header'), 'the heading sits in the header of the detail').not.toBeNull()
  })

  it('starts the headings of the detail at the second level, under the page heading', async () => {
    await mountDetail({ id: 'releases' })

    const levels = wrapper.findAll('h1, h2, h3, h4, h5, h6').map(heading => Number(heading.element.tagName.slice(1)))
    expect(levels.length, 'the detail has section headings').toBeGreaterThan(0)
    expect(Math.min(...levels), 'no level one heading in the detail').toBeGreaterThanOrEqual(2)
    expect(levels[0], 'the first heading is the second level').toBe(2)
  })

  it('draws no back button of its own, the page frame carries the way back', async () => {
    await mountDetail({ id: 'releases' })

    const backs = Array.from(document.querySelectorAll('button, a')).map(control => control.textContent.trim()).filter(text => text.startsWith('Back to'))
    expect(backs, 'no Back to control in the page content').toEqual([])
  })

  it('keeps the type switch above the tabs and the access card on Access', async () => {
    await mountDetail({ id: 'releases' })

    expect(document.querySelector('#entry-detail-type'), 'the type switch is shown').not.toBeNull()
    await chooseTab('Access')
    expect(document.querySelector('[data-test="repository-access"]'), 'the access card is shown to an administrator').not.toBeNull()
  })

  it('offers the Write policy radio group on a hosted repository', async () => {
    await mountDetail({ id: 'releases', address: '/settings/repositories/releases?tab=policy' })

    const choices = writePolicyRadios().map(radio => radio.value)
    expect(choices, 'the three choices of the write policy').toEqual(['allow', 'allowOnce', 'readOnly'])
  })

  it('offers no Write policy on a proxy', async () => {
    await mountDetail({ id: 'central' })

    expect(writePolicyRadios().length, 'a proxy has no write policy').toBe(0)
  })

  it('offers only Allow and Allow once when the schema has no readOnly field', async () => {
    await mountDetail({ id: 'releases', schema: repositorySchema({ redeployment: true }), address: '/settings/repositories/releases?tab=policy' })

    expect(writePolicyRadios().map(radio => radio.value), 'Read only needs the readOnly field').toEqual(['allow', 'allowOnce'])
  })

  it('explains each write policy choice in text', async () => {
    await mountDetail({ id: 'releases', address: '/settings/repositories/releases?tab=policy' })

    const text = wrapper.text()
    expect(text, 'Allow is explained').toContain('Deploys are accepted.')
    expect(text, 'Allow once is explained, with its status code').toContain('refused with 409.')
    expect(text, 'Read only is explained, with its status codes').toContain('refused with 405 Method Not Allowed.')
  })

  it('offers the Version policy field only when the schema has it', async () => {
    await mountDetail({ id: 'releases', address: '/settings/repositories/releases?tab=policy' })
    expect(wrapper.text(), 'the field is drawn from the schema').toContain('Version policy')

    wrapper.unmount()
    await mountDetail({ id: 'releases', schema: repositorySchema({ redeployment: true, readOnly: true }), address: '/settings/repositories/releases?tab=policy' })
    expect(wrapper.text(), 'no field without the schema field').not.toContain('Version policy')
  })

  it('stores Allow once as redeployment false and adds no readOnly key', async () => {
    const { store } = await mountDetail({ id: 'snapshots', address: '/settings/repositories/snapshots?tab=policy' })

    radioNamed('allowOnce').click()
    await flushPromises()

    const entry = entryOf(store, 'snapshots')
    expect(entry.redeployment, 'redeployment is switched off').toBe(false)
    expect(Object.keys(entry), 'no readOnly key is added').not.toContain('readOnly')
  })

  it('keeps redeployment through Read only, and Allow once takes the repository out of read only', async () => {
    const { store } = await mountDetail({ id: 'snapshots', address: '/settings/repositories/snapshots?tab=policy' })

    radioNamed('readOnly').click()
    await flushPromises()
    expect(entryOf(store, 'snapshots').readOnly, 'read only is stored').toBe(true)
    expect(entryOf(store, 'snapshots').redeployment, 'redeployment is kept as it was').toBe(true)

    radioNamed('allowOnce').click()
    await flushPromises()
    expect(entryOf(store, 'snapshots').readOnly, 'read only is no longer set').not.toBe(true)
  })

  it('writes only the type key when a derived type is declared', async () => {
    const { store } = await mountDetail({ id: 'central' })
    const before = entryOf(store, 'central')

    buttonNamed('Declare type').click()
    await flushPromises()

    const after = entryOf(store, 'central')
    const changed = Object.keys({ ...before, ...after }).filter(key => JSON.stringify(before[key]) !== JSON.stringify(after[key]))
    expect(changed, 'only the type key changes').toEqual(['type'])
    expect(after.type, 'the derived type is written').toBe('PROXY')
  })

  it('asks first when declaring the type would drop empty mirrored entries', async () => {
    const store = await loadedStore()
    store.setEntry('maven', 'repositories', 2, {
      id: 'central',
      visibility: 'PUBLIC',
      proxied: [{ reference: 'https://repo.example.org/maven' }, { reference: '/' }]
    })
    await mountDetail({ id: 'central', store })

    buttonNamed('Declare type').click()
    await flushPromises()

    expect(document.querySelector('[data-test="declare-type-dialog"]'), 'the dialog opens').not.toBeNull()
    expect(document.body.textContent, 'the number of entries is named').toContain('1 empty entry will be removed.')
    expect(entryOf(store, 'central').type, 'nothing changes before the confirmation').toBeUndefined()

    buttonNamed('Declare Proxy').click()
    await flushPromises()
    expect(entryOf(store, 'central').type, 'the type is written after the confirmation').toBe('PROXY')
    expect((entryOf(store, 'central').proxied as unknown[]).length, 'the empty entry is gone').toBe(1)
  })

  it('shows the Mixed warning and offers no declare action', async () => {
    await mountDetail({ id: 'mixed' })

    expect(wrapper.text(), 'the warning is text').toContain('References local and remote repositories. Split it into a group and a proxy.')
    expect(Array.from(document.querySelectorAll('button')).map(button => button.textContent.trim()), 'no declare action')
      .not.toContain('Declare type')
  })

  it('shows a not found pane for an id that does not exist, with no link of its own back to the list', async () => {
    await mountDetail({ id: 'nowhere' })

    expect(wrapper.find('h2').text(), 'the pane says it is not found').toBe('Repository not found')
    expect(wrapper.find('a[href="/settings/repositories"]').exists(), 'the page frame carries the way back').toBe(false)
  })
})

// The six tabs of the repository page, in the order of the design (D5), and the keys of the address (D7).
const TAB_LABELS = ['General', 'Storage', 'Access', 'Mirror and proxy', 'Policy', 'Advanced']

const tabElements = () => wrapper.findAll('[role="tab"]')
const selectedLabel = () => tabElements().find(tab => tab.attributes('aria-selected') === 'true')?.text()
const chooseTab = async (label: string) => {
  await tabElements().find(tab => tab.text() === label)!.trigger('click')
  await flushPromises()
}
// The control a label names. The fields of the form are labelled, so the label is the way to reach a field.
const fieldLabelled = (label: string) => {
  const found = Array.from(document.querySelectorAll('label')).find(item => item.textContent?.trim() === label)
  return (found?.control ?? null) as HTMLInputElement | null
}
const typeInto = async (label: string, value: string) => {
  const field = fieldLabelled(label)!
  field.value = value
  field.dispatchEvent(new Event('input'))
  await flushPromises()
}
// Replaces the releases entry of the draft with a copy that has the given values.
const setReleases = (store: LoadedStore, values: Record<string, unknown>) => {
  const repositories = store.configurations.value.maven.repositories as RepositoryRecord[]
  const position = repositories.findIndex(entry => entry.id === 'releases')
  store.setEntry('maven', 'repositories', position, { ...repositories[position], ...values })
}

describe('the repository tabs', () => {
  it('draws the six tabs in the order of the design', async () => {
    await mountDetail({ id: 'releases' })

    expect(tabElements().map(tab => tab.text()), 'the six tabs, in order').toEqual(TAB_LABELS)
  })

  it('gives each tab the tab role, aria-selected, a panel it controls and a roving tab index', async () => {
    await mountDetail({ id: 'releases' })

    const tabs = tabElements()
    expect(tabs.map(tab => tab.attributes('aria-selected')), 'General is selected by default').toEqual(['true', 'false', 'false', 'false', 'false', 'false'])
    expect(tabs.map(tab => tab.attributes('tabindex')), 'only the selected tab is in the tab order').toEqual(['0', '-1', '-1', '-1', '-1', '-1'])
    for (const tab of tabs) {
      const panel = wrapper.get(`#${tab.attributes('aria-controls')}`)
      expect(panel.attributes('role'), 'each tab controls a tab panel').toBe('tabpanel')
      expect(panel.attributes('aria-labelledby'), 'the panel is labelled by its tab').toBe(tab.attributes('id'))
    }
  })

  it('names the tab list for the repository settings', async () => {
    await mountDetail({ id: 'releases' })

    expect(wrapper.get('[role="tablist"]').attributes('aria-label')).toBe('Repository settings sections')
  })

  it('moves the selection and the focus with Right and Left, wrapping at the ends', async () => {
    await mountDetail({ id: 'releases' })

    await tabElements()[0].trigger('keydown', { key: 'ArrowRight' })
    await flushPromises()
    expect(selectedLabel(), 'Right selects the next tab').toBe('Storage')
    expect(document.activeElement?.textContent?.trim(), 'the focus follows the selection').toBe('Storage')

    await tabElements()[1].trigger('keydown', { key: 'ArrowLeft' })
    await flushPromises()
    expect(selectedLabel(), 'Left selects the previous tab').toBe('General')

    await tabElements()[0].trigger('keydown', { key: 'ArrowLeft' })
    await flushPromises()
    expect(selectedLabel(), 'Left on the first tab wraps to the last').toBe('Advanced')

    await tabElements()[5].trigger('keydown', { key: 'ArrowRight' })
    await flushPromises()
    expect(selectedLabel(), 'Right on the last tab wraps to the first').toBe('General')
  })

  it('moves the selection to the first and the last tab with Home and End', async () => {
    await mountDetail({ id: 'releases' })

    await tabElements()[0].trigger('keydown', { key: 'End' })
    await flushPromises()
    expect(selectedLabel(), 'End selects the last tab').toBe('Advanced')

    await tabElements()[5].trigger('keydown', { key: 'Home' })
    await flushPromises()
    expect(selectedLabel(), 'Home selects the first tab').toBe('General')
  })

  it('opens the tab named by the tab query, Storage for tab=storage', async () => {
    await mountDetail({ id: 'releases', address: '/settings/repositories/releases?tab=storage' })

    expect(selectedLabel(), 'the Storage tab is selected').toBe('Storage')
    expect(wrapper.text(), 'the Storage fields are drawn').toContain('File system')
  })

  it('opens General for a tab key that is not one of the six', async () => {
    await mountDetail({ id: 'releases', address: '/settings/repositories/releases?tab=unknown' })

    expect(selectedLabel(), 'an unknown key opens General').toBe('General')
  })

  it('replaces the history entry when the tab changes, so back does not step through the tabs', async () => {
    const { router } = await mountDetail({ id: 'releases' })
    const push = vi.spyOn(router, 'push')
    const replace = vi.spyOn(router, 'replace')

    await chooseTab('Storage')

    expect(push, 'no history entry is added').not.toHaveBeenCalled()
    expect(replace, 'the address is replaced').toHaveBeenCalled()
    expect(router.currentRoute.value.query.tab, 'the address names the tab').toBe('storage')
  })

  it('keeps the delete action below the tabs on every tab', async () => {
    await mountDetail({ id: 'releases' })

    for (const label of TAB_LABELS) {
      await chooseTab(label)
      expect(buttonNamed('Delete repository'), `the delete action is drawn on ${label}`).toBeDefined()
    }
  })

  it('shows the write policy on the Policy tab and not on General', async () => {
    await mountDetail({ id: 'releases' })
    expect(writePolicyRadios().length, 'no write policy on General').toBe(0)

    await chooseTab('Policy')

    expect(writePolicyRadios().map(radio => radio.value), 'the write policy is on the Policy tab').toEqual(['allow', 'allowOnce', 'readOnly'])
  })

  it('keeps a value typed on General when the tab moves to Storage and back', async () => {
    const { store } = await mountDetail({ id: 'releases' })

    await typeInto('Description', 'Typed before the switch')
    await chooseTab('Storage')
    await chooseTab('General')

    expect(fieldLabelled('Description')?.value, 'the typed value is shown after the return').toBe('Typed before the switch')
    expect(entryOf(store, 'releases').description, 'the typed value is in the draft').toBe('Typed before the switch')
  })

  it('keeps the values of the other tabs in the saved entry when a field of General changes', async () => {
    const store = await loadedStore()
    setReleases(store, { resolutionCacheMaxEntries: 7 })
    await mountDetail({ id: 'releases', store })

    await typeInto('Description', 'Changed on General')

    expect(entryOf(store, 'releases').resolutionCacheMaxEntries, 'the Advanced value is kept').toBe(7)
    expect(entryOf(store, 'releases').description).toBe('Changed on General')
  })

  it('counts a problem on another tab on that tab label', async () => {
    const store = await loadedStore()
    setReleases(store, { visibility: 'SECRET' })
    await mountDetail({ id: 'releases', store })

    expect(tabElements()[2].text(), 'the Access tab names its problem').toBe('Access, 1 problem')
    expect(tabElements()[1].text(), 'a tab without a problem keeps its label').toBe('Storage')
  })

  it('keeps the tab of the address when the id is changed', async () => {
    const { router } = await mountDetail({ id: 'releases', address: '/settings/repositories/releases?tab=general' })

    await typeInto('Id', 'renamed')

    expect(router.currentRoute.value.params.id, 'the address follows the new id').toBe('renamed')
    expect(router.currentRoute.value.query.tab, 'the tab of the address is kept').toBe('general')
  })
})

// The fields of a repository in the order the server declares them, with the titles the server gives them.
const declaredOrderSchema = (): RepositorySchema => ({
  type: 'object',
  title: 'Maven',
  properties: {
    repositories: {
      title: 'Repositories',
      description: 'List of Maven repositories.',
      type: 'array',
      items: {
        type: 'object',
        title: 'Repository',
        properties: {
          id: { type: 'string', title: 'Id' },
          visibility: { type: 'string', enum: ['PUBLIC', 'HIDDEN', 'PRIVATE'], title: 'Visibility' },
          description: { type: 'string', title: 'Description' },
          tags: { type: 'array', items: { type: 'string' }, title: 'Tags' },
          redeployment: { type: 'boolean', title: 'Redeployment' },
          preserveSnapshots: { type: 'boolean', title: 'Preserved snapshots' },
          storageProvider: {
            title: 'Storage provider',
            oneOf: [
              { type: 'object', title: 'File system Storage Provider', properties: { type: { type: 'string', const: 'fs' } }, additionalProperties: false },
              { type: 'object', title: 'S3 Storage Provider', properties: { type: { type: 'string', const: 's3' } }, additionalProperties: false }
            ]
          },
          storagePolicy: { type: 'string', enum: ['PRIORITIZE_UPSTREAM_METADATA', 'STRICT'], title: 'Storage policy' },
          metadataMaxAge: { type: 'integer', title: 'Max age of metadata file' },
          resolutionCacheMaxEntries: { type: 'integer', title: 'Resolution cache size' },
          protectedGroupPrefixes: { type: 'array', items: { type: 'string' }, title: 'Protected group prefixes' },
          uploadChecksumPolicy: { type: 'string', enum: ['IGNORE', 'WARN', 'FAIL'], title: 'Upload checksum policy' },
          versionPolicy: { type: 'string', enum: ['RELEASE', 'SNAPSHOT', 'MIXED'], title: 'Version policy' },
          readOnly: { type: 'boolean', title: 'Read-only' },
          format: { type: 'string', title: 'Format' },
          proxied: { type: 'array', title: 'Mirrored repositories', items: { type: 'object', properties: { reference: { type: 'string', title: 'Reference' } } } }
        }
      }
    }
  }
})

// The position of each text in the drawn form of a tab, so the test reads the order the admin sees.
const textPositions = (texts: string[]) => {
  const drawn = wrapper.get('[data-test="entry-form"]').text()
  return texts.map(text => drawn.indexOf(text))
}

describe('the order of the fields on each tab', () => {
  it('draws the storage provider before the preserved snapshots flag on Storage', async () => {
    await mountDetail({ id: 'releases', schema: declaredOrderSchema(), address: '/settings/repositories/releases?tab=storage' })

    const [provider, snapshots] = textPositions(['Storage provider', 'Preserved snapshots'])
    expect(provider, 'the storage provider is drawn').toBeGreaterThanOrEqual(0)
    expect(snapshots, 'the flag is drawn').toBeGreaterThanOrEqual(0)
    expect(provider, 'the provider comes before the flag').toBeLessThan(snapshots)
  })

  it('draws the version policy before the upload checksum policy on Policy', async () => {
    await mountDetail({ id: 'releases', schema: declaredOrderSchema(), address: '/settings/repositories/releases?tab=policy' })

    const [version, checksum] = textPositions(['Version policy', 'Upload checksum policy'])
    expect(version, 'the version policy is drawn').toBeGreaterThanOrEqual(0)
    expect(checksum, 'the checksum policy is drawn').toBeGreaterThanOrEqual(0)
    expect(version, 'the version policy comes before the checksum policy').toBeLessThan(checksum)
  })

  it('draws the mirrored repositories before the storage policy and the protected prefixes on Mirror and proxy', async () => {
    await mountDetail({ id: 'central', schema: declaredOrderSchema(), address: '/settings/repositories/central?tab=mirror' })

    const [mirrors, storage, protectedPrefixes] = textPositions(['Mirrored repositories', 'Storage policy', 'Protected group prefixes'])
    expect(mirrors, 'the mirrors are drawn').toBeGreaterThanOrEqual(0)
    expect(storage, 'the storage policy is drawn').toBeGreaterThanOrEqual(0)
    expect(protectedPrefixes, 'the protected prefixes are drawn').toBeGreaterThanOrEqual(0)
    expect(mirrors, 'the mirrors come before the storage policy').toBeLessThan(storage)
    expect(storage, 'the storage policy comes before the protected prefixes').toBeLessThan(protectedPrefixes)
  })
})
