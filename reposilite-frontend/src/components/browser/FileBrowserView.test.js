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
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import FileBrowserView from './FileBrowserView.vue'
import BrowserUpload from './FileUpload.vue'
import { sessionState } from '../../store/session'
import { DETAILS_COLLAPSED_KEY } from '../card/detailsCollapsed'

// The session store is replaced per file; each test swaps in its own client below.
vi.mock('../../store/session', async () => {
  const { ref } = await import('vue')
  const state = {
    details: ref({ routes: [] }),
    client: ref(null),
    sessionReady: ref(true),
    canWrite: ref(false),
    isManager: ref(false)
  }
  return {
    sessionState: state,
    useSession: () => ({
      details: state.details,
      client: state.client,
      sessionReady: state.sessionReady,
      hasPermissionTo: () => state.canWrite.value,
      isManager: state.isManager,
      token: ref({ name: '', secret: '' })
    })
  }
})

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/releases/net/example' }),
  useRouter: () => ({ push: () => {} })
}))

vi.mock('../../store/qualifier', () => ({
  default: () => ({
    qualifier: { path: 'releases/net/example', watchable: 0 },
    getParentPath: () => '/releases/net',
    refreshQualifier: () => {},
    redirectTo: () => {}
  })
}))

// The card stands in with one button that reports a fold or an unfold, as the real card does.
vi.mock('../card/SnippetsCard.vue', async () => {
  const { h } = await import('vue')
  return {
    default: {
      name: 'SnippetsCard',
      emits: ['update:collapsed'],
      render() {
        return h('button', {
          type: 'button',
          'data-testid': 'card-fold',
          onClick: () => this.$emit('update:collapsed', true)
        }, 'Fold card')
      }
    }
  }
})

const networkError = () => ({ message: 'Network Error', request: {} })
const httpError = (status, message = '') => ({ response: { status, data: { message } } })

const clientWithDetails = (details) => ({
  maven: {
    details,
    download: () => Promise.reject(new Error('not used')),
    delete: () => Promise.reject(new Error('not used'))
  }
})

// Each test unmounts what it mounted, so a later change to the shared session cannot reach
// a view that a previous test left behind.
const mounted = []

const mountView = () => {
  const wrapper = mount(FileBrowserView, {
    props: { qualifier: { path: 'releases/net/example', watchable: 0 } },
    global: { stubs: { 'router-link': RouterLinkStub } }
  })
  mounted.push(wrapper)
  return wrapper
}

const buttonWithText = (wrapper, text) =>
  wrapper.findAll('button').find(button => button.text() === text)

describe('FileBrowserView listing errors', () => {
  beforeEach(() => {
    sessionState.details.value = { routes: [] }
    sessionState.client.value = null
  })

  afterEach(() => {
    mounted.splice(0).forEach(wrapper => wrapper.unmount())
  })

  it('shows the cannot-reach state with a retry when the server does not answer', async () => {
    sessionState.client.value = clientWithDetails(() => Promise.reject(networkError()))

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text(), 'a network failure must not leave the skeleton on screen').toContain('Cannot reach the repository')
    expect(buttonWithText(wrapper, 'Retry'), 'the cannot-reach state offers a retry').toBeDefined()
  })

  it('loads the listing again when retry is pressed', async () => {
    const details = vi.fn()
      .mockRejectedValueOnce(networkError())
      .mockResolvedValueOnce({ data: { files: [{ name: 'example-1.0.jar', type: 'FILE' }] } })
    sessionState.client.value = clientWithDetails(details)

    const wrapper = mountView()
    await flushPromises()
    await buttonWithText(wrapper, 'Retry').trigger('click')
    await flushPromises()

    expect(details, 'retry must request the directory a second time').toHaveBeenCalledTimes(2)
    expect(wrapper.text(), 'the listing appears once the retry succeeds').toContain('example-1.0.jar')
  })

  it('reports a missing directory as not found, not as unreachable', async () => {
    sessionState.client.value = clientWithDetails(() => Promise.reject(httpError(404, 'Not found')))

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text(), 'a 404 names the missing directory').toContain('Directory not found')
    expect(wrapper.text(), 'a 404 is not a connectivity problem').not.toContain('Cannot reach the repository')
  })

  it('reports a forbidden directory as an access problem', async () => {
    sessionState.client.value = clientWithDetails(() => Promise.reject(httpError(403, 'Forbidden')))

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text(), 'a 403 explains the missing access').toContain('do not have access to this directory')
    expect(wrapper.text(), 'a 403 is not reported as a missing directory').not.toContain('Directory not found')
  })

  it('reports other server failures with the server message', async () => {
    sessionState.client.value = clientWithDetails(() => Promise.reject(httpError(500, 'Storage is offline')))

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text(), 'a 500 gets its own heading').toContain('Cannot load this directory')
    expect(wrapper.text(), 'the server message is shown').toContain('Storage is offline')
    expect(wrapper.text(), 'a 500 is not reported as a missing directory').not.toContain('Directory not found')
  })

  it('does not crash on a failure without a response object', async () => {
    sessionState.client.value = clientWithDetails(() => Promise.reject(new Error('boom')))

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text(), 'a bare error still ends the loading state').toContain('Cannot load this directory')
  })
})

describe('FileBrowserView empty directories', () => {
  beforeEach(() => {
    sessionState.details.value = { routes: [] }
  })

  afterEach(() => {
    mounted.splice(0).forEach(wrapper => wrapper.unmount())
  })

  it('reports the hidden checksum files when they are the only files in the directory', async () => {
    sessionState.client.value = clientWithDetails(() => Promise.resolve({
      data: { files: [
        { name: 'example-1.0.jar.sha1', type: 'FILE' },
        { name: 'example-1.0.jar.md5', type: 'FILE' }
      ] }
    }))

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text(), 'the hidden checksum files are counted, not hidden silently').toContain('2 utility files')
    expect(wrapper.text(), 'the directory is not reported as empty').not.toContain('Directory is empty')
  })

  it('reports a plainly empty directory as empty', async () => {
    sessionState.client.value = clientWithDetails(() => Promise.resolve({ data: { files: [] } }))

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text(), 'an empty directory says so').toContain('Directory is empty')
  })
})

describe('FileBrowserView session start-up', () => {
  beforeEach(() => {
    sessionState.details.value = undefined
    sessionState.sessionReady.value = true
    sessionState.canWrite.value = false
  })

  afterEach(() => {
    mounted.splice(0).forEach(wrapper => wrapper.unmount())
  })

  it('does not request the listing before the stored session has been checked', async () => {
    sessionState.sessionReady.value = false
    const details = vi.fn(() => Promise.resolve({ data: { files: [] } }))
    sessionState.client.value = clientWithDetails(details)

    mountView()
    await flushPromises()

    expect(details, 'the answer about the session decides what the listing may show').not.toHaveBeenCalled()
  })

  it('requests the listing once when the stored session is restored', async () => {
    sessionState.sessionReady.value = false
    const details = vi.fn(() => Promise.resolve({ data: { files: [] } }))
    sessionState.client.value = clientWithDetails(details)
    mountView()
    await flushPromises()

    sessionState.details.value = { routes: [] }
    sessionState.sessionReady.value = true
    await flushPromises()

    expect(details, 'a restored session must not cause the listing to be requested twice').toHaveBeenCalledTimes(1)
  })

  it('clears the loading state when a listing fails without a signed-in session', async () => {
    sessionState.client.value = clientWithDetails(() => Promise.reject(httpError(404, 'Not found')))

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text(), 'the failure is shown in place of the listing').toContain('Directory not found')
    expect(wrapper.find('.skeleton-bars').exists(), 'no skeleton may stay on screen after the failure').toBe(false)
  })
})

describe('FileBrowserView upload area', () => {
  beforeEach(() => {
    sessionState.details.value = { routes: [] }
    sessionState.sessionReady.value = true
    sessionState.canWrite.value = true
  })

  afterEach(() => {
    mounted.splice(0).forEach(wrapper => wrapper.unmount())
  })

  it('offers an upload for an existing directory the user may write to', async () => {
    sessionState.client.value = clientWithDetails(() => Promise.resolve({ data: { files: [] } }))

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.findComponent(BrowserUpload).exists()).toBe(true)
  })

  it('hides the upload for a directory that does not exist', async () => {
    sessionState.client.value = clientWithDetails(() => Promise.reject(httpError(404, 'Not found')))

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.findComponent(BrowserUpload).exists(), 'nothing can be uploaded into a missing directory').toBe(false)
  })
})

describe('FileBrowserView toolbar', () => {
  beforeEach(() => {
    sessionState.details.value = { routes: [] }
    sessionState.client.value = clientWithDetails(() => new Promise(() => {}))
  })

  afterEach(() => {
    mounted.splice(0).forEach(wrapper => wrapper.unmount())
  })

  it('gives the view-mode control a tooltip as well as a name', () => {
    const wrapper = mountView()
    const toggle = wrapper.find('button[aria-label="Compact list"]')

    expect(toggle.exists(), 'the view-mode control is a named button').toBe(true)
    expect(toggle.attributes('title'), 'hovering the control names it').toBe('Compact list')
  })

  it('gives the adjustments control a tooltip as well as a name', () => {
    const wrapper = mountView()
    const adjustments = wrapper.find('button[aria-label="File browser adjustments"]')

    expect(adjustments.attributes('title'), 'hovering the control names it').toBe('File browser adjustments')
  })
})

describe('FileBrowserView group references', () => {
  const rootMounted = []

  const clientWith = ({ details, members }) => ({
    maven: {
      details,
      members,
      download: () => Promise.reject(new Error('not used')),
      delete: () => Promise.reject(new Error('not used'))
    }
  })

  const mountAt = (path) => {
    const wrapper = mount(FileBrowserView, {
      props: { qualifier: { path, watchable: 0 } },
      global: { stubs: { 'router-link': RouterLinkStub } }
    })
    rootMounted.push(wrapper)
    return wrapper
  }

  beforeEach(() => {
    sessionState.details.value = { routes: [] }
  })

  afterEach(() => {
    rootMounted.splice(0).forEach(wrapper => wrapper.unmount())
  })

  it('asks the repository for its references when its root is browsed', async () => {
    const members = vi.fn().mockResolvedValue({ data: { members: ['releases', 'snapshots'] } })
    sessionState.client.value = clientWith({ details: () => Promise.resolve({ data: { files: [] } }), members })

    mountAt('public')
    await flushPromises()

    expect(members, 'the root of the repository is asked for its references').toHaveBeenCalledWith('public')
  })

  it('shows the references of a group root with no files of its own', async () => {
    const members = () => Promise.resolve({ data: { members: ['releases', 'snapshots'] } })
    sessionState.client.value = clientWith({ details: () => Promise.resolve({ data: { files: [] } }), members })

    const wrapper = mountAt('public')
    await flushPromises()

    expect(wrapper.text(), 'the references are shown').toContain('Includes')
    expect(wrapper.text(), 'the references are named').toContain('snapshots')
    expect(wrapper.text(), 'the group is not called empty').not.toContain('Directory is empty')
  })

  it('does not ask for references below the repository root', async () => {
    const members = vi.fn().mockResolvedValue({ data: { members: [] } })
    sessionState.client.value = clientWith({ details: () => Promise.resolve({ data: { files: [] } }), members })

    mountAt('releases/net/example')
    await flushPromises()

    expect(members, 'a deeper directory has no references of its own').not.toHaveBeenCalled()
  })

  it('keeps the empty-directory text when the references cannot be read', async () => {
    const members = () => Promise.reject(networkError())
    sessionState.client.value = clientWith({ details: () => Promise.resolve({ data: { files: [] } }), members })

    const wrapper = mountAt('public')
    await flushPromises()

    expect(wrapper.text(), 'a failed reference lookup leaves the listing as it was').toContain('Directory is empty')
  })
})

describe('FileBrowserView details layout', () => {
  // The side-by-side grid of the wide layout, as the view class names it.
  const SIDE_BY_SIDE = 'lg:grid-cols-[minmax(0,1fr)_minmax(320px,400px)]'

  // The view starts with a comment, so its root is a fragment: the grid is found by its class.
  const layoutGrid = (wrapper) => {
    const grid = wrapper.find('.container.grid')
    expect(grid.exists(), 'the details and listing share one grid').toBe(true)
    return grid
  }

  beforeEach(() => {
    localStorage.clear()
    sessionState.details.value = { routes: [] }
    sessionState.client.value = clientWithDetails(() => Promise.resolve({ data: { files: [] } }))
  })

  afterEach(() => {
    mounted.splice(0).forEach(wrapper => wrapper.unmount())
    localStorage.clear()
  })

  it('keeps the listing beside the details card while the card is open', () => {
    localStorage.setItem(DETAILS_COLLAPSED_KEY, 'false')

    const wrapper = mountView()

    expect(layoutGrid(wrapper).classes(), 'the wide layout has the details column').toContain(SIDE_BY_SIDE)
  })

  it('gives the listing the full width while the details card is folded', () => {
    localStorage.setItem(DETAILS_COLLAPSED_KEY, 'true')

    const wrapper = mountView()

    expect(layoutGrid(wrapper).classes(), 'no side column is reserved for a folded card').not.toContain(SIDE_BY_SIDE)
  })

  it('changes the layout when the card is folded from the page', async () => {
    localStorage.setItem(DETAILS_COLLAPSED_KEY, 'false')
    const wrapper = mountView()

    await wrapper.find('[data-testid="card-fold"]').trigger('click')

    expect(layoutGrid(wrapper).classes(), 'the listing takes the width the card gave up').not.toContain(SIDE_BY_SIDE)
  })
})

describe('FileBrowserView repository type in the header', () => {
  const mounted = []
  const emptyDetails = () => Promise.resolve({ data: { files: [] } })
  const clientWithList = (repositories) => ({
    maven: {
      details: emptyDetails,
      members: () => Promise.resolve({ data: { members: [] } }),
      repositories: repositories ?? (() => Promise.resolve({ data: { repositories: [] } })),
      download: () => Promise.reject(new Error('not used')),
      delete: () => Promise.reject(new Error('not used'))
    }
  })

  const mountAt = (path) => {
    const wrapper = mount(FileBrowserView, {
      props: { qualifier: { path, watchable: 0 } },
      global: { stubs: { 'router-link': RouterLinkStub } }
    })
    mounted.push(wrapper)
    return wrapper
  }

  beforeEach(() => {
    sessionState.details.value = { routes: [] }
    sessionState.sessionReady.value = true
  })

  afterEach(() => {
    mounted.splice(0).forEach(wrapper => wrapper.unmount())
  })

  it('shows the type of the repository next to the breadcrumb', async () => {
    sessionState.client.value = clientWithList(() => Promise.resolve({
      data: { repositories: [{ id: 'central', type: 'PROXY', typeExplicit: true }] }
    }))

    const wrapper = mountAt('central/org/example')
    await flushPromises()

    expect(wrapper.get('[data-test="browse-type"]').text()).toBe('Proxy')
  })

  it('marks a repository without a type of its own as legacy in the header too', async () => {
    sessionState.client.value = clientWithList(() => Promise.resolve({
      data: { repositories: [{ id: 'old', type: 'mixed', typeExplicit: false }] }
    }))

    const wrapper = mountAt('old')
    await flushPromises()

    expect(wrapper.get('[data-test="browse-type"]').attributes('title')).toBe('No type set, behaves as before. Set a type to enforce the rules.')
  })

  it('shows no type when the repository is not in the list', async () => {
    sessionState.client.value = clientWithList(() => Promise.resolve({
      data: { repositories: [{ id: 'other', type: 'HOSTED', typeExplicit: true }] }
    }))

    const wrapper = mountAt('releases')
    await flushPromises()

    expect(wrapper.find('[data-test="browse-type"]').exists()).toBe(false)
  })

  it('shows no badge when the repository list cannot be read, and the page still renders', async () => {
    sessionState.client.value = clientWithList(() => Promise.reject(new Error('Network Error')))

    const wrapper = mountAt('releases')
    await flushPromises()

    expect(wrapper.find('[data-test="browse-type"]').exists()).toBe(false)
    expect(wrapper.find('[aria-label="Compact list"]').exists()).toBe(true)
  })

  it('asks for the list once for the repository, not again for each directory below it', async () => {
    const repositories = vi.fn(() => Promise.resolve({ data: { repositories: [] } }))
    sessionState.client.value = clientWithList(repositories)

    const wrapper = mountAt('releases')
    await flushPromises()
    await wrapper.setProps({ qualifier: { path: 'releases/net', watchable: 0 } })
    await flushPromises()

    expect(repositories).toHaveBeenCalledTimes(1)
  })
})

describe('FileBrowserView uploads by repository type', () => {
  const mounted = []
  const emptyDetails = () => Promise.resolve({ data: { files: [] } })
  const clientWithTypes = ({ repositories, members = [] }) => ({
    maven: {
      details: emptyDetails,
      members: () => Promise.resolve({ data: { members } }),
      repositories: () => Promise.resolve({ data: { repositories } }),
      download: () => Promise.reject(new Error('not used')),
      delete: () => Promise.reject(new Error('not used'))
    }
  })

  const mountAt = (path) => {
    const wrapper = mount(FileBrowserView, {
      props: { qualifier: { path, watchable: 0 } },
      global: { stubs: { 'router-link': RouterLinkStub } }
    })
    mounted.push(wrapper)
    return wrapper
  }

  beforeEach(() => {
    sessionState.details.value = { routes: [] }
    sessionState.sessionReady.value = true
    sessionState.canWrite.value = true
    sessionState.isManager.value = false
  })

  afterEach(() => {
    mounted.splice(0).forEach(wrapper => wrapper.unmount())
  })

  it('offers no upload to a proxy, and says that it fetches from remote repositories', async () => {
    sessionState.client.value = clientWithTypes({ repositories: [{ id: 'central', type: 'PROXY', typeExplicit: true }] })

    const wrapper = mountAt('central/org/example')
    await flushPromises()

    expect(wrapper.findComponent(BrowserUpload).exists()).toBe(false)
    expect(wrapper.get('[data-test="read-only-explanation"]').text()).toContain('remote repositories')
  })

  it('offers no upload to a group, and says that it has no files of its own', async () => {
    sessionState.client.value = clientWithTypes({ repositories: [{ id: 'all', type: 'GROUP', typeExplicit: true }] })

    const wrapper = mountAt('all')
    await flushPromises()

    expect(wrapper.findComponent(BrowserUpload).exists()).toBe(false)
    expect(wrapper.get('[data-test="read-only-explanation"]').text()).toContain('no files of its own')
  })

  it('keeps the upload for a hosted repository the user may write to', async () => {
    sessionState.client.value = clientWithTypes({ repositories: [{ id: 'releases', type: 'HOSTED', typeExplicit: true }] })

    const wrapper = mountAt('releases/net/example')
    await flushPromises()

    expect(wrapper.findComponent(BrowserUpload).exists()).toBe(true)
    expect(wrapper.find('[data-test="read-only-explanation"]').exists()).toBe(false)
  })

  it('keeps the upload for a repository without a type, as before', async () => {
    sessionState.client.value = clientWithTypes({ repositories: [{ id: 'old', type: 'mixed', typeExplicit: false }] })

    const wrapper = mountAt('old/org')
    await flushPromises()

    expect(wrapper.findComponent(BrowserUpload).exists()).toBe(true)
  })

  it('keeps the upload when the list of repositories cannot be read, since the type is then not known', async () => {
    sessionState.client.value = {
      ...clientWithTypes({ repositories: [] }),
      maven: {
        ...clientWithTypes({ repositories: [] }).maven,
        repositories: () => Promise.reject(new Error('Network Error'))
      }
    }

    const wrapper = mountAt('releases/net/example')
    await flushPromises()

    expect(wrapper.findComponent(BrowserUpload).exists()).toBe(true)
  })

  it('offers a manager the edit of a group members, from the Includes section', async () => {
    sessionState.isManager.value = true
    sessionState.client.value = clientWithTypes({
      repositories: [{ id: 'all', type: 'GROUP', typeExplicit: true }],
      members: ['releases']
    })

    const wrapper = mountAt('all')
    await flushPromises()

    expect(wrapper.findAll('button').find(button => button.text() === 'Edit members')).toBeDefined()
  })

  it('does not offer the edit of the members to a visitor who is not a manager', async () => {
    sessionState.client.value = clientWithTypes({
      repositories: [{ id: 'all', type: 'GROUP', typeExplicit: true }],
      members: ['releases']
    })

    const wrapper = mountAt('all')
    await flushPromises()

    expect(wrapper.text()).toContain('Includes')
    expect(wrapper.findAll('button').find(button => button.text() === 'Edit members')).toBeUndefined()
  })
})
