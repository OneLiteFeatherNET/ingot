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

import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import StatusLabel from './StatusLabel.vue'

type Status = 'good' | 'warning' | 'critical' | 'neutral'

const mountLabel = (status: Status = 'good') => mount(StatusLabel, {
  props: { status },
  slots: { default: 'Healthy' }
})

const dotOf = (status: Status) => (mountLabel(status).element as HTMLElement).firstElementChild as HTMLElement

// The runtime declaration of the props, where the validator of the status lives.
const declaredProps = () =>
  (StatusLabel as unknown as { props: Record<string, { validator?: (value: unknown) => boolean }> }).props

describe('StatusLabel', () => {
  it('lays the dot and its text on one centre line with the shared gap', () => {
    const root = mountLabel().element as HTMLElement

    expect(root.tagName).toBe('SPAN')
    expect(root.className.split(' ')).toEqual(expect.arrayContaining(['inline-flex', 'items-center', 'gap-1.5']))
  })

  it('draws the dot as an 8 by 8 pixel circle that does not shrink', () => {
    const classes = dotOf('good').className.split(' ')

    expect(classes, 'the dot is size-2, which is 8px').toEqual(expect.arrayContaining(['size-2', 'shrink-0', 'rounded-corner-full']))
  })

  it('places the dot before the text and hides it from assistive technology', () => {
    const wrapper = mountLabel()
    const [dot, text] = (wrapper.element as HTMLElement).children

    expect(dot.getAttribute('aria-hidden')).toBe('true')
    expect(text.textContent?.trim(), 'the text is what a reader hears').toBe('Healthy')
  })

  it.each([
    ['good', 'bg-success'],
    ['warning', 'bg-warning'],
    ['critical', 'bg-error'],
    ['neutral', 'bg-outline']
  ] as const)('paints the dot of the %s status with the %s token class', (status, token) => {
    expect(dotOf(status).className.split(' '), `the ${status} dot takes its colour from a token class`).toContain(token)
  })

  it('sets no inline colour on the dot', () => {
    expect(dotOf('warning').getAttribute('style'), 'the colour comes from the class, not a style attribute').toBeNull()
  })

  it('accepts only the statuses of the closed set', () => {
    const validator = declaredProps().status?.validator

    expect(validator, 'the status prop is checked at runtime').toBeTypeOf('function')
    for (const status of ['good', 'warning', 'critical', 'neutral']) {
      expect(validator?.(status), `${status} is one of the set`).toBe(true)
    }
    expect(validator?.('var(--md-ext-color-success)'), 'a colour is not a status').toBe(false)
  })

  it('has no colour prop', () => {
    expect(Object.keys(declaredProps()), 'the caller names a status, never a colour').not.toContain('color')
  })
})
