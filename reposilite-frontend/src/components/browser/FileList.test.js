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

import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import FileList from './FileList.vue'
import { useAdjustments } from '../../store/adjustments'

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/releases/net/example' })
}))

vi.mock('../../store/session', async () => {
  const { ref } = await import('vue')
  return {
    useSession: () => ({
      client: ref(null),
      hasPermissionTo: () => false,
      token: ref({ name: '', secret: '' })
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
