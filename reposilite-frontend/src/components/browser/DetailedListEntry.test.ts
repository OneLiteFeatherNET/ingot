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
import { mount, RouterLinkStub, type VueWrapper } from '@vue/test-utils'
import type { BrowserFile } from './listing'
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

const jar = { name: 'example-1.0.jar', type: 'FILE' as const, contentLength: 2048 }

const mountEntry = (file: BrowserFile = jar) =>
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

const actionsOf = (wrapper: VueWrapper<InstanceType<typeof DetailedListEntry>>) =>
  wrapper.find('[data-row-actions]')

// Each test mounts its own row and nothing else is shared between them.
const mounted: VueWrapper<InstanceType<typeof DetailedListEntry>>[] = []
const mountRow = (file?: BrowserFile) => {
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
    expect(wrapper.find('a').classes(), 'the link takes the focus ring').toContain('md-focus-ring')
  })

  it('shows the size on the same line as the name in compact mode', () => {
    const wrapper = mountWith({ compactMode: true })
    const size = wrapper.findAll('span').find(span => span.text() === prettyBytes(jar.contentLength))

    expect(size, 'the size is rendered as its own element').toBeDefined()
    expect(size!.element.parentElement!.textContent, 'the size shares the row with the name').toContain('example-1.0.jar')
    expect(size!.classes(), 'the size uses the label-small style').toContain('md-label-small')
  })
})

describe('DetailedListEntry as a list item', () => {
  it('is an MdListItem that keeps the hook of the end to end tests', () => {
    const wrapper = mountRow()

    expect((wrapper.element as HTMLElement).tagName, 'the row is a list item').toBe('LI')
    expect(wrapper.attributes('data-ingot-component'), 'the e2e hook is kept').toBe('detailed-list-entry')
  })

  it('puts the actions in the trailing slot of the item, outside the link', () => {
    const wrapper = mountRow()
    const row = wrapper.find('[data-test="list-item-row"]')

    expect(row.find('[data-row-actions]').exists(), 'the actions are the trailing content of the row').toBe(true)
    expect(wrapper.find('a [data-row-actions]').exists(), 'a button never sits inside the link').toBe(false)
  })
})

describe('DetailedListEntry md3 list spec', () => {
  const folder = { name: 'releases', type: 'DIRECTORY' as const }

  const mountRow = (overrides: Record<string, unknown> = {}, file: BrowserFile = jar) => {
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

  it('keeps the slots 8 pixels apart on a phone in the compact list, so the name keeps its room', () => {
    const wrapper = mountRow({ compactMode: true })

    expect(wrapper.get('[data-test="list-item-row"]').classes(), 'a compact row narrows the gap below the medium window').toContain('gap-2')
  })

  it('keeps the slots 16 pixels apart in the default list', () => {
    const wrapper = mountRow({ compactMode: false })

    expect(wrapper.get('[data-test="list-item-row"]').classes(), 'a default row keeps the wide gap').toContain('gap-4')
  })

  afterEach(() => {
    mounted.splice(0).forEach(wrapper => wrapper.unmount())
  })

  it('gives a one-line row a 56px minimum height', () => {
    const wrapper = mountRow({ compactMode: true })

    expect(wrapper.find('[data-test="list-item-row"]').classes(), 'one-line list items are 56px tall').toContain('min-h-14')
  })

  it('gives a two-line row a 72px minimum height', () => {
    const wrapper = mountRow({ compactMode: false })

    expect(wrapper.find('a').classes(), 'the 48px link and the 12px padding make a 72px row').toContain('min-h-12')
    expect(wrapper.find('a').classes(), 'a two-line row does not pull its link into the padding').not.toContain('-my-3')
  })

  it('uses 16px horizontal padding in both densities', () => {
    const rowOf = (compactMode: boolean) => mountRow({ compactMode }).find('[data-test="list-item-row"]')

    expect(rowOf(true).classes(), 'compact rows pad 16px on each side').toContain('px-4')
    expect(rowOf(false).classes(), 'default rows pad 16px on each side').toContain('px-4')
  })

  it('draws a folder icon in the on-surface-variant role, not primary', () => {
    const icon = mountRow({}, folder).find('svg')

    expect(icon.classes(), 'the folder icon uses the variant role').toContain('text-on-surface-variant')
    expect(icon.classes(), 'the folder icon is not primary').not.toContain('text-primary')
  })

  it('gives a repository link a 48px touch target', () => {
    const link = mountRow({}, folder).find('a')

    expect(link.classes(), 'the link box is at least 48px tall, not the 24px text line').toContain('min-h-12')
  })

  it('gives a file link a 48px touch target', () => {
    const link = mountRow().find('a')

    expect(link.classes(), 'the link box is at least 48px tall').toContain('min-h-12')
  })

  it('draws a file icon in the on-surface-variant role', () => {
    const icon = mountRow().find('svg')

    expect(icon.classes(), 'the file icon uses the variant role').toContain('text-on-surface-variant')
  })

  it('sets the trailing size in the label-small style', () => {
    const wrapper = mountRow({ compactMode: true })
    const size = wrapper.findAll('span').find(span => span.text() === prettyBytes(jar.contentLength))

    expect(size!.classes(), 'the trailing size uses label-small').toContain('md-label-small')
    expect(size!.classes(), 'the trailing size is on-surface-variant').toContain('text-on-surface-variant')
  })

  it('sets the file name in the body-large style', () => {
    const name = mountRow().findAll('span').find(span => span.text() === jar.name)

    expect(name!.classes(), 'the headline uses body-large').toContain('md-body-large')
  })
})

describe('DetailedListEntry actions slot', () => {
  const xml = { name: 'maven-metadata.xml', type: 'FILE' as const, contentType: 'application/xml', contentLength: 512 }
  const javadoc = { name: 'example-1.0-javadoc.jar', type: 'FILE' as const, contentLength: 4096 }
  // Viewable and a javadoc: the widest set of actions a row can have.
  const widest = { name: 'example-1.0-javadoc.jar', type: 'FILE' as const, contentType: 'application/xml', contentLength: 1024 }
  const folder = { name: 'releases', type: 'DIRECTORY' as const }

  const mountRow = (file: BrowserFile, actionSlots?: number) => {
    const wrapper = mount(DetailedListEntry, {
      props: {
        file,
        qualifier: { path: 'releases/net/example' },
        url: `https://repo.example/releases/net/example/${file.name}`,
        to: `/releases/net/example/${file.name}`,
        openDeleteEntryModal: () => {},
        compactMode: true,
        actionSlots
      },
      global: { stubs: { 'router-link': RouterLinkStub } }
    })
    mounted.push(wrapper)
    return wrapper
  }

  afterEach(() => {
    mounted.splice(0).forEach(wrapper => wrapper.unmount())
  })

  it('gives every row of a listing the same actions slot, whatever actions the row has', () => {
    const slots = [jar, xml, javadoc, widest, folder].map(file => actionsOf(mountRow(file, 3)).classes().join(' '))

    expect(new Set(slots).size, 'the actions container of each row carries the same classes').toBe(1)
  })

  it('sizes the slot by the widest row of the listing, not by its own actions', () => {
    const slots = [0, 1, 2, 3].map(count => actionsOf(mountRow(jar, count)).classes())

    expect(slots.map(classes => classes.includes('w-0')), 'no action in the listing reserves no room').toEqual([true, false, false, false])
    expect(slots.map(classes => classes.includes('medium:w-10')), 'one action reserves one button from 600 px').toEqual([false, true, false, false])
    expect(slots.map(classes => classes.includes('medium:w-21')), 'two actions reserve two buttons and a gap from 600 px').toEqual([false, false, true, false])
    expect(slots.map(classes => classes.includes('medium:w-32')), 'three actions reserve three buttons and two gaps from 600 px').toEqual([false, false, false, true])
  })

  it('leaves the slot to the row own buttons below the medium window', () => {
    const slots = [1, 2, 3].map(count => actionsOf(mountRow(jar, count)).classes())

    expect(slots.map(classes => classes.includes('w-auto')), 'below 600 px the slot is only as wide as its buttons').toEqual([true, true, true])
  })

  it('keeps the slot of a listing on a row whose own actions are fewer', () => {
    const wrapper = mountRow(jar, 3)

    expect(actionsOf(wrapper).classes(), 'the slot is the listing width, three buttons wide from 600 px').toContain('medium:w-32')
    expect(wrapper.find('[aria-label="View example-1.0.jar in a new tab"]').exists(), 'a missing view action is not rendered').toBe(false)
    expect(wrapper.find('[aria-label="Open javadoc for example-1.0.jar in a new tab"]').exists(), 'a missing javadoc action is not rendered').toBe(false)
  })

  it('renders only the actions a row has inside the same slot', () => {
    const wrapper = mountRow(xml)
    const names = actionsOf(wrapper).findAll('button').map(button => button.attributes('aria-label'))

    expect(names, 'the xml row offers view and delete').toEqual(['View maven-metadata.xml in a new tab', 'Delete maven-metadata.xml'])
  })
})
