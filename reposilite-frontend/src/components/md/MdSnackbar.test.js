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
import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import MdSnackbar from './MdSnackbar.vue'
import { createSnackbarQueue } from '../../store/snackbar'

// Each test mounts the snackbar on a queue of its own, so nothing is shared between tests.
describe('MdSnackbar', () => {
  let queue

  beforeEach(() => {
    vi.useFakeTimers()
    queue = createSnackbarQueue()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  const mountSnackbar = () => mount(MdSnackbar, { props: { queue } })

  it('renders no notice while the queue is empty', () => {
    const wrapper = mountSnackbar()

    expect(wrapper.find('[role="status"]').exists(), 'an empty queue must render no status region').toBe(false)
    expect(wrapper.find('[role="alert"]').exists(), 'an empty queue must render no alert').toBe(false)
  })

  it('announces a success notice as a polite status update', async () => {
    const wrapper = mountSnackbar()
    queue.enqueue({ message: 'Configuration updated', tone: 'success' })
    await nextTick()

    const status = wrapper.find('[role="status"]')
    expect(status.text(), 'the message must be rendered inside the status region').toBe('Configuration updated')
    expect(status.attributes('aria-live'), 'a status update must be polite').toBe('polite')
  })

  it('announces an error notice as an alert', async () => {
    const wrapper = mountSnackbar()
    queue.enqueue({ message: 'Cannot delete file', tone: 'error' })
    await nextTick()

    const alert = wrapper.find('[role="alert"]')
    expect(alert.text(), 'an error must be announced through an alert region').toBe('Cannot delete file')
    expect(wrapper.find('[role="status"]').exists(), 'an error must not also be a status region').toBe(false)
  })

  it('shows only one notice at a time', async () => {
    const wrapper = mountSnackbar()
    queue.enqueue({ message: 'First', tone: 'error' })
    queue.enqueue({ message: 'Second', tone: 'error' })
    await nextTick()

    expect(wrapper.text(), 'the waiting notice must not be rendered yet').toContain('First')
    expect(wrapper.text(), 'the waiting notice must not be rendered yet').not.toContain('Second')
  })

  it('removes the notice when the close button is pressed', async () => {
    const wrapper = mountSnackbar()
    queue.enqueue({ message: 'Cannot delete file', tone: 'error' })
    await nextTick()

    await wrapper.find('button[aria-label="Dismiss notification"]').trigger('click')

    expect(wrapper.find('[role="alert"]').exists(), 'the close button must remove the notice').toBe(false)
  })

  it('renders the action label and runs the action when it is pressed', async () => {
    const onClick = vi.fn()
    const wrapper = mountSnackbar()
    queue.enqueue({ message: 'Deleted', tone: 'success', action: { label: 'Undo', onClick } })
    await nextTick()

    const action = wrapper.findAll('button').find(button => button.text() === 'Undo')
    expect(action, 'the action must be offered as a button with its label').toBeDefined()

    await action.trigger('click')

    expect(onClick, 'pressing the action must run its callback once').toHaveBeenCalledTimes(1)
    expect(wrapper.find('[role="status"]').exists(), 'the notice must leave after its action').toBe(false)
  })

  it('renders no action button when the notice has no action', async () => {
    const wrapper = mountSnackbar()
    queue.enqueue({ message: 'Signed out', tone: 'info' })
    await nextTick()

    const labels = wrapper.findAll('button').map(button => button.text())
    expect(labels, 'only the close button may be present without an action').toEqual([''])
  })

  it('removes a success notice once its auto-dismiss delay has passed', async () => {
    const wrapper = mountSnackbar()
    queue.enqueue({ message: 'Saved', tone: 'success' })
    await nextTick()

    vi.advanceTimersByTime(4000)
    await nextTick()

    expect(wrapper.find('[role="status"]').exists(), 'the success notice must leave on its own').toBe(false)
  })

  it('steps right of the navigation rail from 600px by reading the rail width variable', () => {
    const wrapper = mountSnackbar()
    const host = wrapper.get('div').classes().join(' ')

    expect(host, 'the side offset comes from the rail width, not the bottom bar height').toContain(
      'min-[600px]:left-[calc(var(--app-rail-width,0px)+1.5rem)]'
    )
  })

  it('sits above the navigation bar below 600px by reading the bar height variable', () => {
    const wrapper = mountSnackbar()
    const host = wrapper.get('div').classes().join(' ')

    expect(host, 'the offset adds the bar height and the safe area to the normal margin').toContain(
      'max-[600px]:bottom-[calc(1rem+var(--app-bottom-bar-height,0px)+env(safe-area-inset-bottom))]'
    )
  })
})
