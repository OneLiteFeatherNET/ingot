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

// @vitest-environment jsdom

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import NodeEditor from './NodeEditor.vue'
import type { NodeEntry } from './permissionNodesApi'

/*
 * The editor is controlled: the host owns the draft. The helper plays the host by feeding every
 * update:nodes back as the new nodes prop, so a click on a row changes what is shown. Nothing here
 * waits on a timer; the suggestions are a plain filter over the catalog prop.
 */

const CATALOG = () => [
  { node: 'ingot.admin.users', description: 'Manage users', kind: 'STATIC' },
  { node: 'ingot.admin.teams', description: 'Manage teams', kind: 'STATIC' },
  { node: 'ingot.repository.releases.read', description: 'Read releases', kind: 'REPOSITORY' },
  { node: 'ingot.repository.releases.deploy', description: 'Deploy releases', kind: 'REPOSITORY' },
  { node: 'ingot.repository.snapshots.read', description: 'Read snapshots', kind: 'REPOSITORY' },
  { node: 'ingot.admin.*', description: 'Every admin area', kind: 'WILDCARD' }
]

const TEMPLATES = () => [
  { id: 'read', label: 'Read', nodes: ['ingot.repository.{repository}.read'] },
  {
    id: 'deploy',
    label: 'Deploy',
    nodes: ['ingot.repository.{repository}.read', 'ingot.repository.{repository}.deploy']
  },
  { id: 'instance-admin', label: 'Instance admin', nodes: ['ingot.admin.*'] }
]

const SAVED = () => [
  { node: 'ingot.repository.releases.read', allowed: true },
  { node: 'ingot.admin.users', allowed: false }
]

const INHERITED = () => [
  { node: 'ingot.repository.releases.deploy', allowed: true, source: 'INHERITED_TEAM', subject: 'developers', depth: 1 },
  { node: 'ingot.admin.users', allowed: true, source: 'TEAM', subject: 'ops', depth: 0 }
]

describe('NodeEditor', () => {
  let wrapper: VueWrapper

  const mountEditor = (props = {}) => {
    wrapper = mount(NodeEditor, {
      props: {
        nodes: SAVED(),
        savedNodes: SAVED(),
        catalog: CATALOG(),
        templates: TEMPLATES(),
        ...props,
        'onUpdate:nodes': (value: unknown) => wrapper.setProps({ nodes: value })
      },
      // Attached, so that the submit button of the template form really submits it.
      attachTo: document.body,
      global: { stubs: { VueFinalModal: { template: '<div><slot /></div>' } } }
    })
    return wrapper
  }

  // jsdom has no layout. The reveal runs after a tick, so the stub stays until the next test replaces it.
  beforeEach(() => {
    Element.prototype.scrollIntoView = vi.fn()
  })

  afterEach(() => {
    wrapper?.unmount()
  })

  const input = () => wrapper.find('input[role="combobox"]')
  const rowTexts = () => wrapper.findAll('[data-test="node-row"] code').map(code => code.text())
  const row = (node: string) => wrapper.find(`[data-test="node-row"] [data-node="${node}"]`)
  const buttonNamed = (name: string) => wrapper.findAll('button').find(button => button.text() === name)
  const lastDraft = (): NodeEntry[] => wrapper.emitted<[NodeEntry[]]>('update:nodes')?.at(-1)?.[0] ?? []
  const type = async (text: string) => {
    await input().setValue(text)
  }

  describe('adding a node', () => {
    it('adds a valid typed node as a new allowed row', async () => {
      mountEditor()

      await type('ingot.admin.teams')
      await wrapper.find('[data-test="add-node"]').trigger('click')

      expect(lastDraft(), 'the draft gets the new node as Allow').toEqual([
        ...SAVED(),
        { node: 'ingot.admin.teams', allowed: true }
      ])
      expect(rowTexts()).toContain('ingot.admin.teams')
    })

    it('clears the field after adding', async () => {
      mountEditor()

      await type('ingot.admin.teams')
      await wrapper.find('[data-test="add-node"]').trigger('click')

      expect((input().element as HTMLInputElement).value).toBe('')
    })

    it('adds the typed node when Enter is pressed without a highlighted option', async () => {
      mountEditor()

      await type('ingot.admin.teams')
      await input().trigger('keydown', { key: 'Enter' })

      expect(lastDraft().map(entry => entry.node)).toContain('ingot.admin.teams')
    })

    it('shows the grammar problem under the field and does not add an invalid node', async () => {
      mountEditor()

      await type('ingot.re*')
      await input().trigger('keydown', { key: 'Enter' })

      expect(wrapper.text()).toContain("Segments may only hold letters, digits, '_' and '-'")
      expect(input().attributes('aria-invalid'), 'the field is marked invalid').toBe('true')
      expect(wrapper.emitted('update:nodes'), 'nothing was added').toBeUndefined()
    })

    it('tells the shape to use for a repository node without an action', async () => {
      mountEditor()

      await type('ingot.repository.releases')

      expect(wrapper.text()).toContain('Use ingot.repository.<repository>.<read|deploy|delete|manage>')
    })

    it('does not show an error for a node that is in the list', async () => {
      mountEditor()

      await type('ingot.admin.users')

      expect(wrapper.text(), 'the duplicate is not refused with a message').not.toContain('Already listed')
      expect(input().attributes('aria-invalid'), 'the field is not marked invalid').not.toBe('true')
    })

    it('highlights the listed row when the field is submitted with Add', async () => {
      Element.prototype.scrollIntoView = vi.fn()
      mountEditor()

      await type('ingot.admin.users')
      await wrapper.find('[data-test="add-node"]').trigger('click')
      await flushPromises()

      expect(row('ingot.admin.users').attributes('data-highlighted'), 'the existing row is highlighted').toBe('true')
      expect((input().element as HTMLInputElement).value, 'the field is cleared').toBe('')
    })

    it('focuses the listed row on Enter instead of adding a second row', async () => {
      Element.prototype.scrollIntoView = vi.fn()
      mountEditor()

      await type('ingot.admin.users')
      await input().trigger('keydown', { key: 'Enter' })
      await flushPromises()

      expect(wrapper.emitted('update:nodes'), 'nothing was added').toBeUndefined()
      expect(document.activeElement, 'the listed row has the focus').toBe(row('ingot.admin.users').element)
    })
  })

  describe('autocomplete', () => {
    it('lists catalog matches in a listbox of options', async () => {
      mountEditor({ nodes: [], savedNodes: [] })

      await type('releases')

      expect(wrapper.find('[role="listbox"]').isVisible()).toBe(true)
      const options = wrapper.findAll('[role="option"]')
      expect(options.map(option => option.find('span').text())).toEqual([
        'ingot.repository.releases.read',
        'ingot.repository.releases.deploy'
      ])
    })

    it('groups the options under Static, Repository and Wildcard headings', async () => {
      mountEditor({ nodes: [], savedNodes: [] })

      await type('ingot')

      expect(wrapper.findAll('[data-test="option-group"]').map(heading => heading.text()))
        .toEqual(['Static', 'Repository', 'Wildcard'])
    })

    it('shows the description of an option', async () => {
      mountEditor({ nodes: [], savedNodes: [] })

      await type('releases')

      expect(wrapper.find('[role="option"]').text()).toContain('Read releases')
    })

    it('leaves out catalog nodes that are already listed', async () => {
      mountEditor()

      await type('releases')

      expect(wrapper.findAll('[role="option"]').map(option => option.find('span').text()))
        .not.toContain('ingot.repository.releases.read')
    })

    it('moves the active option down with ArrowDown and names it in aria-activedescendant', async () => {
      mountEditor({ nodes: [], savedNodes: [] })
      await type('releases')

      await input().trigger('keydown', { key: 'ArrowDown' })

      const options = wrapper.findAll('[role="option"]')
      expect(options[0].attributes('aria-selected')).toBe('true')
      expect(options[1].attributes('aria-selected')).toBe('false')
      expect(input().attributes('aria-activedescendant')).toBe(options[0].attributes('id'))
    })

    it('moves back up with ArrowUp and wraps from the first option to the last', async () => {
      mountEditor({ nodes: [], savedNodes: [] })
      await type('releases')
      await input().trigger('keydown', { key: 'ArrowDown' })

      await input().trigger('keydown', { key: 'ArrowUp' })

      const options = wrapper.findAll('[role="option"]')
      expect(options[1].attributes('aria-selected'), 'the last option is active').toBe('true')
    })

    it('picks the active option with Enter and adds it as Allow', async () => {
      mountEditor({ nodes: [], savedNodes: [] })
      await type('releases')
      await input().trigger('keydown', { key: 'ArrowDown' })
      await input().trigger('keydown', { key: 'ArrowDown' })

      await input().trigger('keydown', { key: 'Enter' })

      expect(lastDraft()).toEqual([{ node: 'ingot.repository.releases.deploy', allowed: true }])
    })

    it('picks an option with a click', async () => {
      mountEditor({ nodes: [], savedNodes: [] })
      await type('snapshots')

      await wrapper.find('[role="option"]').trigger('click')

      expect(lastDraft()).toEqual([{ node: 'ingot.repository.snapshots.read', allowed: true }])
    })

    it('closes the list with Escape', async () => {
      mountEditor()
      await type('releases')

      await input().trigger('keydown', { key: 'Escape' })

      expect(wrapper.find('[role="listbox"]').isVisible()).toBe(false)
      expect(input().attributes('aria-expanded')).toBe('false')
    })

    it('draws the suggestions with the shared listbox in the menu layer', async () => {
      mountEditor({ nodes: [], savedNodes: [] })

      await type('releases')

      const listbox = wrapper.find('[role="listbox"]')
      expect(listbox.attributes('data-ingot-component'), 'the list is the shared MdListbox').toBe('md-listbox')
      expect(listbox.classes(), 'the list sits in the menu layer').toContain('z-md-menu')
      expect(listbox.classes(), 'no z-index literal is left').not.toContain('z-20')
    })

    it('names each group of suggestions by its heading', async () => {
      mountEditor({ nodes: [], savedNodes: [] })

      await type('ingot')

      const group = wrapper.findAll('[role="group"]')[1]
      expect(wrapper.find(`#${group.attributes('aria-labelledby')}`).text(), 'the second group is named Repository')
        .toBe('Repository')
    })

    it('moves to the last and the first option with End and Home', async () => {
      mountEditor({ nodes: [], savedNodes: [] })
      await type('releases')
      await input().trigger('keydown', { key: 'ArrowDown' })

      await input().trigger('keydown', { key: 'End' })
      expect(wrapper.findAll('[role="option"]')[1].attributes('aria-selected'), 'End moves to the last option').toBe('true')

      await input().trigger('keydown', { key: 'Home' })
      expect(wrapper.findAll('[role="option"]')[0].attributes('aria-selected'), 'Home moves to the first option').toBe('true')
    })

    it('reports an open list through aria-expanded and aria-controls', async () => {
      mountEditor()

      await type('releases')

      expect(input().attributes('aria-expanded')).toBe('true')
      expect(input().attributes('aria-controls')).toBe(wrapper.find('[role="listbox"]').attributes('id'))
    })
  })

  describe('rows', () => {
    it('shows each node in the medium code role, which is monospace', () => {
      mountEditor()

      expect(wrapper.findAll('[data-test="node-row"] code').every(code => code.classes().includes('md-code-medium'))).toBe(true)
    })

    it('removes a row with its remove button, which is named after the node', async () => {
      mountEditor()

      await wrapper.find('button[aria-label="Remove ingot.admin.users"]').trigger('click')

      expect(lastDraft()).toEqual([{ node: 'ingot.repository.releases.read', allowed: true }])
      expect(rowTexts()).not.toContain('ingot.admin.users')
    })

    it('flips a row from Allow to Deny', async () => {
      mountEditor()

      await row('ingot.repository.releases.read').findAll('[role="radio"]')
        .find(radio => radio.text() === 'Deny')!.trigger('click')

      expect(lastDraft()).toEqual([
        { node: 'ingot.repository.releases.read', allowed: false },
        { node: 'ingot.admin.users', allowed: false }
      ])
    })

    it('flips a row from Deny to Allow', async () => {
      mountEditor()

      await row('ingot.admin.users').findAll('[role="radio"]')
        .find(radio => radio.text() === 'Allow')!.trigger('click')

      expect(lastDraft()[1]).toEqual({ node: 'ingot.admin.users', allowed: true })
    })

    it('marks the current choice with aria-checked', () => {
      mountEditor()

      const radios = row('ingot.admin.users').findAll('[role="radio"]')
      expect(radios.find(radio => radio.text() === 'Deny')!.attributes('aria-checked')).toBe('true')
      expect(radios.find(radio => radio.text() === 'Allow')!.attributes('aria-checked')).toBe('false')
    })

    it('shows the message the server gave for a row', () => {
      mountEditor({ rowErrors: { 'ingot.admin.users': 'Unknown admin area' } })

      expect(row('ingot.admin.users').find('[data-test="row-error"]').text()).toBe('Unknown admin area')
    })

    it('shows a message that belongs to no row as an alert', () => {
      mountEditor({ formError: 'Nodes must be a list' })

      expect(wrapper.find('[role="alert"]').text()).toBe('Nodes must be a list')
    })
  })

  describe('filter', () => {
    it('keeps only the rows whose node contains the text, ignoring case', async () => {
      mountEditor()

      await wrapper.find('input[type="search"]').setValue('ADMIN')

      expect(rowTexts()).toEqual(['ingot.admin.users'])
    })

    it('says so when no row matches', async () => {
      mountEditor()

      await wrapper.find('input[type="search"]').setValue('nothing')

      expect(wrapper.find('[data-test="nodes-no-match"]').text()).toContain('nothing')
    })
  })

  describe('templates', () => {
    const openTemplate = async (label: string) => {
      await wrapper.find('[data-test="template-toggle"]').trigger('click')
      await wrapper.findAll('[data-test="template-option"]')
        .find(option => option.find('span').text() === label)!.trigger('click')
    }

    const fillRepository = async (name: string) => {
      await wrapper.find('[data-test="template-panel"] input').setValue(name)
      await wrapper.find('[data-test="template-apply"]').trigger('click')
    }

    it('lists the templates when the menu is opened', async () => {
      mountEditor()

      await wrapper.find('[data-test="template-toggle"]').trigger('click')

      expect(wrapper.findAll('[data-test="template-option"]').map(option => option.find('span').text()))
        .toEqual(['Read', 'Deploy', 'Instance admin'])
    })

    it('reports the open menu through aria-expanded', async () => {
      mountEditor()

      await wrapper.find('[data-test="template-toggle"]').trigger('click')

      expect(wrapper.find('[data-test="template-toggle"]').attributes('aria-expanded')).toBe('true')
    })

    it('fills the repository into the template and adds the nodes as Allow', async () => {
      mountEditor({ nodes: [], savedNodes: [] })

      await openTemplate('Deploy')
      await fillRepository('snapshots')

      expect(lastDraft()).toEqual([
        { node: 'ingot.repository.snapshots.read', allowed: true },
        { node: 'ingot.repository.snapshots.deploy', allowed: true }
      ])
    })

    it('adds only the nodes that are not listed yet and says how many were already there', async () => {
      mountEditor()

      await openTemplate('Deploy')
      await fillRepository('releases')

      expect(lastDraft()).toEqual([
        ...SAVED(),
        { node: 'ingot.repository.releases.deploy', allowed: true }
      ])
      expect(wrapper.find('[data-test="template-notice"]').text()).toBe('Added 1 node, 1 already listed')
    })

    it('offers the repositories of the catalog as suggestions', async () => {
      mountEditor()

      await openTemplate('Read')

      const options = wrapper.findAll('[data-test="template-panel"] [role="listbox"] [role="option"]')
      expect(options.map(option => option.text())).toEqual(['releases', 'snapshots'])
    })

    it('picks a repository suggestion with the keyboard', async () => {
      mountEditor()
      await openTemplate('Read')
      const field = wrapper.find('[data-test="template-panel"] input')

      await field.trigger('focus')
      await field.trigger('keydown', { key: 'ArrowDown' })
      await field.trigger('keydown', { key: 'Enter' })

      expect((field.element as HTMLInputElement).value, 'the first repository is filled in').toBe('releases')
    })

    it('refuses a repository name the grammar does not allow', async () => {
      mountEditor()

      await openTemplate('Read')
      await wrapper.find('[data-test="template-panel"] input').setValue('re*leases')

      expect(wrapper.find('[data-test="template-panel"]').text()).toContain("Use letters, digits, '_' and '-', or '*'")
      expect(wrapper.find('[data-test="template-apply"]').attributes('disabled')).toBeDefined()
    })

    it('adds the instance admin template at once, without asking for a repository', async () => {
      mountEditor({ nodes: [], savedNodes: [] })

      await openTemplate('Instance admin')

      expect(lastDraft()).toEqual([{ node: 'ingot.admin.*', allowed: true }])
    })
  })

  describe('tree view', () => {
    const showTree = async () => {
      await wrapper.find('[data-test="view-tree"]').trigger('click')
    }

    it('groups the nodes by segment', async () => {
      mountEditor()

      await showTree()

      const groups = wrapper.findAll('[data-test="tree-group"]').map(group => group.attributes('data-key'))
      expect(groups, 'ingot is the only group, the other levels are single children').toEqual(['ingot'])
    })

    it('shows the allow state on each leaf', async () => {
      mountEditor()

      await showTree()

      const leaves = wrapper.findAll('[data-test="tree-leaf"] [data-allowed]')
      const states = Object.fromEntries(leaves.map(leaf => [leaf.attributes('data-node'), leaf.attributes('data-allowed')]))
      expect(states).toEqual({
        'ingot.admin.users': 'false',
        'ingot.repository.releases.read': 'true'
      })
    })

    it('keeps the full node on the controls of a leaf', async () => {
      mountEditor()

      await showTree()

      expect(wrapper.find('button[aria-label="Remove ingot.admin.users"]').exists()).toBe(true)
    })

    it('flips a leaf like a list row', async () => {
      mountEditor()
      await showTree()

      await wrapper.find('[data-node="ingot.admin.users"]').findAll('[role="radio"]')
        .find(radio => radio.text() === 'Allow')!.trigger('click')

      expect(lastDraft()[1]).toEqual({ node: 'ingot.admin.users', allowed: true })
    })

    it('goes back to the list', async () => {
      mountEditor()
      await showTree()

      await wrapper.find('[data-test="view-list"]').trigger('click')

      expect(wrapper.find('[data-test="node-list"]').exists()).toBe(true)
    })
  })

  describe('many nodes', () => {
    // 24 nodes: 20 repository nodes, three of them denied, and 4 admin nodes, all allowed.
    const MANY = () => [
      ...Array.from({ length: 20 }, (_, index) => ({
        node: `ingot.repository.repo-${index}.read`,
        allowed: ![0, 8, 16].includes(index)
      })),
      ...['area-0', 'area-1', 'area-2', 'area-3'].map(name => ({ node: `ingot.admin.${name}`, allowed: true }))
    ]
    const summary = () => wrapper.find('[data-test="node-summary"]').text()
    const region = () => wrapper.find('[role="region"][aria-label="Permission nodes"]')
    const details = (key: string) => wrapper.find(`[data-key="${key}"] > details`).element as HTMLDetailsElement

    afterEach(() => {
      delete (window as { matchMedia?: unknown }).matchMedia
    })

    it('bounds the node list in a region that scrolls and is named', () => {
      mountEditor({ nodes: MANY(), savedNodes: MANY() })

      expect(region().exists(), 'the region is there').toBe(true)
      expect(region().attributes('tabindex'), 'the region takes the keyboard').toBe('0')
      expect(region().classes()).toEqual(expect.arrayContaining(['max-h-md-node-region', 'overflow-y-auto']))
    })

    it('counts the nodes and their states in a summary line', () => {
      mountEditor({ nodes: MANY(), savedNodes: MANY() })

      expect(summary()).toBe('24 nodes: 21 allow, 3 deny')
    })

    it('says how many nodes a filter shows out of the total', async () => {
      mountEditor({ nodes: MANY(), savedNodes: MANY() })

      await wrapper.find('input[type="search"]').setValue('area-2')

      expect(summary()).toContain('showing 1 of 24')
    })

    it('filters the rows with the Allow and Deny chips', async () => {
      mountEditor({ nodes: MANY(), savedNodes: MANY() })

      await wrapper.find('[data-test="state-deny"]').trigger('click')

      expect(rowTexts()).toHaveLength(3)
      expect(summary()).toContain('showing 3 of 24')

      await wrapper.find('[data-test="state-allow"]').trigger('click')

      expect(rowTexts()).toHaveLength(21)
    })

    it('shows every node again with the All chip', async () => {
      mountEditor({ nodes: MANY(), savedNodes: MANY() })
      await wrapper.find('[data-test="state-deny"]').trigger('click')

      await wrapper.find('[data-test="state-all"]').trigger('click')

      expect(rowTexts()).toHaveLength(24)
    })

    it('keeps the groups of a tree collapsed above 20 nodes', async () => {
      mountEditor({ nodes: MANY(), savedNodes: MANY() })
      await wrapper.find('[data-test="view-tree"]').trigger('click')

      expect(details('ingot').open, 'the root group').toBe(false)
      expect(details('ingot.admin').open, 'the admin group').toBe(false)
      expect(details('ingot.repository').open, 'the repository group').toBe(false)
    })

    it('opens the groups on the path to every match of the filter', async () => {
      mountEditor({ nodes: MANY(), savedNodes: MANY() })
      await wrapper.find('[data-test="view-tree"]').trigger('click')

      await wrapper.find('input[type="search"]').setValue('repo-1')

      expect(details('ingot.repository').open, 'the group of the matches is open').toBe(true)
    })

    it('keeps the groups open for 20 nodes or fewer', async () => {
      mountEditor({ nodes: MANY().slice(20), savedNodes: MANY().slice(20) })
      await wrapper.find('[data-test="view-tree"]').trigger('click')

      expect(details('ingot.admin').open).toBe(true)
    })

    it('shows the count and the state of each group', async () => {
      mountEditor({ nodes: MANY(), savedNodes: MANY() })
      await wrapper.find('[data-test="view-tree"]').trigger('click')

      const admin = wrapper.find('[data-key="ingot.admin"] > details > summary').text()
      const repository = wrapper.find('[data-key="ingot.repository"] > details > summary').text()
      expect(admin, 'the admin group holds four nodes, all allowed').toContain('4 nodes')
      expect(admin).toContain('All allow')
      expect(repository, 'the repository group holds 20 nodes with denied ones').toContain('20 nodes')
      expect(repository).toContain('Mixed')
    })

    it('shows a leaf on one row with its full node and its controls', async () => {
      mountEditor({ nodes: MANY(), savedNodes: MANY() })
      await wrapper.find('[data-test="view-tree"]').trigger('click')

      const leaf = wrapper.find('[data-node="ingot.admin.area-0"]')
      expect(leaf.find('code').text(), 'the full node is shown').toBe('ingot.admin.area-0')
      expect(leaf.find('button[aria-label="Remove ingot.admin.area-0"]').exists(), 'the remove control is on the same row').toBe(true)
      expect(leaf.findAll('[role="radio"]')).toHaveLength(2)
    })

    it('marks a wildcard leaf as covering everything below it', async () => {
      const nodes = [{ node: 'ingot.admin.*', allowed: true }, { node: 'ingot.admin.users', allowed: false }]
      mountEditor({ nodes, savedNodes: nodes })
      await wrapper.find('[data-test="view-tree"]').trigger('click')

      expect(wrapper.find('[data-node="ingot.admin.*"] [data-test="wildcard-hint"]').text()).toBe('all below')
      expect(wrapper.find('[data-node="ingot.admin.users"] [data-test="wildcard-hint"]').exists()).toBe(false)
    })

    it('shows a lone wildcard as one row without groups', async () => {
      const nodes = [{ node: 'ingot.admin.*', allowed: true }]
      mountEditor({ nodes, savedNodes: nodes })
      await wrapper.find('[data-test="view-tree"]').trigger('click')

      expect(wrapper.findAll('[data-test="tree-group"]')).toHaveLength(0)
      expect(wrapper.findAll('[data-test="tree-leaf"]').map(leaf => leaf.find('code').text())).toEqual(['ingot.admin.*'])
    })

    it('scrolls a new node into view and highlights it', async () => {
      const scrollIntoView = vi.fn()
      Element.prototype.scrollIntoView = scrollIntoView
      mountEditor({ nodes: MANY(), savedNodes: MANY() })

      await type('ingot.admin.teams')
      await wrapper.find('[data-test="add-node"]').trigger('click')
      await flushPromises()

      expect(scrollIntoView, 'the new row is scrolled to').toHaveBeenCalled()
      expect(row('ingot.admin.teams').attributes('data-highlighted'), 'the new row is highlighted').toBe('true')
    })

    it('scrolls without smooth motion when reduced motion is preferred', async () => {
      const scrollIntoView = vi.fn()
      Element.prototype.scrollIntoView = scrollIntoView
      window.matchMedia = vi.fn().mockReturnValue({ matches: true })
      mountEditor({ nodes: MANY(), savedNodes: MANY() })

      await type('ingot.admin.teams')
      await wrapper.find('[data-test="add-node"]').trigger('click')
      await flushPromises()

      expect(scrollIntoView).toHaveBeenCalledWith({ block: 'nearest', behavior: 'auto' })
    })

    it('focuses the existing row for a duplicate in the tree view', async () => {
      Element.prototype.scrollIntoView = vi.fn()
      mountEditor({ nodes: MANY(), savedNodes: MANY() })
      await wrapper.find('[data-test="view-tree"]').trigger('click')

      await type('ingot.admin.area-1')
      await input().trigger('keydown', { key: 'Enter' })
      await flushPromises()

      expect(wrapper.emitted('update:nodes'), 'nothing was added').toBeUndefined()
      expect(document.activeElement).toBe(wrapper.find('[data-node="ingot.admin.area-1"]').element)
    })
  })

  describe('inherited nodes', () => {
    const toggle = () => wrapper.find('[data-test="inherited-toggle"]')
    const region = () => wrapper.find(`[id="${toggle().attributes('aria-controls')}"]`)

    it('shows the source team and the distance of a node from a parent team', () => {
      mountEditor({ inherited: INHERITED() })

      const rows = wrapper.findAll('[data-test="inherited-row"]')
      expect(rows[0].find('[data-test="inherited-source"]').text()).toBe('from team developers, 1 level up')
    })

    it('shows the source team without a distance for a direct membership', () => {
      mountEditor({ inherited: INHERITED() })

      const rows = wrapper.findAll('[data-test="inherited-row"]')
      expect(rows[1].find('[data-test="inherited-source"]').text()).toBe('from team ops')
    })

    it('shows the allowed state of an inherited node as text', () => {
      mountEditor({ inherited: INHERITED() })

      expect(wrapper.find('[data-test="inherited-row"] [data-test="inherited-state"]').text()).toBe('Allowed')
    })

    it('marks an inherited node as overridden when the same node is held directly', () => {
      mountEditor({ inherited: INHERITED() })

      const rows = wrapper.findAll('[data-test="inherited-row"]')
      expect(rows[0].find('[data-test="inherited-overridden"]').exists(), 'not held directly').toBe(false)
      expect(rows[1].find('[data-test="inherited-overridden"]').exists(), 'held directly').toBe(true)
    })

    it('is open to start with', () => {
      mountEditor({ inherited: INHERITED() })

      expect(toggle().attributes('aria-expanded')).toBe('true')
      expect(region().isVisible()).toBe(true)
    })

    it('collapses with its button and reports it through aria-expanded', async () => {
      mountEditor({ inherited: INHERITED() })

      await toggle().trigger('click')

      expect(toggle().attributes('aria-expanded')).toBe('false')
      expect(region().isVisible()).toBe(false)
    })

    it('opens again with a second press', async () => {
      mountEditor({ inherited: INHERITED() })
      await toggle().trigger('click')

      await toggle().trigger('click')

      expect(region().isVisible()).toBe(true)
    })

    it('is absent when nothing is inherited', () => {
      mountEditor({ inherited: [] })

      expect(wrapper.find('[data-test="inherited-section"]').exists()).toBe(false)
    })

    it('shows a warning line for a team that could not be read', () => {
      mountEditor({ inherited: INHERITED(), inheritedWarnings: ['Could not read the nodes of team staff: Boom'] })

      expect(wrapper.find('[data-test="inherited-warning"]').text()).toBe('Could not read the nodes of team staff: Boom')
      expect(wrapper.findAll('[data-test="inherited-row"]')).toHaveLength(INHERITED().length)
    })

    it('shows the section for a warning alone, when no inherited node could be read', () => {
      mountEditor({ inherited: [], inheritedWarnings: ['Could not read the nodes of team staff: Boom'] })

      expect(wrapper.find('[data-test="inherited-section"]').exists()).toBe(true)
      expect(wrapper.find('[data-test="inherited-warning"]').exists()).toBe(true)
    })
  })

  describe('unsaved changes', () => {
    it('shows no bar while the draft equals the saved list', () => {
      mountEditor()

      expect(wrapper.find('[data-test="unsaved-bar"]').exists()).toBe(false)
    })

    it('counts the changes against the saved list', () => {
      mountEditor({ nodes: [...SAVED(), { node: 'ingot.admin.teams', allowed: true }] })

      expect(wrapper.find('[data-test="unsaved-bar"]').text()).toContain('1 unsaved change')
    })

    it('emits save from the bar', async () => {
      mountEditor({ nodes: [] })

      await buttonNamed('Save')!.trigger('click')

      expect(wrapper.emitted('save')).toHaveLength(1)
    })

    it('emits discard from the bar', async () => {
      mountEditor({ nodes: [] })

      await buttonNamed('Discard')!.trigger('click')

      expect(wrapper.emitted('discard')).toHaveLength(1)
    })

    it('lists what changed when the count is pressed', async () => {
      mountEditor({ nodes: [{ node: 'ingot.admin.users', allowed: true }] })

      await wrapper.find('[data-test="unsaved-bar"] [aria-haspopup="dialog"]').trigger('click')

      const summary = wrapper.find('[data-test="change-summary"]').text()
      expect(summary).toContain('Remove ingot.repository.releases.read')
      expect(summary).toContain('Change ingot.admin.users from Deny to Allow')
    })

    it('has exactly one filled button, which is Save', async () => {
      mountEditor({ nodes: [] })

      const filled = wrapper.findAll('button[data-variant="filled"]')
      expect(filled.map(button => button.text())).toEqual(['Save'])
    })
  })

  describe('conflict', () => {
    it('passes on the choice to reload from the conflict dialog', async () => {
      mountEditor({ conflict: { kind: 'nodes', name: 'alice' } })

      await buttonNamed('Reload and discard my changes')!.trigger('click')

      expect(wrapper.emitted('conflict-reload')).toHaveLength(1)
    })

    it('names the subject in the dialog', () => {
      mountEditor({ conflict: { kind: 'nodes', name: 'alice' } })

      expect(wrapper.text()).toContain('Permission nodes changed elsewhere')
    })
  })

  describe('states', () => {
    it('shows a loading status instead of the rows', () => {
      mountEditor({ loading: true })

      expect(wrapper.find('[data-test="nodes-loading"]').attributes('role')).toBe('status')
      expect(wrapper.find('[data-test="node-list"]').exists()).toBe(false)
    })

    it('shows the empty state with a hint to add a node', () => {
      mountEditor({ nodes: [], savedNodes: [] })

      expect(wrapper.find('[data-test="nodes-empty"]').text()).toContain('No permission nodes')
      expect(wrapper.find('[data-test="nodes-empty"]').text()).toContain('Add a node above or start from a template.')
    })
  })

  describe('read only', () => {
    it('has no add field, template menu, toggles or remove buttons', () => {
      mountEditor({ readonly: true })

      expect(input().exists(), 'no add field').toBe(false)
      expect(wrapper.find('[data-test="template-toggle"]').exists(), 'no template menu').toBe(false)
      expect(wrapper.findAll('[role="radio"]').filter(radio => ['Allow', 'Deny'].includes(radio.text())), 'no toggles').toHaveLength(0)
      expect(wrapper.findAll('button[aria-label^="Remove"]'), 'no remove buttons').toHaveLength(0)
    })

    it('shows the state of each row as text', () => {
      mountEditor({ readonly: true })

      expect(wrapper.findAll('[data-test="row-state"]').map(state => state.text())).toEqual(['Allowed', 'Denied'])
    })

    it('shows no unsaved bar even when the lists differ', () => {
      mountEditor({ readonly: true, nodes: [] })

      expect(wrapper.find('[data-test="unsaved-bar"]').exists()).toBe(false)
    })

    it('still lists the inherited nodes', () => {
      mountEditor({ readonly: true, inherited: INHERITED() })

      expect(wrapper.findAll('[data-test="inherited-row"]')).toHaveLength(2)
    })
  })
})

describe('NodeEditor artifact nodes', () => {
  let wrapper: VueWrapper

  const mountEditor = (props = {}) => {
    wrapper = mount(NodeEditor, {
      props: {
        nodes: [],
        savedNodes: [],
        catalog: CATALOG(),
        templates: [],
        ...props,
        'onUpdate:nodes': (value: unknown) => wrapper.setProps({ nodes: value })
      }
    })
    return wrapper
  }

  afterEach(() => {
    wrapper?.unmount()
  })

  const input = () => wrapper.find('input[role="combobox"]')
  const options = () => wrapper.findAll('[role="option"]').map(option => option.find('span').text())

  it('suggests the artifact actions after a groupId and adds the chosen one as Allow', async () => {
    mountEditor()

    await input().setValue('ingot.artifact.net.onelitefeather')
    expect(options(), 'the deploy action is suggested').toContain('ingot.artifact.net.onelitefeather.deploy')
    await wrapper.findAll('[role="option"]')
      .find(option => option.find('span').text() === 'ingot.artifact.net.onelitefeather.deploy')!
      .trigger('click')

    expect(wrapper.emitted<[NodeEntry[]]>('update:nodes')?.at(-1)?.[0]).toEqual([
      { node: 'ingot.artifact.net.onelitefeather.deploy', allowed: true }
    ])
  })

  it('fills the field with a repository continuation without adding a node', async () => {
    mountEditor()

    await input().setValue('ingot.repository.releases.')
    const continuation = wrapper.findAll('[role="option"]')
      .find(option => option.find('span').text() === 'ingot.repository.releases.artifact.')!
    await continuation.trigger('click')

    expect((input().element as HTMLInputElement).value).toBe('ingot.repository.releases.artifact.')
    expect(wrapper.emitted('update:nodes'), 'nothing was added').toBeUndefined()
  })

  it('groups artifact suggestions under the heading Artifact', async () => {
    mountEditor({
      catalog: [...CATALOG(), { node: 'ingot.artifact.*.read', description: 'Read every artifact', kind: 'ARTIFACT' }]
    })

    await input().setValue('artifact')

    expect(wrapper.findAll('[data-test="option-group"]').map(heading => heading.text())).toContain('Artifact')
  })

  it('shows the problem the server gave for an artifact row inline', () => {
    mountEditor({
      nodes: [{ node: 'ingot.artifact.net.onelitefeather.deploy', allowed: true }],
      savedNodes: [{ node: 'ingot.artifact.net.onelitefeather.deploy', allowed: true }],
      rowErrors: { 'ingot.artifact.net.onelitefeather.deploy': "The groupId 'net' is refused by the server" }
    })

    expect(wrapper.find('[data-node="ingot.artifact.net.onelitefeather.deploy"] [data-test="row-error"]').text())
      .toBe("The groupId 'net' is refused by the server")
  })
})

describe('NodeEditor artifact templates', () => {
  let wrapper: VueWrapper

  const ARTIFACT_TEMPLATES = () => [
    { id: 'artifact-read', label: 'Read an artifact', nodes: ['ingot.artifact.{groupId}:{artifactId}.read'] },
    {
      id: 'repository-artifact-deploy',
      label: 'Deploy an artifact in one repository',
      nodes: [
        'ingot.repository.{repository}.artifact.{groupId}:{artifactId}.read',
        'ingot.repository.{repository}.artifact.{groupId}:{artifactId}.deploy'
      ]
    }
  ]

  const mountEditor = () => {
    wrapper = mount(NodeEditor, {
      props: {
        nodes: [],
        savedNodes: [],
        catalog: CATALOG(),
        templates: ARTIFACT_TEMPLATES(),
        'onUpdate:nodes': (value: unknown) => wrapper.setProps({ nodes: value })
      },
      attachTo: document.body
    })
    return wrapper
  }

  afterEach(() => {
    wrapper?.unmount()
  })

  const openTemplate = async (label: string) => {
    await wrapper.find('[data-test="template-toggle"]').trigger('click')
    await wrapper.findAll('[data-test="template-option"]')
      .find(option => option.find('span').text() === label)!.trigger('click')
  }
  const field = (test: string) => wrapper.find(`[data-test="${test}"]`)
  const apply = () => wrapper.find('[data-test="template-apply"]')
  const lastDraft = (): NodeEntry[] => wrapper.emitted<[NodeEntry[]]>('update:nodes')?.at(-1)?.[0] ?? []

  it('asks for a groupId and an optional artifactId and adds the node as Allow', async () => {
    mountEditor()

    await openTemplate('Read an artifact')
    await field('template-group').setValue('net.onelitefeather')
    await field('template-artifact').setValue('mycelium-bom')
    await apply().trigger('click')

    expect(lastDraft()).toEqual([{ node: 'ingot.artifact.net.onelitefeather:mycelium-bom.read', allowed: true }])
  })

  it('leaves the artifactId out of the node when the field is empty', async () => {
    mountEditor()

    await openTemplate('Read an artifact')
    await field('template-group').setValue('net.onelitefeather')
    await apply().trigger('click')

    expect(lastDraft()).toEqual([{ node: 'ingot.artifact.net.onelitefeather.read', allowed: true }])
  })

  it('shows the preview of the node in the groupId form', async () => {
    mountEditor()

    await openTemplate('Read an artifact')
    await field('template-group').setValue('net')
    await field('template-artifact').setValue('lib')

    expect(wrapper.find('[data-test="template-panel"]').text()).toContain('Adds ingot.artifact.net:lib.read')
  })

  it('needs a groupId before the template can be added', async () => {
    mountEditor()

    await openTemplate('Read an artifact')

    expect(apply().attributes('disabled')).toBeDefined()
    expect(wrapper.emitted('update:nodes')).toBeUndefined()
  })

  it('refuses a wildcard inside the groupId with the message of the coordinate rules', async () => {
    mountEditor()

    await openTemplate('Read an artifact')
    await field('template-group').setValue('net.*')

    expect(wrapper.find('[data-test="template-panel"]').text()).toContain("Use '*' only as the whole groupId")
    expect(apply().attributes('disabled')).toBeDefined()
  })

  it('refuses a colon in the groupId', async () => {
    mountEditor()

    await openTemplate('Read an artifact')
    await field('template-group').setValue('net:lib')

    expect(wrapper.find('[data-test="template-panel"]').text()).toContain("A groupId cannot contain ':'")
  })

  it('asks for the repository and the coordinates of the qualified template', async () => {
    mountEditor()

    await openTemplate('Deploy an artifact in one repository')
    await field('template-repository').setValue('releases')
    await field('template-group').setValue('net.onelitefeather')
    await apply().trigger('click')

    expect(lastDraft()).toEqual([
      { node: 'ingot.repository.releases.artifact.net.onelitefeather.read', allowed: true },
      { node: 'ingot.repository.releases.artifact.net.onelitefeather.deploy', allowed: true }
    ])
  })

  it('refuses a wildcard repository for the qualified template', async () => {
    mountEditor()

    await openTemplate('Deploy an artifact in one repository')
    await field('template-repository').setValue('*')
    await field('template-group').setValue('net')

    expect(wrapper.find('[data-test="template-panel"]').text()).toContain('The wildcard repository is not allowed')
    expect(apply().attributes('disabled')).toBeDefined()
  })
})
