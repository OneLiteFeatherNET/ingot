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

import { describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { h } from 'vue'
import MdNavigationItem from './MdNavigationItem.vue'

const makeRouter = async (path: string): Promise<Router> => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/browse', component: { render: () => null } },
      { path: '/settings', component: { render: () => null } }
    ]
  })
  await router.push(path)
  await router.isReady()
  return router
}

// A click as the browser dispatches it: a primary-button click, plain unless the init adds modifier keys.
const clickEvent = (init: MouseEventInit = {}) =>
  new MouseEvent('click', { bubbles: true, cancelable: true, button: 0, ...init })

// The native element under the component, dispatched on directly so the event keeps its default state.
const dispatch = (element: unknown, event: MouseEvent) => (element as HTMLElement).dispatchEvent(event)

const mountItem = (props: Record<string, unknown>, router?: Router) =>
  mount(MdNavigationItem, {
    props: { label: 'Browse', ...props },
    slots: { leading: () => h('svg', { 'data-test': 'glyph' }) },
    global: router ? { plugins: [router] } : {}
  })

// The link or the button that the item renders inside its root, which carries the anchor.
const control = (wrapper: ReturnType<typeof mountItem>) => wrapper.find('a, button')

describe('MdNavigationItem rendering', () => {
  it('renders a button when neither to nor href is given', () => {
    const wrapper = mountItem({})

    expect(control(wrapper).element.tagName).toBe('BUTTON')
    expect(control(wrapper).attributes('type')).toBe('button')
    expect(wrapper.text()).toContain('Browse')
  })

  it('renders a plain link when href is given', () => {
    const wrapper = mountItem({ href: 'https://example.org/docs' })

    expect(control(wrapper).element.tagName).toBe('A')
    expect(control(wrapper).attributes('href')).toBe('https://example.org/docs')
  })

  it('draws the leading slot inside the indicator, hidden from assistive technology', () => {
    const wrapper = mountItem({})

    expect(wrapper.find('[data-test="indicator"]').attributes('aria-hidden')).toBe('true')
    expect(wrapper.find('[data-test="indicator"] [data-test="glyph"]').exists()).toBe(true)
  })

  it('carries the md-navigation-item anchor on the root element, and only there', () => {
    const wrapper = mountItem({})

    expect(wrapper.attributes('data-ingot-component')).toBe('md-navigation-item')
    expect(control(wrapper).attributes('data-ingot-component'), 'the control inside the root has no anchor of its own').toBeUndefined()
  })
})

describe('MdNavigationItem active state', () => {
  it('draws the active indicator only on the active destination', () => {
    const active = mountItem({ active: true })
    const inactive = mountItem({ active: false })

    expect(active.find('[data-test="indicator"]').classes(), 'the active destination has the indicator').toContain('bg-secondary-container')
    expect(inactive.find('[data-test="indicator"]').classes(), 'an inactive destination has no indicator fill').not.toContain('bg-secondary-container')
  })

  it('sets aria-current="page" on the active destination', () => {
    expect(control(mountItem({ active: true })).attributes('aria-current')).toBe('page')
  })

  it('leaves aria-current off an inactive destination', () => {
    expect(control(mountItem({ active: false })).attributes('aria-current')).toBeUndefined()
  })

  it('marks a router link current when its route is the exact current route', async () => {
    const router = await makeRouter('/browse')
    const wrapper = mountItem({ to: '/browse' }, router)

    expect(control(wrapper).attributes('aria-current'), 'the route of the link is the current page').toBe('page')
  })

  it('leaves a router link without aria-current when its route is not current', async () => {
    const router = await makeRouter('/browse')
    const wrapper = mountItem({ to: '/settings' }, router)

    expect(control(wrapper).attributes('aria-current')).toBeUndefined()
  })

  it('lets the active prop override the route of a router link', async () => {
    const router = await makeRouter('/browse')
    const wrapper = mountItem({ to: '/settings', active: true }, router)

    expect(control(wrapper).attributes('aria-current')).toBe('page')
  })
})

describe('MdNavigationItem router link', () => {
  it('renders the href of the route location', async () => {
    const router = await makeRouter('/browse')
    const wrapper = mountItem({ to: '/settings' }, router)

    expect(control(wrapper).attributes('href')).toBe('/settings')
  })

  it('navigates through the router on a plain click and emits select', async () => {
    const router = await makeRouter('/browse')
    const wrapper = mountItem({ to: '/settings' }, router)

    await control(wrapper).trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path, 'the router moved to the destination').toBe('/settings')
    expect(wrapper.emitted('select'), 'the item reports the selection once').toHaveLength(1)
  })

  it('leaves a modified click to the browser: no prevented default, no navigation, no emit', async () => {
    const router = await makeRouter('/browse')
    const wrapper = mountItem({ to: '/settings' }, router)
    const event = clickEvent({ ctrlKey: true })

    dispatch(control(wrapper).element, event)
    await flushPromises()

    expect(event.defaultPrevented, 'the browser keeps the default action of a modified click').toBe(false)
    expect(router.currentRoute.value.path, 'the page stays where it is').toBe('/browse')
    expect(wrapper.emitted('select')).toBeUndefined()
  })
})

describe('MdNavigationItem button and plain link events', () => {
  it('emits select once on a plain click of a button', async () => {
    const wrapper = mountItem({})

    await control(wrapper).trigger('click')

    expect(wrapper.emitted('select')).toHaveLength(1)
  })

  it('emits select for a modified click of a button, which has no browser default to leave it to', () => {
    const wrapper = mountItem({})
    const event = clickEvent({ shiftKey: true })

    dispatch(control(wrapper).element, event)

    expect(wrapper.emitted('select'), 'a button destination is selected whatever key is held').toHaveLength(1)
    expect(event.defaultPrevented).toBe(false)
  })

  it('leaves a modified click of a plain link to the browser and does not emit', () => {
    const wrapper = mountItem({ href: '#docs' })
    const event = clickEvent({ ctrlKey: true })

    dispatch(control(wrapper).element, event)

    expect(wrapper.emitted('select')).toBeUndefined()
    expect(event.defaultPrevented).toBe(false)
  })

  it('names the full label as the title of the control and of the label when given', () => {
    const wrapper = mountItem({ label: 'Admin', description: 'Administration', labelTitle: 'Administration' })

    expect(control(wrapper).attributes('title'), 'the description of the destination').toBe('Administration')
    expect(wrapper.find('[data-test="label"]').attributes('title'), 'the full name of a cut label').toBe('Administration')
  })

  it('leaves both titles off by default', () => {
    const wrapper = mountItem({})

    expect(control(wrapper).attributes('title')).toBeUndefined()
    expect(wrapper.find('[data-test="label"]').attributes('title')).toBeUndefined()
  })

  it('does not prevent the default action of a plain link, so the browser follows its href', () => {
    const wrapper = mountItem({ href: '#docs' })
    const event = clickEvent()

    dispatch(control(wrapper).element, event)

    expect(event.defaultPrevented, 'a plain href link is followed by the browser').toBe(false)
    expect(wrapper.emitted('select')).toHaveLength(1)
  })
})

describe('MdNavigationItem variants', () => {
  it('draws the bar indicator at 64px', () => {
    const wrapper = mountItem({ variant: 'bar' })

    expect(wrapper.find('[data-test="indicator"]').classes()).toContain('w-16')
  })

  it('draws the rail indicator at 56px', () => {
    const wrapper = mountItem({ variant: 'rail' })

    expect(wrapper.find('[data-test="indicator"]').classes()).toContain('w-14')
  })

  it('draws the bar state layer on the whole destination', () => {
    const wrapper = mountItem({ variant: 'bar' })

    expect(control(wrapper).classes(), 'the bar destination carries the layer').toContain('md-state-layer')
    expect(wrapper.find('[data-test="indicator"]').classes(), 'the bar indicator has no layer of its own').not.toContain('md-state-layer')
  })

  it('draws the rail state layer on the indicator, not on the whole destination', () => {
    const wrapper = mountItem({ variant: 'rail' })

    expect(wrapper.find('[data-test="indicator"]').classes(), 'the rail indicator carries the layer').toContain('md-state-layer')
    expect(control(wrapper).classes(), 'the rail destination has no layer of its own').not.toContain('md-state-layer')
  })
})
