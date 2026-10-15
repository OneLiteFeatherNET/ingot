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

import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import AppearancePreview from './AppearancePreview.vue'
import { buildThemeCss } from '../../theme/runtime'

const mountPreview = async (appearance) => {
  const wrapper = mount(AppearancePreview, { props: { appearance } })
  await flushPromises()
  return wrapper
}

const swatch = (wrapper, role) => wrapper.find(`[data-role="${role}"]`).attributes('data-colour')

// The first declaration of a role in the generated light scheme, which is what the preview shows.
const generatedLight = (seedColor, role) => {
  const match = new RegExp(`--md-sys-color-${role}: (#[0-9a-f]{6});`).exec(buildThemeCss({ seedColor }))
  return match[1]
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('AppearancePreview', () => {
  it('shows the primary, secondary, tertiary, error and surface colours of the generated scheme', async () => {
    const wrapper = await mountPreview({ seedColor: '#006a6a' })

    expect(wrapper.findAll('[data-role]').map(element => element.attributes('data-role')))
      .toEqual(['primary', 'secondary', 'tertiary', 'error', 'surface'])
    expect(swatch(wrapper, 'primary'), 'the preview uses the same generator as the dashboard').toBe(generatedLight('#006a6a', 'primary'))
    expect(swatch(wrapper, 'surface')).toBe(generatedLight('#006a6a', 'surface'))
  })

  it('follows the seed as it is edited', async () => {
    const wrapper = await mountPreview({ seedColor: '#006a6a' })

    await wrapper.setProps({ appearance: { seedColor: '#8a1c1c' } })
    await flushPromises()

    expect(swatch(wrapper, 'primary')).toBe(generatedLight('#8a1c1c', 'primary'))
  })

  it('asks for a seed when none is set, instead of showing a scheme', async () => {
    const wrapper = await mountPreview({ primaryColor: '#123456' })

    expect(wrapper.text()).toContain('Set a seed colour to preview the generated scheme.')
    expect(wrapper.find('[data-role]').exists()).toBe(false)
  })

  it('leaves out a colour that is not #RRGGBB, without warning about it on every key press', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})

    const wrapper = await mountPreview({ seedColor: '#006a6a', primaryColor: 'teal' })

    expect(warn).not.toHaveBeenCalled()
    expect(wrapper.find('[data-role="primary"]').exists()).toBe(true)
  })
})
