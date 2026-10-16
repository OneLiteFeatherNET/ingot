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

import { describe, it, expect, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import NodeEditor from './NodeEditor.vue'

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
  let wrapper

  const mountEditor = (props = {}) => {
    wrapper = mount(NodeEditor, {
      props: {
        nodes: SAVED(),
        savedNodes: SAVED(),
        catalog: CATALOG(),
        templates: TEMPLATES(),
        ...props,
        'onUpdate:nodes': (value) => wrapper.setProps({ nodes: value })
      },
      // Attached, so that the submit button of the template form really submits it.
      attachTo: document.body,
      global: { stubs: { VueFinalModal: { template: '<div><slot /></div>' } } }
    })
    return wrapper
  }

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  const input = () => wrapper.find('input[role="combobox"]')
  const rowTexts = () => wrapper.findAll('[data-test="node-row"] code').map(code => code.text())
  const row = (node) => wrapper.find(`[data-test="node-row"] [data-node="${node}"]`)
  const buttonNamed = (name) => wrapper.findAll('button').find(button => button.text() === name)
  const lastDraft = () => wrapper.emitted('update:nodes').at(-1)[0]
  const type = async (text) => {
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

      expect(input().element.value).toBe('')
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

    it('refuses a node that is already listed with the message "Already listed"', async () => {
      mountEditor()

      await type('ingot.admin.users')

      expect(wrapper.text()).toContain('Already listed')
      expect(wrapper.find('[data-test="add-node"]').attributes('disabled'), 'Add is off').toBeDefined()
    })

    it('does not add a second row for a listed node on Enter', async () => {
      mountEditor()

      await type('ingot.admin.users')
      await input().trigger('keydown', { key: 'Enter' })

      expect(wrapper.emitted('update:nodes')).toBeUndefined()
    })
  })

  describe('autocomplete', () => {
    it('lists catalog matches in a listbox of options', async () => {
      mountEditor({ nodes: [], savedNodes: [] })

      await type('releases')

      expect(wrapper.find('[role="listbox"]').isVisible()).toBe(true)
      const options = wrapper.findAll('[role="option"]')
      expect(options.map(option => option.find('code').text())).toEqual([
        'ingot.repository.releases.read',
        'ingot.repository.releases.deploy'
      ])
    })

    it('groups the options under Static, Repository and Wildcard headings', async () => {
      mountEditor({ nodes: [], savedNodes: [] })

      await type('ingot')

      expect(wrapper.findAll('[data-test="suggestion-heading"]').map(heading => heading.text()))
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

      expect(wrapper.findAll('[role="option"]').map(option => option.find('code').text()))
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

    it('reports an open list through aria-expanded and aria-controls', async () => {
      mountEditor()

      await type('releases')

      expect(input().attributes('aria-expanded')).toBe('true')
      expect(input().attributes('aria-controls')).toBe(wrapper.find('[role="listbox"]').attributes('id'))
    })
  })

  describe('rows', () => {
    it('shows each node in monospace', () => {
      mountEditor()

      expect(wrapper.findAll('[data-test="node-row"] code').every(code => code.classes().includes('font-mono'))).toBe(true)
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
        .find(radio => radio.text() === 'Deny').trigger('click')

      expect(lastDraft()).toEqual([
        { node: 'ingot.repository.releases.read', allowed: false },
        { node: 'ingot.admin.users', allowed: false }
      ])
    })

    it('flips a row from Deny to Allow', async () => {
      mountEditor()

      await row('ingot.admin.users').findAll('[role="radio"]')
        .find(radio => radio.text() === 'Allow').trigger('click')

      expect(lastDraft()[1]).toEqual({ node: 'ingot.admin.users', allowed: true })
    })

    it('marks the current choice with aria-checked', () => {
      mountEditor()

      const radios = row('ingot.admin.users').findAll('[role="radio"]')
      expect(radios.find(radio => radio.text() === 'Deny').attributes('aria-checked')).toBe('true')
      expect(radios.find(radio => radio.text() === 'Allow').attributes('aria-checked')).toBe('false')
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
    const openTemplate = async (label) => {
      await wrapper.find('[data-test="template-toggle"]').trigger('click')
      await wrapper.findAll('[data-test="template-option"]')
        .find(option => option.find('span').text() === label).trigger('click')
    }

    const fillRepository = async (name) => {
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

      const options = wrapper.findAll('[data-test="template-panel"] datalist option')
      expect(options.map(option => option.attributes('value'))).toEqual(['releases', 'snapshots'])
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
      expect(groups).toEqual(expect.arrayContaining([
        'ingot',
        'ingot.admin',
        'ingot.repository',
        'ingot.repository.releases'
      ]))
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
        .find(radio => radio.text() === 'Allow').trigger('click')

      expect(lastDraft()[1]).toEqual({ node: 'ingot.admin.users', allowed: true })
    })

    it('goes back to the list', async () => {
      mountEditor()
      await showTree()

      await wrapper.find('[data-test="view-list"]').trigger('click')

      expect(wrapper.find('[data-test="node-list"]').exists()).toBe(true)
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

      await buttonNamed('Save').trigger('click')

      expect(wrapper.emitted('save')).toHaveLength(1)
    })

    it('emits discard from the bar', async () => {
      mountEditor({ nodes: [] })

      await buttonNamed('Discard').trigger('click')

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

      await buttonNamed('Reload and discard my changes').trigger('click')

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
  let wrapper

  const mountEditor = (props = {}) => {
    wrapper = mount(NodeEditor, {
      props: {
        nodes: [],
        savedNodes: [],
        catalog: CATALOG(),
        templates: [],
        ...props,
        'onUpdate:nodes': (value) => wrapper.setProps({ nodes: value })
      }
    })
    return wrapper
  }

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  const input = () => wrapper.find('input[role="combobox"]')
  const options = () => wrapper.findAll('[role="option"]').map(option => option.find('code').text())

  it('suggests the artifact actions after a groupId and adds the chosen one as Allow', async () => {
    mountEditor()

    await input().setValue('ingot.artifact.net.onelitefeather')
    expect(options(), 'the deploy action is suggested').toContain('ingot.artifact.net.onelitefeather.deploy')
    await wrapper.findAll('[role="option"]')
      .find(option => option.find('code').text() === 'ingot.artifact.net.onelitefeather.deploy')
      .trigger('click')

    expect(wrapper.emitted('update:nodes').at(-1)[0]).toEqual([
      { node: 'ingot.artifact.net.onelitefeather.deploy', allowed: true }
    ])
  })

  it('fills the field with a repository continuation without adding a node', async () => {
    mountEditor()

    await input().setValue('ingot.repository.releases.')
    const continuation = wrapper.findAll('[role="option"]')
      .find(option => option.find('code').text() === 'ingot.repository.releases.artifact.')
    await continuation.trigger('click')

    expect(input().element.value).toBe('ingot.repository.releases.artifact.')
    expect(wrapper.emitted('update:nodes'), 'nothing was added').toBeUndefined()
  })

  it('groups artifact suggestions under the heading Artifact', async () => {
    mountEditor({
      catalog: [...CATALOG(), { node: 'ingot.artifact.*.read', description: 'Read every artifact', kind: 'ARTIFACT' }]
    })

    await input().setValue('artifact')

    expect(wrapper.findAll('[data-test="suggestion-heading"]').map(heading => heading.text())).toContain('Artifact')
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
