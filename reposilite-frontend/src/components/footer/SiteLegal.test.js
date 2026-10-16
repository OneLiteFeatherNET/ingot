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

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'

// The operator's placeholders are replaced by a plain object, so each test sets the two values.
const placeholders = vi.hoisted(() => ({ icpLicense: '', privacyPolicy: '' }))

vi.mock('../../store/placeholders', () => ({
  default: () => placeholders
}))

import SiteLegal from './SiteLegal.vue'

beforeEach(() => {
  placeholders.icpLicense = ''
  placeholders.privacyPolicy = ''
})

describe('SiteLegal', () => {
  it('shows the ICP licence and the privacy policy link when the operator set both', () => {
    placeholders.icpLicense = '京ICP备00000000号'
    placeholders.privacyPolicy = 'https://example.org/privacy'

    const wrapper = mount(SiteLegal)

    expect(wrapper.text(), 'the licence number is shown').toContain('京ICP备00000000号')
    expect(wrapper.find('a[href="https://example.org/privacy"]').text(), 'the policy link is labelled').toBe('Privacy Policy')
  })

  it('renders nothing when neither value is set', () => {
    const wrapper = mount(SiteLegal)

    expect(wrapper.find('div').exists(), 'an empty legal line adds no box to the page').toBe(false)
  })

  it('shows only the privacy link when there is no licence number', () => {
    placeholders.privacyPolicy = 'https://example.org/privacy'

    const wrapper = mount(SiteLegal)

    expect(wrapper.findAll('a'), 'only the policy link is rendered').toHaveLength(1)
    expect(wrapper.find('span').exists(), 'no separator sits beside a single link').toBe(false)
  })

  it('is drawn in the page flow, not absolutely, so the footer can hold it', () => {
    placeholders.privacyPolicy = 'https://example.org/privacy'

    const wrapper = mount(SiteLegal)

    expect(wrapper.classes(), 'the legal line takes its own space in the flow').not.toContain('absolute')
  })
})
