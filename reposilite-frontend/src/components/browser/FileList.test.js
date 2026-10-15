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
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import download from 'downloadjs'
import FileList from './FileList.vue'
import { useAdjustments } from '../../store/adjustments'
import { sessionState } from '../../store/session'

const { routerPush } = vi.hoisted(() => ({ routerPush: vi.fn() }))

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/releases/net/example' }),
  useRouter: () => ({ push: routerPush })
}))

vi.mock('downloadjs', () => ({
  default: vi.fn()
}))

// The session is replaced per file. Each test sets the client and the token it needs.
vi.mock('../../store/session', async () => {
  const { ref } = await import('vue')
  const state = {
    client: ref(null),
    token: ref({ name: '', secret: '' })
  }
  return {
    sessionState: state,
    useSession: () => ({
      client: state.client,
      hasPermissionTo: () => false,
      token: state.token
    })
  }
})

vi.mock('../../store/qualifier', () => ({
  default: () => ({ refreshQualifier: () => {} })
}))

const jar = { name: 'example-1.0.jar', type: 'FILE', contentLength: 2048 }

const mountList = (props) =>
  mount(FileList, {
    props: {
      qualifier: { path: 'releases/net/example' },
      compactMode: false,
      loading: false,
      files: { list: [], error: false },
      hiddenCount: 0,
      ...props
    },
    global: { stubs: { 'router-link': RouterLinkStub } }
  })

const buttonWithText = (wrapper, text) =>
  wrapper.findAll('button').find(button => button.text() === text)

describe('FileList missing directory', () => {
  beforeEach(() => {
    routerPush.mockClear()
  })

  it('leaves out the empty list when the directory does not exist', () => {
    const wrapper = mountList({ files: { list: [], error: 'not-found' } })

    expect(wrapper.find('ul').exists(), 'no grey box may sit above the not-found message').toBe(false)
  })

  it('offers to go to the parent directory', async () => {
    const wrapper = mountList({ files: { list: [], error: 'not-found' } })

    await buttonWithText(wrapper, 'Go to parent').trigger('click')

    expect(routerPush, 'the parent is the browse route of the directory above').toHaveBeenCalledWith({ name: 'browse', params: { qualifier: 'releases/net' } })
  })

  it('offers to go to the repository root', async () => {
    const wrapper = mountList({ files: { list: [], error: 'not-found' } })

    await buttonWithText(wrapper, 'Go to repository root').trigger('click')

    expect(routerPush, 'the root is the first segment of the browsed path').toHaveBeenCalledWith({ name: 'browse', params: { qualifier: 'releases' } })
  })

  it('offers no navigation for a failure that is not a missing directory', () => {
    const wrapper = mountList({ files: { list: [], error: 'forbidden' } })

    expect(buttonWithText(wrapper, 'Go to parent'), 'a forbidden directory is not a navigation problem').toBeUndefined()
  })
})

describe('FileList empty state', () => {
  afterEach(() => {
    useAdjustments().displayHashFiles.value = false
  })

  it('says the directory is empty when it has no files to show', () => {
    const wrapper = mountList({ files: { list: [], error: false } })

    expect(wrapper.text(), 'an empty directory says so').toContain('Directory is empty')
  })

  it('does not call a directory empty while it still lists files', () => {
    const wrapper = mountList({ files: { list: [jar], error: false } })

    expect(wrapper.text(), 'a listed file means the directory is not empty').not.toContain('Directory is empty')
  })

  it('names the hidden utility files instead of calling the directory empty', () => {
    const wrapper = mountList({ files: { list: [], error: false }, hiddenCount: 2 })

    expect(wrapper.text(), 'the hidden count is stated').toContain('2 utility files')
    expect(wrapper.text(), 'the hidden files are said to be hidden').toContain('hidden')
    expect(wrapper.text(), 'the directory is not reported as empty').not.toContain('Directory is empty')
  })

  it('shows the utility files when the hidden-files action is pressed', async () => {
    const wrapper = mountList({ files: { list: [], error: false }, hiddenCount: 2 })

    await buttonWithText(wrapper, 'Show utility files').trigger('click')

    expect(useAdjustments().displayHashFiles.value, 'the adjustment is switched on').toBe(true)
  })

  it('does not show the empty state while the listing is loading', () => {
    const wrapper = mountList({ loading: true, files: { list: [], error: false } })

    expect(wrapper.text(), 'a loading listing is not empty').not.toContain('Directory is empty')
  })
})

const signedInAs = (name, secret) => {
  sessionState.token.value = { name, secret }
}

// A download that stays pending until the test settles it.
const pendingDownload = () => {
  let settle
  const promise = new Promise((resolve, reject) => {
    settle = { resolve, reject }
  })
  return { promise, settle }
}

const fileLink = (wrapper) =>
  wrapper.find('a[href$="example-1.0.jar"]')

describe('FileList downloads', () => {
  let wrapper

  beforeEach(() => {
    vi.mocked(download).mockClear()
    sessionState.token.value = { name: '', secret: '' }
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('lets anonymous visitors follow the file link directly', () => {
    const maven = { download: vi.fn(() => new Promise(() => {})) }
    sessionState.client.value = { maven }
    wrapper = mountList({ files: { list: [jar], error: false } })

    fileLink(wrapper).trigger('click')

    expect(maven.download, 'no request is made by the page, the browser follows the link').not.toHaveBeenCalled()
    expect(fileLink(wrapper).attributes('href'), 'the link points at the file').toContain('example-1.0.jar')
  })

  it('downloads through the client for signed-in visitors', async () => {
    const maven = { download: vi.fn(() => Promise.resolve({ data: 'bytes', headers: {} })) }
    sessionState.client.value = { maven }
    signedInAs('me', 'secret')
    wrapper = mountList({ files: { list: [jar], error: false } })

    fileLink(wrapper).trigger('click')
    await flushPromises()

    expect(maven.download, 'the request carries the signed-in credentials').toHaveBeenCalledWith('releases/net/example/example-1.0.jar')
    expect(download, 'the bytes are saved under the file name').toHaveBeenCalledWith('bytes', 'example-1.0.jar', undefined)
  })

  it('shows progress on the row while a signed-in download runs', async () => {
    const pending = pendingDownload()
    sessionState.client.value = { maven: { download: () => pending.promise } }
    signedInAs('me', 'secret')
    wrapper = mountList({ files: { list: [jar], error: false } })

    fileLink(wrapper).trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="progressbar"]').exists(), 'the row reports that it is downloading').toBe(true)
  })

  it('removes the progress once the download has finished', async () => {
    const pending = pendingDownload()
    sessionState.client.value = { maven: { download: () => pending.promise } }
    signedInAs('me', 'secret')
    wrapper = mountList({ files: { list: [jar], error: false } })

    fileLink(wrapper).trigger('click')
    pending.settle.resolve({ data: 'bytes', headers: {} })
    await flushPromises()

    expect(wrapper.find('[role="progressbar"]').exists(), 'a finished download leaves no progress behind').toBe(false)
  })
})

describe('FileList container', () => {
  it('gives the list the medium corner radius so it matches the repository details card', () => {
    const wrapper = mountList({ files: { list: [jar], error: false } })

    const list = wrapper.find('ul')
    expect(list.classes(), 'the list uses the card radius').toContain('rounded-corner-medium')
    expect(list.classes(), 'the list no longer uses the 28px radius').not.toContain('rounded-corner-extra-large')
  })
})

describe('FileList rows follow the md3 list spec', () => {
  const folder = { name: 'releases', type: 'DIRECTORY' }

  it('separates default rows with an inset divider instead of a full-width rule', () => {
    const wrapper = mountList({ files: { list: [jar, folder], error: false } })
    const list = wrapper.find('ul')

    expect(list.classes(), 'a full-width divide-y rule is not used').not.toContain('divide-y')
    expect(list.classes(), 'the divider is inset 16px on both sides').toContain('[&>li+li]:after:inset-x-4')
    expect(list.classes(), 'the divider is 1px tall').toContain('[&>li+li]:after:h-px')
    expect(list.classes(), 'the divider uses outline-variant').toContain('[&>li+li]:after:bg-outline-variant')
  })

  it('gives skeleton rows the same minimum height as the one-line rows they stand in for', () => {
    const wrapper = mountList({ loading: true, compactMode: true, files: { list: [], error: false } })
    const skeleton = wrapper.find('ul').findAll('li')[0]

    expect(skeleton.classes(), 'a placeholder one-line row is 56px tall').toContain('min-h-14')
  })
})
