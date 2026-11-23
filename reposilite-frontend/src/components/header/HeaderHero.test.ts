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
import { mount, type VueWrapper } from '@vue/test-utils'
import HeaderHero from './HeaderHero.vue'
import useTheme from '../../store/theme'
import { setAppearance } from '../../store/appearance'

describe('HeaderHero', () => {
  it('draws no brand bar of its own, since the top bar carries the rule across the window', () => {
    const wrapper = mount(HeaderHero)

    expect(wrapper.findAll('.olf-brand-bar'), 'the hero must not draw a second rule under the top bar').toHaveLength(0)
  })
})

describe('HeaderHero window classes', () => {
  it('lays the logo beside the text from the medium window class', () => {
    const classes = mount(HeaderHero).find('img').element.parentElement!.className.split(/\s+/)

    expect(classes, 'a row from the medium window class').toContain('medium:flex-row')
    expect(classes, 'the narrow padding below the medium window class').toContain('max-medium:py-6')
    expect(classes, 'no default Tailwind breakpoint').not.toContain('md:flex-row')
    expect(classes, 'no default Tailwind max breakpoint').not.toContain('max-sm:py-6')
  })
})

describe('HeaderHero logo', () => {
  const logoSource = (wrapper: VueWrapper<InstanceType<typeof HeaderHero>>) => wrapper.find('img').attributes('src')

  afterEach(() => {
    setAppearance({})
    useTheme().theme.isDark = false
  })

  it('shows the dark logo in the dark theme when the operator set one', () => {
    setAppearance({ logoDark: 'https://example.org/logo-dark.svg' })
    useTheme().theme.isDark = true

    expect(logoSource(mount(HeaderHero)), 'the dark theme takes the dark logo').toBe('https://example.org/logo-dark.svg')
  })

  it('keeps the organisation logo in the light theme', () => {
    setAppearance({ logoDark: 'https://example.org/logo-dark.svg' })

    expect(logoSource(mount(HeaderHero)), 'the light theme keeps the regular logo').not.toBe('https://example.org/logo-dark.svg')
  })
})
