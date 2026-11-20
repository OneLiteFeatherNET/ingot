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
import { mount, type VueWrapper } from '@vue/test-utils'
import MdSnackbar from './MdSnackbar.vue'
import source from './MdSnackbar.vue?raw'
import { createSnackbarQueue, snackbar as sharedQueue, SNACKBAR_QUEUE_KEY } from '../../store/snackbar'

// The transition the notice is wrapped in. Its props are read through the wrapper, not through the stub of the test utilities.
type TransitionWrapper = VueWrapper<{ $props: Record<string, unknown> }>

// Each test provides a queue of its own, so nothing is shared between tests. The one test that reads the
// shared queue empties it again afterwards.
describe('MdSnackbar', () => {
  let queue: ReturnType<typeof createSnackbarQueue>

  beforeEach(() => {
    vi.useFakeTimers()
    queue = createSnackbarQueue()
  })

  afterEach(() => {
    vi.useRealTimers()
    for (const entry of [...sharedQueue.entries.value]) sharedQueue.dismiss(entry.id)
  })

  const mountSnackbar = (provided = queue) =>
    mount(MdSnackbar, { global: { provide: { [SNACKBAR_QUEUE_KEY]: provided } } })

  it('renders no notice while the queue is empty', () => {
    const wrapper = mountSnackbar()

    expect(wrapper.find('[role="status"]').exists(), 'an empty queue must render no status region').toBe(false)
    expect(wrapper.find('[role="alert"]').exists(), 'an empty queue must render no alert').toBe(false)
  })

  it('shows the head of the injected queue', async () => {
    const wrapper = mountSnackbar()
    queue.enqueue({ message: 'Configuration updated', tone: 'success' })
    await nextTick()

    const status = wrapper.find('[role="status"]')
    expect(status.text(), 'the message of the injected queue must be rendered').toBe('Configuration updated')
    expect(status.attributes('aria-live'), 'a status update must be polite').toBe('polite')
  })

  it('shows nothing from the shared queue when a provider supplies another queue', async () => {
    const wrapper = mountSnackbar()
    sharedQueue.enqueue({ message: 'Shared notice', tone: 'info' })
    await nextTick()

    expect(wrapper.text(), 'a provided queue must replace the shared queue, not add to it').not.toContain('Shared notice')
  })

  it('falls back to the shared queue when no provider is given', async () => {
    const wrapper = mount(MdSnackbar)
    sharedQueue.enqueue({ message: 'Shared notice', tone: 'info' })
    await nextTick()

    expect(wrapper.text(), 'without a provider the application queue is the default').toContain('Shared notice')
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

    await action!.trigger('click')

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
      'medium:left-[calc(var(--app-rail-width,0px)+1.5rem)]'
    )
  })

  it('sits above the navigation bar below 600px by reading the bar height variable', () => {
    const wrapper = mountSnackbar()
    const host = wrapper.get('div').classes().join(' ')

    expect(host, 'the offset adds the bar height and the safe area to the normal margin').toContain(
      'max-medium:bottom-[calc(1rem+var(--app-bottom-bar-height,0px)+env(safe-area-inset-bottom))]'
    )
  })

  it('stacks on the snackbar layer token instead of an arbitrary z index', () => {
    const wrapper = mountSnackbar()
    const host = wrapper.get('div').classes()

    expect(host, 'the layer comes from the z-md-snackbar token').toContain('z-md-snackbar')
    expect(host.some(name => name.startsWith('z-[')), 'no arbitrary z index may remain').toBe(false)
  })

  it('passes the snackbar pattern to the transition of the notice', async () => {
    const wrapper = mountSnackbar()
    queue.enqueue({ message: 'Saved', tone: 'success' })
    await nextTick()

    expect((wrapper.findComponent({ name: 'MdTransition' }) as TransitionWrapper).props('pattern'), 'the notice must enter and leave with the snackbar pattern').toBe('snackbar')
  })

  it('replaces a notice out-in, so the second notice is drawn only after the first has left', () => {
    const wrapper = mountSnackbar()
    expect((wrapper.findComponent({ name: 'MdTransition' }) as TransitionWrapper).props('mode'), 'the replacement of a notice must be out-in').toBe('out-in')
  })

  it('keeps the leaving notice on screen while the next one waits to enter', async () => {
    const wrapper = mount(MdSnackbar, {
      global: { provide: { [SNACKBAR_QUEUE_KEY]: queue }, stubs: { transition: false } }
    })
    const firstId = queue.enqueue({ message: 'First', tone: 'success' })
    queue.enqueue({ message: 'Second', tone: 'success' })
    await nextTick()
    queue.dismiss(firstId!)
    await nextTick()

    expect(wrapper.findAll('[data-tone]'), 'only one notice may be drawn while the first one leaves').toHaveLength(1)
    expect(wrapper.text(), 'the notice that waits must not be drawn before the first has left').toContain('First')
    expect(wrapper.text(), 'the notice that waits must not be drawn before the first has left').not.toContain('Second')
    expect(wrapper.find('.motion-snackbar-leave-active').exists(), 'the first notice must carry the leave class').toBe(true)
  })

  it('has no style element in its source', () => {
    expect(source, 'the component styles itself with tokens and variants, not a style block').not.toContain('<style')
  })
})
