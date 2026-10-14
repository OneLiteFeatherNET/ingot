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
import DetailedListEntry from './DetailedListEntry.vue'

vi.mock('../../store/session', async () => {
  const { ref } = await import('vue')
  return {
    useSession: () => ({
      client: ref(null),
      hasPermissionTo: () => true,
      token: ref({ name: '', secret: '' })
    })
  }
})

const jar = { name: 'example-1.0.jar', type: 'FILE', contentLength: 2048 }

const mountEntry = (file = jar) =>
  mount(DetailedListEntry, {
    props: {
      file,
      qualifier: { path: 'releases/net/example' },
      url: 'https://repo.example/releases/net/example/example-1.0.jar',
      to: '/releases/net/example/example-1.0.jar',
      openDeleteEntryModal: () => {},
      compactMode: false
    },
    global: { stubs: { 'router-link': RouterLinkStub } }
  })

const actionsOf = (wrapper) =>
  wrapper.find('[data-row-actions]')

// Each test mounts its own row and nothing else is shared between them.
const mounted = []
const mountRow = (file) => {
  const wrapper = mountEntry(file)
  mounted.push(wrapper)
  return wrapper
}

describe('DetailedListEntry row actions', () => {
  afterEach(() => {
    mounted.splice(0).forEach(wrapper => wrapper.unmount())
  })

  it('exposes each row action as a button with an accessible name', () => {
    const wrapper = mountRow()
    const names = actionsOf(wrapper).findAll('button').map(button => button.attributes('aria-label'))

    expect(names, 'the delete action is a named button').toContain('Delete example-1.0.jar')
  })

  it('does not hide the row actions with display none', () => {
    const wrapper = mountRow()

    expect(actionsOf(wrapper).classes(), 'hidden elements cannot be reached by keyboard').not.toContain('hidden')
  })

  it('reveals the row actions when keyboard focus enters the row', () => {
    const wrapper = mountRow()

    expect(actionsOf(wrapper).classes(), 'focus within the row shows its actions').toContain('group-focus-within:opacity-100')
  })

  it('reveals the row actions on touch screens without a hover', () => {
    const wrapper = mountRow()

    expect(actionsOf(wrapper).classes(), 'coarse pointers have no hover, so the actions stay visible').toContain('[@media(pointer:coarse)]:opacity-100')
  })
})
