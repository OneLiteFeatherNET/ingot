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

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createErrorToast, createSuccessToast } from '../../helpers/toast'
import PermissionNodesCard from './PermissionNodesCard.vue'
import { NODES, answer, failure, makeNodesApi } from './teamTestKit'

/*
 * The card is the host of the node editor: it loads the nodes of one subject through the nodes
 * client and saves the draft with the entity tag it loaded. The client is a fake, so nothing here
 * touches the network, and every test builds its own wrapper and client.
 */

const holder = vi.hoisted(() => ({ session: null }))

vi.mock('../../store/session', () => ({ useSession: () => holder.session }))

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.response?.data?.message ?? error?.message ?? `${error}`
}))

describe('PermissionNodesCard', () => {
  let wrapper
  let nodes

  const mountCard = async (props = { subjectKind: 'principal', name: 'alice' }) => {
    wrapper = mount(PermissionNodesCard, { props, attachTo: document.body })
    await flushPromises()
  }

  const rowNodes = () => wrapper.findAll('[data-test="node-row"] [data-node]').map(row => row.attributes('data-node'))
  const buttonNamed = (name) => wrapper.findAll('button').find(button => button.text() === name)
  const remove = async (node) => {
    await wrapper.find(`button[aria-label="Remove ${node}"]`).trigger('click')
  }
  const save = async () => {
    await buttonNamed('Save').trigger('click')
    await flushPromises()
  }

  beforeEach(() => {
    vi.mocked(createSuccessToast).mockClear()
    vi.mocked(createErrorToast).mockClear()
    nodes = makeNodesApi()
    holder.session = { client: ref({ identity: { nodes } }) }
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    document.body.innerHTML = ''
  })

  describe('loading', () => {
    it('renders the node editor under the heading Permission nodes', async () => {
      await mountCard()

      expect(wrapper.find('h4').text()).toBe('Permission nodes')
      expect(wrapper.find('[data-test="node-editor"]').exists()).toBe(true)
    })

    it('reads the nodes of a principal from the principals collection', async () => {
      await mountCard({ subjectKind: 'principal', name: 'alice' })

      expect(nodes.get).toHaveBeenCalledWith('principals', 'alice')
      expect(rowNodes()).toEqual(NODES.map(entry => entry.node))
    })

    it('reads the nodes of a team from the teams collection', async () => {
      await mountCard({ subjectKind: 'team', name: 'developers' })

      expect(nodes.get).toHaveBeenCalledWith('teams', 'developers')
    })

    it('loads the catalog and the templates once for the suggestions', async () => {
      await mountCard()

      expect(nodes.catalog).toHaveBeenCalledTimes(1)
      expect(nodes.templates).toHaveBeenCalledTimes(1)
    })

    it('says so when the subject has no nodes yet', async () => {
      nodes.get.mockResolvedValue(answer({ nodes: [] }, '"n1"'))
      await mountCard()

      expect(wrapper.find('[data-test="nodes-empty"]').exists()).toBe(true)
    })

    it('shows the editor loading state while the nodes are read', async () => {
      nodes.get.mockReturnValue(new Promise(() => {}))
      await mountCard()

      expect(wrapper.find('[data-test="nodes-loading"]').exists()).toBe(true)
    })

    it('shows the failure with a retry that reads the nodes again', async () => {
      nodes.get.mockRejectedValueOnce(failure(500, 'Store unavailable'))
      await mountCard()

      expect(wrapper.find('[data-test="nodes-error"]').text()).toContain('Store unavailable')

      await wrapper.find('[data-test="nodes-retry"]').trigger('click')
      await flushPromises()

      expect(nodes.get).toHaveBeenCalledTimes(2)
      expect(wrapper.find('[data-test="nodes-error"]').exists()).toBe(false)
      expect(rowNodes()).toHaveLength(NODES.length)
    })

    it('says so when the subject does not exist', async () => {
      nodes.get.mockRejectedValue(failure(404, 'No such principal'))
      await mountCard({ subjectKind: 'principal', name: 'ghost' })

      expect(wrapper.find('[data-test="nodes-missing"]').text()).toContain('ghost')
    })

    it('reads the nodes of the new subject when the name changes', async () => {
      await mountCard()

      await wrapper.setProps({ name: 'bob' })
      await flushPromises()

      expect(nodes.get).toHaveBeenLastCalledWith('principals', 'bob')
    })
  })

  describe('saving', () => {
    it('shows no save bar while nothing changed', async () => {
      await mountCard()

      expect(wrapper.find('[data-test="unsaved-bar"]').exists()).toBe(false)
    })

    it('sends the edited list with the entity tag it loaded', async () => {
      await mountCard()

      await remove('ingot.repository.private.read')
      await save()

      expect(nodes.set).toHaveBeenCalledWith(
        'principals',
        'alice',
        [{ node: 'ingot.repository.releases.read', allowed: true }],
        '"n1"'
      )
    })

    it('uses the entity tag of the last answer for the next write', async () => {
      await mountCard()

      await remove('ingot.repository.private.read')
      await save()
      await remove('ingot.repository.releases.read')
      await save()

      expect(nodes.set.mock.calls.map(call => call[3])).toEqual(['"n1"', '"n2"'])
    })

    it('tells the page and the user that the nodes were saved', async () => {
      await mountCard()

      await remove('ingot.repository.private.read')
      await save()

      expect(wrapper.emitted('saved')).toHaveLength(1)
      expect(createSuccessToast).toHaveBeenCalledWith('Permission nodes saved')
      expect(wrapper.find('[data-test="unsaved-bar"]').exists(), 'the bar closes once saved').toBe(false)
    })

    it('puts the server message on the row of a node it rejects', async () => {
      nodes.set.mockRejectedValue(Object.assign(new Error('bad'), {
        response: {
          status: 400,
          data: {
            message: 'Invalid nodes',
            problems: [{ node: 'ingot.repository.releases.read', message: 'Use ingot.repository.<repository>.<read|deploy|delete|manage>' }]
          }
        }
      }))
      await mountCard()

      await remove('ingot.repository.private.read')
      await save()

      expect(wrapper.find('[data-node="ingot.repository.releases.read"] [data-test="row-error"]').text())
        .toContain('Use ingot.repository')
      expect(createErrorToast, 'the row already shows it').not.toHaveBeenCalled()
      expect(wrapper.emitted('saved')).toBeUndefined()
    })

    it('puts a problem that names no row under the editor and the others on their rows', async () => {
      nodes.set.mockRejectedValue(Object.assign(new Error('bad'), {
        response: {
          status: 400,
          data: {
            message: 'Invalid permission nodes: The node \'ingot.re*\' is unknown; Nodes must be a list',
            problems: [
              { node: 'ingot.repository.releases.read', message: 'Use ingot.repository.<repository>.<read|deploy|delete|manage>' },
              { node: null, message: 'Nodes must be a list' }
            ]
          }
        }
      }))
      await mountCard()

      await remove('ingot.repository.private.read')
      await save()

      expect(wrapper.find('[data-node="ingot.repository.releases.read"] [data-test="row-error"]').text())
        .toContain('Use ingot.repository')
      expect(wrapper.find('[data-test="form-error"]').text()).toBe('Nodes must be a list')
      expect(createErrorToast, 'the rows and the form already show it').not.toHaveBeenCalled()
    })

    it('shows a toast when the save fails without a message for a row', async () => {
      nodes.set.mockRejectedValue(failure(500, 'Store unavailable'))
      await mountCard()

      await remove('ingot.repository.private.read')
      await save()

      expect(createErrorToast).toHaveBeenCalledWith('Store unavailable')
    })

    it('opens the conflict dialog when the server answers 412', async () => {
      nodes.set.mockRejectedValue(failure(412, 'Precondition failed'))
      await mountCard()

      await remove('ingot.repository.private.read')
      await save()

      expect(document.querySelector('[role="alertdialog"]').textContent).toContain('Permission nodes changed elsewhere')
      expect(createErrorToast).not.toHaveBeenCalled()
    })

    it('drops the draft and reads the nodes again when the conflict is reloaded', async () => {
      nodes.set.mockRejectedValue(failure(412, 'Precondition failed'))
      await mountCard()
      await remove('ingot.repository.private.read')
      await save()
      nodes.get.mockClear()

      const reload = [...document.querySelectorAll('[role="alertdialog"] button')]
        .find(button => button.textContent.trim() === 'Reload and discard my changes')
      reload.click()
      await flushPromises()

      expect(nodes.get).toHaveBeenCalledTimes(1)
      expect(rowNodes()).toHaveLength(NODES.length)
    })

    it('discards the draft from the bar without a request', async () => {
      await mountCard()

      await remove('ingot.repository.private.read')
      await buttonNamed('Discard').trigger('click')

      expect(nodes.set).not.toHaveBeenCalled()
      expect(rowNodes()).toHaveLength(NODES.length)
    })
  })
})
