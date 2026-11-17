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
import MdAlert from './MdAlert.vue'

describe('MdAlert', () => {
  it('announces the error tone at once with role alert', () => {
    const wrapper = mount(MdAlert, { props: { tone: 'error', title: 'Upload failed' } })

    expect(wrapper.attributes('role'), 'the error tone must use role alert').toBe('alert')
  })

  it.each(['warning', 'tertiary', 'info'] as const)('announces the %s tone politely with role status', (tone) => {
    const wrapper = mount(MdAlert, { props: { tone, title: 'Heads up' } })

    expect(wrapper.attributes('role'), `the ${tone} tone must use role status`).toBe('status')
  })

  it('defaults to the info tone', () => {
    const wrapper = mount(MdAlert, { props: { title: 'Heads up' } })

    expect(wrapper.attributes('data-tone'), 'the default tone must be info').toBe('info')
    expect(wrapper.attributes('role'), 'the default tone must be a status update').toBe('status')
  })

  it('paints the error tone with the error container role', () => {
    const wrapper = mount(MdAlert, { props: { tone: 'error', title: 'Upload failed' } })

    expect(wrapper.classes(), 'the error tone must use the error container role').toEqual(
      expect.arrayContaining(['bg-error-container', 'text-on-error-container'])
    )
  })

  it('renders the title and the default slot as the body', () => {
    const wrapper = mount(MdAlert, {
      props: { tone: 'warning', title: 'Quota almost full' },
      slots: { default: 'Delete old artifacts to free space.' }
    })

    expect(wrapper.text(), 'the title must be rendered').toContain('Quota almost full')
    expect(wrapper.text(), 'the body slot must be rendered').toContain('Delete old artifacts to free space.')
  })

  it('renders the action slot', () => {
    const wrapper = mount(MdAlert, {
      props: { tone: 'info', title: 'Update available' },
      slots: { action: '<button type="button">Review</button>' }
    })

    expect(wrapper.find('button').text(), 'the action slot must render its button').toBe('Review')
  })

  it('carries the anchor on the root', () => {
    const wrapper = mount(MdAlert, { props: { title: 'Heads up' } })

    expect(wrapper.attributes('data-ingot-component'), 'the root must carry the md-alert anchor').toBe('md-alert')
  })
})
