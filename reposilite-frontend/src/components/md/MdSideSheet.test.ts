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

import { describe, it, expect, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import { defineComponent, h, reactive } from 'vue'
import type { Component } from 'vue'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import MdSideSheet from './MdSideSheet.vue'
import MdTransition from './MdTransition.vue'

// The sheet is attached to the document, so that focus can move into it and be read back. Each test unmounts what it
// mounted and removes the opener it made, so no test reads another test's focus.
const mounted: VueWrapper[] = []
const openers: HTMLElement[] = []
afterEach(() => {
  mounted.splice(0).forEach(wrapper => wrapper.unmount())
  openers.splice(0).forEach(element => element.remove())
})

const dialog = () => document.querySelector<HTMLElement>('[role="dialog"]')!
const closeButton = () => dialog().querySelector<HTMLElement>('[aria-label="Close"]')!
// The panel is the scrollable column that holds the heading and the content of the sheet.
const panel = () => dialog().querySelector<HTMLElement>('.overflow-y-auto')!
const scrim = () => dialog().firstElementChild as HTMLElement

// The rules of the named transition classes, read from disk as the stylesheet tests do: vitest does not process
// stylesheets. Each rule is its selectors, with quotes and spaces made uniform, and the text of its block.
const transitionRules = () => {
  const css = readFileSync(join(process.cwd(), 'src', 'styles', 'md3', 'motion-transitions.css'), 'utf8')
    .replace(/\/\*[\s\S]*?\*\//g, '')
  return [...css.matchAll(/([^{}]+)\{([^{}]*)\}/g)].map((match) => ({
    selectors: match[1].split(',').map((selector) => selector.trim().replace(/\s+/g, ' ').replace(/'/g, '"')),
    body: match[2]
  }))
}

// The value that the stylesheet gives one property under one selector, or undefined when no rule sets it.
const declared = (selector: string, property: string): string | undefined => {
  const rule = transitionRules().find((candidate) => candidate.selectors.includes(selector))
  return rule?.body.match(new RegExp(`(?:^|;|\\s)${property}\\s*:\\s*([^;]+);`))?.[1].trim()
}

// A page that owns the open state, as the Administration frame does. The test reads `state` to close the sheet from the
// page side, as a choice of an entry does.
const mountSheet = async ({ open = true, side = 'start', width = 'default' }: { open?: boolean, side?: 'start' | 'end', width?: 'default' | 'wide' } = {}) => {
  const opener = document.createElement('button')
  opener.textContent = 'Sections'
  document.body.append(opener)
  openers.push(opener)
  opener.focus()

  const state = reactive({ open })
  const page = defineComponent({
    setup() {
      return () => h(MdSideSheet, {
        open: state.open,
        side,
        width,
        title: 'Administration pages',
        'onUpdate:open': (value: boolean) => { state.open = value }
      }, () => [
        h('a', { href: '/access/users' }, 'Users'),
        h('a', { href: '/access/teams' }, 'Teams')
      ])
    }
  })
  const wrapper = mount(page, { attachTo: document.body })
  mounted.push(wrapper)
  await flushPromises()
  return { state, opener, wrapper }
}

describe('MdSideSheet', () => {
  it('draws a modal dialog named by its title', async () => {
    await mountSheet()

    expect(dialog(), 'the sheet is a dialog').not.toBeNull()
    expect(dialog().getAttribute('aria-modal'), 'the dialog is modal').toBe('true')
    const name = document.getElementById(dialog().getAttribute('aria-labelledby')!)
    expect(name?.textContent.trim(), 'the accessible name is the title').toBe('Administration pages')
  })

  it('carries the md-side-sheet anchor name on the dialog', async () => {
    await mountSheet()

    expect(dialog().getAttribute('data-ingot-component'), 'the anchor name of the component').toBe('md-side-sheet')
  })

  it('draws nothing while it is closed', async () => {
    await mountSheet({ open: false })

    expect(dialog(), 'no dialog in the document').toBeNull()
  })

  it('moves focus into the sheet when it opens, on the first focusable element', async () => {
    await mountSheet()

    expect(dialog().contains(document.activeElement), 'the focus is inside the sheet').toBe(true)
    expect(document.activeElement, 'the first focusable element is the close button').toBe(closeButton())
  })

  it('keeps Tab inside the sheet by moving from the last element to the first', async () => {
    await mountSheet()
    const links = dialog().querySelectorAll('a')
    links[links.length - 1].focus()

    links[links.length - 1].dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true }))

    expect(document.activeElement, 'focus wraps to the first element').toBe(closeButton())
  })

  it('keeps Shift Tab inside the sheet by moving from the first element to the last', async () => {
    await mountSheet()
    const links = dialog().querySelectorAll('a')
    closeButton().focus()

    closeButton().dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true, cancelable: true }))

    expect(document.activeElement, 'focus wraps to the last element').toBe(links[links.length - 1])
  })

  it('closes on Escape and returns focus to the element that opened it', async () => {
    const { state, opener } = await mountSheet()

    dialog().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }))
    await flushPromises()

    expect(state.open, 'the sheet asked its page to close').toBe(false)
    expect(dialog(), 'the sheet is closed').toBeNull()
    expect(document.activeElement, 'focus is back on the Sections button').toBe(opener)
  })

  it('closes with the close button and returns focus to the element that opened it', async () => {
    const { state, opener } = await mountSheet()

    closeButton().click()
    await flushPromises()

    expect(state.open, 'the sheet asked its page to close').toBe(false)
    expect(document.activeElement, 'focus is back on the Sections button').toBe(opener)
  })

  it('does not take focus back when the page closes it after a choice', async () => {
    const { state, opener } = await mountSheet()
    const link = dialog().querySelector<HTMLAnchorElement>('a')!
    link.focus()

    // The page closes the sheet because an entry was chosen: the page moves the focus to its main content, so the opener
    // must not get it back.
    state.open = false
    await flushPromises()

    expect(document.activeElement, 'the focus is not moved to the opener').not.toBe(opener)
  })

  it('draws the close glyph through MdIcon', async () => {
    await mountSheet()

    expect(closeButton().querySelector('svg')!.getAttribute('data-ingot-component'), 'the glyph is the shared MdIcon').toBe('md-icon')
    expect(closeButton().querySelector('path')!.getAttribute('d'), 'the glyph is the close path of CloseIcon').toBe('M6 18L18 6M6 6l12 12')
  })

  it('switches off its transition for a visitor who asks for less motion', async () => {
    await mountSheet()

    expect(dialog().classList, 'the transition is off under prefers-reduced-motion').toContain('motion-reduce:transition-none')
  })

  it('draws the panel at the leading edge by default', async () => {
    await mountSheet()

    expect(panel().classList, 'the panel is pinned to the start edge').toContain('start-0')
    expect(panel().classList, 'the panel is not pinned to the end edge').not.toContain('end-0')
  })

  it('draws the panel at the trailing edge when side is end', async () => {
    await mountSheet({ side: 'end' })

    expect(panel().classList, 'the panel is pinned to the end edge').toContain('end-0')
    expect(panel().classList, 'the panel is not pinned to the start edge').not.toContain('start-0')
  })

  it('returns focus to the opener when a trailing sheet closes with Escape', async () => {
    const { state, opener } = await mountSheet({ side: 'end' })

    dialog().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }))
    await flushPromises()

    expect(state.open, 'the sheet asked its page to close').toBe(false)
    expect(document.activeElement, 'focus is back on the Sections button').toBe(opener)
  })

  it('draws the panel at the side sheet elevation, one step below a menu', async () => {
    await mountSheet()

    expect(panel().classList, 'the panel carries the side sheet elevation').toContain('shadow-elevation-1')
    expect(panel().classList, 'the panel does not carry the menu elevation').not.toContain('shadow-elevation-2')
  })

  it('takes the default width from the sheet width token', async () => {
    await mountSheet()

    expect(panel().classList, 'the width is the sheet width token').toContain('w-md-sheet')
    expect(panel().classList, 'the width is capped at the window less the margin token').toContain('max-w-md-sheet-max')
    expect(panel().getAttribute('style'), 'the panel has no inline width').toBeNull()
  })

  it('takes the wide width from the wide sheet width token when width is wide', async () => {
    await mountSheet({ side: 'end', width: 'wide' })

    expect(panel().classList, 'the width is the wide sheet width token').toContain('w-md-sheet-wide')
    expect(panel().classList, 'the default width token is not used').not.toContain('w-md-sheet')
  })

  it('draws the dialog on the dialog stacking layer', async () => {
    await mountSheet()

    expect(dialog().classList, 'the stacking layer is the dialog token').toContain('z-md-dialog')
    expect(dialog().classList, 'the stacking layer is not the default z-50').not.toContain('z-50')
  })

  it('draws the scrim from the scrim token with no inline style', async () => {
    await mountSheet()
    const scrim = dialog().firstElementChild as HTMLElement

    expect(scrim.classList, 'the scrim is the md3 scrim at its token opacity').toContain('bg-scrim/md-scrim')
    expect(scrim.getAttribute('style'), 'the scrim has no inline style').toBeNull()
  })

  it('draws the frame, the scrim and the panel of the sheet with no arbitrary value class', async () => {
    await mountSheet()
    const frame = [dialog(), dialog().firstElementChild as HTMLElement, panel()]
    const classes = frame.flatMap(element => [...element.classList])

    expect(classes.filter(name => name.includes('[')), 'no class uses an arbitrary value').toEqual([])
  })

  it('opens and closes through the sheet pattern of MdTransition, from the first paint', async () => {
    const { wrapper } = await mountSheet()
    const transition = wrapper.findComponent(MdTransition as Component) as unknown as { exists: () => boolean, props: (key: 'pattern' | 'appear') => unknown }

    expect(transition.exists(), 'the sheet is wrapped by MdTransition').toBe(true)
    expect(transition.props('pattern'), 'the pattern of a side sheet').toBe('sheet')
    expect(transition.props('appear'), 'a sheet mounted open still slides in').toBe(true)
  })

  it('marks the scrim and the panel as the parts that the sheet pattern moves', async () => {
    await mountSheet()

    expect(scrim().getAttribute('data-md-sheet-part'), 'the scrim is the part that fades').toBe('scrim')
    expect(panel().getAttribute('data-md-sheet-part'), 'the panel is the part that slides').toBe('panel')
  })

  it.each([
    ['start', 'translateX(-100%)'],
    ['end', 'translateX(100%)']
  ] as const)('slides a panel at the %s edge in from outside that edge', async (side, transform) => {
    await mountSheet({ side })

    expect(panel().getAttribute('data-md-sheet-side'), 'the panel names its edge for the stylesheet').toBe(side)
    const part = `[data-md-sheet-part="panel"][data-md-sheet-side="${side}"]`
    expect(declared(`.motion-sheet-enter-from > ${part}`, 'transform'), 'the panel enters from its edge').toBe(transform)
    expect(declared(`.motion-sheet-leave-to > ${part}`, 'transform'), 'the panel leaves to its edge').toBe(transform)
  })

  it('fades the scrim in and out in the sheet pattern, while the panel only slides', () => {
    const scrimPart = '[data-md-sheet-part="scrim"]'
    const panelPart = '[data-md-sheet-part="panel"]'

    expect(declared(`.motion-sheet-enter-from > ${scrimPart}`, 'opacity'), 'the scrim fades in').toBe('0')
    expect(declared(`.motion-sheet-leave-to > ${scrimPart}`, 'opacity'), 'the scrim fades out').toBe('0')
    expect(declared(`.motion-sheet-enter-active > ${scrimPart}`, 'transition'), 'the scrim fades over short4')
      .toMatch(/^opacity var\(--md-sys-motion-duration-short4\)/)
    expect(declared(`.motion-sheet-leave-active > ${scrimPart}`, 'transition'), 'the scrim fades out over short3')
      .toMatch(/^opacity var\(--md-sys-motion-duration-short3\)/)
    expect(declared(`.motion-sheet-enter-active > ${panelPart}`, 'transition'), 'the panel slides in over medium2')
      .toBe('transform var(--md-sys-motion-duration-medium2) var(--md-sys-motion-easing-emphasized-decelerate)')
    expect(declared(`.motion-sheet-leave-active > ${panelPart}`, 'transition'), 'the panel slides out over short4')
      .toBe('transform var(--md-sys-motion-duration-short4) var(--md-sys-motion-easing-emphasized-accelerate)')
  })

  it('keeps the frame of the sheet in place while its parts move', () => {
    expect(declared('.motion-sheet-enter-from', 'transform'), 'the frame does not slide, so the scrim covers the window').toBeUndefined()
    expect(declared('.motion-sheet-leave-to', 'transform'), 'the frame does not slide on the way out').toBeUndefined()
  })
})
