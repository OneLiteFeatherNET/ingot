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
import { mount } from '@vue/test-utils'
import MdCode from './MdCode.vue'

const mountCode = (text = 'mvn deploy') => mount(MdCode, { slots: { default: () => text } })

describe('MdCode', () => {
  it('renders the text inside a code element', () => {
    const wrapper = mountCode('mvn deploy')

    expect((wrapper.element as HTMLElement).tagName).toBe('CODE')
    expect(wrapper.text()).toBe('mvn deploy')
  })

  it('uses the monospace type role of the catalogue', () => {
    expect(mountCode().classes(), 'inline code is set in md-code-medium').toContain('md-code-medium')
  })

  it('draws a surface tint behind the code, with a small corner', () => {
    const classes = mountCode().classes()

    expect(classes).toContain('bg-surface-container-highest')
    expect(classes).toContain('rounded-corner-extra-small')
  })

  it('carries the md-code anchor on the root element', () => {
    expect(mountCode().attributes('data-ingot-component')).toBe('md-code')
  })
})
