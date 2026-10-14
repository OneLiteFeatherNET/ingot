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
import prettyBytes from 'pretty-bytes'
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

describe('DetailedListEntry appearance', () => {
  const mountWith = (overrides = {}) => {
    const wrapper = mount(DetailedListEntry, {
      props: {
        file: jar,
        qualifier: { path: 'releases/net/example' },
        url: 'https://repo.example/releases/net/example/example-1.0.jar',
        to: '/releases/net/example/example-1.0.jar',
        openDeleteEntryModal: () => {},
        compactMode: false,
        ...overrides
      },
      global: { stubs: { 'router-link': RouterLinkStub } }
    })
    mounted.push(wrapper)
    return wrapper
  }

  afterEach(() => {
    mounted.splice(0).forEach(wrapper => wrapper.unmount())
  })

  it('draws the file and folder icons as graphics, not emoji', () => {
    const wrapper = mountWith()

    expect(wrapper.text(), 'no emoji circles in the row').not.toMatch(/[⚪⚫]/)
    expect(wrapper.find('svg').exists(), 'the row carries an icon graphic').toBe(true)
  })

  it('does not fill the row with the primary colour on hover', () => {
    const wrapper = mountWith({ compactMode: true })

    expect(wrapper.classes(), 'hover is a state layer, not a solid primary fill').not.toContain('bg-primary')
    expect(wrapper.classes(), 'the row uses the state layer for hover').toContain('md-state-layer')
  })

  it('shows the size on the same line as the name in compact mode', () => {
    const wrapper = mountWith({ compactMode: true })
    const size = wrapper.findAll('span').find(span => span.text() === prettyBytes(jar.contentLength))

    expect(size, 'the size is rendered as its own element').toBeDefined()
    expect(size.element.parentElement.textContent, 'the size shares the row with the name').toContain('example-1.0.jar')
    expect(size.classes(), 'the size uses the label-small style').toContain('md-label-small')
  })
})

describe('DetailedListEntry md3 list spec', () => {
  const folder = { name: 'releases', type: 'DIRECTORY' }

  const mountRow = (overrides = {}, file = jar) => {
    const wrapper = mount(DetailedListEntry, {
      props: {
        file,
        qualifier: { path: 'releases/net/example' },
        url: 'https://repo.example/releases/net/example/example-1.0.jar',
        to: '/releases/net/example/example-1.0.jar',
        openDeleteEntryModal: () => {},
        compactMode: false,
        ...overrides
      },
      global: { stubs: { 'router-link': RouterLinkStub } }
    })
    mounted.push(wrapper)
    return wrapper
  }

  afterEach(() => {
    mounted.splice(0).forEach(wrapper => wrapper.unmount())
  })

  it('gives a one-line row a 56px minimum height', () => {
    const wrapper = mountRow({ compactMode: true })

    expect(wrapper.classes(), 'one-line list items are 56px tall').toContain('min-h-14')
  })

  it('gives a two-line row a 72px minimum height', () => {
    const wrapper = mountRow({ compactMode: false })

    expect(wrapper.classes(), 'two-line list items are 72px tall').toContain('min-h-18')
  })

  it('uses 16px horizontal padding in both densities', () => {
    expect(mountRow({ compactMode: true }).classes(), 'compact rows pad 16px on each side').toContain('px-4')
    expect(mountRow({ compactMode: false }).classes(), 'default rows pad 16px on each side').toContain('px-4')
  })

  it('draws a folder icon in the on-surface-variant role, not primary', () => {
    const icon = mountRow({}, folder).find('svg')

    expect(icon.classes(), 'the folder icon uses the variant role').toContain('text-on-surface-variant')
    expect(icon.classes(), 'the folder icon is not primary').not.toContain('text-primary')
  })

  it('draws a file icon in the on-surface-variant role', () => {
    const icon = mountRow().find('svg')

    expect(icon.classes(), 'the file icon uses the variant role').toContain('text-on-surface-variant')
  })

  it('sets the trailing size in the label-small style', () => {
    const wrapper = mountRow({ compactMode: true })
    const size = wrapper.findAll('span').find(span => span.text() === prettyBytes(jar.contentLength))

    expect(size.classes(), 'the trailing size uses label-small').toContain('md-label-small')
    expect(size.classes(), 'the trailing size is on-surface-variant').toContain('text-on-surface-variant')
  })

  it('sets the file name in the body-large style', () => {
    const name = mountRow().findAll('span').find(span => span.text() === jar.name)

    expect(name.classes(), 'the headline uses body-large').toContain('md-body-large')
  })
})
