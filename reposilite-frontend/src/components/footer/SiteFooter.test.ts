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
import SiteFooter from './SiteFooter.vue'

describe('SiteFooter', () => {
  it('renders the footer text when one is set', () => {
    const wrapper = mount(SiteFooter, { props: { text: 'Hosted by OneLiteFeather' } })

    expect(wrapper.find('footer').text(), 'the configured text is the footer content').toBe('Hosted by OneLiteFeather')
  })

  it('renders nothing when no footer text is set', () => {
    const wrapper = mount(SiteFooter, { props: { text: '' } })

    expect(wrapper.find('footer').exists(), 'an unset footer text adds no footer element').toBe(false)
  })

  it('shows markup in the footer text as plain text', () => {
    const text = '<img src="x" onerror="alert(1)"> Hosted'
    const wrapper = mount(SiteFooter, { props: { text } })

    expect(wrapper.find('img').exists(), 'the operator text must not create elements').toBe(false)
    expect(wrapper.find('footer').text(), 'the markup is shown literally').toBe(text)
  })
})
