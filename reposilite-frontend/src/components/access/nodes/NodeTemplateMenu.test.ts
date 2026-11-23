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
import { mount, type VueWrapper } from '@vue/test-utils'
import NodeTemplateMenu from './NodeTemplateMenu.vue'

const TEMPLATES = [
  { id: 'reader', label: 'Reader', nodes: ['ingot.repository.{repository}.read'] },
  { id: 'admin', label: 'Instance admin', nodes: ['ingot.instance.admin'] },
  { id: 'broken', label: 'Broken', nodes: ['ingot.repository.{repository}.bogus.extra.parts'] }
]
const CATALOG = [
  { node: 'ingot.repository.releases', kind: 'REPOSITORY' },
  { node: 'ingot.repository.snapshots', kind: 'REPOSITORY' }
]

describe('NodeTemplateMenu', () => {
  let wrapper: VueWrapper | undefined

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  const mountMenu = () => {
    wrapper = mount(NodeTemplateMenu, { props: { templates: TEMPLATES, catalog: CATALOG, nodes: [] }, attachTo: document.body }) as VueWrapper
  }

  const toggle = () => wrapper!.find('[data-test="template-toggle"]')
  const openReader = async () => {
    await toggle().trigger('click')
    await wrapper!.findAll('[data-test="template-option"]')[0].trigger('click')
  }
  const repository = () => wrapper!.find('input[role="combobox"]')
  const listbox = () => wrapper!.find('[role="listbox"]')

  it('draws the problem of a filled node through the shared error alert', async () => {
    mountMenu()
    await toggle().trigger('click')
    await wrapper!.findAll('[data-test="template-option"]')[2].trigger('click')
    await repository().setValue('releases')

    const problem = wrapper!.find('[data-test="template-problem"]')

    expect(problem.exists(), 'the refused node is reported').toBe(true)
    expect(problem.classes(), 'the problem is drawn on the error container').toContain('bg-error-container')
    expect(problem.attributes('role'), 'the problem is announced at once').toBe('alert')
  })

  it('announces the open panel on the toggle button', async () => {
    mountMenu()

    await toggle().trigger('click')

    expect(toggle().attributes('aria-expanded'), 'the toggle says the panel is open').toBe('true')
    expect(wrapper!.find(`#${toggle().attributes('aria-controls')}`).exists(), 'aria-controls names the panel').toBe(true)
  })

  it('names the repository suggestions as a listbox', async () => {
    mountMenu()

    await openReader()

    expect(listbox().attributes('aria-label'), 'the listbox is named').toBe('Repository suggestions')
  })

  it('lists the repositories of the catalog as options', async () => {
    mountMenu()
    await openReader()
    await repository().trigger('focus')

    const options = wrapper!.findAll('[role="option"]').map(option => option.text())

    expect(options, 'every repository is an option').toEqual(['releases', 'snapshots'])
  })

  it('chooses the active repository with ArrowDown and Enter', async () => {
    mountMenu()
    await openReader()
    await repository().trigger('focus')

    await repository().trigger('keydown', { key: 'ArrowDown' })
    await repository().trigger('keydown', { key: 'Enter' })

    expect((repository().element as HTMLInputElement).value, 'the first repository is typed into the field').toBe('releases')
  })
})
